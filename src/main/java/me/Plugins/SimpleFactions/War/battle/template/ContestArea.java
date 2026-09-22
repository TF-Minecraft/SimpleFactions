package me.Plugins.SimpleFactions.War.battle.template;

import org.bukkit.Location;
import org.bukkit.World;

public class ContestArea {
	private BattleLocation min;
	private BattleLocation max;

	public ContestArea() {
	}

	public ContestArea(BattleLocation min, BattleLocation max) {
		this.min = min;
		this.max = max;
	}

	public BattleLocation getMin() {
		return min;
	}

	public void setMin(BattleLocation min) {
		this.min = min;
	}

	public BattleLocation getMax() {
		return max;
	}

	public void setMax(BattleLocation max) {
		this.max = max;
	}

	public boolean isConfigured() {
		if (min == null || max == null) {
			return false;
		}
		Location minLoc = min.toBukkitLocation();
		Location maxLoc = max.toBukkitLocation();
		return minLoc != null && maxLoc != null;
	}

	public boolean contains(Location location) {
		if (location == null || !isConfigured()) {
			return false;
		}
		Location minLoc = min.toBukkitLocation();
		Location maxLoc = max.toBukkitLocation();
		if (minLoc == null || maxLoc == null) {
			return false;
		}
		World world = location.getWorld();
		if (world == null || !world.equals(minLoc.getWorld()) || !world.equals(maxLoc.getWorld())) {
			return false;
		}
		double minX = Math.min(minLoc.getX(), maxLoc.getX());
		double maxX = Math.max(minLoc.getX(), maxLoc.getX());
		double minY = Math.min(minLoc.getY(), maxLoc.getY());
		double maxY = Math.max(minLoc.getY(), maxLoc.getY());
		double minZ = Math.min(minLoc.getZ(), maxLoc.getZ());
		double maxZ = Math.max(minLoc.getZ(), maxLoc.getZ());
		double x = location.getX();
		double y = location.getY();
		double z = location.getZ();
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}
}
