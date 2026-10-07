package net.tfminecraft.simplefactions.espionage;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

public final class EspionageService {
    public static final String BYPASS_PERMISSION = EspionageConfig.DEFAULT_BYPASS_PERMISSION;
    private static CharacterAptitudes characterAptitudes;
    private EspionageService() {}

    public static void loadAptitudes(java.nio.file.Path file) throws java.io.IOException {
        CharacterAptitudes registry = new CharacterAptitudes(file);
        registry.load();
        // Preserve existing rolls from the first dev version, including removed offices.
        for (Faction faction : FactionManager.getCopy().stream()
                .sorted(java.util.Comparator.comparing(Faction::getId)).toList()) {
            faction.getEspionage().legacyAptitudes().forEach((key, value) -> {
                int separator = key.indexOf(':');
                if (separator >= 0) registry.remember(key.substring(separator + 1), value);
            });
            var holder = faction.getEspionage().getSpymaster();
            if (holder != null) registry.remember(holder.characterId, holder.aptitude);
        }
        for (Faction faction : FactionManager.getCopy()) {
            var holder = faction.getEspionage().getSpymaster();
            if (holder != null && holder.characterId != null)
                holder.aptitude = registry.aptitude(holder.characterId, () -> holder.aptitude);
        }
        registry.save();
        characterAptitudes = registry;
    }

    public static boolean eligible(Faction faction, String playerName) {
        return faction.isMemberIgnoreCase(playerName) || faction.isLeader(playerName);
    }

    /** The leader's default holding follows the leadership; deliberate appointments stay. */
    private static boolean validHolder(Faction faction, SpecialPositionAssignment holder) {
        return holder != null && eligible(faction, holder.playerName) && !deadCharacter(holder)
                && !(holder.automatic && !faction.isLeader(holder.playerName));
    }

    /** New factions and every later vacancy fall to the leader; it waits for their active character. */
    public static void initializeFounder(Faction faction) {
        initializeFounder(faction, null);
    }

    private static void initializeFounder(Faction faction, java.util.Set<Faction> dirty) {
        if (faction.getEspionage() == null || faction.getLeader() == null || org.bukkit.Bukkit.getServer() == null) return;
        var founder = org.bukkit.Bukkit.getPlayerExact(faction.getLeader());
        String characterId = OfficeCharacters.activeCharacterId(founder);
        // A dead character would be revoked again on the next check.
        if (characterId != null && founder != null && OfficeCharacters.isDead(founder.getUniqueId(), characterId)) characterId = null;
        for (SpecialPosition office : SpecialPosition.values()) {
            if (faction.getEspionage().holder(office) != null) continue;
            if (characterId == null) {
                faction.getEspionage().pendingFounder(office);
                continue;
            }
            var assignment = new SpecialPositionAssignment();
            assignment.playerName = faction.getLeader();
            assignment.playerId = founder == null ? org.bukkit.Bukkit.getOfflinePlayer(faction.getLeader()).getUniqueId() : founder.getUniqueId();
            int aptitude = 0;
            try {
                aptitude = characterAptitude(founder, characterId);
                assignment.characterId = characterId;
            } catch (java.io.IOException exception) {
                net.tfminecraft.simplefactions.SimpleFactions.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Could not save founder aptitude", exception);
                String previousCharacterId = faction.getEspionage().pendingFounderCharacter(office);
                // Keep the retry intent even for a newly created faction; roll back only the unsaved binding.
                faction.getEspionage().pendingFounder(office);
                var previous = faction.getEspionage().snapshotOffices();
                faction.getEspionage().pendingFounder(office, characterId);
                if (!characterId.equals(previousCharacterId)) {
                    if (dirty == null) {
                        if (!new Database().saveFactionChecked(faction)) faction.getEspionage().restoreOffices(previous);
                    } else saveOrMark(faction, dirty);
                }
                if (founder != null) founder.sendMessage("\u00a7cYour founding office could not be initialized. It will be retried when the office is checked.");
                continue;
            }
            faction.getEspionage().assignFounder(office, assignment, aptitude);
            if (founder != null) founder.sendMessage("\u00a78\u00a7oUntil a trusted hand is appointed, you hold the keys to the "
                    + office.label() + "'s office. Visit Special Positions in your faction menu.");
        }
    }

