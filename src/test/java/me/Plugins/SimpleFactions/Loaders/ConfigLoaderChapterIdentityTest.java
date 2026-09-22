package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.export.ChapterIdentity;

class ConfigLoaderChapterIdentityTest {
	private Path tempDir;
	private String previousChapterId;
	private String previousChapterName;
	private String previousMapRef;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-config-chapter-");
		previousChapterId = Cache.chapterId;
		previousChapterName = Cache.chapterName;
		previousMapRef = Cache.mapRef;
	}

	@AfterEach
	void tearDown() throws IOException {
		Cache.chapterId = previousChapterId;
		Cache.chapterName = previousChapterName;
		Cache.mapRef = previousMapRef;
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void loadConfig_readsValidChapterKeys() throws IOException {
		Path file = writeConfig("""
				map-reference: main
				map-id: Vardera
				map-name:  Vardera
				""");

		new ConfigLoader().loadConfig(file.toFile());

		assertEquals("main", Cache.mapRef);
		assertEquals("vardera", Cache.chapterId);
		assertEquals("Vardera", Cache.chapterName);
	}

	@Test
	void loadConfig_omittedChapterKeysAreUnknown() throws IOException {
		Path file = writeConfig("""
				map-reference: main
				""");

		new ConfigLoader().loadConfig(file.toFile());

		assertEquals(ChapterIdentity.UNKNOWN_ID, Cache.chapterId);
		assertEquals(ChapterIdentity.UNKNOWN_NAME, Cache.chapterName);
	}

	@Test
	void loadConfig_invalidSlugIsUnknownWithoutInventingName() throws IOException {
		Path file = writeConfig("""
				map-id: var_dera
				map-name: Still Named
				""");

		new ConfigLoader().loadConfig(file.toFile());

		assertEquals(ChapterIdentity.UNKNOWN_ID, Cache.chapterId);
		assertEquals("Still Named", Cache.chapterName);
	}

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
