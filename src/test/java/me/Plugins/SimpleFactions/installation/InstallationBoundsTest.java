package me.Plugins.SimpleFactions.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InstallationBoundsTest {
    @Test
    void horizontalDistanceBlocks_samePointIsZero() {
        Location location = locationAt(10, 64, 20);
        assertEquals(0.0, InstallationBounds.horizontalDistanceBlocks(10, 20, location));
    }

    @Test
    void horizontalDistanceBlocks_usesXzOnly() {
        Location location = locationAt(3, 100, 4);
        assertEquals(5.0, InstallationBounds.horizontalDistanceBlocks(0, 0, location));
    }

    @Test
    void horizontalDistanceBlocks_nullLocationIsInfinite() {
        assertEquals(Double.POSITIVE_INFINITY, InstallationBounds.horizontalDistanceBlocks(0, 0, null));
    }

    @Test
    void formatDistance_oneDecimalPlace() {
        assertEquals("116.4", InstallationBounds.formatDistance(116.44));
    }

    @Test
    void isWithinRadius_falseWhenLocationNull() {
        Installation installation = new Installation("id", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L);
        assertFalse(InstallationBounds.isWithinRadius(installation, null));
    }

    @Test
    void isCorrectProvince_falseWhenLocationNull() {
        Installation installation = new Installation("id", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L);
        assertFalse(InstallationBounds.isCorrectProvince(installation, null));
    }

    @Test
    void consentProximity_withinLimitAtExactBoundary() {
        Location vehicle = locationAt(0, 64, 0);
        double distance = InstallationBounds.horizontalDistanceBlocks(20, 0, vehicle);
        assertEquals(20.0, distance);
        assertTrue(distance <= 20);
    }

    @Test
    void consentProximity_exceedsLimitJustOutsideBoundary() {
        Location vehicle = locationAt(0, 64, 0);
        double distance = InstallationBounds.horizontalDistanceBlocks(21, 0, vehicle);
        assertEquals(21.0, distance);
        assertTrue(distance > 20);
    }

    private static Location locationAt(int x, int y, int z) {
        World world = Mockito.mock(World.class);
        return new Location(world, x, y, z);
    }
}
