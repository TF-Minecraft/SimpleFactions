package me.Plugins.SimpleFactions.War.campaign.zoc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Map.export.ZocRealm;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;

public final class FortZocIndex {
	public record OperationalFort(String id, Faction owner, int province, long completedAt) {
	}

	private final Map<Integer, OperationalFort> provinceToFort;
	private final Map<Integer, List<OperationalFort>> provinceToAllForts;

	FortZocIndex(Map<Integer, OperationalFort> provinceToFort) {
		this(provinceToFort, Map.of());
	}

	FortZocIndex(
			Map<Integer, OperationalFort> provinceToFort,
			Map<Integer, List<OperationalFort>> provinceToAllForts) {
		this.provinceToFort = Map.copyOf(provinceToFort);
		Map<Integer, List<OperationalFort>> covering = new HashMap<>();
		for (Map.Entry<Integer, List<OperationalFort>> entry : provinceToAllForts.entrySet()) {
			covering.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		this.provinceToAllForts = Map.copyOf(covering);
	}

	public static List<OperationalFort> listOperationalForts() {
		List<OperationalFort> forts = new ArrayList<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			for (Installation installation : faction.getInstallationHandler().getAll()) {
				if (installation.getKind() != InstallationKind.FORT) {
					continue;
				}
				forts.add(new OperationalFort(
						installation.getId(),
						faction,
						installation.getProvince(),
						installation.getCompletedAt()));
			}
		}
		return forts;
	}

	public static FortZocIndex fromGameState() {
		return fromForts(listOperationalForts());
	}

	public static FortZocIndex fromForts(List<OperationalFort> forts) {
		if (forts == null || forts.isEmpty()) {
			return new FortZocIndex(Map.of(), Map.of());
		}

		List<OperationalFort> sorted = new ArrayList<>(forts);
		sorted.sort(Comparator
				.comparingLong(OperationalFort::completedAt)
				.thenComparing(OperationalFort::id));

		Map<Integer, OperationalFort> provinceToFort = new HashMap<>();
		Map<Integer, List<OperationalFort>> provinceToAllForts = new HashMap<>();
		for (OperationalFort fort : sorted) {
			if (fort == null || fort.owner() == null || fort.id() == null) {
				continue;
			}
			for (int provinceId : ZocRealm.computeZocProvinces(fort.owner(), fort.province())) {
				provinceToFort.putIfAbsent(provinceId, fort);
				provinceToAllForts.computeIfAbsent(provinceId, ignored -> new ArrayList<>()).add(fort);
			}
		}
		return new FortZocIndex(provinceToFort, provinceToAllForts);
	}

	public Optional<OperationalFort> fortForProvince(int provinceId) {
		return Optional.ofNullable(provinceToFort.get(provinceId));
	}

	public List<OperationalFort> fortsCovering(int provinceId) {
		List<OperationalFort> covering = provinceToAllForts.get(provinceId);
		if (covering != null && !covering.isEmpty()) {
			return covering;
		}
		OperationalFort oldest = provinceToFort.get(provinceId);
		return oldest == null ? List.of() : List.of(oldest);
	}
}
