package net.tfminecraft.simplefactions.objects.handler;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * A faction's vehicle tax and fee rates. Each kind has a general rate and optional
 * per-vehicle-type rates, all held inside the bracket of the current vehicle tax law.
 */
public class VehicleFeeHandler {
    private final Faction f;
    private final Map<FeeKind, Double> rates = new EnumMap<>(FeeKind.class);
    private final Map<FeeKind, Map<String, Double>> typeRates = new EnumMap<>(FeeKind.class);
    private final Map<FeeKind, Bracket> brackets = new EnumMap<>(FeeKind.class);

    public VehicleFeeHandler(Faction f) {
        this.f = f;
    }

    public double getRate(FeeKind kind) {
        return rates.getOrDefault(kind, 0.0);
    }

    /** The type's own rate if it has one, otherwise the general rate. */
    public double getRate(FeeKind kind, String vehicleTypeId) {
        Double specific = getTypeRate(kind, vehicleTypeId);
        return specific != null ? specific : getRate(kind);
    }

    public boolean hasTypeRate(FeeKind kind, String vehicleTypeId) {
        return getTypeRate(kind, vehicleTypeId) != null;
    }

    private Double getTypeRate(FeeKind kind, String vehicleTypeId) {
        if (vehicleTypeId == null) {
            return null;
        }
        Map<String, Double> map = typeRates.get(kind);
        return map == null ? null : map.get(key(vehicleTypeId));
    }

    public Map<String, Double> getTypeRates(FeeKind kind) {
        Map<String, Double> map = typeRates.get(kind);
        return map == null ? new HashMap<>() : new HashMap<>(map);
    }

    /** A null vehicle type sets the general rate. The rate is clamped into the bracket. */
    public void setRate(FeeKind kind, String vehicleTypeId, double rate) {
        double clamped = clamp(kind, rate);
        if (vehicleTypeId == null) {
            rates.put(kind, clamped);
            dropTypeRatesEqualToGeneral(kind);
            return;
        }
        if (Double.compare(clamped, getRate(kind)) == 0) {
            removeTypeRate(kind, vehicleTypeId);
            return;
        }
        typeRates.computeIfAbsent(kind, k -> new HashMap<>()).put(key(vehicleTypeId), clamped);
    }

    private void removeTypeRate(FeeKind kind, String vehicleTypeId) {
        Map<String, Double> map = typeRates.get(kind);
        if (map == null) {
            return;
        }
        map.remove(key(vehicleTypeId));
        if (map.isEmpty()) {
            typeRates.remove(kind);
        }
    }

    public void applyBracket(FeeKind kind, Bracket bracket) {
        if (bracket == null) {
            return;
        }
        brackets.put(kind, bracket);
        rates.put(kind, clampTo(getRate(kind), bracket));
        Map<String, Double> map = typeRates.get(kind);
        if (map != null) {
            map.replaceAll((type, rate) -> clampTo(rate, bracket));
        }
        dropTypeRatesEqualToGeneral(kind);
    }

    private void dropTypeRatesEqualToGeneral(FeeKind kind) {
        Map<String, Double> map = typeRates.get(kind);
        if (map == null) {
            return;
        }
        double general = getRate(kind);
        map.values().removeIf(rate -> Double.compare(rate, general) == 0);
        if (map.isEmpty()) {
            typeRates.remove(kind);
        }
    }

    public Bracket getBracket(FeeKind kind) {
        return brackets.get(kind);
    }

    /** Whether the current law lets the faction charge this at all. */
    public boolean canCharge(FeeKind kind) {
        Bracket bracket = brackets.get(kind);
        return bracket != null && bracket.getMax() > 0.0 && f.hasFactionRule(kind.getRule());
    }

    public double getMin(FeeKind kind) {
        Bracket bracket = brackets.get(kind);
        return canCharge(kind) ? bracket.getMin() : 0.0;
    }

    public double getMax(FeeKind kind) {
        Bracket bracket = brackets.get(kind);
        return canCharge(kind) ? bracket.getMax() : 0.0;
    }

    /** The rate actually charged: 0 unless the law allows the charge. */
    public double getChargedRate(FeeKind kind, String vehicleTypeId) {
        if (!canCharge(kind)) {
            return 0.0;
        }
        return clamp(kind, getRate(kind, vehicleTypeId));
    }

    private double clamp(FeeKind kind, double rate) {
        Bracket bracket = brackets.get(kind);
        if (bracket == null) {
            return Math.max(0.0, rate);
        }
        return clampTo(rate, bracket);
    }

    private static double clampTo(double rate, Bracket bracket) {
        return Math.max(bracket.getMin(), Math.min(bracket.getMax(), rate));
    }

    private static String key(String vehicleTypeId) {
        return vehicleTypeId.toLowerCase(Locale.ROOT);
    }

    // Persistence

    public Map<String, Double> serializeRates() {
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<FeeKind, Double> entry : rates.entrySet()) {
            out.put(entry.getKey().name(), entry.getValue());
        }
        return out;
    }

    public Map<String, Map<String, Double>> serializeTypeRates() {
        Map<String, Map<String, Double>> out = new HashMap<>();
        for (Map.Entry<FeeKind, Map<String, Double>> entry : typeRates.entrySet()) {
            out.put(entry.getKey().name(), new HashMap<>(entry.getValue()));
        }
        return out;
    }

    /** Loads saved rates and re-clamps them, since the law's bracket may have changed in config. */
    public void load(Map<String, Double> savedRates, Map<String, ? extends Map<String, Double>> savedTypeRates) {
        if (savedRates != null) {
            for (Map.Entry<String, Double> entry : savedRates.entrySet()) {
                FeeKind kind = parse(entry.getKey());
                if (kind != null && entry.getValue() != null) {
                    rates.put(kind, entry.getValue());
                }
            }
        }
        if (savedTypeRates != null) {
            for (Map.Entry<String, ? extends Map<String, Double>> entry : savedTypeRates.entrySet()) {
                FeeKind kind = parse(entry.getKey());
                if (kind == null || entry.getValue() == null) {
                    continue;
                }
                for (Map.Entry<String, Double> typeEntry : entry.getValue().entrySet()) {
                    if (typeEntry.getKey() != null && typeEntry.getValue() != null) {
                        typeRates.computeIfAbsent(kind, k -> new HashMap<>())
                                .put(key(typeEntry.getKey()), typeEntry.getValue());
                    }
                }
            }
        }
        for (Map.Entry<FeeKind, Bracket> entry : new EnumMap<>(brackets).entrySet()) {
            applyBracket(entry.getKey(), entry.getValue());
        }
    }

    private static FeeKind parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return FeeKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
