package net.tfminecraft.simplefactions.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Turns the stored names of SimpleFactions 3 into member keys, once, store by
 * store. Each store's original is copied to {@code migrations/backup/} with
 * its hash and marked in progress before the new file replaces it; a store
 * still in progress after a crash is restored from that copy and redone.
 */
public final class CharacterMigration {
    public static final String MANIFEST = "migrations/character-v1.json";
    public static final String QUARANTINE = "migrations/quarantine.json";
    private static final String BACKUP = "migrations/backup/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Where names and accounts lead: the account behind a name and the account's reviewed heir. */
    public interface Heirs {
        Optional<UUID> account(String name);

        Optional<CharacterRef> heir(UUID account);
    }

    /** DenarEconomy's name index first, then the server's user cache; heirs from the identity provider. */
    public static final Heirs SHARED = new Heirs() {
        @Override
        public Optional<UUID> account(String name) {
            try {
                UUID id = net.tfminecraft.denareconomy.accounts.OfflineModifier.playerId(name);
                if (id != null) return Optional.of(id);
            } catch (RuntimeException | LinkageError unavailable) {
                // Fall back to the user cache below.
            }
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            return cached == null ? Optional.empty() : Optional.ofNullable(cached.getUniqueId());
        }

        @Override
        public Optional<CharacterRef> heir(UUID account) {
            return Characters.legacyHeir(account);
        }
    };

    /** What a run did, for the log and for tests. */
    public record Result(int migrated, int skipped, int quarantined, int assignmentsApplied) {}

    private final Path root;
    private final Heirs heirs;
    private final Logger logger;

    public CharacterMigration(Path root, Heirs heirs, Logger logger) {
        this.root = root;
        this.heirs = heirs;
        this.logger = logger;
    }

    /** Whether every store has been migrated already. */
    public boolean complete() throws IOException {
        return MigrationManifest.load(root.resolve(MANIFEST)).complete;
    }

    public Result run() throws IOException {
        Path manifestFile = root.resolve(MANIFEST);
        MigrationManifest manifest = MigrationManifest.load(manifestFile);
        Quarantine quarantine = Quarantine.load(root.resolve(QUARANTINE));
        int migrated = 0;
        int skipped = 0;
        if (!manifest.complete) {
            Map<String, Optional<String>> names = new HashMap<>();
            Map<UUID, Optional<String>> accounts = new HashMap<>();
            for (String store : stores()) {
                MigrationManifest.Entry entry = manifest.entry(store);
                if (entry != null && !MigrationManifest.IN_PROGRESS.equals(entry.state)) continue;
                if (entry != null) restore(store, entry);
                if (migrate(store, manifest, manifestFile, quarantine, names, accounts)) migrated++;
                else skipped++;
            }
            manifest.complete = true;
            manifest.save(manifestFile);
        }
        int applied = applyAssignments(quarantine);
        int open = (int) quarantine.records().stream().filter(record -> record.assigned == null).count();
        return new Result(migrated, skipped, open, applied);
    }

