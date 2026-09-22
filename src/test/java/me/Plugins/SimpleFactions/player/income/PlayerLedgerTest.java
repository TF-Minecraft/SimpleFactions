package me.Plugins.SimpleFactions.player.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerLedgerTest {
    @Test
    void netDailyCombinesEarningsAndTax() {
        PlayerLedger ledger = new PlayerLedger();
        ledger.add(PlayerCashflow.EARNINGS, 100.0);
        ledger.add(PlayerCashflow.CITIZEN_TAX, -10.0);

        assertEquals(100.0, ledger.getAmount(PlayerCashflow.EARNINGS));
        assertEquals(-10.0, ledger.getAmount(PlayerCashflow.CITIZEN_TAX));
        assertEquals(90.0, ledger.getNetDaily());
    }

    @Test
    void clearDailyResetsAmounts() {
        PlayerLedger ledger = new PlayerLedger();
        ledger.add(PlayerCashflow.EARNINGS, 50.0);
        ledger.clearDaily();

        assertEquals(0.0, ledger.getNetDaily());
        assertTrue(ledger.getIncomeFlows().isEmpty());
        assertTrue(ledger.getExpenseFlows().isEmpty());
    }
}
