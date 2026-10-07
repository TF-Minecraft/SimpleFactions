package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.tfminecraft.vehicleframework.tracks.TrackJunction;
import net.tfminecraft.vehicleframework.tracks.TrackSample;
import net.tfminecraft.vehicleframework.tracks.TrackSegment;
import net.tfminecraft.vehicleframework.tracks.TrackSpline;

class OpenTrackReachTest {
    @Test
    void travelStopsAtTheRange() {
        List<List<TrackReach.Edge>> adjacent = new ArrayList<>();
        for (int index = 0; index < 3; index++) adjacent.add(new ArrayList<>());
        TrackReach.link(adjacent, 0, 1, 1000);
        TrackReach.link(adjacent, 1, 2, 1000);
        double[] distance = TrackReach.travel(adjacent, new boolean[] {true, false, false}, 2500);
        assertEquals(0, distance[0], 1e-9);
        assertEquals(1000, distance[1], 1e-9);
        assertEquals(2000, distance[2], 1e-9);

        double[] cut = TrackReach.travel(adjacent, new boolean[] {true, false, false}, 1500);
        assertEquals(1000, cut[1], 1e-9);
        assertFalse(Double.isFinite(cut[2]));
    }

    @Test
    void reachFollowsJunctionsSkipsBreaksAndSamplesProvincesBetweenPoints() {
        UUID stemId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID branchId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TrackSpline stem = spline(stemId, 0, 100, 200);
        TrackSpline branch = new TrackSpline(branchId, "world", false, List.of(
                new TrackSample(100, 0, 0, 0, 0, 0),
                new TrackSample(100, 0, 100, 0, 0, 100)), null);
        TrackJunction junction = new TrackJunction(
                UUID.randomUUID(), stemId, 100, 1, TrackJunction.Side.LEFT, branchId, true);

        Map<Integer, Double> reached = VehicleFrameworkTracks.reach(
                List.of(stem, branch), List.of(junction),
                0, 0, 10, 2500, (x, z) -> province(x, z), 1);
        // Boarding begins where the intact track enters the station's ten-block radius.
        assertEquals(90, reached.get(2), 1e-6);
        assertEquals(190, reached.get(3), 1e-6);
        assertTrue(reached.get(4) > 100 && reached.get(4) <= 200);
        assertFalse(reached.containsKey(1));

        TrackSpline broken = new TrackSpline(stemId, "world", false, stem.getSamples(),
                List.of(new TrackSegment(1, true, 1)));
        Map<Integer, Double> blocked = VehicleFrameworkTracks.reach(
                List.of(broken), List.of(),
                0, 0, 10, 2500, (x, z) -> province(x, z), 1);
        assertTrue(blocked.get(2) <= 100);
        assertFalse(blocked.containsKey(3));

        TrackSpline coarse = spline(stemId, 0, 80);
        Map<Integer, Double> between = VehicleFrameworkTracks.reach(
                List.of(coarse), List.of(),
                0, 0, 5, 2500, (x, z) -> x < 10 ? 1 : 2, 1);
        assertTrue(between.get(2) < 40);
        assertTrue(between.get(2) > 0);
    }

    @Test
    void rangeCutsTheFarProvince() {
        UUID id = UUID.randomUUID();
        Map<Integer, Double> reached = VehicleFrameworkTracks.reach(
                List.of(spline(id, 0, 100, 200)), List.of(),
                0, 0, 10, 150, (x, z) -> province(x, z), 1);
        assertTrue(reached.containsKey(2));
        assertFalse(reached.containsKey(3));
    }

    private static int province(double x, double z) {
        if (z > 50) return 4;
        return (int) Math.floor(x / 100.0) + 1;
    }

    private static TrackSpline spline(UUID id, double... xs) {
        List<TrackSample> samples = new ArrayList<>();
        for (double x : xs) {
            samples.add(new TrackSample(x, 0, 0, 0, 0, x));
        }
        return new TrackSpline(id, "world", false, samples, null);
    }
}
