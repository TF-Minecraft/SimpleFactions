package me.Plugins.SimpleFactions.War.battle.military;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;

public final class BattleCasualtyService {
	private BattleCasualtyService() {}

	public static void applyBattleCasualties(War war, Battle battle, Map<String, Integer> sideCasualties) {
		if (!shouldApply(war, battle, sideCasualties)) {
			return;
		}

		Integer battleProvinceId = battle.getProvinceId() != null
				? battle.getProvinceId()
				: war.getScheduledBattleProvinceId();
		if (battleProvinceId == null) {
			return;
		}

		Set<Faction> affectedFactions = new HashSet<>();
		int attackerLosses = applySide(
				war,
				battleProvinceId,
				war.getAttackers(),
				sideCasualties.getOrDefault(BattleTemplate.ATTACKER_SIDE, 0),
				affectedFactions);
		int defenderLosses = applySide(
				war,
				battleProvinceId,
				war.getDefenders(),
				sideCasualties.getOrDefault(BattleTemplate.DEFENDER_SIDE, 0),
				affectedFactions);

		persistCommitments(war);
		for (Faction faction : affectedFactions) {
			if (faction != null) {
				new Database().saveFaction(faction);
			}
		}
		broadcastLosses(war, attackerLosses, defenderLosses);
	}

