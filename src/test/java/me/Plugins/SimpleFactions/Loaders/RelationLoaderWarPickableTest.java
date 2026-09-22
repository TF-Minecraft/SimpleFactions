package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.RelationType;

class RelationLoaderWarPickableTest {
	private List<RelationType> savedTypes;
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		savedTypes = new ArrayList<>(RelationLoader.types);
		RelationLoader.types.clear();
		tempDir = Files.createTempDirectory("sf-diplomacy-");
	}

	@AfterEach
	void tearDown() throws IOException {
		RelationLoader.types.clear();
		RelationLoader.types.addAll(savedTypes);
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void missingCanPickForWar_defaultsTrue() throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString("""
				name: Subject
				vassal: true
				""");

		RelationType type = new RelationType("subject", config);
		assertTrue(type.canPickForWar());
		assertTrue(type.isVassalage());
	}

	@Test
	void canPickForWarFalse_isNotPickable() throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString("""
				name: Integrated Subject
				vassal: true
				can-pick-for-war: false
				""");

		RelationType type = new RelationType("integrated_subject", config);
		assertFalse(type.canPickForWar());
		assertTrue(type.isVassalage());
	}

	@Test
	void getWarPickableVassalTypes_filtersVassalsByFlag() throws IOException {
		Path file = tempDir.resolve("diplomacy.yml");
		Files.writeString(file, """
				types:
				  subject:
				    name: Subject
				    vassal: true
				  mercantile:
				    name: Mercantile
				    vassal: true
				  march:
				    name: March
				    vassal: true
				    limit: 1
				  palatinate:
				    name: Palatinate
				    vassal: true
				    limit: 1
				  integrated_subject:
				    name: Integrated Subject
				    vassal: true
				    can-pick-for-war: false
				  ally:
				    name: Ally
				  tributary:
				    name: Tributary
				""");

		new RelationLoader().loadRelationTypes(file.toFile());

		Set<String> ids = RelationLoader.getWarPickableVassalTypes().stream()
				.map(RelationType::getId)
				.collect(Collectors.toSet());

		assertEquals(Set.of("subject", "mercantile", "march", "palatinate"), ids);
		assertFalse(ids.contains("integrated_subject"));
		assertFalse(ids.contains("ally"));
		assertFalse(ids.contains("tributary"));
	}

	@Test
	void diplomaticAndTreatyFilters_splitByTradeAgreementFlag() throws IOException {
		Path file = tempDir.resolve("diplomacy.yml");
		Files.writeString(file, """
				types:
				  ally:
				    name: Ally
				  tributary:
				    name: Tributary
				  trade_agreement:
				    name: Trade
				    trade-agreement: true
				  embargo:
				    name: Embargo
				    trade-agreement: true
				  nap:
				    name: Non-Aggression Pact
				    treaty: true
				    blocks-war: true
				  none_treaty:
				    name: No Treaty
				    treaty: true
				    clear: true
				""");

		new RelationLoader().loadRelationTypes(file.toFile());

		Set<String> diplomatic = RelationLoader.getDiplomaticTypes().stream()
				.map(RelationType::getId)
				.collect(Collectors.toSet());
		Set<String> treaties = RelationLoader.getTreatyTypes().stream()
				.map(RelationType::getId)
				.collect(Collectors.toSet());
		Set<String> politicalTreaties = RelationLoader.getPoliticalTreatyTypes().stream()
				.map(RelationType::getId)
				.collect(Collectors.toSet());

		assertEquals(Set.of("ally", "tributary"), diplomatic);
		assertEquals(Set.of("trade_agreement", "embargo"), treaties);
		assertEquals(Set.of("nap", "none_treaty"), politicalTreaties);
		assertFalse(treaties.contains("nap"));
	}

	@Test
	void blocksShops_defaultsFalse_andReadsYaml() throws Exception {
		YamlConfiguration missing = new YamlConfiguration();
		missing.loadFromString("""
				name: Trade
				trade-agreement: true
				""");
		assertFalse(new RelationType("trade_agreement", missing).blocksShops());

		YamlConfiguration embargo = new YamlConfiguration();
		embargo.loadFromString("""
				name: Embargo
				trade-agreement: true
				blocks-shops: true
				""");
		assertTrue(new RelationType("embargo", embargo).blocksShops());
	}
}
