package net.tfminecraft.simplefactions.war.declare;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;

/** {@link AnnexPartition.Graph} over the live province map; empty when no map is loaded. */
final class ProvinceAnnexGraph implements AnnexPartition.Graph {
	private final ProvinceManager provinces;

	ProvinceAnnexGraph(ProvinceManager provinces) {
		this.provinces = provinces;
	}

	@Override
	public Collection<Integer> landNeighbours(int province) {
		Province p = get(province);
		if (p == null) {
			return List.of();
		}
		Set<Integer> land = new TreeSet<>();
		for (int neighbour : p.getNeighbours()) {
			if (isLand(neighbour)) {
				land.add(neighbour);
			}
		}
		return land;
	}

	@Override
	public Collection<Integer> routeNeighbours(int province) {
		Province p = get(province);
		if (p == null) {
			return List.of();
		}
		Set<Integer> reach = new TreeSet<>();
		for (int neighbour : p.getNeighbours()) {
			if (isLand(neighbour)) {
				reach.add(neighbour);
				continue;
			}
			Province water = get(neighbour);
			if (water == null || !water.isSea()) {
				continue;
			}
			for (int across : water.getNeighbours()) {
				if (across != province && isLand(across)) {
					reach.add(across);
				}
			}
		}
		return reach;
	}

	@Override
	public int[] centre(int province) {
		Province p = get(province);
		if (p == null || (p.getCenterX() == 0 && p.getCenterZ() == 0)) {
			return null;
		}
		return new int[] {p.getCenterX(), p.getCenterZ()};
	}

	private boolean isLand(int province) {
		Province p = get(province);
		return p != null && p.isValid() && !p.isSea();
	}

	private Province get(int province) {
		return provinces == null ? null : provinces.get(province);
	}
}
