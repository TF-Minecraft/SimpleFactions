package net.tfminecraft.simplefactions.database;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    void parentEntriesAreSyncedOnceUntilTheDirectoryIsCreatedAgain() throws Exception {
        Path folder = directory.toAbsolutePath().resolve("chain/intents");
        List<Path> synced = new ArrayList<>();
        try (var channels = syncedDirectories(synced, path -> false)) {
            DurableJson.write(folder.resolve("a.json"), Map.of("n", 1));
            assertEquals(chain(folder), synced);
            synced.clear();
            DurableJson.write(folder.resolve("b.json"), Map.of("n", 2));
            assertEquals(List.of(folder), synced);

            // A recreated directory has an unsynced entry in its parent again.
            for (String name : List.of("a.json", "b.json")) Files.delete(folder.resolve(name));
            Files.delete(folder);
            synced.clear();
            DurableJson.write(folder.resolve("a.json"), Map.of("n", 3));
            assertEquals(chain(folder), synced);
        }
        assertEquals(3.0, DurableJson.read(folder.resolve("a.json"), Map.class).get("n"));
    }

    @Test
    void batchReplacesEveryFileAndSyncsEachDirectoryOnceOnCommit() throws Exception {
        Path first = directory.toAbsolutePath().resolve("batch/data");
        Path second = directory.toAbsolutePath().resolve("batch/cache");
        List<Path> synced = new ArrayList<>();
        try (var channels = syncedDirectories(synced, path -> false)) {
            var batch = new DurableJson.Batch();
            batch.write(first.resolve("a.json"), Map.of("n", 1));
            batch.write(first.resolve("b.json"), Map.of("n", 2));
            batch.write(second.resolve("c.json"), Map.of("n", 3));
            assertTrue(synced.isEmpty(), "No directory is synced before commit");
            assertEquals(2.0, DurableJson.read(first.resolve("b.json"), Map.class).get("n"));
            batch.commit();
            var expected = new ArrayList<>(chain(first));
            expected.addAll(chain(second));
            assertEquals(expected, synced);

            synced.clear();
            batch.commit();
            assertTrue(synced.isEmpty(), "A committed batch has nothing left to sync");
            batch.write(first.resolve("a.json"), Map.of("n", 4));
            batch.write(first.resolve("b.json"), Map.of("n", 5));
            batch.commit();
            assertEquals(List.of(first), synced);
        }
        try (var files = Files.list(first)) { assertEquals(2, files.count()); }
    }

    @Test
    void failedCommitKeepsItsDirectoriesForARetry() throws Exception {
        Path folder = directory.toAbsolutePath().resolve("retry");
        List<Path> synced = new ArrayList<>();
        boolean[] failing = {true};
        try (var channels = syncedDirectories(synced, path -> failing[0] && path.equals(folder))) {
            var batch = new DurableJson.Batch();
            batch.write(folder.resolve("a.json"), Map.of("n", 1));
            assertThrows(IOException.class, batch::commit);
            failing[0] = false;
            batch.commit();
            assertEquals(chain(folder), synced);
        }
    }

    /** Records directory syncs instead of opening directories, which Windows refuses. */
    private static MockedStatic<FileChannel> syncedDirectories(List<Path> synced, Predicate<Path> fail) {
        MockedStatic<FileChannel> channels = mockStatic(FileChannel.class, CALLS_REAL_METHODS);
        channels.when(() -> FileChannel.open(any(Path.class), any(OpenOption[].class))).thenAnswer(call -> {
            Path path = call.getArgument(0);
            if (!Arrays.asList(call.getArguments()).contains(StandardOpenOption.READ)) return call.callRealMethod();
            if (fail.test(path)) throw new IOException("simulated directory sync failure: " + path);
            synced.add(path);
            return mock(FileChannel.class);
        });
        return channels;
    }

    private static List<Path> chain(Path folder) {
        List<Path> chain = new ArrayList<>();
        for (Path path = folder; path != null; path = path.getParent()) chain.add(path);
        return chain;
    }
}
