package net.tfminecraft.simplefactions.database;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonUtil {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static <T> T readJson(File file, Class<T> type) throws IOException {
        try (FileReader r = new FileReader(file)) {
            return GSON.fromJson(r, type);
        }
    }

    public static void writeJson(File file, Object data) throws IOException {
        try (FileWriter w = new FileWriter(file)) {
            GSON.toJson(data, w);
        }
    }

    /** Stage a complete save beside the destination so failed writes keep the previous data. */
    public static void writeJsonAtomic(File file, Object data) throws IOException {
        var destination = file.toPath().toAbsolutePath();
        var pending = java.nio.file.Files.createFile(destination.resolveSibling(file.getName() + "." + java.util.UUID.randomUUID() + ".tmp"));
        try {
            if (java.nio.file.Files.exists(destination)
                    && java.nio.file.Files.getFileAttributeView(destination, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
                java.nio.file.Files.setPosixFilePermissions(pending, java.nio.file.Files.getPosixFilePermissions(destination));
            }
            java.nio.file.Files.writeString(pending, GSON.toJson(data));
            try {
                java.nio.file.Files.move(pending, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                java.nio.file.Files.move(pending, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(pending);
        }
    }
}
