package net.tfminecraft.simplefactions.integration.rpcharacters;

import java.util.UUID;

import org.bukkit.Bukkit;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.identity.Members;

/**
 * The heir a dead character's will names, read from its saved RPCharacters file ({@code will-heir}).
 * A will cannot change after its testator dies, so the file holds the will made while alive.
 */
public final class WillHeirs {
    private WillHeirs() {}

    /** The heir's member key; null without an heir, without RPCharacters, or when the file cannot be read. */
    public static String heir(String deadKey) {
        UUID character = Members.characterId(deadKey);
        UUID owner = Members.accountId(deadKey).orElse(null);
        if (character == null || owner == null) return null;
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) return null;
            return Saved.heir(owner, character);
        } catch (RuntimeException | LinkageError unreadable) {
            return null;
        }
    }

    /** The heir named in a character file's text, as a member key, or null. */
    static String parse(String json) {
        if (json == null) return null;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonElement heir = root.getAsJsonObject().get("will-heir");
            if (heir == null || !heir.isJsonPrimitive() || !heir.getAsJsonPrimitive().isString()) return null;
            return Members.key(UUID.fromString(heir.getAsString()));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Touches RPCharacters' types; loaded only once the plugin is known to be enabled. */
    private static final class Saved {
        private Saved() {}

        static String heir(UUID owner, UUID character) {
            var account = net.tfminecraft.rpcharacters.database.Database.readSavedAccount(owner);
            if (account == null) return null;
            String file = character + ".json";
            for (var saved : account.characters()) {
                if (saved.file().getName().equalsIgnoreCase(file)) return parse(saved.json());
            }
            return null;
        }
    }
}
