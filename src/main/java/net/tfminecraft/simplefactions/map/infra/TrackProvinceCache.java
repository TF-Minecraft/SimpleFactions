package net.tfminecraft.simplefactions.map.infra;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Immutable track source snapshot shared by live and preview recalculations. */
public final class TrackProvinceCache {
    private static final TrackProvinceCache LIVE = new TrackProvinceCache();

    private volatile Set<Integer> provinces = Set.of();
    private boolean warned;

    public static TrackProvinceCache live() {
        return LIVE;
    }

    public Set<Integer> provinces() {
        return provinces;
    }

    public boolean refresh(Supplier<Set<Integer>> sampler, Runnable recalculate, Consumer<String> warning) {
        Set<Integer> sampled;
        try {
            sampled = Set.copyOf(sampler.get());
        } catch (RuntimeException | LinkageError e) {
            sampled = Set.of();
            if (!warned) {
                warned = true;
                warning.accept("Could not read VehicleFramework tracks; track infrastructure is disabled.");
            }
        }
        if (provinces.equals(sampled)) return false;
        provinces = sampled;
        recalculate.run();
        return true;
    }
}
