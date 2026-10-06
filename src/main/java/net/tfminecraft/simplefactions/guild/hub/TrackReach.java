package net.tfminecraft.simplefactions.guild.hub;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Shortest distances along an undirected track graph, limited to a range. */
public final class TrackReach {
    public interface At {
        int province(double x, double z);
    }

    record Edge(int to, double weight) {
    }

    private record Step(int node, double distance) {
    }

    private TrackReach() {
    }

    static void link(List<List<Edge>> adjacent, int from, int to, double weight) {
        if (from == to || !Double.isFinite(weight) || weight < 0) {
            return;
        }
        adjacent.get(from).add(new Edge(to, weight));
        adjacent.get(to).add(new Edge(from, weight));
    }

    /** Unreached nodes stay at positive infinity. Nodes past {@code range} are not expanded. */
    static double[] travel(List<List<Edge>> adjacent, boolean[] source, double range) {
        int count = adjacent.size();
        double[] distance = new double[count];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        if (!(range >= 0)) {
            return distance;
        }
        PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingDouble(Step::distance));
        for (int index = 0; index < count && index < source.length; index++) {
            if (!source[index]) {
                continue;
            }
            distance[index] = 0;
            queue.add(new Step(index, 0));
        }
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            if (step.distance != distance[step.node] || step.distance > range) {
                continue;
            }
            for (Edge edge : adjacent.get(step.node)) {
                double next = step.distance + edge.weight;
                if (next <= range && next < distance[edge.to]) {
                    distance[edge.to] = next;
                    queue.add(new Step(edge.to, next));
                }
            }
        }
        return distance;
    }
}
