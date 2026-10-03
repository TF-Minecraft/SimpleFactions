package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.simplefactions.Cache;

class ConfigLoaderInfrastructureTest {
    @TempDir
    Path tempDir;
    private final double[] previous = values();

    @AfterEach
    void restore() {
        Cache.infrastructureFull = previous[0];
        Cache.infrastructureTarget = previous[1];
        Cache.infrastructureWildernessSpread = previous[2];
        Cache.infrastructureSpreadFloor = previous[3];
        Cache.infrastructureStation = previous[4];
        Cache.infrastructurePort = previous[5];
        Cache.infrastructureAirport = previous[6];
    }

    @Test
    void missingSectionUsesDefaultsAndResetsPreviousSettings() throws IOException {
        load("""
                infrastructure:
                  full: 30
                  target: 0.80
                  wilderness-spread: 0.10
                  spread-floor: 0.25
                  station: 12
                  port: 8
                  airport: 4
                """);
        load("enable-map: false\n");

        assertValues(20, 0.75, 0.25, 0.5, 10, 10, 5);
    }

    @Test
    void missingKeysUseDefaultsIndependently() throws IOException {
        load("""
                infrastructure:
                  full: 30
                """);

        assertValues(30, 0.75, 0.25, 0.5, 10, 10, 5);
    }

    @Test
    void configuredKeysAreReadIntoCache() throws IOException {
        load("""
                infrastructure:
                  full: 30
                  target: 0.80
                  wilderness-spread: 0.10
                  spread-floor: 0.25
                  station: 12
                  port: 8
                  airport: 4
                """);

        assertValues(30, 0.80, 0.10, 0.25, 12, 8, 4);
    }

    private void load(String yaml) throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, yaml);
        new ConfigLoader().loadConfig(file.toFile());
    }

    private static double[] values() {
        return new double[] {Cache.infrastructureFull, Cache.infrastructureTarget,
                Cache.infrastructureWildernessSpread, Cache.infrastructureSpreadFloor,
                Cache.infrastructureStation, Cache.infrastructurePort, Cache.infrastructureAirport};
    }

    private static void assertValues(double... expected) {
        double[] actual = values();
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i], 1e-9);
    }
}
