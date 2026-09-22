package net.tfminecraft.simplefactions.war.battle.engine.core;

import org.bukkit.Location;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.ContestArea;

public final class BattleContestSetup {
	private BattleContestSetup() {
	}

	public static void setContestMin(Battle battle, Location location) {
		if (location == null) {
			throw new IllegalArgumentException("Location is required");
		}
		BattlePlacementValidator.validatePlacementOrThrow(battle, location, "Contest area min");
		ensureContestArea(battle);
		battle.getContestArea().setMin(BattleLocation.fromBukkitLocation(location));
	}

	public static void setContestMax(Battle battle, Location location) {
		if (location == null) {
			throw new IllegalArgumentException("Location is required");
		}
		BattlePlacementValidator.validatePlacementOrThrow(battle, location, "Contest area max");
		ensureContestArea(battle);
		battle.getContestArea().setMax(BattleLocation.fromBukkitLocation(location));
	}

	private static void ensureContestArea(Battle battle) {
		if (battle.getContestArea() == null) {
			battle.setContestArea(new ContestArea());
		}
	}

	public static int getEffectiveDurationSeconds(Battle battle) {
		if (battle.getContestDurationSeconds() > 0) {
			return battle.getContestDurationSeconds();
		}
		return Math.max(1, Cache.battleSiegeContestDurationSeconds);
	}
}
