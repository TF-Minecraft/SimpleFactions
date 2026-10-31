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
            assertFalse(Files.exists(first), "No file changes before commit");
            batch.commit();
            assertEquals(2.0, DurableJson.read(first.resolve("b.json"), Map.class).get("n"));
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

    @Test
    void aFailedContentSyncRenamesNothingAndKeepsEveryFileForARetry() throws Exception {
        Path folder = directory.toAbsolutePath().resolve("contents");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("a.json"), "{\"n\":0}");
        var batch = new DurableJson.Batch();
        batch.write(folder.resolve("a.json"), Map.of("n", 1));
        batch.write(folder.resolve("b.json"), Map.of("n", 2));
        batch.write(folder.resolve("c.json"), Map.of("n", 3));
        List<Path> synced = new ArrayList<>();
        try (var channels = syncedDirectories(synced, path -> false)) {
            failContentSync(channels, synced, Map.of("a.json", new IllegalStateException("simulated"), "b.json", new IOException("simulated")));
            IOException first = assertThrows(IOException.class, batch::commit);
            assertInstanceOf(IllegalStateException.class, first.getCause(), "The first file's failure is reported");
            assertUntouched(folder, synced);

            IOException failed = new IOException("simulated content sync failure");
            failContentSync(channels, synced, Map.of("c.json", failed));
            assertSame(failed, assertThrows(IOException.class, batch::commit));
            assertUntouched(folder, synced);

            failContentSync(channels, synced, Map.of());
            batch.commit();
            assertEquals(chain(folder), synced);
        }
        assertEquals(1.0, DurableJson.read(folder.resolve("a.json"), Map.class).get("n"));
        assertEquals(3.0, DurableJson.read(folder.resolve("c.json"), Map.class).get("n"));
        try (var files = Files.list(folder)) { assertEquals(3, files.count()); }
    }

    @Test
    void aBatchLargerThanItsOpenFileLimitReplacesEveryFile() throws Exception {
        Path folder = directory.toAbsolutePath().resolve("many");
        var batch = new DurableJson.Batch();
        int count = DurableJson.OPEN_FILES * 2 + 1;
        for (int n = 0; n < count; n++) batch.write(folder.resolve(n + ".json"), Map.of("n", n));
        List<Path> synced = new ArrayList<>();
        try (var channels = syncedDirectories(synced, path -> false)) {
            batch.commit();
        }
        assertEquals(chain(folder), synced, "One directory sync for the whole batch");
        for (int n = 0; n < count; n++) assertEquals((double) n, DurableJson.read(folder.resolve(n + ".json"), Map.class).get("n"));
        try (var files = Files.list(folder)) { assertEquals(count, files.count()); }
    }

    @Test
    void everyChannelIsClosedAndTheFirstCloseFailureIsThrown() throws Exception {
        FileChannel first = mock(FileChannel.class), open = mock(FileChannel.class), last = mock(FileChannel.class);
        IOException failed = new IOException("first close"), later = new IOException("last close");
        doThrow(failed).when(first).close();
        doThrow(later).when(last).close();
        assertSame(failed, assertThrows(IOException.class, () -> DurableJson.Batch.closeAll(List.of(first, open, last), null)));
        assertArrayEquals(new Throwable[] {later}, failed.getSuppressed());
        verify(open).close();
        verify(last).close();
        DurableJson.Batch.closeAll(List.of(open), null);
        IOException sync = new IOException("sync");
        DurableJson.Batch.closeAll(List.of(first), sync);
        assertEquals(1, sync.getSuppressed().length, "A close failure never hides the write failure");
    }

    @Test
    void cleanupGoesOnPastAFileItCannotDeleteAndNeverHidesTheCommitFailure() throws Exception {
        Path stuck = Files.createDirectories(directory.resolve("stuck"));
        Files.writeString(stuck.resolve("keep"), "x");
        Path other = Files.createDirectories(directory.resolve("other"));
        Files.writeString(other.resolve("keep"), "x");
        Path loose = Files.writeString(directory.resolve("loose.tmp"), "x");
        IOException commit = new IOException("commit");
        DurableJson.Batch.deleteAll(List.of(stuck, loose), commit);
        assertFalse(Files.exists(loose), "Files after the stuck one are still removed");
        assertEquals(1, commit.getSuppressed().length);
        IOException thrown = assertThrows(IOException.class, () -> DurableJson.Batch.deleteAll(List.of(stuck, other), null));
        assertEquals(1, thrown.getSuppressed().length, "The second cleanup failure rides on the first");
    }

    private static void assertUntouched(Path folder, List<Path> synced) throws IOException {
        assertEquals("{\"n\":0}", Files.readString(folder.resolve("a.json")));
        assertTrue(synced.isEmpty(), "No directory is synced after a failed content sync");
        try (var files = Files.list(folder)) { assertEquals(1, files.count(), "No file renamed, no temporary file left"); }
    }

    /** Fails the content sync of the named files, on whichever thread runs it. */
    private static void failContentSync(MockedStatic<FileChannel> channels, List<Path> synced, Map<String, Exception> failures) {
        channels.when(() -> FileChannel.open(any(Path.class), any(OpenOption[].class))).thenAnswer(call -> {
            Path path = call.getArgument(0);
            if (Arrays.asList(call.getArguments()).contains(StandardOpenOption.READ)) {
                FileChannel directory = mock(FileChannel.class);
                doAnswer(force -> synced.add(path)).when(directory).force(true);
                return directory;
            }
            String name = path.getFileName().toString();
            String file = name.substring(0, name.indexOf(".json") + ".json".length());
            if (!failures.containsKey(file)) return call.callRealMethod();
            FileChannel channel = mock(FileChannel.class);
            when(channel.write(any(java.nio.ByteBuffer.class))).thenAnswer(write -> {
                java.nio.ByteBuffer bytes = write.getArgument(0);
                int length = bytes.remaining();
                bytes.position(bytes.limit());
                return length;
            });
            doThrow(failures.get(file)).when(channel).force(true);
            return channel;
        });
    }

    /** Records directory syncs (a forced directory channel) instead of opening directories, which Windows refuses. */
    private static MockedStatic<FileChannel> syncedDirectories(List<Path> synced, Predicate<Path> fail) {
        MockedStatic<FileChannel> channels = mockStatic(FileChannel.class, CALLS_REAL_METHODS);
        channels.when(() -> FileChannel.open(any(Path.class), any(OpenOption[].class))).thenAnswer(call -> {
            Path path = call.getArgument(0);
            if (!Arrays.asList(call.getArguments()).contains(StandardOpenOption.READ)) return call.callRealMethod();
            if (fail.test(path)) throw new IOException("simulated directory sync failure: " + path);
            // A sync counts only when the directory is forced, not merely opened.
            FileChannel channel = mock(FileChannel.class);
            doAnswer(force -> synced.add(path)).when(channel).force(true);
            return channel;
        });
        return channels;
    }

    private static List<Path> chain(Path folder) {
        List<Path> chain = new ArrayList<>();
        for (Path path = folder; path != null; path = path.getParent()) chain.add(path);
        return chain;
    }
}
