package net.tfminecraft.simplefactions.espionage;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier;
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
        if (faction.getEspionage() == null || org.bukkit.Bukkit.getServer() == null) return;
        var founder = org.bukkit.Bukkit.getPlayerExact(faction.getLeader());
        var data = founder == null ? null : PlayerManager.get(founder);
        RPCharacter character = data == null ? null : data.getActiveCharacter();
        for (SpecialPosition office : SpecialPosition.values()) {
            if (faction.getEspionage().holder(office) != null) continue;
            if (office == SpecialPosition.SPYMASTER && !eligible(faction, faction.getLeader())) continue;
            var assignment = new SpecialPositionAssignment();
            assignment.playerName = faction.getLeader();
            assignment.playerId = founder == null ? org.bukkit.Bukkit.getOfflinePlayer(faction.getLeader()).getUniqueId() : founder.getUniqueId();
            int aptitude = 0;
            try {
                if (character != null && characterAptitudes != null) {
                    aptitude = characterAptitude(founder, character);
                    assignment.characterId = character.getId();
                }
            } catch (java.io.IOException exception) {
                net.tfminecraft.simplefactions.SimpleFactions.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Could not save founder aptitude", exception);
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
        return target != null && (bypasses(viewer) || isOwn(viewer, target));
    }

    public static SpecialPositionAssignment spymaster(Faction faction) {
        SpecialPositionAssignment holder = faction.getEspionage().getSpymaster();
        if (holder != null && !eligible(faction, holder.playerName)) {
            faction.getEspionage().removeSpymaster();
            new Database().saveFaction(faction);
            return null;
        }
        return holder;
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
        var playerData = PlayerManager.get(candidate);
        RPCharacter character = playerData == null ? null : playerData.getActiveCharacter();
        if (character == null) {
            actor.sendMessage("§cThat member needs an active roleplay character.");
            return false;
        }
        SpecialPositionAssignment current = spymaster(faction);
        if (current != null && !current.automatic && current.isHolder(candidate.getUniqueId())
                && character.getId().equals(current.characterId)) {
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
        assignment.characterId = character.getId();
        try {
            if (characterAptitudes == null) throw new java.io.IOException("Character aptitude registry is unavailable");
            int aptitude = characterAptitude(candidate, character);
            if (!completeAppointment(faction, assignment, aptitude)) return false;
        } catch (java.io.IOException exception) {
            actor.sendMessage("§cThe aptitude could not be saved. The appointment has not been made.");
            net.tfminecraft.simplefactions.SimpleFactions.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not save character aptitude", exception);
            return false;
        }
        new Database().saveFaction(faction);
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

    private static int characterAptitude(Player player, RPCharacter character) throws java.io.IOException {
        if (characterAptitudes == null) throw new java.io.IOException("Character aptitude registry is unavailable");
        return characterAptitudes.aptitude(character.getId(), () -> {
            Map<String, Integer> attributes = new HashMap<>();
            var mmoAttributes = net.Indyuce.mmocore.api.player.PlayerData.get(player).getAttributes();
            for (String attribute : EspionageConfig.DEFAULT_WEIGHTS.keySet()) {
                var instance = mmoAttributes.getInstance(attribute);
                attributes.put(attribute, instance == null
                        ? character.getAttributeData().getAmount(new AttributeModifier(attribute, 0)) : instance.getBase());
            }
            return EspionageMath.aptitude(attributes, ThreadLocalRandom.current());
        });
    }

    public static boolean remove(Player actor, Faction faction) {
        if (!isOwn(actor, faction) || !faction.isLeader(actor.getName())) {
            actor.sendMessage("§cOnly your faction leader can remove the Spymaster.");
            return false;
        }
        faction.getEspionage().removeSpymaster();
        new Database().saveFaction(faction);
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
        if (offense) holder.offenseReduction = reduction;
        else holder.defenseReduction = reduction;
        new Database().saveFaction(faction);
        actor.sendMessage("§7Private " + (offense ? "offensive" : "defensive") + " sabotage: "
                + (reduction == 0 ? "§adisabled" : "§c-" + reduction + " to your roll")
                + "§7. This affects your next daily rolls; existing reports stay unchanged.");
        return true;
    }

    /** Reading or clicking menus never generates intelligence. */
    public static IntelligenceReport report(Player viewer, Faction target) {
        if (viewer == null || canViewExact(viewer, target)) return null;
        Faction observer = FactionManager.getByMember(viewer.getName());
        if (observer == null) return null;
        return observer.getEspionage().cachedReport(target.getId(), target.getFoundedAt(), day());
    }

    private static long day() { return LocalDate.now(ZoneOffset.UTC).toEpochDay(); }

    /** GUI-opening commands request one faction-wide report per foreign target each UTC day. */
    public static void refreshReports(Player viewer) {
        if (viewer == null || bypasses(viewer)) return;
        Faction observer = FactionManager.getByMember(viewer.getName());
        if (observer == null) return;
        boolean updated = false;
        for (Faction target : FactionManager.getCopy()) {
            if (target == observer || target.getId().equals(observer.getId())) continue;
            if (observer.getEspionage().cachedReport(target.getId(), target.getFoundedAt(), day()) == null) {
                generateReport(observer, target);
                updated = true;
            }
        }
        if (updated) viewer.sendMessage("§8§oYour faction's unseen network delivers today's sealed intelligence reports.");
    }

    private static IntelligenceReport generateReport(Faction observer, Faction target) {
        return generateReport(observer, target, true);
    }

    private static IntelligenceReport generateReport(Faction observer, Faction target, boolean persist) {
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        var attacker = spymaster(observer);
        var defender = spymaster(target);
        boolean[] created = {false};
        RandomGenerator random = ThreadLocalRandom.current();
        IntelligenceReport report = observer.getEspionage().report(target.getId(), target.getFoundedAt(), day, () -> {
            created[0] = true;
            int margin = observer.getEspionage().rolls(day, effectiveAptitude(observer, attacker), random).offense()
                    - target.getEspionage().rolls(day, effectiveAptitude(target, defender), random).defense();
            IntelligenceReport generated = createReport(margin <= 0 ? Map.of() : metrics(target), margin, random);
            captureMembers(generated, target, margin, random);
            captureOffices(generated, target, random);
            return generated;
        });
        if (created[0] && persist) {
            Database database = new Database();
            database.saveFaction(target);
            database.saveFaction(observer);
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
        report.members = sample.stream().map(name -> CharacterNames.of(name) + " §7— "
                + net.tfminecraft.simplefactions.utils.Represents.represents(target, name)).toList();
        for (var guild : target.getGuildHandler().getGuilds()) {
            report.guildMembers.put(guild.getId(), sample.stream().filter(guild::isMember)
                    .filter(name -> !guild.isLeader(name)).map(CharacterNames::of).toList());
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
                    holder == null ? "Vacant" : CharacterNames.of(holder.playerName));
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
        for (var observer : factions) {
            for (var target : factions) if (!observer.getId().equals(target.getId())) {
                generateReport(observer, target, false);
                count++;
            }
            // Also persist resets for a lone faction, which has no foreign targets.
            new Database().saveFaction(observer);
        }
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
        for (var guild : faction.getGuildHandler().getGuilds()) {
            values.put("Guild:" + guild.getId() + ":Wealth", guild.getWealth());
            values.put("Guild:" + guild.getId() + ":Members", (double) guild.getMembers().size());
            values.put("Guild:" + guild.getId() + ":Income", guild.getLedger().getNetIncome());
            IntelligenceLedger.capture(values, guild);
            if (guild.hasCapital()) values.put("Guild:" + guild.getId() + ":Trade power", guild.getTradeBreakdown().getTradePower());
        }
        if (faction.getGovernment() != null) {
            values.put("Stability", faction.getGovernment().getStability());
            values.put("Administrative power", faction.getGovernment().getPower());
        }
        return values;
    }
}
