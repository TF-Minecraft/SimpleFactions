package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.database.InstallationData;

class InstallationKindTest {
    @Test
    void loadingBeforeConfigurationKeepsTheSavedPositiveLevel() throws Exception {
        var field = InstallationConfigLoader.class.getDeclaredField("byKind");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var definitions = (java.util.Map<InstallationKind, Object>) field.get(null);
        var previous = new java.util.HashMap<>(definitions);
        definitions.clear();
        try {
            InstallationData data = new InstallationData();
            data.id = "unloaded";
            data.name = "Unloaded Station";
            data.kind = "train_station";
            data.province = 1;
            data.level = 8;
            Installation installation = new Installation(data);
            assertEquals(8, installation.getLevel());
            assertEquals(8, installation.toData().level);
            installation.setLevel(5);
            assertEquals(5, installation.getLevel());
        } finally {
            definitions.clear();
            definitions.putAll(previous);
        }
    }

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
