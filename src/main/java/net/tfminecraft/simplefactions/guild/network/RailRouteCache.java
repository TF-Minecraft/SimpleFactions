package net.tfminecraft.simplefactions.guild.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.tfminecraft.simplefactions.guild.network.RailRoutes.Route;

/** Hits last until forgotten; misses are tried again after thirty seconds. */
final class RailRouteCache {
    private record Key(String first, String second) { }
    private record Cached(Optional<Route> route, long expiresAt) { }

    private final Map<Key, Cached> routes = new HashMap<>();
    private final LongSupplier clock;

    RailRouteCache(LongSupplier clock) {
        this.clock = clock;
    }

    Optional<Route> route(String from, String to, Supplier<Optional<Route>> source) {
        if (from == null || to == null) return Optional.empty();
        boolean reverse = from.compareTo(to) > 0;
        Key key = reverse ? new Key(to, from) : new Key(from, to);
        long now = clock.getAsLong();
        Cached known = routes.get(key);
        if (known != null && (known.route().isPresent() || known.expiresAt() > now)) {
            return reverse ? known.route().map(Route::reversed) : known.route();
        }
        Optional<Route> measured = source.get();
        routes.put(key, new Cached(reverse ? measured.map(Route::reversed) : measured,
                measured.isPresent() ? Long.MAX_VALUE : now + 30_000L));
        return measured;
    }

    void forget() {
        routes.clear();
    }
}
