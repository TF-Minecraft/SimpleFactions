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
        if (!faction.isMemberIgnoreCase(playerName) && !faction.isLeader(playerName)) return false;
        return !faction.isLeader(playerName) || faction.getMembers().stream()
                .allMatch(name -> name.equalsIgnoreCase(playerName));
    }

    /** Only new factions pass through addFaction; loading saves never reassigns offices. */
    public static void initializeFounder(Faction faction) {
        initializeFounder(faction, null);
    }

    private static void initializeFounder(Faction faction, java.util.Set<Faction> dirty) {
        if (faction.getEspionage() == null || org.bukkit.Bukkit.getServer() == null) return;
        var founder = org.bukkit.Bukkit.getPlayerExact(faction.getLeader());
        String characterId = OfficeCharacters.activeCharacterId(founder);
        for (SpecialPosition office : SpecialPosition.values()) {
            if (faction.getEspionage().holder(office) != null) continue;
            if (office == SpecialPosition.SPYMASTER && !eligible(faction, faction.getLeader())) continue;
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

    public static double appointmentCost(Faction faction) {
        return faction.getEspionage().appointmentCount(SpecialPosition.SPYMASTER) == 0 ? 0 : EspionageConfig.repeatCost();
    }

    static boolean canAffordAppointment(Faction faction) {
        double cost = appointmentCost(faction);
        if (cost == 0) return true;
        return faction.getBank() != null && Double.isFinite(faction.getBank().getWealth()) && faction.getBank().getWealth() >= cost;
    }

    static boolean completeAppointment(Faction faction, SpecialPositionAssignment assignment, int aptitude) {
        if (!canAffordAppointment(faction)) return false;
        boolean repeat = faction.getEspionage().appointmentCount(SpecialPosition.SPYMASTER) > 0;
        double cost = appointmentCost(faction);
        if (cost > 0) faction.getBank().withdraw(cost);
        faction.getEspionage().appoint(assignment, aptitude);
        if (repeat) faction.getEspionage().addUnrest(SpecialPosition.SPYMASTER,
                EspionageConfig.stabilityPenalty(), EspionageConfig.penaltyDays(), System.currentTimeMillis());
        return true;
    }

    static boolean completeAndSaveAppointment(Faction faction, SpecialPositionAssignment assignment, int aptitude) {
        var previous = faction.getEspionage().snapshotOffices();
        double cost = appointmentCost(faction);
        if (!completeAppointment(faction, assignment, aptitude)) return false;
        if (new Database().saveFactionChecked(faction)) return true;
        faction.getEspionage().restoreOffices(previous);
        if (cost > 0) faction.getBank().deposit(cost);
        return false;
    }

    public static int effectiveAptitude(Faction faction, SpecialPositionAssignment holder) {
        if (holder == null || !eligible(faction, holder.playerName)) return 0;
        return faction.isLeader(holder.playerName) ? (int) Math.floor(holder.aptitude * EspionageConfig.soloMultiplier()) : holder.aptitude;
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
        var holder = faction.getEspionage().getSpymaster();
        return holder != null && eligible(faction, holder.playerName) && !deadCharacter(holder);
    }

    public static SpecialPositionAssignment spymaster(Faction faction) {
        return spymaster(faction, null);
    }

    private static void saveOrMark(Faction faction, java.util.Set<Faction> dirty) {
        if (dirty == null) new Database().saveFaction(faction);
        else dirty.add(faction);
    }

    private static SpecialPositionAssignment spymaster(Faction faction, java.util.Set<Faction> dirty) {
        if (faction.getEspionage().hasPendingFounder()) {
            initializeFounder(faction, dirty);
            if (!faction.getEspionage().hasPendingFounder()) saveOrMark(faction, dirty);
        }
        SpecialPositionAssignment holder = faction.getEspionage().getSpymaster();
        if (holder != null && (!eligible(faction, holder.playerName) || deadCharacter(holder))) {
            faction.getEspionage().removeSpymaster();
            saveOrMark(faction, dirty);
            return null;
        }
        return holder;
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
            new Database().saveFaction(faction);
            if (owner != null) owner.sendMessage("\u00a78\u00a7oWith the passing of " + characterName
                    + ", the keys to the Spymaster's office return to the faction. The office awaits a successor.");
        }
    }

    public static java.util.List<net.tfminecraft.simplefactions.government.StabilityModifier> stabilityModifiers(Faction faction, long now) {
        var result = new java.util.ArrayList<>(faction.getEspionage().unrestModifiers(now));
        for (SpecialPosition office : SpecialPosition.values()) {
            var holder = faction.getEspionage().holder(office);
            boolean occupied = holder != null && (office != SpecialPosition.SPYMASTER
                    || eligible(faction, holder.playerName) && !deadCharacter(holder));
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
        if (!eligible(faction, candidate.getName())) {
            actor.sendMessage("§cThe faction leader cannot be Spymaster unless the faction has only one member.");
            return false;
        }
        String characterId = OfficeCharacters.activeCharacterId(candidate);
        if (characterId == null) {
            actor.sendMessage("§cThat member needs an active roleplay character.");
            return false;
        }
        SpecialPositionAssignment current = spymaster(faction);
        if (current != null && !current.automatic && current.isHolder(candidate.getUniqueId())
                && characterId.equals(current.characterId)) {
            actor.sendMessage("§7That member is already your Spymaster.");
            return false;
        }
        if (!canAffordAppointment(faction)) {
            actor.sendMessage("\u00a7cThe faction treasury needs " + appointmentCost(faction) + "d for this appointment.");
            return false;
        }
        double cost = appointmentCost(faction);
        boolean repeat = faction.getEspionage().appointmentCount(SpecialPosition.SPYMASTER) > 0;
        SpecialPositionAssignment assignment = new SpecialPositionAssignment();
        assignment.playerId = candidate.getUniqueId();
        assignment.playerName = candidate.getName();
        assignment.characterId = characterId;
        try {
            if (characterAptitudes == null) throw new java.io.IOException("Character aptitude registry is unavailable");
            int aptitude = characterAptitude(candidate, characterId);
            if (!completeAndSaveAppointment(faction, assignment, aptitude)) {
                actor.sendMessage("\u00a7cThe appointment could not be saved. The office and treasury are unchanged.");
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
        candidate.sendMessage("§7Your aptitude for this office is §e" + effectiveAptitude(faction, assignment)
                + "/100§7. Inspect Special Positions in the faction menu, or use §a/faction espionage§7.");
        if (faction.isLeader(candidate.getName()))
            candidate.sendMessage("§7Leading a one-person faction retains " + Math.round(EspionageConfig.soloMultiplier() * 100) + "% aptitude (base: " + assignment.aptitude + ").");
        if (!actor.getUniqueId().equals(candidate.getUniqueId())) actor.sendMessage("§aSpymaster appointed.");
        if (repeat) actor.sendMessage("\u00a77Appointment: " + cost + "d from the treasury. The change of office brings "
                + EspionageConfig.stabilityPenalty() + " points of unrest, fading over " + EspionageConfig.penaltyDays() + " days.");
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
        var previous = faction.getEspionage().snapshotOffices();
        faction.getEspionage().removeSpymaster();
        if (!new Database().saveFactionChecked(faction)) {
            faction.getEspionage().restoreOffices(previous);
            actor.sendMessage("\u00a7cThe office removal could not be saved. The Spymaster remains appointed.");
            return false;
        }
        actor.sendMessage("§aThe Spymaster office is now vacant.");
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
