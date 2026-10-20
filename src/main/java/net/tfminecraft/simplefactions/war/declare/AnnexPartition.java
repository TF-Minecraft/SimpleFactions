package net.tfminecraft.simplefactions.war.declare;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Splits a de jure annex title between the attacker and the realms that hold it.
 * Each holder keeps its protected provinces (settlements and capital) plus the
 * cheapest "road" of title provinces that keeps everything else it holds legally
 * claimed; the attacker takes the rest. Topology comes from a {@link Graph} and
 * claim rules from each holder's {@link Legality}, so this class stays pure.
 */
public final class AnnexPartition {
	/** Cost of routing a road through a province the attacker would otherwise take. */
	static final double ROAD_BASE = 10;
	/** Discount for a road province bordering land its holder keeps anyway. */
	static final double KEPT_BORDER_DISCOUNT = 5;
	/** Discount for a road province on the edge of the title. */
	static final double TITLE_EDGE_DISCOUNT = 3;
	/** Added per bordering title province the holder would lose, to keep roads out of the interior. */
	static final double INTERIOR_PENALTY = 2;
	/** Cost of passing through a province the holder already keeps, so roads get shared. */
	static final double REUSE_COST = 1;
	/** Maximum added cost of turning back on the road; scales with the turn angle. */
	static final double TURN_PENALTY = 3;
	private static final int MAX_CLEANUP_ROUNDS = 32;
	private static final int NO_PREVIOUS = Integer.MIN_VALUE;

	private AnnexPartition() {}

	public interface Graph {
		/** Land provinces bordering {@code province}. */
		Collection<Integer> landNeighbours(int province);

		/** Land provinces across one WATER tile; any claimed province can bridge these. */
		Collection<Integer> waterNeighbours(int province);

		/** Land provinces across one SEA tile; only the capital's land can cross these. */
		Collection<Integer> seaNeighbours(int province);

		/** Map centre as {@code {x, z}}, or null when unknown. */
		int[] centre(int province);
	}

	@FunctionalInterface
	public interface Legality {
		/**
		 * Provinces the holder would keep claimed if it held exactly {@code holdings}. Guild capitals
		 * that would lose their seat count as not legal, even if the province stays claimed.
		 */
		Set<Integer> legal(Set<Integer> holdings);
	}

	/**
	 * One realm holding land in the title.
	 *
	 * @param holdings every province the holder holds, in and out of the title
	 * @param incoming the holder's provinces in the title
	 * @param protectedProvinces provinces the holder never loses (settlements, capital)
	 * @param capital the faction capital, or -1; only its land can cross the sea
	 * @param guildCapitals guild capitals, which stay seated only on land connected to the capital
	 */
	public record Holder(
			String id,
			Set<Integer> holdings,
			Set<Integer> incoming,
			Set<Integer> protectedProvinces,
			int capital,
			Set<Integer> guildCapitals,
			Legality legality) {}

	/**
	 * @param taken provinces the attacker receives
	 * @param kept incoming provinces the holders keep, including protected and road ones
	 * @param road kept provinces needed only to keep the holders' land connected
	 * @param protectedProvinces kept settlements and capitals
	 */
	public record Result(
			Set<Integer> taken,
			Set<Integer> kept,
			Set<Integer> road,
			Set<Integer> protectedProvinces) {
		public int incoming() {
			return taken.size() + kept.size();
		}
	}

	public static Result compute(Graph graph, Set<Integer> title, Set<Integer> attackerLand, List<Holder> holders) {
		List<Plan> plans = new ArrayList<>();
		for (Holder holder : holders) {
			plans.add(new Plan(holder));
		}
		plans.sort(Comparator.comparing(plan -> plan.holder.id()));
		for (Plan plan : plans) {
			planRoad(graph, title, plan);
		}
		cleanUp(graph, attackerLand, plans);

		Set<Integer> taken = new TreeSet<>();
		Set<Integer> kept = new TreeSet<>();
		Set<Integer> road = new TreeSet<>();
		Set<Integer> protectedProvinces = new TreeSet<>();
		for (Plan plan : plans) {
			taken.addAll(plan.taken());
			kept.addAll(plan.kept);
			road.addAll(plan.road);
			protectedProvinces.addAll(plan.protectedIn);
		}
		return new Result(taken, kept, road, protectedProvinces);
	}

