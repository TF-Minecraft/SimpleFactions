package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;

class ConfigLoaderProvincesTest {
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-config-provinces-");
		Cache.provincesEnabled = true;
		Cache.mapEnabled = false;
	}

	@AfterEach
	void tearDown() throws IOException {
		Cache.provincesEnabled = true;
		Cache.mapEnabled = false;
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void loadConfig_missingEnableProvincesDefaultsTrue() throws IOException {
		Path file = writeConfig("""
				enable-map: false
				""");

		new ConfigLoader().loadConfig(file.toFile());

		assertTrue(Cache.provincesEnabled);
	}

	@Test
	void loadConfig_enableProvincesFalseForcesMapOff() throws IOException {
		Path file = writeConfig("""
				enable-map: true
				enable-provinces: false
				""");

		new ConfigLoader().loadConfig(file.toFile());

		assertFalse(Cache.provincesEnabled);
		assertFalse(Cache.mapEnabled);
	}

	@Test
	void loadConfig_enableProvincesTrueKeepsMap() throws IOException {
		Path file = writeConfig("""
				enable-map: true
				enable-provinces: true
				""");

		new ConfigLoader().loadConfig(file.toFile());

		assertTrue(Cache.provincesEnabled);
		assertTrue(Cache.mapEnabled);
	}

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
