package me.Plugins.SimpleFactions.player.income;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PlayerLedger {
    private final Map<PlayerCashflow, Double> dailyAmounts = new EnumMap<>(PlayerCashflow.class);

    public void add(PlayerCashflow cashflow, double amount) {
        if (cashflow == null || amount == 0.0) {
            return;
        }
        dailyAmounts.merge(cashflow, amount, Double::sum);
    }

    public double getAmount(PlayerCashflow cashflow) {
        if (cashflow == null) {
            return 0.0;
        }
        return dailyAmounts.getOrDefault(cashflow, 0.0);
    }

    public double getNetDaily() {
        double net = 0.0;
        for (double amount : dailyAmounts.values()) {
            net += amount;
        }
        return net;
    }

    public void clearDaily() {
        dailyAmounts.clear();
    }

    public List<PlayerCashflow> getIncomeFlows() {
        List<PlayerCashflow> out = new ArrayList<>();
        for (PlayerCashflow cashflow : PlayerCashflow.values()) {
            if (getAmount(cashflow) > 0) {
                out.add(cashflow);
            }
        }
        return out;
    }

    public List<PlayerCashflow> getExpenseFlows() {
        List<PlayerCashflow> out = new ArrayList<>();
        for (PlayerCashflow cashflow : PlayerCashflow.values()) {
            if (getAmount(cashflow) < 0) {
                out.add(cashflow);
            }
        }
        return out;
    }
}