	public static boolean shouldApply(War war, Battle battle, Map<String, Integer> sideCasualties) {
		if (war == null || !war.isActive() || battle == null || battle.getWarId() == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		if (type == BattleType.RAID) {
			return false;
		}
		if (type != BattleType.FIELD && type != BattleType.SIEGE) {
			return false;
		}
		if (sideCasualties == null || sideCasualties.isEmpty()) {
			return false;
		}
		for (int casualties : sideCasualties.values()) {
			if (casualties > 0) {
				return true;
			}
		}
		return false;
	}

	public static void persistCommitments(War war) {
		if (war != null) {
			WarManager.persist(war);
		}
	}

	private static int applySide(
			War war,
			int battleProvinceId,
			Side side,
			int casualties,
			Set<Faction> affectedFactions) {
		if (casualties <= 0 || side == null) {
			return 0;
		}

		PoolMode mode = BattlePoolService.resolvePoolMode(war, battleProvinceId, side);
		Map<String, Map<String, Integer>> eligible = BattlePoolService.eligibleRegiments(
				war,
				battleProvinceId,
				side,
				mode);
		if (eligible.isEmpty()) {
			return 0;
		}

		int remaining = casualties;

		for (Map.Entry<String, Map<String, Integer>> factionEntry : eligible.entrySet()) {
			String factionId = factionEntry.getKey();
			Map<String, Integer> regimentCounts = factionEntry.getValue();
			Integer militiaCount = regimentCounts.get(BattlePoolService.MILITIA_REGIMENT_ID);
			if (militiaCount == null || militiaCount <= 0) {
				continue;
			}
			int take = Math.min(remaining, militiaCount);
			if (take <= 0) {
				continue;
			}
			debitOwnRegiment(war, factionId, BattlePoolService.MILITIA_REGIMENT_ID, take, affectedFactions);
			remaining -= take;
			if (remaining <= 0) {
				return casualties;
			}
		}

		List<RegimentTarget> targets = new ArrayList<>();
		for (Map.Entry<String, Map<String, Integer>> factionEntry : eligible.entrySet()) {
			String factionId = factionEntry.getKey();
			for (Map.Entry<String, Integer> regimentEntry : factionEntry.getValue().entrySet()) {
				if (BattlePoolService.MILITIA_REGIMENT_ID.equalsIgnoreCase(regimentEntry.getKey())) {
					continue;
				}
				int weight = regimentEntry.getValue();
				if (weight <= 0) {
					continue;
				}
				targets.add(new RegimentTarget(factionId, regimentEntry.getKey(), weight));
			}
		}

		Map<RegimentTarget, Integer> allocation = allocateProportionally(remaining, targets);
		for (Map.Entry<RegimentTarget, Integer> entry : allocation.entrySet()) {
			RegimentTarget target = entry.getKey();
			int amount = entry.getValue();
			if (amount <= 0) {
				continue;
			}
			if (WarCommitment.LEVY_REGIMENT_ID.equalsIgnoreCase(target.regimentId())) {
				debitLevyForHolder(war, target.factionId(), amount, affectedFactions);
			} else {
				debitOwnRegiment(war, target.factionId(), target.regimentId(), amount, affectedFactions);
			}
		}
		return casualties;
	}

	static void debitOwnRegiment(
			War war,
			String factionId,
			String regimentId,
			int amount,
			Set<Faction> affectedFactions) {
		if (amount <= 0 || factionId == null || regimentId == null) {
			return;
		}
		WarCommitmentService.debitCount(war.getId(), factionId, null, regimentId, amount);
		Faction faction = FactionManager.getByString(factionId);
		if (faction == null || faction.getMilitary() == null) {
			return;
		}
		Regiment regiment = faction.getMilitary().getRegiment(regimentId);
		if (regiment == null) {
			return;
		}
		for (int i = 0; i < amount; i++) {
			regiment.sizeDecrease();
		}
		affectedFactions.add(faction);
	}

	static void debitLevyForHolder(
			War war,
			String holderFactionId,
			int amount,
			Set<Faction> affectedFactions) {
		if (amount <= 0 || holderFactionId == null) {
			return;
		}
		List<LevyRowTarget> levyRows = new ArrayList<>();
		for (WarCommitment commitment : WarCommitmentService.getCommitmentsForWar(war.getId())) {
			if (!commitment.isLevyRow()) {
				continue;
			}
			if (!commitment.factionId().equalsIgnoreCase(holderFactionId)) {
				continue;
			}
			if (commitment.count() <= 0) {
				continue;
			}
			levyRows.add(new LevyRowTarget(
					commitment.factionId(),
					commitment.sourceFactionId(),
					commitment.count()));
		}
		if (levyRows.isEmpty()) {
			return;
		}

		List<RegimentTarget> rowTargets = new ArrayList<>();
		for (LevyRowTarget row : levyRows) {
			rowTargets.add(new RegimentTarget(row.holderId(), row.sourceId(), row.weight()));
		}
		Map<RegimentTarget, Integer> rowAllocation = allocateProportionally(amount, rowTargets);
		for (Map.Entry<RegimentTarget, Integer> entry : rowAllocation.entrySet()) {
			RegimentTarget target = entry.getKey();
			int rowDebit = entry.getValue();
			if (rowDebit <= 0) {
				continue;
			}
			debitLevyRow(war, target.factionId(), target.regimentId(), rowDebit, affectedFactions);
		}
	}

	static void debitLevyRow(
			War war,
			String holderFactionId,
			String sourceFactionId,
			int amount,
			Set<Faction> affectedFactions) {
		if (amount <= 0) {
			return;
		}
		WarCommitmentService.debitCount(
				war.getId(),
				holderFactionId,
				sourceFactionId,
				WarCommitment.LEVY_REGIMENT_ID,
				amount);
		debitSourceForLevy(war, sourceFactionId, amount, affectedFactions);
	}

	static void debitSourceForLevy(
			War war,
			String sourceFactionId,
			int amount,
			Set<Faction> affectedFactions) {
		if (amount <= 0 || sourceFactionId == null) {
			return;
		}
		List<RegimentTarget> sourceTargets = new ArrayList<>();
		for (WarCommitment commitment : WarCommitmentService.getCommitmentsForWar(war.getId())) {
			if (commitment.isLevyRow()) {
				continue;
			}
			if (commitment.factionId() == null || !commitment.factionId().equalsIgnoreCase(sourceFactionId)) {
				continue;
			}
			if (commitment.count() <= 0) {
				continue;
			}
			sourceTargets.add(new RegimentTarget(sourceFactionId, commitment.regimentId(), commitment.count()));
		}
		if (sourceTargets.isEmpty()) {
			return;
		}

		Map<RegimentTarget, Integer> allocation = allocateProportionally(amount, sourceTargets);
		for (Map.Entry<RegimentTarget, Integer> entry : allocation.entrySet()) {
			RegimentTarget target = entry.getKey();
			int debit = entry.getValue();
			if (debit <= 0) {
				continue;
			}
			debitOwnRegiment(war, target.factionId(), target.regimentId(), debit, affectedFactions);
		}

		Faction source = FactionManager.getByString(sourceFactionId);
		if (source == null || source.getMilitary() == null) {
			return;
		}
		for (Regiment regiment : source.getMilitary().getRegiments()) {
			if (regiment.isLevy() || regiment.isOffensive()) {
				continue;
			}
			int sent = regiment.sentToOverlord();
			if (sent <= 0) {
				continue;
			}
			int reduce = Math.min(amount, sent);
			regiment.setSentToOverlord(sent - reduce);
			amount -= reduce;
			affectedFactions.add(source);
			if (amount <= 0) {
				break;
			}
		}
	}

	static Map<RegimentTarget, Integer> allocateProportionally(int total, List<RegimentTarget> targets) {
		if (total <= 0 || targets == null || targets.isEmpty()) {
			return Map.of();
		}
		int weightSum = 0;
		for (RegimentTarget target : targets) {
			weightSum += target.weight();
		}
		if (weightSum <= 0) {
			return Map.of();
		}

		Map<RegimentTarget, Integer> allocation = new LinkedHashMap<>();
		List<RemainderEntry> remainders = new ArrayList<>();
		int assigned = 0;
		for (RegimentTarget target : targets) {
			double exact = (double) total * target.weight() / weightSum;
			int floor = (int) Math.floor(exact);
			allocation.put(target, floor);
			assigned += floor;
			remainders.add(new RemainderEntry(target, exact - floor));
		}
		remainders.sort(Comparator.comparingDouble(RemainderEntry::remainder).reversed());
		int leftover = total - assigned;
		for (int i = 0; i < leftover && i < remainders.size(); i++) {
			RegimentTarget target = remainders.get(i).target();
			allocation.put(target, allocation.getOrDefault(target, 0) + 1);
		}
		return allocation;
	}

	private static void broadcastLosses(War war, int attackerLosses, int defenderLosses) {
		if (attackerLosses <= 0 && defenderLosses <= 0) {
			return;
		}
		String message = "§7Campaign battle losses: attacker "
				+ attackerLosses
				+ ", defender "
				+ defenderLosses
				+ " regiments.";
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getAttackers())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getDefenders())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}

	record RegimentTarget(String factionId, String regimentId, int weight) {}

	private record LevyRowTarget(String holderId, String sourceId, int weight) {}

	private record RemainderEntry(RegimentTarget target, double remainder) {}

	static int getCommitmentCount(int warId, String factionId, String sourceFactionId, String regimentId) {
		for (WarCommitment commitment : WarCommitmentService.getCommitmentsForWar(warId)) {
			if (!matchesCommitment(commitment, factionId, sourceFactionId, regimentId)) {
				continue;
			}
			return commitment.count();
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
}
