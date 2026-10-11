package net.tfminecraft.simplefactions.mercenary.company;

/**
 * Character-trait gate on mercenary work. Factions stay player-based; the
 * {@code mercenary} trait (from RPCharacters evil-traits) decides whether
 * someone may found, join, or remain in a company.
 *
 * <p>Founding and joining read the active character. A company then remembers
 * the character each leader and mercenary signed up as, so switching to
 * another character leaves the company alone; only that character dying or
 * losing the trait counts.
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

        /** The player's active character. */
        Status check(String player);

        /** One of the player's characters, active or not. */
        default Status checkCharacter(String player, String characterId) {
            return check(player);
        }

        /**
         * ID of the player's living character with the trait, preferring the
         * active one. Null when offline, unknown, or they have none.
         */
        default String mercenaryCharacter(String player) {
            return null;
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
        return check(player, null);
    }

    /** A remembered character, or the active one when none is remembered. */
    public static Status check(String player, String characterId) {
        if (player == null || player.isBlank()) return Status.INELIGIBLE;
        return characterId == null ? probe.check(player) : probe.checkCharacter(player, characterId);
    }

    /** The character a company should remember for this player, or null. */
    public static String mercenaryCharacter(String player) {
        if (player == null || player.isBlank()) return null;
        return probe.mercenaryCharacter(player);
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
