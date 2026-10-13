package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.simplefactions.vehicles.owners.VehicleQuarantine.Entry;

class VehicleQuarantineTest {
    @TempDir Path folder;

    @Test
    void heldBackVehiclesAreListedUntilStaffGiveThemAnOwnerAndSurviveRestarts() throws IOException {
        FakeOwnerApi api = FakeOwnerApi.install().stored("legacy", "cart", "player_Old")
                .stored("built", "cart", "char_none").stored("fixed", "cart", "char_none");
        VehicleQuarantine quarantine = new VehicleQuarantine(folder);
        quarantine.load(List.of(new Entry("legacy", "player_Old", "renamed"), new Entry("fixed", "player_Old", "x"),
                new Entry("gone", "player_Old", "removed")));
        quarantine.add(new Entry("built", "char_none", "payer is not the constructor"));
        quarantine.setFindings(List.of(new Entry("row", null, "reconcile: registry row has no vehicle")));

        assertEquals(List.of(new Entry("legacy", "player_Old", "renamed"),
                new Entry("built", "char_none", "payer is not the constructor"),
                new Entry("row", null, "reconcile: registry row has no vehicle")), quarantine.list());
        assertEquals(1, quarantine.findings().size());

        api.stored("built", "cart", "char_" + java.util.UUID.randomUUID());
        VehicleQuarantine restarted = new VehicleQuarantine(folder);
        restarted.load(List.of());
        assertEquals(List.of(), restarted.list());
        api.stored("built", "cart", "char_none");
        assertEquals(List.of(new Entry("built", "char_none", "payer is not the constructor")), restarted.list());
    }

    @Test
    void theRunningListIsSharedAndAnUnreadableFileFailsTheLoad() throws IOException {
        VehicleQuarantine quarantine = new VehicleQuarantine(folder);
        assertNull(VehicleQuarantine.current());
        VehicleQuarantine.use(quarantine);
        assertSame(quarantine, VehicleQuarantine.current());
        quarantine.load(List.of());
        assertEquals(List.of(), quarantine.list());

        Files.createDirectories(folder.resolve("vehicles"));
        Files.writeString(folder.resolve("vehicles/quarantine.json"), "{\"entries\":null}");
        quarantine.load(List.of());
        assertEquals(List.of(), quarantine.list());
        Files.writeString(folder.resolve("vehicles/quarantine.json"), "{broken");
        assertThrows(IOException.class, () -> new VehicleQuarantine(folder).load(List.of()));
    }
}
