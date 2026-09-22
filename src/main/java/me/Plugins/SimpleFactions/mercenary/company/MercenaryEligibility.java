package me.Plugins.SimpleFactions.mercenary.company;

/**
 * Character-trait gate on mercenary work. Factions stay player-based; only the
 * active character's {@code mercenary} trait (from RPCharacters evil-traits)
 * decides whether someone may found, join, or remain in a company.
 *
 * <p>Default probe is open so tests and servers without RPCharacters keep working.
 * Production swaps in {@link RpCharactersMercenaryTraitProbe} when that plugin is
 * present. The company tick treats {@link Status#UNKNOWN} as "leave them alone"
 * (offline, or no character plugin) and {@link Status#INELIGIBLE} as kick or
 * disband.
 */
public final class MercenaryEligibility {

    public enum Status {
        /** Offline, or RPCharacters is not loaded. The tick does not act. */
        UNKNOWN,
        ELIGIBLE,
        INELIGIBLE
    }

    public interface Probe {
        Probe OPEN = player -> Status.ELIGIBLE;

        Status check(String player);
    }

    private static Probe probe = Probe.OPEN;

    private MercenaryEligibility() {
    }

    public static void setProbe(Probe newProbe) {
        probe = newProbe == null ? Probe.OPEN : newProbe;
    }

    public static void reset() {
        probe = Probe.OPEN;
    }

    public static Status check(String player) {
        if (player == null || player.isBlank()) return Status.INELIGIBLE;
        return probe.check(player);
    }

    /** False while the open probe is installed, so tests and unconfigured servers skip the tick. */
    public static boolean isEnforced() {
        return probe != Probe.OPEN;
    }

    /** Whether a player may found a mercenary company. */
    public static boolean canCreate(String player) {
        return check(player) != Status.INELIGIBLE;
    }

    /** Whether a player may enlist in a mercenary company. */
    public static boolean canJoin(String player) {
        return check(player) != Status.INELIGIBLE;
    }
}
