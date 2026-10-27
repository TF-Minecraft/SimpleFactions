package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;

class InstallationCreatorTest {
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-installation-creator-");
		Path vehiclesYaml = tempDir.resolve("vehicles.yml");
		Files.writeString(vehiclesYaml, """
				categories:
				  static_emplacements:
				    display-name: Static Emplacements
				  land_vehicles: {}
				""");
		VehiclesConfigLoader.load(vehiclesYaml.toFile());
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
	void categoryDisplayNameUsesConfiguredNameOrReadableId() {
		assertEquals("Static Emplacements", InstallationCreator.categoryDisplayName("static_emplacements"));
		assertEquals("land vehicles", InstallationCreator.categoryDisplayName("land_vehicles"));
	}

	@Test
	void everyDefaultVehicleHasADefaultIcon() {
		List<String> icons = YamlConfiguration
				.loadConfiguration(Path.of("src/main/resources/config.yml").toFile())
				.getStringList("icons");
		ConfigurationSection categories = YamlConfiguration
				.loadConfiguration(Path.of("src/main/resources/vehicles.yml").toFile())
				.getConfigurationSection("categories");
		int vehicles = 0;
		for (String category : categories.getKeys(false)) {
			ConfigurationSection section = categories.getConfigurationSection(category);
			for (String vehicle : section.getKeys(false)) {
				if (!section.isConfigurationSection(vehicle)) continue;
				vehicles++;
				String prefix = "vehicle_" + vehicle + "(coal.";
				assertTrue(icons.stream().anyMatch(icon -> icon.startsWith(prefix)), vehicle);
			}
		}
		assertEquals(22, vehicles);
		assertTrue(icons.contains("vehicle_pool(coal.7414)"));
	}
}
