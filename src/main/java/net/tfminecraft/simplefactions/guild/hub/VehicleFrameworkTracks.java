package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.guild.network.RailRoutes.Route;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceLookup.Point;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.tracks.TrackJunction;
import net.tfminecraft.vehicleframework.tracks.TrackPose;
import net.tfminecraft.vehicleframework.tracks.TrackRegistry;
import net.tfminecraft.vehicleframework.tracks.TrackSample;
import net.tfminecraft.vehicleframework.tracks.TrackSpline;

/**
 * The only class here that names VehicleFramework types, so it is loaded only once
 * the caller has checked that the plugin is enabled, or through the guarded route method.
 */
public final class VehicleFrameworkTracks {
    private VehicleFrameworkTracks() {
    }

    static OptionalDouble distance(String world, Installation from, Installation to) {
        TrackRegistry tracks = VehicleFramework.getTrackRegistry();
        if (tracks == null || world == null) {
            return OptionalDouble.empty();
        }
        return tracks.shortestRouteLength(
                world,
                from.getCenterX(),
                from.getCenterZ(),
                InstallationConfigLoader.getRadius(from.getKind()),
                to.getCenterX(),
                to.getCenterZ(),
                InstallationConfigLoader.getRadius(to.getKind()));
    }

    /** Empty when VehicleFramework is missing or too old to return route points. */
    public static Optional<Route> route(String world, Installation from, Installation to) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")) return Optional.empty();
            TrackRegistry tracks = VehicleFramework.getTrackRegistry();
            if (tracks == null || world == null) return Optional.empty();
            return tracks.shortestRoute(world,
                    from.getCenterX(), from.getCenterZ(), InstallationConfigLoader.getRadius(from.getKind()),
                    to.getCenterX(), to.getCenterZ(), InstallationConfigLoader.getRadius(to.getKind()), 8)
                    .map(route -> new Route(route.length(), route.points().stream()
                            .map(point -> new Point(point.x(), point.y(), point.z())).toList()));
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    static Set<Integer> sample(String world, ProvinceGrid grid, Map<Integer, Province> provinces) {
        TrackRegistry tracks = VehicleFramework.getTrackRegistry();
        if (tracks == null || world == null) return Set.of();
        return TrackProvinceLookup.collect(grid, tracks.sampleTrack(world, 8).stream()
                .map(point -> new Point(point.x(), point.y(), point.z())).toList(), provinces);
    }

    /** Same spacing the station-to-station route uses when it names provinces. */
    private static final double SAMPLE_SPACING = 8;
    private static final double EPS = 1e-6;

    /**
     * Along-track distance from each finished train station, measured on this thread
     * with the route read. The registry is not a snapshot off the server thread.
     */
    public static List<TradeGraph.OpenTrack> openTracks(
            String world, Iterable<TradeGraph.Node> stations, double radius, double range, TrackReach.At provinceAt) {
        if (world == null || stations == null || provinceAt == null || !(radius >= 0) || !(range > 0)) {
            return List.of();
        }
        TrackRegistry tracks = VehicleFramework.getTrackRegistry();
        if (tracks == null) return List.of();
        List<TrackSpline> splines = tracks.inWorld(world);
        if (splines.isEmpty()) return List.of();
        List<TrackJunction> junctions = new ArrayList<>();
        for (TrackSpline spline : splines) {
            if (spline == null) continue;
            List<TrackJunction> onStem = tracks.junctionsOn(spline.getId());
            if (onStem != null) junctions.addAll(onStem);
        }
        Network network = network(splines, junctions);
        List<TradeGraph.OpenTrack> found = new ArrayList<>();
        for (TradeGraph.Node station : stations) {
            if (station == null || station.kind() != InstallationKind.TRAIN_STATION) continue;
            found.add(new TradeGraph.OpenTrack(
                    station.ownerFactionId(),
                    station.installationId(),
                    cover(network, station.centerX(), station.centerZ(), radius, range, provinceAt, station.provinceId())));
        }
        return found;
    }

    /** Distances for one station. Tests pass splines directly so the registry is not required. */
    static Map<Integer, Double> reach(
            Collection<TrackSpline> splines, Collection<TrackJunction> junctions,
            double x, double z, double radius, double range, TrackReach.At provinceAt, int home) {
        if (splines == null || provinceAt == null) return Map.of();
        return cover(network(splines, junctions == null ? List.of() : junctions),
                x, z, radius, range, provinceAt, home);
    }

