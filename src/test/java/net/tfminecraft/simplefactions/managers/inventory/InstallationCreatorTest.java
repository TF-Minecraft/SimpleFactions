package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
