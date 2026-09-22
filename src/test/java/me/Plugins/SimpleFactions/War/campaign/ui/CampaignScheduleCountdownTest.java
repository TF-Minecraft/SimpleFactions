package me.Plugins.SimpleFactions.War.campaign.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;

class CampaignScheduleCountdownTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		Cache.warVoteCloseHour = 16;
	}

	@Test
	void scheduled_showsStartsIn() {
		War war = votingWar();
		Instant now = BattleWindowService.atScheduleHour(BATTLE_DAY, 18);
		Instant scheduledAt = now.plusSeconds(92 * 60L);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(scheduledAt);

		Optional<String> text = CampaignScheduleCountdown.formatNextMilestone(war, now);

		assertTrue(text.isPresent());
		assertEquals("Starts in 1h 32m", text.get());
	}

	@Test
	void voting_onBattleDay_showsVoteClosesIn() {
		War war = votingWar();
		Instant now = BattleWindowService.atScheduleHour(BATTLE_DAY, 12);

		Optional<String> text = CampaignScheduleCountdown.formatNextMilestone(war, now);

		assertTrue(text.isPresent());
		assertTrue(text.get().startsWith("Vote closes in "));
	}

	@Test
	void voting_beforeBattleDay_showsBattleDayIn() {
		War war = votingWar();
		Instant now = BATTLE_DAY.minusDays(1).atTime(12, 0)
				.atZone(BattleWindowService.SCHEDULE_ZONE).toInstant();

		Optional<String> text = CampaignScheduleCountdown.formatNextMilestone(war, now);

		assertTrue(text.isPresent());
		assertTrue(text.get().startsWith("Battle day in "));
	}

	@Test
	void scheduled_atFightTime_showsStartingNow() {
		War war = votingWar();
		Instant fightAt = BattleWindowService.atScheduleHour(BATTLE_DAY, 21);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(fightAt);

		Optional<String> text = CampaignScheduleCountdown.formatNextMilestone(war, fightAt);

		assertEquals(Optional.of("Starting now"), text);
	}

	@Test
	void scheduled_overdueSiegeWithoutContest_showsCannotStart() {
		War war = votingWar();
		Instant fightAt = BattleWindowService.atScheduleHour(BATTLE_DAY, 21);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(fightAt);

		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "campaign_w1_p20");
			battle.setWarId(war.getId());
			BattleManager.addBattle(battle);

			Optional<String> text = CampaignScheduleCountdown.formatNextMilestone(war, fightAt.plusSeconds(30));

			assertTrue(text.isPresent());
			assertTrue(text.get().startsWith("Cannot start: "));
			assertTrue(text.get().contains("contest area"));
		});
	}

	@Test
	void voting_afterVoteClose_returnsEmpty() {
		War war = votingWar();
		Instant now = BattleWindowService.atScheduleHour(BATTLE_DAY, 17);

		Optional<String> text = CampaignScheduleCountdown.formatNextMilestone(war, now);

		assertFalse(text.isPresent());
	}

	private War votingWar() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
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
