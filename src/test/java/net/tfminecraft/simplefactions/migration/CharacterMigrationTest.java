package net.tfminecraft.simplefactions.migration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;

class CharacterMigrationTest {
    @TempDir
    Path root;

    private final Logger logger = mock(Logger.class);
    private final Map<String, UUID> accounts = new HashMap<>();
    private final Map<UUID, CharacterRef> heirs = new HashMap<>();
    private int heirLookups;

    private final CharacterMigration.Heirs fakeHeirs = new CharacterMigration.Heirs() {
        @Override
        public Optional<UUID> account(String name) {
            return Optional.ofNullable(accounts.get(name.toLowerCase(java.util.Locale.ROOT)));
        }

        @Override
        public Optional<CharacterRef> heir(UUID account) {
            heirLookups++;
            return Optional.ofNullable(heirs.get(account));
        }
    };

    /** An account with a reviewed heir; returns the heir's member key. */
    private String player(String... names) {
        UUID account = UUID.randomUUID();
        for (String name : names) accounts.put(name.toLowerCase(java.util.Locale.ROOT), account);
        CharacterRef heir = new CharacterRef(account, UUID.randomUUID());
        heirs.put(account, heir);
        return Members.key(heir);
    }

    /** An account the server knows, but with no reviewed heir. */
    private UUID heirless(String name) {
        UUID account = UUID.randomUUID();
        accounts.put(name.toLowerCase(java.util.Locale.ROOT), account);
        return account;
    }

    private CharacterMigration migration() {
        return new CharacterMigration(root, fakeHeirs, logger);
    }

