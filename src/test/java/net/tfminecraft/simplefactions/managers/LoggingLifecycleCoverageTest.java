package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LoggingLifecycleCoverageTest {
  @TempDir Path temp;
  private final Map<Field, Object> original = new LinkedHashMap<>();
  private List<String> lines;
  private List<String> oldLines;
  private final List<String> warnings = new ArrayList<>();
  private Logger logger;
  private Handler handler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    for (String name :
        List.of(
            "enabled",
            "logFile",
            "relationsLogFile",
            "movementLogFile",
            "civilwarLogFile",
            "warLogFile")) {
      Field field = LogManager.class.getDeclaredField(name);
      field.setAccessible(true);
      original.put(field, field.get(null));
      if (name.equals("enabled")) field.setBoolean(null, false);
      else field.set(null, null);
    }
    Field field = LogManager.class.getDeclaredField("sessionLines");
    field.setAccessible(true);
    lines = (List<String>) field.get(null);
    oldLines = new ArrayList<>(lines);
    lines.clear();
    logger = Logger.getLogger(LogManager.class.getName());
    handler =
        new Handler() {
          public void publish(LogRecord record) {
            warnings.add(record.getMessage());
          }

          public void flush() {}

          public void close() {}
        };
    logger.addHandler(handler);
  }

  @AfterEach
  void close() throws Exception {
    logger.removeHandler(handler);
    lines.clear();
    lines.addAll(oldLines);
    for (var entry : original.entrySet()) entry.getKey().set(null, entry.getValue());
  }

  @Test
  void disabledOrUnconfiguredLoggingDoesNotCreateFilesOrThrow() {
    LogManager.configure(false, true, null);
    LogManager.beginSession("disabled");
    LogManager.section("disabled");
    LogManager.line("%s", "disabled");
    LogManager.append("disabled");
    LogManager.relations("%s", "disabled");
    LogManager.movement("%s", "disabled");
    LogManager.civilwar("%s", "disabled");
    LogManager.war("%s", "disabled");
    LogManager.flush();
    assertTrue(lines.isEmpty());
    assertTrue(warnings.isEmpty());
    LogManager.configure(true, false, null);
    LogManager.append("unconfigured");
    LogManager.relations("unconfigured");
    LogManager.movement("unconfigured");
    LogManager.flush();
    assertTrue(warnings.isEmpty());
  }

  @Test
  void formattedDomainsAndSessionSectionsAreWrittenOnce() throws Exception {
    LogManager.configure(true, false, temp.toFile());
    LogManager.beginSession("settlement");
    LogManager.section("Balances");
    LogManager.line("balance=%d", 12);
    LogManager.line((String) null);
    LogManager.flush();
    LogManager.flush();
    LogManager.relations("relation=%s", "ally");
    LogManager.movement("movement=%s", "cause");
    LogManager.civilwar("civilwar=%d", 3);
    LogManager.war("war=%d", 4);
    String log = Files.readString(temp.resolve("logs/log.txt"));
    assertTrue(log.contains("--- Balances ---"));
    assertEquals(1, log.split("balance=12", -1).length - 1);
    assertTrue(Files.readString(temp.resolve("logs/relations.log")).contains("relation=ally"));
    assertTrue(Files.readString(temp.resolve("logs/movement.log")).contains("movement=cause"));
    assertTrue(Files.readString(temp.resolve("logs/civilwar.log")).contains("civilwar=3"));
    assertTrue(Files.readString(temp.resolve("logs/war.log")).contains("war=4"));
  }

  @Test
  void writeFailuresReportTheFileAndDoNotThrowToTheGameplayCaller() throws Exception {
    Files.writeString(temp.resolve("logs"), "a file blocks the log directory");
    LogManager.configure(true, false, temp.toFile());
    assertDoesNotThrow(() -> LogManager.append("event"));
    assertTrue(warnings.stream().anyMatch(s -> s.startsWith("Failed to write log.txt:")));
    assertEquals("a file blocks the log directory", Files.readString(temp.resolve("logs")));
  }

  @Test
  void failedWipePreservesUnexpectedDirectoryContentsAndReportsFailure() throws Exception {
    Path nested = temp.resolve("logs/log.txt/keep.txt");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "keep");
    assertDoesNotThrow(() -> LogManager.configure(true, true, temp.toFile()));
    assertEquals("keep", Files.readString(nested));
    assertTrue(warnings.stream().anyMatch(s -> s.startsWith("Failed to wipe log.txt:")));
  }
}
