package net.tfminecraft.simplefactions.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * {@code migrations/quarantine.json}: everything the migration could not give
 * to a character. A record keeps the original name (or account), every place
 * it was found with the complete original JSON there, and the token that
 * holds its seat in the migrated store until staff assign a character.
 */
public final class Quarantine {
    /** A name that was never a known account, or an account with no reviewed heir. */
    public static final String NAME = "name";
    /** An account UUID with no reviewed heir; the store keeps the account UUID. */
    public static final String ACCOUNT = "account";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** One place a quarantined name or account was found. */
    public static final class Occurrence {
        public String path;
        public JsonElement fragment;

        Occurrence(String path, JsonElement fragment) {
            this.path = path;
            this.fragment = fragment;
        }
    }

    /** Everything one unmapped name or account held in one store. */
    public static final class Record {
        public String id;
        public String store;
        public String kind;
        public String original;
        public String token;
        public List<Occurrence> occurrences = new ArrayList<>();
        /** Character ID chosen by staff, or null while unresolved. */
        public String assigned;
        /** True once the assignment has been written into the store. */
        public boolean applied;

        /** Whether it held a realm or guild leader's seat. */
        public boolean holdsLeader() {
            for (Occurrence occurrence : occurrences) {
                if (occurrence.path.equals("$.leader") || occurrence.path.matches("\\$\\.guilds\\[[^]]*]\\.leader")) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Path file;
    private final Map<String, Record> records = new LinkedHashMap<>();

    private Quarantine(Path file) {
        this.file = file;
    }

    public static Quarantine load(Path file) throws IOException {
        Quarantine quarantine = new Quarantine(file);
        QuarantineCommands.snapshot(file, List.of());
        if (!Files.exists(file)) return quarantine;
        try {
            List<Record> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<Record>>() {}.getType());
            if (loaded == null) throw new JsonParseException("empty quarantine");
            for (Record record : loaded) quarantine.records.put(record.id, record);
            QuarantineCommands.snapshot(file, quarantine.records());
            return quarantine;
        } catch (JsonParseException malformed) {
            throw new IOException("Unreadable quarantine " + file, malformed);
        }
    }

    public void save() throws IOException {
        MigrationFiles.write(file, GSON.toJson(new ArrayList<>(records.values())));
        QuarantineCommands.snapshot(file, records());
    }

    public Collection<Record> records() {
        return records.values();
    }

    public Record get(String id) {
        return id == null ? null : records.get(id);
    }

    /** Adds the records of one store; a redone store replaces its earlier records with the same IDs. */
    void merge(Collection<Record> found) {
        for (Record record : found) {
            Record existing = records.get(record.id);
            if (existing != null) {
                record.assigned = existing.assigned;
                record.applied = existing.applied;
            }
            records.put(record.id, record);
        }
    }
}
