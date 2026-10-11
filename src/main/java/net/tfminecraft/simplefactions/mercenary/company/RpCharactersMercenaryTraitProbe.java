package net.tfminecraft.simplefactions.mercenary.company;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.trait.Trait;

/**
 * Reads character traits from RPCharacters. Online players are judged on the
 * character asked about: it must exist, be alive and carry the mercenary
 * trait. Offline players are unknown so the tick does not act until they log in.
 */
public final class RpCharactersMercenaryTraitProbe implements MercenaryEligibility.Probe {
    public static final String TRAIT_ID = "mercenary";

    @Override
    public MercenaryEligibility.Status check(String player) {
        Player online = online(player);
        if (online == null) return MercenaryEligibility.Status.UNKNOWN;
        PlayerData data = PlayerManager.get(online);
        return judge(data == null ? null : data.getActiveCharacter());
    }

    @Override
    public MercenaryEligibility.Status checkCharacter(String player, String characterId) {
        Player online = online(player);
        if (online == null) return MercenaryEligibility.Status.UNKNOWN;
        PlayerData data = PlayerManager.get(online);
        return judge(data == null ? null : data.getCharacterById(characterId));
    }

    @Override
    public String mercenaryCharacter(String player) {
        Player online = online(player);
        PlayerData data = online == null ? null : PlayerManager.get(online);
        if (data == null) return null;
        RPCharacter active = data.getActiveCharacter();
        if (isMercenary(active)) return active.getId();
        for (RPCharacter character : data.getCharacters()) {
            if (isMercenary(character)) return character.getId();
        }
        return null;
    }

    /** The online player, or null while offline or RPCharacters is missing. */
    private static Player online(String player) {
        if (Bukkit.getServer() == null
                || Bukkit.getPluginManager() == null
                || !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            return null;
        }
        return Bukkit.getPlayerExact(player);
    }

    private static MercenaryEligibility.Status judge(RPCharacter character) {
        return isMercenary(character)
                ? MercenaryEligibility.Status.ELIGIBLE
                : MercenaryEligibility.Status.INELIGIBLE;
    }

    static boolean isMercenary(RPCharacter character) {
        if (character == null
                || character.getStatus() == net.tfminecraft.rpcharacters.enums.Status.DEAD) {
            return false;
        }
        for (Trait trait : character.getTraits()) {
            if (trait != null && TRAIT_ID.equalsIgnoreCase(trait.getId())) return true;
        }
        return false;
    }
}
