package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * VehicleFramework owner values, kept free of VehicleFramework types so an older
 * VehicleFramework never has to load them.
 */
public final class OwnerTags {
    public static final String NONE = "none";
    public static final String NO_CHARACTER = "char_none";
    public static final String CHARACTER_PREFIX = "char_";
    public static final String LEGACY_PREFIX = "player_";

    private OwnerTags() {}

    public static String character(UUID characterId) {
        return characterId == null ? NO_CHARACTER : CHARACTER_PREFIX + characterId;
    }

    /** The character of a {@code char_<uuid>} value; empty for anything else, {@code char_none} included. */
    public static Optional<UUID> characterId(String owner) {
        if (owner == null || !owner.regionMatches(true, 0, CHARACTER_PREFIX, 0, CHARACTER_PREFIX.length())) {
            return Optional.empty();
        }
        String id = owner.substring(CHARACTER_PREFIX.length());
        if (id.length() != 36) return Optional.empty();
        try {
            UUID parsed = UUID.fromString(id);
            return parsed.toString().equals(id.toLowerCase(Locale.ROOT)) ? Optional.of(parsed) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isUnowned(String owner) {
        return owner == null || owner.isBlank() || owner.equalsIgnoreCase(NONE);
    }

    public static boolean isCharacter(String owner) {
        return characterId(owner).isPresent();
    }

    /** Whether the value names this character. */
    public static boolean names(String owner, UUID characterId) {
        return characterId != null && characterId.equals(characterId(owner).orElse(null));
    }

    /** The account name of a legacy {@code player_<name>} value, or null ({@code player_none} included). */
    public static String legacyName(String owner) {
        if (owner == null || owner.length() <= LEGACY_PREFIX.length()
                || !owner.regionMatches(true, 0, LEGACY_PREFIX, 0, LEGACY_PREFIX.length())
                || owner.equalsIgnoreCase(LEGACY_PREFIX + NONE)) {
            return null;
        }
        return owner.substring(LEGACY_PREFIX.length());
    }

    /** VehicleFramework's stored form of a character whitelist entry. */
    public static String whitelistEntry(CharacterRef character, String name) {
        return character(character.characterId()) + ";" + character.ownerId() + ";" + (name == null ? "" : name);
    }

    /** Whether a raw whitelist entry is already in character form. */
    public static boolean isCharacterEntry(String raw) {
        if (raw == null) return false;
        int end = raw.indexOf(';');
        return end > 0 && isCharacter(raw.substring(0, end));
    }
}
