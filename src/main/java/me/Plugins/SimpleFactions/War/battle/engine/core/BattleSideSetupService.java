package me.Plugins.SimpleFactions.War.battle.engine.core;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.War.battle.engine.capture.BattleCapturePoints;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;

public final class BattleSideSetupService {
	private BattleSideSetupService() {
	}

	public static void setSpawn(Battle battle, BattleSide side, Location location) {
		if (side == null) {
			throw new IllegalArgumentException("Side is required");
		}
		if (location == null) {
			throw new IllegalArgumentException("Location is required");
		}
		BattlePlacementValidator.validatePlacementOrThrow(battle, location, side.getId() + " spawn");
		side.setSpawn(location);
		if (battle != null && battle.isSequentialCapture() && battle.isCapturePointsEnabled()) {
			syncLinearChainIfDefenderSpawnReady(battle);
		}
	}

	private static void syncLinearChainIfDefenderSpawnReady(Battle battle) {
		if (BattleCapturePoints.resolveDefenderSpawn(battle) != null) {
			BattleCapturePoints.syncLinearChain(battle);
		}
	}

	public static void setJail(Battle battle, BattleSide side, Location location) {
		if (side == null) {
			throw new IllegalArgumentException("Side is required");
		}
		if (location == null) {
			throw new IllegalArgumentException("Location is required");
		}
		BattlePlacementValidator.validatePlacementOrThrow(battle, location, side.getId() + " jail");
		side.setJail(location);
	}

	public static void setSideLives(Battle battle, BattleSide side, int lives) {
		if (battle != null && battle.getWarId() != null) {
			throw new IllegalStateException("Campaign battle lives are computed from war commitment");
		}
		if (side == null) {
			throw new IllegalArgumentException("Side is required");
		}
		if (lives < 1) {
			throw new IllegalArgumentException("Lives must be at least 1");
		}
		side.setLives(lives);
	}

	public static CapturePoint addCapturePoint(Battle battle, BattleSide side, Location location) {
		if (battle == null) {
			throw new IllegalArgumentException("Battle is required");
		}
		if (side == null) {
			throw new IllegalArgumentException("Side is required");
		}
		if (location == null) {
			throw new IllegalArgumentException("Location is required");
		}
		if (!battle.isCapturePointsEnabled()) {
			throw new IllegalStateException("Capture points are not enabled for this battle");
		}
		BattlePlacementValidator.validatePlacementOrThrow(battle, location, "Capture point");
		CapturePoint point = BattleCapturePoints.createAtPlayer(battle, side.getId(), location, side);
		battle.addPoint(point);
		BattleCapturePoints.afterPointListChanged(battle);
		return point;
	}
}
