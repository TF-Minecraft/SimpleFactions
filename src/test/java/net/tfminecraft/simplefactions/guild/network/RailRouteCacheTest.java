package net.tfminecraft.simplefactions.guild.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.network.RailRoutes.Route;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;

class RailRouteCacheTest {
    @Test
    void aMeasuredRouteIsKeptUntilForgotten() {
        long[] now = {1_000};
        RailRouteCache cache = new RailRouteCache(() -> now[0]);
        int[] calls = {0};
        Optional<Route> route = Optional.of(new Route(10, List.of(new Point(1, 2, 3), new Point(4, 5, 6))));

        assertEquals(route, cache.route("a", "b", () -> {
            calls[0]++;
            return route;
        }));
        now[0] += 1_000_000;
        assertEquals(route, cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.empty();
        }));
        assertEquals(1, calls[0]);

        cache.forget();
        cache.route("a", "b", () -> {
            calls[0]++;
            return route;
        });
        assertEquals(2, calls[0]);
    }

    @Test
    void theOppositeDirectionReusesTheCachedPointsInReverse() {
        RailRouteCache cache = new RailRouteCache(() -> 0L);
        int[] calls = {0};
        Route measured = new Route(10, List.of(new Point(1, 2, 3), new Point(4, 5, 6)));

        Optional<Route> forward = cache.route("b", "a", () -> {
            calls[0]++;
            return Optional.of(measured);
        });
        Optional<Route> back = cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.empty();
        });

        assertEquals(1, calls[0]);
        assertEquals(List.of(new Point(1, 2, 3), new Point(4, 5, 6)), forward.orElseThrow().points());
        assertEquals(List.of(new Point(4, 5, 6), new Point(1, 2, 3)), back.orElseThrow().points());
        assertEquals(10, back.orElseThrow().length(), 1e-9);
    }

    @Test
    void aMissIsRetriedOnlyAfterThirtySeconds() {
        long[] now = {1_000};
        RailRouteCache cache = new RailRouteCache(() -> now[0]);
        int[] calls = {0};

        assertTrue(cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.empty();
        }).isEmpty());
        assertTrue(cache.route("b", "a", () -> {
            calls[0]++;
            return Optional.empty();
        }).isEmpty());
        now[0] += 29_999;
        cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.empty();
        });
        assertEquals(1, calls[0]);

        now[0] += 1;
        cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.empty();
        });
        assertEquals(2, calls[0]);
    }

    @Test
    void aMissingPairCanBeMeasuredAgainOnceForgotten() {
        RailRouteCache cache = new RailRouteCache(() -> 5_000L);
        int[] calls = {0};

        cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.empty();
        });
        cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.of(new Route(3, List.of()));
        });
        assertEquals(1, calls[0]);

        cache.forget();
        assertTrue(cache.route("a", "b", () -> {
            calls[0]++;
            return Optional.of(new Route(3, List.of()));
        }).isPresent());
        assertEquals(2, calls[0]);
    }
}
