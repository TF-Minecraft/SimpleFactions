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

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.enums.Terrain;

class CampaignBattlePlacerTest {
	private static final List<Integer> DEFAULT_AXIS = List.of(5, 10, 20, 30);

	private Faction attacker;
	private Faction defender;
	private MockedStatic<SimpleFactions> simpleFactions;
	private MockedStatic<TitleManager> titleManager;
	private ProvinceManager pm;

	@BeforeEach
	void setUp() {
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
	void placeBattle_border_appendsField() {
		CampaignScheduleBuildContext ctx = context(10, 1);
		War war = war();

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 10, BattleTrigger.BORDER, CampaignCoalition.AGGRESSOR, null, null);

		assertEquals(1, ctx.invasion().size());
		assertEquals(10, ctx.invasion().get(0).provinceId());
		assertEquals(CampaignBattleKind.FIELD, ctx.invasion().get(0).kind());
		assertFalse(ctx.invasion().get(0).required());
	}

	@Test
	void placeBattle_objective_upgradesExistingField() {
		CampaignScheduleBuildContext ctx = context(10, 1);
		ctx.invasion().add(new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, false, null));
		War war = war();

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 30, BattleTrigger.OBJECTIVE, CampaignCoalition.AGGRESSOR, null, null);

		assertEquals(1, ctx.invasion().size());
		assertTrue(ctx.invasion().get(0).required());
	}

	@Test
	void placeBattle_fortZoc_appendsSiegeAtFortHome() {
		pm.start(Map.of(20, province(20), 21, province(21)));
		link(pm.get(20), pm.get(21));
		titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(21)).thenReturn(defender);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(
				List.of(5, 20, 21, 30), 21, 1, 3, index);
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.DEFENDER);

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 21, BattleTrigger.FORT_ZOC, CampaignCoalition.AGGRESSOR, "fort_a", null);

		assertEquals(1, ctx.invasion().size());
		assertEquals(CampaignBattleKind.SIEGE, ctx.invasion().get(0).kind());
		assertEquals(20, ctx.invasion().get(0).provinceId());
		assertTrue(ctx.scheduledFortIds().contains("fort_a"));
	}

	@Test
	void placeBattle_fortZoc_skipsDuplicateFort() {
		pm.start(Map.of(20, province(20)));
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", defender, 20, 100L)));
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(
				List.of(20, 30), 20, 0, 1, index);
		ctx.scheduledFortIds().add("fort_a");
		War war = war();
		war.putFortController("fort_a", CampaignCoalition.DEFENDER);

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 20, BattleTrigger.FORT_ZOC, CampaignCoalition.AGGRESSOR, "fort_a", null);

		assertTrue(ctx.invasion().isEmpty());
	}

	@Test
	void placeBattle_naval_prependsOnInvasion() {
		pm.start(Map.of(11, seaProvince(11)));
		CampaignScheduleBuildContext ctx = context(10, 1);
		ctx.invasion().add(new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null));
		War war = war();

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 11, BattleTrigger.NAVAL, CampaignCoalition.AGGRESSOR, null, "port_a");

		assertEquals(2, ctx.invasion().size());
		assertEquals(CampaignBattleKind.NAVAL, ctx.invasion().get(0).kind());
		assertEquals(11, ctx.invasion().get(0).provinceId());
		assertEquals(10, ctx.invasion().get(1).provinceId());
	}

	@Test
	void placeBattle_naval_skipsDuplicatePortOnLeg() {
		pm.start(Map.of(11, seaProvince(11)));
		CampaignScheduleBuildContext ctx = context(10, 1);
		ctx.portIdsFor(ScheduleLeg.INVASION).add("port_a");
		War war = war();

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 11, BattleTrigger.NAVAL, CampaignCoalition.AGGRESSOR, null, "port_a");

		assertTrue(ctx.invasion().isEmpty());
	}

	@Test
	void placeBattle_cadence_skipsDuplicateField() {
		CampaignScheduleBuildContext ctx = context(10, 1);
		ctx.invasion().add(new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null));
		War war = war();

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 20, BattleTrigger.CADENCE, CampaignCoalition.AGGRESSOR, null, null);

		assertEquals(1, ctx.invasion().size());
	}

	@Test
	void placeBattle_skipsLandCadenceOnSea() {
		pm.start(Map.of(11, seaProvince(11)));
		CampaignScheduleBuildContext ctx = context(10, 1);
		War war = war();

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 11, BattleTrigger.CADENCE, CampaignCoalition.AGGRESSOR, null, null);

		assertTrue(ctx.invasion().isEmpty());
	}

	@Test
	void placeBattle_axisOrder_siegeBeforeLaterField() {
		List<Integer> axis = List.of(709, 713, 706, 705);
		pm.start(Map.of(
				706, province(706),
				713, province(713),
				705, province(705)));
		link(pm.get(713), pm.get(706));
		titleManager.when(() -> TitleManager.getByProvince(713)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(706)).thenReturn(defender);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 100L)));
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(axis, 709, 0, 3, index);
		War war = war();
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 706, BattleTrigger.CADENCE, CampaignCoalition.AGGRESSOR, null, null);
		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 706, BattleTrigger.FORT_ZOC, CampaignCoalition.AGGRESSOR, "Greenfort", null);

		assertEquals(2, ctx.invasion().size());
		assertEquals(CampaignBattleKind.SIEGE, ctx.invasion().get(0).kind());
		assertEquals(713, ctx.invasion().get(0).provinceId());
		assertEquals(CampaignBattleKind.FIELD, ctx.invasion().get(1).kind());
		assertEquals(706, ctx.invasion().get(1).provinceId());
	}

	@Test
	void placeBattle_offAxisSiege_dropsOptionalFieldOnChronologyTile() {
		List<Integer> axis = List.of(704, 705);
		Province airfield = province(704);
		Province fortHome = province(713);
		Province capital = province(705);
		link(fortHome, airfield);
		link(airfield, capital);
		pm.start(Map.of(704, airfield, 713, fortHome, 705, capital));
		titleManager.when(() -> TitleManager.getByProvince(704)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(713)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(705)).thenReturn(defender);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("Greenfort", defender, 713, 100L)));
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(axis, 704, 0, 1, index);
		War war = war();
		war.putFortController("Greenfort", CampaignCoalition.DEFENDER);

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 704, BattleTrigger.BORDER, CampaignCoalition.AGGRESSOR, null, null);
		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 704, BattleTrigger.FORT_ZOC, CampaignCoalition.AGGRESSOR, "Greenfort", null);

		assertEquals(1, ctx.invasion().size());
		assertEquals(CampaignBattleKind.SIEGE, ctx.invasion().get(0).kind());
		assertEquals(713, ctx.invasion().get(0).provinceId());
		assertEquals(704, ctx.invasion().get(0).chronologyProvinceId());
	}

	@Test
	void placeBattle_axisOrder_counterNavalInsertsBySeaIndex() {
		pm.start(Map.of(5, province(5), 10, province(10), 11, seaProvince(11), 20, province(20)));
		List<Integer> axis = List.of(5, 10, 11, 20);
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(
				axis, 20, 3, 0, FortZocIndex.fromForts(List.of()));
		War war = war();

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.COUNTER, 10, BattleTrigger.CADENCE, CampaignCoalition.DEFENDER, null, null);
		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.COUNTER, 11, BattleTrigger.NAVAL, CampaignCoalition.DEFENDER, null, "port_a");

		assertEquals(2, ctx.counter().size());
		assertEquals(CampaignBattleKind.NAVAL, ctx.counter().get(0).kind());
		assertEquals(11, ctx.counter().get(0).provinceId());
		assertEquals(10, ctx.counter().get(1).provinceId());
	}

	@Test
	void placeBattle_sameProvince_siegeBeforeRequiredObjective() {
		List<Integer> axis = List.of(5, 705, 30);
		pm.start(Map.of(705, province(705)));
		titleManager.when(() -> TitleManager.getByProvince(705)).thenReturn(defender);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_cap", defender, 705, 100L)));
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(axis, 705, 1, 1, index);
		War war = war();
		war.putFortController("fort_cap", CampaignCoalition.DEFENDER);

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 705, BattleTrigger.FORT_ZOC, CampaignCoalition.AGGRESSOR, "fort_cap", null);
		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 705, BattleTrigger.OBJECTIVE, CampaignCoalition.AGGRESSOR, null, null);

		assertEquals(2, ctx.invasion().size());
		assertEquals(CampaignBattleKind.SIEGE, ctx.invasion().get(0).kind());
		assertEquals(705, ctx.invasion().get(0).provinceId());
		assertEquals(CampaignBattleKind.FIELD, ctx.invasion().get(1).kind());
		assertTrue(ctx.invasion().get(1).required());
	}

	@Test
	void placeBattle_axisOrder_offAxisFortHomeSortsAtTrigger() {
		List<Integer> axis = List.of(706, 713, 705);
		Province airfieldHome = province(704);
		Province p706 = province(706);
		Province p713 = province(713);
		Province p705 = province(705);
		link(airfieldHome, p706);
		link(p706, p713);
		link(p713, p705);
		pm.start(Map.of(704, airfieldHome, 706, p706, 713, p713, 705, p705));
		titleManager.when(() -> TitleManager.getByProvince(704)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(706)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(713)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(705)).thenReturn(defender);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("Lan_Airfield", defender, 704, 100L)));
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(axis, 706, 0, 2, index);
		War war = war();
		war.putFortController("Lan_Airfield", CampaignCoalition.DEFENDER);

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 705, BattleTrigger.OBJECTIVE, CampaignCoalition.AGGRESSOR, null, null);
		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 706, BattleTrigger.FORT_ZOC, CampaignCoalition.AGGRESSOR, "Lan_Airfield", null);

		assertEquals(2, ctx.invasion().size());
		assertEquals(CampaignBattleKind.SIEGE, ctx.invasion().get(0).kind());
		assertEquals(704, ctx.invasion().get(0).provinceId());
		assertEquals(706, ctx.invasion().get(0).chronologyProvinceId());
		assertEquals(CampaignBattleKind.FIELD, ctx.invasion().get(1).kind());
		assertEquals(705, ctx.invasion().get(1).provinceId());
	}

	@Test
	void placeBattle_fortZoc_skipsInvasionPastObjective() {
		List<Integer> axis = List.of(706, 705, 708);
		Province p706 = province(706);
		Province p705 = province(705);
		Province p708 = province(708);
		link(p706, p705);
		link(p705, p708);
		pm.start(Map.of(706, p706, 705, p705, 708, p708));
		titleManager.when(() -> TitleManager.getByProvince(706)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(705)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(708)).thenReturn(defender);
		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_past", defender, 708, 100L)));
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(axis, 706, 0, 1, index);
		War war = war();
		war.putFortController("fort_past", CampaignCoalition.DEFENDER);

		CampaignBattlePlacer.placeBattle(
				ctx, war, ScheduleLeg.INVASION, 708, BattleTrigger.FORT_ZOC, CampaignCoalition.AGGRESSOR, "fort_past", null);

		assertTrue(ctx.invasion().isEmpty());
	}

	private CampaignScheduleBuildContext context(int borderProvinceId, int cursorIndex) {
		return new CampaignScheduleBuildContext(
				DEFAULT_AXIS,
				borderProvinceId,
				cursorIndex,
				3,
				FortZocIndex.fromForts(List.of()));
	}

	private War war() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		return war;
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
