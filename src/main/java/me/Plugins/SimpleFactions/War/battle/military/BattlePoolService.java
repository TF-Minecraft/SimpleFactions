package me.Plugins.SimpleFactions.War.battle.military;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;

public final class BattlePoolService {
	public static final String MILITIA_REGIMENT_ID = "militia";

	private BattlePoolService() {}

	public static PoolMode resolvePoolMode(War war, int battleProvinceId, Side side) {
		if (war == null || side == null) {
			return PoolMode.DEFENSIVE;
		}
		CampaignCoalition offensive = CampaignCapabilityService.battleOffensiveCoalition(war);
		CampaignCoalition sideCoalition = CampaignCoalitionService.coalitionOf(war, side);
		if (offensive == null || sideCoalition == null) {
			return PoolMode.DEFENSIVE;
		}
		return sideCoalition == offensive ? PoolMode.OFFENSIVE : PoolMode.DEFENSIVE;
	}

	public static boolean isMilitiaEligible(Faction faction, int battleProvinceId) {
		if (faction == null || faction.getId() == null) {
			return false;
		}
		Faction owner = TitleManager.getByProvince(battleProvinceId);
		return owner != null && owner.getId().equalsIgnoreCase(faction.getId());
	}

	public static Map<String, Map<String, Integer>> eligibleRegiments(
			War war,
			int battleProvinceId,
			Side side,
			PoolMode mode) {
		if (war == null || side == null || mode == null) {
			return Map.of();
		}
		List<Faction> fighters = BattleSideMembers.collectParticipatingFactions(side);
		Set<String> fighterIds = toIdSet(fighters);
		Map<String, Map<String, Integer>> eligible = new LinkedHashMap<>();

		for (Faction fighter : fighters) {
			Map<String, Integer> regimentCounts = collectOwnRegiments(war, fighter, battleProvinceId, mode);
			addLevyRows(war, fighter.getId(), fighterIds, mode, regimentCounts);
			if (!regimentCounts.isEmpty()) {
				eligible.put(fighter.getId(), regimentCounts);
			}
		}
		return Map.copyOf(eligible);
	}

	public static int totalCommittedRegiments(War war, int battleProvinceId, Side side) {
		PoolMode mode = resolvePoolMode(war, battleProvinceId, side);
		return totalCommittedRegiments(war, battleProvinceId, side, mode);
	}

	public static int totalCommittedRegiments(
			War war,
			int battleProvinceId,
			Side side,
			PoolMode mode) {
		int total = 0;
		for (Map<String, Integer> regimentCounts : eligibleRegiments(war, battleProvinceId, side, mode).values()) {
			for (int count : regimentCounts.values()) {
				total += count;
			}
		}
		return total;
	}

	private static Map<String, Integer> collectOwnRegiments(
			War war,
			Faction faction,
			int battleProvinceId,
			PoolMode mode) {
		Map<String, Integer> regimentCounts = new LinkedHashMap<>();
		if (faction == null || faction.getMilitary() == null) {
			return regimentCounts;
		}
		for (Regiment regiment : faction.getMilitary().getRegiments()) {
			if (regiment.isLevy()) {
				continue;
			}
			if (!isRegimentEligible(war, regiment, faction, battleProvinceId, mode)) {
				continue;
			}
			int count = regiment.getCurrentSlots();
			if (count <= 0) {
				continue;
			}
			regimentCounts.put(regiment.getId(), count);
		}
		return regimentCounts;
	}

	private static boolean isRegimentEligible(
			War war,
			Regiment regiment,
			Faction faction,
			int battleProvinceId,
			PoolMode mode) {
		if (CivilWarBorderLock.isCivilWar(war)) {
			return true;
		}
		if (MILITIA_REGIMENT_ID.equalsIgnoreCase(regiment.getId())) {
			return mode == PoolMode.DEFENSIVE && isMilitiaEligible(faction, battleProvinceId);
		}
		if (mode == PoolMode.OFFENSIVE) {
			return regiment.isOffensive();
		}
		return !regiment.isOffensive();
	}

	private static void addLevyRows(
			War war,
			String holderFactionId,
			Set<String> fighterIds,
			PoolMode mode,
			Map<String, Integer> regimentCounts) {
		if (holderFactionId == null || !fighterIds.contains(holderFactionId.toLowerCase())) {
			return;
		}
		if (mode != PoolMode.OFFENSIVE && !CivilWarBorderLock.isCivilWar(war)) {
			return;
		}
		int levyTotal = 0;
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
			levyTotal += commitment.count();
		}
		if (levyTotal > 0) {
			regimentCounts.put(WarCommitment.LEVY_REGIMENT_ID, levyTotal);
		}
	}

	private static BelligerentRole resolveSideRole(War war, Side side) {
		CampaignCoalition coalition = CampaignCoalitionService.coalitionOf(war, side);
		return coalition != null ? CampaignCoalitionService.coalitionToBelligerentRole(coalition) : null;
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
}
