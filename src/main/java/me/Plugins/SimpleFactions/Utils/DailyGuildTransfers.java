package me.Plugins.SimpleFactions.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import me.Plugins.SimpleFactions.Guild.Guild;

public class DailyGuildTransfers {

    // from -> (to -> amount)
    private final Map<Guild, Map<Guild, Double>> transfers = new HashMap<>();

    // per-guild net change coming from/to "the system" (minting/sinks)
    private final Map<Guild, Double> externalDeltas = new HashMap<>();

    // from guild -> (player -> amount), paid after other settlement is applied
    private final Map<Guild, Map<UUID, Double>> playerPayouts = new HashMap<>();

    // unclamped dividend pool earmarked during populateDailyTransfers
    private final Map<Guild, Double> pendingDividendPools = new HashMap<>();

    public void add(Guild from, Guild to, double amount) {
        if (amount <= 0) return;
        transfers.computeIfAbsent(from, k -> new HashMap<>())
                 .merge(to, amount, Double::sum);
    }

    // Positive = deposit to guild, Negative = withdraw from guild
    public void addExternalDelta(Guild guild, double delta) {
        if (delta == 0) return;
        externalDeltas.merge(guild, delta, Double::sum);
    }

    public void addPlayerPayout(Guild from, UUID to, double amount) {
        if (from == null || to == null || amount <= 0) {
            return;
        }
        playerPayouts.computeIfAbsent(from, k -> new HashMap<>())
                .merge(to, amount, Double::sum);
    }

    public void setPendingDividendPool(Guild guild, double pool) {
        if (guild == null || pool <= 0) {
            return;
        }
        pendingDividendPools.merge(guild, pool, Double::sum);
    }

    public Map<Guild, Map<Guild, Double>> getTransfers() {
        return transfers;
    }

    public Map<Guild, Double> getExternalDeltas() {
        return externalDeltas;
    }

    public Map<Guild, Map<UUID, Double>> getPlayerPayouts() {
        return playerPayouts;
    }

    public Map<Guild, Double> getPendingDividendPools() {
        return pendingDividendPools;
    }

    public void clear() {
        transfers.clear();
        externalDeltas.clear();
        playerPayouts.clear();
        pendingDividendPools.clear();
    }
}


