package me.Plugins.SimpleFactions.War.pathfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.enums.Terrain;

public class ProvincePathfinder {
	private final ProvinceManager pm;
	private final ProvinceOwnerLookup owners;

	public ProvincePathfinder(ProvinceManager pm, ProvinceOwnerLookup owners) {
		this.pm = pm;
		this.owners = owners;
	}

	public PathfinderResult findRoute(
			int from,
			int to,
			PathfinderPass pass,
			BelligerentTerritory territory) {
		PathfinderResult result = dijkstra(from, to, pass, territory);
		return result.withStartProvinceId(from);
	}

	public PathfinderResult findRouteWithFallback(
			int from,
			int to,
			BelligerentTerritory territory) {
		List<PathfinderPass> passes = new ArrayList<>();
		passes.add(PathfinderPass.LAND_NO_NEUTRAL);
		if (Cache.warPathfinderSeaPassEnabled) {
			passes.add(PathfinderPass.SEA_NO_NEUTRAL);
		}

		for (PathfinderPass pass : passes) {
			PathfinderResult result = findRoute(from, to, pass, territory);
			if (result.isFound()) {
				return result;
			}
		}
		return PathfinderResult.notFound();
	}

	public PathfinderResult computeCampaignLine(War war, int objectiveProvinceId) {
		BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners);
		List<Integer> candidates = territory.findInvasionEntryProvinces(pm);
		boolean seaContactFallback = candidates.isEmpty();
		if (seaContactFallback) {
			candidates = territory.findSeaInvasionEntryProvinces(pm);
			if (candidates.isEmpty()) {
				candidates = territory.findDefenderProvinces(pm);
			}
		}
		LogManager.line(
				"pathfinder invasion entry candidates=%s seaContactFallback=%s objective=%d",
				candidates,
				seaContactFallback,
				objectiveProvinceId);

		int attackerCapital = resolveAttackerCapital(war);

