package me.Plugins.SimpleFactions.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.player.income.PlayerCashflow;
import me.Plugins.SimpleFactions.player.income.PlayerLedger;

class PlayerEconomyManagerTest {
    @Test
    void separatePlayersHaveSeparateLedgers() {
        PlayerEconomyManager manager = new PlayerEconomyManager();
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();

        manager.getLedger(playerOne).add(PlayerCashflow.EARNINGS, 25.0);
        manager.getLedger(playerTwo).add(PlayerCashflow.EARNINGS, 40.0);

        assertEquals(25.0, manager.getLedger(playerOne).getAmount(PlayerCashflow.EARNINGS));
        assertEquals(40.0, manager.getLedger(playerTwo).getAmount(PlayerCashflow.EARNINGS));
    }

    @Test
    void clearAllDailyClearsEveryLedger() {
        PlayerEconomyManager manager = new PlayerEconomyManager();
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();

        manager.getLedger(playerOne).add(PlayerCashflow.EARNINGS, 10.0);
        manager.getLedger(playerTwo).add(PlayerCashflow.CITIZEN_TAX, -5.0);
        manager.clearAllDaily();

        assertEquals(0.0, manager.getLedger(playerOne).getNetDaily());
        assertEquals(0.0, manager.getLedger(playerTwo).getNetDaily());
    }
}
