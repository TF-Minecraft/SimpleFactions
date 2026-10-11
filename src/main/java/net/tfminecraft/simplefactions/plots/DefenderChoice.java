package net.tfminecraft.simplefactions.plots;

/** Who guards a character against plots. */
public enum DefenderChoice {
    SELF("Yourself"),
    CHARACTER("A trusted character"),
    SPYMASTER("Your Spymaster");

    private final String label;

    DefenderChoice(String label) { this.label = label; }

    public String label() { return label; }
}
