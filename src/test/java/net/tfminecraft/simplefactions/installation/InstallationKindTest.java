package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.database.InstallationData;

class InstallationKindTest {
    @Test
    void trainStationParsesFromCommandName() {
        assertEquals(InstallationKind.TRAIN_STATION, InstallationKind.fromCommand("train_station"));
    }

    @Test
    void trainStationHasPlayerFacingDisplayName() {
        assertEquals("train station", InstallationKind.TRAIN_STATION.getDisplayName());
    }

    @Test
    void oldSavedInstallationWithoutLevelLoadsAndSavesAsLevelOne() {
        InstallationData data = new InstallationData();
        data.id = "station";
        data.name = "Station";
        data.kind = "train_station";
        data.province = 1;

        Installation installation = new Installation(data);

        assertEquals(1, installation.getLevel());
        assertEquals(1, installation.toData().level);
    }

    @Test
    void savedLevelAboveKindMaximumIsClamped() {
        InstallationData data = new InstallationData();
        data.id = "station";
        data.name = "Station";
        data.kind = "train_station";
        data.province = 1;
        data.level = 8;
        try (MockedStatic<InstallationConfigLoader> config = org.mockito.Mockito.mockStatic(InstallationConfigLoader.class)) {
            config.when(() -> InstallationConfigLoader.getMaximumLevel(InstallationKind.TRAIN_STATION)).thenReturn(3);
            assertEquals(3, new Installation(data).getLevel());
        }
    }
}
