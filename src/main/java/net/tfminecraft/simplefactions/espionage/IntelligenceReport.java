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
    public Map<String, EspionageMath.Estimate> estimates = new LinkedHashMap<>();
    public java.util.List<String> members = new java.util.ArrayList<>();
    public Map<String, java.util.List<String>> guildMembers = new LinkedHashMap<>();

    public String loreDate() {
        return net.tfminecraft.rpcharacters.calendar.FantasyCalendar.formatDate(java.time.LocalDate.ofEpochDay(day)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public String display(String metric) {
        EspionageMath.Estimate estimate = estimate(metric);
        return estimate == null ? UNKNOWN : estimate.display();
    }

    public EspionageMath.Estimate estimate(String metric) {
        return !EspionageConfig.allows(tier(), metric) ? null
                : IntelligenceRanges.reasonable(metric, estimates == null ? null : estimates.get(metric), tier());
    }

    public String qualityLabel() {
        return tier().label();
    }

    public IntelligenceTier tier() { return IntelligenceTier.parse(quality); }
    public boolean allows(String field) { return EspionageConfig.allows(tier(), field); }
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
