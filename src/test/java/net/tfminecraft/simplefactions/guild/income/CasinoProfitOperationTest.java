package net.tfminecraft.simplefactions.guild.income;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.economy.EconomyRuntime;
import net.tfminecraft.simplefactions.economy.ExternalIncome;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CasinoProfitOperationTest {
    @TempDir Path folder;
    @Test void restartRecoversFailedBookkeepingBeforeSettlementAndExactRetryDoesNotRetax() throws Exception {
        try (var fixture = new FactionDomainFixture(); var runtime = mockStatic(EconomyRuntime.class)) {
            when(fixture.ui.plugin.getDataFolder()).thenReturn(folder.toFile());
            runtime.when(EconomyRuntime::ready).thenReturn(true);
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            var ledger = faction.getOrCreateMainGuild().getLedger();
            try (var database = mockConstruction(Database.class, (db, context) -> when(db.saveFactionDurably(faction)).thenReturn(true))) {
                assertTrue(ledger.addCasinoProfitEntry("table:one", 25));
                assertEquals(25, ledger.getCasinoProfit());
                ledger.setCasinoProfit(0);
                assertTrue(ledger.addCasinoProfitEntry("table:one", 25));
                assertEquals(0, ledger.getCasinoProfit());
                assertFalse(ledger.addCasinoProfitEntry("table:one", 26));
            }
            try (var database = mockConstruction(Database.class)) {
                assertFalse(ledger.addCasinoProfitEntry("table:two", 50));
                runtime.verify(() -> EconomyRuntime.failClosed(any()));
            }
            // Reopen the prior persisted guild image; the pending SF journal owns the missing bookkeeping.
            ledger.setCasinoProfit(0);
            ledger.restoreCasinoProfitOperations(Map.of("table:one", 25.0));
            runtime.when(EconomyRuntime::ready).thenReturn(false);
            try (var database = mockConstruction(Database.class, (db, context) -> when(db.saveFactionDurably(faction)).thenReturn(true))) {
                ExternalIncome.recover(folder);
                assertEquals(50, ledger.getCasinoProfit());
                assertTrue(ledger.addCasinoProfitEntry("table:two", 50));
                assertFalse(ledger.addCasinoProfitEntry("table:three", 25));
                ledger.setCasinoProfit(0);
                ExternalIncome.recover(folder);
                assertEquals(0, ledger.getCasinoProfit());
            }
            for (String id : new String[]{null, "", " "}) assertFalse(ledger.addCasinoProfitEntry(id, 2));
            assertFalse(ledger.addCasinoProfitEntry("bad", Double.NaN));
            assertFalse(ledger.addCasinoProfitEntry("bad", 0));
            ledger.restoreCasinoProfitOperations(null);
            assertTrue(ledger.getCasinoProfitOperations().isEmpty());
        }
    }
}
