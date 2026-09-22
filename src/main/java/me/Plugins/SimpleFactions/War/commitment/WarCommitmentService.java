package me.Plugins.SimpleFactions.War.commitment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.War.commitment.LevySnapshotCalculator.LevyRow;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;

public final class WarCommitmentService {
	private static final Map<Integer, List<WarCommitment>> commitmentsByWar = new HashMap<>();

	private WarCommitmentService() {}

	public static List<WarCommitment> commitFaction(War war, Faction faction) {
		if (war == null || faction == null || faction.getId() == null) {
			return List.of();
		}
		List<WarCommitment> existing = getOwnRegimentCommitments(war.getId(), faction.getId());
		if (!existing.isEmpty()) {
			return List.copyOf(existing);
		}

		Instant committedAt = Instant.now();
		List<WarCommitment> created = new ArrayList<>();
		for (Regiment regiment : faction.getMilitary().getRegiments()) {
			if (regiment.isLevy()) {
				continue;
			}
			created.add(new WarCommitment(
					war.getId(),
					faction.getId(),
					null,
					regiment.getId(),
					regiment.getCurrentSlots(),
					committedAt));
		}
		appendCommitments(war.getId(), created);
		return List.copyOf(created);
	}

	public static List<WarCommitment> snapshotLevyForSide(War war, Side side) {
		if (war == null || side == null) {
			return List.of();
		}
		return insertLevyRows(war, LevySnapshotCalculator.collectLevyRows(side));
	}

	public static List<WarCommitment> snapshotLevyForFighter(War war, Faction fighter) {
		if (war == null || fighter == null) {
			return List.of();
		}
		Side side = war.getSide(fighter);
		if (side == null) {
			return List.of();
		}
		Set<String> sideFighterIds = new HashSet<>();
		for (Faction participating : BattleSideMembers.collectParticipatingFactions(side)) {
			if (participating != null && participating.getId() != null) {
				sideFighterIds.add(participating.getId().toLowerCase());
			}
		}
		return insertLevyRows(war, LevySnapshotCalculator.collectLevyRowsForFighter(fighter, sideFighterIds));
	}

	public static void commitAllParticipants(War war) {
		if (war == null) {
			return;
		}
		for (Faction fighter : BattleSideMembers.collectParticipatingFactions(war.getAttackers())) {
			commitFaction(war, fighter);
		}
		for (Faction fighter : BattleSideMembers.collectParticipatingFactions(war.getDefenders())) {
			commitFaction(war, fighter);
		}
		snapshotLevyForSide(war, war.getAttackers());
		snapshotLevyForSide(war, war.getDefenders());
	}

	public static void onVassalageEnded(Faction origin, Faction target) {
		if (origin == null || target == null) {
			return;
		}
		if (me.Plugins.SimpleFactions.Managers.RelationManager.isOverlord(origin, target)) {
			removeLevySubtree(target);
			return;
		}
		if (me.Plugins.SimpleFactions.Managers.RelationManager.isOverlord(target, origin)) {
			removeLevySubtree(origin);
		}
	}

	public static void removeLevySubtree(Faction brokenSubject) {
		if (brokenSubject == null) {
			return;
		}
		Set<String> subtreeIds = LevySnapshotCalculator.collectSubjectSubtreeIds(brokenSubject);
		String brokenSubjectId = brokenSubject.getId();
		for (War war : WarManager.getActive()) {
			removeLevyRows(war.getId(), commitment -> {
				if (brokenSubjectId != null
						&& brokenSubjectId.equalsIgnoreCase(commitment.factionId())
						&& commitment.isLevyRow()) {
					return true;
				}
				String sourceId = commitment.sourceFactionId();
				return sourceId != null && subtreeIds.contains(sourceId.toLowerCase());
			});
		}
	}

	public static List<WarCommitment> getCommitmentsForWar(int warId) {
		List<WarCommitment> commitments = commitmentsByWar.get(warId);
		if (commitments == null || commitments.isEmpty()) {
			return List.of();
		}
		return Collections.unmodifiableList(commitments);
	}

	public static void clearCommitments(int warId) {
		commitmentsByWar.remove(warId);
	}

	public static void restoreCommitments(int warId, List<WarCommitment> commitments) {
		if (warId <= 0) {
			return;
		}
		if (commitments == null || commitments.isEmpty()) {
			commitmentsByWar.remove(warId);
			return;
		}
		commitmentsByWar.put(warId, new ArrayList<>(commitments));
	}

