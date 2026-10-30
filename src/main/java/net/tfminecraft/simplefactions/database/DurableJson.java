package net.tfminecraft.simplefactions.database;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Durable intent files: sync the new content and the directory entry before any dependent effect. */
public final class DurableJson {
    /** Directories whose own entry and every parent entry this process has already synced. */
    private static final Set<Path> syncedParents = ConcurrentHashMap.newKeySet();

    private DurableJson() {}

    public static void write(Path file, Object data) throws IOException {
        write(file, data, JsonUtil.GSON);
    }

    /** A raw-document writer may retain explicit null fields without changing intent schemas. */
    public static void write(Path file, Object data, com.google.gson.Gson serializer) throws IOException {
        syncDirectory(replace(file, data, serializer));
    }

    /**
     * Independent durable replacements that share one sync per directory. Each file's content is
     * synced before its rename, so a crash leaves every file whole, old or new. The renames are
     * durable only once {@link #commit()} returns; nothing may depend on them before that.
     */
    public static final class Batch {
        private final Set<Path> directories = new LinkedHashSet<>();

        public void write(Path file, Object data) throws IOException {
            directories.add(replace(file, data, JsonUtil.GSON));
        }

        public void commit() throws IOException {
            for (Path directory : directories) syncDirectory(directory);
            directories.clear();
        }
    }

    private static Path replace(Path file, Object data, com.google.gson.Gson serializer) throws IOException {
        Path destination = file.toAbsolutePath();
        Path directory = destination.getParent();
        // A directory created here (again) has a new, unsynced entry in its parent.
        if (!Files.isDirectory(directory)) syncedParents.remove(directory);
        Files.createDirectories(directory);
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
