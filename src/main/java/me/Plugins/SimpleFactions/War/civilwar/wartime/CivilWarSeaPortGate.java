package me.Plugins.SimpleFactions.War.civilwar.wartime;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.SeaConnectivity;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

public final class CivilWarSeaPortGate {
	private CivilWarSeaPortGate() {}

	public static boolean rebelsWouldHaveRequiredPort(
			Faction host,
			CivilWarLandSplitService.LandSplitPlan plan) {
		SimpleFactions plugin = SimpleFactions.getInstance();
		ProvinceManager pm = plugin == null ? null : plugin.getProvinceManager();
		return rebelsWouldHaveRequiredPort(pm, host, plan);
	}

	public static boolean rebelsWouldHaveRequiredPort(
			ProvinceManager pm,
			Faction host,
			CivilWarLandSplitService.LandSplitPlan plan) {
		if (plan == null || host == null) {
			return false;
		}
		if (pm == null) {
			return true;
		}
		int hostCapital = host.getCapital();
		int rebelCapital = plan.rebelProvinceIds().isEmpty() ? -1 : plan.rebelProvinceIds().get(0);
		if (hostCapital > 0 && rebelCapital > 0 && landReachable(pm, hostCapital, rebelCapital)) {
			return true;
		}
		Set<Integer> sharedSea = SeaConnectivity.sharedSeaProvinces(
				pm,
				plan.rebelProvinceIds(),
				plan.loyalProvinceIds());
		if (sharedSea.isEmpty()) {
			return true;
		}
		return hasPortOnSea(host, plan.rebelProvinceIds(), pm, sharedSea);
	}

	static boolean landReachable(ProvinceManager pm, int from, int to) {
		if (from == to) {
			return true;
		}
		Queue<Integer> queue = new ArrayDeque<>();
		Set<Integer> visited = new HashSet<>();
		queue.add(from);
		visited.add(from);
		while (!queue.isEmpty()) {
			int currentId = queue.poll();
			Province current = pm.get(currentId);
			if (current == null || !current.isValid()) {
				continue;
			}
			for (int neighbourId : current.getNeighbours()) {
				if (!visited.add(neighbourId)) {
					continue;
				}
				Province neighbour = pm.get(neighbourId);
				if (neighbour == null || !neighbour.isValid()) {
					continue;
				}
				if (neighbour.getTerrain() == Terrain.SEA) {
					continue;
				}
				if (neighbourId == to) {
					return true;
				}
				queue.add(neighbourId);
			}
		}
		return false;
	}

	static boolean hasPortOnSea(
			Faction host,
			List<Integer> rebelProvinces,
			ProvinceManager pm,
			Set<Integer> sharedSea) {
		InstallationHandler handler = host.getInstallationHandler();
		if (handler == null) {
			return false;
		}
		Set<Integer> rebelSet = new HashSet<>(rebelProvinces);
		for (Installation installation : handler.getAll()) {
			if (installation == null || installation.getKind() != InstallationKind.PORT) {
				continue;
			}
			if (!rebelSet.contains(installation.getProvince())) {
				continue;
			}
			Province land = pm.get(installation.getProvince());
			if (land == null || !land.isValid()) {
				continue;
			}
			for (int neighbourId : land.getNeighbours()) {
				if (sharedSea.contains(neighbourId)) {
					return true;
				}
			}
		}
		return false;
	}
}