	public static int debitCount(
			int warId,
			String factionId,
			String sourceFactionId,
			String regimentId,
			int amount) {
		if (amount <= 0 || factionId == null || regimentId == null) {
			return 0;
		}
		List<WarCommitment> commitments = commitmentsByWar.get(warId);
		if (commitments == null || commitments.isEmpty()) {
			return 0;
		}
		for (int i = 0; i < commitments.size(); i++) {
			WarCommitment commitment = commitments.get(i);
			if (!matchesCommitment(commitment, factionId, sourceFactionId, regimentId)) {
				continue;
			}
			int debited = Math.min(amount, commitment.count());
			if (debited <= 0) {
				return 0;
			}
			int remaining = commitment.count() - debited;
			if (remaining <= 0) {
				commitments.remove(i);
			} else {
				commitments.set(i, new WarCommitment(
						commitment.warId(),
						commitment.factionId(),
						commitment.sourceFactionId(),
						commitment.regimentId(),
						remaining,
						commitment.committedAt()));
			}
			if (commitments.isEmpty()) {
				commitmentsByWar.remove(warId);
			}
			return debited;
		}
		return 0;
	}

	private static boolean matchesCommitment(
			WarCommitment commitment,
			String factionId,
			String sourceFactionId,
			String regimentId) {
		if (commitment.factionId() == null || !commitment.factionId().equalsIgnoreCase(factionId)) {
			return false;
		}
		if (!commitment.regimentId().equalsIgnoreCase(regimentId)) {
			return false;
		}
		if (sourceFactionId == null) {
			return commitment.sourceFactionId() == null;
		}
		return sourceFactionId.equalsIgnoreCase(commitment.sourceFactionId());
	}

	/**
	 * Raw commitment sum without pool/militia/live-slot rules. Use {@link me.Plugins.SimpleFactions.War.battle.military.BattlePoolService} for battle pools (61.03+).
	 */
	public static int totalCommittedRegiments(
			int warId,
			Set<String> factionIds,
			Predicate<WarCommitment> regimentFilter) {
		if (factionIds == null || factionIds.isEmpty()) {
			return 0;
		}
		Set<String> normalizedIds = new HashSet<>();
		for (String factionId : factionIds) {
			if (factionId != null) {
				normalizedIds.add(factionId.toLowerCase());
			}
		}
		int total = 0;
		for (WarCommitment commitment : getCommitmentsForWar(warId)) {
			if (commitment.factionId() == null || !normalizedIds.contains(commitment.factionId().toLowerCase())) {
				continue;
			}
			if (regimentFilter != null && !regimentFilter.test(commitment)) {
				continue;
			}
			total += commitment.count();
		}
		return total;
	}

	private static List<WarCommitment> insertLevyRows(War war, Map<String, LevyRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}
		Instant committedAt = Instant.now();
		List<WarCommitment> created = new ArrayList<>();
		for (LevyRow row : rows.values()) {
			if (hasLevyRow(war.getId(), row.holderId(), row.sourceId())) {
				continue;
			}
			created.add(new WarCommitment(
					war.getId(),
					row.holderId(),
					row.sourceId(),
					WarCommitment.LEVY_REGIMENT_ID,
					row.count(),
					committedAt));
		}
		appendCommitments(war.getId(), created);
		return List.copyOf(created);
	}

	private static boolean hasLevyRow(int warId, String holderId, String sourceId) {
		for (WarCommitment commitment : getCommitmentsForWar(warId)) {
			if (!commitment.isLevyRow()) {
				continue;
			}
			if (commitment.factionId().equalsIgnoreCase(holderId)
					&& commitment.sourceFactionId().equalsIgnoreCase(sourceId)) {
				return true;
			}
		}
		return false;
	}

	private static List<WarCommitment> getOwnRegimentCommitments(int warId, String factionId) {
		List<WarCommitment> matches = new ArrayList<>();
		for (WarCommitment commitment : getCommitmentsForWar(warId)) {
			if (commitment.factionId().equalsIgnoreCase(factionId) && !commitment.isLevyRow()) {
				matches.add(commitment);
			}
		}
		return matches;
	}

	private static void appendCommitments(int warId, List<WarCommitment> commitments) {
		if (commitments == null || commitments.isEmpty()) {
			return;
		}
		commitmentsByWar.computeIfAbsent(warId, ignored -> new ArrayList<>()).addAll(commitments);
	}

	private static void removeLevyRows(int warId, Predicate<WarCommitment> shouldRemove) {
		List<WarCommitment> commitments = commitmentsByWar.get(warId);
		if (commitments == null || commitments.isEmpty()) {
			return;
		}
		commitments.removeIf(shouldRemove);
		if (commitments.isEmpty()) {
			commitmentsByWar.remove(warId);
		}
	}
}
