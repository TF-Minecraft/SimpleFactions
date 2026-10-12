package net.tfminecraft.simplefactions.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DurableJsonTest {
    @TempDir Path directory;

    @Test
    void durableReplacementReadsBackAndLeavesNoTemporaryFiles() throws Exception {
        Path file = directory.resolve("intents/day.json");
        DurableJson.write(file, Map.of("amount", 123, "name", "Éowyn"));
        assertEquals("Éowyn", DurableJson.read(file, Map.class).get("name"));
        DurableJson.write(file, Map.of("amount", 456));
        assertEquals(456.0, DurableJson.read(file, Map.class).get("amount"));
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    @Test
    void corruptAndEmptyIntentsFailClosed() throws Exception {
        Path file = directory.resolve("day.json");
        Files.writeString(file, "null");
        assertThrows(IOException.class, () -> DurableJson.read(file, Map.class));
        Files.writeString(file, "{");
        assertThrows(IOException.class, () -> DurableJson.read(file, Map.class));
        assertThrows(IOException.class, () -> DurableJson.read(directory.resolve("missing"), Map.class));
    }

    @Test
    void failedReplacementPreservesExistingDirectoryAndCleansTemporaryFile() throws Exception {
        Path destination = directory.resolve("existing");
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("original"), "keep");
        assertThrows(IOException.class, () -> DurableJson.write(destination, Map.of("amount", 1)));
        assertEquals("keep", Files.readString(destination.resolve("original")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
