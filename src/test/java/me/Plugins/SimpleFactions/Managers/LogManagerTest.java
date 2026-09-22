package me.Plugins.SimpleFactions.Managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogManagerTest {

	@TempDir
	Path tempDir;

	@Test
	void flush_writesSessionWhenEnabled() throws Exception {
		LogManager.configure(true, false, tempDir.toFile());
		LogManager.beginSession("test-session");
		LogManager.line("hello");
		LogManager.flush();

		Path logFile = tempDir.resolve("logs").resolve("log.txt");
		assertTrue(Files.exists(logFile));
		String content = Files.readString(logFile);
		assertTrue(content.contains("test-session"));
		assertTrue(content.contains("hello"));
	}

	@Test
	void flush_noOpWhenDisabled() throws Exception {
		LogManager.configure(false, false, tempDir.toFile());
		LogManager.beginSession("test-session");
		LogManager.line("hello");
		LogManager.flush();

		assertEquals(false, Files.exists(tempDir.resolve("logs").resolve("log.txt")));
	}

	@Test
	void append_writesImmediatelyWhenEnabled() throws Exception {
		LogManager.configure(true, false, tempDir.toFile());
		LogManager.append("one-off");

		String content = Files.readString(tempDir.resolve("logs").resolve("log.txt"));
		assertTrue(content.contains("one-off"));
	}

	@Test
	void configure_wipeLog_deletesExistingFile() throws Exception {
		Path logFile = tempDir.resolve("logs").resolve("log.txt");
		Path relationsFile = tempDir.resolve("logs").resolve("relations.log");
		Files.createDirectories(logFile.getParent());
		Files.writeString(logFile, "old content");
		Files.writeString(relationsFile, "old relations");
		LogManager.configure(false, true, tempDir.toFile());

		assertEquals(false, Files.exists(logFile));
		assertEquals(false, Files.exists(relationsFile));
	}

	@Test
	void configure_wipeLog_deletesDomainLogs() throws Exception {
		Path movementFile = tempDir.resolve("logs").resolve("movement.log");
		Path civilwarFile = tempDir.resolve("logs").resolve("civilwar.log");
		Path warFile = tempDir.resolve("logs").resolve("war.log");
		Files.createDirectories(movementFile.getParent());
		Files.writeString(movementFile, "old movement");
		Files.writeString(civilwarFile, "old civilwar");
		Files.writeString(warFile, "old war");
		LogManager.configure(false, true, tempDir.toFile());

		assertEquals(false, Files.exists(movementFile));
		assertEquals(false, Files.exists(civilwarFile));
		assertEquals(false, Files.exists(warFile));
	}

	@Test
	void relations_writesImmediatelyWhenEnabled() throws Exception {
		LogManager.configure(true, false, tempDir.toFile());
		LogManager.relations("Lantan -> Invaders subject");

		String content = Files.readString(tempDir.resolve("logs").resolve("relations.log"));
		assertTrue(content.contains("Lantan -> Invaders subject"));
	}

	@Test
	void relations_noOpWhenDisabled() throws Exception {
		LogManager.configure(false, false, tempDir.toFile());
		LogManager.relations("should-not-write");

		assertEquals(false, Files.exists(tempDir.resolve("logs").resolve("relations.log")));
	}

	@Test
	void domainLogs_writeImmediatelyWhenEnabled() throws Exception {
		LogManager.configure(true, false, tempDir.toFile());
		LogManager.movement("movementId=m1 created");
		LogManager.civilwar("movementId=m1 split");
		LogManager.war("warId=9 declared");

		assertTrue(Files.readString(tempDir.resolve("logs").resolve("movement.log")).contains("movementId=m1"));
		assertTrue(Files.readString(tempDir.resolve("logs").resolve("civilwar.log")).contains("split"));
		assertTrue(Files.readString(tempDir.resolve("logs").resolve("war.log")).contains("warId=9"));
	}
}