		CampaignLineChoice best = CampaignLineChoice.notFound();
		CampaignLineChoice objectiveSelfFallback = CampaignLineChoice.notFound();
		for (int candidate : candidates) {
			PathfinderResult toObjective = findRouteWithFallback(candidate, objectiveProvinceId, territory);
			if (!toObjective.isFound()) {
				LogManager.line("pathfinder candidate=%d SKIP no route to objective", candidate);
				continue;
			}
			double capitalReach = resolveCapitalReachCost(attackerCapital, candidate, territory);
			LogManager.line(
					"pathfinder candidate=%d capitalReach=%s objectiveCost=%s objectivePath=%s",
					candidate,
					formatCost(capitalReach),
					formatCost(toObjective.getTotalCost()),
					toObjective.getPath());
			if (candidate == objectiveProvinceId) {
				objectiveSelfFallback = new CampaignLineChoice(candidate, toObjective, capitalReach);
				continue;
			}
			CampaignLineChoice choice = new CampaignLineChoice(candidate, toObjective, capitalReach);
			if (isBetterCampaignLineChoice(choice, seaContactFallback, best, territory)) {
				best = choice;
			}
		}
		if (!best.isFound() && objectiveSelfFallback.isFound()) {
			LogManager.line(
					"pathfinder selected objective self-path borderStart=%d path=%s",
					objectiveSelfFallback.startProvinceId(),
					objectiveSelfFallback.toObjective().getPath());
			return objectiveSelfFallback.toObjective();
		}
		if (best.isFound()) {
			LogManager.line(
					"pathfinder selected borderStart=%d capitalReach=%s objectiveCost=%s path=%s",
					best.startProvinceId(),
					formatCost(best.capitalReachCost()),
					formatCost(best.toObjective().getTotalCost()),
					best.toObjective().getPath());
		} else {
			LogManager.line("pathfinder no border route found");
		}
		return best.isFound() ? best.toObjective() : PathfinderResult.notFound();
	}

	private double resolveCapitalReachCost(int attackerCapital, int candidate, BelligerentTerritory territory) {
		if (attackerCapital <= 0) {
			return Double.POSITIVE_INFINITY;
		}
		PathfinderResult toBorder = findRouteWithFallback(attackerCapital, candidate, territory);
		return toBorder.isFound() ? toBorder.getTotalCost() : Double.POSITIVE_INFINITY;
	}

	private boolean isBetterCampaignLineChoice(
			CampaignLineChoice candidate,
			boolean seaContactFallback,
			CampaignLineChoice currentBest,
			BelligerentTerritory territory) {
		if (!currentBest.isFound()) {
			return true;
		}
		boolean capitalGuided = !Double.isInfinite(candidate.capitalReachCost())
				|| !Double.isInfinite(currentBest.capitalReachCost());
		if (capitalGuided) {
			if (candidate.capitalReachCost() < currentBest.capitalReachCost()) {
				return true;
			}
			if (candidate.capitalReachCost() > currentBest.capitalReachCost()) {
				return false;
			}
			double candidateObjectiveCost = candidate.toObjective().getTotalCost();
			double bestObjectiveCost = currentBest.toObjective().getTotalCost();
			if (candidateObjectiveCost > bestObjectiveCost) {
				return true;
			}
			if (candidateObjectiveCost < bestObjectiveCost) {
				return false;
			}
		}
		return isBetterCandidate(
				candidate.toObjective(),
				candidate.startProvinceId(),
				territory,
				seaContactFallback,
				currentBest.toObjective());
	}

	private int resolveAttackerCapital(War war) {
		if (war == null || war.getAttackers() == null || war.getAttackers().getLeader() == null) {
			return -1;
		}
		return war.getAttackers().getLeader().getCapital();
	}

	private static String formatCost(double cost) {
		return Double.isInfinite(cost) ? "unreachable" : String.valueOf(cost);
	}

	private record CampaignLineChoice(int startProvinceId, PathfinderResult toObjective, double capitalReachCost) {
		static CampaignLineChoice notFound() {
			return new CampaignLineChoice(-1, PathfinderResult.notFound(), Double.POSITIVE_INFINITY);
		}

		boolean isFound() {
			return toObjective.isFound();
		}
	}

	private boolean isBetterCandidate(
			PathfinderResult candidateResult,
			int candidateStart,
			BelligerentTerritory territory,
			boolean seaContactFallback,
			PathfinderResult currentBest) {
		if (!currentBest.isFound()) {
			return true;
		}
		if (candidateResult.getTotalCost() < currentBest.getTotalCost()) {
			return true;
		}
		if (candidateResult.getTotalCost() > currentBest.getTotalCost()) {
			return false;
		}
		if (seaContactFallback) {
			boolean candidateSea = territory.isAdjacentToSea(pm, candidateStart);
			boolean bestSea = territory.isAdjacentToSea(pm, currentBest.getStartProvinceId());
			if (candidateSea && !bestSea) {
				return true;
			}
			if (!candidateSea && bestSea) {
				return false;
			}
		}
		return candidateStart < currentBest.getStartProvinceId();
	}

	private PathfinderResult dijkstra(
			int from,
			int to,
			PathfinderPass pass,
			BelligerentTerritory territory) {
		Province start = pm.get(from);
		Province end = pm.get(to);
		if (start.getId() == 0 || end.getId() == 0) {
			return PathfinderResult.notFound();
		}
		if (!canTraverse(start, pass, territory) || !canTraverse(end, pass, territory)) {
			return PathfinderResult.notFound();
		}
		if (from == to) {
			return PathfinderResult.found(List.of(from), 0.0, pass, from);
		}

		Map<Integer, Double> dist = new HashMap<>();
		Map<Integer, Integer> previous = new HashMap<>();
		PriorityQueue<Integer> queue = new PriorityQueue<>(
				Comparator.comparingDouble(id -> dist.getOrDefault(id, Double.POSITIVE_INFINITY)));

		dist.put(from, 0.0);
		queue.add(from);

		while (!queue.isEmpty()) {
			int currentId = queue.poll();
			double currentCost = dist.getOrDefault(currentId, Double.POSITIVE_INFINITY);
			if (currentId == to) {
				break;
			}

			Province current = pm.get(currentId);
			if (current.getId() == 0) {
				continue;
			}

			for (int neighbourId : current.getNeighbours()) {
				Province neighbour = pm.get(neighbourId);
				if (neighbour.getId() == 0) {
					continue;
				}
				if (!canTraverse(neighbour, pass, territory)) {
					continue;
				}

				double stepCost = enterCost(neighbour, pass, territory);
				if (Double.isInfinite(stepCost)) {
					continue;
				}

				double nextCost = currentCost + stepCost;
				if (nextCost >= dist.getOrDefault(neighbourId, Double.POSITIVE_INFINITY)) {
					continue;
				}

				dist.put(neighbourId, nextCost);
				previous.put(neighbourId, currentId);
				queue.add(neighbourId);
			}
		}

		if (!dist.containsKey(to)) {
			return PathfinderResult.notFound();
		}

		List<Integer> path = reconstructPath(previous, from, to);
		double totalCost = dist.getOrDefault(to, Double.POSITIVE_INFINITY);
		return PathfinderResult.found(path, totalCost, pass, from);
	}

	private List<Integer> reconstructPath(Map<Integer, Integer> previous, int from, int to) {
		List<Integer> path = new ArrayList<>();
		Integer current = to;
		while (current != null) {
			path.add(0, current);
			if (current == from) {
				break;
			}
			current = previous.get(current);
		}
		return path;
	}

	private boolean canTraverse(Province province, PathfinderPass pass, BelligerentTerritory territory) {
		Terrain terrain = province.getTerrain();
		switch (pass) {
			case LAND_NO_NEUTRAL:
				return terrain != Terrain.SEA && !territory.isForeignNation(province.getId());
			case SEA_NO_NEUTRAL:
				if (terrain == Terrain.SEA) {
					return true;
				}
				return !territory.isForeignNation(province.getId());
			case LAND_NEUTRAL_PENALTY:
				return terrain != Terrain.SEA;
			default:
				return false;
		}
	}

	private double enterCost(Province province, PathfinderPass pass, BelligerentTerritory territory) {
		return terrainEnterCost(province.getTerrain());
	}

	static double terrainEnterCost(Terrain terrain) {
		if (Cache.warPathfinderWaterCost > 0 && terrain == Terrain.WATER) {
			return Cache.warPathfinderWaterCost;
		}
		double carry = Cache.getTradeCarry(terrain);
		return carry > 0 ? 1.0 / carry : Double.POSITIVE_INFINITY;
	}
}
