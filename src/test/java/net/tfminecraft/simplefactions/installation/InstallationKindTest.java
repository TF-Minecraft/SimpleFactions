package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InstallationKindTest {
    @Test
    void trainStationParsesFromCommandName() {
        assertEquals(InstallationKind.TRAIN_STATION, InstallationKind.fromCommand("train_station"));
    }

    @Test
    void trainStationHasPlayerFacingDisplayName() {
        assertEquals("train station", InstallationKind.TRAIN_STATION.getDisplayName());
    }
}
