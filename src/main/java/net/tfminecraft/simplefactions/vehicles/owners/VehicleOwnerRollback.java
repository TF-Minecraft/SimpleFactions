package net.tfminecraft.simplefactions.vehicles.owners;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerMigration.Entry;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerMigration.Plan;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerMigration.Status;

/**
 * Stage 6a rollback for vehicles, run from the server folder with the server stopped:
 * <pre>java -cp &lt;SimpleFactions jar&gt;:&lt;gson jar&gt;:&lt;sqlite-jdbc jar&gt;
 *     net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerRollback &lt;server folder&gt; [--apply]</pre>
 * It restores only the owner fields (the SQLite column and the payload's {@code owner},
 * {@code whitelisted} and {@code whitelist}) of rows that still hold exactly what the
 * migration wrote; anything changed since is listed and left alone. It also lists the
 * claim, build and handover fees paid after the migration, for refunds. Without
 * {@code --apply} nothing is written.
 */
public final class VehicleOwnerRollback {
    static final String PAYMENTS = "vehicle-payments";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /** A fee paid after the migration. */
    public record Refund(String receipt, String scope, String payerCharacter, String payerAccount, String factionId,
            long cents, long at) {}

    public record Report(List<String> restored, List<String> changed, List<String> gone, List<Refund> refunds) {}