	private static final class Plan {
		final Holder holder;
		final Set<Integer> incoming = new TreeSet<>();
		final Set<Integer> outside = new TreeSet<>();
		final Set<Integer> protectedIn = new TreeSet<>();
		final Set<Integer> kept = new TreeSet<>();
		final Set<Integer> road = new TreeSet<>();
		/** Provinces legal before the war; only these must stay legal. */
		final Set<Integer> baseline;
		private final Map<Set<Integer>, Set<Integer>> legalCache = new HashMap<>();

		Plan(Holder holder) {
			this.holder = holder;
			incoming.addAll(holder.incoming());
			Set<Integer> all = new HashSet<>(holder.holdings());
			all.addAll(incoming);
			for (int province : all) {
				if (!incoming.contains(province)) {
					outside.add(province);
				}
			}
			for (int province : holder.protectedProvinces()) {
				if (incoming.contains(province)) {
					protectedIn.add(province);
				}
			}
			kept.addAll(protectedIn);
			baseline = legal(all);
		}

		Set<Integer> legal(Set<Integer> holdings) {
			return legalCache.computeIfAbsent(Set.copyOf(holdings), key -> {
				Set<Integer> legal = holder.legality().legal(key);
				return legal == null ? Set.of() : legal;
			});
		}

		Set<Integer> missing(Set<Integer> holdings, Set<Integer> legal) {
			Set<Integer> missing = new TreeSet<>();
			for (int province : holdings) {
				if (baseline.contains(province) && !legal.contains(province)) {
					missing.add(province);
				}
			}
			return missing;
		}

		/** Owned land reachable from the capital without crossing water: the only land that may cross the sea. */
		Set<Integer> capitalBlob(Graph graph, Set<Integer> holdings) {
			Set<Integer> blob = new HashSet<>();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			if (holdings.contains(holder.capital())) {
				blob.add(holder.capital());
				queue.add(holder.capital());
			}
			while (!queue.isEmpty()) {
				for (int next : graph.landNeighbours(queue.poll())) {
					if (holdings.contains(next) && blob.add(next)) {
						queue.add(next);
					}
				}
			}
			return blob;
		}

		Set<Integer> holdingsWith(Set<Integer> inTitle) {
			Set<Integer> holdings = new HashSet<>(outside);
			holdings.addAll(inTitle);
			return holdings;
		}

		Set<Integer> taken() {
			Set<Integer> taken = new TreeSet<>(incoming);
			taken.removeAll(kept);
			return taken;
		}

		/** Whether keeping {@code inTitle} leaves every pre-war legal holding, and {@code added}, legal. */
		boolean holds(Set<Integer> inTitle, Set<Integer> added) {
			Set<Integer> holdings = holdingsWith(inTitle);
			Set<Integer> legal = legal(holdings);
			for (int province : holdings) {
				if ((baseline.contains(province) || added.contains(province)) && !legal.contains(province)) {
					return false;
				}
			}
			return true;
		}
	}

	// Road

	private static void planRoad(Graph graph, Set<Integer> title, Plan plan) {
		Set<Integer> banned = new HashSet<>();
		while (true) {
			Set<Integer> holdings = plan.holdingsWith(plan.kept);
			Set<Integer> legal = plan.legal(holdings);
			Set<Integer> missing = plan.missing(holdings, legal);
			if (missing.isEmpty()) {
				break;
			}
			List<Integer> path = route(graph, title, plan, holdings, legal, missing, banned);
			List<Integer> added = new ArrayList<>();
			for (int province : path == null ? List.<Integer>of() : path) {
				if (plan.incoming.contains(province) && plan.kept.add(province)) {
					added.add(province);
				}
			}
			if (added.isEmpty()) {
				// No route under the claim rules; holding everything is legal by definition,
				// and the cleanup passes trim what is not needed.
				plan.kept.addAll(plan.incoming);
				break;
			}
			if (Collections.disjoint(plan.legal(plan.holdingsWith(plan.kept)), missing)) {
				// The path legalises nothing after all; route around it instead.
				plan.kept.removeAll(added);
				banned.addAll(added);
			} else {
				plan.road.addAll(added);
			}
		}
		trimRoad(graph, title, plan);
	}

