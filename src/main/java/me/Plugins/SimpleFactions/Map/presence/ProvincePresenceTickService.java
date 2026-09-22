package me.Plugins.SimpleFactions.Map.presence;

import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;

public final class ProvincePresenceTickService {
	private ProvincePresenceTickService() {}

	public static void start() {
		long interval = Math.max(1L, Cache.battleProvincePollIntervalTicks);
		new BukkitRunnable() {
			@Override
			public void run() {
				ProvincePresenceService.getInstance().tick();
			}
		}.runTaskTimer(SimpleFactions.plugin, interval, interval);
	}
}
