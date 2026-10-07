package net.tfminecraft.simplefactions.guild.income;

import java.util.Map;

/**
 * One guild's daily trade lines before settlement: gross trade, trade upkeep, and the tariffs
 * it pays, with those tariffs split by the faction that collects them (keyed by faction id).
 */
public record TradeIncome(double gross, double upkeep, double tariffs, Map<String, Double> tariffsByOwner) {
    public static final TradeIncome NONE = new TradeIncome(0, 0, 0, Map.of());

    public TradeIncome {
        tariffsByOwner = Map.copyOf(tariffsByOwner);
    }

    public double tariffsPaidTo(String factionId) {
        return factionId == null ? 0 : tariffsByOwner.getOrDefault(factionId, 0.0);
    }
}
