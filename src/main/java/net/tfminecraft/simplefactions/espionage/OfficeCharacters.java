package net.tfminecraft.simplefactions.espionage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/** Keep optional roleplay/attribute APIs out of the office service's signatures. */
final class OfficeCharacters {
    private OfficeCharacters() {}

    static boolean enabled(String plugin) {
        return Bukkit.getServer() != null && Bukkit.getPluginManager() != null
                && Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    static CharacterSession currentSession(Player player) {
        if (player == null) return null;
        var session = Characters.currentSession(player).orElse(null);
        if (session == null || !session.ref().ownerId().equals(player.getUniqueId())) return null;
        var info = Characters.info(session.ref().characterId()).orElse(null);
        return info != null && info.ref().equals(session.ref()) && info.alive()
                && isCurrent(player, session) ? session : null;
    }

    static boolean isCurrent(Player player, CharacterSession session) {
        return session != null && player != null && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && session.ref().ownerId().equals(player.getUniqueId()) && Characters.isCurrent(session);
    }

    static boolean isDead(UUID owner, String characterId) {
        UUID id = characterUuid(characterId);
        if (owner == null || id == null) return false;
        return Members.info(Members.key(id)).filter(info -> info.ref().ownerId().equals(owner))
                .map(info -> info.status() == CharacterStatus.DEAD).orElse(false);
    }

    static UUID characterUuid(String value) {
        if (value == null) return null;
        try {
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? id : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Map<String, Integer> attributes(Player player, CharacterSession session) {
        requireCurrent(player, session);
        if (!enabled("RPCharacters")) throw new StaleCharacterSession();
        return Roleplay.attributes(player, session);
    }

    static void requireCurrent(Player player, CharacterSession session) {
        if (!isCurrent(player, session)) throw new StaleCharacterSession();
    }

    static final class StaleCharacterSession extends IllegalStateException {
        StaleCharacterSession() { super("A matching ready character session is required"); }
    }

    private static final class Roleplay {
        static Map<String, Integer> attributes(Player player, CharacterSession session) {
            var data = net.tfminecraft.rpcharacters.managers.PlayerManager.get(player);
            var character = data == null ? null : data.getActiveCharacter();
            if (character == null || character.getOwner() != player
                    || !session.ref().characterId().equals(characterUuid(character.getId())))
                throw new StaleCharacterSession();
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String attribute : EspionageConfig.DEFAULT_WEIGHTS.keySet()) result.put(attribute,
                    character.getAttributeData().getAmount(new net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier(attribute, 0)));
            requireCurrent(player, session);
            // RPCharacters supplies permanent values when MMOCore is absent.
            if (enabled("MMOCore")) Attributes.overlay(player, result);
            requireCurrent(player, session);
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
