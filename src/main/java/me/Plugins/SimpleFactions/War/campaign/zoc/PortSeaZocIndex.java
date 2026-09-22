package me.Plugins.SimpleFactions.War.campaign.zoc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;

public final class PortSeaZocIndex {
	public record OperationalPort(String id, Faction owner, int province, long completedAt) {
	}

	private final Map<Integer, OperationalPort> seaProvinceToPort;

	PortSeaZocIndex(Map<Integer, OperationalPort> seaProvinceToPort) {
		this.seaProvinceToPort = Map.copyOf(seaProvinceToPort);
	}

	public static List<OperationalPort> listOperationalPorts() {
		List<OperationalPort> ports = new ArrayList<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			for (Installation installation : faction.getInstallationHandler().getAll()) {
				if (installation.getKind() != InstallationKind.PORT) {
					continue;
				}
				ports.add(new OperationalPort(
						installation.getId(),
						faction,
						installation.getProvince(),
						installation.getCompletedAt()));
			}
		}
		return ports;
	}

	public static PortSeaZocIndex fromGameState() {
		return fromPorts(listOperationalPorts());
	}

	public static PortSeaZocIndex fromPorts(List<OperationalPort> ports) {
		if (ports == null || ports.isEmpty()) {
			return new PortSeaZocIndex(Map.of());
		}

		List<OperationalPort> sorted = new ArrayList<>(ports);
		sorted.sort(Comparator
				.comparingLong(OperationalPort::completedAt)
				.thenComparing(OperationalPort::id));

		ProvinceManager provinceManager = SimpleFactions.getInstance().getProvinceManager();
		Map<Integer, OperationalPort> seaProvinceToPort = new HashMap<>();
		for (OperationalPort port : sorted) {
			if (port == null || port.id() == null) {
				continue;
			}
			for (int seaProvinceId : computeSeaCoverage(port.province(), provinceManager)) {
				seaProvinceToPort.putIfAbsent(seaProvinceId, port);
			}
		}
		return new PortSeaZocIndex(seaProvinceToPort);
	}

	public Optional<OperationalPort> portForSeaProvince(int seaProvinceId) {
		return Optional.ofNullable(seaProvinceToPort.get(seaProvinceId));
	}

	public List<OperationalPort> portsCoveringSeaProvinces(Collection<Integer> seaProvinceIds) {
		if (seaProvinceIds == null || seaProvinceIds.isEmpty()) {
			return List.of();
		}
		Set<OperationalPort> ports = new LinkedHashSet<>();
		for (int seaProvinceId : seaProvinceIds) {
			portForSeaProvince(seaProvinceId).ifPresent(ports::add);
		}
		return List.copyOf(ports);
	}

	private static Set<Integer> computeSeaCoverage(int portLandProvince, ProvinceManager provinceManager) {
		if (provinceManager == null) {
			return Set.of();
		}
		Province portProvince = provinceManager.get(portLandProvince);
		if (portProvince == null || !portProvince.isValid()) {
			return Set.of();
		}

		int radius = Math.max(0, Cache.warPortSeaZocRadius);
		if (radius == 0) {
			return Set.of();
		}

		Set<Integer> covered = new HashSet<>();
		Queue<SeaNode> queue = new ArrayDeque<>();
		Set<Integer> visited = new HashSet<>();

		for (int neighbourId : portProvince.getNeighbours()) {
			Province neighbour = provinceManager.get(neighbourId);
			if (neighbour == null || !neighbour.isValid() || neighbour.getTerrain() != Terrain.SEA) {
				continue;
			}
			if (visited.add(neighbourId)) {
				queue.add(new SeaNode(neighbourId, 1));
			}
		}

		while (!queue.isEmpty()) {
			SeaNode current = queue.poll();
			covered.add(current.provinceId());
			if (current.depth() >= radius) {
				continue;
			}

			Province province = provinceManager.get(current.provinceId());
			if (province == null || !province.isValid()) {
				continue;
			}
			for (int neighbourId : province.getNeighbours()) {
				Province neighbour = provinceManager.get(neighbourId);
				if (neighbour == null || !neighbour.isValid() || neighbour.getTerrain() != Terrain.SEA) {
					continue;
				}
				if (visited.add(neighbourId)) {
					queue.add(new SeaNode(neighbourId, current.depth() + 1));
				}
			}
		}
		return covered;
	}

	private record SeaNode(int provinceId, int depth) {
	}
}
