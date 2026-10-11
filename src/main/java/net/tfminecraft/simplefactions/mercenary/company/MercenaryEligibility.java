package net.tfminecraft.simplefactions.mercenary.company;

/**
 * Character-trait gate on mercenary work. Factions stay player-based. Founding
 * and joining use the active character's {@code mercenary} trait (from
 * RPCharacters evil-traits). Remaining in a company uses every alive character
 * the player has, including one they are not currently playing.
 *
 * <p>Default probe is open so tests and servers without RPCharacters keep working.
 * Production swaps in {@link RpCharactersMercenaryTraitProbe} when that plugin is
 * present. The company tick treats {@link Status#UNKNOWN} as "leave them alone"
 * and {@link Status#INELIGIBLE} as no living mercenary.
 */
public final class MercenaryEligibility {

    public enum Status {
        /** Their characters could not be read. The tick does not act on them. */
        UNKNOWN,
        ELIGIBLE,
        INELIGIBLE
    }

    public interface Probe {
        Probe OPEN = player -> Status.ELIGIBLE;

        /** Active character, for founding and joining. */
        Status check(String player);

        /**
         * Any alive character with the mercenary trait. Defaults to
         * {@link #check} so a probe that only knows one answer keeps it.
         */
        default Status livingMercenary(String player) {
            return check(player);
        }
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

    /**
     * Whether {@code player} has an alive character with the mercenary trait.
     * {@link Status#UNKNOWN} means the characters could not be read.
     */
    public static Status livingMercenary(String player) {
        if (player == null || player.isBlank()) return Status.INELIGIBLE;
        return probe.livingMercenary(player);
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