    /** Epoch millis from which the leader may appoint another Spymaster; zero means no wait. */
    public static long nextAppointmentAt(Faction faction) {
        long last = faction.getEspionage().lastAppointedAt(SpecialPosition.SPYMASTER);
        double days = EspionageConfig.changeCooldownDays();
        return last <= 0 || days <= 0 ? 0 : last + Math.round(days * 86_400_000);
    }

    static boolean completeAppointment(Faction faction, SpecialPositionAssignment assignment, int aptitude, long now) {
        if (now < nextAppointmentAt(faction)) return false;
        boolean repeat = faction.getEspionage().appointmentCount(SpecialPosition.SPYMASTER) > 0;
        faction.getEspionage().appoint(assignment, aptitude, now);
        if (repeat) faction.getEspionage().addUnrest(SpecialPosition.SPYMASTER,
                EspionageConfig.stabilityPenalty(), EspionageConfig.penaltyDays(), now);
        return true;
    }

    static boolean completeAndSaveAppointment(Faction faction, SpecialPositionAssignment assignment, int aptitude, long now) {
        var previous = faction.getEspionage().snapshotOffices();
        if (!completeAppointment(faction, assignment, aptitude, now)) return false;
        if (new Database().saveFactionChecked(faction)) return true;
        faction.getEspionage().restoreOffices(previous);
        return false;
    }

    public static int effectiveAptitude(Faction faction, SpecialPositionAssignment holder) {
        return effectiveAptitude(faction, holder, System.currentTimeMillis());
    }

    /** A new holder builds up to their permanent aptitude; a solo leader keeps only part of it. */
    static int effectiveAptitude(Faction faction, SpecialPositionAssignment holder, long now) {
        if (holder == null || !eligible(faction, holder.playerName)) return 0;
        // The epsilon keeps 1 - 0.1 * 3 from flooring a whole result one point low.
        return (int) Math.floor(holder.aptitude * EspionageMath.buildUp(holder.appointedAt, now)
                * positionMultiplier(positionsHeld(faction, holder)) + 1e-9);
    }

    /** Offices held by the same person; an assignment not saved yet still counts as one. */
    public static int positionsHeld(Faction faction, SpecialPositionAssignment holder) {
        if (holder == null || holder.playerName == null || faction.getEspionage() == null) return 1;
        int held = 0;
        for (SpecialPosition office : SpecialPosition.values()) {
            var other = faction.getEspionage().holder(office);
            if (other != null && holder.playerName.equalsIgnoreCase(other.playerName)) held++;
        }
        return Math.max(1, held);
    }

    public static double positionMultiplier(int held) {
        return Math.max(0, 1 - EspionageConfig.extraPositionPenalty() * Math.max(0, held - 1));
    }

    /** Millis until the holder reaches full aptitude; zero once built up. */
    public static long buildUpRemaining(SpecialPositionAssignment holder, long now) {
        if (holder == null || holder.appointedAt <= 0 || !EspionageConfig.buildsUp()) return 0;
        return Math.max(0, holder.appointedAt + Math.round(EspionageConfig.buildUpDays() * 86_400_000) - now);
    }

    public static String duration(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        return net.tfminecraft.tlibs.utils.TimeFormatter.formatTime((int) Math.min(Integer.MAX_VALUE / 60, minutes) * 60);
    }

    public static boolean isOwn(Player viewer, Faction target) {
        return viewer != null && target != null
                && (target.isMemberIgnoreCase(viewer.getName()) || target.isLeader(viewer.getName()));
    }

    public static boolean bypasses(Player viewer) {
        return viewer != null && viewer.hasPermission(EspionageConfig.bypassPermission());
    }

    /** Viewing permission does not grant membership or authority over faction offices. */
    public static boolean canViewExact(Player viewer, Faction target) {
        return viewer != null && target != null
                && (bypasses(viewer) || isOwn(viewer, target) || !hasSpymaster(target));
    }

