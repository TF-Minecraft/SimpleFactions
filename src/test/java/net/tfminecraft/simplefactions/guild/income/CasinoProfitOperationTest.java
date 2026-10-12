package net.tfminecraft.simplefactions.guild.income;

import java.util.Map;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CasinoProfitOperationTest {
    @Test
    void replayAfterSettlementDoesNotTaxProfitAgainAndSaveFailureRollsBack() {
        Guild guild = mock(Guild.class);
        Faction faction = mock(Faction.class);
        when(guild.getFaction()).thenReturn(faction);
        Ledger ledger = new Ledger(guild);
        try (var database = mockConstruction(Database.class, (db, context) ->
                when(db.saveFactionDurably(faction)).thenReturn(true))) {
            assertTrue(ledger.addCasinoProfitEntry("table:one", 25));
            assertEquals(25, ledger.getCasinoProfit());
            ledger.setCasinoProfit(0);
            Ledger restored = new Ledger(guild);
            restored.restoreCasinoProfitOperations(ledger.getCasinoProfitOperations());
            assertTrue(restored.addCasinoProfitEntry("table:one", 25));
            assertEquals(0, restored.getCasinoProfit());
            assertFalse(restored.addCasinoProfitEntry("table:one", 26));
            assertEquals(1, database.constructed().size());
        }
        try (var database = mockConstruction(Database.class)) {
            assertFalse(ledger.addCasinoProfitEntry("table:two", 50));
            assertEquals(0, ledger.getCasinoProfit());
            assertEquals(Map.of("table:one", 25.0), ledger.getCasinoProfitOperations());
        }
        for (String id : new String[]{null, "", " "}) assertFalse(ledger.addCasinoProfitEntry(id, 2));
        assertFalse(ledger.addCasinoProfitEntry("bad", Double.NaN));
        assertFalse(ledger.addCasinoProfitEntry("bad", 0));
        ledger.restoreCasinoProfitOperations(null);
        assertTrue(ledger.getCasinoProfitOperations().isEmpty());
    }
}
