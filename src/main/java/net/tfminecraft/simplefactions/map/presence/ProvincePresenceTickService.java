package net.tfminecraft.simplefactions.map.presence;

import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;

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
