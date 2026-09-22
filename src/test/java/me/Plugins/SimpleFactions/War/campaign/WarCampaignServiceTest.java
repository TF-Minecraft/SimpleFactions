package me.Plugins.SimpleFactions.War.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignProgressionService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class WarCampaignServiceTest {
	private ProvinceManager pm;
	private WarCampaignService service;
	private Faction attacker;
	private Faction defender;
	private MockedStatic<SimpleFactions> simpleFactions;

	@BeforeEach
	void setUp() {
		Cache.tradeCarry.clear();
		Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
		Cache.warPathfinderNeutralPenalty = 8.0;
		Cache.warPathfinderSeaPassEnabled = true;
		Cache.warPathfinderWaterCost = 0.0;
		Cache.warInitiativeFactor = 1.5;
		Cache.warGoalMaxBattles = new EnumMap<>(WarGoalType.class);
		for (WarGoalType goal : WarGoalType.values()) {
			Cache.warGoalMaxBattles.put(goal, 4);
		}
		Cache.warFirstBattleAtBorder = true;
		Cache.warFirstBattleDayAfterDeclare = true;
		Cache.warProvincesBetweenBattles = 1;
		Cache.warPortSeaZocRadius = 2;
		Cache.tradeCarry.put(Terrain.SEA, 0.6);

		pm = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);
		service = new WarCampaignService(pm);

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getCapital()).thenReturn(5);
		when(defender.getCapital()).thenReturn(30);

		SettlementHandler handler = mock(SettlementHandler.class);
		when(defender.getSettlementHandler()).thenReturn(handler);
		when(handler.getAll()).thenReturn(List.of());
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(defender);
		FactionManager.factions.remove(attacker);
		if (simpleFactions != null) {
			simpleFactions.close();
		}
	}

	@Test
	void populateCampaign_keepsNextBattleVisibleAfterScheduleInit() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province border = province(10, Terrain.PLAINS);
		Province mid = province(20, Terrain.PLAINS);
		Province objective = province(30, Terrain.PLAINS);
		link(atkCapital, border);
		link(border, mid);
		link(mid, objective);
		pm.start(Map.of(5, atkCapital, 10, border, 20, mid, 30, objective));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, border, mid, objective);

			assertTrue(service.populateCampaign(war));
			assertTrue(war.isPostBattleChoiceResolved());
			assertEquals(List.of(20), CampaignProgressionService.resolveNextBattleNodes(war));
		}
	}

	@Test
	void populateCampaign_buildsFullAxisWithCursorAtBorder() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province border = province(10, Terrain.PLAINS);
		Province mid = province(20, Terrain.PLAINS);
		Province objective = province(30, Terrain.PLAINS);
		link(atkCapital, border);
		link(border, mid);
		link(mid, objective);
		pm.start(Map.of(5, atkCapital, 10, border, 20, mid, 30, objective));

		War war = new War(
				1,
				new Side(attacker),
				new Side(defender),
				WarGoalType.SUBJUGATE,
				WarType.SUBJUGATE,
				null,
				null,
				Instant.parse("2026-08-19T12:00:00Z"));

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, border, mid, objective);

			assertTrue(service.populateCampaign(war));
			assertEquals(30, war.getObjectiveProvinceId());
			assertEquals(20, war.getCampaignStartProvinceId());
			assertEquals(List.of(5, 10, 20, 30), war.getCampaignProvinces());
			assertEquals(2, war.getCursorIndex());
			int expectedAttackerFuel = (int) Math.ceil(
					war.getCampaignBattleSchedule().size() * Cache.warInitiativeFactor);
			int expectedDefenderFuel = (int) Math.ceil(
					war.getCampaignCounterSchedule().size() * Cache.warInitiativeFactor);
			assertEquals(expectedAttackerFuel, war.getInitiativeAttacker());
			assertEquals(expectedDefenderFuel, war.getInitiativeDefender());
			assertEquals(CampaignPhase.INVASION, war.getCampaignPhase());
			assertEquals(ObjectiveHolder.DEFENDER, war.getObjectiveHeldBy());
			assertTrue(war.getOccupiedByAttacker().isEmpty());
			assertFalse(war.isWhitePeaceProposedByAttacker());
			assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
			assertEquals(LocalDate.parse("2026-08-20"), war.getBattleDay());
			assertTrue(war.getBattleVotes().isEmpty());
			assertEquals(0, war.getPostponementsThisCycle());
		}
	}

	@Test
	void populateCampaign_buildsCounterScheduleLeftOfBorder() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province border = province(10, Terrain.PLAINS);
		Province mid = province(20, Terrain.PLAINS);
		Province objective = province(30, Terrain.PLAINS);
		link(atkCapital, border);
		link(border, mid);
		link(mid, objective);
		pm.start(Map.of(5, atkCapital, 10, border, 20, mid, 30, objective));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 30));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));

			assertFalse(war.getCampaignCounterSchedule().isEmpty());
			assertTrue(war.getCampaignCounterSchedule().stream()
					.noneMatch(slot -> slot.provinceId() == 20));
			assertTrue(war.getCampaignCounterSchedule().stream()
					.anyMatch(slot -> slot.provinceId() == 5 && slot.required()));
			assertTrue(war.getCampaignBattleSchedule().size() <= 4);
			assertTrue(war.getCampaignCounterSchedule().size() <= 4);
			assertEquals(0, war.getCampaignCounterScheduleIndex());
			int expectedAttackerFuel = (int) Math.ceil(
					war.getCampaignBattleSchedule().size() * Cache.warInitiativeFactor);
			int expectedDefenderFuel = (int) Math.ceil(
					war.getCampaignCounterSchedule().size() * Cache.warInitiativeFactor);
			assertEquals(expectedAttackerFuel, war.getInitiativeAttacker());
			assertEquals(expectedDefenderFuel, war.getInitiativeDefender());
		}
	}

	@Test
	void populateCampaign_usesDefenderCapitalWhenCloserThanRegionalObjective() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province border = province(10, Terrain.PLAINS);
		Province defMid = province(20, Terrain.PLAINS);
		Province defCapital = province(25, Terrain.PLAINS);
		Province regionalFar = province(30, Terrain.PLAINS);
		link(atkCapital, border);
		link(border, defMid);
		link(defMid, defCapital);
		link(defCapital, regionalFar);
		pm.start(Map.of(5, atkCapital, 10, border, 20, defMid, 25, defCapital, 30, regionalFar));

		when(defender.getCapital()).thenReturn(25);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(30));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(25)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));
			assertEquals(25, war.getObjectiveProvinceId());
			assertEquals(List.of(5, 10, 20, 25), war.getCampaignProvinces());
			assertEquals(2, war.getCursorIndex());
		}
	}

	@Test
	void populateCampaign_adjacentCapitals_firstBattleAtDefenderCapital() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province defCapital = province(30, Terrain.PLAINS);
		link(atkCapital, defCapital);
		pm.start(Map.of(5, atkCapital, 30, defCapital));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(30));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));
			assertEquals(30, war.getObjectiveProvinceId());
			assertEquals(30, war.getCampaignStartProvinceId());
			assertEquals(List.of(5, 30), war.getCampaignProvinces());
			assertEquals(1, war.getCursorIndex());
		}
	}

	@Test
	void populateCampaign_fortBeforeCapital_firstBattleAtFort() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province atkBorder = province(10, Terrain.PLAINS);
		Province fort = province(20, Terrain.PLAINS);
		Province defCapital = province(25, Terrain.PLAINS);
		link(atkCapital, atkBorder);
		link(atkBorder, fort);
		link(fort, defCapital);
		pm.start(Map.of(5, atkCapital, 10, atkBorder, 20, fort, 25, defCapital));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		when(defender.getCapital()).thenReturn(25);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 25));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(25)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));
			assertEquals(25, war.getObjectiveProvinceId());
			assertEquals(20, war.getCampaignStartProvinceId());
			assertEquals(List.of(5, 10, 20, 25), war.getCampaignProvinces());
			assertEquals(2, war.getCursorIndex());
		}
	}

	@Test
	void populateCampaign_failsWhenAttackerHasNoCapital() {
		when(attacker.getCapital()).thenReturn(0);
		Province border = province(10, Terrain.PLAINS);
		Province objective = province(30, Terrain.PLAINS);
		link(border, objective);
		pm.start(Map.of(10, border, 30, objective));

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			stubOwnership(titleManager, border, objective);
			assertFalse(service.populateCampaign(war));
		}
	}

	@Test
	void populateCampaign_schedulesSiegeWhenFortOnRoute() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province atkBorder = province(10, Terrain.PLAINS);
		Province fortProvince = province(20, Terrain.PLAINS);
		Province defCapital = province(25, Terrain.PLAINS);
		link(atkCapital, atkBorder);
		link(atkBorder, fortProvince);
		link(fortProvince, defCapital);
		pm.start(Map.of(5, atkCapital, 10, atkBorder, 20, fortProvince, 25, defCapital));

		InstallationHandler installationHandler = mock(InstallationHandler.class);
		Installation fort = new Installation(
				"fort_a",
				"Fort",
				InstallationKind.FORT,
				20,
				0,
				0,
				1_000L);
		when(installationHandler.getAll()).thenReturn(List.of(fort));
		when(defender.getInstallationHandler()).thenReturn(installationHandler);
		FactionManager.factions.add(defender);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		when(defender.getCapital()).thenReturn(25);

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 25));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(25)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));

			List<ScheduledCampaignBattle> schedule = war.getCampaignBattleSchedule();
			assertTrue(schedule.stream().anyMatch(slot ->
					slot.kind() == CampaignBattleKind.SIEGE
							&& "fort_a".equals(slot.fortInstallationId())));
			assertEquals(CampaignCoalition.DEFENDER, war.getFortControllers().get("fort_a"));
			ScheduledCampaignBattle last = schedule.get(schedule.size() - 1);
			assertEquals(25, last.provinceId());
			assertTrue(last.required());
			assertEquals(0, war.getCampaignScheduleIndex());
		}
	}

	@Test
	void populateCampaign_schedulesNavalWhenPortBlocksSea() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province atkCoast = province(10, Terrain.PLAINS);
		Province foreignLand = province(12, Terrain.PLAINS);
		Province sea = province(11, Terrain.SEA);
		Province defCoast = province(20, Terrain.PLAINS);
		Province defCapital = province(30, Terrain.PLAINS);
		link(atkCapital, atkCoast);
		link(atkCoast, foreignLand);
		link(foreignLand, defCoast);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(defCoast, defCapital);
		pm.start(Map.of(
				5, atkCapital,
				10, atkCoast,
				11, sea,
				12, foreignLand,
				20, defCoast,
				30, defCapital));

		InstallationHandler installationHandler = mock(InstallationHandler.class);
		Installation port = new Installation(
				"port_a",
				"Port",
				InstallationKind.PORT,
				20,
				0,
				0,
				1_000L);
		when(installationHandler.getAll()).thenReturn(List.of(port));
		when(defender.getInstallationHandler()).thenReturn(installationHandler);
		FactionManager.factions.add(defender);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		when(defender.getCapital()).thenReturn(30);

		Faction foreignFaction = mock(Faction.class);
		when(foreignFaction.getId()).thenReturn("foreign");

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 30));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(11)).thenReturn(null);
			titleManager.when(() -> TitleManager.getByProvince(12)).thenReturn(foreignFaction);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));

			List<ScheduledCampaignBattle> schedule = war.getCampaignBattleSchedule();
			assertFalse(schedule.isEmpty());
			assertEquals(CampaignBattleKind.NAVAL, schedule.get(0).kind());
			assertEquals(11, schedule.get(0).provinceId());
			assertEquals("port_a", schedule.get(0).portInstallationId());
			assertEquals(CampaignBattleKind.FIELD, schedule.get(1).kind());
			assertEquals(20, schedule.get(1).provinceId());
			assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
		}
	}

	@Test
	void applyInitiativeFromLegs_asymmetricFuel() {
		War war = new War(1, attacker, defender);
		List<ScheduledCampaignBattle> invasion = List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, true, null),
				new ScheduledCampaignBattle(40, CampaignBattleKind.FIELD, false, null));
		List<ScheduledCampaignBattle> counter = List.of(
				new ScheduledCampaignBattle(8, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null));

		WarCampaignService.applyInitiativeFromLegs(war, invasion, counter);

		assertEquals(6, war.getInitiativeAttacker());
		assertEquals(3, war.getInitiativeDefender());
	}

	@Test
	void mergeAxisPaths_deduplicatesBorderProvince() {
		assertEquals(
				List.of(5, 10, 20, 30),
				WarCampaignService.mergeAxisPaths(List.of(5, 10), List.of(10, 20, 30)));
	}

	@Test
	void populateCampaign_pillageOneBattleAtSettlementSkipsCapitalRetarget() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province border = province(10, Terrain.PLAINS);
		Province capital = province(15, Terrain.PLAINS);
		Province settlementLand = province(30, Terrain.PLAINS);
		link(atkCapital, border);
		link(border, capital);
		link(capital, settlementLand);
		pm.start(Map.of(5, atkCapital, 10, border, 15, capital, 30, settlementLand));

		when(defender.getCapital()).thenReturn(15);
		Settlement settlement = new Settlement("town", "Town", 30, 0, 0);
		when(defender.getSettlementHandler().getById("town")).thenReturn(settlement);
		InstallationHandler installations = mock(InstallationHandler.class);
		when(installations.getAll()).thenReturn(List.of());
		when(defender.getInstallationHandler()).thenReturn(installations);
		FactionManager.factions.add(defender);

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.PILLAGE);
		war.setTargetSettlementId("town");

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(15, 30));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(15)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));
			assertEquals(30, war.getObjectiveProvinceId());
			assertEquals(1, war.getCampaignBattleSchedule().size());
			assertEquals(30, war.getCampaignBattleSchedule().get(0).provinceId());
			assertEquals(CampaignBattleKind.FIELD, war.getCampaignBattleSchedule().get(0).kind());
			assertTrue(war.getCampaignBattleSchedule().get(0).required());
			assertTrue(war.getCampaignCounterSchedule().isEmpty());
			assertFalse(war.isPillageNaturalNavyRequired());
		}
	}

	@Test
	void populateCampaign_pillageKeepsNaturalNavyFlag() {
		Province atkCapital = province(5, Terrain.PLAINS);
		Province atkCoast = province(10, Terrain.PLAINS);
		Province foreignLand = province(12, Terrain.PLAINS);
		Province sea = province(11, Terrain.SEA);
		Province defCoast = province(20, Terrain.PLAINS);
		Province defCapital = province(30, Terrain.PLAINS);
		link(atkCapital, atkCoast);
		link(atkCoast, foreignLand);
		link(foreignLand, defCoast);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(defCoast, defCapital);
		pm.start(Map.of(
				5, atkCapital,
				10, atkCoast,
				11, sea,
				12, foreignLand,
				20, defCoast,
				30, defCapital));

		InstallationHandler installationHandler = mock(InstallationHandler.class);
		Installation port = new Installation(
				"port_a",
				"Port",
				InstallationKind.PORT,
				20,
				0,
				0,
				1_000L);
		when(installationHandler.getAll()).thenReturn(List.of(port));
		when(defender.getInstallationHandler()).thenReturn(installationHandler);
		when(defender.getCapital()).thenReturn(30);
		Settlement settlement = new Settlement("town", "Town", 30, 0, 0);
		when(defender.getSettlementHandler().getById("town")).thenReturn(settlement);
		FactionManager.factions.add(defender);

		Faction foreignFaction = mock(Faction.class);
		when(foreignFaction.getId()).thenReturn("foreign");

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.PILLAGE);
		war.setTargetSettlementId("town");

		try (MockedStatic<TitleManager> titleManager = mockStatic(TitleManager.class)) {
			titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 30));
			titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
			titleManager.when(() -> TitleManager.getByProvince(11)).thenReturn(null);
			titleManager.when(() -> TitleManager.getByProvince(12)).thenReturn(foreignFaction);
			titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
			titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

			assertTrue(service.populateCampaign(war));
			assertEquals(1, war.getCampaignBattleSchedule().size());
			assertEquals(30, war.getCampaignBattleSchedule().get(0).provinceId());
			assertEquals(CampaignBattleKind.FIELD, war.getCampaignBattleSchedule().get(0).kind());
			assertTrue(war.getCampaignCounterSchedule().isEmpty());
			assertTrue(war.isPillageNaturalNavyRequired());
		}
	}

	private void stubOwnership(MockedStatic<TitleManager> titleManager, Province... provinces) {
		titleManager.when(() -> TitleManager.getProvinces(defender)).thenReturn(List.of(20, 30));
		for (Province province : provinces) {
			if (province.getId() == 5 || province.getId() == 10) {
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
}
