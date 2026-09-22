package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.PrestigeRank;

class RankLoaderReloadTest {

	private Path tempDir;
	private List<PrestigeRank> previousRanks;
	private List<Faction> previousFactions;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-ranks-");
		previousRanks = new ArrayList<>(RankLoader.ranks);
		previousFactions = new ArrayList<>(FactionManager.factions);
		RankLoader.ranks.clear();
		FactionManager.factions.clear();
	}

	@AfterEach
	void tearDown() throws IOException {
		RankLoader.ranks.clear();
		RankLoader.ranks.addAll(previousRanks);
		FactionManager.factions.clear();
		FactionManager.factions.addAll(previousFactions);
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void loadRanks_twice_replacesInsteadOfAppending() throws IOException {
		Path file = writeRanks(0);
		RankLoader loader = new RankLoader();
		loader.loadRanks(file.toFile());
		loader.loadRanks(writeRanks(400).toFile());

		assertEquals(1, RankLoader.getRanks().size());
		assertEquals(400.0, RankLoader.getByString("obscure_faction").getMin(), 1e-9);
	}

	@Test
	void rebindRanks_swapsToTheReloadedInstance() throws IOException {
		RankLoader loader = new RankLoader();
		loader.loadRanks(writeRanks(0).toFile());
		PrestigeRank first = RankLoader.getByString("obscure_faction");

		Faction faction = mock(Faction.class);
		when(faction.getRank()).thenReturn(first);
		FactionManager.factions.add(faction);

		loader.loadRanks(writeRanks(400).toFile());
		PrestigeRank second = RankLoader.getByString("obscure_faction");
		assertNotSame(first, second);

		FactionManager.rebindRanks();
		verify(faction).setRank(second);
	}

	@Test
	void rebindRanks_fallsBackToLowestWhenIdIsGone() throws IOException {
		RankLoader loader = new RankLoader();
		loader.loadRanks(writeRanks(0).toFile());
		PrestigeRank lowest = RankLoader.getLowest();

		PrestigeRank missing = mock(PrestigeRank.class);
		when(missing.getId()).thenReturn("deleted_rank");
		Faction faction = mock(Faction.class);
		when(faction.getRank()).thenReturn(missing);
		FactionManager.factions.add(faction);

		FactionManager.rebindRanks();
		verify(faction).setRank(argThat(rank -> rank == lowest));
		assertSame(lowest, RankLoader.getLowest());
	}

	private Path writeRanks(int minimumPrestige) throws IOException {
		Path file = tempDir.resolve("ranks.yml");
		Files.writeString(file, """
				obscure_faction:
				  name: Obscure Faction
				  level: 1
				  minimum-prestige: %d
				  percentage-of-highest: 0.0
				""".formatted(minimumPrestige));
		return file;
	}
}
