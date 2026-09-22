package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;

class ConfigLoaderBattleTemplateDefaultsTest {
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-config-battle-template-");
	}

	@AfterEach
	void tearDown() throws IOException {
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void loadsBattleTemplateDefaults() throws IOException {
		Path configFile = tempDir.resolve("config.yml");
		Files.writeString(configFile, """
				world-name: TFMC_Map
				battle:
				  province_poll_interval_ticks: 20
				  province_leave_countdown_seconds: 10
				  siege:
				    contest_duration_seconds: 240
				  raid:
				    defender_respawn_mode_default: LIVES
				  campaign_template:
				    field: custom_field
				    siege: custom_siege
				    raid: custom_raid
				""");

		new ConfigLoader().loadConfig(configFile.toFile());

		assertEquals(10, Cache.battleProvinceLeaveCountdownSeconds);
		assertEquals(240, Cache.battleSiegeContestDurationSeconds);
		assertEquals(DefenderRespawnMode.LIVES, Cache.battleRaidDefenderRespawnModeDefault);
		assertEquals("custom_field", Cache.battleCampaignTemplateField);
		assertEquals("custom_siege", Cache.battleCampaignTemplateSiege);
		assertEquals("custom_raid", Cache.battleCampaignTemplateRaid);
	}

	@Test
	void invalidLeaveCountdown_throws() throws IOException {
		Path configFile = tempDir.resolve("config.yml");
		Files.writeString(configFile, """
				world-name: TFMC_Map
				battle:
				  province_poll_interval_ticks: 20
				  province_leave_countdown_seconds: 0
				""");

		assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadConfig(configFile.toFile()));
	}
}
