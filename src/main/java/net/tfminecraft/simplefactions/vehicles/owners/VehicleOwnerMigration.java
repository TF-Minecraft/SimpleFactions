package net.tfminecraft.simplefactions.vehicles.owners;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;

/**
 * The one-time move of VehicleFramework owners from account names to characters,
 * journalled in {@code vehicles/owner-migration.json}. The plan records every vehicle's
 * original and decided fields before anything is written, so a crash resumes from the
 * plan and a decision is never re-derived from a value that may already be rewritten.
 * Once {@code completed} it never runs again and its backups are never retaken.
 */
public final class VehicleOwnerMigration {
    public static final String PLAN = "vehicles/owner-migration.json";
    public static final String DATABASE_BACKUP = "vehicles/vehicles-6a-premigration.db";
    public static final String REGISTRY_BACKUP = "vehicles/vehicles_registry-6a-premigration.json";
    static final int SAVE_EVERY = 25;

    /** Status of one plan entry. */
    public enum Status { PENDING, DONE, QUARANTINED, GONE }

    /** Name and character lookups, from the live providers in production. */
    public interface Identity {
        /** The account whose current Minecraft name is this one; null when the name does not round-trip. */
        UUID roundTrip(String name);

        Optional<UUID> accountOf(UUID character);

        /** The account's reviewed legacy heir. */
        Optional<CharacterRef> heir(UUID account);

        /** The character's real name, or null. */
        String nameOf(UUID character);
    }

    /** VehicleFramework's database copy and check. */
    public interface Backups {
        void vacuumInto(Path destination) throws IOException;

        boolean quickCheck(Path database) throws IOException;
    }

    public record Outcome(boolean ran, int migrated, int unchanged, int quarantined, int gone) {}

    static final class Fields {
        String owner;
        List<String> whitelist;
        boolean whitelisted;
        boolean ticketsEnabled;
        String ticketId;

        static Fields of(OwnerFields fields) {
            Fields out = new Fields();
            out.owner = fields.owner();
            out.whitelist = new ArrayList<>(fields.whitelist());
            out.whitelisted = fields.whitelisted();
            out.ticketsEnabled = fields.ticketsEnabled();
            out.ticketId = fields.ticketId();
            return out;
        }

        OwnerFields value() {
            return new OwnerFields(owner, whitelist, whitelisted, ticketsEnabled, ticketId);
        }
    }

    static final class Entry {
        String vehicle;
        String type;
        String mode;
        Fields original;
        Fields planned;
        Status status = Status.PENDING;
        String reason;
        /** The account the legacy owner name round-tripped to, when it did. */
        UUID account;
    }

    static final class Plan {
        int version = 1;
        long createdAt;
        boolean backedUp;
        String databaseBackup;
        String registryBackup;
        boolean completed;
        long completedAt;
        List<Entry> entries = new ArrayList<>();
        List<VehicleQuarantine.Entry> quarantine = new ArrayList<>();
    }

    private final Path folder;
    private final VehicleOwnerApi api;
    private final PlayerVehicleRegistry registry;
    private final Identity identity;
    private final Backups backups;
    private final Path registryFile;

    public VehicleOwnerMigration(Path folder, VehicleOwnerApi api, PlayerVehicleRegistry registry, Identity identity,
            Backups backups) {
        this.folder = folder;
        this.api = api;
        this.registry = registry;
        this.identity = identity;
        this.backups = backups;
        this.registryFile = folder.resolve("Cache/vehicles_registry.json");
    }

    /** Accounts the plan matched legacy owner names to, by vehicle; empty without a plan. */
    public static java.util.Map<String, UUID> accounts(Path folder) throws IOException {
        Path file = folder.resolve(PLAN);
        java.util.Map<String, UUID> out = new java.util.HashMap<>();
        if (Files.exists(file)) {
            for (Entry entry : read(file).entries) if (entry.account != null) out.put(entry.vehicle, entry.account);
        }
        return out;
    }

    /** The quarantine section of an existing plan, for staff listings; empty without a plan. */
    public static List<VehicleQuarantine.Entry> quarantine(Path folder) throws IOException {
        Path file = folder.resolve(PLAN);
        if (!Files.exists(file)) return List.of();
        Plan plan = read(file);
        return List.copyOf(plan.quarantine);
    }

