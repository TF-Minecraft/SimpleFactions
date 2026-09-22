package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.Objects.ModifierScale;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

class RelationLoaderAttitudeScaleTest {
	private List<Attitude> savedAttitudes;
	private List<RelationType> savedTypes;
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		savedAttitudes = new ArrayList<>(RelationLoader.attitudes);
		savedTypes = new ArrayList<>(RelationLoader.types);
		RelationLoader.attitudes.clear();
		RelationLoader.types.clear();
		tempDir = Files.createTempDirectory("sf-attitudes-");
	}

	@AfterEach
	void tearDown() throws IOException {
		RelationLoader.attitudes.clear();
		RelationLoader.attitudes.addAll(savedAttitudes);
		RelationLoader.types.clear();
		RelationLoader.types.addAll(savedTypes);
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void loadsFriendlyCostAndHostileCurve() throws IOException {
		Path file = tempDir.resolve("diplomacy.yml");
		Files.writeString(file, """
				attitudes:
				  friendly:
				    name: Friendly
				    cost: 0.25
				  hostile:
				    name: Hostile
				    recieve-modifiers:
				      - type: diplomatic_capacity_multiplier
				        scale: relative_prestige
				        at_weaker: -2
				        at_equal: 4
				        at_stronger: 8
				types:
				  rival:
				    name: Rival
				    recieve-modifiers:
				      - type: diplomatic_capacity_multiplier
				        scale: relative_prestige
				        at_weaker: -4
				        at_equal: 10
				        at_stronger: 20
				""");
		RelationLoader loader = new RelationLoader();
		loader.loadAttitudes(file.toFile());
		loader.loadRelationTypes(file.toFile());

		Attitude friendly = RelationLoader.getAttitude("friendly");
		assertEquals(0.25, friendly.getBaseCost(), 1e-9);

		Attitude hostile = RelationLoader.getAttitude("hostile");
		assertTrue(hostile.hasRecieveModifiers());
		FactionModifier hostileMod = hostile.getRecieveModifiers().get(0);
		assertEquals(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER, hostileMod.getType());
		assertEquals(ModifierScale.Kind.RELATIVE_PRESTIGE, hostileMod.getScale());

		RelationType rival = RelationLoader.getType("rival");
		assertEquals(ModifierScale.Kind.RELATIVE_PRESTIGE, rival.getRecieveModifiers().get(0).getScale());
	}
}