    /** A vacant, ineligible or deceased holder leaves every guild's information unguarded. */
    public static boolean hasSpymaster(Faction faction) {
        if (faction == null || faction.getEspionage() == null) return false;
        return validHolder(faction, faction.getEspionage().getSpymaster());
    }

    public static SpecialPositionAssignment spymaster(Faction faction) {
        return spymaster(faction, null);
    }

    private static void saveOrMark(Faction faction, java.util.Set<Faction> dirty) {
        if (dirty == null) new Database().saveFaction(faction);
        else dirty.add(faction);
    }

    private static SpecialPositionAssignment spymaster(Faction faction, java.util.Set<Faction> dirty) {
        var state = faction.getEspionage();
        boolean changed = false;
        SpecialPositionAssignment holder = state.getSpymaster();
        if (holder != null && !validHolder(faction, holder)) {
            boolean died = eligible(faction, holder.playerName) && deadCharacter(holder);
            state.removeSpymaster();
            if (died) state.waiveAppointmentWait(SpecialPosition.SPYMASTER);
            changed = true;
        }
        // Whatever emptied the office, it falls back to the current leader.
        if (state.getSpymaster() == null && !state.isPendingFounder(SpecialPosition.SPYMASTER) && faction.getLeader() != null) {
            state.pendingFounder(SpecialPosition.SPYMASTER);
            changed = true;
        }
        if (state.hasPendingFounder()) {
            initializeFounder(faction, dirty);
            if (!state.hasPendingFounder()) changed = true;
        }
        if (changed) saveOrMark(faction, dirty);
        return state.getSpymaster();
    }

    private static boolean deadCharacter(SpecialPositionAssignment holder) {
        return OfficeCharacters.isDead(holder.playerId, holder.characterId);
    }

