package net.tfminecraft.simplefactions.plots;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Plot settings from the plots section of special-positions.yml. Invalid values fall back to the defaults. */
public final class PlotConfig {
    static final long DAY = 86_400_000L;
    private static final String ROOT = "plots.";
    private static final YamlConfiguration BUNDLED = YamlConfiguration.loadConfiguration(new InputStreamReader(
            PlotConfig.class.getResourceAsStream("/special-positions.yml"), StandardCharsets.UTF_8));
    private static ConfigurationSection source = BUNDLED;
    private static final Map<PlotApproach, int[]> approaches = new EnumMap<>(PlotApproach.class);
    private static final Map<PlotRole, Integer> difficulty = new EnumMap<>(PlotRole.class);
    private static String staffPermission;
    private static double requestExpiryDays, recruitingDays, retargetDays, inviteRadius, inviteMinutes,
            approachHours, defenderHours, aloneShare, helperShare, buildDays, luckMin, luckMax, progressPerCollaborator,
            masterStrength, assassinStrength, strengthCollaborator, otherCollaborator, requiredBase,
            requiredPerProtection, dailyLoss, lossPerMember, hidePerCollaborator, minimumLoss, leakLoss,
            revealMembers, revealProgress, revealAssassins, earlyProgress, lateProgress, betrayalSpeed, investigationDays,
            radius, huntAlertSecrecy, readyDays, findableMinutes, maxDays;
    private static int spymasterPenalty, helperPenalty, scanSeconds, maxInvestigators, knownBonus, unknownPenalty,
            strongMargin, hitWindowSeconds, maxAssassins, maxCollaborators, noticeLimit, huntWarmupSeconds, huntMinimumMinutes,
            huntDays, maxDepth, areaIntervalSeconds, areaSize;
    private static java.util.List<String> worlds = java.util.List.of();

    static { load(BUNDLED); }

    private PlotConfig() {}

    public static void load(ConfigurationSection config) {
        source = config;
        String permission = config.getString(ROOT + "staff-permission", "");
        staffPermission = permission == null || permission.isBlank() ? "simplefactions.plots.staff" : permission.strip();
        requestExpiryDays = number("request-expiry-days", 7, 0, 3650);
        recruitingDays = number("recruiting-days", 7, 0, 3650);
        retargetDays = number("retarget-cooldown-days", 7, 0, 3650);
        inviteRadius = number("invite-radius", 8, 1, 256);
        inviteMinutes = number("invite-minutes", 10, 1, 10_080);
        maxAssassins = (int) number("roles.max-assassins", 2, 1, 20);
        maxCollaborators = (int) number("roles.max-collaborators", 2, 0, 20);
        noticeLimit = (int) number("notice-limit", 30, 1, 1000);
        approachHours = number("approach.change-cooldown-hours", 24, 0, 87_600);
        for (PlotApproach approach : PlotApproach.values()) {
            String key = "approach." + approach.key() + ".";
            approaches.put(approach, new int[]{(int) number(key + "offense", approach.defaultOffense(), -100, 100),
                    (int) number(key + "defense", approach.defaultDefense(), -100, 100)});
        }
        aloneShare = number("defense.alone-share", .5, 0, 1);
        helperShare = number("defense.helper-share", .75, 0, 1);
        defenderHours = number("defense.change-cooldown-hours", 24, 0, 87_600);
        buildDays = number("building.days", 7, .01, 3650);
        readyDays = number("building.ready-days", 7, .01, 3650);
        luckMin = number("building.luck-min", .5, 0, 10);
        luckMax = Math.max(luckMin, number("building.luck-max", 1.5, 0, 10));
        progressPerCollaborator = number("building.progress-per-collaborator", .5, 0, 10);
        masterStrength = number("building.strength.master", .1, 0, 10);
        assassinStrength = number("building.strength.assassin", .06, 0, 10);
        strengthCollaborator = number("building.strength.strength-collaborator", .15, 0, 10);
        otherCollaborator = number("building.strength.other-collaborator", .03, 0, 10);
        requiredBase = number("building.strength.required-base", 10, 0, 10_000);
        requiredPerProtection = number("building.strength.required-per-protection", 1, 0, 100);
        dailyLoss = number("building.secrecy.daily-loss", 4, 0, 100);
        lossPerMember = number("building.secrecy.loss-per-member", 2, 0, 100);
        hidePerCollaborator = number("building.secrecy.hide-per-collaborator", 6, 0, 100);
        minimumLoss = number("building.secrecy.minimum-loss", 1, 0, 100);
        leakLoss = number("building.secrecy.leak-loss", 3, 0, 100);
        revealMembers = number("building.reveal.member-count", 60, 0, 100);
        revealProgress = number("building.reveal.progress", 40, 0, 100);
        revealAssassins = number("building.reveal.assassins", 20, 0, 100);
        earlyProgress = number("building.reveal.early-progress", 34, 0, 100);
        lateProgress = Math.max(earlyProgress, number("building.reveal.late-progress", 67, 0, 100));
        betrayalSpeed = number("betrayal.speed-multiplier", 2, 1, 100);
        spymasterPenalty = (int) number("betrayal.spymaster-investigation-penalty", 40, 0, 1000);
        helperPenalty = (int) number("betrayal.helper-investigation-penalty", 20, 0, 1000);
        investigationDays = number("investigation.days", 3, 0, 3650);
        radius = number("investigation.radius", 6, 1, 256);
        scanSeconds = (int) number("investigation.scan-seconds", 5, 1, 3600);
        maxInvestigators = (int) number("investigation.max-investigators", 2, 0, 10);
        knownBonus = (int) number("investigation.known-bonus", 15, 0, 1000);
        unknownPenalty = (int) number("investigation.unknown-penalty", 15, 0, 1000);
        strongMargin = (int) number("investigation.strong-margin", 40, 0, 10_000);
        for (PlotRole role : PlotRole.values())
            difficulty.put(role, (int) number("investigation.difficulty." + role.key(), role.defaultDifficulty(), -1000, 1000));
        huntDays = (int) number("execution.hunt-days", 4, 1, 365);
        findableMinutes = number("execution.findable-minutes", 30, 0, 1440);
        maxDays = number("execution.max-days", 21, .01, 3650);
        maxDepth = (int) number("execution.findable.max-depth", 16, 0, 4096);
        worlds = java.util.List.copyOf(source.getStringList(ROOT + "execution.findable.worlds"));
        areaIntervalSeconds = (int) number("execution.area.interval-seconds", 120, 0, 86_400);
        areaSize = (int) number("execution.area.size", 250, 1, 100_000);
        hitWindowSeconds = (int) number("execution.hit-window-seconds", 120, 0, 86_400);
        huntWarmupSeconds = (int) number("execution.hunt.warmup-seconds", 60, 0, 86_400);
        huntMinimumMinutes = (int) number("execution.hunt.minimum-minutes", 10, 0, 10_080);
        huntAlertSecrecy = number("execution.hunt.alert-secrecy", 40, 0, 100);
    }

