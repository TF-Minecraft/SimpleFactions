package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterRef;

class VehicleOwnerMigrationTest {
    @TempDir Path folder;
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final Identity identity = new Identity();
    private final Backups backups = new Backups();
    private FakeOwnerApi api;
    private List<Faction> previousFactions;

    private final UUID alice = UUID.randomUUID();
    private final CharacterRef aliceCharacter = new CharacterRef(alice, UUID.randomUUID());
    private final UUID bob = UUID.randomUUID();
    private final CharacterRef bobHeir = new CharacterRef(bob, UUID.randomUUID());
    private final UUID dave = UUID.randomUUID();
    private final CharacterRef daveHeir = new CharacterRef(dave, UUID.randomUUID());
    private final UUID leader = UUID.randomUUID();

    /** Name, account and heir lookups as the providers would answer them. */
    static final class Identity implements VehicleOwnerMigration.Identity {
        final Map<String, UUID> names = new HashMap<>();
        final Map<UUID, UUID> accounts = new HashMap<>();
        final Map<UUID, CharacterRef> heirs = new HashMap<>();
        final Map<UUID, String> characterNames = new HashMap<>();

        void character(CharacterRef ref, String name) {
            accounts.put(ref.characterId(), ref.ownerId());
            characterNames.put(ref.characterId(), name);
        }

        @Override
        public UUID roundTrip(String name) {
            return names.get(name);
        }

        @Override
        public Optional<UUID> accountOf(UUID character) {
            return Optional.ofNullable(accounts.get(character));
        }

        @Override
        public Optional<CharacterRef> heir(UUID account) {
            return Optional.ofNullable(heirs.get(account));
        }

        @Override
        public String nameOf(UUID character) {
            return characterNames.get(character);
        }
    }

    /** Copies by writing a marker file; a failure set to throw simulates a crash mid-backup. */
    static final class Backups implements VehicleOwnerMigration.Backups {
        int copies;
        IOException failure;
        boolean valid = true;

        @Override
        public void vacuumInto(Path destination) throws IOException {
            copies++;
            if (failure != null) {
                IOException thrown = failure;
                failure = null;
                throw thrown;
            }
            Files.writeString(destination, "copy " + copies);
        }

        @Override
        public boolean quickCheck(Path database) {
            return valid;
        }
    }

    /** Throws after a number of writes, like a crash part-way through the apply step. */
    static class Crashing implements VehicleOwnerApi {
        final FakeOwnerApi delegate;
        int writesBeforeCrash;

        Crashing(FakeOwnerApi delegate, int writesBeforeCrash) {
            this.delegate = delegate;
            this.writesBeforeCrash = writesBeforeCrash;
        }

        @Override
        public boolean characters() {
            return true;
        }

        @Override
        public List<OwnerMeta> list() {
            return delegate.list();
        }

        @Override
        public Optional<OwnerMeta> read(String vehicle) {
            return delegate.read(vehicle);
        }

