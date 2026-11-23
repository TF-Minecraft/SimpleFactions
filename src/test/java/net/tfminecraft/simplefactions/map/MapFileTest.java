package net.tfminecraft.simplefactions.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class MapFileTest {
    @TempDir Path dir;

    private static JsonObject payload() {
        JsonObject o = new JsonObject(); o.addProperty("id", 7); o.add("list", new JsonArray()); return o;
    }

    @Test void writesPrettyOrCompactJsonIntoNewFoldersAndLeavesNoTemporaryFile() throws Exception {
        Path pretty = dir.resolve("MapAPI/a.json"), compact = dir.resolve("MapAPI/b.json");
        new MapFile(pretty.toFile(), payload(), true).write();
        new MapFile(compact.toFile(), payload(), false).write();
        assertTrue(Files.readString(pretty).contains("\n"));
        assertEquals("{\"id\":7,\"list\":[]}", Files.readString(compact));
        assertEquals(7, JsonParser.parseString(Files.readString(pretty)).getAsJsonObject().get("id").getAsInt());
        // A later write replaces the file in one move.
        JsonObject next = new JsonObject(); next.addProperty("id", 8);
        new MapFile(compact.toFile(), next, false).write();
        assertEquals("{\"id\":8}", Files.readString(compact));
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            // Same mode as any plain new file in that folder (not owner-only).
            Path plain = Files.writeString(dir.resolve("plain.txt"), "x");
            assertEquals(Files.getPosixFilePermissions(plain), Files.getPosixFilePermissions(compact));
            Files.delete(plain);
        }
        try (var files = Files.list(dir.resolve("MapAPI"))) {
            assertEquals(List.of("a.json", "b.json"), files.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test void fallsBackToAPlainReplaceWhereAnAtomicMoveIsNotSupported() throws Exception {
        Path target = dir.resolve("c.json");
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(any(Path.class), any(Path.class), any(CopyOption[].class))).thenAnswer(call -> {
                Object[] options = call.getRawArguments().length > 2 ? (Object[]) call.getRawArguments()[2] : new Object[0];
                if (List.of(options).contains(java.nio.file.StandardCopyOption.ATOMIC_MOVE)) {
                    throw new AtomicMoveNotSupportedException("a", "b", "no atomic moves here");
                }
                return call.callRealMethod();
            });
            new MapFile(target.toFile(), payload(), false).write();
        }
        assertEquals("{\"id\":7,\"list\":[]}", Files.readString(target));
    }

    @Test void aFailingWriteIsLoggedRemovesItsTemporaryFileAndTheOthersStillWrite() throws Exception {
        Path blocked = dir.resolve("blocked.json");
        Files.createDirectories(blocked.resolve("inside"));
        Path ok = dir.resolve("ok.json");
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(logger);
            MapSystem.writeAll(List.of(new MapFile(blocked.toFile(), payload(), false), new MapFile(ok.toFile(), payload(), false)));
        }
        verify(logger).severe("[SimpleFactions] Failed to export blocked.json");
        assertTrue(Files.isRegularFile(ok));
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test void aPayloadThatCannotBeBuiltIsLoggedAndSkipped() {
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(logger);
            assertEquals(List.of(), MapSystem.live("guilds", () -> { throw new IllegalStateException("no guilds"); }, true));
            List<MapFile> built = MapSystem.live("guilds", JsonArray::new, true);
            assertEquals(new File("plugins/SimpleFactions/MapAPI/guilds.json"), built.get(0).file());
        }
        verify(logger).severe("[SimpleFactions] Failed to export guilds.json");
    }
}
