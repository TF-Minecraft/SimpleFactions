package net.tfminecraft.simplefactions.identity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;

/** Reads an online player's active RPCharacters character name. */
public final class RpCharactersLeaderCharacterProbe implements LeaderCharacters.Probe {
    @Override
    public String activeCharacterName(String player) {
        if (Bukkit.getServer() == null
                || Bukkit.getPluginManager() == null
                || !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(player);
        if (online == null) return null;
        PlayerData data = PlayerManager.get(online);
        if (data == null || !data.hasActiveCharacter()) return null;
        RPCharacter character = data.getActiveCharacter();
        return character == null ? null : character.getName();
    }
}
