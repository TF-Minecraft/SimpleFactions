package net.tfminecraft.simplefactions.identity;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;

/**
 * A player's active RPCharacters character name.
 *
 * Online, it asks RPCharacters directly. Offline, RPCharacters holds nothing
 * in memory, so this reads the character files it saves, one per character in
 * {@code plugins/RPCharacters/data/characterdata/<uuid>/}, and takes the one
 * marked {@code active}. Players the server has never cached a UUID for are
 * unknown; no lookup ever goes to Mojang.
 */
public final class RpCharactersLeaderCharacterProbe implements LeaderCharacters.Probe {
    static final File CHARACTER_ROOT = new File("plugins/RPCharacters/data/characterdata");

    @Override
    public boolean available() {
        return Bukkit.getServer() != null
                && Bukkit.getPluginManager() != null
                && Bukkit.getPluginManager().isPluginEnabled("RPCharacters");
    }

    @Override
    public String activeCharacterName(String player) {
        if (!available()) return null;
        Player online = Bukkit.getPlayerExact(player);
        if (online != null) {
            PlayerData data = PlayerManager.get(online);
            if (data == null || !data.hasActiveCharacter()) return null;
            RPCharacter character = data.getActiveCharacter();
            return character == null ? null : character.getName();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(player);
        if (cached == null) return null;
        return activeNameOnDisk(new File(CHARACTER_ROOT, cached.getUniqueId().toString()));
    }

    /** The {@code name} of the character file in {@code folder} marked active. */
    static String activeNameOnDisk(File folder) {
        File[] files = folder.listFiles((dir, name) -> !name.startsWith("."));
        if (files == null) return null;
        for (File file : files) {
            if (!file.isFile()) continue;
            try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) continue;
                JsonObject json = parsed.getAsJsonObject();
                JsonElement active = json.get("active");
                JsonElement name = json.get("name");
                if (active == null || name == null || !name.isJsonPrimitive()) continue;
                if ("true".equalsIgnoreCase(active.getAsString())) {
                    return name.getAsString();
                }
            } catch (Exception e) {
                // A file being rewritten or unreadable: try the rest.
            }
        }
        return null;
    }
}
