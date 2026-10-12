package net.tfminecraft.simplefactions.database;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Durable intent files: sync the new content and the directory entry before any dependent effect. */
public final class DurableJson {
    private DurableJson() {}

    public static void write(Path file, Object data) throws IOException {
        Path destination = file.toAbsolutePath();
        Path directory = destination.getParent();
        Files.createDirectories(directory);
        Path pending = Files.createTempFile(directory, destination.getFileName().toString() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(JsonUtil.GSON.toJson(data));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            // A previous failed attempt may already have created an unsynced parent entry.
            for (Path path = directory; path != null; path = path.getParent()) {
                try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                    channel.force(true);
                }
            }
        } finally {
            Files.deleteIfExists(pending);
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
