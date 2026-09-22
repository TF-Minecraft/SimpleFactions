package me.Plugins.SimpleFactions.War.declare;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.function.IntPredicate;

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.SeaConnectivity;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.settlement.Settlement;

public final class PillageRangeQueries {
	private PillageRangeQueries() {}

	public static OptionalInt landDistanceFromAttacker(
			ProvinceManager provinceManager,
			Faction attacker,
			int provinceId) {
		if (provinceManager == null || attacker == null || provinceId <= 0) {
			return OptionalInt.empty();
		}
		Set<Integer> starts = landProvinces(provinceManager, attacker.getProvinces());
		if (starts.isEmpty()) {
			return OptionalInt.empty();
		}
		return landBfs(provinceManager, starts, current -> current == provinceId);
	}

	public static OptionalInt distanceToCoast(ProvinceManager provinceManager, int provinceId) {
		if (provinceManager == null || provinceId <= 0) {
			return OptionalInt.empty();
		}
		Province start = provinceManager.get(provinceId);
		if (start == null || !start.isValid() || start.getTerrain() == Terrain.SEA) {
			return OptionalInt.empty();
		}
		return landBfs(provinceManager, Set.of(provinceId), current -> isCoastal(provinceManager, current));
	}

	public static boolean inLandRange(
			ProvinceManager provinceManager,
			Faction attacker,
			int provinceId,
			int range) {
		OptionalInt distance = landDistanceFromAttacker(provinceManager, attacker, provinceId);
		return distance.isPresent() && distance.getAsInt() <= range;
	}

	public static boolean inSeaRange(
			ProvinceManager provinceManager,
			Faction attacker,
			Faction settlementOwner,
			int centerId,
			int range) {
		OptionalInt coast = distanceToCoast(provinceManager, centerId);
		if (coast.isEmpty() || coast.getAsInt() > range) {
			return false;
		}
		return SeaConnectivity.hasSeaConnection(provinceManager, attacker, settlementOwner);
	}

	public static boolean canPillageSettlement(
			ProvinceManager provinceManager,
			Faction attacker,
			Settlement settlement,
			Faction settlementOwner,
			Collection<Integer> defenderRealmProvinces,
			int range) {
		if (provinceManager == null
				|| attacker == null
				|| settlement == null
				|| settlementOwner == null
				|| defenderRealmProvinces == null) {
			return false;
		}
		int center = settlement.getCenterProvince();
		if (center <= 0) {
			return false;
		}
		if (ownsProvince(attacker, center)) {
			return false;
		}
		if (!defenderRealmProvinces.contains(center)) {
			return false;
		}
		return inLandRange(provinceManager, attacker, center, range)
				|| inSeaRange(provinceManager, attacker, settlementOwner, center, range);
	}

	private static boolean ownsProvince(Faction faction, int provinceId) {
		List<Integer> provinces = faction.getProvinces();
		return provinces != null && provinces.contains(provinceId);
	}

	private static Set<Integer> landProvinces(ProvinceManager provinceManager, List<Integer> ids) {
		Set<Integer> land = new HashSet<>();
		if (ids == null) {
			return land;
		}
		for (int id : ids) {
			Province province = provinceManager.get(id);
			if (province != null && province.isValid() && province.getTerrain() != Terrain.SEA) {
				land.add(id);
			}
		}
		return land;
	}

	private static boolean isCoastal(ProvinceManager provinceManager, int landId) {
		Province land = provinceManager.get(landId);
		if (land == null || !land.isValid() || land.getTerrain() == Terrain.SEA) {
			return false;
		}
		for (int neighbourId : land.getNeighbours()) {
			Province neighbour = provinceManager.get(neighbourId);
			if (neighbour != null && neighbour.isValid() && neighbour.getTerrain() == Terrain.SEA) {
				return true;
			}
		}
		return false;
	}

	private static OptionalInt landBfs(
			ProvinceManager provinceManager,
			Set<Integer> starts,
			IntPredicate goal) {
		Queue<Integer> queue = new ArrayDeque<>(starts);
		Map<Integer, Integer> distance = new HashMap<>();
		for (int start : starts) {
			distance.put(start, 0);
		}
		while (!queue.isEmpty()) {
			int current = queue.poll();
			int currentDistance = distance.get(current);
			if (goal.test(current)) {
				return OptionalInt.of(currentDistance);
			}
			Province province = provinceManager.get(current);
			if (province == null || !province.isValid()) {
				continue;
			}
			for (int neighbourId : province.getNeighbours()) {
				if (distance.containsKey(neighbourId)) {
					continue;
				}
				Province neighbour = provinceManager.get(neighbourId);
				if (neighbour == null || !neighbour.isValid() || neighbour.getTerrain() == Terrain.SEA) {
					continue;
				}
				distance.put(neighbourId, currentDistance + 1);
				queue.add(neighbourId);
			}
		}
		return OptionalInt.empty();
	}
}
