package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class ConfigLoaderBattleScheduleTest {
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-config-");
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
	void loadConfig_validBattleScheduleHours() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				    dev_min_players: 1
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(21, Cache.warBattleWindowStartHour);
		assertEquals(24, Cache.warBattleWindowEndHour);
		assertEquals(19, Cache.warRaidWindowStartHour);
		assertEquals(20, Cache.warRaidWindowEndHour);
		assertEquals(16, Cache.warVoteCloseHour);
		assertEquals(12, Cache.warDefenderChoiceDeadlineHour);
		// The old vote's keys are still accepted and ignored.
		assertEquals(7, Cache.warAvailabilityDaysShown);
		assertEquals(48, Cache.warAvailabilityDayLockHours);
		assertEquals(24, Cache.warAvailabilityTimeLockHours);
		assertEquals(3, Cache.warAvailabilityMinPerSide);
		assertEquals(3, Cache.warAvailabilityMaxDaysWithoutBattle);
		assertEquals("", Cache.warAvailabilityVoteUrl);
		assertEquals(0.15, Cache.warAutoresolveLuck, 0.0001);
		assertEquals(0.5, Cache.warAutoresolveLoserLossFraction, 0.0001);
	}

	@Test
	void loadConfig_autoresolveAndAvailability() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  availability:
				    days_shown: 5
				    day_lock_hours_before: 36
				    time_lock_hours_before: 12
				    min_per_side: 2
				    max_days_without_battle: 0
				    vote_url: "https://example.test/wars/{war_key}#vote"
				  autoresolve:
				    luck: 0.2
				    loser_loss_fraction: 0.25
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(5, Cache.warAvailabilityDaysShown);
		assertEquals(36, Cache.warAvailabilityDayLockHours);
		assertEquals(12, Cache.warAvailabilityTimeLockHours);
		assertEquals(2, Cache.warAvailabilityMinPerSide);
		assertEquals(0, Cache.warAvailabilityMaxDaysWithoutBattle);
		assertEquals("https://example.test/wars/{war_key}#vote", Cache.warAvailabilityVoteUrl);
		assertEquals(0.2, Cache.warAutoresolveLuck, 0.0001);
		assertEquals(0.25, Cache.warAutoresolveLoserLossFraction, 0.0001);
	}

	@Test
	void loadConfig_raidWindowDefaultsWhenOmitted() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(19, Cache.warRaidWindowStartHour);
		assertEquals(20, Cache.warRaidWindowEndHour);
	}

	@Test
	void loadConfig_availabilityDefaultsWhenOmitted() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(3, Cache.warAvailabilityMinPerSide);
	}

	@Test
	void loadConfig_availabilityValuesAreKeptInRange() throws IOException {
		Path file = writeConfig("""
				war:
				  availability:
				    days_shown: 12
				    day_lock_hours_before: 10
				    time_lock_hours_before: 0
				    min_per_side: 0
				    max_days_without_battle: -2
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(7, Cache.warAvailabilityDaysShown);
		assertEquals(1, Cache.warAvailabilityTimeLockHours);
		assertEquals(10, Cache.warAvailabilityDayLockHours);
		assertEquals(1, Cache.warAvailabilityMinPerSide);
		assertEquals(0, Cache.warAvailabilityMaxDaysWithoutBattle);

		Path tight = writeConfig("""
				war:
				  availability:
				    days_shown: 0
				    day_lock_hours_before: 20
				    time_lock_hours_before: 24
				""");
		new ConfigLoader().loadWar(tight.toFile());
		assertEquals(1, Cache.warAvailabilityDaysShown);
		assertEquals(25, Cache.warAvailabilityDayLockHours);
	}

	@Test
	void loadConfig_invalidVoteCloseAfterRaidStartThrows() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 19
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				    dev_min_players: 1
				""");

		assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(file.toFile()));
	}

	@Test
	void loadConfig_invalidRaidOverlapsBattleWindowThrows() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 20
				    raid_window_end_hour: 21
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				    dev_min_players: 1
				""");

		assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(file.toFile()));
	}

	@Test
	void loadConfig_defenderDeadlineMustFallOnTheBattleDate() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    defender_choice_deadline_hour: 24
				    vote_close_hour: 25
				    raid_window_start_hour: 26
				    raid_window_end_hour: 27
				    window_start_hour: 28
				    window_end_hour: 48
				  battle_voting:
				    min_players: 4
				    dev_min_players: 1
				""");

		IllegalStateException error =
				assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(file.toFile()));
		assertTrue(error.getMessage().contains("between 0 and 23"));
	}

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
