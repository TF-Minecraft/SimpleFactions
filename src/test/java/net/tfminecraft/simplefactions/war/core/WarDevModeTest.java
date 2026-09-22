package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleRosterService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;

class WarDevModeTest {
	private int previousPhantomCount;

	@BeforeEach
	void setUp() {
		previousPhantomCount = Cache.warDevmodePhantomCount;
		Cache.warDevmodePhantomCount = 10;
		Cache.warBattleLivesPerRegiment = 4;
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		WarDevMode.resetForTests();
	}

	@AfterEach
	void tearDown() {
		Cache.warDevmodePhantomCount = previousPhantomCount;
		WarDevMode.resetForTests();
	}

	@Test
	void toggle_notPersisted() {
		WarDevMode.setEnabled(true);
		assertTrue(WarDevMode.isEnabled());

		WarDevMode.resetForTests();

		assertFalse(WarDevMode.isEnabled());
	}

	@Test
	void seedDummyMembers_addsTenWithDisplayNames() {
		UUID leaderId = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("test_band", leaderId, true);

		WarDevMode.seedDummyMembers(warband, 10);

		assertEquals(11, warband.getMemberCount());
		assertEquals(10, warband.getDummyMemberCount());
		UUID firstDummy = WarDevMode.dummyMemberId("test_band", 0);
		assertEquals(WarDevMode.dummyDisplayName("test_band", 0), warband.getMemberDisplayName(firstDummy));
	}

	@Test
	void dummyMemberIds_deterministic() {
		UUID first = WarDevMode.dummyMemberId("alpha", 0);
		UUID second = WarDevMode.dummyMemberId("alpha", 0);

		assertEquals(first, second);
	}