    public Outcome run() throws IOException {
        Path file = folder.resolve(PLAN);
        Plan plan;
        if (Files.exists(file)) {
            plan = read(file);
            if (plan.completed) return new Outcome(false, 0, 0, 0, 0);
        } else {
            plan = plan();
            DurableJson.write(file, plan);
        }
        if (!plan.backedUp) backUp(file, plan);
        int migrated = apply(file, plan);
        verify(file, plan);
        plan.completed = true;
        plan.completedAt = System.currentTimeMillis();
        DurableJson.write(file, plan);
        int unchanged = (int) plan.entries.stream()
                .filter(entry -> entry.status == Status.DONE && entry.original.value().equals(entry.planned.value()))
                .count();
        return new Outcome(true, migrated, unchanged, count(plan, Status.QUARANTINED), count(plan, Status.GONE));
    }

    private static Plan read(Path file) throws IOException {
        Plan plan = DurableJson.read(file, Plan.class);
        if (plan.entries == null || plan.quarantine == null) throw new IOException("Invalid vehicle owner plan: " + file);
        for (Entry entry : plan.entries) {
            if (entry == null || entry.vehicle == null || entry.status == null
                    || (entry.status != Status.QUARANTINED && (entry.original == null || entry.planned == null))) {
                throw new IOException("Invalid vehicle owner plan entry in " + file);
            }
        }
        return plan;
    }

    private static int count(Plan plan, Status status) {
        return (int) plan.entries.stream().filter(entry -> entry.status == status).count();
    }

    // Step 1: decide every vehicle once.

    Plan plan() throws IOException {
        if (!api.characters()) throw new IOException("VehicleFramework is not in characters mode");
        Plan plan = new Plan();
        plan.createdAt = System.currentTimeMillis();
        for (OwnerMeta meta : api.list()) {
            Entry entry = decide(meta);
            plan.entries.add(entry);
            if (entry.status == Status.QUARANTINED) {
                plan.quarantine.add(new VehicleQuarantine.Entry(entry.vehicle,
                        entry.original == null ? null : entry.original.owner, entry.reason));
            }
        }
        return plan;
    }

    Entry decide(OwnerMeta meta) {
        Entry entry = new Entry();
        entry.vehicle = meta.vehicle();
        entry.type = meta.typeId();
        PlayerVehicleRecord record = registry.getByVehicleUuid(meta.vehicle()).orElse(null);
        entry.mode = record == null ? null : record.getMode().name();
        OwnerFields current = meta.current().orElse(null);
        if (current == null) return quarantine(entry, "its ownership fields cannot be read");
        entry.original = Fields.of(current);
        String owner = current.owner();
        String next;
        if (LeaderStamps.isFactionRow(record)) {
            next = LeaderStamps.expected(record);
        } else if (OwnerTags.isUnowned(owner) || OwnerTags.isCharacter(owner) || OwnerTags.NO_CHARACTER.equals(owner)) {
            entry.planned = entry.original;
            return entry;
        } else if (record != null) {
            entry.account = account(OwnerTags.legacyName(owner));
            String why = personalProblem(entry.account, record);
            if (why != null) return quarantine(entry, why);
            next = OwnerTags.character(record.getPlayerUuid());
        } else {
            UUID account = account(OwnerTags.legacyName(owner));
            entry.account = account;
            CharacterRef heir = account == null ? null : heir(account);
            if (heir == null) return quarantine(entry, account == null
                    ? "owner name does not match a known account" : "owner account has no reviewed heir");
            next = OwnerTags.character(heir.characterId());
        }
        entry.planned = Fields.of(current.withOwnership(next, seedWhitelist(current.whitelist())));
        return entry;
    }

    /** Why a personal row cannot seed the owner, or null when its character belongs to the tag's account. */
    private String personalProblem(UUID account, PlayerVehicleRecord record) {
        if (account == null) return "owner name does not match a known account";
        if (record.getPlayerUuid() == null) return "personal registry row has no character";
        if (!account.equals(identity.accountOf(record.getPlayerUuid()).orElse(null))) {
            return "personal registry character belongs to another account";
        }
        return null;
    }