    private record SplineEdge(TrackSpline spline, double startS, double endS, double weight, int from, int to) {
    }

    private record Network(List<double[]> xy, List<List<TrackReach.Edge>> adjacent, List<SplineEdge> edges) {
    }

    private static Network network(Collection<TrackSpline> splines, Collection<TrackJunction> junctions) {
        List<double[]> xy = new ArrayList<>();
        List<List<TrackReach.Edge>> adjacent = new ArrayList<>();
        List<SplineEdge> edges = new ArrayList<>();
        Map<UUID, Integer> base = new HashMap<>();
        for (TrackSpline spline : splines) {
            if (spline == null || spline.getId() == null || spline.getSamples().size() < 2) continue;
            int origin = xy.size();
            base.put(spline.getId(), origin);
            for (TrackSample sample : spline.getSamples()) {
                xy.add(new double[] {sample.x, sample.z});
                adjacent.add(new ArrayList<>());
            }
            int count = spline.getSamples().size();
            for (int edge = 0; edge < spline.edgeCount(); edge++) {
                if (spline.segment(edge).broken) continue;
                int from = origin + edge;
                int to = origin + ((edge + 1) % count);
                double start = startS(spline, edge);
                double end = endS(spline, edge);
                double weight = Math.abs(end - start);
                if (weight == 0) {
                    TrackSample left = spline.getSamples().get(edge % count);
                    TrackSample right = spline.getSamples().get((edge + 1) % count);
                    weight = Math.hypot(left.x - right.x, left.z - right.z);
                }
                edges.add(new SplineEdge(spline, start, end, weight, from, to));
            }
        }
        List<SplineEdge> splitEdges = new ArrayList<>();
        for (SplineEdge edge : edges) {
            List<EdgeCut> cuts = new ArrayList<>();
            cuts.add(new EdgeCut(0, edge.from));
            cuts.add(new EdgeCut(edge.weight, edge.to));
            for (TrackJunction junction : junctions) {
                if (junction == null || !edge.spline.getId().equals(junction.stemSplineId)
                        || junction.stemSplineId.equals(junction.branchSplineId)) continue;
                Integer branch = base.get(junction.branchSplineId);
                if (branch == null) continue;
                double at = TrackJunction.wrapS(junction.s, edge.spline.length(), edge.spline.isLoop());
                if (at < edge.startS - EPS || at > edge.endS + EPS) continue;
                double fraction = edge.endS > edge.startS
                        ? (at - edge.startS) / (edge.endS - edge.startS) : 0;
                cuts.add(new EdgeCut(Math.max(0, Math.min(edge.weight, fraction * edge.weight)), branch));
            }
            cuts.sort(java.util.Comparator.comparingDouble(EdgeCut::along));
            for (int index = 1; index < cuts.size(); index++) {
                EdgeCut left = cuts.get(index - 1), right = cuts.get(index);
                double weight = right.along - left.along;
                TrackReach.link(adjacent, left.node, right.node, weight);
                splitEdges.add(new SplineEdge(edge.spline, edgePosition(edge, left.along),
                        edgePosition(edge, right.along), weight, left.node, right.node));
            }
        }
        return new Network(xy, adjacent, splitEdges);
    }

    private record EdgeCut(double along, int node) {}

    private static double edgePosition(SplineEdge edge, double travelled) {
        return edge.weight <= EPS ? edge.startS
                : edge.startS + (travelled / edge.weight) * (edge.endS - edge.startS);
    }

