package me.Plugins.SimpleFactions.War.campaign.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.War.campaign.zoc.PortSeaZocIndex.OperationalPort;
import me.Plugins.SimpleFactions.War.campaign.zoc.PortSeaZocIndex;
import me.Plugins.SimpleFactions.enums.Terrain;

class CampaignScheduleBuilderTest {
	private Faction attacker;
	private Faction defender;
	private MockedStatic<SimpleFactions> simpleFactions;
	private MockedStatic<TitleManager> titleManager;
	private ProvinceManager pm;

	@BeforeEach
	void setUp() {
		Cache.warProvincesBetweenBattles = 1;
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		pm = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);
		titleManager = mockStatic(TitleManager.class);
	}

	@AfterEach
	void tearDown() {
		if (titleManager != null) {
			titleManager.close();
		}
		if (simpleFactions != null) {
			simpleFactions.close();
		}
	}

	@Test
	void build_cadenceOnly_marksObjectiveRequired() {
		List<Integer> axis = List.of(5, 10, 20, 30);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				2,
				3,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertEquals(2, schedule.size());
		assertEquals(20, schedule.get(0).provinceId());
		assertEquals(CampaignBattleKind.FIELD, schedule.get(0).kind());
		assertFalse(schedule.get(0).required());
		assertEquals(30, schedule.get(1).provinceId());
		assertTrue(schedule.get(1).required());
	}

	@Test
	void build_fortOnRoute_insertsSiegeBeforeFields() {
		setupMap(List.of(5, 10, 20, 30), 10, 20, 30);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.DEFENDER);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(5, 10, 20, 30),
				2,
				3,
				index,
				PortSeaZocIndex.fromPorts(List.of()));

		assertEquals(CampaignBattleKind.SIEGE, schedule.stream()
				.filter(slot -> slot.kind() == CampaignBattleKind.SIEGE)
				.findFirst()
				.orElseThrow()
				.kind());
		assertEquals(20, schedule.stream()
				.filter(slot -> slot.kind() == CampaignBattleKind.SIEGE)
				.findFirst()
				.orElseThrow()
				.provinceId());
		assertEquals("fort_a", schedule.stream()
				.filter(slot -> slot.kind() == CampaignBattleKind.SIEGE)
				.findFirst()
				.orElseThrow()
				.fortInstallationId());
		assertTrue(schedule.stream().noneMatch(slot -> slot.provinceId() == 20 && slot.kind() == CampaignBattleKind.FIELD));
		assertTrue(schedule.stream().anyMatch(slot -> slot.provinceId() == 30 && slot.required()));
	}

	@Test
	void build_capitalInsideZoc_keepsSiegeAndRequiredObjectiveSeparate() {
		setupMap(List.of(5, 10, 21, 25), 10, 21, 25);
		Province p20 = province(20);
		Province p21 = province(21);
		link(p20, p21);
		pm.start(Map.of(
				5, province(5),
				10, province(10),
				20, p20,
				21, p21,
				25, province(25)));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 21, 25}, defender);

		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.DEFENDER);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(5, 10, 21, 25),
				2,
				3,
				index,
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.SIEGE
						&& slot.provinceId() == 20
						&& "fort_a".equals(slot.fortInstallationId())));
		assertTrue(schedule.stream().anyMatch(slot -> slot.provinceId() == 25 && slot.required()));
		assertTrue(schedule.stream().noneMatch(slot ->
				slot.kind() == CampaignBattleKind.FIELD && slot.provinceId() == 21));
		assertEquals(2, schedule.size());
	}

	@Test
	void build_attackerOwnedFort_skipsSiege() {
		setupMap(List.of(5, 10, 20, 30), 10, 20, 30);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", attacker, 20, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(5, 10, 20, 30),
				2,
				3,
				index,
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.SIEGE));
	}

	@Test
	void build_manuallyFlippedController_skipsSiege() {
		setupMap(List.of(5, 10, 20, 30), 10, 20, 30);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(5, 10, 20, 30),
				2,
				3,
				index,
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.SIEGE));
	}

	@Test
	void build_enemyPortBlocksSeaRun_insertsNaval() {
		Cache.warPortSeaZocRadius = 2;
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province defCoast = province(20);
		Province objective = province(30);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(defCoast, objective);
		pm.start(Map.of(10, atkCoast, 11, sea, 20, defCoast, 30, objective));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 30}, defender);

		PortSeaZocIndex portIndex = PortSeaZocIndex.fromPorts(List.of(
				new OperationalPort("port_a", defender, 20, 100L)));
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 20, 30),
				0,
				3,
				FortZocIndex.fromForts(List.of()),
				portIndex);

		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.NAVAL
						&& slot.provinceId() == 11
						&& "port_a".equals(slot.portInstallationId())));
	}

	@Test
	void build_friendlyPortOnSeaRun_skipsNaval() {
		Cache.warPortSeaZocRadius = 2;
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province defCoast = province(20);
		Province objective = province(30);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(defCoast, objective);
		pm.start(Map.of(10, atkCoast, 11, sea, 20, defCoast, 30, objective));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 30}, defender);

		PortSeaZocIndex portIndex = PortSeaZocIndex.fromPorts(List.of(
				new OperationalPort("port_a", attacker, 10, 100L)));
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 20, 30),
				0,
				3,
				FortZocIndex.fromForts(List.of()),
				portIndex);

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL));
	}

	@Test
	void build_seaRunWithoutPort_skipsNaval() {
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province defCoast = province(20);
		Province objective = province(30);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(defCoast, objective);
		pm.start(Map.of(10, atkCoast, 11, sea, 20, defCoast, 30, objective));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 30}, defender);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 20, 30),
				0,
				3,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL));
	}

	@Test
	void build_seaCrossing_withoutPort_placesBorderFieldOnly() {
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province defCoast = province(20);
		Province objective = province(30);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(defCoast, objective);
		pm.start(Map.of(10, atkCoast, 11, sea, 20, defCoast, 30, objective));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 30}, defender);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 20, 30),
				0,
				3,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
		assertEquals(10, schedule.get(0).provinceId());
		assertEquals(CampaignBattleKind.FIELD, schedule.get(0).kind());
	}

	@Test
	void build_seaCrossing_skipsNavalWhenExitCoastIsAttacker() {
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province atkBeachhead = province(12);
		Province defCoast = province(20);
		link(atkCoast, sea);
		link(sea, atkBeachhead);
		link(atkBeachhead, defCoast);
		pm.start(Map.of(10, atkCoast, 11, sea, 12, atkBeachhead, 20, defCoast));
		stubOwnership(new int[] {10, 12}, attacker);
		stubOwnership(20, defender);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 12, 20),
				0,
				3,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL));
		assertEquals(10, schedule.get(0).provinceId());
	}

	@Test
	void build_seaCrossing_borderAtCoast_placesBorderFieldNotNavalInvasion() {
		Cache.warProvincesBetweenBattles = 1;
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province defCoast = province(20);
		Province objective = province(30);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(defCoast, objective);
		pm.start(Map.of(10, atkCoast, 11, sea, 20, defCoast, 30, objective));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 30}, defender);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 20, 30),
				2,
				3,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
		assertTrue(schedule.stream().anyMatch(slot ->
				slot.provinceId() == 20 && slot.kind() == CampaignBattleKind.FIELD));
	}

	@Test
	void build_seaCrossing_landingInEnemyFortZoc_insertsSiegeNotInvasion() {
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province fortProvince = province(20);
		Province defCoast = province(21);
		Province objective = province(30);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(fortProvince, defCoast);
		link(defCoast, objective);
		pm.start(Map.of(10, atkCoast, 11, sea, 20, fortProvince, 21, defCoast, 30, objective));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 21, 30}, defender);

		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.DEFENDER);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 21, 30),
				0,
				3,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.SIEGE
						&& slot.provinceId() == 20
						&& "fort_a".equals(slot.fortInstallationId())));
		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
	}

	@Test
	void build_seaCrossing_landingInFriendlyFortZoc_skipsSiege() {
		Province atkCoast = province(10);
		Province sea = seaProvince(11);
		Province fortProvince = province(20);
		Province defCoast = province(21);
		Province objective = province(30);
		link(atkCoast, sea);
		link(sea, defCoast);
		link(fortProvince, defCoast);
		link(defCoast, objective);
		pm.start(Map.of(10, atkCoast, 11, sea, 20, fortProvince, 21, defCoast, 30, objective));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {20, 21, 30}, defender);

		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(10, 11, 21, 30),
				0,
				3,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.SIEGE));
		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
		assertEquals(10, schedule.get(0).provinceId());
	}

	@Test
	void build_offAxisFortZocOnBorderCapital_anchorsSiegeOnCapital() {
		Province atk = province(5);
		Province sea = seaProvince(795);
		Province fortProvince = province(713);
		Province capital = province(705);
		link(atk, sea);
		link(sea, capital);
		link(fortProvince, capital);
		pm.start(Map.of(5, atk, 795, sea, 713, fortProvince, 705, capital));
		stubOwnership(5, attacker);
		stubOwnership(new int[] {713, 705}, defender);

		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 100L)));
		War war = war();
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);

		List<Integer> axis = List.of(5, 795, 705);
		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				2,
				2,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertEquals(1, schedule.stream()
				.filter(slot -> slot.kind() == CampaignBattleKind.SIEGE
						&& "Greenfort".equals(slot.fortInstallationId()))
				.count());
		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.SIEGE
						&& slot.provinceId() == 713
						&& "Greenfort".equals(slot.fortInstallationId())));
		assertTrue(schedule.stream().anyMatch(slot ->
				slot.provinceId() == 705 && slot.required()));
		assertEquals(2, schedule.size());
	}

	@Test
	void build_offAxisFortZocOnBorder_siegeReplacesBorderField() {
		Province atk = province(10);
		Province border = province(704);
		Province fortHome = province(713);
		Province capital = province(705);
		link(atk, border);
		link(border, capital);
		link(fortHome, border);
		pm.start(Map.of(10, atk, 704, border, 713, fortHome, 705, capital));
		stubOwnership(10, attacker);
		stubOwnership(new int[] {704, 713, 705}, defender);

		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 100L)));
		War war = war();
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);

		List<Integer> axis = List.of(10, 704, 705);
		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				1,
				2,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertEquals(2, schedule.size());
		assertEquals(CampaignBattleKind.SIEGE, schedule.get(0).kind());
		assertEquals(713, schedule.get(0).provinceId());
		assertEquals(704, schedule.get(0).chronologyProvinceId());
		assertEquals("Greenfort", schedule.get(0).fortInstallationId());
		assertTrue(schedule.stream().noneMatch(slot ->
				slot.kind() == CampaignBattleKind.FIELD && slot.provinceId() == 704));
		assertEquals(705, schedule.get(1).provinceId());
		assertTrue(schedule.get(1).required());
	}

	@Test
	void build_sameFortOnConsecutiveAxisSteps_schedulesOneSiege() {
		Province atk = province(5);
		Province zocA = province(20);
		Province zocB = province(21);
		Province objective = province(25);
		link(atk, zocA);
		link(zocA, zocB);
		link(zocB, objective);
		pm.start(Map.of(5, atk, 20, zocA, 21, zocB, 25, objective));
		stubOwnership(5, attacker);
		stubOwnership(new int[] {20, 21, 25}, defender);

		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.DEFENDER);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				List.of(5, 20, 21, 25),
				1,
				3,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertEquals(1, schedule.stream()
				.filter(slot -> slot.kind() == CampaignBattleKind.SIEGE)
				.count());
		assertEquals(20, schedule.stream()
				.filter(slot -> slot.kind() == CampaignBattleKind.SIEGE)
				.findFirst()
				.orElseThrow()
				.provinceId());
	}

	@Test
	void build_brumeShaped_portAndFortZoc_anchorBattlesOnAxis() {
		Cache.warPortSeaZocRadius = 2;
		Cache.warProvincesBetweenBattles = 3;
		Province atk = province(452);
		Province sea = seaProvince(795);
		Province border = province(709);
		Province fortProvince = province(713);
		Province portProvince = province(695);
		Province capital = province(705);
		link(atk, sea);
		link(sea, border);
		link(border, capital);
		link(border, fortProvince);
		link(fortProvince, capital);
		link(portProvince, sea);
		pm.start(Map.of(
				452, atk,
				795, sea,
				709, border,
				713, fortProvince,
				695, portProvince,
				705, capital));
		stubOwnership(452, attacker);
		stubOwnership(new int[] {713, 695, 709, 705}, defender);

		PortSeaZocIndex portIndex = PortSeaZocIndex.fromPorts(List.of(
				new OperationalPort("Lan_Harbour", defender, 695, 100L)));
		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 200L)));
		War war = war();
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);

		List<Integer> axis = List.of(452, 795, 709, 713, 705);
		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				2,
				4,
				fortIndex,
				portIndex);

		assertEquals(3, schedule.size());
		assertEquals(CampaignBattleKind.NAVAL, schedule.get(0).kind());
		assertEquals(795, schedule.get(0).provinceId());
		assertEquals("Lan_Harbour", schedule.get(0).portInstallationId());
		assertEquals(CampaignBattleKind.SIEGE, schedule.get(1).kind());
		assertEquals(713, schedule.get(1).provinceId());
		assertEquals("Greenfort", schedule.get(1).fortInstallationId());
		assertTrue(schedule.stream().noneMatch(slot ->
				slot.provinceId() == 709 && slot.kind() == CampaignBattleKind.FIELD));
		ScheduledCampaignBattle last = schedule.get(schedule.size() - 1);
		assertEquals(705, last.provinceId());
		assertTrue(last.required());
	}

	@Test
	void build_landOnlyAxis_noNavalInvasion() {
		List<Integer> axis = List.of(5, 10, 20, 30);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				2,
				3,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
	}

	@Test
	void buildCounter_cadence_marksCapitalRequired() {
		List<Integer> axis = List.of(5, 10, 20, 30);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.buildCounter(
				war,
				axis,
				2,
				0,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().anyMatch(slot -> slot.provinceId() == 10 && !slot.required()));
		assertTrue(schedule.stream().anyMatch(slot -> slot.provinceId() == 5 && slot.required()));
		assertTrue(schedule.stream().noneMatch(slot -> slot.provinceId() == 20));
	}

	@Test
	void buildCounter_emptyWhenBorderAtCapital() {
		List<Integer> axis = List.of(5, 10, 20, 30);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.buildCounter(
				war,
				axis,
				0,
				0,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.isEmpty());
	}

	@Test
	void buildCounter_fortOnRoute_insertsSiege() {
		setupMap(List.of(5, 10, 20, 30), 5, 20, 30);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", attacker, 10, 100L)));
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.buildCounter(
				war,
				List.of(5, 10, 20, 30),
				2,
				0,
				index,
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.SIEGE
						&& slot.provinceId() == 10
						&& "fort_a".equals(slot.fortInstallationId())));
	}

	@Test
	void buildCounter_navalOnSeaRun() {
		Cache.warPortSeaZocRadius = 2;
		Province capital = province(5);
		Province sea = seaProvince(11);
		Province defCoast = province(20);
		Province atkCoast = province(30);
		link(capital, sea);
		link(sea, defCoast);
		link(defCoast, atkCoast);
		pm.start(Map.of(5, capital, 11, sea, 20, defCoast, 30, atkCoast));
		stubOwnership(5, attacker);
		stubOwnership(new int[] {20, 30}, defender);

		PortSeaZocIndex portIndex = PortSeaZocIndex.fromPorts(List.of(
				new OperationalPort("port_a", attacker, 5, 100L)));
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.buildCounter(
				war,
				List.of(5, 11, 20, 30),
				3,
				0,
				FortZocIndex.fromForts(List.of()),
				portIndex);

		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.NAVAL
						&& slot.provinceId() == 11
						&& "port_a".equals(slot.portInstallationId())));
	}

	@Test
	void buildCounter_noBorderSlot() {
		List<Integer> axis = List.of(5, 10, 20, 30);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.buildCounter(
				war,
				axis,
				2,
				0,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.stream().noneMatch(slot -> slot.provinceId() == 20));
	}

	@Test
	void buildCounter_brumeAxis_wildernessCadenceFields() {
		Cache.warProvincesBetweenBattles = 3;
		List<Integer> axis = List.of(452, 782, 758, 757, 672, 709, 713, 705);
		setupMap(axis, 452, 709, 713, 705);
		War war = war();

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.buildCounter(
				war,
				axis,
				5,
				0,
				FortZocIndex.fromForts(List.of()),
				PortSeaZocIndex.fromPorts(List.of()));

		assertFalse(schedule.isEmpty());
		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.FIELD
						&& slot.provinceId() == 672
						&& !slot.required()));
		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.FIELD
						&& slot.provinceId() == 782
						&& !slot.required()));
		ScheduledCampaignBattle last = schedule.get(schedule.size() - 1);
		assertEquals(CampaignBattleKind.FIELD, last.kind());
		assertEquals(452, last.provinceId());
		assertTrue(last.required());
		assertTrue(schedule.stream().noneMatch(slot -> slot.provinceId() == 709));
		assertTrue(indexOfProvince(schedule, 672) < indexOfProvince(schedule, 782));
		assertTrue(indexOfProvince(schedule, 782) < indexOfProvince(schedule, 452));
	}

	private static int indexOfProvince(List<ScheduledCampaignBattle> schedule, int provinceId) {
		for (int index = 0; index < schedule.size(); index++) {
			if (schedule.get(index).provinceId() == provinceId) {
				return index;
			}
		}
		return -1;
	}

	@Test
	void build_brumeAxis_invasion_cadenceAndSiege() {
		Cache.warProvincesBetweenBattles = 3;
		List<Integer> axis = List.of(452, 782, 758, 757, 672, 709, 713, 705);
		setupMap(axis, 452, 709, 713, 705);
		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 100L)));
		War war = war();
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				5,
				7,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertTrue(schedule.size() <= 4);
		assertEquals(2, schedule.size());
		assertEquals(CampaignBattleKind.SIEGE, schedule.get(0).kind());
		assertEquals(713, schedule.get(0).provinceId());
		assertEquals("Greenfort", schedule.get(0).fortInstallationId());
		assertTrue(schedule.stream().noneMatch(slot ->
				slot.provinceId() == 709 && slot.kind() == CampaignBattleKind.FIELD));
		assertEquals(CampaignBattleKind.FIELD, schedule.get(1).kind());
		assertEquals(705, schedule.get(1).provinceId());
		assertTrue(schedule.get(1).required());
		assertTrue(schedule.stream().noneMatch(slot -> slot.kind() == CampaignBattleKind.NAVAL_INVASION));
		war.setObjectiveProvinceId(705);
		assertTrue(CampaignScheduleValidator.isValidInvasionSchedule(war, axis, schedule));
	}

	@Test
	void build_borderAtFortHome_placesSiegeNotBorderField() {
		List<Integer> axis = List.of(452, 709, 713, 705);
		setupMap(axis, 452, 709, 713, 705);
		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 100L)));
		War war = war();
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				2,
				3,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertEquals(2, schedule.size());
		assertEquals(CampaignBattleKind.SIEGE, schedule.get(0).kind());
		assertEquals(713, schedule.get(0).provinceId());
		assertEquals("Greenfort", schedule.get(0).fortInstallationId());
		assertTrue(schedule.stream().noneMatch(slot ->
				slot.provinceId() == 713 && slot.kind() == CampaignBattleKind.FIELD));
		assertEquals(705, schedule.get(1).provinceId());
		assertTrue(schedule.get(1).required());
	}

	@Test
	void build_brumeAxis_dualFortOverlap_onlyGreenfortSiege() {
		Cache.warProvincesBetweenBattles = 3;
		List<Integer> axis = List.of(452, 782, 758, 757, 672, 709, 713, 705);
		setupMap(axis, 452, 709, 713, 705);
		Map<Integer, Province> map = new java.util.HashMap<>();
		for (int id : axis) {
			map.put(id, pm.get(id));
		}
		Province airfieldHome = province(704);
		map.put(704, airfieldHome);
		link(pm.get(705), airfieldHome);
		pm.start(map);
		stubOwnership(452, attacker);
		stubOwnership(new int[] {709, 713, 705, 704}, defender);

		FortZocIndex fortIndex = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 1787472176192L),
				new OperationalFort("Lan_Airfield", defender, 704, 1787472195192L)));
		War war = war();
		war.setObjectiveProvinceId(705);
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);
		war.putFortController("Lan_Airfield", CampaignCoalition.DEFENDER);

		List<ScheduledCampaignBattle> schedule = CampaignScheduleBuilder.build(
				war,
				axis,
				5,
				7,
				fortIndex,
				PortSeaZocIndex.fromPorts(List.of()));

		assertEquals(2, schedule.size());
		assertTrue(schedule.stream().noneMatch(slot -> "Lan_Airfield".equals(slot.fortInstallationId())));
		assertTrue(schedule.stream().anyMatch(slot ->
				slot.kind() == CampaignBattleKind.SIEGE && "Greenfort".equals(slot.fortInstallationId())));
		assertTrue(CampaignScheduleValidator.isValidInvasionSchedule(war, axis, schedule));
	}

	private War war() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		return war;
	}

	private void setupMap(List<Integer> axisIds, int attackerProvince, int... defenderProvinces) {
		Map<Integer, Province> map = new java.util.HashMap<>();
		for (int id : axisIds) {
			map.put(id, province(id));
		}
		for (int i = 0; i < axisIds.size() - 1; i++) {
			link(map.get(axisIds.get(i)), map.get(axisIds.get(i + 1)));
		}
		pm.start(map);
		stubOwnership(attackerProvince, attacker);
		stubOwnership(defenderProvinces, defender);
	}

	private void stubOwnership(int provinceId, Faction owner) {
		titleManager.when(() -> TitleManager.getByProvince(provinceId)).thenReturn(owner);
	}

	private void stubOwnership(int[] provinceIds, Faction owner) {
		for (int provinceId : provinceIds) {
			stubOwnership(provinceId, owner);
		}
	}

	private Province province(int id) {
		return new Province(id, Terrain.PLAINS.name(), 50, id * 10, id * 10);
	}

	private Province seaProvince(int id) {
		return new Province(id, Terrain.SEA.name(), 50, id * 10, id * 10);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}
}
