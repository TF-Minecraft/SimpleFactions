package net.tfminecraft.simplefactions.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * {@code migrations/character-v1.json}: per store, whether it has been
 * migrated, with the backup of the original and that original's hash.
 */
final class MigrationManifest {
    static final String IN_PROGRESS = "in-progress";
    static final String DONE = "done";
    static final String UNREADABLE = "unreadable";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static final class Entry {
        String state;
        String backup;
        String hash;
    }

    String migration = "character-v1";
    boolean complete;
    Map<String, Entry> stores = new TreeMap<>();

    static MigrationManifest load(Path file) throws IOException {
        if (!Files.exists(file)) return new MigrationManifest();
        try {
            MigrationManifest manifest = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MigrationManifest.class);
            if (manifest == null || manifest.stores == null) throw new JsonParseException("empty manifest");
            return manifest;
        } catch (JsonParseException malformed) {
            throw new IOException("Unreadable migration manifest " + file, malformed);
        }
    }

    void save(Path file) throws IOException {
        MigrationFiles.write(file, GSON.toJson(this));
    }

    Entry entry(String store) {
        return stores.get(store);
    }

    void mark(String store, String state, String backup, String hash) {
        Entry entry = new Entry();
        entry.state = state;
        entry.backup = backup;
        entry.hash = hash;
        stores.put(store, entry);
    }
}
