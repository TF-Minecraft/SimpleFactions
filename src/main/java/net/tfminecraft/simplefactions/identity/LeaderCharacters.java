package net.tfminecraft.simplefactions.identity;

import java.util.Objects;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * The roleplay name of a realm's leader, for the web map.
 *
 * A faction stores its leader as a Minecraft username, which is not what the
 * map should show: players are known by their character there. RPCharacters
 * only knows a player's active character while they are online, so the name
 * is read whenever the leader is seen online and remembered with the player it
 * belongs to. A remembered name is dropped as soon as the leader changes, so a
 * new leader never inherits the old one's character.
 *
 * Production swaps in {@link RpCharactersLeaderCharacterProbe} when that
 * plugin is present; without it nothing is ever known, remembered names are
 * dropped too, and the map shows no ruler name.
 */
public final class LeaderCharacters {

    /** Active character name of an online player, or null if unknown. */
    public interface Probe {
        String activeCharacterName(String player);

        /** False while RPCharacters is missing, so remembered names are not kept either. */
        default boolean available() {
            return true;
        }
    }

    /** A remembered character name and the player it belongs to. */
    public record Remembered(String name, String player) {
        public static final Remembered NONE = new Remembered(null, null);
    }

    private static volatile Probe probe;

    private LeaderCharacters() {}

    public static void setProbe(Probe next) {
        probe = next;
    }

    public static void reset() {
        probe = null;
    }

    /**
     * What to remember for `leader` now: their active character if they are
     * online with one, else what was remembered for this same leader, else
     * nothing. Without RPCharacters it is always nothing.
     */
    public static Remembered resolve(String leader, String rememberedName, String rememberedFor) {
        if (leader == null || leader.isBlank()) return Remembered.NONE;
        Probe current = probe;
        if (current == null || !current.available()) return Remembered.NONE;
        String active = clean(current.activeCharacterName(leader));
        if (active != null) return new Remembered(active, leader);
        if (rememberedName != null && leader.equalsIgnoreCase(rememberedFor)) {
            String kept = clean(rememberedName);
            if (kept != null) return new Remembered(kept, leader);
        }
        return Remembered.NONE;
    }

    /** Re-reads the realm leader's character onto `faction`; true if the name changed. */
    public static boolean refresh(Faction faction) {
        Remembered remembered = resolve(
                faction.getLeader(), faction.getLeaderCharacter(), faction.getLeaderCharacterOf());
        boolean changed = !Objects.equals(remembered.name(), faction.getLeaderCharacter());
        faction.rememberLeaderCharacter(remembered.name(), remembered.player());
        return changed;
    }

    /**
     * Re-reads the guild leader's character onto `guild`; true if the name
     * changed. A realm's own guild shares the realm's, so it is left alone.
     */
    public static boolean refresh(Guild guild) {
        if (guild.isBase()) return false;
        Remembered remembered = resolve(
                guild.getLeader(), guild.getLeaderCharacter(), guild.getLeaderCharacterOf());
        boolean changed = !Objects.equals(remembered.name(), guild.getLeaderCharacter());
        guild.rememberLeaderCharacter(remembered.name(), remembered.player());
        return changed;
    }

    /** Strips Minecraft colour codes and blank names. */
    static String clean(String name) {
        if (name == null) return null;
        String stripped = name.replaceAll("(?i)§[0-9A-FK-ORX]", "").replace("§", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
