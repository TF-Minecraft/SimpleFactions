package net.tfminecraft.simplefactions.espionage;

/** Offices are independent of membership, guild leadership, and council seats. */
public enum SpecialPosition {
    SPYMASTER("Spymaster");

    private final String label;
    SpecialPosition(String label) { this.label = label; }
    public String label() { return label; }
}
