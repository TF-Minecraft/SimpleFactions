package me.Plugins.SimpleFactions.War.battle.engine.core;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.enums.Terrain;

public final class BattleBoundsService {
	private BattleBoundsService() {
	}

	public static boolean applies(Battle battle) {
		if (battle == null || !battle.hasStarted()) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}

	public static void resolveAllowedProvinces(Battle battle) {
		resolveAllowedProvinces(battle, SimpleFactions.getInstance());
	}

	static void resolveAllowedProvinces(Battle battle, SimpleFactions plugin) {
		if (battle == null) {
			return;
		}
		battle.setAllowedProvinceIds(java.util.Collections.emptySet());
		if (battle.getBattleType() == BattleType.RAID) {
			return;
		}
		Integer provinceId = battle.getProvinceId();
		if (provinceId == null || provinceId <= 0) {
			provinceId = resolveProvinceFromSpawn(battle, plugin);
			if (provinceId != null && provinceId > 0) {
				battle.setProvinceId(provinceId);
			}
		}
	}

	public static boolean isInBounds(Battle battle, org.bukkit.entity.Player player) {
		return true;
	}

	public static boolean isProvinceAllowed(Battle battle, int provinceId) {
		return true;
	}

	static Integer resolveProvinceFromSpawn(Battle battle, SimpleFactions plugin) {
		if (plugin == null) {
			return null;
		}
		ProvinceGrid grid = plugin.getProvinceGrid();
		if (grid == null) {
			return null;
		}
		BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
		Location spawn = defender != null ? defender.getSpawn() : null;
		if (spawn == null) {
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			spawn = attacker != null ? attacker.getSpawn() : null;
		}
		if (spawn == null || spawn.getWorld() == null) {
			return null;
		}
		int provinceId = grid.getAt(spawn.getBlockX(), spawn.getBlockZ());
		return provinceId > 0 ? provinceId : null;
	}

	static Integer findAdjacentSeaProvince(ProvinceManager pm, int provinceId) {
		if (pm == null) {
			return null;
		}
		Province province = pm.get(provinceId);
		if (province == null || province.getId() == 0) {
			return null;
		}
		for (int neighbourId : province.getNeighbours()) {
			Province neighbour = pm.get(neighbourId);
			if (neighbour != null && neighbour.getTerrain() == Terrain.SEA) {
				return neighbourId;
			}
		}
		return null;
	}
}
