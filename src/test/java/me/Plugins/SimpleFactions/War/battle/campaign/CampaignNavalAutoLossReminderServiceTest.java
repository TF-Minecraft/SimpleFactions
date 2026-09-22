package me.Plugins.SimpleFactions.War.battle.campaign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.campaign.progression.AttackerNavalContestService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiCopy;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;

class CampaignNavalAutoLossReminderServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		Cache.warBattleWindowStartHour = 20;
		Cache.warBattleWindowEndHour = 24;
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Bob");
		when(attacker.getMembers()).thenReturn(List.of("Alice"));
		when(defender.getMembers()).thenReturn(List.of("Bob"));
	}

	@AfterEach
	void tearDown() {
		BattleManager.resetForTests();
	}

	@Test
	void processReminders_pingsAttackerLeaderWhenWouldAutoLose() {
		War war = navalVotingWar();
		Player alice = mock(Player.class);
		when(alice.isOnline()).thenReturn(true);
		Instant now = BattleWindowService.atScheduleHour(BATTLE_DAY, 14);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<AttackerNavalContestService> contest =
						mockStatic(AttackerNavalContestService.class)) {
			contest.when(() -> AttackerNavalContestService.wouldAttackerAutoLoseNaval(war)).thenReturn(true);
			bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(alice);

			CampaignNavalAutoLossReminderService.processReminders(war, now);

			verify(alice).sendMessage(CampaignUiCopy.navalAutoLossLeaderPing());
		}
	}

	@Test
	void processReminders_skipsWhenAttackerHasBerthedNavy() {
		War war = navalVotingWar();
		Player alice = mock(Player.class);
		when(alice.isOnline()).thenReturn(true);
		Instant now = BattleWindowService.atScheduleHour(BATTLE_DAY, 14);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<AttackerNavalContestService> contest =
						mockStatic(AttackerNavalContestService.class)) {
			contest.when(() -> AttackerNavalContestService.wouldAttackerAutoLoseNaval(war)).thenReturn(false);
			bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(alice);

			CampaignNavalAutoLossReminderService.processReminders(war, now);

			verify(alice, never()).sendMessage(anyString());
		}
	}

	private War navalVotingWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null)));
		war.setCampaignScheduleIndex(0);
		return war;
	}
}
