package me.Plugins.SimpleFactions.War.battle.engine.raid;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.template.CapturePointDefinition;

public final class BattleRaidSetup {
	private BattleRaidSetup() {
	}

	public static void onStart(Battle battle) {
		if (battle == null || battle.getBattleType() != BattleType.RAID) {
			return;
		}
		battle.setAllowedProvinceIds(java.util.Collections.emptySet());
		battle.clearPoints();
		if (!battle.isCampaignRaid()) {
			seedTargetPoint(battle);
		}
		applySideLives(battle);
	}

	public static void setRaidTarget(Battle battle, Location location) {
		if (battle == null || location == null) {
			return;
		}
		String id = "target";
		CapturePointDefinition existing = battle.getRaidTarget();
		if (existing != null && existing.getId() != null && !existing.getId().isBlank()) {
			id = existing.getId();
		}
		battle.setRaidTarget(new CapturePointDefinition(id, BattleLocation.fromBukkitLocation(location)));
	}

	public static int getEffectiveDefenderLives(Battle battle) {
		if (battle.getDefenderLives() != null && battle.getDefenderLives() > 0) {
			return battle.getDefenderLives();
		}
		if (battle.getLives() > 0) {
			return battle.getLives();
		}
		return 25;
	}

	public static DefenderRespawnMode getEffectiveDefenderRespawnMode(Battle battle) {
		DefenderRespawnMode mode = battle.getDefenderRespawnMode();
		if (mode != null) {
			return mode;
		}
		return Cache.battleRaidDefenderRespawnModeDefault != null
				? Cache.battleRaidDefenderRespawnModeDefault
				: DefenderRespawnMode.INFINITE;
	}

	private static void seedTargetPoint(Battle battle) {
		CapturePointDefinition targetDef = battle.getRaidTarget();
		if (targetDef == null || targetDef.getLocation() == null) {
			return;
		}
		Location location = targetDef.getLocation().toBukkitLocation();
		if (location == null) {
			return;
		}
		BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
		if (defender == null) {
			return;
		}
		String pointId = targetDef.getId() != null && !targetDef.getId().isBlank() ? targetDef.getId() : "target";
		CapturePoint point = new CapturePoint(pointId, location, defender, 100);
		point.setAdvanceSideId(BattleTemplate.ATTACKER_SIDE);
		battle.addPoint(point);
	}

	private static void applySideLives(Battle battle) {
		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
		if (attacker != null) {
			attacker.setLives(0);
		}
		if (defender == null) {
			return;
		}
		if (getEffectiveDefenderRespawnMode(battle) == DefenderRespawnMode.LIVES) {
			defender.setLives(getEffectiveDefenderLives(battle));
		} else {
			defender.setLives(9999);
		}
	}
}