    private void write(String store, String json) throws IOException {
        Path file = root.resolve(store);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private JsonObject read(String store) throws IOException {
        return JsonParser.parseString(Files.readString(root.resolve(store))).getAsJsonObject();
    }

    private static List<String> strings(JsonElement array) {
        return array.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
    }

    private static final String FACTION = """
            {
              "id": "rome", "leader": "Caesar", "leader character": "Gaius", "leader character of": "Caesar",
              "guilds": [
                {"id": "rome", "leader": "Caesar", "members": ["Caesar", "Brutus", "OldBrutus", "Ghost"],
                 "dividendEligible": ["Caesar", "Brutus", "OldBrutus"],
                 "citizen taxes": {"Brutus": 2.5, "OldBrutus": 1.5, "Ghost": 4.0},
                 "deposits today": {"Caesar": 10},
                 "ledger last day": {"CITIZENS": {"Brutus": 1.0, "oldbrutus": 2.0}, "GUILD_TAXES": {"smiths": 3.0},
                                     "DEPOSITS": {"Caesar": 5.0}},
                 "ledger lifetime": {"CITIZENS": {"Ghost": 7.0}},
                 "company": {"enlisted": ["Brutus", "OldBrutus"], "activeWageOverrides": {"Brutus": 5.0, "OldBrutus": 9.0},
                             "peacetimeWageOverrides": {"Ghost": 1.0}, "pendingWages": {"Brutus": 3.0, "OldBrutus": 4.0}}},
                {"id": "smiths", "leader": "Ghost", "members": ["Ghost", "Cassius"], "leader character": "Spooky"}
              ],
              "governmentData": {
                "council members": ["Brutus", "Cassius"],
                "eligible voters": ["Caesar", "Brutus", "OldBrutus"],
                "election candidates": {"LEADER": ["Brutus", "OldBrutus"]},
                "election votes": {"LEADER": {"Caesar": "Brutus", "Brutus": "Cassius", "OldBrutus": "Caesar"}},
                "previous votes": {"COUNCIL": {"Brutus": 2, "OldBrutus": 3}},
                "proposals": ["Brutus:law:economy:free_trade", "Cassius:action:CHANGE_LEADER:Brutus", "broken", "Ghost:tax:CITIZENS:x:5.0"],
                "movements": [
                  {"id": "m1", "leader": "Cassius", "supporters": {"citizens": ["Cassius", "Brutus"], "guilds": ["smiths"]},
                   "causes": [{"leader": "Cassius", "members": {"citizens": ["Cassius"]},
                               "proposal": {"proposer": "Cassius", "action key": "CHANGE_LEADER", "target": "Brutus"}},
                              {"leader": "Brutus", "proposal": {"proposer": "Brutus", "action key": "END_WAR", "target": "gaul"}}]}
                ]
              }
            }
            """;

    @Test
    void migratesEveryNameFieldOfARealmAndMergesNamesOfOneAccount() throws IOException {
        String caesar = player("Caesar");
        String brutus = player("Brutus", "OldBrutus");
        String cassius = player("Cassius");
        write("Data/rome.json", FACTION);

        CharacterMigration.Result result = migration().run();
        assertEquals(1, result.migrated());

        JsonObject rome = read("Data/rome.json");
        assertEquals(caesar, rome.get("leader").getAsString());
        assertFalse(rome.has("leader character"));
        assertFalse(rome.has("leader character of"));
        JsonObject base = rome.getAsJsonArray("guilds").get(0).getAsJsonObject();
        JsonObject smiths = rome.getAsJsonArray("guilds").get(1).getAsJsonObject();
        String ghost = smiths.get("leader").getAsString();
        assertTrue(Members.isQuarantined(ghost));
        // The quarantined leader of the smiths no longer also sits in the realm's own guild.
        assertEquals(List.of(caesar, brutus), strings(base.get("members")));
        assertEquals(List.of(caesar, brutus), strings(base.get("dividendEligible")));
        assertEquals(4.0, base.getAsJsonObject("citizen taxes").get(brutus).getAsDouble());
        assertEquals(4.0, base.getAsJsonObject("citizen taxes").get(ghost).getAsDouble());
        assertEquals(10, base.getAsJsonObject("deposits today").get(caesar).getAsLong());
        JsonObject lastDay = base.getAsJsonObject("ledger last day");
        assertEquals(3.0, lastDay.getAsJsonObject("CITIZENS").get(brutus).getAsDouble());
        assertEquals(3.0, lastDay.getAsJsonObject("GUILD_TAXES").get("smiths").getAsDouble());
        assertEquals(5.0, lastDay.getAsJsonObject("DEPOSITS").get(caesar).getAsDouble());
        assertEquals(7.0, base.getAsJsonObject("ledger lifetime").getAsJsonObject("CITIZENS").get(ghost).getAsDouble());
        JsonObject company = base.getAsJsonObject("company");
        assertEquals(List.of(brutus), strings(company.get("enlisted")));
        assertEquals(5.0, company.getAsJsonObject("activeWageOverrides").get(brutus).getAsDouble());
        assertEquals(1.0, company.getAsJsonObject("peacetimeWageOverrides").get(ghost).getAsDouble());
        assertEquals(7.0, company.getAsJsonObject("pendingWages").get(brutus).getAsDouble());
        assertNotNull(base.get("incarnation").getAsString());

        assertEquals(List.of(ghost, cassius), strings(smiths.get("members")));
        assertFalse(smiths.has("leader character"));
        assertNotEquals(base.get("incarnation"), smiths.get("incarnation"));

        JsonObject government = rome.getAsJsonObject("governmentData");
        assertEquals(List.of(brutus, cassius), strings(government.get("council members")));
        assertEquals(List.of(caesar, brutus), strings(government.get("eligible voters")));
        assertEquals(List.of(brutus), strings(government.getAsJsonObject("election candidates").get("LEADER")));
        JsonObject ballots = government.getAsJsonObject("election votes").getAsJsonObject("LEADER");
        assertEquals(brutus, ballots.get(caesar).getAsString());
        assertEquals(cassius, ballots.get(brutus).getAsString());
        assertEquals(2, ballots.size());
        assertEquals(5, government.getAsJsonObject("previous votes").getAsJsonObject("COUNCIL").get(brutus).getAsLong());
        assertEquals(List.of(brutus + ":law:economy:free_trade", cassius + ":action:CHANGE_LEADER:" + brutus,
                "broken", ghost + ":tax:CITIZENS:x:5.0"), strings(government.get("proposals")));
        JsonObject movement = government.getAsJsonArray("movements").get(0).getAsJsonObject();
        assertEquals(cassius, movement.get("leader").getAsString());
        assertEquals(List.of(cassius, brutus), strings(movement.getAsJsonObject("supporters").get("citizens")));
        assertEquals(List.of("smiths"), strings(movement.getAsJsonObject("supporters").get("guilds")));
        JsonObject cause = movement.getAsJsonArray("causes").get(0).getAsJsonObject();
        assertEquals(cassius, cause.get("leader").getAsString());
        assertEquals(List.of(cassius), strings(cause.getAsJsonObject("members").get("citizens")));
        assertEquals(cassius, cause.getAsJsonObject("proposal").get("proposer").getAsString());
        assertEquals(brutus, cause.getAsJsonObject("proposal").get("target").getAsString());
        JsonObject warEnd = movement.getAsJsonArray("causes").get(1).getAsJsonObject();
        assertEquals("gaul", warEnd.getAsJsonObject("proposal").get("target").getAsString());

        Quarantine quarantine = Quarantine.load(root.resolve(CharacterMigration.QUARANTINE));
        assertEquals(1, quarantine.records().size());
        Quarantine.Record record = quarantine.records().iterator().next();
        assertEquals(Quarantine.NAME, record.kind);
        assertEquals("Ghost", record.original);
        assertEquals("Data/rome.json", record.store);
        assertEquals(ghost, record.token);
        assertTrue(record.holdsLeader());
        assertTrue(record.occurrences.stream().anyMatch(o -> o.path.equals("$.guilds[smiths].leader")));
        assertTrue(record.occurrences.stream().anyMatch(o -> o.path.equals("$.guilds[rome].citizen taxes[Ghost]")
                && o.fragment.getAsJsonObject().get("Ghost").getAsDouble() == 4.0));

        assertArrayEquals(FACTION.getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(root.resolve("migrations/backup/Data/rome.json")));
        MigrationManifest manifest = MigrationManifest.load(root.resolve(CharacterMigration.MANIFEST));
        assertTrue(manifest.complete);
        assertEquals(MigrationManifest.DONE, manifest.entry("Data/rome.json").state);
        assertEquals(MigrationFiles.sha256(FACTION), manifest.entry("Data/rome.json").hash);
    }

    @Test
    void incarnationsAreStableAcrossARedo() throws IOException {
        player("Caesar", "Brutus", "OldBrutus", "Cassius", "Ghost");
        write("Data/rome.json", FACTION);
        migration().run();
        String first = read("Data/rome.json").getAsJsonArray("guilds").get(0).getAsJsonObject().get("incarnation").getAsString();
        Files.writeString(root.resolve("Data/rome.json"), FACTION);
        Files.delete(root.resolve(CharacterMigration.MANIFEST));
        migration().run();
        assertEquals(first, read("Data/rome.json").getAsJsonArray("guilds").get(0).getAsJsonObject().get("incarnation").getAsString());
    }

    @Test
    void keepsAnExistingIncarnationAndLeavesKeysAndTokensAlone() throws IOException {
        String key = Members.key(UUID.randomUUID());
        write("Data/gaul.json", "{\"id\":\"gaul\",\"leader\":\"" + key + "\",\"guilds\":[{\"id\":\"gaul\",\"incarnation\":\"fixed\","
                + "\"leader\":\"" + key.toUpperCase() + "\",\"members\":[\"quarantine:q1\"]}]}");
        migration().run();
        JsonObject gaul = read("Data/gaul.json");
        JsonObject guild = gaul.getAsJsonArray("guilds").get(0).getAsJsonObject();
        assertEquals("fixed", guild.get("incarnation").getAsString());
        assertEquals(key, guild.get("leader").getAsString());
        assertEquals(List.of("quarantine:q1"), strings(guild.get("members")));
        assertEquals(0, heirLookups);
    }

    @Test
    void aCharacterInSeveralGuildsOfARealmKeepsOnlyOne() throws IOException {
        String caesar = player("Caesar");
        String brutus = player("Brutus", "OldBrutus");
        String cassius = player("Cassius", "Cass");
        write("Data/rome.json", """
                {"id":"rome","leader":"Caesar","guilds":[
                  {"id":"rome","leader":"Caesar","members":["Caesar","Brutus","Cass", 5]},
                  {"id":"smiths","leader":"Cassius","members":["Cassius","OldBrutus"]},
                  {"id":"bakers","leader":"Brutus","members":["Brutus","Cass"]},
                  {"id":"empty"}]}
                """);
        migration().run();
        JsonArray guilds = read("Data/rome.json").getAsJsonArray("guilds");
        assertEquals(List.of(caesar), strings(guilds.get(0).getAsJsonObject().get("members")).subList(0, 1));
        assertEquals(2, guilds.get(0).getAsJsonObject().getAsJsonArray("members").size());
        assertEquals(List.of(cassius), strings(guilds.get(1).getAsJsonObject().get("members")));
        assertEquals(List.of(brutus), strings(guilds.get(2).getAsJsonObject().get("members")));
        org.mockito.Mockito.verify(logger, org.mockito.Mockito.atLeastOnce())
                .warning(org.mockito.ArgumentMatchers.contains("several guilds"));
    }

    @Test
    void migratesWarsLoginsAndVehicleStoresAndLeavesCombatFilesAlone() throws IOException {
        String brutus = player("Brutus", "OldBrutus");
        String cassius = player("Cassius");
        UUID orphanAccount = heirless("Orphan");
        CharacterRef brutusHeir = heirs.get(accounts.get("brutus"));
        write("Wars/war_1.json", "{\"id\":1,\"civilWarWantedLeaderName\":\"Brutus\",\"civilWarMemberMoves\":"
                + "[{\"player\":\"Cassius\",\"originGuildId\":\"smiths\"},{\"player\":\"Orphan\"}],\"battleVotes\":{\"x\":[1]}}");
        write("Wars/notes.txt", "ignored");
        write("Cache/logins.json", "{\"brutus\":4,\"oldbrutus\":1,\"cassius\":2}");
        write("Cache/vehicle_fees.json", "{\"lastOwners\":{\"v1\":\"Brutus\",\"v2\":\"Orphan\"},\"paidBuilds\":[{\"payerUuid\":\""
                + brutusHeir.ownerId() + "\",\"amount\":5},{\"payerUuid\":\"" + orphanAccount + "\",\"amount\":2},{\"payerUuid\":\"nope\"},{}]}");
        write("Cache/vehicles_registry.json", "[{\"playerUuid\":\"" + brutusHeir.ownerId() + "\",\"vehicleUuid\":\"v1\"},"
                + "{\"vehicleUuid\":\"v3\"}, 7]");
        String warband = "{\"id\":\"w\",\"leaderId\":\"" + UUID.randomUUID() + "\"}";
        write("Warbands/warband_w.json", warband);
        write("Battles/battle_b.json", "{\"id\":\"b\"}");

        migration().run();

        JsonObject war = read("Wars/war_1.json");
        assertEquals(brutus, war.get("civilWarWantedLeaderName").getAsString());
        JsonArray moves = war.getAsJsonArray("civilWarMemberMoves");
        assertEquals(cassius, moves.get(0).getAsJsonObject().get("player").getAsString());
        assertTrue(Members.isQuarantined(moves.get(1).getAsJsonObject().get("player").getAsString()));
        JsonObject logins = read("Cache/logins.json");
        assertEquals(1.0, logins.get(brutus).getAsDouble());
        assertEquals(2, logins.get(cassius).getAsInt());
        JsonObject fees = read("Cache/vehicle_fees.json");
        assertEquals(brutus, fees.getAsJsonObject("lastOwners").get("v1").getAsString());
        assertTrue(Members.isQuarantined(fees.getAsJsonObject("lastOwners").get("v2").getAsString()));
        JsonArray builds = fees.getAsJsonArray("paidBuilds");
        assertEquals(brutusHeir.characterId().toString(), builds.get(0).getAsJsonObject().get("payerUuid").getAsString());
        assertEquals(orphanAccount.toString(), builds.get(1).getAsJsonObject().get("payerUuid").getAsString());
        assertEquals("nope", builds.get(2).getAsJsonObject().get("payerUuid").getAsString());
        JsonArray registry = JsonParser.parseString(Files.readString(root.resolve("Cache/vehicles_registry.json"))).getAsJsonArray();
        assertEquals(brutusHeir.characterId().toString(), registry.get(0).getAsJsonObject().get("playerUuid").getAsString());
        assertEquals(warband, Files.readString(root.resolve("Warbands/warband_w.json")));

        Quarantine quarantine = Quarantine.load(root.resolve(CharacterMigration.QUARANTINE));
        Quarantine.Record account = quarantine.records().stream()
                .filter(record -> Quarantine.ACCOUNT.equals(record.kind)).findFirst().orElseThrow();
        assertEquals(orphanAccount.toString(), account.token);
        assertEquals("Cache/vehicle_fees.json", account.store);
        assertFalse(account.holdsLeader());
        MigrationManifest manifest = MigrationManifest.load(root.resolve(CharacterMigration.MANIFEST));
        assertEquals(MigrationManifest.DONE, manifest.entry("Warbands/warband_w.json").state);
        assertEquals(MigrationManifest.DONE, manifest.entry("Battles/battle_b.json").state);
    }

    @Test
    void anInterruptedStoreIsRestoredFromItsBackupAndRedone() throws IOException {
        String caesar = player("Caesar");
        String original = "{\"id\":\"rome\",\"leader\":\"Caesar\",\"guilds\":[]}";
        write("Data/rome.json", original);
        write("migrations/backup/Data/rome.json", original);
        MigrationManifest manifest = new MigrationManifest();
        manifest.mark("Data/rome.json", MigrationManifest.IN_PROGRESS, "migrations/backup/Data/rome.json",
                MigrationFiles.sha256(original));
        manifest.mark("Data/done.json", MigrationManifest.DONE, "migrations/backup/Data/done.json", "x");
        manifest.save(root.resolve(CharacterMigration.MANIFEST));
        // A half-finished earlier attempt already rewrote the store.
        write("Data/rome.json", "{\"id\":\"rome\",\"leader\":\"" + Members.key(UUID.randomUUID()) + "\"}");
        write("Data/done.json", "{\"id\":\"done\",\"leader\":\"Caesar\"}");

        CharacterMigration.Result result = migration().run();

        assertEquals(1, result.migrated());
        assertEquals(caesar, read("Data/rome.json").get("leader").getAsString());
        assertEquals("Caesar", read("Data/done.json").get("leader").getAsString());
        org.mockito.Mockito.verify(logger).warning(org.mockito.ArgumentMatchers.contains("Restored Data/rome.json"));
    }

    @Test
    void anInterruptedStoreWithoutAGoodBackupStopsTheMigration() throws IOException {
        write("Data/rome.json", "{\"id\":\"rome\"}");
        MigrationManifest manifest = new MigrationManifest();
        manifest.mark("Data/rome.json", MigrationManifest.IN_PROGRESS, "migrations/backup/Data/rome.json", "abc");
        manifest.save(root.resolve(CharacterMigration.MANIFEST));
        assertThrows(IOException.class, () -> migration().run());
        write("migrations/backup/Data/rome.json", "{\"id\":\"other\"}");
        assertThrows(IOException.class, () -> migration().run());
        assertFalse(MigrationManifest.load(root.resolve(CharacterMigration.MANIFEST)).complete);
    }

    @Test
    void aCompletedMigrationNeverRunsAgain() throws IOException {
        player("Caesar");
        write("Data/rome.json", "{\"id\":\"rome\",\"leader\":\"Caesar\"}");
        CharacterMigration migration = migration();
        assertFalse(migration.complete());
        migration.run();
        assertTrue(migration.complete());
        String migrated = Files.readString(root.resolve("Data/rome.json"));
        write("Data/new.json", "{\"id\":\"new\",\"leader\":\"Caesar\"}");
        assertEquals(new CharacterMigration.Result(0, 0, 0, 0), migration.run());
        assertEquals(migrated, Files.readString(root.resolve("Data/rome.json")));
        assertEquals("Caesar", read("Data/new.json").get("leader").getAsString());
    }

    @Test
    void anUnreadableStoreIsLeftAsItIsAndReported() throws IOException {
        write("Data/broken.json", "{not json");
        write("Data/list.json", "[1,2]");
        CharacterMigration.Result result = migration().run();
        assertEquals(1, result.skipped());
        assertEquals("{not json", Files.readString(root.resolve("Data/broken.json")));
        assertEquals(MigrationManifest.UNREADABLE,
                MigrationManifest.load(root.resolve(CharacterMigration.MANIFEST)).entry("Data/broken.json").state);
    }

    @Test
    void aFreshServerCompletesWithNothingToDo() throws IOException {
        assertEquals(new CharacterMigration.Result(0, 0, 0, 0), migration().run());
        assertTrue(migration().complete());
    }

    @Test
    void staffAssignmentsReplaceTheTokenEverywhereAtTheNextStart() throws IOException {
        String caesar = player("Caesar");
        String brutus = player("Brutus");
        write("Data/rome.json", """
                {"id":"rome","leader":"Ghost","guilds":[
                  {"id":"rome","leader":"Ghost","members":["Ghost","Brutus"],"citizen taxes":{"Ghost":2.0,"Brutus":1.0},
                   "company":{"pendingWages":{"Ghost":3.0},"activeWageOverrides":{"Ghost":2.0,"Brutus":7.0}}}],
                 "governmentData":{"election votes":{"LEADER":{"Ghost":"Brutus","Brutus":"Ghost"}},
                   "previous votes":{"LEADER":{"Ghost":2,"Brutus":1}},"election candidates":{"LEADER":["Ghost","Brutus"]}}}
                """);
        write("Cache/logins.json", "{\"ghost\":5,\"brutus\":3}");
        migration().run();
        Path quarantineFile = root.resolve(CharacterMigration.QUARANTINE);
        Quarantine quarantine = Quarantine.load(quarantineFile);
        assertEquals(2, quarantine.records().size());
        for (Quarantine.Record record : quarantine.records()) record.assigned = brutus;
        quarantine.save();

        CharacterMigration.Result result = migration().run();

        assertEquals(2, result.assignmentsApplied());
        JsonObject rome = read("Data/rome.json");
        assertEquals(brutus, rome.get("leader").getAsString());
        JsonObject base = rome.getAsJsonArray("guilds").get(0).getAsJsonObject();
        assertEquals(List.of(brutus), strings(base.get("members")));
        assertEquals(3.0, base.getAsJsonObject("citizen taxes").get(brutus).getAsDouble());
        assertEquals(3.0, base.getAsJsonObject("company").getAsJsonObject("pendingWages").get(brutus).getAsDouble());
        assertEquals(2.0, base.getAsJsonObject("company").getAsJsonObject("activeWageOverrides").get(brutus).getAsDouble());
        JsonObject government = rome.getAsJsonObject("governmentData");
        assertEquals(brutus, government.getAsJsonObject("election votes").getAsJsonObject("LEADER").get(brutus).getAsString());
        assertEquals(3, government.getAsJsonObject("previous votes").getAsJsonObject("LEADER").get(brutus).getAsLong());
        assertEquals(List.of(brutus), strings(government.getAsJsonObject("election candidates").get("LEADER")));
        assertEquals(3, read("Cache/logins.json").get(brutus).getAsInt());
        assertNotEquals(caesar, brutus);
        Quarantine after = Quarantine.load(quarantineFile);
        assertTrue(after.records().stream().allMatch(record -> record.applied));
        Quarantine.Record first = after.records().iterator().next();
        assertTrue(Files.exists(root.resolve("migrations/backup/assign-" + first.id + "/" + first.store)));
        assertEquals(0, migration().run().assignmentsApplied());
    }

    @Test
    void anAssignmentWhoseStoreIsGoneOrNoLongerHoldsTheTokenIsStillMarkedApplied() throws IOException {
        Path quarantineFile = root.resolve(CharacterMigration.QUARANTINE);
        write("Data/rome.json", "{\"id\":\"rome\"}");
        migration().run();
        Quarantine quarantine = Quarantine.load(quarantineFile);
        Quarantine.Record gone = new Quarantine.Record();
        gone.id = "qgone";
        gone.store = "Data/gone.json";
        gone.token = "quarantine:qgone";
        gone.original = "Gone";
        gone.assigned = Members.key(UUID.randomUUID());
        Quarantine.Record moved = new Quarantine.Record();
        moved.id = "qmoved";
        moved.store = "Data/rome.json";
        moved.token = "quarantine:qmoved";
        moved.original = "Moved";
        moved.assigned = gone.assigned;
        Quarantine.Record open = new Quarantine.Record();
        open.id = "qopen";
        open.store = "Data/rome.json";
        open.token = "quarantine:qopen";
        open.original = "Open";
        quarantine.merge(List.of(gone, moved, open));
        quarantine.save();
        CharacterMigration.Result result = migration().run();
        assertEquals(2, result.assignmentsApplied());
        assertEquals(1, result.quarantined());
        assertFalse(Files.exists(root.resolve("migrations/backup/assign-qmoved/Data/rome.json")));
    }

    @Test
    void aRedoneStoreKeepsEarlierAssignments() throws IOException {
        Quarantine quarantine = Quarantine.load(root.resolve("q.json"));
        Quarantine.Record record = new Quarantine.Record();
        record.id = "q1";
        record.assigned = "abc";
        record.applied = true;
        quarantine.merge(List.of(record));
        Quarantine.Record again = new Quarantine.Record();
        again.id = "q1";
        quarantine.merge(List.of(again));
        assertEquals("abc", quarantine.get("q1").assigned);
        assertTrue(quarantine.get("q1").applied);
        assertNull(quarantine.get(null));
        assertNull(quarantine.get("missing"));
    }

    @Test
    void unreadableManifestsAndQuarantinesAreErrors() throws IOException {
        write(CharacterMigration.MANIFEST, "{oops");
        assertThrows(IOException.class, () -> migration().run());
        write(CharacterMigration.MANIFEST, "null");
        assertThrows(IOException.class, () -> migration().complete());
        Files.delete(root.resolve(CharacterMigration.MANIFEST));
        write(CharacterMigration.QUARANTINE, "{oops");
        assertThrows(IOException.class, () -> migration().run());
        write(CharacterMigration.QUARANTINE, "null");
        assertThrows(IOException.class, () -> Quarantine.load(root.resolve(CharacterMigration.QUARANTINE)));
    }

    @Test
    void replaceMergesByTheFieldsMeaning() {
        assertEquals(StoreMapper.Merge.MIN, CharacterMigration.mergeFor(""));
        assertEquals(StoreMapper.Merge.SUM, CharacterMigration.mergeFor("/guilds/pendingWages"));
        assertEquals(StoreMapper.Merge.SUM, CharacterMigration.mergeFor("/governmentData/previous votes/LEADER"));
        assertEquals(StoreMapper.Merge.FIRST, CharacterMigration.mergeFor("/governmentData/election votes/LEADER"));
        assertTrue(CharacterMigration.memberList("/guilds/members"));
        assertTrue(CharacterMigration.memberList("/governmentData/election candidates/LEADER"));
        assertFalse(CharacterMigration.memberList("/governmentData/proposals"));
        JsonElement tree = JsonParser.parseString("{\"a\":[\"t\",\"t\",1],\"b\":{\"x\":\"y\"}}");
        assertTrue(CharacterMigration.replace(tree, "t", "k", ""));
        assertEquals("{\"a\":[\"k\",\"k\",1],\"b\":{\"x\":\"y\"}}", tree.toString());
        assertFalse(CharacterMigration.replace(tree, "zzz", "k", ""));
        assertEquals(new com.google.gson.JsonPrimitive(1.5), StoreMapper.merge(
                new com.google.gson.JsonPrimitive(1), new com.google.gson.JsonPrimitive(0.5), StoreMapper.Merge.SUM));
        assertEquals(new com.google.gson.JsonPrimitive("a"), StoreMapper.merge(
                new com.google.gson.JsonPrimitive("a"), new com.google.gson.JsonPrimitive(2), StoreMapper.Merge.SUM));
    }

    @Test
    void aRealmFileWithoutItsOwnGuildStillMigrates() throws IOException {
        String caesar = player("Caesar");
        write("Data/rome.json", "{\"id\":\"rome\",\"leader\":\"Caesar\",\"guilds\":[{\"id\":\"smiths\",\"leader\":\"Caesar\","
                + "\"members\":[\"Caesar\"]}]}");
        write("Data/nameless.json", "{\"guilds\":[{\"leader\":\"Caesar\"}]}");
        migration().run();
        assertEquals(caesar, read("Data/rome.json").getAsJsonArray("guilds").get(0).getAsJsonObject().get("leader").getAsString());
        assertEquals(caesar, read("Data/nameless.json").getAsJsonArray("guilds").get(0).getAsJsonObject().get("leader").getAsString());
    }

    @Test
    void sharedHeirsAskDenarEconomyThenTheUserCacheThenTheIdentityProvider() {
        try (var identity = new net.tfminecraft.simplefactions.testsupport.SharedIdentity();
                var names = org.mockito.Mockito.mockStatic(net.tfminecraft.denareconomy.accounts.OfflineModifier.class)) {
            UUID known = UUID.randomUUID();
            names.when(() -> net.tfminecraft.denareconomy.accounts.OfflineModifier.playerId("Known")).thenReturn(known);
            assertEquals(Optional.of(known), CharacterMigration.SHARED.account("Known"));
            UUID cached = identity.account("Cached");
            assertEquals(Optional.of(cached), CharacterMigration.SHARED.account("Cached"));
            assertEquals(Optional.empty(), CharacterMigration.SHARED.account("Nobody"));
            names.when(() -> net.tfminecraft.denareconomy.accounts.OfflineModifier.playerId("Cached"))
                    .thenThrow(new NoClassDefFoundError("DenarEconomy"));
            assertEquals(Optional.of(cached), CharacterMigration.SHARED.account("Cached"));
            CharacterRef heir = identity.character(cached, "Heir");
            identity.heirs.put(cached, heir);
            assertEquals(Optional.of(heir), CharacterMigration.SHARED.heir(cached));
            assertEquals(Optional.empty(), CharacterMigration.SHARED.heir(known));
        }
    }

    @Test
    void writesFallBackWhenTheFilesystemCannotMoveAtomically() throws IOException {
        Path target = root.resolve("nested/file.json");
        try (var files = org.mockito.Mockito.mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("move")
                    && java.util.Arrays.asList((java.nio.file.CopyOption[]) call.getRawArguments()[2])
                            .contains(java.nio.file.StandardCopyOption.ATOMIC_MOVE)) {
                throw new java.nio.file.AtomicMoveNotSupportedException("a", "b", "no atomic moves here");
            }
            return call.callRealMethod();
        })) {
            MigrationFiles.write(target, "{}");
        }
        assertEquals("{}", Files.readString(target));
        try (var stream = Files.list(target.getParent())) {
            assertEquals(1, stream.count(), "no temporary file is left behind");
        }
    }

    @Test
    void aMissingHashAlgorithmIsAnError() {
        try (var digests = org.mockito.Mockito.mockStatic(java.security.MessageDigest.class)) {
            digests.when(() -> java.security.MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new java.security.NoSuchAlgorithmException("none"));
            assertThrows(IllegalStateException.class, () -> MigrationFiles.sha256("x"));
        }
    }

    @Test
    void characterKeysAreLowerCaseCharacterIds() {
        UUID id = UUID.randomUUID();
        assertEquals(id.toString(), CharacterMigration.characterKey(" " + id.toString().toUpperCase() + " "));
        assertNull(CharacterMigration.characterKey("Brutus"));
        assertNull(CharacterMigration.characterKey(null));
    }
}
