package me.Plugins.SimpleFactions.mercenary.company;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.RPCharacters.Managers.PlayerManager;
import net.tfminecraft.RPCharacters.Objects.PlayerData;
import net.tfminecraft.RPCharacters.Objects.RPCharacter;
import net.tfminecraft.RPCharacters.Objects.Trait.Trait;

/**
 * Reads the active character's traits. Online players without the mercenary
 * trait (or without an active character) are ineligible; offline players are
 * unknown so the tick does not kick them until they log in.
 */
public final class RpCharactersMercenaryTraitProbe implements MercenaryEligibility.Probe {
    public static final String TRAIT_ID = "mercenary";

    @Override
    public MercenaryEligibility.Status check(String player) {
        if (Bukkit.getServer() == null
                || Bukkit.getPluginManager() == null
                || !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        Player online = Bukkit.getPlayerExact(player);
        if (online == null) {
            return MercenaryEligibility.Status.UNKNOWN;
        }
        PlayerData data = PlayerManager.get(online);
        if (data == null || !data.hasActiveCharacter()) {
            return MercenaryEligibility.Status.INELIGIBLE;
        }
        RPCharacter character = data.getActiveCharacter();
        if (character == null) {
            return MercenaryEligibility.Status.INELIGIBLE;
        }
        for (Trait trait : character.getTraits()) {
            if (trait != null && TRAIT_ID.equalsIgnoreCase(trait.getId())) {
                return MercenaryEligibility.Status.ELIGIBLE;
            }
        }
        return MercenaryEligibility.Status.INELIGIBLE;
    }
}
