package net.tfminecraft.simplefactions.vehicles.owners;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.tfminecraft.simplefactions.database.DurableJson;

/**
 * Vehicles held back for staff: the owner migration's quarantine, vehicles stamped
 * {@code char_none} at construction because payer and constructor disagreed, and what
 * the reconciles found. Shown by {@code /vf admin quarantine}.
 */
public final class VehicleQuarantine {
    /** One listed vehicle; {@code owner} is its owner value when it was listed. */
    public record Entry(String vehicle, String owner, String reason) {}

    private static final class Stored {
        List<Entry> entries = new ArrayList<>();
    }

    private static volatile VehicleQuarantine active;

    private final Path runtimeFile;
    private List<Entry> migration = List.of();
    private List<Entry> runtime = List.of();
    private List<Entry> findings = List.of();

    public VehicleQuarantine(Path folder) {
        this.runtimeFile = folder.resolve("vehicles/quarantine.json");
    }

    public static void use(VehicleQuarantine quarantine) {
        active = quarantine;
    }

    /** The running server's list, or null before the vehicle stage. */
    public static VehicleQuarantine current() {
        return active;
    }

    public void load(List<Entry> migrationEntries) throws IOException {
        migration = List.copyOf(migrationEntries);
        if (Files.exists(runtimeFile)) {
            Stored stored = DurableJson.read(runtimeFile, Stored.class);
            runtime = stored.entries == null ? List.of() : List.copyOf(stored.entries);
        }
    }

    /** Lists a vehicle for staff and saves the list. */
    public void add(Entry entry) throws IOException {
        List<Entry> next = new ArrayList<>(runtime);
        next.add(entry);
        Stored stored = new Stored();
        stored.entries = next;
        DurableJson.write(runtimeFile, stored);
        runtime = List.copyOf(next);
    }

    public void setFindings(List<Entry> next) {
        findings = List.copyOf(next);
    }

    public List<Entry> findings() {
        return findings;
    }

    /**
     * Held-back vehicles that still carry the owner they were listed with (staff have not
     * since given them an owner), then the reconcile findings.
     */
    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : concat(migration, runtime)) {
            String current = VehicleOwnerWrites.currentOwner(entry.vehicle());
            if (current != null && Objects.equals(current, entry.owner())) out.add(entry);
        }
        out.addAll(findings);
        return out;
    }

    private static List<Entry> concat(List<Entry> first, List<Entry> second) {
        List<Entry> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