    private static double number(String key, double fallback, double min, double max) {
        double value = source.getDouble(ROOT + key, fallback);
        return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
    }

    /** A hint for an investigation roll; {name} is the conspirator's character name. */
    public static String hint(PlotRole role, boolean strong, String name) {
        String key = ROOT + "investigation.hints." + role.key() + "." + (strong ? "strong" : "weak");
        String text = source.getString(key);
        if (text == null) text = BUNDLED.getString(key, "&7&o{name} is acting strangely.");
        return text.replace("{name}", name);
    }

    public static String staffPermission() { return staffPermission; }
    public static long requestExpiry() { return millis(requestExpiryDays); }
    public static long recruiting() { return millis(recruitingDays); }
    public static long retargetCooldown() { return millis(retargetDays); }
    public static double inviteRadius() { return inviteRadius; }
    public static int maxAssassins() { return maxAssassins; }
    public static int maxCollaborators() { return maxCollaborators; }
    public static int maxMembers() { return maxAssassins + maxCollaborators; }
    public static int noticeLimit() { return noticeLimit; }
    public static long inviteDuration() { return Math.round(inviteMinutes * 60_000); }
    public static long approachCooldown() { return Math.round(approachHours * 3_600_000); }
    public static long defenderCooldown() { return Math.round(defenderHours * 3_600_000); }
    public static int offenseBonus(PlotApproach approach) { return approaches.get(approach)[0]; }
    public static int defenseBonus(PlotApproach approach) { return approaches.get(approach)[1]; }
    public static double aloneShare() { return aloneShare; }
    public static double helperShare() { return helperShare; }
    public static double buildDays() { return buildDays; }
    public static double luckMin() { return luckMin; }
    public static double luckMax() { return luckMax; }
    public static double progressPerCollaborator() { return progressPerCollaborator; }
    public static double strengthWeight(PlotRole role, CollaboratorTask task) {
        return switch (role) {
            case MASTER -> masterStrength;
            case ASSASSIN -> assassinStrength;
            case COLLABORATOR -> task == CollaboratorTask.STRENGTH ? strengthCollaborator : otherCollaborator;
            default -> 0;
        };
    }
    public static double requiredBase() { return requiredBase; }
    public static double requiredPerProtection() { return requiredPerProtection; }
    public static double dailyLoss() { return dailyLoss; }
    public static double lossPerMember() { return lossPerMember; }
    public static double hidePerCollaborator() { return hidePerCollaborator; }
    public static double minimumLoss() { return minimumLoss; }
    public static double leakLoss() { return leakLoss; }
    public static double revealMembers() { return revealMembers; }
    public static double revealProgress() { return revealProgress; }
    public static double revealAssassins() { return revealAssassins; }
    public static double earlyProgress() { return earlyProgress; }
    public static double lateProgress() { return lateProgress; }
    public static double betrayalSpeed() { return betrayalSpeed; }
    public static int betrayalPenalty(Betrayal betrayal) {
        return betrayal == Betrayal.SPYMASTER ? spymasterPenalty : betrayal == Betrayal.HELPER ? helperPenalty : 0;
    }
    public static long investigation() { return millis(investigationDays); }
    public static double radius() { return radius; }
    public static int scanSeconds() { return scanSeconds; }
    public static int maxInvestigators() { return maxInvestigators; }
    public static int knownBonus() { return knownBonus; }
    public static int unknownPenalty() { return unknownPenalty; }
    public static int strongMargin() { return strongMargin; }
    public static int difficulty(PlotRole role) { return difficulty.get(role); }
    public static long ready() { return millis(readyDays); }
    public static int huntDays() { return huntDays; }
    public static long findable() { return Math.round(findableMinutes * 60_000); }
    public static long maxHunt() { return millis(maxDays); }
    public static int maxDepth() { return maxDepth; }
    /** Worlds where a target can be found; empty means every world. */
    public static java.util.List<String> worlds() { return worlds; }
    public static long areaInterval() { return areaIntervalSeconds * 1000L; }
    public static int areaSize() { return areaSize; }
    public static long hitWindow() { return hitWindowSeconds * 1000L; }
    public static long huntWarmup() { return huntWarmupSeconds * 1000L; }
    /** From the start of the hunt, including the warm-up. */
    public static long huntMinimum() { return huntWarmup() + huntMinimumMinutes * 60_000L; }
    public static double huntAlertSecrecy() { return huntAlertSecrecy; }

    private static long millis(double days) { return Math.round(days * DAY); }
}
