package me.Plugins.SimpleFactions.War.campaign.raid.fight;



import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidMusterReminderService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class CampaignRaidMusterReminderServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		Cache.campaignRaidMusterReminderSecondsBefore = List.of(45, 15);
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Bob");
		when(attacker.getMembers()).thenReturn(List.of("Alice"));
		when(defender.getMembers()).thenReturn(List.of("Bob"));
	}

	@Test
	void catchUpFiresOnlyMostUrgentDueReminder() {
		Cache.campaignRaidMusterReminderSecondsBefore = List.of(45, 30, 15, 10);
		War war = warWithMusterRaid();
		CampaignRaid raid = war.getActiveCampaignRaid();
		Instant now = raid.getMusterEndsAt().minusSeconds(8);

		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(player);
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);

			CampaignRaidMusterReminderService.processReminders(war, now);

			verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("starts in"));
			assertEquals(1, raid.getMusterRemindersSent().size());
			assertTrue(raid.getMusterRemindersSent().contains(10));
		}
	}

	@Test
	void firesReminderAtConfiguredOffset() {
		War war = warWithMusterRaid();
		CampaignRaid raid = war.getActiveCampaignRaid();
		Instant now = raid.getMusterEndsAt().minusSeconds(45);

		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(player);
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);

			CampaignRaidMusterReminderService.processReminders(war, now);

			verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("starts in"));
			assertTrue(raid.getMusterRemindersSent().contains(45));
			warManager.verify(() -> WarManager.persist(war));
		}
	}


	private War warWithMusterRaid() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		war.setBattleDay(battleDay);

		CampaignRaid raid = new CampaignRaid();
		raid.setId("harbor_raid");
		raid.setDisplayName("Harbor Raid");
		raid.setWarId(1);
		raid.setBattleDay(battleDay);
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		raid.setLauncherFactionId("atk");
		raid.setSourceInstallationId("src");
		raid.setTargetInstallationId("tgt");
		raid.setState(CampaignRaidState.MUSTER);
		raid.setMusterEndsAt(BattleWindowService.atScheduleHour(battleDay, 19).plusSeconds(60));
		war.setActiveCampaignRaid(raid);
		return war;
	}
}
