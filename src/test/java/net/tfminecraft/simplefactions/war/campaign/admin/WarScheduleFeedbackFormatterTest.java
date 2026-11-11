package net.tfminecraft.simplefactions.war.campaign.admin;


import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
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

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;

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
	void lockday_showsNavalScheduleSlots() {
		War war = votingWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(12, CampaignBattleKind.NAVAL, false, null, "port_a"),
				new ScheduledCampaignBattle(14, CampaignBattleKind.NAVAL_INVASION, false, null)));
		war.setCampaignScheduleIndex(0);

		List<String> lines = WarScheduleFeedbackFormatter.format("lockday", war);
		String combined = stripColorCodes(String.join("\n", lines));
		assertTrue(combined.contains("province 12"));
		assertTrue(combined.contains("kind naval"));
		assertTrue(combined.contains("port port_a"));
		assertTrue(combined.contains("province 14"));
		assertTrue(combined.contains("kind naval_invasion"));
		assertNoJsonBraces(lines);
	}

	@Test
	void lockday_showsBothLegSchedules() {
		War war = votingWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignScheduleIndex(0);
		war.setPushTarget(net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);

		List<String> lines = WarScheduleFeedbackFormatter.format("lockday", war);
		String combined = stripColorCodes(String.join("\n", lines));
		assertTrue(combined.contains("Invasion schedule"));
		assertTrue(combined.contains("Counter schedule"));
		assertTrue(combined.contains("province 10"));
		assertTrue(combined.contains("(current)"));
	}

	@Test
	void lockday_showsScheduleSlots() {
		War war = votingWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(1);

		List<String> lines = WarScheduleFeedbackFormatter.format("lockday", war);
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
	void lockday_showsPhase() {
		War war = votingWar();
		List<String> lines = WarScheduleFeedbackFormatter.format("lockday", war);
		String combined = String.join(" ", lines);
		assertTrue(combined.contains("voting"));
		assertTrue(combined.contains("2026-08-21"));
		assertNoJsonBraces(lines);
	}

	@Test
	void battleDayTools_listTheLockedBattleDaysWithTheirTimes() {
		War war = votingWar();
		war.getAvailability().putBattleDay(LockedBattleDay.open(LocalDate.of(2026, 8, 21), false).withHour(21, false));
		war.getAvailability().putBattleDay(LockedBattleDay.open(LocalDate.of(2026, 8, 22), false));
		for (String tool : List.of("skipday", "unlockday", "sethour")) {
			String combined = String.join(" ", WarScheduleFeedbackFormatter.format(tool, war));
			assertTrue(combined.contains("Battle days: §e2026-08-21 21:00 CET, 2026-08-22"), combined);
		}
		war.getAvailability().clearBattleDays();
		assertTrue(String.join(" ", WarScheduleFeedbackFormatter.format("lockday", war)).contains("Battle days: §e-"));
		assertTrue(WarScheduleFeedbackFormatter.format("availability", war).isEmpty());
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
		war.getAvailability().putBattleDay(LockedBattleDay.open(BATTLE_DAY, false));
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
