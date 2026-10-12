package net.tfminecraft.simplefactions.mercenary.company;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterStatus;

/**
 * Character-trait gate on mercenary work. Every question is about one
 * character, named by its member key: founding, joining and staying in a
 * company all need that character's own {@code mercenary} trait. Another
 * character on the same account does not count.
 *
 * <p>The default probe is open for standalone callers and tests. Production uses
 * {@link #useSharedIdentity()} to query the current character provider on every call.
 * The company tick treats {@link Status#UNKNOWN} as "leave them alone"
 * and {@link Status#INELIGIBLE} as not a living mercenary.
 */
public final class MercenaryEligibility {

    public enum Status {
        /** The character could not be read. The tick does not act on it. */
        UNKNOWN,
        ELIGIBLE,
        INELIGIBLE
    }

    public interface Probe {
        Probe OPEN = member -> Status.ELIGIBLE;

        /** Whether the character with this member key is a living mercenary. */
        Status check(String member);

        /** Kept for the company tick; defaults to {@link #check}. */
        default Status livingMercenary(String member) {
            return check(member);
        }
    }

    private static final String TRAIT_ID = "mercenary";
    private static final Probe SHARED_IDENTITY = new SharedIdentityProbe();
    private static Probe probe = Probe.OPEN;

    private MercenaryEligibility() {
    }

    public static void setProbe(Probe newProbe) {
        probe = newProbe == null ? Probe.OPEN : newProbe;
    }

    public static void useSharedIdentity() {
        probe = SHARED_IDENTITY;
    }

    public static void reset() {
        probe = Probe.OPEN;
    }

    /** A null or blank key is a player without a character, who may not do mercenary work. */
    public static Status check(String member) {
        if (member == null || member.isBlank()) return Status.INELIGIBLE;
        return probe.check(member);
    }

    /**
     * Whether the character with this key is alive and has the mercenary trait.
     * {@link Status#UNKNOWN} means the character could not be read.
     */
    public static Status livingMercenary(String member) {
        if (member == null || member.isBlank()) return Status.INELIGIBLE;
        return probe.livingMercenary(member);
    }

    /** False while the open probe is installed, so tests and unconfigured servers skip the tick. */
    public static boolean isEnforced() {
        return probe != Probe.OPEN;
    }

    /** Whether a character may found a mercenary company. */
    public static boolean canCreate(String member) {
        return check(member) != Status.INELIGIBLE;
    }

    /** Whether a character may enlist in a mercenary company. */
    public static boolean canJoin(String member) {
        return check(member) != Status.INELIGIBLE;
    }

    private static final class SharedIdentityProbe implements Probe {
        @Override
        public Status check(String member) {
            CharacterInfo character = Members.info(member).orElse(null);
            if (!readable(character)) return Status.UNKNOWN;
            return character.status() == CharacterStatus.ALIVE && character.traits().contains(TRAIT_ID)
                    ? Status.ELIGIBLE : Status.INELIGIBLE;
        }

        private static boolean readable(CharacterInfo character) {
            if (character == null || character.status() == null || character.traits() == null) return false;
            for (String trait : character.traits()) {
                if (trait == null) return false;
            }
            return true;
        }
    }
}
