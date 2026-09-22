package me.Plugins.SimpleFactions.War.battle.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleJoinService;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.core.War;

class BattlePersistenceServiceTest {
	private BossBar bossBar;
	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		cleanPersistenceDirs();

		bossBar = mock(BossBar.class);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenReturn(bossBar);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		cleanPersistenceDirs();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
	}

	@Test
	void saveAndLoad_restoresManualBattleAndReferencedWarband() {
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "persist_field");
		battle.setLocked(false);
		Warband warband = Warband.createWithMemberIds("persist_band", UUID.randomUUID(), true);
		WarbandManager.addWarband(warband);
		BattleManager.addBattle(battle);
		assertNull(BattleJoinService.join(warband, battle, BattleTemplate.ATTACKER_SIDE));
		battle.setStarted(true);

		BattlePersistenceService.saveAll();

		assertTrue(new File("plugins/SimpleFactions/Battles/battle_persist_field.json").exists());
		assertTrue(new File("plugins/SimpleFactions/Warbands/warband_persist_band.json").exists());

		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		BattlePersistenceService.loadAll();

		Battle loaded = BattleManager.getByString("persist_field");
		assertNotNull(loaded);
		assertTrue(loaded.hasStarted());
		assertNotNull(WarbandManager.getByString("persist_band"));
		assertNotNull(loaded.getSideById(BattleTemplate.ATTACKER_SIDE));
		assertEquals(1, loaded.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().size());
	}

	@Test
	void saveAll_purgesOrphanManualWarbands() {
		Warband orphan = Warband.createWithMemberIds("orphan_band", UUID.randomUUID(), true);
		WarbandManager.addWarband(orphan);
		BattlePersistenceService.saveAll();

		assertNull(WarbandManager.getByString("orphan_band"));
		assertFalse(new File("plugins/SimpleFactions/Warbands/warband_orphan_band.json").exists());
	}

	@Test
	void loadAll_keepsSingleManualBattleWhenMultipleFilesExist() {
		writeManualBattleFile("manual_a", false);
		writeManualBattleFile("manual_b", true);

		BattlePersistenceService.loadAll();

		assertEquals(1, BattleManager.get().size());
		assertNotNull(BattleManager.getByString("manual_b"));
		assertFalse(new File("plugins/SimpleFactions/Battles/battle_manual_a.json").exists());
	}

	@Test
	void deleteCampaignBattle_removesAttachedAndShellWarbands() {
		War war = mockWar(1);
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w1_p20");
		battle.setWarId(1);
		battle.setDisplayName("Battle of Lanbury");
		battle.setLocked(false);
		Warband attacker = Warband.createCampaignSideShell(
				BattleNamingService.campaignWarbandId(battle.getDisplayName(), BattleTemplate.ATTACKER_SIDE),
				war,
				war.getAttackers(),
				BattleTemplate.ATTACKER_SIDE);
		Warband defender = Warband.createCampaignSideShell(
				BattleNamingService.campaignWarbandId(battle.getDisplayName(), BattleTemplate.DEFENDER_SIDE),
				war,
				war.getDefenders(),
				BattleTemplate.DEFENDER_SIDE);
		WarbandManager.addWarband(attacker);
		WarbandManager.addWarband(defender);
		BattleManager.addBattle(battle);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(attacker);
		battle.getSideById(BattleTemplate.DEFENDER_SIDE).addBand(defender);

		BattlePersistenceService.deleteCampaignBattle(battle);

		assertNull(WarbandManager.getByString(
				BattleNamingService.campaignWarbandId(battle.getDisplayName(), BattleTemplate.ATTACKER_SIDE)));
		assertNull(WarbandManager.getByString(
				BattleNamingService.campaignWarbandId(battle.getDisplayName(), BattleTemplate.DEFENDER_SIDE)));
		assertTrue(BattleManager.get().isEmpty());
	}

	@Test
	void deleteRaidBattle_removesAttachedWarbands() {
		War war = mockWar(1);
		Battle battle = BattleFactory.createBlank(BattleType.RAID, "harbor_raid");
		battle.setCampaignRaid(true);
		battle.setLocked(false);
		Warband attacker = Warband.createRaidShell(
				"harbor_raid_attacker", war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		Warband defender = Warband.createRaidShell(
				"harbor_raid_defender", war.getDefenders(), BattleTemplate.DEFENDER_SIDE);
		WarbandManager.addWarband(attacker);
		WarbandManager.addWarband(defender);
		BattleManager.addBattle(battle);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(attacker);
		battle.getSideById(BattleTemplate.DEFENDER_SIDE).addBand(defender);

		BattlePersistenceService.deleteRaidBattle(battle);

		assertNull(WarbandManager.getByString("harbor_raid_attacker"));
		assertNull(WarbandManager.getByString("harbor_raid_defender"));
		assertTrue(BattleManager.get().isEmpty());
	}

	private War mockWar(int id) {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		return new War(id, attacker, defender);
	}

	@Test
	void hasManualBattle_blocksSecondManualBattle() {
		BattleManager.addBattle(BattleFactory.createBlank(BattleType.FIELD, "only_manual"));
		assertTrue(BattleManager.hasManualBattle());
	}

	private void writeManualBattleFile(String id, boolean started) {
		File folder = new File("plugins/SimpleFactions/Battles");
		folder.mkdirs();
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, id);
		battle.setStarted(started);
		new me.Plugins.SimpleFactions.Database.Database().saveBattle(battle);
	}

	private void cleanPersistenceDirs() {
		deleteRecursively(new File("plugins/SimpleFactions/Battles"));
		deleteRecursively(new File("plugins/SimpleFactions/Warbands"));
	}

	private void deleteRecursively(File file) {
		if (file == null || !file.exists()) {
			return;
		}
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursively(child);
				}
			}
		}
		file.delete();
	}
}
