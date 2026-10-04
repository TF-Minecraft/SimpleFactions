package net.tfminecraft.simplefactions.guild.network;

import java.util.List;
import java.util.Optional;

import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;

/** Routes contain points in order from the first installation to the second. */
@FunctionalInterface
public interface RailRoutes {
    Optional<Route> route(Installation from, Installation to);

    record Route(double length, List<Point> points) {
        public Route {
            points = List.copyOf(points);
        }

        public Route reversed() {
            return new Route(length, points.reversed());
        }
    }
}
