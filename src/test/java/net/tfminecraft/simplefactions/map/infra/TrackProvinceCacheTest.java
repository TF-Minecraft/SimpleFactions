package net.tfminecraft.simplefactions.map.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TrackProvinceCacheTest {
    @Test
    void changedSetRecalculatesOnceAndUnchangedSetDoesNothing() {
        TrackProvinceCache cache = new TrackProvinceCache();
        AtomicInteger recalculations = new AtomicInteger();

        assertTrue(cache.refresh(() -> Set.of(5, 6), recalculations::incrementAndGet, message -> {}));
        assertEquals(Set.of(5, 6), cache.provinces());
        assertFalse(cache.refresh(() -> Set.of(6, 5), recalculations::incrementAndGet, message -> {}));
        assertEquals(1, recalculations.get());
    }

    @Test
    void linkageFailureClearsSetAndWarnsOnlyOnce() {
        TrackProvinceCache cache = new TrackProvinceCache();
        AtomicInteger warnings = new AtomicInteger();
        AtomicInteger recalculations = new AtomicInteger();
        cache.refresh(() -> Set.of(5), recalculations::incrementAndGet, message -> {});

        assertTrue(cache.refresh(() -> { throw new LinkageError("missing sampleTrack"); },
                recalculations::incrementAndGet, message -> warnings.incrementAndGet()));
        assertFalse(cache.refresh(() -> { throw new LinkageError("missing sampleTrack"); },
                recalculations::incrementAndGet, message -> warnings.incrementAndGet()));

        assertEquals(Set.of(), cache.provinces());
        assertEquals(1, warnings.get());
        assertEquals(2, recalculations.get());
    }

    @Test
    void vehicleFrameworkAbsentLogsOnlyOnce() {
        TrackProvinceCache cache = new TrackProvinceCache();
        AtomicInteger infoMessages = new AtomicInteger();
        AtomicInteger recalculations = new AtomicInteger();

        assertFalse(cache.refresh(() -> {
            cache.vehicleFrameworkUnavailable(message -> {
                assertEquals("VehicleFramework is not enabled; railway track is not read.", message);
                infoMessages.incrementAndGet();
            });
            return Set.of();
        }, recalculations::incrementAndGet, message -> {}));
        assertFalse(cache.refresh(() -> {
            cache.vehicleFrameworkUnavailable(message -> infoMessages.incrementAndGet());
            return Set.of();
        }, recalculations::incrementAndGet, message -> {}));

        assertEquals(1, infoMessages.get());
        assertEquals(0, recalculations.get());
    }
}
