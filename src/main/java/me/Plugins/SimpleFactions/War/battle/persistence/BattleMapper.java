package me.Plugins.SimpleFactions.War.battle.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Database.BattleData;
import me.Plugins.SimpleFactions.Database.BattleSideData;
import me.Plugins.SimpleFactions.Database.CapturePointData;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;
import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.CapturePointDefinition;
import me.Plugins.SimpleFactions.War.battle.template.ContestArea;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

public final class BattleMapper {
	private BattleMapper() {
	}

	public static BattleData toData(Battle battle) {
		if (battle == null) {
			return null;
		}
		BattleData data = new BattleData();
		data.id = battle.getId();
		data.displayName = battle.getDisplayName();
		data.battleType = battle.getBattleType() != null ? battle.getBattleType().toJson() : null;
		data.warId = battle.getWarId();
		data.provinceId = battle.getProvinceId();
		data.templateName = battle.getTemplateName();
		data.friendlyFire = battle.hasFriendlyFire();
		data.keepInventory = battle.hasKeepInventory();
		data.lootEnabled = battle.hasLootEnabled();
		data.teleport = battle.hasTeleport();
		data.locked = battle.isLocked();
		data.lives = battle.getLives();
		data.lifeType = battle.getLifeType() != null ? battle.getLifeType().name() : LifeType.COLLECTIVE.name();
		data.sequentialCapture = battle.isSequentialCapture();
		data.capturePointsEnabled = battle.isCapturePointsEnabled();
		data.campaignRaid = battle.isCampaignRaid();
		data.started = battle.hasStarted();
		if (battle.getStartedAt() != null) {
			data.startedAt = battle.getStartedAt().toString();
		}
		data.contestDurationSeconds = battle.getContestDurationSeconds();
		data.contestHoldRemainingSeconds = battle.getContestHoldRemainingSeconds();
		data.defenderRespawnMode = battle.getDefenderRespawnMode() != null
				? battle.getDefenderRespawnMode().name()
				: null;
		data.defenderLives = battle.getDefenderLives();
		data.navalVariant = battle.isNavalVariant();
		data.navalSpawn = BattleLocation.fromBukkitLocation(battle.getNavalSpawn());
		if (battle.getContestArea() != null) {
			data.contestMin = battle.getContestArea().getMin();
			data.contestMax = battle.getContestArea().getMax();
		}
		data.raidTarget = battle.getRaidTarget();
		for (BattleSide side : battle.getSides()) {
			data.sides.add(toSideData(side));
		}
		for (CapturePoint point : battle.getPoints()) {
			data.points.add(toPointData(point));
		}
		return data;
	}

	public static Battle fromData(BattleData data) {
		if (data == null || data.id == null || data.id.isBlank()) {
			return null;
		}
		Battle battle = new Battle(data.id);
		battle.setDisplayName(data.displayName);
		battle.setBattleType(parseBattleType(data.battleType));
		battle.setWarId(data.warId);
		battle.setProvinceId(data.provinceId);
		battle.setTemplateName(data.templateName);
		battle.setFriendlyFire(data.friendlyFire);
		battle.setKeepInventory(data.keepInventory);
		// Files written before loot existed carry no key. Campaign raids are the only
		// battles that ship with it off, so derive the rest as on.
		battle.setLootEnabled(data.lootEnabled != null ? data.lootEnabled : !data.campaignRaid);
		battle.setTeleport(data.teleport);
		battle.setLocked(data.locked);
		battle.setLives(data.lives);
		battle.setLifeType(parseLifeType(data.lifeType));
		battle.setSequentialCapture(data.sequentialCapture);
		battle.setCapturePointsEnabled(data.capturePointsEnabled);
		battle.setCampaignRaid(data.campaignRaid);
		battle.setStarted(data.started);
		if (data.startedAt != null && !data.startedAt.isBlank()) {
			battle.setStartedAt(Instant.parse(data.startedAt));
		}
		battle.setContestDurationSeconds(data.contestDurationSeconds);
		battle.setContestHoldRemainingSeconds(data.contestHoldRemainingSeconds);
		battle.setDefenderRespawnMode(parseDefenderRespawnMode(data.defenderRespawnMode));
		battle.setDefenderLives(data.defenderLives);
		battle.setNavalVariant(data.navalVariant);
		battle.setNavalSpawn(toLocation(data.navalSpawn));
		if (data.contestMin != null && data.contestMax != null) {
			battle.setContestArea(new ContestArea(data.contestMin, data.contestMax));
		}
		battle.setRaidTarget(data.raidTarget);

		for (BattleSideData sideData : data.sides) {
			BattleSide side = fromSideData(sideData, battle.getLifeType(), battle.getLives());
			if (side != null) {
				battle.addSide(side);
			}
		}
		for (CapturePointData pointData : data.points) {
			CapturePoint point = fromPointData(pointData, battle);
			if (point != null) {
				battle.addPoint(point);
			}
		}
		battle.getPointManager().setPoints(battle.getPoints());
		return battle;
	}

