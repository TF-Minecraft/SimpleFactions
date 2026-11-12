package net.tfminecraft.simplefactions.war.campaign.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.war.campaign.vote.InstallationPickWarnings.Warning;

class InstallationPickWarningsTest {
	private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString(text);
		return config;
	}

	@Test
	void defaultsWarnAtTheTimeLockThenThreeHoursAndOneHourBeforeToEveryLeader() {
		InstallationPickWarnings defaults = InstallationPickWarnings.defaults();
		assertTrue(defaults.enabled());
		assertEquals(new Warning(Duration.ZERO, false), defaults.atTimeLock());
		assertEquals(List.of(new Warning(Duration.ofHours(3), false), new Warning(Duration.ofHours(1), true)),
				defaults.beforeLock());
		assertEquals(defaults, InstallationPickWarnings.fromConfig(null, null), "No section: the defaults");
	}

	@Test
	void aSectionWithoutBeforeLockKeepsTheDefaultWarnings() throws InvalidConfigurationException {
		InstallationPickWarnings parsed = InstallationPickWarnings.fromConfig(
				yaml("enabled: false\nat_time_lock:\n  enabled: false\n"), null);
		assertFalse(parsed.enabled());
		assertNull(parsed.atTimeLock());
		assertEquals(InstallationPickWarnings.defaults().beforeLock(), parsed.beforeLock());
	}

	@Test
	void entriesAreSortedEarliestFirstAndDisabledOrBrokenOnesAreSkipped() throws InvalidConfigurationException {
		Logger logger = mock(Logger.class);
		InstallationPickWarnings parsed = InstallationPickWarnings.fromConfig(yaml("""
				at_time_lock:
				  include_committed: true
				before_lock:
				  - hours_before: 0.5
				    include_committed: true
				  - hours_before: 6
				  - hours_before: 2
				    enabled: false
				  - hours_before: 0
				  - hours_before: soon
				  - hours_before: 6
				    include_committed: true
				  - hours_before: 0.001
				"""), logger);

		assertTrue(parsed.enabled());
		assertEquals(new Warning(Duration.ZERO, true), parsed.atTimeLock());
		assertEquals(List.of(new Warning(Duration.ofHours(6), false), new Warning(Duration.ofMinutes(30), true),
				new Warning(Duration.ofMinutes(1), false)), parsed.beforeLock());
		verify(logger, times(2)).warning(contains("hours_before must be a number above 0"));
		verify(logger).warning(contains("two warnings at 6 h"));
	}

	@Test
	void anEmptyListTurnsTheTimedWarningsOffAndProblemsNeedNoLogger() throws InvalidConfigurationException {
		assertTrue(InstallationPickWarnings.fromConfig(yaml("before_lock: []\n"), null).beforeLock().isEmpty());
		InstallationPickWarnings parsed = InstallationPickWarnings.fromConfig(yaml("""
				before_lock:
				  - hours_before: -1
				  - hours_before: 1.5
				  - hours_before: 1.5
				"""), null);
		assertEquals(List.of(new Warning(Duration.ofMinutes(90), false)), parsed.beforeLock());
	}

	@Test
	void shortFormsAndMistakesAreHandled() throws InvalidConfigurationException {
		Logger logger = mock(Logger.class);
		InstallationPickWarnings parsed = InstallationPickWarnings.fromConfig(yaml("""
				at_time_lock: false
				before_lock: 3
				"""), logger);
		assertNull(parsed.atTimeLock(), "at_time_lock: false turns the line off");
		assertTrue(parsed.beforeLock().isEmpty());
		verify(logger).warning("[SimpleFactions] war.installation_warnings.before_lock must be a list;"
				+ " no warnings before the lock are sent.");

		parsed = InstallationPickWarnings.fromConfig(yaml("""
				at_time_lock: true
				before_lock:
				  - hours_before: 400
				  - hours_before: .inf
				  - hours_before: 336
				"""), logger);
		assertEquals(new Warning(Duration.ZERO, false), parsed.atTimeLock());
		assertEquals(List.of(new Warning(Duration.ofHours(336), false)), parsed.beforeLock());
		verify(logger, times(2)).warning(contains("at most 336"));
	}

	@Test
	void theWholeSectionCanBeWrittenAsTrueOrFalse() throws InvalidConfigurationException {
		Logger logger = mock(Logger.class);
		InstallationPickWarnings off = InstallationPickWarnings.load(
				yaml("war:\n  installation_warnings: false\n"), "war.installation_warnings", logger);
		assertFalse(off.enabled());
		assertEquals(InstallationPickWarnings.defaults().beforeLock(), off.beforeLock());
		assertEquals(InstallationPickWarnings.defaults(), InstallationPickWarnings.load(
				yaml("war:\n  installation_warnings: true\n"), "war.installation_warnings", logger));
		assertEquals(InstallationPickWarnings.defaults(), InstallationPickWarnings.load(
				yaml("war:\n  other: 1\n"), "war.installation_warnings", logger), "Missing: the defaults");
		assertEquals(InstallationPickWarnings.defaults(), InstallationPickWarnings.load(
				yaml("war:\n  installation_warnings: soon\n"), "war.installation_warnings", logger));
		verify(logger).warning("[SimpleFactions] war.installation_warnings must be a section, true or false;"
				+ " using the defaults.");
		InstallationPickWarnings section = InstallationPickWarnings.load(yaml("""
				war:
				  installation_warnings:
				    before_lock:
				      - hours_before: "2"
				        include_committed: "true"
				      - hours_before: 4
				        enabled: "false"
				      - 5
				"""), "war.installation_warnings", logger);
		assertEquals(List.of(new Warning(Duration.ofHours(2), true)), section.beforeLock(), "Quoted values work");
		verify(logger).warning(contains("entries need hours_before, got 5"));
	}

	@Test
	void duplicateWarningsAtHalfHoursAreNamedInHours() throws InvalidConfigurationException {
		Logger logger = mock(Logger.class);
		InstallationPickWarnings.fromConfig(yaml("""
				before_lock:
				  - hours_before: 1.5
				  - hours_before: 1.5
				"""), logger);
		verify(logger).warning(contains("two warnings at 1.5 h"));
	}
}