	/** Drops road provinces a later road made redundant, interior ones first. */
	private static void trimRoad(Graph graph, Set<Integer> title, Plan plan) {
		boolean changed = true;
		while (changed) {
			changed = false;
			Set<Integer> holdings = plan.holdingsWith(plan.kept);
			List<Integer> order = new ArrayList<>(plan.road);
			order.sort(Comparator.comparingDouble((Integer p) -> -candidateCost(graph, title, holdings, p))
					.thenComparing(Comparator.naturalOrder()));
			for (int province : order) {
				Set<Integer> next = new TreeSet<>(plan.kept);
				next.remove(province);
				if (plan.holds(next, Set.of())) {
					plan.kept.remove(province);
					plan.road.remove(province);
					changed = true;
					break;
				}
			}
		}
	}

	private enum Crossing { LAND, WATER, SEA }

	/** A search position; {@code blob} is whether the province would join the capital's land. */
	private record Step(int province, int previous, boolean blob) {}

	private record State(double cost, Step step) {}

	private static final Comparator<State> STATE_ORDER = Comparator.comparingDouble(State::cost)
			.thenComparingInt((State state) -> state.step().province())
			.thenComparingInt(state -> state.step().previous())
			.thenComparing(state -> !state.step().blob());

	/**
	 * Cheapest path from the legal set to any missing province, excluding the legal start. Edges follow
	 * the claim rules: land and WATER from anywhere, SEA only from the capital's land, and a guild
	 * capital only counts once reached over land from the capital.
	 */
	private static List<Integer> route(
			Graph graph,
			Set<Integer> title,
			Plan plan,
			Set<Integer> holdings,
			Set<Integer> legal,
			Set<Integer> missing,
			Set<Integer> banned) {
		Set<Integer> blob = plan.capitalBlob(graph, holdings);
		Map<Step, Double> best = new HashMap<>();
		Map<Step, Step> parent = new HashMap<>();
		Set<Step> settled = new HashSet<>();
		Map<Integer, Double> nodeCosts = new HashMap<>();
		PriorityQueue<State> queue = new PriorityQueue<>(STATE_ORDER);
		for (int source : new TreeSet<>(legal)) {
			if (holdings.contains(source)) {
				Step start = new Step(source, NO_PREVIOUS, blob.contains(source));
				best.put(start, 0.0);
				queue.add(new State(0, start));
			}
		}
		while (!queue.isEmpty()) {
			State state = queue.poll();
			Step step = state.step();
			if (!settled.add(step)) {
				continue;
			}
			if (missing.contains(step.province())
					&& (step.blob() || !plan.holder.guildCapitals().contains(step.province()))) {
				LinkedList<Integer> path = new LinkedList<>();
				for (Step at = step; at != null; at = parent.get(at)) {
					if (!legal.contains(at.province())) {
						path.addFirst(at.province());
					}
				}
				return path;
			}
			for (Map.Entry<Integer, Crossing> edge : crossings(graph, step).entrySet()) {
				int next = edge.getKey();
				boolean candidate = plan.incoming.contains(next) && !banned.contains(next);
				if (legal.contains(next) || !(candidate || holdings.contains(next))) {
					continue;
				}
				boolean nextBlob = (step.blob() && edge.getValue() == Crossing.LAND)
						|| !Collections.disjoint(graph.landNeighbours(next), blob);
				double cost = state.cost()
						+ nodeCosts.computeIfAbsent(next, n -> nodeCost(graph, title, holdings, n))
						+ turnCost(graph, step.previous(), step.province(), next);
				Step nextStep = new Step(next, step.province(), nextBlob);
				Double known = best.get(nextStep);
				if (known == null || cost < known) {
					best.put(nextStep, cost);
					parent.put(nextStep, step);
					queue.add(new State(cost, nextStep));
				}
			}
		}
		return null;
	}

