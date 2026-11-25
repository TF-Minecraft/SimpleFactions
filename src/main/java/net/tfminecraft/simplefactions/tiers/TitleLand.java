package net.tfminecraft.simplefactions.tiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Free titles that can become one higher title (a Duchy from Counties, a Kingdom from Duchies, ...):
 * the titles whose land is connected, joined the same way as {@link CountyLand}. Untitled land of
 * the realm that touches them is part of the piece and joins the new title; untitled land elsewhere
 * stays untitled. A title whose own land is already split counts as one piece with all its parts.
 */
public final class TitleLand {
	private TitleLand() {
	}

	/** What a new higher title would take: the free lower titles and the untitled provinces of one connected piece. */
	public record Piece(List<Title> titles, List<Integer> provinces) {
		public static final Piece EMPTY = new Piece(List.of(), List.of());
	}

	/** The connected piece holding {@code start}; empty when start is in neither a free title nor untitled land. */
	public static Piece pieceAt(int start, List<Title> free, Collection<Integer> untitled,
			Function<Title, Collection<Integer>> landOf, CountyLand.Ties ties) {
		Set<Integer> group = new HashSet<>(CountyLand.groupOf(start, land(free, untitled, landOf), withinTitles(free, landOf, ties)));
		return piece(group, free, untitled, landOf);
	}

	/** The most free titles one connected piece holds. */
	public static int largestPiece(List<Title> free, Collection<Integer> untitled,
			Function<Title, Collection<Integer>> landOf, CountyLand.Ties ties) {
		int largest = 0;
		for (List<Integer> group : CountyLand.groups(land(free, untitled, landOf), withinTitles(free, landOf, ties))) {
			largest = Math.max(largest, piece(new HashSet<>(group), free, untitled, landOf).titles().size());
		}
		return largest;
	}

	/** {@code ties} plus links between the provinces of each free title, so a title split in two still counts as one piece. */
	private static CountyLand.Ties withinTitles(List<Title> free, Function<Title, Collection<Integer>> landOf, CountyLand.Ties ties) {
		Map<Integer, Set<Integer>> sameTitle = new HashMap<>();
		for (Title title : free) {
			Collection<Integer> land = landOf.apply(title);
			for (int province : land) {
				sameTitle.computeIfAbsent(province, key -> new LinkedHashSet<>()).addAll(land);
			}
		}
		return new CountyLand.Ties(province -> {
			Set<Integer> out = new LinkedHashSet<>(ties.borders().apply(province));
			out.addAll(sameTitle.getOrDefault(province, Set.of()));
			return out;
		}, ties.rivers());
	}

	private static Set<Integer> land(List<Title> free, Collection<Integer> untitled, Function<Title, Collection<Integer>> landOf) {
		Set<Integer> land = new LinkedHashSet<>();
		for (Title title : free) {
			land.addAll(landOf.apply(title));
		}
		land.addAll(untitled);
		return land;
	}

	private static Piece piece(Set<Integer> group, List<Title> free, Collection<Integer> untitled,
			Function<Title, Collection<Integer>> landOf) {
		List<Title> titles = new ArrayList<>();
		for (Title title : free) {
			if (landOf.apply(title).stream().anyMatch(group::contains)) {
				titles.add(title);
			}
		}
		if (titles.isEmpty()) {
			return Piece.EMPTY;
		}
		List<Integer> provinces = untitled.stream().filter(group::contains).distinct().toList();
		return new Piece(titles, provinces);
	}

	/** The connected piece around {@code province} that a new title above {@code lower} formed there would take. */
	public static Piece pieceAt(Faction f, Tier lower, int province) {
		return pieceAt(province, f.getFreeTitles(lower), f.getUntitledProvinces(), TitleManager::getProvinces, CountyLand.live());
	}

	/** The most {@code lower} titles one new title above them could take anywhere in {@code f}'s realm. */
	public static int largestPiece(Faction f, Tier lower) {
		return largestPiece(f.getFreeTitles(lower), f.getUntitledProvinces(), TitleManager::getProvinces, CountyLand.live());
	}
}
