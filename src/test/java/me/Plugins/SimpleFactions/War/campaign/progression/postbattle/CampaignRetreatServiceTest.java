package me.Plugins.SimpleFactions.War.campaign.progression.postbattle;




import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignRetreatService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignRetreatService.ConcedeResult;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignRetreatService.RetreatResult;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.enums.Terrain;

class CampaignRetreatServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private MockedStatic<WarManager> warManagerMock;
	private MockedStatic<TitleManager> titleManagerMock;
	private SimpleFactions pluginBackup;
	private Instant beforeVoteClose;

	@BeforeEach
	void setUp() {
		CampaignClock.reset();
		Cache.warVoteCloseHour = 16;
		Cache.warFirstBattleAtBorder = true;
		beforeVoteClose = BattleWindowService.atScheduleHour(BATTLE_DAY, 12);

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		ProvinceManager pm = new ProvinceManager();
		Province battleProvince = new Province(20, Terrain.PLAINS.name(), 50, 200, 200);
		Province nextProvince = new Province(30, Terrain.PLAINS.name(), 50, 300, 300);
		pm.start(Map.of(20, battleProvince, 30, nextProvince));

		pluginBackup = SimpleFactions.plugin;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getProvinceManager()).thenReturn(pm);
		SimpleFactions.plugin = plugin;

		titleManagerMock = mockStatic(TitleManager.class);
		titleManagerMock.when(() -> TitleManager.getByProvince(20)).thenReturn(defender);
		titleManagerMock.when(() -> TitleManager.getByProvince(30)).thenReturn(defender);

		warManagerMock = mockStatic(WarManager.class);
		warManagerMock.when(() -> WarManager.persist(any())).then(inv -> null);
	}

	@AfterEach
	void tearDown() {
		warManagerMock.close();
		titleManagerMock.close();
		SimpleFactions.plugin = pluginBackup;
		CampaignClock.reset();
	}

	@Test
	void pushedCoalition_towardObjective() {
		War war = baseWar();
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);

		assertEquals(CampaignCoalition.DEFENDER, CampaignRetreatService.pushedCoalition(war));
	}

	@Test
	void pushedCoalition_towardAggressorCapital() {
		War war = baseWar();
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);

		assertEquals(CampaignCoalition.AGGRESSOR, CampaignRetreatService.pushedCoalition(war));
	}

	@Test
	void pushedCoalition_retakeObjective() {
		War war = baseWar();
		war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);

		assertEquals(null, CampaignRetreatService.pushedCoalition(war));
	}

	@Test
	void slotKey_formatsLegAndIndex() {
		assertEquals("invasion:0", CampaignRetreatService.slotKey(ScheduleLeg.INVASION, 0));
		assertEquals("counter:1", CampaignRetreatService.slotKey(ScheduleLeg.COUNTER, 1));
	}

	@Test
	void canRetreat_falseWhenNotVoting() {
		War war = retreatableWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);

		assertFalse(CampaignRetreatService.canRetreat(war, defender, beforeVoteClose));
		assertEquals(
				RetreatResult.REJECTED_NOT_ELIGIBLE,
				CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose).result());
	}

	@Test
	void canRetreat_falseOnRetakePhase() {
		War war = retreatableWar();
		war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);

		assertFalse(CampaignRetreatService.canRetreat(war, defender, beforeVoteClose));
		assertEquals(
				RetreatResult.REJECTED_NOT_ELIGIBLE,
				CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose).result());
	}

	@Test
	void canRetreat_falseWhenPostBattleChoicePending() {
		War war = retreatableWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);

		assertFalse(CampaignRetreatService.canRetreat(war, defender, beforeVoteClose));
		assertEquals(
				RetreatResult.REJECTED_POST_BATTLE_CHOICE,
				CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose).result());
	}

	@Test
	void canRetreat_falseAfterVoteClose() {
		War war = retreatableWar();
		Instant atClose = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);

		assertFalse(CampaignRetreatService.canRetreat(war, defender, atClose));
		assertEquals(
				RetreatResult.REJECTED_VOTE_CLOSED,
				CampaignRetreatService.concedeActiveSlot(war, defender, atClose).result());
	}

	@Test
	void canRetreat_falseWhenAttackerOnInvasionPush() {
		War war = retreatableWar();

		assertFalse(CampaignRetreatService.canRetreat(war, attacker, beforeVoteClose));
		assertEquals(
				RetreatResult.REJECTED_NOT_LEADER,
				CampaignRetreatService.concedeActiveSlot(war, attacker, beforeVoteClose).result());
	}

	@Test
	void canRetreat_trueForDefenderBeforeVoteClose() {
		War war = retreatableWar();

		assertTrue(CampaignRetreatService.canRetreat(war, defender, beforeVoteClose));
	}

	@Test
	void concedeActiveSlot_advancesScheduleIndexAndCursor() {
		War war = retreatableWar();
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		ConcedeResult result = CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose);

		assertEquals(RetreatResult.SUCCESS, result.result());
		assertEquals(1, war.getCampaignScheduleIndex());
		assertEquals(3, war.getCursorIndex());
	}

	@Test
	void concedeActiveSlot_doesNotIncrementBattlesFought() {
		War war = retreatableWar();
		war.setCampaignBattlesFought(2);
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose);

		assertEquals(2, war.getCampaignBattlesFought());
	}

	@Test
	void concedeActiveSlot_doesNotSpendInitiative() {
		War war = retreatableWar();
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(3);
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose);

		assertEquals(4, war.getInitiativeAttacker());
		assertEquals(3, war.getInitiativeDefender());
	}

	@Test
	void concedeActiveSlot_staysVotingAndKeepsVotes() {
		War war = retreatableWar();
		UUID voter = UUID.randomUUID();
		war.getBattleVotes().put(voter, new HashSet<>(Set.of(21)));
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose);

		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertEquals(Set.of(21), war.getBattleVotes().get(voter));
	}

	@Test
	void concedeActiveSlot_recordsConcededSlotKey() {
		War war = retreatableWar();
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose);

		assertTrue(CampaignRetreatService.isSlotConceded(war, ScheduleLeg.INVASION, 0));
	}

	@Test
	void concedeActiveSlot_noPostBattleChoice() {
		War war = retreatableWar();
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose);

		assertFalse(CampaignPostBattleChoiceService.needsAnyChoice(war));
	}

	@Test
	void concedeActiveSlot_siegeFlipsFortAndAdvances() {
		War war = retreatableWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.putFortController("fort_a", CampaignCoalition.DEFENDER);
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose);

		assertEquals(CampaignCoalition.AGGRESSOR, war.getFortControllers().get("fort_a"));
		assertEquals(1, war.getCampaignScheduleIndex());
		assertEquals(0, war.getCampaignBattlesFought());
	}

	@Test
	void concedeActiveSlot_counterPush_advancesCounterIndexOnly() {
		War war = retreatableWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(0);
		war.setCampaignCounterScheduleIndex(0);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		assertFalse(CampaignRetreatService.canRetreat(war, defender, beforeVoteClose));

		ConcedeResult result = CampaignRetreatService.concedeActiveSlot(war, attacker, beforeVoteClose);

		assertEquals(RetreatResult.SUCCESS, result.result());
		assertEquals(0, war.getCampaignScheduleIndex());
		assertEquals(1, war.getCampaignCounterScheduleIndex());
	}

	@Test
	void concedeActiveSlot_twiceInSameVotingWindow() {
		War war = retreatableWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, false, null)));
		UUID voter = UUID.randomUUID();
		war.getBattleVotes().put(voter, new HashSet<>(Set.of(21)));
		warManagerMock.when(() -> WarManager.getById(1)).thenReturn(war);

		assertEquals(
				RetreatResult.SUCCESS,
				CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose).result());
		assertEquals(
				RetreatResult.SUCCESS,
				CampaignRetreatService.concedeActiveSlot(war, defender, beforeVoteClose).result());

		assertTrue(CampaignRetreatService.isSlotConceded(war, ScheduleLeg.INVASION, 0));
		assertTrue(CampaignRetreatService.isSlotConceded(war, ScheduleLeg.INVASION, 1));
		assertEquals(2, war.getCampaignScheduleIndex());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertEquals(Set.of(21), war.getBattleVotes().get(voter));
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolder(BelligerentRole.ATTACKER);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		return war;
	}

	private War retreatableWar() {
		War war = baseWar();
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignScheduleIndex(0);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		return war;
	}
}
