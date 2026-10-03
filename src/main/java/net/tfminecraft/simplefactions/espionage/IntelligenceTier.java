package net.tfminecraft.simplefactions.espionage;

import java.util.Locale;

/** Stable identifiers for saved reports and configurable disclosure rules. */
public enum IntelligenceTier {
    UNKNOWN("Unknown"), RUMOURS("Rumours"), BROAD("Broad estimates"),
    RELIABLE("Reliable estimates"), DETAILED("Detailed estimates");

    private final String label;
    IntelligenceTier(String label) { this.label = label; }
    public String label() { return label; }
    public String key() { return name().toLowerCase(Locale.ROOT); }
    public static IntelligenceTier parse(String value) {
        for (var tier : values())
            if (tier.name().equalsIgnoreCase(value) || tier.label.equalsIgnoreCase(value)) return tier;
        return UNKNOWN; // Invalid or legacy Hidden values never disclose information.
    }
}
