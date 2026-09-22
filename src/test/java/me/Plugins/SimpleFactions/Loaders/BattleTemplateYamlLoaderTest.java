package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

class BattleTemplateYamlLoaderTest {
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		BattleTemplateLoader.resetForTests();
		Cache.worldName = "TFMC_Map";
		tempDir = Files.createTempDirectory("sf-battle-templates-");
	}

	@AfterEach
	void tearDown() throws IOException {
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
		BattleTemplateLoader.resetForTests();
	}

	@Test
	void load_parsesSettingsOnlyYaml() throws IOException {
		Path yamlFile = tempDir.resolve("battle-templates.yml");
		Files.writeString(yamlFile, """
				raid_template:
				  type: raid
				  defender_respawn_mode: infinite
				  friendly_fire: true
				  keep_inventory: true
				field_default:
				  type: field
				  capture_points_enabled: true
				  lives: 25
				  life_type: COLLECTIVE
				  friendly_fire: true
				  keep_inventory: true
				""");

		new BattleTemplateLoader().load(yamlFile.toFile());

		BattleTemplate raid = BattleTemplateLoader.getByName("raid_template");
		assertNotNull(raid);
		assertEquals(BattleType.RAID, raid.getType());
		assertEquals(DefenderRespawnMode.INFINITE, raid.getConfig().getDefenderRespawnMode());
		assertTrue(raid.getConfig().getFriendlyFire());

		BattleTemplate field = BattleTemplateLoader.getByName("field_default");
		assertNotNull(field);
		assertEquals(Boolean.TRUE, field.getConfig().getCapturePointsEnabled());
		assertEquals(25, field.getConfig().getLives());
		assertEquals(LifeType.COLLECTIVE, field.getConfig().getLifeType());
	}

	@Test
	void load_invalidType_skipsTemplate() throws IOException {
		Path yamlFile = tempDir.resolve("battle-templates.yml");
		Files.writeString(yamlFile, """
				broken:
				  type: not_a_type
				  friendly_fire: true
				""");

		new BattleTemplateLoader().load(yamlFile.toFile());

		assertTrue(BattleTemplateLoader.getAll().isEmpty());
	}
}

