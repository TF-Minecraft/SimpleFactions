package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.loaders.ConfigLoader;

class GuildElevationCostTest {

	private double base;
	private double multiplier;
	private double exponent;
	private double eviction;

	@BeforeEach
	void saveCache() {
		base = Cache.elevationBase;
		multiplier = Cache.elevationSizeMultiplier;
		exponent = Cache.elevationExponent;
		eviction = Cache.evictionMultiplier;
	}

	@AfterEach
	void restoreCache() {
		Cache.elevationBase = base;
		Cache.elevationSizeMultiplier = multiplier;
		Cache.elevationExponent = exponent;
		Cache.evictionMultiplier = eviction;
	}

	@Test
	void costUsesConfiguredSizeMultiplier() {
		Cache.elevationBase = 25;
		Cache.elevationSizeMultiplier = 1;
		Cache.elevationExponent = 1.1;
		Cache.evictionMultiplier = 2;

		Guild guild = mock(Guild.class, Answers.CALLS_REAL_METHODS);
		doReturn(4).when(guild).getSize();

		double expected = 25 + Math.pow(4, 1.1);
		assertEquals(expected, guild.getElevationCost(), 1e-9);
		assertEquals(expected * 2, guild.getEvictionCost(), 1e-9);
	}

	@Test
	void zeroSizeChargesOnlyTheBase() {
		Cache.elevationBase = 25;
		Cache.elevationSizeMultiplier = 1;
		Cache.elevationExponent = 1.1;
		Cache.evictionMultiplier = 2;

		Guild guild = mock(Guild.class, Answers.CALLS_REAL_METHODS);
		doReturn(0).when(guild).getSize();

		assertEquals(25, guild.getElevationCost(), 1e-9);
		assertEquals(50, guild.getEvictionCost(), 1e-9);
	}

	@Test
	void missingConfigUsesSizeMultiplierOfOne() throws IOException {
		Path dir = Files.createTempDirectory("sf-elevation-");
		try {
			new ConfigLoader().loadConfig(dir.resolve("config.yml").toFile());
			assertEquals(25.0, Cache.elevationBase, 1e-9);
			assertEquals(1.0, Cache.elevationSizeMultiplier, 1e-9);
			assertEquals(1.1, Cache.elevationExponent, 1e-9);
			assertEquals(2.0, Cache.evictionMultiplier, 1e-9);
		} finally {
			Files.deleteIfExists(dir.resolve("config.yml"));
			Files.deleteIfExists(dir);
		}
	}

	@Test
	void invalidConfigFallsBack() throws IOException {
		Path dir = Files.createTempDirectory("sf-elevation-");
		Path file = dir.resolve("config.yml");
		Files.writeString(file, """
				elevation-base: -5
				elevation-size-multiplier: -1
				elevation-exponent: 1.4
				eviction-multiplier: 3
				""");
		try {
			new ConfigLoader().loadConfig(file.toFile());
			assertEquals(25.0, Cache.elevationBase, 1e-9);
			assertEquals(1.0, Cache.elevationSizeMultiplier, 1e-9);
			assertEquals(1.4, Cache.elevationExponent, 1e-9);
			assertEquals(3.0, Cache.evictionMultiplier, 1e-9);
		} finally {
			Files.deleteIfExists(file);
			Files.deleteIfExists(dir);
		}
	}
}
