package net.tfminecraft.simplefactions.tiers;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Untitled land that can become one county: provinces joined by a land border or across a single
 * river or lake tile. Sea routes do not join land into a county.
 */
public final class CountyLand {
	private CountyLand() {
	}

	/** The connected part of {@code untitled} that holds {@code start}, in {@code untitled}'s order; empty when start is not in it. */
	public static List<Integer> groupOf(int start, Collection<Integer> untitled, IntFunction<Collection<Integer>> links) {
		Set<Integer> land = new LinkedHashSet<>(untitled);
		if (!land.contains(start)) {
			return List.of();
		}
		Set<Integer> reached = new LinkedHashSet<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		reached.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			for (int next : links.apply(queue.poll())) {
				if (land.contains(next) && reached.add(next)) {
					queue.add(next);
				}
			}
		}
		return land.stream().filter(reached::contains).toList();
	}

	/** The size of the biggest connected part of {@code untitled}. */
	public static int largestGroup(Collection<Integer> untitled, IntFunction<Collection<Integer>> links) {
		Set<Integer> left = new LinkedHashSet<>(untitled);
		int largest = 0;
		while (!left.isEmpty()) {
			List<Integer> group = groupOf(left.iterator().next(), left, links);
			left.removeAll(group);
			largest = Math.max(largest, group.size());
		}
		return largest;
	}

	/** The connected untitled land, own or a vassal's, around {@code province} that a county formed there would take. */
	public static List<Integer> countyAt(Faction f, int province) {
		return groupOf(province, f.getUntitledProvinces(), liveLinks());
	}

	/** The most provinces one new county could take anywhere in {@code f}'s realm. */
	public static int largestCounty(Faction f) {
		return largestGroup(f.getUntitledProvinces(), liveLinks());
	}

	/**
	 * Whether {@code f} must form a county before claiming more untitled land: true while its own
	 * untitled provinces hold a connected area as large as the untitled cap. A vassal's land counts
	 * only toward the vassal's own cap, and scattered land too small for a county never blocks.
	 */
	public static boolean mustFormCounty(Faction f) {
		return largestGroup(f.getProvinceHandler().getOwnUntitledProvinces(), liveLinks()) >= claimCap();
	}

	/** The untitled area size that blocks claiming; never below a county's form cost, so a blocked realm can always form one. */
	public static int claimCap() {
		Tier county = TierLoader.getByString("county");
		int formCost = county == null ? 0 : county.getFormCost();
		return Math.max(1, Math.max(Cache.maxUntitledProvinces, formCost));
	}

	/** Land provinces bordering {@code province}, directly or across one WATER tile, on the live map. */
	public static IntFunction<Collection<Integer>> liveLinks() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		ProvinceManager provinces = plugin == null ? null : plugin.getProvinceManager();
		return province -> links(provinces, province);
	}

	private static Collection<Integer> links(ProvinceManager provinces, int province) {
		if (provinces == null) {
			return List.of();
		}
		Set<Integer> out = new LinkedHashSet<>();
		for (int neighbour : provinces.get(province).getNeighbours()) {
			Province tile = provinces.get(neighbour);
			if (!tile.isSea()) {
				out.add(neighbour);
			} else if (tile.getTerrain() == Terrain.WATER) {
				for (int across : tile.getNeighbours()) {
					if (across != province && !provinces.get(across).isSea()) {
						out.add(across);
					}
				}
			}
		}
		return out;
	}
}
