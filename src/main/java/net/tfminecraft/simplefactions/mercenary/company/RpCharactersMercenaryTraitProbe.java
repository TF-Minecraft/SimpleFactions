package net.tfminecraft.simplefactions.mercenary.company;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.rpcharacters.enums.Status;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.trait.Trait;

/**
 * Reads RPCharacters traits. Founding and joining use the active character.
 * A company stays while any player on it still has an alive character with
 * the mercenary trait. Online players are read from memory. Offline players
 * are read from the character files RPCharacters saves under its own data
 * folder, {@code data/characterdata/<uuid>/}, and only when the server already
 * knows their UUID. No lookup goes to Mojang. A missing data folder is unknown,
 * not proof that a player has no characters.
 */
public final class RpCharactersMercenaryTraitProbe implements MercenaryEligibility.Probe {
    public static final String TRAIT_ID = "mercenary";

    /** Tests point this at a fixture. Production uses RPCharacters' data folder. */
    static File characterRoot;

    /**
     * Offline character files are read on the main thread. Keep a result for a
     * minute so the company tick does not reopen those files every second.
     */
    static long diskCacheMillis = 60_000L;
    private static final Map<String, DiskHit> diskCache = new HashMap<>();

    @Override
    public MercenaryEligibility.Status check(String player) {
        if (!charactersAvailable()) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        Player online = Bukkit.getPlayerExact(player);
        if (online == null) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        PlayerData data = PlayerManager.get(online);
        if (data == null) {
            return MercenaryEligibility.Status.INELIGIBLE;
        }
        RPCharacter character = data.getActiveCharacter();
        if (character == null) {
            return MercenaryEligibility.Status.INELIGIBLE;
        }
        if (hasMercenaryTrait(character)) {
            return MercenaryEligibility.Status.ELIGIBLE;
        }
        return MercenaryEligibility.Status.INELIGIBLE;
    }

    @Override
    public MercenaryEligibility.Status livingMercenary(String player) {
        if (!charactersAvailable()) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        Player online = Bukkit.getPlayerExact(player);
        if (online != null) {
            return livingInMemory(online);
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(player);
        if (cached == null) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        return cachedLivingOnDisk(cached.getUniqueId());
    }

    static void clearDiskCache() {
        diskCache.clear();
    }

    private static MercenaryEligibility.Status cachedLivingOnDisk(UUID uuid) {
        File root = resolveCharacterRoot();
        if (root == null || !root.isDirectory()) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        String key = root.getPath() + '\u0000' + uuid;
        long now = System.currentTimeMillis();
        DiskHit hit = diskCache.get(key);
        if (hit != null && now - hit.at < diskCacheMillis) {
            return hit.status;
        }
        MercenaryEligibility.Status status = livingOnDisk(new File(root, uuid.toString()));
        diskCache.put(key, new DiskHit(status, now));
        return status;
    }

    private static File resolveCharacterRoot() {
        if (characterRoot != null) {
            return characterRoot;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("RPCharacters");
        if (plugin == null || plugin.getDataFolder() == null) {
            return null;
        }
        return new File(plugin.getDataFolder(), "data/characterdata");
    }

    private static final class DiskHit {
        private final MercenaryEligibility.Status status;
        private final long at;

        private DiskHit(MercenaryEligibility.Status status, long at) {
            this.status = status;
            this.at = at;
        }
    }

    private static boolean charactersAvailable() {
        return Bukkit.getServer() != null
                && Bukkit.getPluginManager() != null
                && Bukkit.getPluginManager().isPluginEnabled("RPCharacters");
    }

    private static MercenaryEligibility.Status livingInMemory(Player online) {
        PlayerData data = PlayerManager.get(online);
        if (data == null) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        List<RPCharacter> characters = data.getCharacters();
        if (characters == null) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        for (RPCharacter character : characters) {
            if (character == null || character.getStatus() != Status.ALIVE) {
                continue;
            }
            if (hasMercenaryTrait(character)) {
                return MercenaryEligibility.Status.ELIGIBLE;
            }
        }
        return MercenaryEligibility.Status.INELIGIBLE;
    }

    private static boolean hasMercenaryTrait(RPCharacter character) {
        List<Trait> traits = character.getTraits();
        if (traits == null) {
            return false;
        }
        for (Trait trait : traits) {
            if (trait != null && TRAIT_ID.equalsIgnoreCase(trait.getId())) {
                return true;
            }
        }
        return false;
    }

    /** Alive mercenary trait anywhere in {@code folder}, which is one player's character directory. */
    static MercenaryEligibility.Status livingOnDisk(File folder) {
        if (folder == null || !folder.exists()) {
            return MercenaryEligibility.Status.INELIGIBLE;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        boolean eligible = false;
        boolean unreadable = false;
        for (File file : files) {
            if (!file.isFile() || file.getName().startsWith(".")) {
                continue;
            }
            JsonObject json = readObject(file);
            if (json == null) {
                unreadable = true;
                continue;
            }
            Boolean alive = aliveStatus(json);
            if (alive == null) {
                unreadable = true;
                continue;
            }
            if (!alive) {
                continue;
            }
            if (jsonHasMercenaryTrait(json.get("traits"))) {
                eligible = true;
            }
        }
        if (eligible) {
            return MercenaryEligibility.Status.ELIGIBLE;
        }
        if (unreadable) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        return MercenaryEligibility.Status.INELIGIBLE;
    }

    /** {@code null} when status is missing or not text, so a damaged file is not treated as dead. */
    private static Boolean aliveStatus(JsonObject json) {
        JsonElement status = json.get("status");
        if (status == null || !status.isJsonPrimitive()) {
            return null;
        }
        return "ALIVE".equalsIgnoreCase(status.getAsString());
    }

    private static boolean jsonHasMercenaryTrait(JsonElement traits) {
        if (traits == null || !traits.isJsonArray()) {
            return false;
        }
        for (JsonElement trait : traits.getAsJsonArray()) {
            if (trait != null && trait.isJsonPrimitive()
                    && TRAIT_ID.equalsIgnoreCase(trait.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject readObject(File file) {
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
