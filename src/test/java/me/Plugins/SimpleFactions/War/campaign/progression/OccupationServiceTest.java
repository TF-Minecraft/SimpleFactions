package me.Plugins.SimpleFactions.War.campaign.progression;


import me.Plugins.SimpleFactions.War.campaign.progression.OccupationService.OccupationZone;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.pathfinder.BelligerentTerritory;
import me.Plugins.SimpleFactions.War.pathfinder.TitleManagerProvinceOwnerLookup;
import me.Plugins.SimpleFactions.enums.Terrain;

class OccupationServiceTest {
	private ProvinceManager pm;
	private OccupationService service;
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		Cache.warOccupationIncludeEnemyNeighbors = true;
		pm = new ProvinceManager();
		service = new OccupationService(
				pm,
				new TitleManagerProvinceOwnerLookup(),
				FortZocIndex.fromForts(List.of()));
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
	}

	@Test
	void computeOccupationZone_isolatedBattleProvince() {
		Province battle = province(10, Terrain.PLAINS);
		pm.start(Map.of(10, battle));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, battle);
			War war = baseWar(List.of(10, 20, 30));
			assertEquals(List.of(10), service.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_includesCampaignLineNeighbor() {
		Province battle = province(10, Terrain.PLAINS);
		Province onLine = province(20, Terrain.PLAINS);
		Province offLine = province(99, Terrain.PLAINS);
		link(battle, onLine);
		link(battle, offLine);
		pm.start(Map.of(10, battle, 20, onLine, 99, offLine));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(99)).thenReturn(attacker);
			War war = baseWar(List.of(5, 10, 20, 30));
			assertEquals(List.of(10, 20), service.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_skipsOwnCampaignLineNeighbor() {
		Province battle = province(20, Terrain.PLAINS);
		Province ownOnLine = province(10, Terrain.PLAINS);
		link(battle, ownOnLine);
		pm.start(Map.of(10, ownOnLine, 20, battle));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 20, 30));
			assertEquals(
					List.of(20),
					service.computeOccupationZone(war, 20, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_includesExistingOccupationNeighbor() {
		Province battle = province(10, Terrain.PLAINS);
		Province occupiedNeighbor = province(99, Terrain.PLAINS);
		link(battle, occupiedNeighbor);
		pm.start(Map.of(10, battle, 99, occupiedNeighbor));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, battle, occupiedNeighbor);
			War war = baseWar(List.of(5, 10, 30));
			war.setOccupiedByDefender(new ArrayList<>(List.of(99)));
			assertEquals(List.of(10, 99), service.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_includesEnemyNeighborWhenConfigEnabled() {
		Province battle = province(10, Terrain.PLAINS);
		Province enemy = province(99, Terrain.PLAINS);
		link(battle, enemy);
		pm.start(Map.of(10, battle, 99, enemy));
		Cache.warOccupationIncludeEnemyNeighbors = true;

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(99)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 30));
			assertEquals(List.of(10, 99), service.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_excludesEnemyNeighborWhenConfigDisabled() {
		Province battle = province(10, Terrain.PLAINS);
		Province enemy = province(99, Terrain.PLAINS);
		link(battle, enemy);
		pm.start(Map.of(10, battle, 99, enemy));
		Cache.warOccupationIncludeEnemyNeighbors = false;

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(99)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 30));
			assertEquals(List.of(10), service.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void applyBattleWin_attacker_mergesIntoAttackerOccupation() {
		Province battle = province(10, Terrain.PLAINS);
		Province onLine = province(20, Terrain.PLAINS);
		link(battle, onLine);
		pm.start(Map.of(10, battle, 20, onLine));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, battle, onLine);
			War war = baseWar(List.of(5, 10, 20, 30));
			assertTrue(service.applyBattleWin(war, 10, BelligerentRole.ATTACKER));
			assertEquals(List.of(20), war.getOccupiedByAttacker());
			assertEquals(List.of(20), war.getLastBattleOccupied());
			assertTrue(war.getOccupiedByDefender().isEmpty());
		}
	}

	@Test
	void applyBattleWin_defender_mergesIntoDefenderOccupation() {
		Province battle = province(10, Terrain.PLAINS);
		pm.start(Map.of(10, battle));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, battle);
			War war = baseWar(List.of(5, 10, 20, 30));
			assertTrue(service.applyBattleWin(war, 10, BelligerentRole.DEFENDER));
			assertEquals(List.of(10), war.getOccupiedByDefender());
			assertEquals(List.of(10), war.getLastBattleOccupied());
			assertTrue(war.getOccupiedByAttacker().isEmpty());
		}
	}

	@Test
	void applyBattleWin_deduplicatesExistingOccupation() {
		Province battle = province(20, Terrain.PLAINS);
		pm.start(Map.of(20, battle));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, battle);
			War war = baseWar(List.of(5, 10, 20, 30));
			war.setOccupiedByAttacker(new ArrayList<>(List.of(20)));
			assertTrue(service.applyBattleWin(war, 20, BelligerentRole.ATTACKER));
			assertEquals(List.of(20), war.getOccupiedByAttacker());
			assertTrue(war.getLastBattleOccupied().isEmpty());
		}
	}

	@Test
	void applyBattleWin_recapture_stripsOtherSide() {
		Province battle = province(10, Terrain.PLAINS);
		pm.start(Map.of(10, battle));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, battle);
			War war = baseWar(List.of(5, 10, 30));
			war.setOccupiedByDefender(new ArrayList<>(List.of(10)));
			assertTrue(service.applyBattleWin(war, 10, BelligerentRole.ATTACKER));
			assertTrue(war.getOccupiedByAttacker().isEmpty());
			assertTrue(war.getOccupiedByDefender().isEmpty());
			assertEquals(List.of(10), war.getLastBattleOccupied());
		}
	}

	@Test
	void mergeOccupation_returnsOnlyNewProvinces() {
		List<Integer> existing = new ArrayList<>(List.of(10));
		List<Integer> added = OccupationService.mergeOccupation(existing, OccupationZone.of(List.of(10, 20)));
		assertEquals(List.of(10, 20), existing);
		assertEquals(List.of(20), added);
	}

	@Test
	void qualifiesNeighbor_usesBelligerentTerritoryForEnemyOwnership() {
		Province battle = province(10, Terrain.PLAINS);
		Province enemy = province(99, Terrain.PLAINS);
		link(battle, enemy);
		pm.start(Map.of(10, battle, 99, enemy));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(99)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 30));
			BelligerentTerritory territory = BelligerentTerritory.fromWar(war, new TitleManagerProvinceOwnerLookup());
			assertTrue(OccupationService.qualifiesNeighbor(
					war, 10, 99, BelligerentRole.ATTACKER, territory, FortZocIndex.fromForts(List.of())));
			assertFalse(OccupationService.qualifiesNeighbor(
					war, 10, 99, BelligerentRole.DEFENDER, territory, FortZocIndex.fromForts(List.of())));
		}
	}

	@Test
	void computeOccupationZone_skipsNextUnfoughtSiegeHome() {
		Province battle = province(10, Terrain.PLAINS);
		Province nextSiege = province(20, Terrain.PLAINS);
		link(battle, nextSiege);
		pm.start(Map.of(10, battle, 20, nextSiege));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 20, 30));
			war.setCampaignBattleSchedule(List.of(
					new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
					new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "fort_a")));
			war.setCampaignScheduleIndex(0);
			OccupationService withEmptyForts = new OccupationService(
					pm, new TitleManagerProvinceOwnerLookup(), FortZocIndex.fromForts(List.of()));
			assertEquals(
					List.of(10),
					withEmptyForts.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_skipsNeighborInUntakenFortZoc() {
		Province battle = province(10, Terrain.PLAINS);
		Province zocNeighbor = province(21, Terrain.PLAINS);
		Province fortHome = province(20, Terrain.PLAINS);
		link(battle, zocNeighbor);
		link(fortHome, zocNeighbor);
		pm.start(Map.of(10, battle, 20, fortHome, 21, zocNeighbor));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class);
				MockedStatic<SimpleFactions> simpleFactions = mockStatic(SimpleFactions.class)) {
			SimpleFactions plugin = mock(SimpleFactions.class);
			when(plugin.getProvinceManager()).thenReturn(pm);
			simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(21)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 30));
			war.putFortController("fort_a", CampaignCoalition.DEFENDER);
			FortZocIndex forts = FortZocIndex.fromForts(List.of(
					new OperationalFort("fort_a", defender, 20, 100L)));
			OccupationService zocService = new OccupationService(
					pm, new TitleManagerProvinceOwnerLookup(), forts);
			assertEquals(
					List.of(10),
					zocService.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_allowsBattleProvinceInsideFortZoc() {
		Province fortHome = province(20, Terrain.PLAINS);
		pm.start(Map.of(20, fortHome));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class);
				MockedStatic<SimpleFactions> simpleFactions = mockStatic(SimpleFactions.class)) {
			SimpleFactions plugin = mock(SimpleFactions.class);
			when(plugin.getProvinceManager()).thenReturn(pm);
			simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 20, 30));
			war.putFortController("fort_a", CampaignCoalition.DEFENDER);
			FortZocIndex forts = FortZocIndex.fromForts(List.of(
					new OperationalFort("fort_a", defender, 20, 100L)));
			OccupationService zocService = new OccupationService(
					pm, new TitleManagerProvinceOwnerLookup(), forts);
			assertEquals(
					List.of(20),
					zocService.computeOccupationZone(war, 20, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void computeOccupationZone_overlappingZocRequiresAllFortsTaken() {
		Province battle = province(10, Terrain.PLAINS);
		Province overlap = province(21, Terrain.PLAINS);
		Province fortA = province(20, Terrain.PLAINS);
		Province fortB = province(22, Terrain.PLAINS);
		link(battle, overlap);
		link(fortA, overlap);
		link(fortB, overlap);
		pm.start(Map.of(10, battle, 20, fortA, 21, overlap, 22, fortB));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class);
				MockedStatic<SimpleFactions> simpleFactions = mockStatic(SimpleFactions.class)) {
			SimpleFactions plugin = mock(SimpleFactions.class);
			when(plugin.getProvinceManager()).thenReturn(pm);
			simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(21)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(22)).thenReturn(defender);
			War war = baseWar(List.of(5, 10, 30));
			war.putFortController("fort_old", CampaignCoalition.AGGRESSOR);
			war.putFortController("fort_young", CampaignCoalition.DEFENDER);
			FortZocIndex forts = FortZocIndex.fromForts(List.of(
					new OperationalFort("fort_old", defender, 20, 100L),
					new OperationalFort("fort_young", defender, 22, 200L)));
			OccupationService zocService = new OccupationService(
					pm, new TitleManagerProvinceOwnerLookup(), forts);
			assertEquals(
					List.of(10),
					zocService.computeOccupationZone(war, 10, BelligerentRole.ATTACKER).provinceIds());
		}
	}

	@Test
	void applyBattleWin_doesNotOccupyKingLiegeTransit() {
		List<Faction> saved = new ArrayList<>(FactionManager.factions);
		FactionManager.factions.clear();
		try {
			Faction king = mockIndependent("king");
			Faction dukeA = mockSubject("dukeA", "king");
			Faction dukeB = mockSubject("dukeB", "king");
			FactionManager.factions.add(king);
			FactionManager.factions.add(dukeA);
			FactionManager.factions.add(dukeB);

			Province battle = province(10, Terrain.PLAINS);
			Province kingLand = province(21, Terrain.PLAINS);
			link(battle, kingLand);
			pm.start(Map.of(10, battle, 21, kingLand));

			try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
				titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(dukeB);
				titleManager.when(() -> TitleManager.getByProvince(21)).thenReturn(king);
				War war = new War(1, dukeA, dukeB);
				war.setGoal(WarGoalType.SUBJUGATE);
				war.setWarType(WarType.SUBJUGATE);
				war.setObjectiveProvinceId(30);
				war.setCampaignStartProvinceId(10);
				war.setCampaignProvinces(List.of(5, 10, 30));
				war.setCursorIndex(1);
				war.setCampaignPhase(CampaignPhase.INVASION);
				war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
				war.setOccupiedByAttacker(new ArrayList<>());
				war.setOccupiedByDefender(new ArrayList<>());
				war.setLastBattleOccupied(new ArrayList<>());

				assertTrue(service.applyBattleWin(war, 10, BelligerentRole.ATTACKER));
				assertTrue(war.getOccupiedByAttacker().contains(10));
				assertFalse(war.getOccupiedByAttacker().contains(21));
				assertFalse(war.getLastBattleOccupied().contains(21));
				BelligerentTerritory territory = BelligerentTerritory.fromWar(
						war, new TitleManagerProvinceOwnerLookup());
				assertTrue(territory.isLiegeTransit(21));
			}
		} finally {
			FactionManager.factions.clear();
			FactionManager.factions.addAll(saved);
		}
	}

	private War baseWar(List<Integer> axis) {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(10);
		war.setCampaignProvinces(axis);
		war.setCursorIndex(1);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
		war.setLastBattleOccupied(new ArrayList<>());
		return war;
	}

	private void stubOwnership(MockedStatic<TitleManager> titleManager, Province... provinces) {
		for (Province province : provinces) {
			if (province.getId() == 10 || province.getId() == 5) {
				titleManager.when(() -> TitleManager.getByProvince(province.getId())).thenReturn(attacker);
			} else {
				titleManager.when(() -> TitleManager.getByProvince(province.getId())).thenReturn(defender);
			}
		}
	}

	private Province province(int id, Terrain terrain) {
		return new Province(id, terrain.name(), 50, id * 10, id * 10);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}

	private static Faction mockIndependent(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getMembers()).thenReturn(new ArrayList<>());
		return faction;
	}

	private static Faction mockSubject(String id, String overlordId) {
		Faction faction = mockIndependent(id);
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.isOverlord()).thenReturn(true);
		when(type.getId()).thenReturn("subject");
		when(relation.getType()).thenReturn(type);
		HashMap<String, Relation> relations = new HashMap<>();
		relations.put(overlordId, relation);
		when(faction.getRelations()).thenReturn(relations);
		when(faction.getRelation(overlordId)).thenReturn(relation);
		return faction;
	}
}