    /** Character death, rather than an ordinary Minecraft respawn, ends the appointment. */
    public static void characterDied(Player owner, String characterId, String characterName) {
        if (characterId == null) return;
        for (Faction faction : FactionManager.getCopy()) {
            var state = faction.getEspionage();
            var holder = state.getSpymaster();
            boolean matching = holder != null && (characterId.equals(holder.characterId)
                    || holder.characterId == null && owner != null && holder.isHolder(owner.getUniqueId()));
            boolean pending = state.isPendingFounder(SpecialPosition.SPYMASTER) && owner != null && faction.isLeader(owner.getName())
                    && characterId.equals(state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
            if (!matching && !pending) continue;
            state.removeSpymaster();
            state.waiveAppointmentWait(SpecialPosition.SPYMASTER);
            new Database().saveFaction(faction);
            if (owner != null) owner.sendMessage("\u00a78\u00a7oWith the passing of " + characterName
                    + ", the keys to the Spymaster's office return to the faction leader until a successor is appointed.");
        }
    }

    public static java.util.List<net.tfminecraft.simplefactions.government.StabilityModifier> stabilityModifiers(Faction faction, long now) {
        var result = new java.util.ArrayList<>(faction.getEspionage().unrestModifiers(now));
        for (SpecialPosition office : SpecialPosition.values()) {
            var holder = faction.getEspionage().holder(office);
            boolean occupied = office != SpecialPosition.SPYMASTER ? holder != null : validHolder(faction, holder);
            double penalty = EspionageConfig.vacancyPenalty(office);
            if (!occupied && penalty > 0) result.add(new net.tfminecraft.simplefactions.government.StabilityModifier(
                    "Vacant " + office.label(), -penalty, 0));
        }
        return result;
    }

    public static boolean appoint(Player actor, Faction faction, Player candidate) {
        if (!isOwn(actor, faction) || !faction.isLeader(actor.getName())) {
            actor.sendMessage("§cOnly your faction leader can appoint a Spymaster.");
            return false;
        }
        if (candidate == null || !candidate.isOnline() || !isOwn(candidate, faction)) {
            actor.sendMessage("§cChoose an online member of your own faction.");
            return false;
        }
        String characterId = OfficeCharacters.activeCharacterId(candidate);
        if (characterId == null || OfficeCharacters.isDead(candidate.getUniqueId(), characterId)) {
            actor.sendMessage("§cThat member needs an active roleplay character.");
            return false;
        }
        SpecialPositionAssignment current = spymaster(faction);
        if (current != null && current.isHolder(candidate.getUniqueId()) && characterId.equals(current.characterId)) {
            actor.sendMessage("§7That member is already your Spymaster.");
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < nextAppointmentAt(faction)) {
            actor.sendMessage("\u00a7cA new Spymaster can be appointed in " + duration(nextAppointmentAt(faction) - now) + ".");
            return false;
        }
        boolean repeat = faction.getEspionage().appointmentCount(SpecialPosition.SPYMASTER) > 0;
        SpecialPositionAssignment assignment = new SpecialPositionAssignment();
        assignment.playerId = candidate.getUniqueId();
        assignment.playerName = candidate.getName();
        assignment.characterId = characterId;
        try {
            if (characterAptitudes == null) throw new java.io.IOException("Character aptitude registry is unavailable");
            int aptitude = characterAptitude(candidate, characterId);
            if (!completeAndSaveAppointment(faction, assignment, aptitude, now)) {
                actor.sendMessage("\u00a7cThe appointment could not be saved. The office is unchanged.");
                return false;
            }
        } catch (java.io.IOException exception) {
            actor.sendMessage("§cThe aptitude could not be saved. The appointment has not been made.");
            net.tfminecraft.simplefactions.SimpleFactions.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not save character aptitude", exception);
            return false;
        }
        // Address only the appointee. Sabotage preferences are never broadcast.
        candidate.sendMessage("§8§oA sealed letter reaches your hands. The keys to " + faction.getName()
                + "§8§o's unseen network are now yours. You have been appointed Spymaster.");
        candidate.sendMessage("§7Your aptitude for this office is §e" + effectiveAptitude(faction, assignment, now)
                + "/100§7. Inspect Special Positions in the faction menu, or use §a/faction espionage§7.");
        if (buildUpRemaining(assignment, now) > 0)
            candidate.sendMessage("§7Your network is still taking shape. Full aptitude (§e" + assignment.aptitude
                    + "§7) in §e" + duration(buildUpRemaining(assignment, now)) + "§7.");
        int held = positionsHeld(faction, assignment);
        if (held > 1) candidate.sendMessage("§7Holding " + held + " offices leaves you " + Math.round(positionMultiplier(held) * 100)
                + "% of your aptitude in each (base: " + assignment.aptitude + ").");
        if (!actor.getUniqueId().equals(candidate.getUniqueId())) actor.sendMessage("§aSpymaster appointed.");
        if (repeat && EspionageConfig.stabilityPenalty() > 0 && EspionageConfig.penaltyDays() > 0)
            actor.sendMessage("\u00a77The change of office brings " + EspionageConfig.stabilityPenalty()
                    + " points of unrest, fading over " + EspionageConfig.penaltyDays() + " days.");
        return true;
    }

    private static int characterAptitude(Player player, String characterId) throws java.io.IOException {
        if (characterAptitudes == null) throw new java.io.IOException("Character aptitude registry is unavailable");
        return characterAptitudes.aptitude(characterId, () ->
                EspionageMath.aptitude(OfficeCharacters.attributes(player), ThreadLocalRandom.current()));
    }

    public static boolean remove(Player actor, Faction faction) {
        if (!isOwn(actor, faction) || !faction.isLeader(actor.getName())) {
            actor.sendMessage("§cOnly your faction leader can remove the Spymaster.");
            return false;
        }
        var current = spymaster(faction);
        if (current == null || current.automatic) {
            actor.sendMessage("§7No Spymaster has been appointed. The office already rests with you as faction leader.");
            return false;
        }
        var previous = faction.getEspionage().snapshotOffices();
        faction.getEspionage().removeSpymaster();
        if (!new Database().saveFactionChecked(faction)) {
            faction.getEspionage().restoreOffices(previous);
            actor.sendMessage("\u00a7cThe office removal could not be saved. The Spymaster remains appointed.");
            return false;
        }
        actor.sendMessage("§aThe Spymaster has been dismissed. The office returns to you as faction leader.");
        spymaster(faction);
        return true;
    }

    public static boolean setSabotage(Player actor, Faction faction, boolean offense, int reduction) {
        SpecialPositionAssignment holder = spymaster(faction);
        if (!isOwn(actor, faction) || holder == null || !holder.isHolder(actor.getUniqueId())) {
            actor.sendMessage("§cOnly the appointed Spymaster can change their private conduct.");
            return false;
        }
        if (reduction < 0 || reduction > 100 || reduction % 25 != 0) {
            actor.sendMessage("§cChoose a reduction of 0, 25, 50, 75, or 100. Zero disables sabotage.");
            return false;
        }
        int previous = offense ? holder.offenseReduction : holder.defenseReduction;
        if (offense) holder.offenseReduction = reduction;
        else holder.defenseReduction = reduction;
        if (!new Database().saveFactionChecked(faction)) {
            if (offense) holder.offenseReduction = previous;
            else holder.defenseReduction = previous;
            actor.sendMessage("\u00a7cYour private conduct could not be saved. Your previous choice remains in effect.");
            return false;
        }
        actor.sendMessage("§7Private " + (offense ? "offensive" : "defensive") + " sabotage: "
                + (reduction == 0 ? "§adisabled" : "§c-" + reduction + " to your roll")
                + "§7. This affects your next daily rolls; existing reports stay unchanged.");
        return true;
    }

    /** Reading or clicking menus never generates intelligence. */
    public static IntelligenceReport report(Player viewer, Faction target) {
        if (viewer == null || canViewExact(viewer, target)) return null;
        Faction observer = FactionManager.getByMember(viewer.getName());
        if (!hasSpymaster(observer)) return null;
        return observer.getEspionage().cachedReport(target.getId(), target.getFoundedAt(), day());
    }

    private static long day() { return LocalDate.now(ZoneOffset.UTC).toEpochDay(); }

    /** GUI-opening commands request one faction-wide report per foreign target each UTC day. */
    public static void refreshReports(Player viewer) {
        if (viewer == null || bypasses(viewer)) return;
        Faction observer = FactionManager.getByMember(viewer.getName());
        if (observer == null) return;
        boolean updated = false;
        java.util.Set<Faction> dirty = new java.util.LinkedHashSet<>();
        if (spymaster(observer, dirty) == null) {
            for (Faction faction : dirty) new Database().saveFaction(faction);
            return;
        }
        for (Faction target : FactionManager.getCopy()) {
            if (target == observer || target.getId().equals(observer.getId())) continue;
            if (observer.getEspionage().cachedReport(target.getId(), target.getFoundedAt(), day()) == null) {
                if (generateReport(observer, target, dirty) != null) updated = true;
            }
        }
        for (Faction faction : dirty) new Database().saveFaction(faction);
        if (updated) viewer.sendMessage("§8§oYour faction's unseen network delivers today's sealed intelligence reports.");
    }

    private static IntelligenceReport generateReport(Faction observer, Faction target, java.util.Set<Faction> dirty) {
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        var defender = spymaster(target, dirty);
        if (defender == null) return null; // Exact public information needs no daily estimate.
        var attacker = spymaster(observer, dirty);
        if (attacker == null) return null; // A vacant office cannot deliver foreign findings.
        boolean[] created = {false};
        RandomGenerator random = ThreadLocalRandom.current();
        IntelligenceReport report = observer.getEspionage().report(target.getId(), target.getFoundedAt(), day, () -> {
            created[0] = true;
            int margin = observer.getEspionage().rolls(day, effectiveAptitude(observer, attacker), random).offense()
                    - target.getEspionage().rolls(day, effectiveAptitude(target, defender), random).defense();
            IntelligenceReport generated = createReport(metrics(target), margin, random);
            captureMembers(generated, target, margin, random);
            captureOffices(generated, target, random);
            ReportDetails.capture(generated, target);
            return generated;
        });
        if (created[0]) {
            dirty.add(target);
            dirty.add(observer);
        }
        return report;
    }

    static java.util.List<String> sample(java.util.List<String> members, int margin, RandomGenerator random) {
        java.util.List<String> shuffled = new java.util.ArrayList<>(members);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            java.util.Collections.swap(shuffled, i, other);
        }
        var tier = EspionageConfig.tier(margin);
        double fraction = EspionageConfig.allows(tier, "roster") ? EspionageConfig.settings(tier).rosterFraction() : 0;
        int count = (int) Math.floor(shuffled.size() * fraction);
        return shuffled.subList(0, Math.min(EspionageConfig.rosterLimit(), count)).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    static void captureMembers(IntelligenceReport report, Faction target, int margin, RandomGenerator random) {
        var sample = sample(target.getMembers().stream().distinct()
                .filter(name -> !target.isLeader(name)).toList(), margin, random);
        report.members = sample.stream().map(name -> CharacterNames.forForeign(name) + " §7— "
                + net.tfminecraft.simplefactions.utils.Represents.represents(target, name)).toList();
        for (var guild : target.getGuildHandler().getGuilds()) {
            report.guildMembers.put(guild.getId(), sample.stream().filter(guild::isMember)
                    .filter(name -> !guild.isLeader(name)).map(CharacterNames::forForeign).toList());
        }
        report.roster = new java.util.ArrayList<>();
        for (var guild : target.getGuildHandler().getGuilds()) {
            for (String name : guild.getMembers()) {
                if (target.isLeader(name)) continue;
                var offices = java.util.Arrays.stream(SpecialPosition.values()).filter(office -> {
                    var holder = target.getEspionage().holder(office);
                    return holder != null && holder.playerName.equalsIgnoreCase(name);
                }).toList();
                boolean knownLeader = guild.isLeader(name) && report.allows("guild-leader");
                boolean knownOffice = !offices.isEmpty() && report.allows("office-holder");
                if (!knownLeader && !knownOffice && (!report.allows("guild-members") || !sample.contains(name))) continue;
                report.roster.add(new IntelligenceReport.RosterMember(CharacterNames.forForeign(name), guild.getId(), guild.getName(), sample.contains(name),
                        knownLeader, knownOffice ? offices : java.util.List.of()));
            }
        }
    }

    public static Double visibleValue(Player viewer, Faction target, String metric, java.util.function.DoubleSupplier exact) {
        if (metric.equals("Prestige") || canViewExact(viewer, target)) return exact.getAsDouble();
        var report = report(viewer, target);
        var estimate = report == null ? null : report.estimate(metric);
        return estimate == null ? null : estimate.midpoint();
    }

    static IntelligenceReport createReport(Map<String, Double> metrics, int margin, RandomGenerator random) {
        IntelligenceReport report = new IntelligenceReport();
        report.quality = EspionageMath.quality(margin);
        metrics.forEach((key, value) -> {
            if (!report.allows(key)) return;
            boolean signed = !IntelligenceRanges.nonnegative(key);
            var estimate = EspionageMath.estimate(value, margin, signed, random);
            estimate = IntelligenceRanges.reasonable(key, estimate, report.tier());
            if (estimate != null) report.estimates.put(key, estimate);
        });
        return report;
    }

    static void captureOffices(IntelligenceReport report, Faction target, RandomGenerator random) {
        for (var office : SpecialPosition.values()) {
            var holder = target.getEspionage().holder(office);
            if (report.allows("office-holder")) report.officeHolders.put(office,
                    holder == null ? "Vacant" : CharacterNames.forForeign(holder.playerName));
            String key = IntelligenceReport.officeAptitudeKey(office);
            if (!report.allows(key)) continue;
            double aptitude = office == SpecialPosition.SPYMASTER ? effectiveAptitude(target, holder)
                    : holder == null ? 0 : holder.aptitude;
            var range = EspionageMath.estimate(aptitude, EspionageConfig.settings(report.tier()).minimumMargin(), false, random);
            range = IntelligenceRanges.reasonable(key, range, report.tier());
            if (range != null) report.estimates.put(key, range);
        }
    }

    /** Staff testing explicitly invalidates both sides before rebuilding any report. */
    public static int regenerateReports() {
        var factions = FactionManager.getCopy();
        for (var faction : factions) faction.getEspionage().resetReportsAndRolls();
        int count = 0;
        java.util.Set<Faction> dirty = new java.util.LinkedHashSet<>(factions);
        for (var observer : factions) {
            for (var target : factions) if (!observer.getId().equals(target.getId())) {
                if (generateReport(observer, target, dirty) != null) count++;
            }
        }
        // Persist every reset/report once, including a lone faction with no foreign targets.
        for (Faction faction : factions) new Database().saveFaction(faction);
        return count;
    }

    private static Map<String, Double> metrics(Faction faction) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("Members", (double) faction.getMembers().size());
        values.put("Wealth", faction.getWealth());
        values.put("Prosperity", faction.getProsperity());
        values.put("Daily net income", faction.getOrCreateMainGuild().getLedger().getNetIncome());
        values.put("Professional army", (double) faction.getMilitary().getRegiments().stream()
                .filter(regiment -> regiment.isProfessional() && !regiment.isEquipment())
                .mapToInt(regiment -> regiment.getCurrentSlots()).sum());
        values.put("Levies", (double) (faction.getMilitary().getManpower(false) - faction.getMilitary().getManpowerNoLevy(false)));
        values.put("Mercenaries", (double) faction.getMilitary().getMercenaryManpower());
        values.put("Installations", (double) faction.getInstallationHandler().getAll().size());
        for (var tax : net.tfminecraft.simplefactions.government.proposal.TaxTarget.values())
            if (!tax.name().endsWith("_ID")) values.put("Tax:" + tax.name(), faction.getTaxHandler().getTaxRate(tax, null, false));
        for (int index = 0; index < faction.getMilitary().getQueue().size(); index++)
            values.put("Training:" + index, (double) faction.getMilitary().getQueue().get(index).getTimeLeft());
        for (var guild : faction.getGuildHandler().getGuilds()) {
            values.put("Guild:" + guild.getId() + ":Wealth", guild.getWealth());
            values.put("Guild:" + guild.getId() + ":Members", (double) guild.getMembers().size());
            values.put("Guild:" + guild.getId() + ":Income", guild.getLedger().getNetIncome());
            IntelligenceLedger.capture(values, guild);
            for (int group = 0; group < 10; group++) {
                var branch = guild.getBranch(group);
                if (branch != null) values.put(IntelligenceLedger.key(guild, "Branch:" + branch.getId()), (double) branch.getLevel());
            }
            for (var upgrade : guild.getUpgrades()) values.put(IntelligenceLedger.key(guild, "Upgrade:" + upgrade.getId()), (double) upgrade.getLevel());
            if (guild.hasCapital()) values.put("Guild:" + guild.getId() + ":Trade power", guild.getTradeBreakdown().getTradePower());
        }
        for (var regiment : faction.getMilitary().getRegiments()) values.put("Regiment:" + regiment.getId()
                + (regiment.isLevy() ? ":Levies" : ":Soldiers"), (double) (regiment.isLevy()
                ? regiment.getEntries().stream().mapToInt(net.tfminecraft.simplefactions.army.LevyEntry::getAmount).sum() : regiment.getCurrentSlots()));
        for (var installation : faction.getInstallationHandler().getAll()) values.put("Installation:" + installation.getId() + ":Level", (double) installation.getLevel());
        if (faction.getGovernment() != null) {
            values.put("Stability", faction.getGovernment().getStability());
            values.put("Administrative power", faction.getGovernment().getPower());
            values.put("Legitimacy", faction.getGovernment().stateReport().legitimacy);
            values.put("Council size", (double) faction.getGovernment().getCouncil().getCurrentSize());
        }
        return values;
    }
}
