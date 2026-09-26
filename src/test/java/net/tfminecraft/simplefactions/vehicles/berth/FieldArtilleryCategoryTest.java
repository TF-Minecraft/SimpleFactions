package net.tfminecraft.simplefactions.vehicles.berth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;

class FieldArtilleryCategoryTest {

	@Test
	void fieldArtilleryIsItsOwnCategoryAndCannotBerth() throws URISyntaxException {
		VehiclesConfigLoader.load(resource("vehicles.yml"));
		InstallationConfigLoader.load(resource("installations.yml"));

		assertEquals("artillery", VehiclesConfigLoader.getCategoryId("field_artillery").orElseThrow());
		assertEquals(4.0, VehiclesConfigLoader.getUpkeep("field_artillery"));
		assertEquals(1, VehiclesConfigLoader.getSize("field_artillery"));
		assertEquals("static_emplacements", VehiclesConfigLoader.getCategoryId("fixed_artillery").orElseThrow());
		assertFalse(VehicleCategoryRules.isBerthableCategory("artillery"));
		assertFalse(VehicleCategoryRules.isBerthableType("field_artillery"));
		assertTrue(VehicleCategoryRules.isBerthableType("fixed_artillery"));
		for (InstallationKind kind : InstallationKind.values()) {
			assertEquals(0, InstallationConfigLoader.getCategorySlotCapacity(kind, "artillery"));
		}
	}

	private static File resource(String name) throws URISyntaxException {
		return new File(FieldArtilleryCategoryTest.class.getClassLoader().getResource(name).toURI());
	}
}
