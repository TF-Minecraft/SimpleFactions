package me.Plugins.SimpleFactions.installation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import me.Plugins.SimpleFactions.Cache;

public final class InstallationSpawnService {
	private InstallationSpawnService() {}

	public static Location resolveCenter(Installation installation) {
		if (installation == null) {
			return null;
		}
		String worldName = Cache.worldName;
		if (worldName == null || worldName.isBlank()) {
			return null;
		}
		World world = Bukkit.getWorld(worldName);
		if (world == null) {
			return null;
		}
		int x = installation.getCenterX();
		int z = installation.getCenterZ();
		int y = world.getHighestBlockYAt(x, z) + 1;
		return new Location(world, x + 0.5, y, z + 0.5);
	}
}
