package net.tfminecraft.simplefactions.economy;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.ledger.Ledger;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuildTreasuryTest {
    @TempDir Path directory;

    @Test
    void importPreservesDebtAndHeldTaxAcrossReopenWithoutMintingAgain() {
        // BungeeCord bundles a JUL binding whose logger is assigned by its own bootstrap.
        org.slf4j.jul.JDK14LoggerFactory.LOGGER = Logger.getAnonymousLogger();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            Ledger previous = DenarEconomy.getLedger();
            var database = directory.resolve("ledger.db").toFile();
            var ledger = Ledger.open(database, Logger.getAnonymousLogger(), account -> false);
            DenarEconomy.setLedger(ledger);
            UUID guild = UUID.randomUUID();
            try {
                GuildTreasury.importLegacy(guild, -25.12, Map.of("quarantine:unknown", 1.25, "member", 2.50));
                assertEquals(-2512, Denars.balance(Denars.guild(guild)));
                assertEquals(375, Denars.balance(Denars.guildTax(guild)));
                assertEquals(2137, Denars.balance(Denars.MINT));
                ledger.close();
                ledger = Ledger.open(database, Logger.getAnonymousLogger(), account -> false);
                DenarEconomy.setLedger(ledger);
                GuildTreasury.importLegacy(guild, 999, Map.of("member", 99.0));
                assertEquals(-2512, Denars.balance(Denars.guild(guild)));
                assertEquals(375, Denars.balance(Denars.guildTax(guild)));
                GuildTreasury.importLegacy(UUID.randomUUID(), 0, null);
                assertEquals(-1, Money.cents(-0.005));
                assertEquals(1, Money.cents(0.005));
                assertEquals(12.34, Money.denars(1234));
                assertThrows(ArithmeticException.class, () -> Money.cents(Double.MAX_VALUE));
            } finally {
                ledger.close();
                DenarEconomy.setLedger(previous);
            }
        }
    }

    @Test
    void refusedOrUnavailableImportPreventsStartupAndNeverRepostsRefusal() {
        try (var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            UUID guild = UUID.randomUUID();
            denars.when(() -> Denars.status("import-guild:" + guild))
                    .thenReturn(java.util.Optional.of(net.tfminecraft.denareconomy.api.Result.REFUSED));
            assertThrows(IllegalStateException.class, () -> GuildTreasury.importLegacy(guild, 100, null));
            denars.when(() -> Denars.status("import-guild:" + guild)).thenReturn(java.util.Optional.empty());
            denars.when(() -> Denars.post(anyString(), anyString(), anyList()))
                    .thenReturn(net.tfminecraft.denareconomy.api.Result.UNAVAILABLE);
            assertThrows(IllegalStateException.class, () -> GuildTreasury.importLegacy(guild, 100, null));
        }
    }
}