    /** Every store that may hold names or accounts, in a stable order. */
    List<String> stores() throws IOException {
        List<String> stores = new ArrayList<>();
        for (String folder : List.of("Data", "Wars", "Warbands", "Battles")) {
            Path directory = root.resolve(folder);
            if (!Files.isDirectory(directory)) continue;
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(file -> file.getFileName().toString().endsWith(".json") && Files.isRegularFile(file))
                        .map(file -> folder + "/" + file.getFileName()).sorted().forEach(stores::add);
            }
        }
        for (String cache : List.of("Cache/logins.json", "Cache/vehicle_fees.json", "Cache/vehicles_registry.json")) {
            if (Files.isRegularFile(root.resolve(cache))) stores.add(cache);
        }
        return stores;
    }

    private void restore(String store, MigrationManifest.Entry entry) throws IOException {
        Path backup = root.resolve(entry.backup);
        if (!Files.isRegularFile(backup) || !MigrationFiles.sha256(Files.readAllBytes(backup)).equals(entry.hash)) {
            throw new IOException("Backup of " + store + " is missing or does not match its hash; restore it by hand");
        }
        MigrationFiles.write(root.resolve(store), Files.readAllBytes(backup));
        logger.warning("Restored " + store + " from its backup to redo an interrupted migration");
    }

    private boolean migrate(String store, MigrationManifest manifest, Path manifestFile, Quarantine quarantine,
                            Map<String, Optional<String>> names, Map<UUID, Optional<String>> accounts) throws IOException {
        Path file = root.resolve(store);
        byte[] original = Files.readAllBytes(file);
        String hash = MigrationFiles.sha256(original);
        String backup = BACKUP + store;
        MigrationFiles.write(root.resolve(backup), original);
        manifest.mark(store, MigrationManifest.IN_PROGRESS, backup, hash);
        manifest.save(manifestFile);

        JsonElement tree;
        try {
            tree = JsonParser.parseString(new String(original, StandardCharsets.UTF_8));
        } catch (JsonParseException malformed) {
            logger.log(Level.SEVERE, "Cannot read " + store + "; it was left unchanged and is not migrated", malformed);
            manifest.mark(store, MigrationManifest.UNREADABLE, backup, hash);
            manifest.save(manifestFile);
            return false;
        }
        StoreMapper mapper = new StoreMapper(store, heirs, names, accounts);
        if (store.startsWith("Data/") && tree instanceof JsonObject faction) mapper.faction(faction, hash);
        else if (store.startsWith("Wars/") && tree instanceof JsonObject war) mapper.war(war);
        else if (store.equals("Cache/logins.json") && tree instanceof JsonObject logins) mapper.logins(logins);
        else if (store.equals("Cache/vehicle_fees.json") && tree instanceof JsonObject fees) mapper.vehicleFees(fees);
        else if (store.equals("Cache/vehicles_registry.json") && tree instanceof JsonArray rows) mapper.vehicleRegistry(rows);
        // Warbands and battles hold accounts for live combat only, which stay as they are.

        quarantine.merge(mapper.records());
        quarantine.save();
        for (String warning : mapper.warnings()) logger.warning(warning);
        if (mapper.changed()) MigrationFiles.write(file, GSON.toJson(tree));
        manifest.mark(store, MigrationManifest.DONE, backup, hash);
        manifest.save(manifestFile);
        return true;
    }

    /**
     * Writes staff assignments into their stores: every place holding the
     * record's token now holds the character's key. Runs before the stores
     * are loaded, so it never races the plugin's own saves.
     */
    int applyAssignments(Quarantine quarantine) throws IOException {
        int applied = 0;
        for (Quarantine.Record record : quarantine.records()) {
            if (record.assigned == null || record.applied) continue;
            Path file = root.resolve(record.store);
            if (Files.isRegularFile(file)) {
                byte[] current = Files.readAllBytes(file);
                JsonElement tree = JsonParser.parseString(new String(current, StandardCharsets.UTF_8));
                if (replace(tree, record.token, record.assigned, "")) {
                    MigrationFiles.write(root.resolve(BACKUP + "assign-" + record.id + "/" + record.store), current);
                    MigrationFiles.write(file, GSON.toJson(tree));
                }
            }
            record.applied = true;
            quarantine.save();
            applied++;
            logger.info("Gave quarantine record " + record.id + " (" + record.original + ") to character " + record.assigned);
        }
        return applied;
    }

    /**
     * Replaces every string and map key equal to {@code token}; merged entries
     * follow the migration's rules. {@code path} is the chain of field names.
     */
    static boolean replace(JsonElement element, String token, String key, String path) {
        boolean changed = false;
        if (element instanceof JsonArray array) {
            Set<String> seen = new java.util.HashSet<>();
            JsonArray rewritten = new JsonArray();
            for (JsonElement child : array) {
                String value = StoreMapper.string(child);
                if (token.equals(value)) {
                    changed = true;
                    value = key;
                    child = new JsonPrimitive(key);
                }
                if (value != null && memberList(path) && !seen.add(value)) {
                    changed = true;
                    continue;
                }
                changed |= replace(child, token, key, path);
                rewritten.add(child);
            }
            if (changed) {
                while (!array.isEmpty()) array.remove(0);
                array.addAll(rewritten);
            }
        } else if (element instanceof JsonObject object) {
            Map<String, JsonElement> rewritten = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String name = entry.getKey();
                JsonElement value = entry.getValue();
                if (token.equals(StoreMapper.string(value))) {
                    value = new JsonPrimitive(key);
                    changed = true;
                }
                changed |= replace(value, token, key, path + "/" + name);
                if (token.equals(name)) {
                    name = key;
                    changed = true;
                }
                JsonElement existing = rewritten.get(name);
                rewritten.put(name, existing == null ? value : StoreMapper.merge(existing, value, mergeFor(path)));
            }
            if (changed) {
                for (String old : new ArrayList<>(object.keySet())) object.remove(old);
                rewritten.forEach(object::add);
            }
        }
        return changed;
    }

    private static final Set<String> LISTS_OF_MEMBERS = Set.of(
            "members", "dividendEligible", "enlisted", "council members", "eligible voters", "citizens");
    private static final Set<String> SUMMED = Set.of(
            "citizen taxes", "deposits today", "pendingWages", "CITIZENS", "DEPOSITS");

    private static String last(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static boolean memberList(String path) {
        return LISTS_OF_MEMBERS.contains(last(path)) || path.contains("/election candidates/");
    }

    /** Logins keep the most recent; money and vote counts add up; anything else keeps the first. */
    static StoreMapper.Merge mergeFor(String path) {
        if (path.isEmpty()) return StoreMapper.Merge.MIN;
        if (SUMMED.contains(last(path)) || path.contains("/previous votes/")) return StoreMapper.Merge.SUM;
        return StoreMapper.Merge.FIRST;
    }

    /** The key a record would be assigned: a character ID, lower-cased. */
    public static String characterKey(String input) {
        UUID id = Members.characterId(input == null ? null : input.strip());
        return id == null ? null : Members.key(id);
    }
}