	private static BattleSideData toSideData(BattleSide side) {
		BattleSideData data = new BattleSideData();
		data.id = side.getId();
		data.spawn = BattleLocation.fromBukkitLocation(side.getSpawn());
		data.jail = BattleLocation.fromBukkitLocation(side.getJail());
		for (Location respawn : side.getRespawnPoints()) {
			BattleLocation location = BattleLocation.fromBukkitLocation(respawn);
			if (location != null) {
				data.respawnPoints.add(location);
			}
		}
		data.lives = side.getLives();
		data.maxLives = side.getMaxLives();
		data.lifeType = side.getLt() != null ? side.getLt().name() : LifeType.COLLECTIVE.name();
		for (Warband warband : side.getBands()) {
			if (warband != null) {
				data.warbandIds.add(warband.getId());
			}
		}
		return data;
	}

	private static BattleSide fromSideData(BattleSideData data, LifeType defaultLifeType, int defaultLives) {
		if (data == null || data.id == null) {
			return null;
		}
		LifeType lifeType = parseLifeType(data.lifeType);
		if (lifeType == null) {
			lifeType = defaultLifeType != null ? defaultLifeType : LifeType.COLLECTIVE;
		}
		int initialLives = data.maxLives > 0 ? data.maxLives : defaultLives;
		BattleSide side = new BattleSide(data.id, lifeType, initialLives);
		side.setSpawn(toLocation(data.spawn));
		side.setJail(toLocation(data.jail));
		for (BattleLocation respawn : data.respawnPoints) {
			Location location = toLocation(respawn);
			if (location != null) {
				side.addRespawnPoint(location);
			}
		}
		int lives = data.lives;
		int maxLives = data.maxLives > 0 ? data.maxLives : lives;
		side.restoreLives(lives, maxLives);
		return side;
	}

	private static CapturePointData toPointData(CapturePoint point) {
		CapturePointData data = new CapturePointData();
		data.id = point.getId();
		data.location = BattleLocation.fromBukkitLocation(point.getLoc());
		data.controllerSideId = point.getController() != null ? point.getController().getId() : null;
		data.captureProgress = point.getCaptureProgress();
		data.sequenceIndex = point.getSequenceIndex();
		data.advanceSideId = point.getAdvanceSideId();
		return data;
	}

	private static CapturePoint fromPointData(CapturePointData data, Battle battle) {
		if (data == null || data.id == null || data.location == null) {
			return null;
		}
		Location location = toLocation(data.location);
		if (location == null) {
			return null;
		}
		BattleSide controller = data.controllerSideId != null
				? battle.getSideById(data.controllerSideId)
				: null;
		if (controller == null && !battle.getSides().isEmpty()) {
			controller = battle.getSides().get(0);
		}
		CapturePoint point = new CapturePoint(data.id, location, controller, data.captureProgress);
		point.setSequenceIndex(data.sequenceIndex);
		point.setAdvanceSideId(data.advanceSideId);
		if (data.controllerSideId != null) {
			BattleSide resolved = battle.getSideById(data.controllerSideId);
			if (resolved != null) {
				point.setController(resolved);
			}
		}
		return point;
	}

	private static Location toLocation(BattleLocation location) {
		return location != null ? location.toBukkitLocation() : null;
	}

	private static BattleType parseBattleType(String value) {
		return BattleType.fromJson(value);
	}

	private static LifeType parseLifeType(String value) {
		if (value == null || value.isBlank()) {
			return LifeType.COLLECTIVE;
		}
		try {
			return LifeType.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			return LifeType.COLLECTIVE;
		}
	}

	private static DefenderRespawnMode parseDefenderRespawnMode(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return DefenderRespawnMode.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