    private static Map<Integer, Double> cover(
            Network network, double x, double z, double radius, double range, TrackReach.At provinceAt, int home) {
        int count = network.xy.size();
        List<List<TrackReach.Edge>> adjacent = new ArrayList<>();
        for (List<TrackReach.Edge> edges : network.adjacent) adjacent.add(new ArrayList<>(edges));
        List<Integer> seeds = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (int index = 0; index < count; index++) {
            double dx = network.xy.get(index)[0] - x;
            double dz = network.xy.get(index)[1] - z;
            if (dx * dx + dz * dz <= radiusSquared) seeds.add(index);
        }
        Map<SplineEdge, double[]> boarding = new HashMap<>();
        for (SplineEdge edge : network.edges) {
            double[] interval = boardingInterval(edge, x, z, radius);
            if (interval == null) continue;
            boarding.put(edge, interval);
            int source = adjacent.size();
            adjacent.add(new ArrayList<>());
            seeds.add(source);
            TrackReach.link(adjacent, source, edge.from, interval[0]);
            TrackReach.link(adjacent, source, edge.to, Math.max(0, edge.weight - interval[1]));
        }
        boolean[] source = new boolean[adjacent.size()];
        for (int seed : seeds) source[seed] = true;
        double[] distance = TrackReach.travel(adjacent, source, range);
        Map<Integer, Double> out = new HashMap<>();
        for (int index = 0; index < count; index++) {
            if (!Double.isFinite(distance[index])) continue;
            record(out, network.xy.get(index)[0], network.xy.get(index)[1], distance[index], range, provinceAt, home);
        }
        for (SplineEdge edge : network.edges) {
            double from = distance[edge.from];
            double to = distance[edge.to];
            double[] interval = boarding.get(edge);
            if (!Double.isFinite(from) && !Double.isFinite(to) && interval == null) continue;
            double weight = edge.weight;
            int steps = weight <= 0 ? 0 : (int) Math.floor(weight / SAMPLE_SPACING);
            for (int step = 0; step <= steps; step++) {
                double travelled = weight <= 0 ? 0 : Math.min(weight, step * SAMPLE_SPACING);
                recordAlong(out, edge, travelled, from, to, interval, range, provinceAt, home);
            }
            recordAlong(out, edge, weight, from, to, interval, range, provinceAt, home);
        }
        return Map.copyOf(out);
    }

    /** The portion of this intact segment inside the station's horizontal boarding circle. */
    private static double[] boardingInterval(SplineEdge edge, double x, double z, double radius) {
        TrackPose left = edge.spline.sampleAt(edge.startS), right = edge.spline.sampleAt(edge.endS);
        double dx = right.x - left.x, dz = right.z - left.z;
        double a = dx * dx + dz * dz;
        double ox = left.x - x, oz = left.z - z;
        double c = ox * ox + oz * oz - radius * radius;
        if (a <= EPS * EPS) return c <= 0 ? new double[] {0, edge.weight} : null;
        double b = 2 * (ox * dx + oz * dz);
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return null;
        double root = Math.sqrt(discriminant);
        double first = Math.max(0, (-b - root) / (2 * a));
        double last = Math.min(1, (-b + root) / (2 * a));
        return first <= last ? new double[] {first * edge.weight, last * edge.weight} : null;
    }

    private static void recordAlong(
            Map<Integer, Double> out, SplineEdge edge, double travelled,
            double fromDistance, double toDistance, double[] boarding,
            double range, TrackReach.At provinceAt, int home) {
        double fromHere = Double.isFinite(fromDistance) ? fromDistance + travelled : Double.POSITIVE_INFINITY;
        double fromThere = Double.isFinite(toDistance)
                ? toDistance + Math.max(0, edge.weight - travelled) : Double.POSITIVE_INFINITY;
        double along = Math.min(fromHere, fromThere);
        if (boarding != null) {
            double direct = Math.max(0, Math.max(boarding[0] - travelled, travelled - boarding[1]));
            along = Math.min(along, direct);
        }
        if (!Double.isFinite(along) || along > range) return;
        double s = edgePosition(edge, travelled);
        TrackPose pose = edge.spline.sampleAt(s);
        record(out, pose.x, pose.z, along, range, provinceAt, home);
    }

    private static void record(
            Map<Integer, Double> out, double x, double z, double distance,
            double range, TrackReach.At provinceAt, int home) {
        if (!Double.isFinite(distance) || distance < 0 || distance > range) return;
        int province = provinceAt.province(x, z);
        if (province == 0 || province == home) return;
        out.merge(province, distance, Math::min);
    }

    private static double startS(TrackSpline spline, int edge) {
        List<TrackSample> samples = spline.getSamples();
        return samples.get(Math.min(edge, samples.size() - 1)).s;
    }

    private static double endS(TrackSpline spline, int edge) {
        List<TrackSample> samples = spline.getSamples();
        if (edge < samples.size() - 1) return samples.get(edge + 1).s;
        return spline.length();
    }
}
