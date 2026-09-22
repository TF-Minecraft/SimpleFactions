package me.Plugins.SimpleFactions.War.battle.campaign;


import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandLeaveBlock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleJoinService;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService;
import me.Plugins.SimpleFactions.War.battle.military.BattlePoolService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandMembershipService;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandRejoinState;

class CampaignBattleJoinServiceTest {
	private static final int PROVINCE_ID = 20;
	private Faction attacker;
	private Faction defender;
	private UUID leaderId;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		WarbandMembershipService.resetForTests();
		CampaignWarbandLeaveBlock.resetForTests();
		WarDevMode.resetForTests();
		Cache.warBattleLivesPerRegiment = 5;
		Cache.warBattleMinSideLives = 1;
		leaderId = UUID.randomUUID();

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
	}

	@Test
	void wrongSide_rejected() {
		War war = new War(1, attacker, defender);
		Battle battle = campaignBattle(1);
		Warband warband = campaignSideWarband(war, BattleTemplate.ATTACKER_SIDE);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByMember("Carol")).thenReturn(defender);

			String error = CampaignBattleJoinService.validateWarbandMemberJoin(
					war, battle, BattleTemplate.ATTACKER_SIDE, warband, "Carol", UUID.randomUUID());

			assertEquals("Your faction is not on this battle side", error);
		}
	}

	@Test
	void rosterCap_enforced() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		Battle battle = campaignBattle(1);
		Warband warband = campaignSideWarband(war, BattleTemplate.ATTACKER_SIDE);
		warband.addMember(UUID.randomUUID());

		try (MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(0);

			String error = CampaignBattleJoinService.validateJoin(
					war, battle, warband, BattleTemplate.ATTACKER_SIDE);

			assertEquals("Cannot join: side roster is full (max 0 players for this battle)", error);
		}
	}

	@Test
	void phantoms_countTowardCap_devmode() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		Battle battle = campaignBattle(1);
		Warband warband = campaignSideWarband(war, BattleTemplate.ATTACKER_SIDE);
		WarDevMode.seedDummyMembers(warband, 5);

		try (MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(0);

			String error = CampaignBattleJoinService.validateJoin(
					war, battle, warband, BattleTemplate.ATTACKER_SIDE);

			assertEquals("Cannot join: side roster is full (max 0 players for this battle)", error);
		}
	}

	@Test
	void previewSideRosterCap_usesPoolLives() {
		War war = new War(1, attacker, defender);
		Battle battle = campaignBattle(1);
		Warband warband = campaignSideWarband(war, BattleTemplate.ATTACKER_SIDE);
		WarDevMode.seedDummyMembers(warband, 10);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(warband);

		try (MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(10);

			int cap = CampaignBattleJoinService.previewSidePoolLives(
					war, battle, BattleTemplate.ATTACKER_SIDE);

			assertEquals(50, cap);
		}
	}

	@Test
	void manualBattle_noSideCheck() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "manual");
			battle.setLocked(false);
			Warband warband = Warband.createWithMemberIds("manual_band", leaderId, false);

			assertNull(BattleJoinService.join(warband, battle, BattleTemplate.ATTACKER_SIDE));
		}
	}

	@Test
	void campaignRejoin_allowedWhenRosterHasRoom() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		Battle battle = campaignBattle(1);
		Warband warband = campaignSideWarband(war, BattleTemplate.ATTACKER_SIDE);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(warband);
		BattleManager.addBattle(battle);
		WarbandManager.addWarband(warband);

		WarbandRejoinState state = new WarbandRejoinState(warband.getId(), attacker);
		WarbandMembershipService service = WarbandMembershipService.getInstance();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getByString("atk")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);
			bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);

			assertTrue(service.evaluateRejoin(warband, leaderId, attacker, state));
		}
	}

	@Test
	void midBattleJoin_blockedWhenNoLives() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		Battle battle = campaignBattle(1);
		Warband warband = campaignSideWarband(war, BattleTemplate.ATTACKER_SIDE);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(warband);
		BattleManager.addBattle(battle);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getDefenders())))
					.thenReturn(5);

			battle.start();
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(0);

			String error = CampaignBattleJoinService.validateWarbandMemberJoin(
					war, battle, BattleTemplate.ATTACKER_SIDE, warband, "Alice", UUID.randomUUID());

			assertEquals("Cannot join: this side has no lives remaining in the battle", error);
		}
	}

	@Test
	void validateJoin_successOnCorrectSide() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		Battle battle = campaignBattle(1);
		Warband warband = campaignSideWarband(war, BattleTemplate.ATTACKER_SIDE);

		try (MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);

			assertNull(CampaignBattleJoinService.validateJoin(
					war, battle, warband, BattleTemplate.ATTACKER_SIDE));
		}
	}

	@Test
	void leaveBlock_recordsAndChecksPlayer() {
		CampaignWarbandLeaveBlock.resetForTests();
		UUID playerId = UUID.randomUUID();
		assertFalse(CampaignWarbandLeaveBlock.isBlocked("battle1", "warband1", playerId));

		CampaignWarbandLeaveBlock.block("battle1", "warband1", playerId);

		assertTrue(CampaignWarbandLeaveBlock.isBlocked("battle1", "warband1", playerId));
		assertFalse(CampaignWarbandLeaveBlock.isBlocked("battle1", "warband2", playerId));
	}

	private Battle campaignBattle(int warId) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w" + warId);
			battle.setWarId(warId);
			battle.setProvinceId(PROVINCE_ID);
			battle.setLocked(false);
			return battle;
		}
	}

	private Warband campaignSideWarband(War war, String sideId) {
		if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(sideId)) {
			return Warband.createCampaignSideShell(war, war.getAttackers(), sideId);
		}
		return Warband.createCampaignSideShell(war, war.getDefenders(), sideId);
	}
}