	/** Reachable land by the best crossing; SEA only from the capital's land. */
	private static Map<Integer, Crossing> crossings(Graph graph, Step step) {
		Map<Integer, Crossing> out = new java.util.TreeMap<>();
		if (step.blob()) {
			for (int next : graph.seaNeighbours(step.province())) {
				out.put(next, Crossing.SEA);
			}
		}
		for (int next : graph.waterNeighbours(step.province())) {
			out.put(next, Crossing.WATER);
		}
		for (int next : graph.landNeighbours(step.province())) {
			out.put(next, Crossing.LAND);
		}
		return out;
	}

	private static double nodeCost(Graph graph, Set<Integer> title, Set<Integer> holdings, int province) {
		return holdings.contains(province) ? REUSE_COST : candidateCost(graph, title, holdings, province);
	}

	private static double candidateCost(Graph graph, Set<Integer> title, Set<Integer> holdings, int province) {
		double cost = ROAD_BASE;
		boolean bordersKept = false;
		boolean titleEdge = false;
		int interior = 0;
		for (int neighbour : graph.landNeighbours(province)) {
			if (holdings.contains(neighbour)) {
				bordersKept = true;
			} else if (title.contains(neighbour)) {
				interior++;
			} else {
				titleEdge = true;
			}
		}
		if (bordersKept) {
			cost -= KEPT_BORDER_DISCOUNT;
		} else if (titleEdge) {
			cost -= TITLE_EDGE_DISCOUNT;
		}
		return cost + INTERIOR_PENALTY * interior;
	}

	private static double turnCost(Graph graph, int previous, int at, int next) {
		if (previous == NO_PREVIOUS) {
			return 0;
		}
		int[] a = graph.centre(previous);
		int[] b = graph.centre(at);
		int[] c = graph.centre(next);
		if (a == null || b == null || c == null) {
			return 0;
		}
		double inX = b[0] - a[0];
		double inZ = b[1] - a[1];
		double outX = c[0] - b[0];
		double outZ = c[1] - b[1];
		double lengths = Math.hypot(inX, inZ) * Math.hypot(outX, outZ);
		double cos = lengths == 0 ? 1 : (inX * outX + inZ * outZ) / lengths;
		return TURN_PENALTY * (1 - cos) / 2;
	}

	// Cleanup

	private static void cleanUp(Graph graph, Set<Integer> attackerLand, List<Plan> plans) {
		boolean changed = true;
		for (int round = 0; changed && round < MAX_CLEANUP_ROUNDS; round++) {
			changed = returnSlivers(graph, plans);
			changed |= returnEnclaves(graph, attackerLand, plans);
			changed |= takeSlivers(graph, attackerLand, plans);
		}
	}

	/** A taken province mostly surrounded by its holder's land goes back to the holder. */
	private static boolean returnSlivers(Graph graph, List<Plan> plans) {
		boolean changed = false;
		for (Plan plan : plans) {
			for (int province : plan.taken()) {
				Collection<Integer> land = graph.landNeighbours(province);
				int keptAround = 0;
				for (int neighbour : land) {
					if (plan.kept.contains(neighbour) || plan.outside.contains(neighbour)) {
						keptAround++;
					}
				}
				if (land.isEmpty() || keptAround * 3 < land.size() * 2) {
					continue;
				}
				Set<Integer> next = new TreeSet<>(plan.kept);
				next.add(province);
				if (plan.holds(next, Set.of(province))) {
					plan.kept.add(province);
					changed = true;
				}
			}
		}
		return changed;
	}

