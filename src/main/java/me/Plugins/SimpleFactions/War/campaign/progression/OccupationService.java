package me.Plugins.SimpleFactions.War.campaign.progression;


import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortControlService;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.pathfinder.BelligerentTerritory;
import me.Plugins.SimpleFactions.War.pathfinder.ProvinceOwnerLookup;
import me.Plugins.SimpleFactions.installation.WartimeInstallationService;

public class OccupationService {
	private final ProvinceManager provinceManager;
	private final ProvinceOwnerLookup owners;
	private final FortZocIndex fortIndex;

	public OccupationService(ProvinceManager provinceManager, ProvinceOwnerLookup owners) {
		this(provinceManager, owners, null);
	}

	public OccupationService(
			ProvinceManager provinceManager,
			ProvinceOwnerLookup owners,
			FortZocIndex fortIndex) {
		this.provinceManager = provinceManager;
		this.owners = owners;
		this.fortIndex = fortIndex;
	}

	private FortZocIndex forts() {
		return fortIndex != null ? fortIndex : FortZocIndex.fromGameState();
	}

	public OccupationZone computeOccupationZone(War war, int battleProvinceId, BelligerentRole winner) {
		if (!isValidInput(war, battleProvinceId, winner)) {
			return OccupationZone.of(List.of());
		}

		BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners);
		List<Integer> zone = new ArrayList<>();
		zone.add(battleProvinceId);

		Province battleProvince = provinceManager.get(battleProvinceId);
		if (battleProvince == null || !battleProvince.isValid()) {
			return OccupationZone.of(zone);
		}

