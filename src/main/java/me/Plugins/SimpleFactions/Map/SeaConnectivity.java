package me.Plugins.SimpleFactions.Map;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.Terrain;

public final class SeaConnectivity {
	private SeaConnectivity() {}

	public static boolean hasSeaConnection(Faction a, Faction b) {
		if (SimpleFactions.getInstance() == null) {
			return false;
		}
		return hasSeaConnection(SimpleFactions.getInstance().getProvinceManager(), a, b);
	}

	public static boolean hasSeaConnection(ProvinceManager provinceManager, Faction a, Faction b) {
		if (provinceManager == null || a == null || b == null) {
			return false;
		}
		return !sharedSeaProvinces(provinceManager, a.getProvinces(), b.getProvinces()).isEmpty();
	}

	public static Set<Integer> sharedSeaProvinces(
			ProvinceManager provinceManager,
			List<Integer> landA,
			List<Integer> landB) {
		if (provinceManager == null) {
			return Set.of();
		}
		Set<Integer> startSeas = adjacentSeaProvinces(provinceManager, landA);
		Set<Integer> goalSeas = adjacentSeaProvinces(provinceManager, landB);
		if (startSeas.isEmpty() || goalSeas.isEmpty()) {
			return Set.of();
		}
		Set<Integer> visited = floodSea(provinceManager, startSeas);
		for (int seaId : visited) {
			if (goalSeas.contains(seaId)) {
				return visited;
			}
		}
		return Set.of();
	}

	private static Set<Integer> floodSea(ProvinceManager provinceManager, Set<Integer> startSeas) {
		Queue<Integer> queue = new ArrayDeque<>(startSeas);
		Set<Integer> visited = new HashSet<>(startSeas);
		while (!queue.isEmpty()) {
			int currentId = queue.poll();
			Province current = provinceManager.get(currentId);
			if (current == null || !current.isValid()) {
				continue;
			}
			for (int neighbourId : current.getNeighbours()) {
				if (visited.contains(neighbourId)) {
					continue;
				}
				Province neighbour = provinceManager.get(neighbourId);
				if (neighbour == null || !neighbour.isValid() || neighbour.getTerrain() != Terrain.SEA) {
					continue;
				}
				visited.add(neighbourId);
				queue.add(neighbourId);
			}
		}
		return visited;
	}

	private static Set<Integer> adjacentSeaProvinces(ProvinceManager provinceManager, List<Integer> landIds) {
		Set<Integer> seas = new HashSet<>();
		if (landIds == null) {
			return seas;
		}
		for (int landId : landIds) {
			Province land = provinceManager.get(landId);
			if (land == null || !land.isValid() || land.getTerrain() == Terrain.SEA) {
				continue;
			}
			for (int neighbourId : land.getNeighbours()) {
				Province neighbour = provinceManager.get(neighbourId);
				if (neighbour != null && neighbour.isValid() && neighbour.getTerrain() == Terrain.SEA) {
					seas.add(neighbourId);
				}
			}
		}
		return seas;
	}
}
