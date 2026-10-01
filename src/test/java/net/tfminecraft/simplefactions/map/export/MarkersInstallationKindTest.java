package net.tfminecraft.simplefactions.map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
