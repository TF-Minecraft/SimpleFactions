package me.Plugins.SimpleFactions.Managers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Buffered debug log written to {@code logs/log.txt} in the plugin data folder.
 * Domain files: {@code relations.log}, {@code movement.log}, {@code civilwar.log}, {@code war.log}.
 * All files under {@code logs/} are deleted when {@code wipe-log} is true.
 */
public final class LogManager {
	private static final Logger LOGGER = Logger.getLogger(LogManager.class.getName());
	private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());
	static final String LOG_DIRECTORY = "logs";
	static final String LOG_FILE_NAME = "log.txt";
	static final String RELATIONS_LOG_FILE_NAME = "relations.log";
	static final String MOVEMENT_LOG_FILE_NAME = "movement.log";
	static final String CIVILWAR_LOG_FILE_NAME = "civilwar.log";
	static final String WAR_LOG_FILE_NAME = "war.log";

	private static volatile boolean enabled;
	private static volatile Path logFile;
	private static volatile Path relationsLogFile;
	private static volatile Path movementLogFile;
	private static volatile Path civilwarLogFile;
	private static volatile Path warLogFile;
	private static final Object LOCK = new Object();
	private static final List<String> sessionLines = new ArrayList<>();

	private LogManager() {
	}

	public static void configure(boolean loggingEnabled, boolean wipeLog, File dataFolder) {
		enabled = loggingEnabled;
		if (dataFolder != null) {
			Path logDir = dataFolder.toPath().resolve(LOG_DIRECTORY);
			logFile = logDir.resolve(LOG_FILE_NAME);
			relationsLogFile = logDir.resolve(RELATIONS_LOG_FILE_NAME);
			movementLogFile = logDir.resolve(MOVEMENT_LOG_FILE_NAME);
			civilwarLogFile = logDir.resolve(CIVILWAR_LOG_FILE_NAME);
			warLogFile = logDir.resolve(WAR_LOG_FILE_NAME);
		}
		if (wipeLog) {
			wipeLogFile();
			wipeRelationsLogFile();
			deleteLog(movementLogFile, MOVEMENT_LOG_FILE_NAME);
			deleteLog(civilwarLogFile, CIVILWAR_LOG_FILE_NAME);
			deleteLog(warLogFile, WAR_LOG_FILE_NAME);
		}
	}

	private static void wipeLogFile() {
		deleteLog(logFile, "log.txt");
		synchronized (LOCK) {
			sessionLines.clear();
		}
	}

	private static void wipeRelationsLogFile() {
		deleteLog(relationsLogFile, "relations.log");
	}

	private static void deleteLog(Path file, String label) {
		if (file == null) {
			return;
		}
		synchronized (LOCK) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException exception) {
				LOGGER.warning("Failed to wipe " + label + ": " + exception.getMessage());
			}
		}
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void beginSession(String title) {
		if (!enabled) {
			return;
		}
		synchronized (LOCK) {
			sessionLines.clear();
			sessionLines.add("===== " + SESSION_TIME.format(Instant.now()) + " " + title + " =====");
		}
	}

	public static void section(String header) {
		if (!enabled) {
			return;
		}
		synchronized (LOCK) {
			sessionLines.add("");
			sessionLines.add("--- " + header + " ---");
		}
	}

	public static void line(String message) {
		if (!enabled || message == null) {
			return;
		}
		synchronized (LOCK) {
			sessionLines.add(message);
		}
	}

	public static void line(String format, Object... args) {
		if (!enabled) {
			return;
		}
		line(String.format(format, args));
	}

	/** Append a single line immediately (no session buffer). */
	public static void append(String message) {
		if (!enabled || message == null || logFile == null) {
			return;
		}
		writeLines(logFile, List.of(SESSION_TIME.format(Instant.now()) + " " + message), false);
	}

	/** Immediate line in {@code logs/relations.log}. Same {@code logging} / {@code wipe-log} as other log files. */
	public static void relations(String message) {
		if (!enabled || message == null || relationsLogFile == null) {
			return;
		}
		writeLines(relationsLogFile, List.of(SESSION_TIME.format(Instant.now()) + " " + message), false);
	}

	public static void relations(String format, Object... args) {
		if (!enabled) {
			return;
		}
		relations(String.format(format, args));
	}

	public static void movement(String message) {
		appendDomain(movementLogFile, message);
	}

	public static void movement(String format, Object... args) {
		if (!enabled) {
			return;
		}
		movement(String.format(format, args));
	}

	public static void civilwar(String message) {
		appendDomain(civilwarLogFile, message);
	}

	public static void civilwar(String format, Object... args) {
		if (!enabled) {
			return;
		}
		civilwar(String.format(format, args));
	}

	public static void war(String message) {
		appendDomain(warLogFile, message);
	}

	public static void war(String format, Object... args) {
		if (!enabled) {
			return;
		}
		war(String.format(format, args));
	}

	private static void appendDomain(Path file, String message) {
		if (!enabled || message == null || file == null) {
			return;
		}
		writeLines(file, List.of(SESSION_TIME.format(Instant.now()) + " " + message), false);
	}

	public static void flush() {
		if (!enabled || logFile == null) {
			return;
		}
		synchronized (LOCK) {
			if (sessionLines.isEmpty()) {
				return;
			}
			writeLines(logFile, sessionLines, true);
			sessionLines.clear();
		}
	}

	private static void writeLines(Path file, List<String> lines, boolean trailingBlankLine) {
		try {
			Files.createDirectories(file.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(
					file,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND)) {
				for (String line : lines) {
					writer.write(line);
					writer.newLine();
				}
				if (trailingBlankLine) {
					writer.newLine();
				}
			}
		} catch (IOException exception) {
			LOGGER.warning("Failed to write " + file.getFileName() + ": " + exception.getMessage());
		}
	}
}
