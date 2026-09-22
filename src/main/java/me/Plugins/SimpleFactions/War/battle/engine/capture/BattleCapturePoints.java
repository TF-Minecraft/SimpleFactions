package me.Plugins.SimpleFactions.War.battle.engine.capture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

public final class BattleCapturePoints {
	private BattleCapturePoints() {
	}

	public static String letterForIndex(int index) {
		if (index < 0) {
			return "A";
		}
		StringBuilder label = new StringBuilder();
		int value = index + 1;
		while (value > 0) {
			value--;
			label.insert(0, (char) ('A' + (value % 26)));
			value /= 26;
		}
		return label.toString();
	}

	public static int nextGlobalSequenceIndex(Battle battle) {
		int max = -1;
		for (CapturePoint point : battle.getPoints()) {
			max = Math.max(max, point.getSequenceIndex());
		}
		return max + 1;
	}

	public static CapturePoint createAtPlayer(Battle battle, String sideId, Location location, BattleSide controller) {
		CapturePoint point = new CapturePoint(
				letterForIndex(battle.getPoints().size()),
				location,
				controller,
				100);
		point.setAdvanceSideId(sideId);
		point.setSequenceIndex(nextGlobalSequenceIndex(battle));
		return point;
	}

	public static void afterPointListChanged(Battle battle) {
		if (battle == null) {
			return;
		}
		if (battle.isSequentialCapture()) {
			syncLinearChain(battle);
		} else {
			compressGlobalLetters(battle);
		}
	}

	public static void compressGlobalLetters(Battle battle) {
		if (battle == null || battle.getPoints().isEmpty()) {
			return;
		}
		List<CapturePoint> ordered = new ArrayList<>(battle.getPoints());
		ordered.sort(Comparator.comparingInt(CapturePoint::getSequenceIndex));
		for (int i = 0; i < ordered.size(); i++) {
			CapturePoint point = ordered.get(i);
			point.setSequenceIndex(i);
			point.setId(letterForIndex(i));
		}
	}

	/**
	 * Orders capture points A..N along the battle axis: closest to defender spawn first,
	 * closest to attacker spawn last. When both spawns are set, middle points follow
	 * projection along that line; with defender spawn only, falls back to greedy nearest-neighbor.
	 */
	public static void syncLinearChain(Battle battle) {
		if (battle == null || battle.getPoints().isEmpty()) {
			return;
		}
		List<CapturePoint> points = new ArrayList<>(battle.getPoints());
		Location defenderSpawn = resolveDefenderSpawn(battle);
		if (defenderSpawn == null) {
			compressGlobalLetters(battle);
			return;
		}

		Location attackerSpawn = resolveAttackerSpawn(battle);
		CapturePoint first = closestPoint(points, defenderSpawn);
		if (first == null) {
			return;
		}

		List<CapturePoint> ordered;
		if (attackerSpawn != null) {
			CapturePoint last = closestPoint(points, attackerSpawn);
			if (last == null) {
				ordered = greedyChainFromAnchor(points, first);
			} else if (first == last || points.size() == 1) {
				ordered = sortByAxisProjection(points, defenderSpawn, attackerSpawn);
			} else {
				ordered = orderPinnedEndpoints(points, defenderSpawn, attackerSpawn, first, last);
			}
		} else {
			ordered = greedyChainFromAnchor(points, first);
		}

		assignSequenceLabels(ordered);
	}

	public static boolean removePoint(Battle battle, CapturePoint point) {
		if (battle == null || point == null) {
			return false;
		}
		if (!battle.removePointById(point.getId())) {
			return false;
		}
		afterPointListChanged(battle);
		return true;
	}

	public static Location resolveDefenderSpawn(Battle battle) {
		if (battle == null) {
			return null;
		}
		BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);
		if (defender != null && defender.getSpawn() != null) {
			return defender.getSpawn();
		}
		return null;
	}

	static Location resolveAttackerSpawn(Battle battle) {
		if (battle == null) {
			return null;
		}
		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		if (attacker != null && attacker.getSpawn() != null) {
			return attacker.getSpawn();
		}
		return null;
	}

	static CapturePoint closestPoint(List<CapturePoint> points, Location reference) {
		CapturePoint closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (CapturePoint point : points) {
			double distance = distanceXZ(reference, point.getLoc());
			if (distance < closestDistance) {
				closestDistance = distance;
				closest = point;
			}
		}
		return closest;
	}

	static List<CapturePoint> orderPinnedEndpoints(
			List<CapturePoint> points,
			Location axisStart,
			Location axisEnd,
			CapturePoint first,
			CapturePoint last) {
		List<CapturePoint> middle = new ArrayList<>();
		for (CapturePoint point : points) {
			if (point != first && point != last) {
				middle.add(point);
			}
		}
		middle.sort(Comparator.comparingDouble(
				point -> axisProjection(axisStart, axisEnd, point.getLoc())));

		List<CapturePoint> ordered = new ArrayList<>(points.size());
		ordered.add(first);
		ordered.addAll(middle);
		ordered.add(last);
		return ordered;
	}

	static List<CapturePoint> sortByAxisProjection(
			List<CapturePoint> points,
			Location axisStart,
			Location axisEnd) {
		List<CapturePoint> ordered = new ArrayList<>(points);
		ordered.sort(Comparator.comparingDouble(
				point -> axisProjection(axisStart, axisEnd, point.getLoc())));
		return ordered;
	}

	static List<CapturePoint> greedyChainFromAnchor(List<CapturePoint> points, CapturePoint anchor) {
		List<CapturePoint> ordered = new ArrayList<>();
		Set<CapturePoint> remaining = new HashSet<>(points);
		ordered.add(anchor);
		remaining.remove(anchor);

		while (!remaining.isEmpty()) {
			CapturePoint last = ordered.get(ordered.size() - 1);
			CapturePoint nearest = null;
			double nearestDistance = Double.MAX_VALUE;
			for (CapturePoint candidate : remaining) {
				double distance = distanceXZ(last.getLoc(), candidate.getLoc());
				if (distance < nearestDistance) {
					nearestDistance = distance;
					nearest = candidate;
				}
			}
			if (nearest == null) {
				break;
			}
			ordered.add(nearest);
			remaining.remove(nearest);
		}
		return ordered;
	}

	static void assignSequenceLabels(List<CapturePoint> ordered) {
		for (int i = 0; i < ordered.size(); i++) {
			CapturePoint point = ordered.get(i);
			point.setSequenceIndex(i);
			point.setId(letterForIndex(i));
		}
	}

	static double axisProjection(Location axisStart, Location axisEnd, Location point) {
		if (axisStart == null || axisEnd == null || point == null) {
			return 0;
		}
		double axisX = axisEnd.getX() - axisStart.getX();
		double axisZ = axisEnd.getZ() - axisStart.getZ();
		double axisLengthSq = axisX * axisX + axisZ * axisZ;
		if (axisLengthSq < 1e-6) {
			return distanceXZ(axisStart, point);
		}
		double pointX = point.getX() - axisStart.getX();
		double pointZ = point.getZ() - axisStart.getZ();
		return (pointX * axisX + pointZ * axisZ) / axisLengthSq;
	}

	static double distanceXZ(Location a, Location b) {
		if (a == null || b == null) {
			return Double.MAX_VALUE;
		}
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
