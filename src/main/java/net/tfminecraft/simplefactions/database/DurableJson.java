package net.tfminecraft.simplefactions.database;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Durable intent files: sync the new content and the directory entry before any dependent effect. */
public final class DurableJson {
    /** Directories whose own entry and every parent entry this process has already synced. */
    private static final Set<Path> syncedParents = ConcurrentHashMap.newKeySet();
    /** Contents a batch syncs at the same time, so the filesystem can share journal commits between them. */
    static final int PARALLEL_SYNCS = 8;
    /** Temporary files a batch keeps open at once. */
    static final int OPEN_FILES = 64;

    private DurableJson() {}

    public static void write(Path file, Object data) throws IOException {
        write(file, data, JsonUtil.GSON);
    }

    /** A raw-document writer may retain explicit null fields without changing intent schemas. */
    public static void write(Path file, Object data, com.google.gson.Gson serializer) throws IOException {
        syncDirectory(replace(file, data, serializer));
    }

    /**
     * Independent durable replacements that share their syncs. {@link #write} takes the file's new
     * content at once but leaves the file alone. {@link #commit()} writes every content to a
     * temporary file, syncs those contents in parallel, renames each over its file and syncs each
     * directory once. Each content is synced before its rename, so a crash leaves every file whole,
     * old or new. The files change only in {@link #commit()} and are durable once it returns;
     * nothing may depend on them before that. A failed commit keeps what it did not finish for a retry.
     */
    public static final class Batch {
        private final Map<Path, byte[]> pending = new LinkedHashMap<>();
        private final Set<Path> directories = new LinkedHashSet<>();

        public void write(Path file, Object data) throws IOException {
            pending.put(file.toAbsolutePath(), JsonUtil.GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
        }

        public void commit() throws IOException {
            List<Path> files = new ArrayList<>(pending.keySet());
            for (int start = 0; start < files.size(); start += OPEN_FILES) {
                replaceAll(files.subList(start, Math.min(files.size(), start + OPEN_FILES)));
            }
            for (Path directory : directories) syncDirectory(directory);
            directories.clear();
        }

        private void replaceAll(List<Path> files) throws IOException {
            List<Path> temporary = new ArrayList<>(files.size());
            try {
                List<FileChannel> channels = new ArrayList<>(files.size());
                try {
                    for (Path file : files) {
                        Path pendingFile = Files.createTempFile(directory(file), file.getFileName().toString() + ".", ".tmp");
                        temporary.add(pendingFile);
                        FileChannel channel = FileChannel.open(pendingFile, StandardOpenOption.WRITE);
                        channels.add(channel);
                        ByteBuffer bytes = ByteBuffer.wrap(pending.get(file));
                        while (bytes.hasRemaining()) channel.write(bytes);
                    }
                    syncAll(channels);
                } finally {
                    // Closed before the renames: Windows cannot rename a file that is still open.
                    for (FileChannel channel : channels) channel.close();
                }
                for (int i = 0; i < files.size(); i++) {
                    Files.move(temporary.get(i), files.get(i), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    directories.add(files.get(i).getParent());
                    pending.remove(files.get(i));
                }
            } finally {
                for (Path pendingFile : temporary) Files.deleteIfExists(pendingFile);
            }
        }
    }

    /**
     * Syncs every channel's content and returns once every sync has ended. One after another, each
     * sync waited for its own filesystem journal commit, so the daily settlement's faction saves
     * held the server thread for the sum of them. Syncs that run together can share a commit.
     */
    private static void syncAll(List<FileChannel> channels) throws IOException {
        if (channels.size() == 1) {
            channels.getFirst().force(true);
            return;
        }
        ExecutorService syncs = Executors.newFixedThreadPool(Math.min(channels.size(), PARALLEL_SYNCS), task -> {
            Thread thread = new Thread(task, "SimpleFactions durable sync");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<Void>> running = new ArrayList<>(channels.size());
            for (FileChannel channel : channels) running.add(CompletableFuture.runAsync(() -> sync(channel), syncs));
            IOException failure = null;
            for (CompletableFuture<Void> sync : running) {
                try {
                    sync.join();
                } catch (CompletionException error) {
                    if (failure == null) failure = error.getCause() instanceof UncheckedIOException io ? io.getCause() : new IOException(error.getCause());
                }
            }
            if (failure != null) throw failure;
        } finally {
            syncs.shutdown();
        }
    }

    private static void sync(FileChannel channel) {
        try {
            channel.force(true);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** The file's directory, created if it is missing. */
    private static Path directory(Path destination) throws IOException {
        Path directory = destination.getParent();
        // A directory created here (again) has a new, unsynced entry in its parent.
        if (!Files.isDirectory(directory)) syncedParents.remove(directory);
        Files.createDirectories(directory);
        return directory;
    }

    private static Path replace(Path file, Object data, com.google.gson.Gson serializer) throws IOException {
        Path destination = file.toAbsolutePath();
        Path directory = directory(destination);
        Path pending = Files.createTempFile(directory, destination.getFileName().toString() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(serializer.toJson(data));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(pending);
        }
        return directory;
    }

    private static void syncDirectory(Path directory) throws IOException {
        force(directory);
        if (syncedParents.contains(directory)) return;
        // A previous failed attempt or run may already have created an unsynced parent entry.
        for (Path path = directory.getParent(); path != null; path = path.getParent()) force(path);
        syncedParents.add(directory);
    }

    private static void force(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        try {
            T value = JsonUtil.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
            if (value == null) throw new IOException("Empty intent file: " + file);
            return value;
        } catch (com.google.gson.JsonParseException error) {
            throw new IOException("Invalid intent file: " + file, error);
        }
    }
}
