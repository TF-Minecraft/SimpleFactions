package me.Plugins.SimpleFactions.War.battle.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class CampaignBattleSignupReminderServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		Cache.warBattleWindowStartHour = 16;
		Cache.warBattleWindowEndHour = 24;
		Cache.battleSignupReminderSecondsBefore = List.of(300, 60);
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Bob");
		when(attacker.getMembers()).thenReturn(List.of("Alice"));
		when(defender.getMembers()).thenReturn(List.of("Bob"));
		mockMilitary(attacker);
		mockMilitary(defender);
	}

	@AfterEach
	void tearDown() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
	}

	@Test
	void catchUpFiresOnlyMostUrgentDueReminder() {
		Cache.battleSignupReminderSecondsBefore = List.of(300, 60, 30);
		War war = scheduledWar();
		createFieldBattle("Battle of Test");
		Instant now = war.getScheduledBattleAt().minusSeconds(25);

		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		withMocks(player, null, () -> {
			try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
				warManager.when(() -> WarManager.persist(any())).then(inv -> null);

				CampaignBattleSignupReminderService.processReminders(war, now);

				verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("Battle of Test"));
				assertEquals(1, war.getSignupRemindersSent().size());
				assertTrue(war.getSignupRemindersSent().contains(30));
			}
		});
	}

	@Test
	void firesReminderAtConfiguredOffset() {
		War war = scheduledWar();
		Battle battle = createFieldBattle("Battle of Test");
		Instant now = war.getScheduledBattleAt().minusSeconds(300);

		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		withMocks(player, null, () -> {
			try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
				warManager.when(() -> WarManager.persist(any())).then(inv -> null);

				CampaignBattleSignupReminderService.processReminders(war, now);

				verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("Battle of Test"));
				assertTrue(war.getSignupRemindersSent().contains(300));
				warManager.verify(() -> WarManager.persist(war));
			}
		});
	}

	@Test
	void doesNotRepeatSameOffset() {
		War war = scheduledWar();
		war.getSignupRemindersSent().add(300);
		createFieldBattle("Battle of Test");
		Instant now = war.getScheduledBattleAt().minusSeconds(300);

		Player player = mock(Player.class);

		withMocks(player, null, () -> {
			try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
				CampaignBattleSignupReminderService.processReminders(war, now);

				verify(player, never()).sendMessage(anyString());
				warManager.verify(() -> WarManager.persist(any()), never());
			}
		});
	}

	@Test
	void skipsPlayersAlreadyInWarband() {
		War war = scheduledWar();
		createFieldBattle("Battle of Test");

		UUID memberId = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("wb1", memberId, false, memberId);
		WarbandManager.addWarband(warband);

		Instant now = war.getScheduledBattleAt().minusSeconds(60);

		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getUniqueId()).thenReturn(memberId);

		withMocks(player, null, () -> {
			try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
				warManager.when(() -> WarManager.persist(any())).then(inv -> null);

				CampaignBattleSignupReminderService.processReminders(war, now);

				verify(player, never()).sendMessage(anyString());
				assertEquals(1, war.getSignupRemindersSent().size());
				warManager.verify(() -> WarManager.persist(war));
			}
		});
	}

	private Battle createFieldBattle(String displayName) {
		return withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w1_p20");
			battle.setWarId(1);
			battle.setDisplayName(displayName);
			BattleManager.addBattle(battle);
			return battle;
		});
	}

	private War scheduledWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		LocalDate battleDay = LocalDate.of(2026, 8, 21);
		war.setBattleDay(battleDay);
		war.setScheduledBattleHour(21);
		war.setScheduledBattleAt(BattleWindowService.computeScheduledBattleAt(battleDay, 21));
		war.setScheduledBattleProvinceId(20);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		return war;
	}

	private void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment professional = mock(Regiment.class);
		when(professional.getId()).thenReturn("professional");
		when(professional.isLevy()).thenReturn(false);
		when(professional.isOffensive()).thenReturn(true);
		when(professional.getCurrentSlots()).thenReturn(10);
		when(military.getManpowerNoLevy(any(Boolean.class))).thenReturn(10);
		when(military.getRegiments()).thenReturn(List.of(professional));
		when(faction.getMilitary()).thenReturn(military);
		when(faction.getName()).thenReturn("faction");
	}

	private void withMocks(Player alice, Player bob, Runnable action) {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(alice);
			bukkit.when(() -> Bukkit.getPlayerExact("Bob")).thenReturn(bob);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(mock(BossBar.class));
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(mock(BossBar.class));
			action.run();
		}
	}

	private <T> T withMockBossBar(java.util.function.Supplier<T> supplier) {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(mock(BossBar.class));
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(mock(BossBar.class));
			return supplier.get();
		}
	}
}
