package me.Plugins.SimpleFactions.War.battle.engine.capture;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

public final class CapturePointMarkerService {
	static final int REFRESH_INTERVAL_TICKS = 10;
	static final int VIEW_RANGE = 192;
	static final double VIEW_RANGE_SQ = (double) VIEW_RANGE * VIEW_RANGE;
	static final int COLUMN_HEIGHT = 100;
	static final int COLUMN_STEP = 2;
	static final float DUST_SIZE = 3.5f;

	enum MarkerColor {
		GRAY(Color.fromRGB(128, 128, 128)),
		YELLOW(Color.YELLOW),
		GREEN(Color.fromRGB(0, 255, 0)),
		RED(Color.RED);

		private final Color bukkitColor;

		MarkerColor(Color bukkitColor) {
			this.bukkitColor = bukkitColor;
		}

		Color bukkitColor() {
			return bukkitColor;
		}
	}

	private int tickCounter;

	void tick(Battle battle, PointManager pointManager) {
		if (!shouldRender(battle)) {
			return;
		}
		tickCounter++;
		if (tickCounter % REFRESH_INTERVAL_TICKS != 0) {
			return;
		}
		List<CapturePoint> points = pointManager.getPoints();
		org.bukkit.World world = resolveWorld(points);
		if (world == null) {
			return;
		}
		for (Player player : world.getPlayers()) {
			for (CapturePoint point : points) {
				renderPoint(player, battle, point, points, pointManager);
			}
		}
	}

	void reset() {
		tickCounter = 0;
	}

	static MarkerColor resolveColor(
			Battle battle,
			CapturePoint point,
			List<CapturePoint> points,
			boolean viewerOnControllerSide) {
		if (battle.isSequentialCapture()
				&& !CapturePoint.isFrontPoint(point, points, battle)) {
			return MarkerColor.GRAY;
		}
		if (point.isContested()) {
			return MarkerColor.YELLOW;
		}
		if (viewerOnControllerSide) {
			return MarkerColor.GREEN;
		}
		return MarkerColor.RED;
	}

	private static boolean shouldRender(Battle battle) {
		return battle != null
				&& battle.hasStarted()
				&& battle.isCapturePointsEnabled()
				&& battle.getBattleType() == BattleType.FIELD
				&& !battle.getPointManager().getPoints().isEmpty();
	}

	private void renderPoint(
			Player player,
			Battle battle,
			CapturePoint point,
			List<CapturePoint> points,
			PointManager pointManager) {
		Location anchor = point.getLoc();
		if (anchor == null || anchor.getWorld() == null) {
			return;
		}
		if (!isChunkLoaded(anchor)) {
			return;
		}
		if (!isWithinViewRange(player, anchor)) {
			return;
		}
		MarkerColor color = resolveColor(
				battle,
				point,
				points,
				pointManager.isOnSide(point.getController(), player));
		spawnPillar(player, anchor, color);
	}

	private static void spawnPillar(Player player, Location anchor, MarkerColor color) {
		Particle.DustOptions dust = new Particle.DustOptions(color.bukkitColor(), DUST_SIZE);
		double x = anchor.getX() + 0.5;
		double z = anchor.getZ() + 0.5;
		double baseY = anchor.getY() + 2;
		for (double y = baseY; y < baseY + COLUMN_HEIGHT; y += COLUMN_STEP) {
			spawnDust(player, x, y, z, dust);
		}
	}

	private static void spawnDust(Player player, double x, double y, double z, Particle.DustOptions dust) {
		player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust, true);
	}

	private static org.bukkit.World resolveWorld(List<CapturePoint> points) {
		if (points == null) {
			return null;
		}
		for (CapturePoint point : points) {
			Location location = point.getLoc();
			if (location != null && location.getWorld() != null) {
				return location.getWorld();
			}
		}
		return null;
	}

	private static boolean isWithinViewRange(Player player, Location location) {
		if (player == null || location == null || location.getWorld() == null) {
			return false;
		}
		if (!player.getWorld().equals(location.getWorld())) {
			return false;
		}
		return player.getLocation().distanceSquared(location) <= VIEW_RANGE_SQ;
	}

	private static boolean isChunkLoaded(Location location) {
		return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
	}
}
