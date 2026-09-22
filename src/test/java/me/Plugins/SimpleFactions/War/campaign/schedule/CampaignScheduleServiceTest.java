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
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.enums.Terrain;

class CampaignScheduleServiceTest {
	private Faction attacker;
	private Faction defender;
	private ProvinceManager pm;
	private MockedStatic<SimpleFactions> simpleFactions;
	private MockedStatic<TitleManager> titleManager;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		pm = new ProvinceManager();
		Province p5 = province(5);
		Province p10 = province(10);
		Province p20 = province(20);
		Province p30 = province(30);
		link(p5, p10);
		link(p10, p20);
		link(p20, p30);
		pm.start(Map.of(5, p5, 10, p10, 20, p20, 30, p30));

		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		simpleFactions = mockStatic(SimpleFactions.class);
		simpleFactions.when(SimpleFactions::getInstance).thenReturn(plugin);

		titleManager = mockStatic(TitleManager.class);
		titleManager.when(() -> TitleManager.getByProvince(5)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(10)).thenReturn(attacker);
		titleManager.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
		titleManager.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);
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
	void currentSlot_returnsIndex0Then1() {
		War war = warWithSchedule(
				field(20),
				siege(18, "fort_a"));

		assertEquals(20, CampaignScheduleService.currentSlot(war).orElseThrow().provinceId());

		CampaignScheduleService.advanceIndex(war);

		assertEquals(18, CampaignScheduleService.currentSlot(war).orElseThrow().provinceId());
	}

	@Test
	void advanceIndex_increments() {
		War war = warWithSchedule(field(20));
		assertEquals(0, war.getCampaignScheduleIndex());

		CampaignScheduleService.advanceIndex(war);

		assertEquals(1, war.getCampaignScheduleIndex());
	}

	@Test
	void insertSiegeAtCurrentIndex_shiftsTail() {
		War war = warWithSchedule(field(20), field(30));
		OperationalFort fort = new OperationalFort("fort_a", defender, 18, 100L);

		CampaignScheduleService.insertSiegeAtCurrentIndex(war, fort, 20);

		assertEquals(3, war.getCampaignBattleSchedule().size());
		assertEquals(CampaignBattleKind.SIEGE, war.getCampaignBattleSchedule().get(0).kind());
		assertEquals(20, war.getCampaignBattleSchedule().get(0).provinceId());
		assertEquals("fort_a", war.getCampaignBattleSchedule().get(0).fortInstallationId());
		assertEquals(20, war.getCampaignBattleSchedule().get(1).provinceId());
	}

	@Test
	void ensureReSiegeInsert_addsSiegeWhenEnemyFortOnPath() {
		War war = warWithSchedule(field(30));
		war.setCampaignCounterSchedule(List.of(field(5)));
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);

		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", attacker, 10, 100L)));

		CampaignScheduleService.ensureReSiegeInsert(war, index);

		assertEquals(1, war.getCampaignBattleSchedule().size());
		assertEquals(CampaignBattleKind.SIEGE, war.getCampaignCounterSchedule().get(0).kind());
		assertEquals(10, war.getCampaignCounterSchedule().get(0).provinceId());
	}

	@Test
	void ensureReSiegeInsert_skipsWhenCurrentSlotAlreadySiegeForFort() {
		War war = warWithSchedule(field(30));
		war.setCampaignCounterSchedule(List.of(siege(10, "fort_a"), field(5)));
		war.putFortController("fort_a", CampaignCoalition.AGGRESSOR);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);

		FortZocIndex index = FortZocIndex.fromForts(List.of(
				new OperationalFort("fort_a", attacker, 10, 100L)));

		CampaignScheduleService.ensureReSiegeInsert(war, index);

		assertEquals(2, war.getCampaignCounterSchedule().size());
		assertEquals(CampaignBattleKind.SIEGE, war.getCampaignCounterSchedule().get(0).kind());
	}

	@Test
	void currentSlot_counterPush_usesCounterSchedule() {
		War war = warWithSchedule(field(20), field(30));
		war.setCampaignCounterSchedule(List.of(field(10), field(5)));
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		war.setCampaignCounterScheduleIndex(0);

		assertEquals(10, CampaignScheduleService.currentSlot(war).orElseThrow().provinceId());
	}

	@Test
	void advanceIndex_counterPush_incrementsCounterIndexOnly() {
		War war = warWithSchedule(field(20), field(30));
		war.setCampaignCounterSchedule(List.of(field(10), field(5)));
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);

		CampaignScheduleService.advanceIndex(war);

		assertEquals(0, war.getCampaignScheduleIndex());
		assertEquals(1, war.getCampaignCounterScheduleIndex());
	}

	@Test
	void switchPushTarget_preservesBothIndices() {
		War war = warWithSchedule(field(20), field(30));
		war.setCampaignCounterSchedule(List.of(field(10), field(5)));

		CampaignScheduleService.advanceIndex(war);
		assertEquals(1, war.getCampaignScheduleIndex());
		assertEquals(0, war.getCampaignCounterScheduleIndex());

		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		CampaignScheduleService.advanceIndex(war);
		assertEquals(1, war.getCampaignScheduleIndex());
		assertEquals(1, war.getCampaignCounterScheduleIndex());

		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		assertEquals(30, CampaignScheduleService.currentSlot(war).orElseThrow().provinceId());
	}

	@Test
	void hasSchedule_falseWhenEmpty() {
		War war = new War(1, attacker, defender);
		assertFalse(CampaignScheduleService.hasSchedule(war));
	}

	@Test
	void slotForProvince_returnsUpcomingSlotFromScheduleIndex() {
		War war = warWithSchedule(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "fort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null));
		war.setCampaignProvinces(List.of(452, 795, 705));

		assertEquals(CampaignBattleKind.NAVAL,
				CampaignScheduleService.slotForProvince(war, 795).orElseThrow().kind());
		assertEquals(CampaignBattleKind.SIEGE,
				CampaignScheduleService.slotForProvince(war, 705).orElseThrow().kind());

		CampaignScheduleService.advanceIndex(war);
		assertEquals(CampaignBattleKind.SIEGE,
				CampaignScheduleService.slotForProvince(war, 705).orElseThrow().kind());
	}

	@Test
	void firstOnAxisScheduleProvince_returnsFirstScheduledAxisTile() {
		War war = warWithSchedule(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "fort"));
		war.setCampaignProvinces(List.of(452, 795, 705));

		assertEquals(795, CampaignScheduleService.firstOnAxisScheduleProvince(war).orElseThrow());
	}

	@Test
	void slotForProvince_returnsMatchingSlot() {
		War war = warWithSchedule(field(20), siege(18, "fort_a"));
		assertEquals(20, CampaignScheduleService.slotForProvince(war, 20).orElseThrow().provinceId());
		assertEquals("fort_a", CampaignScheduleService.slotForProvince(war, 18).orElseThrow().fortInstallationId());
		assertTrue(CampaignScheduleService.slotForProvince(war, 99).isEmpty());
	}

	private War warWithSchedule(ScheduledCampaignBattle... slots) {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setCampaignBattleSchedule(List.of(slots));
		war.setCampaignScheduleIndex(0);
		return war;
	}

	private static ScheduledCampaignBattle field(int provinceId) {
		return new ScheduledCampaignBattle(provinceId, CampaignBattleKind.FIELD, false, null);
	}

	private static ScheduledCampaignBattle siege(int provinceId, String fortId) {
		return new ScheduledCampaignBattle(provinceId, CampaignBattleKind.SIEGE, false, fortId);
	}

	private Province province(int id) {
		return new Province(id, Terrain.PLAINS.name(), 50, id * 10, id * 10);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}
}
