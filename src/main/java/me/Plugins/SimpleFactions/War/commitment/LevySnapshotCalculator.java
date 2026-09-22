package me.Plugins.SimpleFactions.War.commitment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

public final class LevySnapshotCalculator {
	private LevySnapshotCalculator() {}

	public record LevyRow(String holderId, String sourceId, int count) {}

	public static Faction findNearestFighterHolder(Faction source, Set<String> fighterIds) {
		if (source == null || fighterIds == null || fighterIds.isEmpty()) {
			return null;
		}
		Faction current = source;
		while (current != null) {
			String currentId = current.getId();
			if (currentId != null && containsId(fighterIds, currentId)) {
				return current;
			}
			String overlordId = RelationManager.getOverlord(current);
			if (overlordId == null || overlordId.isBlank()) {
				return null;
			}
			current = FactionManager.getByString(overlordId);
		}
		return null;
	}

	public static int directLevyContribution(Faction source) {
		if (source == null) {
			return 0;
		}
		int total = 0;
		int memberCap = source.getMembers() != null ? source.getMembers().size() : 0;
		double levyPercent = source.getModifier(FactionModifiers.LEVY).getAmount();
		for (Regiment regiment : source.getMilitary().getRegiments()) {
			if (regiment.isLevy()) {
				continue;
			}
			int count = regiment.getCurrentSlots();
			count = (int) Math.round(count * (levyPercent / 100.0));
			if (total + count >= memberCap) {
				count = memberCap - total;
			}
			if (count <= 0) {
				continue;
			}
			total += count;
		}
		return total;
	}

	public static int levyContribution(Faction source, Faction holder) {
		if (source == null || holder == null) {
			return 0;
		}
		int count = directLevyContribution(source);
		Faction current = source;
		while (current != null && !sameId(current, holder)) {
			String overlordId = RelationManager.getOverlord(current);
			if (overlordId == null || overlordId.isBlank()) {
				return 0;
			}
			Faction overlord = FactionManager.getByString(overlordId);
			if (overlord == null) {
				return 0;
			}
			if (sameId(overlord, holder)) {
				break;
			}
			double levyPercent = overlord.getModifier(FactionModifiers.LEVY).getAmount();
			count = (int) Math.round(count * (levyPercent / 100.0));
			if (count <= 0) {
				return 0;
			}
			current = overlord;
		}
		return count;
	}

	public static Map<String, LevyRow> collectLevyRows(Side side) {
		if (side == null) {
			return Map.of();
		}
		Set<String> fighterIds = toIdSet(BattleSideMembers.collectParticipatingFactions(side));
		Set<Faction> candidateSources = new HashSet<>();
		for (Faction fighter : BattleSideMembers.collectParticipatingFactions(side)) {
			collectNonFighterDescendants(fighter, fighterIds, candidateSources);
		}
		return buildRows(candidateSources, fighterIds);
	}

	public static Map<String, LevyRow> collectLevyRowsForFighter(Faction fighter, Set<String> sideFighterIds) {
		if (fighter == null || sideFighterIds == null || sideFighterIds.isEmpty()) {
			return Map.of();
		}
		Set<Faction> candidateSources = new HashSet<>();
		collectNonFighterDescendants(fighter, sideFighterIds, candidateSources);
		return buildRows(candidateSources, sideFighterIds);
	}

	private static Map<String, LevyRow> buildRows(Set<Faction> candidateSources, Set<String> fighterIds) {
		Map<String, LevyRow> rows = new LinkedHashMap<>();
		for (Faction source : candidateSources) {
			if (source == null || containsId(fighterIds, source.getId())) {
				continue;
			}
			Faction holder = findNearestFighterHolder(source, fighterIds);
			if (holder == null || !RelationManager.sameRealm(source, holder)) {
				continue;
			}
			int count = levyContribution(source, holder);
			if (count <= 0) {
				continue;
			}
			String key = levyKey(holder.getId(), source.getId());
			rows.put(key, new LevyRow(holder.getId(), source.getId(), count));
		}
		return rows;
	}

	private static void collectNonFighterDescendants(
			Faction root,
			Set<String> fighterIds,
			Set<Faction> out) {
		if (root == null) {
			return;
		}
		for (Faction subject : RelationManager.getSubjects(root)) {
			if (subject == null) {
				continue;
			}
			if (!containsId(fighterIds, subject.getId())) {
				out.add(subject);
			}
			collectNonFighterDescendants(subject, fighterIds, out);
		}
	}

	public static Set<String> collectSubjectSubtreeIds(Faction root) {
		Set<String> ids = new HashSet<>();
		if (root == null || root.getId() == null) {
			return ids;
		}
		ids.add(root.getId().toLowerCase());
		for (Faction subject : RelationManager.getSubjects(root)) {
			ids.addAll(collectSubjectSubtreeIds(subject));
		}
		return ids;
	}

	public static String levyKey(String holderId, String sourceId) {
		return holderId.toLowerCase() + "|" + sourceId.toLowerCase();
	}

	private static Set<String> toIdSet(List<Faction> factions) {
		Set<String> ids = new HashSet<>();
		for (Faction faction : factions) {
			if (faction != null && faction.getId() != null) {
				ids.add(faction.getId().toLowerCase());
			}
		}
		return ids;
	}

	private static boolean containsId(Set<String> ids, String factionId) {
		return factionId != null && ids.contains(factionId.toLowerCase());
	}

	private static boolean sameId(Faction a, Faction b) {
		return a != null
				&& b != null
				&& a.getId() != null
				&& a.getId().equalsIgnoreCase(b.getId());
	}
}
