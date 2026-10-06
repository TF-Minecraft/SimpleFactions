package net.tfminecraft.simplefactions.espionage;

import java.util.LinkedHashMap;
import java.util.Map;

/** A snapshot, shared by every member of the observing faction. */
public class IntelligenceReport {
    public static final String UNKNOWN = "Unknown";
    public Map<SpecialPosition, String> officeHolders = new LinkedHashMap<>();
    public long day;
    public long targetFoundedAt;
    public String quality;
    /** Highest tier the target's Spymaster opened to this observer; fields at or below it are exact. */
    public String shared;
    public Map<String, EspionageMath.Estimate> estimates = new LinkedHashMap<>();
    public java.util.List<String> members = new java.util.ArrayList<>();
    public Map<String, java.util.List<String>> guildMembers = new LinkedHashMap<>();
    public java.util.List<RosterMember> roster = new java.util.ArrayList<>();
    public Map<String, java.util.List<String>> details = new LinkedHashMap<>();
    public Map<String, Long> maximums = new LinkedHashMap<>();

    public record RosterMember(String character, String guildId, String guildName, boolean sampled, boolean guildLeader,
                               java.util.List<SpecialPosition> offices) {}

    /** Detail lists use the same disclosure gates as ranged metrics, including after reload. */
    public java.util.List<String> details(String field, String key) {
        return !allows(field) || details == null ? java.util.List.of() : details.getOrDefault(key, java.util.List.of());
    }

    public String loreDate() {
        return net.tfminecraft.rpcharacters.calendar.FantasyCalendar.formatDate(java.time.LocalDate.ofEpochDay(day)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public String display(String metric) {
        EspionageMath.Estimate estimate = estimate(metric);
        return estimate == null ? UNKNOWN : estimate.display();
    }

    public EspionageMath.Estimate estimate(String metric) {
        var raw = estimates == null ? null : estimates.get(metric);
        // A field that left the shared tiers after a reload falls back to the range rules, which reject exact values.
        if (exact(metric) && raw != null && raw.isExact()) return raw;
        if (!EspionageConfig.allows(tier(), metric)) return null;
        Long maximum = maximums == null ? null : maximums.get(metric);
        if (raw != null && maximum != null) {
            raw = new EspionageMath.Estimate(Math.max(0, raw.lower()), Math.min(maximum, raw.upper()));
            if (raw.upper() - (double) raw.lower() > maximum * EspionageConfig.settings(tier()).boundedFraction()) return null;
        }
        return IntelligenceRanges.reasonable(metric, raw, tier());
    }

    public String qualityLabel() {
        return tier().label();
    }

    public IntelligenceTier tier() { return IntelligenceTier.parse(quality); }
    public IntelligenceTier sharedTier() { return shared == null ? IntelligenceTier.UNKNOWN : IntelligenceTier.parse(shared); }
    /** Turning sharing off hides shared values in today's reports too. */
    public boolean exact(String field) {
        return EspionageConfig.sharingAllowed() && EspionageConfig.allows(sharedTier(), field);
    }
    public boolean allows(String field) { return EspionageConfig.allows(tier(), field) || exact(field); }
    public String officeHolder(SpecialPosition office) {
        return !allows("office-holder") || officeHolders == null ? UNKNOWN : officeHolders.getOrDefault(office, UNKNOWN);
    }
    public static String officeAptitudeKey(SpecialPosition office) { return "Position:" + office.name() + ":Aptitude"; }

    public static String stabilityState(IntelligenceReport report) {
        var range = report == null ? null : report.estimate("Stability");
        if (range == null) return "\u00a77Unknown State";
        var state = net.tfminecraft.simplefactions.government.stability.StabilityStatus.fromStability(range.midpoint());
        return state.getListColor() + state.getLabel();
    }

    public static String stabilityRange(IntelligenceReport report) {
        var range = report == null ? null : report.estimate("Stability");
        if (range == null) return "\u00a77" + UNKNOWN;
        var state = net.tfminecraft.simplefactions.government.stability.StabilityStatus.fromStability(range.midpoint());
        return state.getListColor() + range.display() + "%";
    }
}
