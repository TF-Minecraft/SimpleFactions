package me.Plugins.SimpleFactions.War.battle.warband;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class WarbandVehicleRulesTest {
	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		WarManager.get().clear();
	}

	@AfterEach
	void tearDown() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		WarManager.get().clear();
	}

	@Test
	void campaignAutoWarband_detectsCampaignShell() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(1, attacker, defender);
		Warband warband = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		assertTrue(WarbandVehicleRules.isCampaignAutoWarband(warband));
	}

	@Test
	void campaignAutoWarband_detectsRaidShell() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Atk");
		War war = new War(1, attacker, defender);
		WarManager.addWar(war);
		CampaignRaid raid = new CampaignRaid();
		raid.setId("harbor_raid");
		raid.setDisplayName("Harbor Raid");
		raid.setWarId(1);
		raid.setState(CampaignRaidState.MUSTER);
		war.setActiveCampaignRaid(raid);
		Warband warband = Warband.createRaidShell(
				"harbor_raid_attacker", war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		assertTrue(WarbandVehicleRules.isCampaignAutoWarband(warband));
	}

	@Test
	void blocksVehicleEntry_whenCampaignBattleNotStarted() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);

		Warband warband = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		WarbandManager.addWarband(warband);

		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			warManager.when(() -> WarManager.getById(1)).thenReturn(war);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w1_p20");
			battle.setWarId(1);
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(warband);
			BattleManager.addBattle(battle);

			assertTrue(WarbandVehicleRules.blocksVehicleEntryForWarband(warband));
		}
	}

	@Test
	void joinBlockedReason_nullWhenNotMounted() {
		Player player = mock(Player.class);
		when(player.isInsideVehicle()).thenReturn(false);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			mockVehicleFrameworkDisabled(bukkit);
			assertEquals(null, WarbandVehicleRules.joinBlockedReason(player));
		}
	}

	@Test
	void joinBlockedReason_whenInsideVehicle() {
		Player player = mock(Player.class);
		when(player.isInsideVehicle()).thenReturn(true);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			mockVehicleFrameworkDisabled(bukkit);
			assertEquals(WarbandVehicleRules.JOIN_BLOCKED_MOUNTED, WarbandVehicleRules.joinBlockedReason(player));
		}
	}

	private void mockVehicleFrameworkDisabled(MockedStatic<Bukkit> bukkit) {
		PluginManager pluginManager = mock(PluginManager.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		when(pluginManager.isPluginEnabled("VehicleFramework")).thenReturn(false);
	}
}