	@Test
	void getPlayers_stillOnlineOnly() {
		UUID leaderId = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("test_band", leaderId, true);

		WarDevMode.seedDummyMembers(warband, 10);

		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(null);
			assertTrue(warband.getPlayers().isEmpty());
		}
	}

	@Test
	void seedDummyMembersIfEnabled_skipsWhenOff() {
		UUID leaderId = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("test_band", leaderId, true);

		WarDevMode.seedDummyMembersIfEnabled(warband);

		assertEquals(1, warband.getMemberCount());
		assertEquals(0, warband.getDummyMemberCount());
	}

	@Test
	void seedDummyMembersIfEnabled_seedsWhenOn() {
		WarDevMode.setEnabled(true);
		UUID leaderId = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("test_band", leaderId, true);

		WarDevMode.seedDummyMembersIfEnabled(warband);

		assertEquals(11, warband.getMemberCount());
		assertEquals(10, warband.getDummyMemberCount());
	}

	@Test
	void seedDummyMembersOnFirstSignupIfEnabled_noOpWhenDummiesAlreadySeeded() {
		WarDevMode.setEnabled(true);
		UUID leaderId = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("test_band", leaderId, true);
		WarDevMode.seedDummyMembers(warband, 3);

		Faction attacker = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		War war = new War(1, attacker, mock(Faction.class));
		Battle battle = mock(Battle.class);
		when(battle.getProvinceId()).thenReturn(20);

		WarDevMode.seedDummyMembersOnFirstSignupIfEnabled(
				warband, war, battle, BattleTemplate.ATTACKER_SIDE);

		assertEquals(3, warband.getDummyMemberCount());
	}

	@Test
	void seedDummyMembersOnFirstSignupIfEnabled_seedsWhenEmpty() {
		WarDevMode.setEnabled(true);
		UUID leaderId = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("test_band", leaderId, true);

		Faction attacker = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		War war = new War(1, attacker, mock(Faction.class));
		Battle battle = mock(Battle.class);
		when(battle.getProvinceId()).thenReturn(null);

		WarDevMode.seedDummyMembersOnFirstSignupIfEnabled(
				warband, war, battle, BattleTemplate.ATTACKER_SIDE);

		assertEquals(10, warband.getDummyMemberCount());
	}

	// Exercise the retained compatibility entry point so legacy behavior stays covered.
	@SuppressWarnings("deprecation")
	@Test
	void seedCampaignSideIfEnabled_skipsWhenOff() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getName()).thenReturn("Brume");
		when(attacker.getLeader()).thenReturn("Alice");
		War war = new War(1, attacker, defender);
		Warband warband = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		Battle battle = mock(Battle.class);

		WarDevMode.seedCampaignSideIfEnabled(warband, war, battle, BattleTemplate.ATTACKER_SIDE);

		assertEquals(0, warband.getDummyMemberCount());
		assertTrue(warband.isPendingLeader());
	}

	// Exercise the retained compatibility entry point so legacy behavior stays covered.
	@SuppressWarnings("deprecation")
	@Test
	void seedCampaignSideIfEnabled_setsDummyLeaderWithDisplayNameWhenOn() {
		WarDevMode.setEnabled(true);
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getName()).thenReturn("Brume");
		when(attacker.getLeader()).thenReturn("Alice");
		War war = new War(1, attacker, defender);
		Warband warband = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		Battle battle = mock(Battle.class);
		when(battle.getProvinceId()).thenReturn(null);

		WarDevMode.seedCampaignSideIfEnabled(warband, war, battle, BattleTemplate.ATTACKER_SIDE);

		assertEquals(10, warband.getDummyMemberCount());
		assertEquals(WarDevMode.dummyMemberId(warband.getId(), 0), warband.getLeaderId());
		assertEquals(
				WarDevMode.dummyDisplayName(warband.getId(), 0),
				warband.getLeaderDisplayName());
		assertNotEquals("Test Dummy", warband.getLeaderDisplayName());
	}

	// Exercise the retained compatibility entry point so legacy behavior stays covered.
	@SuppressWarnings("deprecation")
	@Test
	void sideRosterCount_matchesMemberCountWithDummies() {
		WarDevMode.setEnabled(true);
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getName()).thenReturn("Brume");
		when(attacker.getLeader()).thenReturn("Alice");
		War war = new War(1, attacker, defender);
		Warband warband = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		Battle battle = mock(Battle.class);
		when(battle.getProvinceId()).thenReturn(null);

		WarDevMode.seedCampaignSideIfEnabled(warband, war, battle, BattleTemplate.ATTACKER_SIDE);

		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
			BossBar bossBar = mock(BossBar.class);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			BattleSide side = new BattleSide(BattleTemplate.ATTACKER_SIDE, LifeType.COLLECTIVE, 0);
			side.getBands().add(warband);

			assertEquals(warband.getMemberCount(), side.getAllParticipants());
			assertTrue(side.getAllParticipants() > 0);
		}
	}

	@Test
	void setEnabled_on_refillsCampaignSideWarbandsAfterRestart() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Carol");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		mockMilitary(attacker);
		mockMilitary(defender);

		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(20);

		try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<net.tfminecraft.simplefactions.managers.FactionManager> factions =
						mockStatic(net.tfminecraft.simplefactions.managers.FactionManager.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattlePoolService> pool =
						mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.class)) {
			mockBossBar(bukkit);
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w1");
			battle.setWarId(1);
			battle.setProvinceId(20);
			battle.setDisplayName("Battle of Lanbury");
			BattleManager.addBattle(battle);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			factions.when(() -> net.tfminecraft.simplefactions.managers.FactionManager.getByString("atk"))
					.thenReturn(attacker);
			factions.when(() -> net.tfminecraft.simplefactions.managers.FactionManager.getByString("def"))
					.thenReturn(defender);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					eq(war), eq(20), any())).thenReturn(5);

			CampaignBattleRosterService.enrollWarbands(war, battle);

			Warband attackerBand = WarbandManager.getByString(
					net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService.campaignWarbandId(
							battle.getDisplayName(), BattleTemplate.ATTACKER_SIDE));
			Warband defenderBand = WarbandManager.getByString(
					net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService.campaignWarbandId(
							battle.getDisplayName(), BattleTemplate.DEFENDER_SIDE));
			assertEquals(0, attackerBand.getDummyMemberCount());
			assertEquals(0, defenderBand.getDummyMemberCount());

			int filled = WarDevMode.setEnabled(true);

			assertEquals(2, filled);
			assertEquals(10, attackerBand.getDummyMemberCount());
			assertEquals(10, defenderBand.getDummyMemberCount());
		}
	}

	@Test
	void setEnabled_off_clearsDummyMembersFromAllWarbands() {
		UUID leaderId = UUID.randomUUID();
		Warband manual = Warband.createWithMemberIds("manual", leaderId, true);
		WarbandManager.addWarband(manual);

		WarDevMode.seedDummyMembers(manual, 5);
		assertEquals(5, manual.getDummyMemberCount());

		int cleared = WarDevMode.setEnabled(false);

		assertEquals(1, cleared);
		assertEquals(0, manual.getDummyMemberCount());
		assertFalse(WarDevMode.isEnabled());
	}

	private static void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(levy.getEntries()).thenReturn(List.of());
		when(faction.getMilitary()).thenReturn(military);
	}

	private static void mockBossBar(MockedStatic<org.bukkit.Bukkit> bukkit) {
		BossBar bossBar = mock(BossBar.class);
		bukkit.when(() -> org.bukkit.Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenReturn(bossBar);
		bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
				anyString(), any(BarColor.class), any(BarStyle.class), any()))
				.thenReturn(bossBar);
	}
}
