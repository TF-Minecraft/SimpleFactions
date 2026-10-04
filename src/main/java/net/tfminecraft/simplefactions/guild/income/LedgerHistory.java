package net.tfminecraft.simplefactions.guild.income;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.simplefactions.utils.Formatter;

/**
 * Last-day and lifetime totals behind the ledger view icons, keyed by payer name. This is a
 * record of what already moved; settlement never reads it, so it cannot change a bank.
 */
public final class LedgerHistory {

    public enum Source {
        CITIZENS,
        GUILD_TAXES,
        VASSALS,
        TRIBUTES,
        TARIFFS,
        DEPOSITS
    }

    private final Map<Source, Map<String, Double>> lastDay = new EnumMap<>(Source.class);
    private final Map<Source, Map<String, Double>> lifetime = new EnumMap<>(Source.class);
    // Deposits land in the bank straight away, so they are gathered here until the day ends.
    private final Map<String, Double> depositsToday = new HashMap<>();

    public void addDeposit(String name, double amount) {
        if (name == null || name.isBlank() || amount <= 0) return;
        depositsToday.merge(name, amount, Double::sum);
    }

    public Map<String, Double> getDepositsToday() {
        return depositsToday;
    }

    /** Ends the day: what settled becomes the last day and is added to the lifetime totals. */
    public void closeDay(Map<Source, Map<String, Double>> settled) {
        lastDay.clear();
        Map<Source, Map<String, Double>> day = new EnumMap<>(Source.class);
        if (settled != null) day.putAll(settled);
        day.put(Source.DEPOSITS, new HashMap<>(depositsToday));
        depositsToday.clear();

        for (Map.Entry<Source, Map<String, Double>> entry : day.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            Map<String, Double> kept = new HashMap<>();
            for (Map.Entry<String, Double> amount : entry.getValue().entrySet()) {
                String name = amount.getKey();
                Double value = amount.getValue();
                if (name == null || name.isBlank() || value == null || value <= 0) continue;
                kept.merge(name, value, Double::sum);
                lifetime.computeIfAbsent(entry.getKey(), s -> new HashMap<>())
                        .merge(name, value, (a, b) -> Formatter.formatDouble(a + b));
            }
            if (!kept.isEmpty()) lastDay.put(entry.getKey(), kept);
        }
    }

    public Map<String, Double> getLastDay(Source source) {
        return lastDay.getOrDefault(source, Map.of());
    }

    public Map<String, Double> getLifetime(Source source) {
        return lifetime.getOrDefault(source, Map.of());
    }

    public static double total(Map<String, Double> amounts) {
        double total = 0;
        for (double value : amounts.values()) total += value;
        return total;
    }

    public static List<Map.Entry<String, Double>> descending(Map<String, Double> amounts) {
        return amounts.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .toList();
    }

    // --- Save/load ---

    public Map<String, Map<String, Double>> getLastDayCopy() {
        return copyOut(lastDay);
    }

    public Map<String, Map<String, Double>> getLifetimeCopy() {
        return copyOut(lifetime);
    }

    public Map<String, Double> getDepositsTodayCopy() {
        return new HashMap<>(depositsToday);
    }

    public void load(Map<String, Map<String, Double>> lastDayData,
                     Map<String, Map<String, Double>> lifetimeData,
                     Map<String, Double> depositsTodayData) {
        copyIn(lastDayData, lastDay);
        copyIn(lifetimeData, lifetime);
        depositsToday.clear();
        if (depositsTodayData != null) {
            depositsTodayData.forEach((name, value) -> {
                if (value != null) addDeposit(name, value);
            });
        }
    }

    private static Map<String, Map<String, Double>> copyOut(Map<Source, Map<String, Double>> from) {
        Map<String, Map<String, Double>> out = new HashMap<>();
        from.forEach((source, amounts) -> {
            if (!amounts.isEmpty()) out.put(source.name(), new HashMap<>(amounts));
        });
        return out;
    }

    private static void copyIn(Map<String, Map<String, Double>> from, Map<Source, Map<String, Double>> into) {
        into.clear();
        if (from == null) return;
        for (Map.Entry<String, Map<String, Double>> entry : from.entrySet()) {
            Source source;
            try {
                source = Source.valueOf(entry.getKey());
            } catch (IllegalArgumentException | NullPointerException e) {
                continue;
            }
            if (entry.getValue() == null) continue;
            Map<String, Double> amounts = new HashMap<>();
            entry.getValue().forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null && value > 0) amounts.put(name, value);
            });
            if (!amounts.isEmpty()) into.put(source, amounts);
        }
    }
}