    private VehicleOwnerRollback() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !args[1].equals("--apply"))) {
            throw new IllegalArgumentException("Usage: VehicleOwnerRollback <server folder> [--apply]");
        }
        String missing = missingLibraries(JsonArray.class, "org.sqlite.JDBC");
        if (missing != null) throw new IllegalStateException(missing);
        run(Path.of(args[0]), args.length == 2, System.out);
    }

    /**
     * Why the classpath cannot run the tool, or null. Gson must be 2.10 or newer (records,
     * {@code JsonParser.parseString}); a server's {@code libraries/} may also hold older ones.
     */
    static String missingLibraries(Class<?> jsonArray, String sqliteDriver) {
        try {
            jsonArray.getMethod("asList");
        } catch (NoSuchMethodException old) {
            return "Gson 2.10 or newer is needed on the classpath (found an older one); use the newest"
                    + " libraries/com/google/code/gson/gson/<version>/gson-<version>.jar";
        }
        try {
            Class.forName(sqliteDriver);
        } catch (ClassNotFoundException | LinkageError absent) {
            return "sqlite-jdbc is needed on the classpath:"
                    + " libraries/org/xerial/sqlite-jdbc/<version>/sqlite-jdbc-<version>.jar";
        }
        return null;
    }

    public static Report run(Path server, boolean apply, PrintStream out) throws IOException {
        Path factions = server.resolve("plugins/SimpleFactions");
        Path planFile = factions.resolve(VehicleOwnerMigration.PLAN);
        Plan plan = DurableJson.read(planFile, Plan.class);
        Report report;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:"
                + server.resolve("plugins/VehicleFramework/data/vehicles.db").toAbsolutePath())) {
            // Fees count from when the plan was made, so an unfinished migration lists them too.
            report = restore(connection, plan, apply, refunds(factions.resolve(PAYMENTS), plan.createdAt));
        } catch (SQLException error) {
            throw new IOException("Could not roll back vehicle owners", error);
        }
        Path refundFile = factions.resolve("vehicles/refunds-" + System.currentTimeMillis() + ".json");
        if (apply) {
            DurableJson.write(refundFile, report.refunds());
            // A re-upgrade must plan afresh; the old plan is kept beside it for the record.
            Files.move(planFile, planFile.resolveSibling(planFile.getFileName() + ".rolled-back-"
                    + System.currentTimeMillis()), StandardCopyOption.ATOMIC_MOVE);
        }
        out.println((apply ? "Restored " : "Would restore ") + report.restored().size() + " vehicle owners");
        report.changed().forEach(vehicle -> out.println("Changed since the migration, left alone: " + vehicle));
        report.gone().forEach(vehicle -> out.println("No longer stored: " + vehicle));
        out.println(report.refunds().size() + " fees paid after the migration"
                + (apply ? ", listed in " + refundFile : ":"));
        if (!apply) report.refunds().forEach(refund -> out.println(PRETTY.toJson(refund)));
        return report;
    }

    static Report restore(Connection connection, Plan plan, boolean apply, List<Refund> refunds) throws SQLException {
        List<String> restored = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> gone = new ArrayList<>();
        connection.setAutoCommit(false);
        try {
            for (Entry entry : plan.entries) {
                // Pending entries may have been written before a crash; the row check keeps the rest safe.
                if (entry.status == Status.QUARANTINED || entry.planned == null
                        || entry.original.value().sameOwnership(entry.planned.value())) continue;
                Row row = row(connection, entry.vehicle);
                if (row == null) {
                    gone.add(entry.vehicle);
                } else if (!row.holds(entry.planned)) {
                    changed.add(entry.vehicle);
                } else {
                    write(connection, entry, row.payload());
                    restored.add(entry.vehicle);
                }
            }
            if (apply) connection.commit();
            else connection.rollback();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        }
        return new Report(restored, changed, gone, refunds);
    }

    /** A stored row's owner column and payload. */
    private record Row(String column, JsonObject payload) {
        /** Whether the column and payload still hold exactly the migration's values. */
        boolean holds(VehicleOwnerMigration.Fields planned) {
            List<String> whitelist = new ArrayList<>();
            if (payload.get("whitelist") instanceof JsonArray entries) {
                for (JsonElement entry : entries) whitelist.add(entry.getAsString());
            }
            return planned.owner.equals(column)
                    && planned.owner.equals(text(payload, "owner"))
                    && planned.whitelist.equals(whitelist)
                    && planned.whitelisted == (payload.has("whitelisted") && payload.get("whitelisted").getAsBoolean());
        }
    }

    private static Row row(Connection connection, String vehicle) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT owner, payload_json FROM vehicles WHERE uuid = ? AND deleted = 0")) {
            select.setString(1, vehicle);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) return null;
                return new Row(row.getString(1), JsonParser.parseString(row.getString(2)).getAsJsonObject());
            }
        }
    }

    private static void write(Connection connection, Entry entry, JsonObject payload) throws SQLException {
        payload.addProperty("owner", entry.original.owner);
        payload.addProperty("whitelisted", entry.original.whitelisted);
        JsonArray whitelist = new JsonArray();
        entry.original.whitelist.forEach(whitelist::add);
        payload.add("whitelist", whitelist);
        try (PreparedStatement update = connection.prepareStatement("UPDATE vehicles SET owner = ?, payload_json = ?,"
                + " revision = revision + 1, updated_at = ? WHERE uuid = ? AND deleted = 0")) {
            update.setString(1, entry.original.owner);
            update.setString(2, GSON.toJson(payload));
            update.setLong(3, System.currentTimeMillis());
            update.setString(4, entry.vehicle);
            update.executeUpdate();
        }
    }

    /**
     * Claim, build and handover fees whose charge completed at or after {@code since} and
     * was not refunded. Older journal entries without a time use the file's time.
     */
    static List<Refund> refunds(Path journal, long since) throws IOException {
        List<JsonObject> saved = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        if (Files.isDirectory(journal)) {
            try (Stream<Path> files = Files.list(journal)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                    saved.add(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                    times.add(Files.getLastModifiedTime(file).toMillis());
                }
            }
        }
        Set<String> refunded = new HashSet<>();
        for (JsonObject value : saved) {
            JsonObject changes = value.getAsJsonObject("intent").getAsJsonObject("changes");
            if (applied(value) && "refund".equals(text(changes, "action"))) {
                refunded.add(changes.getAsJsonObject("receipt").get("id").getAsString());
            }
        }
        List<Refund> out = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            JsonObject changes = saved.get(i).getAsJsonObject("intent").getAsJsonObject("changes");
            String scope = text(changes, "scope");
            if (!applied(saved.get(i)) || !"charge".equals(text(changes, "action")) || scope == null
                    || !(scope.startsWith("claim:") || scope.startsWith("build:") || scope.startsWith("handover:"))) {
                continue;
            }
            long at = changes.has("at") ? changes.get("at").getAsLong() : times.get(i);
            JsonObject receipt = changes.getAsJsonObject("receipt");
            String id = receipt.get("id").getAsString();
            if (at < since || refunded.contains(id)) continue;
            JsonObject payer = receipt.getAsJsonObject("payer");
            out.add(new Refund(id, scope, text(payer, "characterId"), text(payer, "ownerId"), text(receipt, "factionId"),
                    receipt.get("cents").getAsLong(), at));
        }
        return out;
    }

    private static boolean applied(JsonObject saved) {
        return "APPLIED".equals(text(saved, "completed"));
    }

    private static String text(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
}
