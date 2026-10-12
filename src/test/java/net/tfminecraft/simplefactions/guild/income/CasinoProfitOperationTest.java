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

    @Test void exactRetryResumesAnUnfinishedOperationButAChangedAmountOrLostBookkeepingFailsClosed() {
        try (var fixture = new FactionDomainFixture(); var runtime = mockStatic(EconomyRuntime.class)) {
            when(fixture.ui.plugin.getDataFolder()).thenReturn(folder.toFile());
            runtime.when(EconomyRuntime::ready).thenReturn(true);
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            var ledger = faction.getOrCreateMainGuild().getLedger();
            try (var database = mockConstruction(Database.class)) {
                assertFalse(ledger.addCasinoProfitEntry("table:retry", 50), "the save fails, leaving the journal unfinished");
            }
            try (var database = mockConstruction(Database.class, (db, context) -> when(db.saveFactionDurably(faction)).thenReturn(true))) {
                assertFalse(ledger.addCasinoProfitEntry("table:retry", 51), "the journalled amount may not change");
                assertEquals(0, ledger.getCasinoProfit());
                assertTrue(ledger.getCasinoProfitOperations().isEmpty());
                assertTrue(ledger.addCasinoProfitEntry("table:retry", 50), "an exact retry resumes the same intent");
                assertEquals(50, ledger.getCasinoProfit());
                assertEquals(50.0, ledger.getCasinoProfitOperations().get("table:retry"));
                ledger.setCasinoProfit(0);
                ledger.restoreCasinoProfitOperations(Map.of());
                assertFalse(ledger.addCasinoProfitEntry("table:retry", 50),
                        "a completed posting whose bookkeeping was lost is incomplete, not a success");
            }
            runtime.verify(() -> EconomyRuntime.failClosed(any()), org.mockito.Mockito.atLeast(2));
        }
    }

    @Test void recoveryRefusesADisappearedGuildAChangedAmountAndMovedIncome() throws Exception {
        try (var fixture = new FactionDomainFixture(); var runtime = mockStatic(EconomyRuntime.class)) {
            runtime.when(EconomyRuntime::ready).thenReturn(true);
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            var ledger = faction.getOrCreateMainGuild().getLedger();

            Path gone = unfinished(fixture, ledger, "gone", "table:gone");
            net.tfminecraft.simplefactions.managers.FactionManager.factions.remove(faction);
            try {
                var error = assertThrows(java.io.IOException.class, () -> ExternalIncome.recover(gone));
                assertEquals("Casino treasury disappeared or was replaced", error.getMessage());
            } finally {
                if (!net.tfminecraft.simplefactions.managers.FactionManager.factions.contains(faction))
                    net.tfminecraft.simplefactions.managers.FactionManager.factions.add(faction);
            }

            Path changed = unfinished(fixture, ledger, "changed", "table:changed");
            ledger.restoreCasinoProfitOperations(Map.of("table:changed", 999.0));
            var amountError = assertThrows(java.io.IOException.class, () -> ExternalIncome.recover(changed));
            assertEquals("Casino operation disagrees with saved amount", amountError.getMessage());
            ledger.restoreCasinoProfitOperations(Map.of());

            Path moved = unfinished(fixture, ledger, "moved", "table:moved");
            ledger.setCasinoProfit(123);
            var movedError = assertThrows(java.io.IOException.class, () -> ExternalIncome.recover(moved));
            assertEquals("Casino income changed before unfinished bookkeeping recovered", movedError.getMessage());
            ledger.setCasinoProfit(0);
        }
    }

    /** Leaves a posted-but-unsaved casino intent in its own data folder, ready for recovery. */
    private Path unfinished(FactionDomainFixture fixture, Ledger ledger, String subfolder, String operation) {
        Path data = folder.resolve(subfolder);
        when(fixture.ui.plugin.getDataFolder()).thenReturn(data.toFile());
        try (var database = mockConstruction(Database.class)) {
            assertFalse(ledger.addCasinoProfitEntry(operation, 50));
        }
        return data;
    }
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
