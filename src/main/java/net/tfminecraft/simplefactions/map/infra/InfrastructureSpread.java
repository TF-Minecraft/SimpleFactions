package net.tfminecraft.simplefactions.map.infra;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

public final class InfrastructureSpread {
    private InfrastructureSpread() {}

    public record Node(double terrain, boolean land, String realm, Collection<Integer> neighbours) {}
    public record Arrival(double amount, String origin) {}
    private record Step(int province, Arrival arrival) {}

    public static Map<Integer, Arrival> spread(
            Map<Integer, Node> provinces, Map<Integer, Double> sources, double wilderness, double floor) {
        Map<Integer, Arrival> best = new HashMap<>();
        PriorityQueue<Step> pending = new PriorityQueue<>(
                Comparator.comparingDouble((Step step) -> step.arrival().amount()).reversed()
                        .thenComparingInt(Step::province));
        for (Map.Entry<Integer, Double> source : sources.entrySet()) {
            Node province = provinces.get(source.getKey());
            if (province == null || !province.land() || source.getValue() <= 0) continue;
            Arrival arrival = new Arrival(source.getValue(), province.realm());
            best.put(source.getKey(), arrival);
            pending.add(new Step(source.getKey(), arrival));
        }
        while (!pending.isEmpty()) {
            Step step = pending.remove();
            Arrival here = step.arrival();
            if (!here.equals(best.get(step.province())) || here.amount() < floor) continue;
            for (Integer neighbour : provinces.get(step.province()).neighbours()) {
                Node entered = provinces.get(neighbour);
                if (entered == null || !entered.land()) continue;
                if (entered.realm() != null && !Objects.equals(entered.realm(), here.origin())) continue;
                double amount = here.amount() * entered.terrain();
                if (entered.realm() == null) amount *= wilderness;
                Arrival previous = best.get(neighbour);
                if (amount < floor || (previous != null && amount <= previous.amount())) continue;
                Arrival arrival = new Arrival(amount, here.origin());
                best.put(neighbour, arrival);
                pending.add(new Step(neighbour, arrival));
            }
        }
        return best;
    }
}
