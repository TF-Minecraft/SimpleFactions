package me.Plugins.SimpleFactions.War.campaign.admin;


import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

class WarScheduleFeedbackFormatterTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of("Alice", "Bob"));
		when(defender.getMembers()).thenReturn(List.of("Carol", "Dave"));
	}

	@Test
	void opencvote_showsNavalScheduleSlots() {
		War war = votingWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(12, CampaignBattleKind.NAVAL, false, null, "port_a"),
				new ScheduledCampaignBattle(14, CampaignBattleKind.NAVAL_INVASION, false, null)));
		war.setCampaignScheduleIndex(0);

		List<String> lines = WarScheduleFeedbackFormatter.format("opencvote", war);
		String combined = stripColorCodes(String.join("\n", lines));
		assertTrue(combined.contains("province 12"));
		assertTrue(combined.contains("kind naval"));
		assertTrue(combined.contains("port port_a"));
		assertTrue(combined.contains("province 14"));
		assertTrue(combined.contains("kind naval_invasion"));
		assertNoJsonBraces(lines);
	}

	@Test
	void opencvote_showsBothLegSchedules() {
		War war = votingWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignScheduleIndex(0);
		war.setPushTarget(me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);

		List<String> lines = WarScheduleFeedbackFormatter.format("opencvote", war);
		String combined = stripColorCodes(String.join("\n", lines));
		assertTrue(combined.contains("Invasion schedule"));
		assertTrue(combined.contains("Counter schedule"));
		assertTrue(combined.contains("province 10"));
		assertTrue(combined.contains("(current)"));
	}

	@Test
	void opencvote_showsScheduleSlots() {
		War war = votingWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(1);

		List<String> lines = WarScheduleFeedbackFormatter.format("opencvote", war);
		String combined = stripColorCodes(String.join("\n", lines));
		assertTrue(combined.contains("province 20"));
		assertTrue(combined.contains("kind field"));
		assertTrue(combined.contains("province 18"));
		assertTrue(combined.contains("kind siege"));
		assertTrue(combined.contains("fort fort_a"));
		assertTrue(combined.contains("(current)"));
		assertNoJsonBraces(lines);
	}

	@Test
	void opencvote_showsPhase() {
		War war = votingWar();
		List<String> lines = WarScheduleFeedbackFormatter.format("opencvote", war);
		String combined = String.join(" ", lines);
		assertTrue(combined.contains("voting"));
		assertTrue(combined.contains("2026-08-21"));
		assertNoJsonBraces(lines);
	}

	@Test
	void closevote_scheduled_showsProvinceAndInstant() {
		War war = scheduledWar();
		List<String> lines = WarScheduleFeedbackFormatter.format("closevote", war);
		String combined = String.join(" ", lines);
		assertTrue(combined.contains("2026-08-21T21:00:00Z"));
		assertTrue(combined.contains("20"));
		assertTrue(combined.contains("scheduled"));
		assertNoJsonBraces(lines);
	}

	@Test
	void castvote_showsVoterCount() {
		War war = votingWar();
		Map<UUID, Set<Integer>> votes = new HashMap<>();
		votes.put(UUID.randomUUID(), new HashSet<>(Set.of(21)));
		votes.put(UUID.randomUUID(), new HashSet<>(Set.of(21, 22)));
		war.setBattleVotes(votes);

		List<String> lines = WarScheduleFeedbackFormatter.format("castvote", war, 21);
		String combined = String.join(" ", lines);
		assertTrue(combined.contains("21"));
		assertTrue(combined.contains("2"));
		assertTrue(combined.contains("voting"));
		assertNoJsonBraces(lines);
	}

	@Test
	void setscheduled_showsPhaseAndProvince() {
		War war = scheduledWar();
		List<String> lines = WarScheduleFeedbackFormatter.format("setscheduled", war);
		String combined = String.join(" ", lines);
		assertTrue(combined.contains("scheduled"));
		assertTrue(combined.contains("20"));
		assertTrue(combined.contains("2026-08-21T21:00:00Z"));
		assertNoJsonBraces(lines);
	}

	@Test
	void winbattle_showsPostBattleChoicePending() {
		War war = votingWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);
		List<String> lines = WarScheduleFeedbackFormatter.format("winbattle", war);
		assertTrue(String.join(" ", lines).contains("Post-battle choice pending"));
	}

	@Test
	void appendCampaignBattleLine_whenBattleExists() {
		War war = scheduledWar();
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w1_p20");
			battle.setWarId(war.getId());
			BattleManager.addBattle(battle);

			List<String> lines = WarScheduleFeedbackFormatter.format("setscheduled", war);
			assertTrue(lines.stream().anyMatch(line -> line.contains("campaign_w1_p20")));
		});
	}

	private War votingWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}

	private War scheduledWar() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(Instant.parse("2026-08-21T21:00:00Z"));
		war.setScheduledBattleHour(21);
		war.setScheduledBattleProvinceId(20);
		return war;
	}

	private static void assertNoJsonBraces(List<String> lines) {
		String combined = lines.stream().collect(Collectors.joining("\n"));
		assertFalse(combined.contains("{"), "Expected no JSON braces in: " + combined);
	}

	private static String stripColorCodes(String text) {
		return text == null ? "" : text.replaceAll("§.", "");
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			action.run();
		}
	}
}
