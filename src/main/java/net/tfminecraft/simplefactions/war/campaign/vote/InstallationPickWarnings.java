package net.tfminecraft.simplefactions.war.campaign.vote;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

/**
 * {@code war.installation_warnings}: when faction leaders are warned that their installation
 * choices for a battle are about to lock. {@code at_time_lock} is the line sent when the battle's
 * start time is decided; {@code before_lock} lists the warnings sent a set time before the choices
 * lock ({@code battle_schedule.vote_close_hour} on the battle day). Disabled warnings are dropped
 * here, so the rest of the plugin only sees the ones that are on.
 */
public record InstallationPickWarnings(boolean enabled, Warning atTimeLock, List<Warning> beforeLock) {
	/** One warning. {@code before} is how long before the lock; it is zero for {@code at_time_lock}. */
	public record Warning(Duration before, boolean includeCommitted) {
	}

	public InstallationPickWarnings {
		beforeLock = List.copyOf(beforeLock);
	}

	/** Used when war.yml has no {@code installation_warnings} section: 3 h before, then 1 h before to every leader. */
	public static InstallationPickWarnings defaults() {
		return new InstallationPickWarnings(true, new Warning(Duration.ZERO, false), List.of(
				new Warning(Duration.ofHours(3), false),
				new Warning(Duration.ofHours(1), true)));
	}

	public static InstallationPickWarnings fromConfig(ConfigurationSection section, Logger logger) {
		if (section == null) {
			return defaults();
		}
		boolean enabled = section.getBoolean("enabled", true);
		Warning atTimeLock = null;
		ConfigurationSection timeLock = section.getConfigurationSection("at_time_lock");
		if (timeLock == null || timeLock.getBoolean("enabled", true)) {
			atTimeLock = new Warning(Duration.ZERO,
					timeLock != null && timeLock.getBoolean("include_committed", false));
		}
		List<Warning> beforeLock = new ArrayList<>();
		if (!section.contains("before_lock")) {
			beforeLock.addAll(defaults().beforeLock());
		} else {
			for (Map<?, ?> entry : section.getMapList("before_lock")) {
				Warning warning = parseWarning(entry, logger);
				if (warning != null) {
					beforeLock.add(warning);
				}
			}
		}
		// Earliest first; two warnings at the same time would only ever send one.
		beforeLock.sort(Comparator.comparing(Warning::before).reversed());
		List<Warning> distinct = new ArrayList<>();
		for (Warning warning : beforeLock) {
			if (distinct.isEmpty() || !distinct.getLast().before().equals(warning.before())) {
				distinct.add(warning);
			} else if (logger != null) {
				logger.warning("war.installation_warnings.before_lock has two warnings at "
						+ hours(warning.before()) + " h; only the first is used.");
			}
		}
		return new InstallationPickWarnings(enabled, atTimeLock, distinct);
	}

	private static Warning parseWarning(Map<?, ?> entry, Logger logger) {
		if (Boolean.FALSE.equals(entry.get("enabled"))) {
			return null;
		}
		Object hours = entry.get("hours_before");
		if (!(hours instanceof Number number) || !(number.doubleValue() > 0)) {
			if (logger != null) {
				logger.warning("war.installation_warnings.before_lock: hours_before must be a number above 0, got "
						+ hours + "; that warning is skipped.");
			}
			return null;
		}
		long minutes = Math.max(1, Math.round(number.doubleValue() * 60));
		return new Warning(Duration.ofMinutes(minutes), Boolean.TRUE.equals(entry.get("include_committed")));
	}

	private static String hours(Duration duration) {
		long minutes = duration.toMinutes();
		return minutes % 60 == 0 ? String.valueOf(minutes / 60) : String.valueOf(minutes / 60.0);
	}
}
