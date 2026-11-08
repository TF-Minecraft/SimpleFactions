package net.tfminecraft.simplefactions.war.stats;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

/** The MMOCore class a player is playing, or null when MMOCore is missing. */
final class FighterClass {
	private FighterClass() {
	}

	static String of(Player player) {
		PluginManager plugins = Bukkit.getPluginManager();
		if (plugins == null || !plugins.isPluginEnabled("MMOCore")) {
			return null;
		}
		try {
			return Mmo.name(player);
		} catch (RuntimeException | LinkageError unavailable) {
			return null;
		}
	}

	private static final class Mmo {
		static String name(Player player) {
			var data = net.Indyuce.mmocore.api.player.PlayerData.get(player);
			var profess = data == null ? null : data.getProfess();
			return profess == null ? null : profess.getName();
		}
	}
}
