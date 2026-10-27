package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
	void everyVehicleHasItsResourcePackIcon() {
		List<String> icons = YamlConfiguration
				.loadConfiguration(Path.of("src/main/resources/config.yml").toFile())
				.getStringList("icons");
		// The pack draws one icon per vehicle on COAL 7400-7421, in vehicle id order.
		List<String> vehicles = List.of(
				"aa_turret", "anti_air", "behemoth", "biplane",
				"bomber", "cloudskimmer", "coal_car", "cruiser",
				"field_artillery", "fixed_artillery", "flat_car", "gunboat",
				"gyrobomber", "horse_cart", "ironclad", "monoplane",
				"passenger_car", "simple_locomotive", "sloop", "small_car",
				"torpedoboat", "wooden_cart");
		for (int n = 0; n < vehicles.size(); n++) {
			String icon = "vehicle_" + vehicles.get(n) + "(coal." + (7400 + n) + ")";
			assertTrue(icons.contains(icon), icon);
		}
		assertTrue(icons.contains("vehicle_pool(coal.7414)"));
	}
}
