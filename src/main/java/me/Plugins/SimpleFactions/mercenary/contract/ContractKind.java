package me.Plugins.SimpleFactions.mercenary.contract;

/**
 * What a contract buys. The discriminator exists from the first version so the
 * assassin program could reuse this object instead of copying it.
 *
 * <p>Assassins have since been dropped from the roadmap, so {@link #ASSASSIN} is
 * vestigial: nothing constructs it and no code path branches on it. It is kept
 * because saved contracts persist {@code kind} by name, and because the seam costs
 * nothing if hired violence ever returns.
 */
public enum ContractKind {
    MERCENARY("Mercenary Contract"),
    ASSASSIN("Assassination Contract");

    private final String display;

    ContractKind(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
