package me.Plugins.SimpleFactions.mercenary.company;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a company promises its own soldiers. Two kinds, both paid by the host
 * guild: an active wage that is a share of what the slot earns under contract,
 * and a flat peacetime wage paid whether or not there is a contract.
 *
 * <p>Each kind has a company-wide base and an optional per-player override, so a
 * veteran can be kept on better terms than a new recruit without giving everyone
 * the raise.
 */
public class WageSettings {

    private double activePercent;
    private double peacetimePerDay;

    private final Map<String, Double> activeOverrides = new LinkedHashMap<>();
    private final Map<String, Double> peacetimeOverrides = new LinkedHashMap<>();

    /* =====================================================
     * Bases
     * ===================================================== */

    public double getActivePercent() {
        return activePercent;
    }

    public void setActivePercent(double percent) {
        this.activePercent = clampPercent(percent);
    }

    public double getPeacetimePerDay() {
        return peacetimePerDay;
    }

    public void setPeacetimePerDay(double amount) {
        this.peacetimePerDay = Math.max(0, amount);
    }

    /* =====================================================
     * Overrides
     * ===================================================== */

    public void setActiveOverride(String player, Double percent) {
        put(activeOverrides, player, percent == null ? null : clampPercent(percent));
    }

    public void clearActiveOverride(String player) {
        setActiveOverride(player, null);
    }

    public Double getActiveOverride(String player) {
        return get(activeOverrides, player);
    }

    public void setPeacetimeOverride(String player, Double amount) {
        put(peacetimeOverrides, player, amount == null ? null : Math.max(0, amount));
    }

    public void clearPeacetimeOverride(String player) {
        setPeacetimeOverride(player, null);
    }

    public Double getPeacetimeOverride(String player) {
        return get(peacetimeOverrides, player);
    }

    public Map<String, Double> getActiveOverrides() {
        return Collections.unmodifiableMap(activeOverrides);
    }

    public Map<String, Double> getPeacetimeOverrides() {
        return Collections.unmodifiableMap(peacetimeOverrides);
    }

    /* =====================================================
     * Resolution
     * ===================================================== */

    /** The percentage this player earns of what their slot is paid. */
    public double activePercentFor(String player) {
        Double override = get(activeOverrides, player);
        return override == null ? activePercent : override;
    }

    /**
     * The player's cut of one slot price. The lock's worked example: 20% against
     * the config minimums is 2 denars of a 10 denar day and 10 denars of a 50
     * denar battle.
     */
    public double activeShareOf(double slotPrice, String player) {
        if (slotPrice <= 0) return 0;
        return slotPrice * activePercentFor(player) / 100.0;
    }

    /** Flat denars per day, paid with or without a contract. */
    public double peacetimeFor(String player) {
        Double override = get(peacetimeOverrides, player);
        return override == null ? peacetimePerDay : override;
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    private static double clampPercent(double percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static void put(Map<String, Double> map, String player, Double value) {
        if (player == null || player.isBlank()) return;
        if (value == null) {
            map.remove(player.toLowerCase());
            return;
        }
        map.put(player.toLowerCase(), value);
    }

    private static Double get(Map<String, Double> map, String player) {
        if (player == null) return null;
        return map.get(player.toLowerCase());
    }
}
