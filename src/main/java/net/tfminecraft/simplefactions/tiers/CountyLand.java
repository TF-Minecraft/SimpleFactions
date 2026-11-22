package net.tfminecraft.simplefactions.tiers;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

	/**
	 * How provinces touch: the land each one borders, directly or across a river, and the river or
	 * lake tiles each one faces. Two provinces facing the same river tile are joined even when the
	 * tile itself does not list them.
	 */
	public record Ties(IntFunction<Collection<Integer>> borders, IntFunction<Collection<Integer>> rivers) {
	}

	/** The connected part of {@code untitled} that holds {@code start}, in {@code untitled}'s order; empty when start is not in it. */
	public static List<Integer> groupOf(int start, Collection<Integer> untitled, Ties ties) {
		Set<Integer> land = new LinkedHashSet<>(untitled);
		return groupOf(start, land, joins(land, ties));
	}

	/** The size of the biggest connected part of {@code untitled}. */
	public static int largestGroup(Collection<Integer> untitled, Ties ties) {
		Set<Integer> left = new LinkedHashSet<>(untitled);
		Map<Integer, Set<Integer>> joins = joins(left, ties);
		int largest = 0;
		while (!left.isEmpty()) {
			List<Integer> group = groupOf(left.iterator().next(), left, joins);
			left.removeAll(group);
			largest = Math.max(largest, group.size());
		}
		return largest;
	}

	private static List<Integer> groupOf(int start, Set<Integer> land, Map<Integer, Set<Integer>> joins) {
		if (!land.contains(start)) {
			return List.of();
		}
		Set<Integer> reached = new LinkedHashSet<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		reached.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			for (int next : joins.get(queue.poll())) {
				if (reached.add(next)) {
					queue.add(next);
				}
			}
		}
		return land.stream().filter(reached::contains).toList();
	}

	/** Links between provinces of {@code land}, both ways, so a border or river stored on one side only still joins them. */
	private static Map<Integer, Set<Integer>> joins(Set<Integer> land, Ties ties) {
		Map<Integer, Set<Integer>> joins = new HashMap<>();
		for (int province : land) {
			joins.put(province, new LinkedHashSet<>());
		}
		Map<Integer, Integer> firstOnRiver = new HashMap<>();
		for (int province : land) {
			for (int next : ties.borders().apply(province)) {
				if (land.contains(next)) {
					join(joins, province, next);
				}
			}
			for (int river : ties.rivers().apply(province)) {
				Integer first = firstOnRiver.putIfAbsent(river, province);
				if (first != null) {
					join(joins, province, first);
				}
			}
		}
		return joins;
	}

	private static void join(Map<Integer, Set<Integer>> joins, int a, int b) {
		joins.get(a).add(b);
		joins.get(b).add(a);
	}

	/** The connected untitled land, own or a vassal's, around {@code province} that a county formed there would take. */
	public static List<Integer> countyAt(Faction f, int province) {
		return groupOf(province, f.getUntitledProvinces(), live());
	}

	/** The most provinces one new county could take anywhere in {@code f}'s realm. */
	public static int largestCounty(Faction f) {
		return largestGroup(f.getUntitledProvinces(), live());
	}

	/**
	 * Whether {@code f} must form a county before claiming more untitled land: true while its own
	 * untitled provinces hold a connected area as large as the untitled cap. A vassal's land counts
	 * only toward the vassal's own cap, and scattered land too small for a county never blocks.
	 */
	public static boolean mustFormCounty(Faction f) {
		return largestGroup(f.getProvinceHandler().getOwnUntitledProvinces(), live()) >= claimCap();
	}

	/** The untitled area size that blocks claiming; never below a county's form cost, so a blocked realm can always form one. */
	public static int claimCap() {
		Tier county = TierLoader.getByString("county");
		int formCost = county == null ? 0 : county.getFormCost();
		return Math.max(1, Math.max(Cache.maxUntitledProvinces, formCost));
	}

	/** How provinces touch on the live map; with no map loaded, nothing touches. */
	public static Ties live() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		ProvinceManager provinces = plugin == null ? null : plugin.getProvinceManager();
		return new Ties(province -> borders(provinces, province), province -> rivers(provinces, province));
	}

	private static Collection<Integer> rivers(ProvinceManager provinces, int province) {
		if (provinces == null) {
			return List.of();
		}
		return provinces.get(province).getNeighbours().stream()
				.filter(neighbour -> provinces.get(neighbour).getTerrain() == Terrain.WATER)
				.toList();
	}

	/** Land bordering {@code province}, directly or across a WATER tile that lists it. */
	private static Collection<Integer> borders(ProvinceManager provinces, int province) {
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
