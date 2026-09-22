package me.Plugins.SimpleFactions.War.battle.template;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.Cache;

public class BattleLocation {
	private String world;
	private double x;
	private double y;
	private double z;
	private float yaw;
	private float pitch;

	public BattleLocation() {
	}

	public BattleLocation(String world, double x, double y, double z, float yaw, float pitch) {
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public static BattleLocation fromSection(ConfigurationSection section) {
		if (section == null) {
			return null;
		}
		String worldName = section.getString("world");
		if (worldName == null || worldName.isBlank()) {
			worldName = Cache.worldName;
		}
		if (!section.contains("x") || !section.contains("y") || !section.contains("z")) {
			return null;
		}
		return new BattleLocation(
				worldName,
				section.getDouble("x"),
				section.getDouble("y"),
				section.getDouble("z"),
				(float) section.getDouble("yaw", 0),
				(float) section.getDouble("pitch", 0));
	}

	public Location toBukkitLocation() {
		if (world == null || world.isBlank()) {
			world = Cache.worldName;
		}
		World bukkitWorld = Bukkit.getWorld(world);
		if (bukkitWorld == null) {
			return null;
		}
		return new Location(bukkitWorld, x, y, z, yaw, pitch);
	}

	public static BattleLocation fromBukkitLocation(Location location) {
		if (location == null || location.getWorld() == null) {
			return null;
		}
		return new BattleLocation(
				location.getWorld().getName(),
				location.getX(),
				location.getY(),
				location.getZ(),
				location.getYaw(),
				location.getPitch());
	}

	@SuppressWarnings("unchecked")
	public static BattleLocation fromMap(java.util.Map<?, ?> map) {
		if (map == null) {
			return null;
		}
		Object xObj = map.get("x");
		Object yObj = map.get("y");
		Object zObj = map.get("z");
		if (!(xObj instanceof Number) || !(yObj instanceof Number) || !(zObj instanceof Number)) {
			return null;
		}
		String worldName = map.get("world") instanceof String world ? world : Cache.worldName;
		float yaw = map.get("yaw") instanceof Number yawNum ? yawNum.floatValue() : 0f;
		float pitch = map.get("pitch") instanceof Number pitchNum ? pitchNum.floatValue() : 0f;
		return new BattleLocation(
				worldName,
				((Number) xObj).doubleValue(),
				((Number) yObj).doubleValue(),
				((Number) zObj).doubleValue(),
				yaw,
				pitch);
	}

	public String getWorld() {
		return world;
	}

	public void setWorld(String world) {
		this.world = world;
	}

	public double getX() {
		return x;
	}

	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		this.y = y;
	}

	public double getZ() {
		return z;
	}

	public void setZ(double z) {
		this.z = z;
	}

	public float getYaw() {
		return yaw;
	}

	public void setYaw(float yaw) {
		this.yaw = yaw;
	}

	public float getPitch() {
		return pitch;
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
	}
}