		FortZocIndex forts = forts();
		List<Integer> neighbors = new ArrayList<>();
		for (int neighborId : battleProvince.getNeighbours()) {
			if (qualifiesNeighbor(war, battleProvinceId, neighborId, winner, territory, forts)) {
				neighbors.add(neighborId);
			}
		}
		Collections.sort(neighbors);
		zone.addAll(neighbors);
		return OccupationZone.of(zone);
	}

	public boolean applyBattleWin(War war, int battleProvinceId, BelligerentRole winner) {
		if (!isValidInput(war, battleProvinceId, winner)) {
			return false;
		}

		BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners);
		OccupationZone zone = computeOccupationZone(war, battleProvinceId, winner);
		if (zone.provinceIds().isEmpty()) {
			return false;
		}

		OccupationZone enemyZone = enemyOwnedZone(zone, winner, territory);

		if (winner == BelligerentRole.ATTACKER) {
			List<Integer> winnerList = copyList(war.getOccupiedByAttacker());
			List<Integer> loserList = copyList(war.getOccupiedByDefender());
			List<Integer> stripped = stripOccupation(loserList, zone);
			List<Integer> newlyAdded = mergeOccupation(winnerList, enemyZone);
			pruneNonEnemyOccupation(winnerList, winner, territory);
			war.setOccupiedByAttacker(winnerList);
			war.setOccupiedByDefender(loserList);
			war.setLastBattleOccupied(unionOccupied(stripped, newlyAdded));
		} else {
			List<Integer> winnerList = copyList(war.getOccupiedByDefender());
			List<Integer> loserList = copyList(war.getOccupiedByAttacker());
			List<Integer> stripped = stripOccupation(loserList, zone);
			List<Integer> newlyAdded = mergeOccupation(winnerList, enemyZone);
			pruneNonEnemyOccupation(winnerList, winner, territory);
			war.setOccupiedByDefender(winnerList);
			war.setOccupiedByAttacker(loserList);
			war.setLastBattleOccupied(unionOccupied(stripped, newlyAdded));
		}
		WartimeInstallationService.occupyLastBattle(war, winner);
		FactionManager.getMap().enqueueOccupationFromWar(war);
		return true;
	}

	static boolean qualifiesNeighbor(
			War war,
			int battleProvinceId,
			int neighborId,
			BelligerentRole winner,
			BelligerentTerritory territory) {
		return qualifiesNeighbor(
				war,
				battleProvinceId,
				neighborId,
				winner,
				territory,
				FortZocIndex.fromGameState());
	}

	static boolean qualifiesNeighbor(
			War war,
			int battleProvinceId,
			int neighborId,
			BelligerentRole winner,
			BelligerentTerritory territory,
			FortZocIndex forts) {
		if (neighborId == battleProvinceId) {
			return false;
		}
		if (isUpcomingUnfoughtSlot(war, neighborId, battleProvinceId)) {
			return false;
		}
		if (blockedByUntakenEnemyFortZoc(war, neighborId, battleProvinceId, winner, forts)) {
			return false;
		}
		if (!isEnemyOwned(neighborId, winner, territory)) {
			return false;
		}
		if (isOnCampaignLine(war, neighborId)) {
			return true;
		}
		if (isAlreadyOccupied(war, neighborId)) {
			return true;
		}
		if (Cache.warOccupationIncludeEnemyNeighbors && isEnemyOwned(neighborId, winner, territory)) {
			return true;
		}
		return false;
	}

	private static boolean isUpcomingUnfoughtSlot(War war, int provinceId, int battleProvinceId) {
		if (war == null || !CampaignScheduleService.hasActiveSchedule(war)) {
			return false;
		}
		List<ScheduledCampaignBattle> schedule = CampaignScheduleService.scheduleListForLeg(
				war,
				CampaignScheduleService.activeLeg(war));
		int fromIndex = Math.max(0, CampaignScheduleService.getActiveScheduleIndex(war));
		for (int index = fromIndex; index < schedule.size(); index++) {
			ScheduledCampaignBattle slot = schedule.get(index);
			if (slotMatchesBattle(slot, battleProvinceId)) {
				continue;
			}
			return slot.provinceId() == provinceId
					|| Objects.equals(slot.chronologyProvinceId(), provinceId);
		}
		return false;
	}

	private static boolean slotMatchesBattle(ScheduledCampaignBattle slot, int battleProvinceId) {
		return slot.provinceId() == battleProvinceId
				|| Objects.equals(slot.chronologyProvinceId(), battleProvinceId);
	}

	private static boolean blockedByUntakenEnemyFortZoc(
			War war,
			int provinceId,
			int battleProvinceId,
			BelligerentRole winner,
			FortZocIndex forts) {
		if (provinceId == battleProvinceId || forts == null) {
			return false;
		}
		CampaignCoalition advancing = winner == BelligerentRole.ATTACKER
				? CampaignCoalition.AGGRESSOR
				: CampaignCoalition.DEFENDER;
		List<OperationalFort> covering = forts.fortsCovering(provinceId);
		if (covering.isEmpty()) {
			return false;
		}
		for (OperationalFort fort : covering) {
			if (FortControlService.isEnemyControlled(war, fort.id(), advancing)) {
				return true;
			}
		}
		return false;
	}

	static List<Integer> mergeOccupation(List<Integer> existing, OccupationZone zone) {
		Set<Integer> seen = new HashSet<>(existing);
		List<Integer> newlyAdded = new ArrayList<>();
		for (int provinceId : zone.provinceIds()) {
			if (seen.add(provinceId)) {
				existing.add(provinceId);
				newlyAdded.add(provinceId);
			}
		}
		return newlyAdded;
	}

	static List<Integer> stripOccupation(List<Integer> existing, OccupationZone zone) {
		if (existing == null || existing.isEmpty() || zone == null) {
			return List.of();
		}
		Set<Integer> zoneIds = new HashSet<>(zone.provinceIds());
		List<Integer> stripped = new ArrayList<>();
		existing.removeIf(provinceId -> {
			if (zoneIds.contains(provinceId)) {
				stripped.add(provinceId);
				return true;
			}
			return false;
		});
		return stripped;
	}

	private static OccupationZone enemyOwnedZone(
			OccupationZone zone,
			BelligerentRole winner,
			BelligerentTerritory territory) {
		List<Integer> enemyIds = new ArrayList<>();
		for (int provinceId : zone.provinceIds()) {
			if (isEnemyOwned(provinceId, winner, territory)) {
				enemyIds.add(provinceId);
			}
		}
		return OccupationZone.of(enemyIds);
	}

	private static void pruneNonEnemyOccupation(
			List<Integer> occupied,
			BelligerentRole winner,
			BelligerentTerritory territory) {
		if (occupied == null || occupied.isEmpty()) {
			return;
		}
		occupied.removeIf(provinceId -> !isEnemyOwned(provinceId, winner, territory));
	}

	private static List<Integer> unionOccupied(List<Integer> stripped, List<Integer> newlyAdded) {
		LinkedHashSet<Integer> union = new LinkedHashSet<>();
		if (stripped != null) {
			union.addAll(stripped);
		}
		if (newlyAdded != null) {
			union.addAll(newlyAdded);
		}
		return new ArrayList<>(union);
	}

	private static boolean isValidInput(War war, int battleProvinceId, BelligerentRole winner) {
		if (war == null || !war.isActive() || winner == null || battleProvinceId <= 0) {
			return false;
		}
		List<Integer> campaign = war.getCampaignProvinces();
		return campaign != null && !campaign.isEmpty();
	}

	private static boolean isOnCampaignLine(War war, int provinceId) {
		List<Integer> campaign = war.getCampaignProvinces();
		return campaign != null && campaign.contains(provinceId);
	}

	private static boolean isAlreadyOccupied(War war, int provinceId) {
		return containsProvince(war.getOccupiedByAttacker(), provinceId)
				|| containsProvince(war.getOccupiedByDefender(), provinceId);
	}

	private static boolean containsProvince(List<Integer> provinces, int provinceId) {
		return provinces != null && provinces.contains(provinceId);
	}

	private static boolean isEnemyOwned(int provinceId, BelligerentRole winner, BelligerentTerritory territory) {
		return switch (winner) {
			case ATTACKER -> territory.isDefenderSide(provinceId);
			case DEFENDER -> territory.isAttackerSide(provinceId);
		};
	}

	private static List<Integer> copyList(List<Integer> source) {
		return source == null ? new ArrayList<>() : new ArrayList<>(source);
	}

	public static final class OccupationZone {
		private final List<Integer> provinceIds;

		private OccupationZone(List<Integer> provinceIds) {
			this.provinceIds = List.copyOf(provinceIds);
		}

		public static OccupationZone of(List<Integer> provinceIds) {
			if (provinceIds == null || provinceIds.isEmpty()) {
				return new OccupationZone(List.of());
			}
			return new OccupationZone(new ArrayList<>(provinceIds));
		}

		public List<Integer> provinceIds() {
			return provinceIds;
		}
	}
}
