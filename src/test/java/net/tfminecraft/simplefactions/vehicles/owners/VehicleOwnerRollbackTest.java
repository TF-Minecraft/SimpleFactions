package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class VehicleOwnerRollbackTest {
    @TempDir Path server;
    private Path database;
    private Path factions;
    private final long migratedAt = 1_000_000L;
    private final String kira = "char_" + UUID.randomUUID();
    private final String bran = "char_" + UUID.randomUUID() + ";" + UUID.randomUUID() + ";Bran";

    @BeforeEach
    void setUp() throws Exception {
        org.slf4j.jul.JDK14LoggerFactory.LOGGER = java.util.logging.Logger.getAnonymousLogger();
        factions = server.resolve("plugins/SimpleFactions");
        database = server.resolve("plugins/VehicleFramework/data/vehicles.db");
        Files.createDirectories(database.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE vehicles (uuid TEXT PRIMARY KEY, owner TEXT NOT NULL, payload_json TEXT NOT NULL,"
                    + " revision INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        }
        row("restore", kira, "{\"owner\":\"" + kira + "\",\"whitelisted\":true,\"whitelist\":[\"" + bran
                + "\"],\"ticketsEnabled\":true,\"ticketId\":\"t9\",\"health\":27,\"x\":1.5,\"note\":\"<a&b>\"}", 0);
        row("changed", "char_staff", "{\"owner\":\"char_staff\",\"whitelisted\":false,\"whitelist\":[]}", 0);
        row("payload", kira, "{\"owner\":\"" + kira + "\",\"whitelisted\":false,\"whitelist\":[\"" + bran + "\"]}", 0);
        row("tombstoned", kira, "{\"owner\":\"" + kira + "\"}", 1);
        writePlan();
        Path journal = factions.resolve("vehicle-payments");
        Files.createDirectories(journal);
        intent(journal, "a", "charge", "claim:v1", "sf:vehicle:claim:1", migratedAt + 5, "APPLIED");
        intent(journal, "b", "charge", "build:s", "sf:vehicle:build:2", migratedAt - 5, "APPLIED");
        Files.setLastModifiedTime(intent(journal, "c", "charge", "handover:v2", "sf:vehicle:handover:3", null, "APPLIED"),
                FileTime.fromMillis(migratedAt + 10));
        Files.setLastModifiedTime(intent(journal, "c2", "charge", "handover:v4", "sf:vehicle:handover:4", null, "APPLIED"),
                FileTime.fromMillis(migratedAt - 10));
        intent(journal, "d", "charge", "claim:v3", "sf:vehicle:claim:5", migratedAt + 1, "APPLIED");
        intent(journal, "e", "refund", "claim:v3", "sf:vehicle:claim:5", null, "APPLIED");
        intent(journal, "f", "charge", "other:v", "sf:vehicle:other", migratedAt + 1, "APPLIED");
        intent(journal, "g", "charge", "claim:v5", "sf:vehicle:claim:6", migratedAt + 1, "REFUSED");
        intent(journal, "h", "maintenance", null, "sf:vehicle:m", null, "APPLIED");
        intent(journal, "i", "charge", "build:t", "sf:vehicle:build:7", migratedAt + 1, null);
        Files.writeString(journal.resolve("notes.txt"), "not an intent");
    }

    private void row(String uuid, String owner, String payload, int deleted) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO vehicles (uuid, owner, payload_json, revision, deleted, updated_at) VALUES (?, ?, ?, 4, ?, 0)")) {
            insert.setString(1, uuid);
            insert.setString(2, owner);
            insert.setString(3, payload);
            insert.setInt(4, deleted);
            insert.executeUpdate();
        }
    }

    private String entry(String vehicle, String status, String originalOwner, String plannedOwner, String plannedEntry,
            boolean plannedFlag) {
        return "{\"vehicle\":\"" + vehicle + "\",\"status\":\"" + status + "\","
                + "\"original\":{\"owner\":\"" + originalOwner + "\",\"whitelist\":[\"player_Bob\"],\"whitelisted\":true,"
                + "\"ticketsEnabled\":false},"
                + "\"planned\":{\"owner\":\"" + plannedOwner + "\",\"whitelist\":[" + plannedEntry + "],\"whitelisted\":"
                + plannedFlag + ",\"ticketsEnabled\":false}}";
    }

    private void writePlan() throws IOException {
        String quoted = "\"" + bran + "\"";
        String entries = String.join(",",
                entry("restore", "PENDING", "player_Alice", kira, quoted, true),
                entry("changed", "DONE", "player_Alice", kira, quoted, true),
                entry("payload", "DONE", "player_Alice", kira, quoted, true),
                entry("tombstoned", "DONE", "player_Alice", kira, quoted, true),
                entry("unchanged", "DONE", kira, kira, "\"player_Bob\"", true),
                "{\"vehicle\":\"held\",\"status\":\"QUARANTINED\"}");
        Files.createDirectories(factions.resolve("vehicles"));
        // An interrupted plan: no completion time, fees count from when it was made.
        Files.writeString(factions.resolve(VehicleOwnerMigration.PLAN), "{\"completed\":false,\"createdAt\":" + migratedAt
                + ",\"entries\":[" + entries + "],\"quarantine\":[]}");
    }

    private static Path intent(Path journal, String file, String action, String scope, String id, Long at,
            String completed) throws IOException {
        JsonObject changes = new JsonObject();
        changes.addProperty("action", action);
        if (scope != null) changes.addProperty("scope", scope);
        if (at != null) changes.addProperty("at", at);
        JsonObject receipt = new JsonObject();
        receipt.addProperty("id", id);
        JsonObject payer = new JsonObject();
        payer.addProperty("ownerId", "account-" + file);
        payer.addProperty("characterId", "character-" + file);
        receipt.add("payer", payer);
        receipt.addProperty("factionId", "red");
        receipt.addProperty("cents", 4000);
        if (!action.equals("maintenance")) changes.add("receipt", receipt);
        JsonObject intent = new JsonObject();
        intent.addProperty("id", id + (action.equals("refund") ? ":refund" : ""));
        intent.add("changes", changes);
        JsonObject saved = new JsonObject();
        saved.add("intent", intent);
        if (completed != null) saved.addProperty("completed", completed);
        Path path = journal.resolve(file + ".json");
        Files.writeString(path, saved.toString());
        return path;
    }

    private String[] stored(String uuid) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                PreparedStatement select = connection.prepareStatement(
                        "SELECT owner, payload_json, revision FROM vehicles WHERE uuid = ?")) {
            select.setString(1, uuid);
            try (ResultSet row = select.executeQuery()) {
                row.next();
                return new String[] {row.getString(1), row.getString(2), String.valueOf(row.getInt(3))};
            }
        }
    }

    @Test
    void aDryRunListsWhatWouldChangeAndTheRefundsWithoutWritingAnything() throws Exception {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();

        VehicleOwnerRollback.Report report = VehicleOwnerRollback.run(server, false,
                new PrintStream(printed, true, StandardCharsets.UTF_8));

        assertEquals(List.of("restore"), report.restored());
        assertEquals(List.of("changed", "payload"), report.changed());
        assertEquals(List.of("tombstoned"), report.gone());
        assertEquals(List.of("claim:v1", "handover:v2"), report.refunds().stream().map(VehicleOwnerRollback.Refund::scope).toList());
        VehicleOwnerRollback.Refund claim = report.refunds().getFirst();
        assertEquals(new VehicleOwnerRollback.Refund("sf:vehicle:claim:1", "claim:v1", "character-a", "account-a", "red",
                4000, migratedAt + 5), claim);
        assertEquals(migratedAt + 10, report.refunds().get(1).at(), "Older entries fall back to the file time");
        String output = printed.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Would restore 1 vehicle owners"));
        assertTrue(output.contains("Changed since the migration, left alone: changed"));
        assertTrue(output.contains("No longer stored: tombstoned"));
        assertTrue(output.contains("\"scope\": \"claim:v1\""));
        assertEquals(kira, stored("restore")[0]);
        assertTrue(Files.exists(factions.resolve(VehicleOwnerMigration.PLAN)));
    }

    @Test
    void applyingRestoresOnlyTheOwnerFieldsOfUntouchedRowsAndResetsThePlan() throws Exception {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();

        VehicleOwnerRollback.run(server, true, new PrintStream(printed, true, StandardCharsets.UTF_8));

        String[] restored = stored("restore");
        assertEquals("player_Alice", restored[0]);
        assertEquals("5", restored[2]);
        JsonObject payload = JsonParser.parseString(restored[1]).getAsJsonObject();
        assertEquals("player_Alice", payload.get("owner").getAsString());
        assertTrue(payload.get("whitelisted").getAsBoolean());
        assertEquals("player_Bob", payload.getAsJsonArray("whitelist").get(0).getAsString());
        assertTrue(payload.get("ticketsEnabled").getAsBoolean(), "Tickets are never touched");
        assertEquals("t9", payload.get("ticketId").getAsString());
        assertEquals(27, payload.get("health").getAsInt(), "Position, health and cargo stay as they are now");
        assertEquals("<a&b>", payload.get("note").getAsString());
        assertTrue(restored[1].contains("<a&b>"), "No HTML escaping is introduced");
        assertEquals("char_staff", stored("changed")[0]);
        assertFalse(Files.exists(factions.resolve(VehicleOwnerMigration.PLAN)), "A re-upgrade plans afresh");
        try (Stream<Path> files = Files.list(factions.resolve("vehicles"))) {
            List<String> names = files.map(path -> path.getFileName().toString()).sorted().toList();
            assertTrue(names.stream().anyMatch(name -> name.startsWith("owner-migration.json.rolled-back-")));
            Path refunds = factions.resolve("vehicles").resolve(names.stream()
                    .filter(name -> name.startsWith("refunds-")).findFirst().orElseThrow());
            assertTrue(Files.readString(refunds).contains("handover:v2"));
        }
        assertTrue(printed.toString(StandardCharsets.UTF_8).contains("Restored 1 vehicle owners"));
    }

    @Test
    void theCommandLineTakesTheServerFolderAndAnOptionalApply() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            VehicleOwnerRollback.main(new String[] {server.toString()});
        } finally {
            System.setOut(original);
        }
        assertTrue(printed.toString(StandardCharsets.UTF_8).contains("Would restore 1"));
        for (String[] wrong : List.of(new String[0], new String[] {"a", "--force"}, new String[] {"a", "--apply", "b"})) {
            assertThrows(IllegalArgumentException.class, () -> VehicleOwnerRollback.main(wrong));
        }
    }

    @Test
    void aDatabaseWithoutTheVehicleTableStopsWithNothingChanged() throws Exception {
        Files.delete(database);
        assertThrows(IOException.class, () -> VehicleOwnerRollback.run(server, true, new PrintStream(
                new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)));
        assertTrue(Files.exists(factions.resolve(VehicleOwnerMigration.PLAN)));
    }

    @Test
    void withoutAPaymentJournalThereIsNothingToRefund() throws IOException {
        assertEquals(List.of(), VehicleOwnerRollback.refunds(server.resolve("absent"), 0));
    }
}
