package me.Plugins.SimpleFactions.War.battle.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Loaders.BattleTemplateLoader;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;

class BattleTemplateServiceTest {
	private BattleTemplateService service;

	@BeforeEach
	void setUp() {
		BattleTemplateLoader.resetForTests();
		BattleTemplateService.resetForTests();
		Cache.battleSiegeContestDurationSeconds = 180;
		Cache.battleRaidDefenderRespawnModeDefault = DefenderRespawnMode.INFINITE;
		service = BattleTemplateService.getInstance();
	}

	@Test
	void getModeConfig_returnsNullWhenTemplateMissing() {
		assertNull(service.getModeConfig("missing"));
		assertFalse(service.hasTemplate("missing"));
	}

	@Test
	void getModeConfig_appliesDefaultsForPartialModeBlock() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("type", "field");
		BattleTemplateLoader.putForTests(new BattleTemplate("partial_field", config));

		BattleModeTemplate resolved = service.getModeConfig("partial_field");

		assertNotNull(resolved);
		assertTrue(resolved.getFriendlyFire());
		assertTrue(resolved.getKeepInventory());
		assertTrue(resolved.getLootEnabled());
		assertEquals(LifeType.COLLECTIVE, resolved.getLifeType());
		assertEquals(25, resolved.getLives());
	}

	@Test
	void getModeConfig_readsExplicitLootFlag() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("type", "field");
		config.set("loot_enabled", false);
		BattleTemplateLoader.putForTests(new BattleTemplate("no_loot_field", config));

		assertFalse(service.getModeConfig("no_loot_field").getLootEnabled());
	}

	@Test
	void getModeConfig_defaultsLootOffForCampaignRaidTemplate() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("type", "raid");
		config.set("campaign_raid", true);
		BattleTemplateLoader.putForTests(new BattleTemplate("campaign_raid_template", config));

		assertFalse(service.getModeConfig("campaign_raid_template").getLootEnabled());
	}

	@Test
	void applyDefaults_fillsSiegeAndRaidDefaults() {
		BattleModeTemplate siege = new BattleModeTemplate();
		BattleModeTemplate raid = new BattleModeTemplate();

		service.applyDefaults(siege, BattleType.SIEGE);
		service.applyDefaults(raid, BattleType.RAID);

		assertEquals(180, siege.getContestDurationSeconds());
		assertEquals(DefenderRespawnMode.INFINITE, raid.getDefenderRespawnMode());
	}

	@Test
	void getTemplate_returnsStoredTemplate() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("type", "raid");
		BattleTemplateLoader.putForTests(new BattleTemplate("raid_template", config));

		assertTrue(service.hasTemplate("raid_template"));
		assertEquals(BattleType.RAID, service.getTemplate("raid_template").getType());
	}
}
