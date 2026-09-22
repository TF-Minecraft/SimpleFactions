package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.ChatColor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;

class LedgerDayCountdownTest {
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    @ParameterizedTest
    @CsvSource({
        "0, 86400, 24h 0m",
        "37800, 48600, 13h 30m",
        "82800, 3600, 1h 0m",
        "82801, 3599, 1h 0m",
        "86340, 60, 0h 1m",
        "86399, 1, 0h 1m",
        "86400, 0, 0h 0m",
        "86401, 0, 0h 0m"
    })
    void countdownUsesPluginTimer(int elapsed, int remaining, String display) {
        int previous = FactionManager.timer;
        try {
            FactionManager.timer = elapsed;
            assertEquals(remaining, FactionManager.getSecondsUntilNewDay());
            String expected = "New day in: " + display;
            assertEquals(expected, ChatColor.stripColor(LedgerDayCountdown.loreLine()));
            assertTrue(new PlayerLedgerCreator().buildLore(new PlayerLedger()).stream()
                    .map(ChatColor::stripColor).anyMatch(expected::equals));
        } finally {
            FactionManager.timer = previous;
        }
    }
}
