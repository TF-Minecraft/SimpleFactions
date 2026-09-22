package me.Plugins.SimpleFactions.War.battle.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;

class CampaignBattleRosterServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private Participant attackerPar;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		WarDevMode.resetForTests();
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Carol");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		mockMilitary(attacker);
		mockMilitary(defender);
		attackerPar = new Participant(attacker);
	}

	@Test
	void enrollWarbands_createsEmptyShellOnBattleSide() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(20);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.WarManager> wars =
						mockStatic(me.Plugins.SimpleFactions.Managers.WarManager.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
						mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class);
				MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
						mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			mockBossBar(bukkit);
			Battle battle = createBattle(1);
			wars.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.getById(1)).thenReturn(war);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("atk"))
					.thenReturn(attacker);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("def"))
					.thenReturn(defender);
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					org.mockito.ArgumentMatchers.eq(war),
					org.mockito.ArgumentMatchers.eq(20),
					org.mockito.ArgumentMatchers.any())).thenReturn(5);

			CampaignBattleRosterService.enrollWarbands(war, battle);

			Warband shell = WarbandManager.getByString(
					BattleNamingService.campaignWarbandId(battle.getDisplayName(), BattleTemplate.ATTACKER_SIDE));
			assertTrue(shell != null);
			assertEquals(0, shell.getMemberCount());
			assertTrue(shell.isPendingLeader());
			assertEquals(1, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().size());
			assertEquals(1, battle.getSideById(BattleTemplate.DEFENDER_SIDE).getBands().size());
			assertEquals(2, WarbandManager.get().size());
		}
	}

	@Test
	void ensureEnrolled_deferredUntilSignupOpens() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(20);
		war.setBattleDay(BATTLE_DAY);
		Instant beforeSignup = BattleWindowService.atScheduleHour(BATTLE_DAY, 10);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.WarManager> wars =
						mockStatic(me.Plugins.SimpleFactions.Managers.WarManager.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
						mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class);
				MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
						mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			mockBossBar(bukkit);
			Battle battle = createBattle(1);
			wars.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.getById(1)).thenReturn(war);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("atk"))
					.thenReturn(attacker);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("def"))
					.thenReturn(defender);
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					org.mockito.ArgumentMatchers.eq(war),
					org.mockito.ArgumentMatchers.eq(20),
					org.mockito.ArgumentMatchers.any())).thenReturn(5);

			CampaignBattleRosterService.ensureEnrolledAt(war, battle, beforeSignup, false, false);

			assertTrue(WarbandManager.get().isEmpty());
			assertEquals(0, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().size());
		}
	}

	@Test
	void tryEnrollWhenSignupOpens_createsShellsAtSignupHour() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(20);
		war.setBattleDay(BATTLE_DAY);
		Instant signupHour = BattleWindowService.atScheduleHour(BATTLE_DAY, 20);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.WarManager> wars =
						mockStatic(me.Plugins.SimpleFactions.Managers.WarManager.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
						mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class);
				MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
						mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			mockBossBar(bukkit);
			Battle battle = createBattle(1);
			wars.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.getById(1)).thenReturn(war);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("atk"))
					.thenReturn(attacker);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("def"))
					.thenReturn(defender);
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					org.mockito.ArgumentMatchers.eq(war),
					org.mockito.ArgumentMatchers.eq(20),
					org.mockito.ArgumentMatchers.any())).thenReturn(5);

			CampaignBattleRosterService.tryEnrollWhenSignupOpens(war, signupHour);

			assertEquals(2, WarbandManager.get().size());
			assertEquals(1, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().size());
		}
	}

	@Test
	void ensureEnrolled_fillsMissingSideShells() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(20);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.WarManager> wars =
						mockStatic(me.Plugins.SimpleFactions.Managers.WarManager.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
						mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class);
				MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
						mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			mockBossBar(bukkit);
			Battle battle = createBattle(1);
			wars.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.getById(1)).thenReturn(war);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("atk"))
					.thenReturn(attacker);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("def"))
					.thenReturn(defender);
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					org.mockito.ArgumentMatchers.eq(war),
					org.mockito.ArgumentMatchers.eq(20),
					org.mockito.ArgumentMatchers.any())).thenReturn(5);

			CampaignBattleRosterService.ensureEnrolledForced(war, battle);

			assertEquals(1, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().size());
			assertEquals(1, battle.getSideById(BattleTemplate.DEFENDER_SIDE).getBands().size());
		}
	}

	@Test
	void enrollWarbands_idempotentWhenBandAlreadyOnSide() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(20);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.WarManager> wars =
						mockStatic(me.Plugins.SimpleFactions.Managers.WarManager.class);
				MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
						mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class);
				MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
						mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			mockBossBar(bukkit);
			Battle battle = createBattle(1);
			wars.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.getById(1)).thenReturn(war);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("atk"))
					.thenReturn(attacker);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("def"))
					.thenReturn(defender);
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					org.mockito.ArgumentMatchers.eq(war),
					org.mockito.ArgumentMatchers.eq(20),
					org.mockito.ArgumentMatchers.any())).thenReturn(5);

			CampaignBattleRosterService.enrollWarbands(war, battle);
			CampaignBattleRosterService.enrollWarbands(war, battle);

			assertEquals(1, battle.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().size());
			assertEquals(2, WarbandManager.get().size());
		}
	}

	private Battle createBattle(int warId) {
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w" + warId);
		battle.setWarId(warId);
		battle.setProvinceId(20);
		battle.setDisplayName("Battle of Lanbury");
		battle.setLocked(false);
		BattleManager.addBattle(battle);
		return battle;
	}

	private void mockBossBar(MockedStatic<Bukkit> bukkit) {
		BossBar bossBar = mock(BossBar.class);
		bukkit.when(() -> Bukkit.createBossBar(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(BarColor.class),
				org.mockito.ArgumentMatchers.any(BarStyle.class))).thenReturn(bossBar);
		bukkit.when(() -> Bukkit.createBossBar(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(BarColor.class),
				org.mockito.ArgumentMatchers.any(BarStyle.class),
				org.mockito.ArgumentMatchers.any())).thenReturn(bossBar);
	}

	private void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(levy.getEntries()).thenReturn(List.of());
		when(faction.getMilitary()).thenReturn(military);
	}
}
