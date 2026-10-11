package net.tfminecraft.simplefactions.plots;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import net.tfminecraft.simplefactions.database.JsonUtil;

/** Keeps RPCharacters out of the plot service's signatures. Without it nobody has a character. */
final class PlotCharacters {
    private PlotCharacters() {}

    static boolean enabled() {
        return Bukkit.getServer() != null && Bukkit.getPluginManager() != null
                && Bukkit.getPluginManager().isPluginEnabled("RPCharacters");
    }

    /** The player's living active character, or null. */
    static PlotCharacter active(Player player) {
        return player == null || !enabled() ? null : Roleplay.active(player);
    }

    static boolean plays(Player player, PlotCharacter character) {
        PlotCharacter active = active(player);
        return active != null && character != null && active.is(character.characterId);
    }

    static Player online(PlotCharacter character) {
        if (character == null || character.playerId == null) return null;
        Player player = Bukkit.getPlayer(character.playerId);
        return plays(player, character) ? player : null;
    }

    static boolean dead(PlotCharacter character) {
        return character != null && enabled() && Roleplay.dead(character.playerId, character.characterId);
    }

    /** The active character of an offline account, read from RPCharacters' saved files. */
    static PlotCharacter offline(OfflinePlayer account) {
        if (account == null || !enabled()) return null;
        var plugin = Bukkit.getPluginManager().getPlugin("RPCharacters");
        Path folder = plugin.getDataFolder().toPath().resolve("data/characterdata").resolve(account.getUniqueId().toString());
        if (!Files.isDirectory(folder)) return null;
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                JsonObject json = read(file);
                if (json != null && text(json, "id") != null && text(json, "name") != null
                        && "true".equalsIgnoreCase(text(json, "active")) && !"DEAD".equalsIgnoreCase(text(json, "status")))
                    return new PlotCharacter(text(json, "id"), account.getUniqueId(), account.getName(), text(json, "name"));
            }
        } catch (java.io.IOException ignored) {
            // An unreadable folder is treated like an account without characters.
        }
        return null;
    }

    private static JsonObject read(Path file) {
        try {
            return JsonUtil.GSON.fromJson(Files.readString(file), JsonObject.class);
        } catch (java.io.IOException | RuntimeException damaged) {
            return null;
        }
    }

    private static String text(JsonObject json, String key) {
        var value = json.get(key);
        return value != null && value.isJsonPrimitive() && !value.getAsString().isBlank() ? value.getAsString() : null;
    }

    /** The name others see for the player: a mask or alias hides the character. */
    static String seenAs(Player player, PlotCharacter character) {
        String seen = enabled() ? org.bukkit.ChatColor.stripColor(
                net.tfminecraft.rpcharacters.identity.DisplayIdentityService.resolveDisplay(player)) : null;
        return seen == null || seen.isBlank() ? character.name : seen;
    }

    /** Permanently kills the character if it is still the player's living active character. */
    static boolean permakill(Player player, String characterId, Player killer) {
        return enabled() && Roleplay.permakill(player, characterId, killer);
    }

    private static final class Roleplay {
        static PlotCharacter active(Player player) {
            var data = net.tfminecraft.rpcharacters.managers.PlayerManager.get(player);
            var character = data == null ? null : data.getActiveCharacter();
            if (character == null || character.getStatus() != net.tfminecraft.rpcharacters.enums.Status.ALIVE) return null;
            return new PlotCharacter(character.getId(), player.getUniqueId(), player.getName(), character.getName());
        }

        static boolean dead(UUID owner, String id) {
            var data = owner == null ? null : net.tfminecraft.rpcharacters.managers.PlayerManager.get(owner);
            return data != null && data.getCharacters().stream().anyMatch(character ->
                    id.equals(character.getId()) && character.getStatus() == net.tfminecraft.rpcharacters.enums.Status.DEAD);
        }

        static boolean permakill(Player player, String id, Player killer) {
            var data = net.tfminecraft.rpcharacters.managers.PlayerManager.get(player);
            var character = data == null ? null : data.getActiveCharacter();
            if (character == null || !id.equals(character.getId())) return false;
            return net.tfminecraft.rpcharacters.permadeath.PermadeathService.killCharacter(player, character,
                    net.tfminecraft.rpcharacters.permadeath.PermakillCause.OTHER, killer);
        }
    }
}
