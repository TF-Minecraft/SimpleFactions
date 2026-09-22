package me.Plugins.SimpleFactions.prestige;

import org.bukkit.Bukkit;

import net.tfminecraft.RPCharacters.playtime.PlaytimeService;

/**
 * Reads active-character online time from the RPCharacters playtime index.
 *
 * <p>That index is an in-memory map covering offline players too, so this stays
 * cheap enough for the prestige recompute path. Online playtime cannot advance
 * while a player is away, so the figure for an offline member is exact.
 */
public final class RpCharactersPlaytimeProbe implements MemberPlaytime.Probe {

	@Override
	public Integer secondsFor(String player) {
		if (Bukkit.getServer() == null
				|| Bukkit.getPluginManager() == null
				|| !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
			return null;
		}
		return PlaytimeService.getSeconds(player);
	}
}