    /** A {@code player_<uuid>} construction fallback names its account directly; a name must round-trip. */
    private UUID account(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            UUID direct = UUID.fromString(name);
            if (direct.toString().equalsIgnoreCase(name)) return direct;
        } catch (IllegalArgumentException notUuid) {
            // A Minecraft name.
        }
        return identity.roundTrip(name);
    }

    private CharacterRef heir(UUID account) {
        return identity.heir(account).filter(ref -> account.equals(ref.ownerId())).orElse(null);
    }

    /** Legacy entries move to the named account's heir when the name round-trips; the rest stay as they are. */
    List<String> seedWhitelist(List<String> entries) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : entries) {
            if (OwnerTags.isCharacterEntry(raw)) {
                out.add(raw);
                continue;
            }
            String name = OwnerTags.legacyName(raw) != null ? OwnerTags.legacyName(raw) : raw;
            UUID account = name.startsWith(OwnerTags.LEGACY_PREFIX) ? null : account(name);
            CharacterRef heir = account == null ? null : heir(account);
            if (heir == null) {
                out.add(raw);
                continue;
            }
            String characterName = identity.nameOf(heir.characterId());
            out.add(OwnerTags.whitelistEntry(heir, characterName == null ? name : characterName));
        }
        return new ArrayList<>(out);
    }

    private static Entry quarantine(Entry entry, String reason) {
        entry.status = Status.QUARANTINED;
        entry.reason = reason;
        return entry;
    }

    // Step 2: fixed-name backups outside VehicleFramework's rotation.

    private void backUp(Path file, Plan plan) throws IOException {
        Path database = folder.resolve(DATABASE_BACKUP);
        Files.createDirectories(database.getParent());
        Files.deleteIfExists(database);
        backups.vacuumInto(database);
        if (!backups.quickCheck(database)) throw new IOException("Vehicle database backup failed its quick check");
        Path registryCopy = folder.resolve(REGISTRY_BACKUP);
        copyDurably(Files.exists(registryFile) ? Files.readAllBytes(registryFile) : "[]".getBytes(), registryCopy);
        plan.databaseBackup = database.toString();
        plan.registryBackup = registryCopy.toString();
        plan.backedUp = true;
        DurableJson.write(file, plan);
    }

    static void copyDurably(byte[] bytes, Path destination) throws IOException {
        Path directory = destination.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path pending = Files.createTempFile(directory, destination.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    // Step 3: apply conditionally.

    private int apply(Path file, Plan plan) throws IOException {
        int migrated = 0;
        int dirty = 0;
        for (Entry entry : plan.entries) {
            if (entry.status != Status.PENDING) continue;
            OwnerFields current = api.read(entry.vehicle).flatMap(OwnerMeta::current).orElse(null);
            OwnerFields original = entry.original.value();
            OwnerFields planned = entry.planned.value();
            if (current == null) {
                entry.status = Status.GONE;
            } else if (current.equals(planned)) {
                entry.status = Status.DONE;
            } else if (current.equals(original)) {
                Result result = api.write(entry.vehicle, planned.owner(), planned.whitelist(), Reason.MIGRATE);
                if (result == Result.NOT_FOUND) {
                    entry.status = Status.GONE;
                } else if (result != Result.OK) {
                    DurableJson.write(file, plan);
                    throw new IOException("Could not migrate the owner of vehicle " + entry.vehicle + ": " + result);
                } else {
                    entry.status = Status.DONE;
                    migrated++;
                }
            } else {
                changedSincePlanned(plan, entry, current);
            }
            if (++dirty % SAVE_EVERY == 0) DurableJson.write(file, plan);
        }
        DurableJson.write(file, plan);
        return migrated;
    }

    private static void changedSincePlanned(Plan plan, Entry entry, OwnerFields current) {
        quarantine(entry, "owner changed after the migration was planned");
        plan.quarantine.add(new VehicleQuarantine.Entry(entry.vehicle, current.owner(), entry.reason));
    }

    // Step 4: verify stored and live copies separately.

    private void verify(Path file, Plan plan) throws IOException {
        List<String> torn = new ArrayList<>();
        boolean changed = false;
        for (Entry entry : plan.entries) {
            if (entry.status != Status.DONE) continue;
            OwnerMeta meta = api.read(entry.vehicle).orElse(null);
            if (meta == null) {
                entry.status = Status.GONE;
                changed = true;
                continue;
            }
            OwnerFields planned = entry.planned.value();
            List<OwnerFields> copies = new ArrayList<>();
            meta.live().ifPresent(copies::add);
            if (meta.hasStoredRow()) {
                OwnerFields stored = meta.stored().orElse(null);
                if (stored == null || !stored.owner().equals(meta.storedColumn())) {
                    torn.add(entry.vehicle);
                    continue;
                }
                copies.add(stored);
            }
            if (copies.size() == 2 && !copies.get(0).sameOwnership(copies.get(1))) {
                torn.add(entry.vehicle);
            } else if (!copies.get(0).sameOwnership(planned)) {
                // Both copies agree on something else: staff changed it since; list it, never overwrite.
                changedSincePlanned(plan, entry, copies.get(0));
                changed = true;
            }
        }
        if (changed) DurableJson.write(file, plan);
        if (!torn.isEmpty()) {
            throw new IOException("Stored and live vehicle owners disagree after migration: " + torn);
        }
    }

    // Production lookups.

    /**
     * Live providers. A legacy owner name counts only while it is still its account's latest
     * name: Bukkit's name for the account when the server still knows one, otherwise the name
     * DenarEconomy remembered last for it (its durable {@code player-names.json}, which never
     * expires as the user cache does).
     */
    public static final class LiveIdentity implements Identity {
        static final Path REMEMBERED_NAMES = Path.of("plugins/DenarEconomy/Data/player-names.json");
        private final Path rememberedNames;
        private java.util.Map<UUID, String> latestNames;

        public LiveIdentity() {
            this(REMEMBERED_NAMES);
        }

        /** Reads DenarEconomy's name index from this file instead of the server's. */
        public LiveIdentity(Path rememberedNames) {
            this.rememberedNames = rememberedNames;
        }

        @Override
        public UUID roundTrip(String name) {
            UUID account = VehicleOwnershipQueries.resolvePlayerUuid(name);
            if (account == null) return null;
            String current = Bukkit.getOfflinePlayer(account).getName();
            if (current == null) current = latestRemembered().get(account);
            return name.equalsIgnoreCase(current) ? account : null;
        }

        /** Each account's most recently first-seen name in DenarEconomy's index; read once. */
        public java.util.Map<UUID, String> latestRemembered() {
            if (latestNames == null) latestNames = readLatest(rememberedNames);
            return latestNames;
        }

        static java.util.Map<UUID, String> readLatest(Path file) {
            java.util.Map<UUID, String> latest = new java.util.HashMap<>();
            if (!Files.isRegularFile(file)) return latest;
            try {
                com.google.gson.JsonObject names = com.google.gson.JsonParser.parseString(Files.readString(file))
                        .getAsJsonObject();
                // DenarEconomy appends a name the first time it sees it, so the last entry is the newest.
                for (var entry : names.entrySet()) {
                    try {
                        latest.put(UUID.fromString(entry.getValue().getAsString()), entry.getKey());
                    } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException bad) {
                        // A damaged row names nobody.
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                // Without the index only Bukkit's names count.
                latest.clear();
            }
            return latest;
        }

        @Override
        public Optional<UUID> accountOf(UUID character) {
            return Members.accountId(Members.key(character));
        }

        @Override
        public Optional<CharacterRef> heir(UUID account) {
            return Characters.legacyHeir(account);
        }

        @Override
        public String nameOf(UUID character) {
            return Members.info(Members.key(character)).map(CharacterInfo::name).map(VehicleOwnerMigration::plain)
                    .orElse(null);
        }
    }

    static String plain(String name) {
        String stripped = org.bukkit.ChatColor.stripColor(name);
        return stripped == null || stripped.isBlank() ? null : stripped.strip();
    }

    /** {@code VACUUM INTO} and {@code quick_check} through SQLite JDBC, beside VehicleFramework's own connection. */
    public static final class SqliteBackups implements Backups {
        private final Path database;

        public SqliteBackups(Path database) {
            this.database = database;
        }

        @Override
        public void vacuumInto(Path destination) throws IOException {
            try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("VACUUM INTO '" + destination.toAbsolutePath().toString().replace("'", "''") + "'");
            } catch (SQLException error) {
                throw new IOException("Could not copy " + database, error);
            }
        }

        @Override
        public boolean quickCheck(Path copy) throws IOException {
            try (Connection connection = connect(copy); Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
                return result.next() && "ok".equalsIgnoreCase(result.getString(1)) && !result.next();
            } catch (SQLException error) {
                throw new IOException("Could not check " + copy, error);
            }
        }

        private static Connection connect(Path file) throws SQLException {
            return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        }
    }
}
