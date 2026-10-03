package net.tfminecraft.simplefactions.espionage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Keep optional roleplay/attribute APIs out of the office service's signatures. */
final class OfficeCharacters {
    private OfficeCharacters() {}

    static boolean enabled(String plugin) {
        return Bukkit.getServer() != null && Bukkit.getPluginManager() != null
                && Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    static String activeCharacterId(Player player) {
        return player == null || !enabled("RPCharacters") ? null : Roleplay.activeId(player);
    }

    static boolean isDead(UUID owner, String characterId) {
        return owner != null && characterId != null && enabled("RPCharacters")
                && Roleplay.isDead(owner, characterId);
    }

    static Map<String, Integer> attributes(Player player) {
        return !enabled("RPCharacters") ? Map.of() : Roleplay.attributes(player);
    }

    private static final class Roleplay {
        static String activeId(Player player) {
            var data = net.tfminecraft.rpcharacters.managers.PlayerManager.get(player);
            var character = data == null ? null : data.getActiveCharacter();
            return character == null ? null : character.getId();
        }

        static boolean isDead(UUID owner, String id) {
            var data = net.tfminecraft.rpcharacters.managers.PlayerManager.get(owner);
            return data != null && data.getCharacters().stream().anyMatch(character ->
                    id.equals(character.getId()) && character.getStatus() == net.tfminecraft.rpcharacters.enums.Status.DEAD);
        }

        static Map<String, Integer> attributes(Player player) {
            var data = net.tfminecraft.rpcharacters.managers.PlayerManager.get(player);
            var character = data == null ? null : data.getActiveCharacter();
            if (character == null) throw new IllegalStateException("An active character is required for an aptitude roll");
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String attribute : EspionageConfig.DEFAULT_WEIGHTS.keySet()) result.put(attribute,
                    character.getAttributeData().getAmount(new net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier(attribute, 0)));
            // RPCharacters supplies the permanent values when MMOCore is absent.
            if (enabled("MMOCore")) Attributes.overlay(player, result);
            return result;
        }
    }

    private static final class Attributes {
        static void overlay(Player player, Map<String, Integer> result) {
            var attributes = net.Indyuce.mmocore.api.player.PlayerData.get(player).getAttributes();
            for (String attribute : result.keySet()) {
                var instance = attributes.getInstance(attribute);
                if (instance != null) result.put(attribute, instance.getBase());
            }
        }
    }
}
