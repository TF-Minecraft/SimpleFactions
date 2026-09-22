package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.Attitude;
import me.Plugins.SimpleFactions.Diplomacy.DiplomacyHandler;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

class RelationLoaderReloadTest {

	private Path tempDir;
	private List<RelationType> previousTypes;
	private List<Attitude> previousAttitudes;
	private List<Faction> previousFactions;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-diplomacy-reload-");
		previousTypes = new ArrayList<>(RelationLoader.types);
		previousAttitudes = new ArrayList<>(RelationLoader.attitudes);
		previousFactions = new ArrayList<>(FactionManager.factions);
		RelationLoader.types.clear();
		RelationLoader.attitudes.clear();
		FactionManager.factions.clear();
	}

	@AfterEach
	void tearDown() throws IOException {
		RelationLoader.types.clear();
		RelationLoader.types.addAll(previousTypes);
		RelationLoader.attitudes.clear();
		RelationLoader.attitudes.addAll(previousAttitudes);
		FactionManager.factions.clear();
		FactionManager.factions.addAll(previousFactions);
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void loadRelationTypes_twice_replacesInsteadOfAppending() throws IOException {
		RelationLoader loader = new RelationLoader();
		loader.loadRelationTypes(writeDiplomacy(10).toFile());
		loader.loadRelationTypes(writeDiplomacy(40).toFile());

		assertEquals(2, RelationLoader.getTypes().size());
		assertEquals(40, RelationLoader.getType("ally").getTarget());
	}

	@Test
	void loadAttitudes_twice_replacesInsteadOfAppending() throws IOException {
		RelationLoader loader = new RelationLoader();
		loader.loadAttitudes(writeDiplomacy(10).toFile());
		loader.loadAttitudes(writeDiplomacy(40).toFile());

		assertEquals(1, RelationLoader.getAttitudes().size());
		assertEquals(40, RelationLoader.getAttitude("friendly").getTarget());
	}

	@Test
	void rebindDiplomacy_swapsLiveRelationsAndDropsMissingTrade() throws IOException {
		RelationLoader loader = new RelationLoader();
		loader.loadRelationTypes(writeDiplomacy(10).toFile());
		loader.loadAttitudes(writeDiplomacy(10).toFile());
		RelationType firstAlly = RelationLoader.getType("ally");
		Attitude firstFriendly = RelationLoader.getAttitude("friendly");
		RelationType firstTrade = RelationLoader.getType("trade_agreement");

		Faction faction = mock(Faction.class);
		DiplomacyHandler handler = new DiplomacyHandler(faction);
		when(faction.getDiplomacyHandler()).thenReturn(handler);
		when(faction.getId()).thenReturn("us");
		Relation relation = new Relation(firstAlly, firstFriendly);
		handler.getRelations().put("them", relation);
		handler.getTradeRelations().put("them", firstTrade);
		handler.getTradeRelations().put("gone", firstTrade);
		FactionManager.factions.add(faction);

		loader.loadRelationTypes(writeDiplomacyWithoutTrade(40).toFile());
		loader.loadAttitudes(writeDiplomacy(40).toFile());
		RelationType secondAlly = RelationLoader.getType("ally");
		Attitude secondFriendly = RelationLoader.getAttitude("friendly");
		assertNotSame(firstAlly, secondAlly);

		FactionManager.rebindDiplomacy();

		assertSame(secondAlly, relation.getType());
		assertSame(secondFriendly, relation.getAttitude());
		assertNull(handler.getTradeRelation("them"));
		assertNull(handler.getTradeRelation("gone"));
	}

	private Path writeDiplomacy(int target) throws IOException {
		Path file = tempDir.resolve("diplomacy.yml");
		Files.writeString(file, """
				types:
				  ally:
				    name: Ally
				    target: %d
				    default: true
				  trade_agreement:
				    name: Trade
				    trade-agreement: true
				attitudes:
				  friendly:
				    name: Friendly
				    target: %d
				    default: true
				""".formatted(target, target));
		return file;
	}

	private Path writeDiplomacyWithoutTrade(int target) throws IOException {
		Path file = tempDir.resolve("diplomacy-no-trade.yml");
		Files.writeString(file, """
				types:
				  ally:
				    name: Ally
				    target: %d
				    default: true
				attitudes:
				  friendly:
				    name: Friendly
				    target: %d
				    default: true
				""".formatted(target, target));
		return file;
	}
}