        @Override
        public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
            if (writesBeforeCrash-- == 0) throw new IllegalStateException("server killed");
            return delegate.write(vehicle, owner, whitelist, reason);
        }
    }

    @BeforeEach
    void setUp() {
        api = FakeOwnerApi.install();
        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();
        identity.character(aliceCharacter, "Kira");
        identity.names.put("Alice", alice);
        identity.names.put("Bob", bob);
        identity.heirs.put(bob, bobHeir);
        identity.character(bobHeir, "Bran");
        identity.names.put("Dave", dave);
        identity.heirs.put(dave, daveHeir);
    }

    @AfterEach
    void tearDown() {
        FactionManager.factions = previousFactions;
    }

    private VehicleOwnerMigration migration(VehicleOwnerApi with) {
        return new VehicleOwnerMigration(folder, with, registry, identity, backups);
    }

    private VehicleOwnerMigration migration() {
        return migration(api);
    }

    private JsonObject plan() throws IOException {
        return JsonParser.parseString(Files.readString(folder.resolve(VehicleOwnerMigration.PLAN))).getAsJsonObject();
    }

    private void personal(String vehicle, UUID character) {
        registry.register(new PlayerVehicleRecord(character, vehicle, "cart", OwnershipMode.PERSONAL, null));
    }

    private Faction faction(String id, String leaderKey) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        when(faction.getLeader()).thenReturn(leaderKey);
        FactionManager.factions.add(faction);
        return faction;
    }

    @Test
    void personalRowsBecomeTheirCharacterWhenTheOwnerNameRoundTripsToItsAccount() throws IOException {
        personal("personal", aliceCharacter.characterId());
        api.live("personal", "cart", "player_Alice", "player_Bob", "Bob", "char_" + UUID.randomUUID() + ";x;Kept",
                "player_Renamed", "player_none", "");

        VehicleOwnerMigration.Outcome outcome = migration().run();

        assertEquals(new VehicleOwnerMigration.Outcome(true, 1, 0, 0, 0), outcome);
        OwnerFields after = api.vehicles.get("personal").current().orElseThrow();
        assertEquals("char_" + aliceCharacter.characterId(), after.owner());
        // Round-tripped names move to the account's heir under its character name; the rest stay inert.
        String bran = OwnerTags.whitelistEntry(bobHeir, "Bran");
        assertEquals(List.of(bran, after.whitelist().get(1), "player_Renamed", "player_none", ""), after.whitelist());
        assertTrue(after.whitelist().get(1).endsWith(";x;Kept"));
        assertEquals(List.of("personal char_" + aliceCharacter.characterId() + " MIGRATE"), api.writes);
        assertEquals(Map.of("personal", alice), VehicleOwnerMigration.accounts(folder));
    }

    @Test
    void factionRowsGetTheirLeadersCharacterOrNobody() throws IOException {
        faction("red", leader.toString());
        faction("blue", "dummy_3");
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "pool", "gun", OwnershipMode.POOL, null, "red"));
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "dummy", "gun", OwnershipMode.POOL, null, "blue"));
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "berth", "ship", OwnershipMode.INSTALLATION,
                "port", "gone"));
        api.stored("pool", "gun", "player_OldLeader").stored("dummy", "gun", "none").stored("berth", "ship", "faction_gone");

        migration().run();

        assertEquals("char_" + leader, api.owner("pool"));
        assertEquals("char_none", api.owner("dummy"));
        assertEquals("char_none", api.owner("berth"));
    }

    @Test
    void vehiclesWithoutARowGoToTheRoundTrippedAccountsHeir() throws IOException {
        UUID uuidTagged = UUID.randomUUID();
        CharacterRef uuidHeir = new CharacterRef(uuidTagged, UUID.randomUUID());
        identity.heirs.put(uuidTagged, uuidHeir);
        api.stored("orphan", "cart", "player_Dave").stored("uuid", "cart", "player_" + uuidTagged)
                .stored("short", "cart", "player_1-1-1-1-1");

        migration().run();

        assertEquals("player_1-1-1-1-1", api.owner("short"), "Only a full account UUID is taken as one");
        assertEquals("char_" + daveHeir.characterId(), api.owner("orphan"));
        assertEquals("char_" + uuidHeir.characterId(), api.owner("uuid"), "A player_<uuid> tag names its account");
    }

    @Test
    void anythingThatCannotBeSeededSafelyIsQuarantinedAndKeepsItsLegacyOwner() throws IOException {
        CharacterRef stranger = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        identity.character(stranger, "Mallory");
        identity.names.put("Heirless", UUID.randomUUID());
        personal("stranger", stranger.characterId());
        personal("nullcharacter", null);
        personal("nobody", aliceCharacter.characterId());
        personal("renamedrow", aliceCharacter.characterId());
        api.stored("stranger", "cart", "player_Alice")
                .stored("nullcharacter", "cart", "player_Alice")
                .stored("nobody", "cart", "player_none")
                .stored("renamedrow", "cart", "player_Renamed")
                .stored("renamed", "cart", "player_Renamed")
                .stored("heirless", "cart", "player_Heirless");
        api.vehicles.put("unreadable", new OwnerMeta("unreadable", "cart", "player_Alice", Optional.empty(),
                Optional.empty()));

        VehicleOwnerMigration.Outcome outcome = migration().run();

        assertEquals(7, outcome.quarantined());
        assertEquals(List.of(), api.writes);
        assertEquals("player_Alice", api.owner("stranger"));
        Map<String, String> reasons = new HashMap<>();
        for (VehicleQuarantine.Entry entry : VehicleOwnerMigration.quarantine(folder)) {
            reasons.put(entry.vehicle(), entry.reason());
        }
        assertEquals("personal registry character belongs to another account", reasons.get("stranger"));
        assertEquals("personal registry row has no character", reasons.get("nullcharacter"));
        assertEquals("owner name does not match a known account", reasons.get("nobody"));
        assertEquals("owner name does not match a known account", reasons.get("renamedrow"));
        assertEquals("owner name does not match a known account", reasons.get("renamed"));
        assertEquals("owner account has no reviewed heir", reasons.get("heirless"));
        assertEquals("its ownership fields cannot be read", reasons.get("unreadable"));
    }

    @Test
    void unownedAndAlreadyCharacterOwnedVehiclesAreLeftAsTheyAre() throws IOException {
        api.stored("unowned", "cart", "none").stored("owned", "cart", "char_" + UUID.randomUUID())
                .stored("leaderless", "cart", "char_none");

        VehicleOwnerMigration.Outcome outcome = migration().run();

        assertEquals(new VehicleOwnerMigration.Outcome(true, 0, 3, 0, 0), outcome);
        assertEquals(List.of(), api.writes);
    }

    @Test
    void thePlanAndFixedNameBackupsComeBeforeAnyWriteAndASecondBootTouchesNothing() throws IOException {
        Files.createDirectories(folder.resolve("Cache"));
        Files.writeString(folder.resolve("Cache/vehicles_registry.json"), "[\"before\"]");
        api.stored("orphan", "cart", "player_Dave");

        migration().run();

        JsonObject plan = plan();
        assertTrue(plan.get("completed").getAsBoolean());
        assertTrue(plan.get("backedUp").getAsBoolean());
        Path database = folder.resolve(VehicleOwnerMigration.DATABASE_BACKUP);
        Path registryCopy = folder.resolve(VehicleOwnerMigration.REGISTRY_BACKUP);
        assertEquals(database.toString(), plan.get("databaseBackup").getAsString());
        assertEquals(registryCopy.toString(), plan.get("registryBackup").getAsString());
        assertEquals("copy 1", Files.readString(database));
        assertEquals("[\"before\"]", Files.readString(registryCopy));
        byte[] planBytes = Files.readAllBytes(folder.resolve(VehicleOwnerMigration.PLAN));

        api.stored("orphan", "cart", "char_" + UUID.randomUUID());
        Files.writeString(folder.resolve("Cache/vehicles_registry.json"), "[\"after\"]");
        assertEquals(new VehicleOwnerMigration.Outcome(false, 0, 0, 0, 0), migration().run());

        assertEquals(1, backups.copies, "Backups are never retaken");
        assertEquals("[\"before\"]", Files.readString(registryCopy));
        assertArrayEquals(planBytes, Files.readAllBytes(folder.resolve(VehicleOwnerMigration.PLAN)));
        assertEquals(List.of("orphan char_" + daveHeir.characterId() + " MIGRATE"), api.writes);
    }

    @Test
    void anAbsentRegistryIsBackedUpAsAnEmptyList() throws IOException {
        migration().run();
        assertEquals("[]", Files.readString(folder.resolve(VehicleOwnerMigration.REGISTRY_BACKUP)));
    }

    @Test
    void aCrashDuringTheBackupResumesAtTheBackupWithThePlannedDecisions() throws IOException {
        api.stored("orphan", "cart", "player_Dave");
        backups.failure = new IOException("disk full");

        assertThrows(IOException.class, () -> migration().run());
        assertFalse(plan().get("backedUp").getAsBoolean());
        assertEquals(List.of(), api.writes);

        // The name now belongs to someone else, but the plan's decision stands.
        identity.names.remove("Dave");
        migration().run();

        assertEquals(2, backups.copies);
        assertEquals("char_" + daveHeir.characterId(), api.owner("orphan"));
    }

    @Test
    void aBackupThatFailsItsCheckIsNeverMarkedDone() throws IOException {
        backups.valid = false;
        IOException failure = assertThrows(IOException.class, () -> migration().run());
        assertTrue(failure.getMessage().contains("quick check"));
        assertFalse(plan().get("backedUp").getAsBoolean());
    }

    @Test
    void aCrashMidApplyResumesWithoutRewritingDoneVehiclesOrRedoingTheBackup() throws IOException {
        for (int i = 0; i < 30; i++) api.stored("v" + i, "cart", "player_Dave");

        assertThrows(IllegalStateException.class, () -> migration(new Crashing(api, 27)).run());
        assertEquals(27, api.writes.size());
        JsonObject crashed = plan();
        long done = crashed.getAsJsonArray("entries").asList().stream()
                .filter(entry -> entry.getAsJsonObject().get("status").getAsString().equals("DONE")).count();
        assertEquals(VehicleOwnerMigration.SAVE_EVERY, done, "Only the batched done flags reached disk");

        VehicleOwnerMigration.Outcome resumed = migration().run();

        assertEquals(3, resumed.migrated(), "Applied-but-unflagged vehicles are marked done without a write");
        assertEquals(30, api.writes.size());
        assertEquals(1, backups.copies);
        for (int i = 0; i < 30; i++) assertEquals("char_" + daveHeir.characterId(), api.owner("v" + i));
    }

    @Test
    void aStaffTakeoverBetweenACrashAndTheResumeIsQuarantinedNotOverwritten() throws IOException {
        api.stored("first", "cart", "player_Dave").stored("taken", "cart", "player_Dave");
        assertThrows(IllegalStateException.class, () -> migration(new Crashing(api, 1)).run());
        String staff = "char_" + UUID.randomUUID();
        api.stored("taken", "cart", staff);

        VehicleOwnerMigration.Outcome resumed = migration().run();

        assertEquals(1, resumed.quarantined());
        assertEquals(staff, api.owner("taken"));
        VehicleQuarantine.Entry entry = VehicleOwnerMigration.quarantine(folder).getFirst();
        assertEquals(new VehicleQuarantine.Entry("taken", staff, "owner changed after the migration was planned"), entry);
    }

    @Test
    void vehiclesGoneSincePlanningAreCountedWithoutBlockingCompletion() throws IOException {
        api.stored("removed", "cart", "player_Dave").stored("vanishing", "cart", "player_Dave");
        VehicleOwnerApi vanishing = new Crashing(api, Integer.MAX_VALUE) {
            @Override
            public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
                return vehicle.equals("vanishing") ? Result.NOT_FOUND : super.write(vehicle, owner, whitelist, reason);
            }
        };
        VehicleOwnerMigration migration = migration(vanishing);
        VehicleOwnerMigration.Plan plan = migration.plan();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Files.createDirectories(folder.resolve("vehicles"));
        Files.writeString(folder.resolve(VehicleOwnerMigration.PLAN), gson.toJson(plan));
        api.vehicles.remove("removed");

        VehicleOwnerMigration.Outcome outcome = migration.run();

        assertEquals(2, outcome.gone());
        assertTrue(plan().get("completed").getAsBoolean());
    }

    @Test
    void aFailedOrRefusedWriteAbortsTheStageWithTheProgressSaved() throws IOException {
        api.stored("a", "cart", "player_Dave");
        api.forced = Result.SAVE_FAILED;
        IOException failure = assertThrows(IOException.class, () -> migration().run());
        assertTrue(failure.getMessage().contains("SAVE_FAILED"));
        assertTrue(plan().get("backedUp").getAsBoolean());
        assertFalse(plan().get("completed").getAsBoolean());

        api.forced = Result.REFUSED_MODE;
        assertThrows(IOException.class, () -> migration().run());
        api.forced = null;
        migration().run();
        assertEquals(1, backups.copies);
        assertEquals("char_" + daveHeir.characterId(), api.owner("a"));
    }

    @Test
    void nothingIsPlannedOutsideCharactersMode() {
        api.characters = false;
        assertThrows(IOException.class, () -> migration().run());
        assertFalse(Files.exists(folder.resolve(VehicleOwnerMigration.PLAN)));
    }

    @Test
    void storedAndLiveCopiesThatDisagreeAfterTheApplyFailTheStage() throws IOException {
        api.stored("torn", "cart", "player_Dave");
        VehicleOwnerApi tearing = new Crashing(api, Integer.MAX_VALUE) {
            @Override
            public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
                Result result = super.write(vehicle, owner, whitelist, reason);
                OwnerMeta meta = delegate.vehicles.get(vehicle);
                delegate.vehicles.put(vehicle, new OwnerMeta(vehicle, "cart", meta.storedColumn(), meta.stored(),
                        Optional.of(FakeOwnerApi.fields("player_Dave"))));
                return result;
            }
        };

        IOException failure = assertThrows(IOException.class, () -> migration(tearing).run());

        assertTrue(failure.getMessage().contains("torn"));
        assertFalse(plan().get("completed").getAsBoolean());
    }

    @Test
    void aStoredColumnThatDisagreesWithItsPayloadOrAMissingPayloadFailsTheStage() throws IOException {
        api.stored("column", "cart", "player_Dave").stored("payload", "cart", "player_Dave");
        VehicleOwnerApi tearing = new Crashing(api, Integer.MAX_VALUE) {
            @Override
            public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
                Result result = super.write(vehicle, owner, whitelist, reason);
                OwnerMeta meta = delegate.vehicles.get(vehicle);
                delegate.vehicles.put(vehicle, vehicle.equals("column")
                        ? new OwnerMeta(vehicle, "cart", "player_Dave", meta.stored(), Optional.empty())
                        : new OwnerMeta(vehicle, "cart", owner, Optional.empty(), Optional.empty()));
                return result;
            }
        };

        IOException failure = assertThrows(IOException.class, () -> migration(tearing).run());

        assertTrue(failure.getMessage().contains("column") && failure.getMessage().contains("payload"));
    }

    @Test
    void aDoneVehicleWithNeitherAStoredNorALiveCopyFailsTheStageAsTorn() throws IOException {
        api.stored("blank", "cart", "player_Dave");
        VehicleOwnerApi emptying = new Crashing(api, Integer.MAX_VALUE) {
            @Override
            public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
                Result result = super.write(vehicle, owner, whitelist, reason);
                delegate.vehicles.put(vehicle, new OwnerMeta(vehicle, "cart", null, Optional.empty(), Optional.empty()));
                return result;
            }
        };

        IOException failure = assertThrows(IOException.class, () -> migration(emptying).run());

        assertEquals("Stored and live vehicle owners disagree after migration: [blank]", failure.getMessage());
        assertFalse(plan().get("completed").getAsBoolean());
    }

    @Test
    void copiesThatAgreeOnSomethingElseAtVerificationAreQuarantined() throws IOException {
        api.stored("moved", "cart", "player_Dave").live("gone", "cart", "player_Dave");
        String staff = "char_" + UUID.randomUUID();
        VehicleOwnerApi movedOn = new Crashing(api, Integer.MAX_VALUE) {
            @Override
            public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
                Result result = super.write(vehicle, owner, whitelist, reason);
                if (vehicle.equals("moved")) delegate.stored("moved", "cart", staff);
                else delegate.vehicles.remove("gone");
                return result;
            }
        };

        VehicleOwnerMigration.Outcome outcome = migration(movedOn).run();

        assertEquals(1, outcome.quarantined());
        assertEquals(1, outcome.gone());
        assertEquals(staff, VehicleOwnerMigration.quarantine(folder).getFirst().owner());
    }

    @Test
    void anUnreadablePlanStopsTheStage() throws IOException {
        Files.createDirectories(folder.resolve("vehicles"));
        Path plan = folder.resolve(VehicleOwnerMigration.PLAN);
        for (String invalid : List.of("{\"entries\":null,\"quarantine\":[]}", "{\"entries\":[],\"quarantine\":null}",
                "{\"entries\":[null],\"quarantine\":[]}", "{\"entries\":[{\"status\":\"DONE\"}],\"quarantine\":[]}",
                "{\"entries\":[{\"vehicle\":\"v\"}],\"quarantine\":[]}",
                "{\"entries\":[{\"vehicle\":\"v\",\"status\":\"DONE\"}],\"quarantine\":[]}",
                "{\"entries\":[{\"vehicle\":\"v\",\"status\":\"DONE\",\"original\":{}}],\"quarantine\":[]}")) {
            Files.writeString(plan, invalid);
            assertThrows(IOException.class, () -> migration().run(), invalid);
        }
        Files.writeString(plan, "{\"entries\":[{\"vehicle\":\"v\",\"status\":\"QUARANTINED\"}],\"quarantine\":[],"
                + "\"completed\":true}");
        assertFalse(migration().run().ran());
    }

    @Test
    void withoutAPlanThereIsNothingQuarantinedOrMatched() throws IOException {
        assertEquals(List.of(), VehicleOwnerMigration.quarantine(folder));
        assertEquals(Map.of(), VehicleOwnerMigration.accounts(folder));
    }

    @Test
    void sqliteBackupsCopyAConsistentDatabaseAndCheckIt() throws Exception {
        // sqlite-jdbc logs through the server's slf4j bridge, which needs a logger first.
        org.slf4j.jul.JDK14LoggerFactory.LOGGER = java.util.logging.Logger.getAnonymousLogger();
        Path live = folder.resolve("vehicles.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE vehicles (uuid TEXT PRIMARY KEY, owner TEXT)");
            statement.execute("INSERT INTO vehicles VALUES ('v', 'player_Dave')");
            var sqlite = new VehicleOwnerMigration.SqliteBackups(live);
            Path copy = folder.resolve("it's a copy.db");
            sqlite.vacuumInto(copy);
            assertTrue(sqlite.quickCheck(copy));
            assertThrows(IOException.class, () -> sqlite.vacuumInto(copy), "VACUUM INTO never overwrites");
        }
        Path garbage = folder.resolve("garbage.db");
        Files.writeString(garbage, "not a database at all, just text that is long enough to have a header");
        var sqlite = new VehicleOwnerMigration.SqliteBackups(live);
        assertThrows(IOException.class, () -> sqlite.quickCheck(garbage));
        Path empty = folder.resolve("empty.db");
        assertTrue(sqlite.quickCheck(empty), "An empty database is consistent");
    }

}
