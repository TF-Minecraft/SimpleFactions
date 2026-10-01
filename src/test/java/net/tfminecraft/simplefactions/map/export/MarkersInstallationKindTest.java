package net.tfminecraft.simplefactions.map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class MarkersInstallationKindTest {
    @Test
    void trainStationUsesCommandNameAsMapKind() {
        Installation station = new Installation(
                "central_station", "Central Station", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);

        assertEquals("train_station", Markers.installationKind(station));
    }

    @Test
    void installationMapRowIncludesLevel() {
        Installation station = new Installation(
                "central_station", "Central Station", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L, 2);

        var row = Markers.installationRow(station, "faction-a");

        assertTrue(row.get("level").isJsonPrimitive());
        assertEquals(2, row.get("level").getAsInt());
    }
}