	/** Taken land cut off from the attacker by land stays with its holder when that is legal. */
	private static boolean returnEnclaves(Graph graph, Set<Integer> attackerLand, List<Plan> plans) {
		List<Set<Integer>> components = components(graph, allTaken(plans));
		List<Set<Integer>> unanchored = new ArrayList<>();
		boolean anyAnchored = false;
		for (Set<Integer> component : components) {
			if (touches(graph, component, attackerLand)) {
				anyAnchored = true;
			} else {
				unanchored.add(component);
			}
		}
		if (!anyAnchored) {
			// The attacker borders none of it by land; leave the split to the road.
			return false;
		}
		boolean changed = false;
		for (Set<Integer> component : unanchored) {
			for (Plan plan : plans) {
				Set<Integer> part = new TreeSet<>(component);
				part.retainAll(plan.taken());
				if (part.isEmpty()) {
					continue;
				}
				Set<Integer> next = new TreeSet<>(plan.kept);
				next.addAll(part);
				if (plan.holds(next, part)) {
					plan.kept.addAll(part);
					changed = true;
				}
			}
		}
		return changed;
	}

	/** A kept province mostly surrounded by the attacker's land goes to the attacker when nothing kept breaks. */
	private static boolean takeSlivers(Graph graph, Set<Integer> attackerLand, List<Plan> plans) {
		boolean changed = false;
		for (Plan plan : plans) {
			for (int province : new ArrayList<>(plan.kept)) {
				if (plan.protectedIn.contains(province) || plan.road.contains(province)) {
					continue;
				}
				Collection<Integer> land = graph.landNeighbours(province);
				Set<Integer> taken = allTaken(plans);
				int takenAround = 0;
				for (int neighbour : land) {
					if (taken.contains(neighbour) || attackerLand.contains(neighbour)) {
						takenAround++;
					}
				}
				if (land.isEmpty() || takenAround * 3 < land.size() * 2
						|| !wouldBeAnchored(graph, attackerLand, taken, province)) {
					continue;
				}
				Set<Integer> next = new TreeSet<>(plan.kept);
				next.remove(province);
				if (plan.holds(next, Set.of())) {
					plan.kept.remove(province);
					changed = true;
				}
			}
		}
		return changed;
	}

	/** Whether taking {@code province} joins land the attacker reaches, so the enclave pass will not undo it. */
	private static boolean wouldBeAnchored(Graph graph, Set<Integer> attackerLand, Set<Integer> taken, int province) {
		boolean anyAnchored = false;
		Set<Integer> withProvince = new TreeSet<>(taken);
		withProvince.add(province);
		for (Set<Integer> component : components(graph, withProvince)) {
			boolean anchored = touches(graph, component, attackerLand);
			anyAnchored |= anchored;
			if (component.contains(province) && anchored) {
				return true;
			}
		}
		return !anyAnchored;
	}

	private static Set<Integer> allTaken(List<Plan> plans) {
		Set<Integer> taken = new TreeSet<>();
		for (Plan plan : plans) {
			taken.addAll(plan.taken());
		}
		return taken;
	}

	private static boolean touches(Graph graph, Set<Integer> component, Set<Integer> land) {
		for (int province : component) {
			for (int neighbour : graph.landNeighbours(province)) {
				if (land.contains(neighbour)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Land-connected groups of {@code provinces}, in order of their lowest id. */
	private static List<Set<Integer>> components(Graph graph, Set<Integer> provinces) {
		List<Set<Integer>> components = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (int start : new TreeSet<>(provinces)) {
			if (!seen.add(start)) {
				continue;
			}
			Set<Integer> component = new TreeSet<>();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				int province = queue.poll();
				component.add(province);
				for (int neighbour : graph.landNeighbours(province)) {
					if (provinces.contains(neighbour) && seen.add(neighbour)) {
						queue.add(neighbour);
					}
				}
			}
			components.add(component);
		}
		return components;
	}
}
