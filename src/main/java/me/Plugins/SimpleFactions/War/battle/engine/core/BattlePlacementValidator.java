package me.Plugins.SimpleFactions.War.battle.engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.ContestArea;

public final class BattlePlacementValidator {
	private BattlePlacementValidator() {
	}

	public static boolean applies(Battle battle) {
		if (battle == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}

	public static Set<Integer> resolveAllowedProvinces(Battle battle) {
		return Collections.emptySet();
	}

	static Set<Integer> resolveAllowedProvinces(Battle battle, SimpleFactions plugin) {
		return Collections.emptySet();
	}

	public static boolean canResolveBounds(Battle battle) {
		return false;
	}

	public static int provinceAt(Location location) {
		if (location == null || location.getWorld() == null) {
			return -1;
		}
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin == null) {
			return -1;
		}
		ProvinceGrid grid = plugin.getProvinceGrid();
		if (grid == null) {
			return -1;
		}
		return grid.getAt(location.getBlockX(), location.getBlockZ());
	}

	public static boolean isLocationAllowed(Battle battle, Location location) {
		return true;
	}

	public static void validatePlacementOrThrow(Battle battle, Location location, String label) {
		// Province bounds removed in step 64.08; staff may place anywhere.
	}

	public static List<String> validate(Battle battle) {
		if (!applies(battle)) {
			return Collections.emptyList();
		}
		List<String> errors = new ArrayList<>();
		if (battle.getBattleType() == BattleType.SIEGE) {
			ContestArea area = battle.getContestArea();
			if (area == null || !area.isConfigured()) {
				errors.add("Siege contest area is not configured (setcontestmin / setcontestmax).");
			}
		}
		return errors;
	}

	public static String validateForStart(Battle battle) {
		List<String> errors = validate(battle);
		if (errors.isEmpty()) {
			return null;
		}
		return errors.get(0);
	}
}
