package net.tfminecraft.simplefactions.database;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonUtilAtomicTest {
    @TempDir Path directory;

    @Test void failedSerializationPreservesThePreviousSaveAndRemovesTheStagedFile() throws Exception {
        Path file = directory.resolve("faction.json");
        Files.writeString(file, "{\"leader\":\"Old\"}");
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.writeJsonAtomic(file.toFile(), Map.of("invalid", Double.NaN)));
        assertEquals("{\"leader\":\"Old\"}", Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void completeSaveReplacesThePreviousData() throws Exception {
        Path file = directory.resolve("faction.json");
        Files.writeString(file, "{}");
        var permissions = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
        boolean posix = Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class) != null;
        if (posix) Files.setPosixFilePermissions(file, permissions);
        JsonUtil.writeJsonAtomic(file.toFile(), Map.of("leader", "New"));
        assertEquals("New", JsonUtil.readJson(file.toFile(), Map.class).get("leader"));
        if (posix) assertEquals(permissions, Files.getPosixFilePermissions(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
