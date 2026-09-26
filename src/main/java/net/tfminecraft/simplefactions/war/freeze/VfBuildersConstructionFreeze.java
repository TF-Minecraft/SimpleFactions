package net.tfminecraft.simplefactions.war.freeze;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import net.tfminecraft.vfbuilders.api.ConstructionFreeze;

/** Holds VFBuilders projects started by players whose battle was postponed. */
public final class VfBuildersConstructionFreeze implements ConstructionFreeze {

	/**
	 * Only call once VFBuilders is enabled and its ConstructionFreeze API is on the
	 * classpath; this class cannot load without it.
	 */
	public static void register(Plugin plugin) {
		Bukkit.getServicesManager().register(
				ConstructionFreeze.class, new VfBuildersConstructionFreeze(), plugin, ServicePriority.Normal);
		plugin.getLogger().info("VFBuilders projects freeze with postponed battles");
	}

	@Override
	public String freezeReason(UUID constructorUuid) {
		OfflinePlayer player = Bukkit.getOfflinePlayer(constructorUuid);
		Instant now = Instant.now();
		Instant until = PreparationFreeze.frozenUntil(player.getName(), now);
		if (until == null) {
			return null;
		}
		return "battle postponed (" + PreparationFreeze.formatRemaining(until, now) + ")";
	}
}
