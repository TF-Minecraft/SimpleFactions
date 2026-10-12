package net.tfminecraft.simplefactions.mercenary.company;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Character-trait gate on mercenary work. Factions stay player-based. Founding
 * and joining use the active character's {@code mercenary} trait. Remaining in a
 * company uses every alive character the player has, including one they are not
 * currently playing.
 *
 * <p>The default probe is open for standalone callers and tests. Production uses
 * {@link #useSharedIdentity()} to query the current character provider on every call.
 * The company tick treats {@link Status#UNKNOWN} as "leave them alone"
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

    private static final class SharedIdentityProbe implements Probe {
        @Override
        public Status check(String player) {
            if (Bukkit.getServer() == null || !Characters.available()) return Status.UNKNOWN;
            Player online = Bukkit.getPlayerExact(player);
            if (online == null) return Status.UNKNOWN;

            CharacterSession session = Characters.currentSession(online).orElse(null);
            if (session == null || !online.getUniqueId().equals(session.ref().ownerId())) {
                return Status.INELIGIBLE;
            }
            CharacterInfo character = Characters.info(session.ref().characterId()).orElse(null);
            if (!readable(character, online.getUniqueId())
                    || !session.ref().equals(character.ref()) || !livingMercenary(character)) {
                return Status.INELIGIBLE;
            }
            return online.isOnline() && Bukkit.getPlayer(online.getUniqueId()) == online
                    && Characters.isCurrent(session) ? Status.ELIGIBLE : Status.INELIGIBLE;
        }

        @Override
        public Status livingMercenary(String player) {
            if (Bukkit.getServer() == null || !Characters.available()) return Status.UNKNOWN;
            Player online = Bukkit.getPlayerExact(player);
            OfflinePlayer account = online != null ? online : Bukkit.getOfflinePlayerIfCached(player);
            if (account == null || account.getUniqueId() == null) return Status.UNKNOWN;

            UUID ownerId = account.getUniqueId();
            List<CharacterInfo> characters = Characters.characters(ownerId).orElse(null);
            if (characters == null || !Characters.available()) return Status.UNKNOWN;
            boolean unreadable = false;
            for (CharacterInfo character : characters) {
                if (!readable(character, ownerId)) {
                    unreadable = true;
                } else if (livingMercenary(character)) {
                    return Status.ELIGIBLE;
                }
            }
            return unreadable ? Status.UNKNOWN : Status.INELIGIBLE;
        }

        private static boolean readable(CharacterInfo character, UUID ownerId) {
            if (character == null || character.ref() == null
                    || !ownerId.equals(character.ref().ownerId()) || character.ref().characterId() == null
                    || character.status() == null || character.traits() == null) {
                return false;
            }
            for (String trait : character.traits()) {
                if (trait == null) return false;
            }
            return true;
        }

        private static boolean livingMercenary(CharacterInfo character) {
            return character.status() == CharacterStatus.ALIVE && character.traits().contains(TRAIT_ID);
        }
    }
}
