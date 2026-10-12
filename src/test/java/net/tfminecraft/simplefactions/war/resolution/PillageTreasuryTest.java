package net.tfminecraft.simplefactions.war.resolution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.economy.EconomyRuntime;
import net.tfminecraft.simplefactions.economy.TreasuryActions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.core.War;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PillageTreasuryTest {
    @TempDir Path folder;
    @Test void completedPillageCannotPayOrResetHitsAgainWhenQuoteChanges() throws Exception {
        try (var domain = new FactionDomainFixture(); var saves = mockConstruction(Database.class,
                (database, context) -> when(database.saveFactionDurably(any())).thenReturn(true))) {
            var attacker = domain.saved("attacker", "A");
            var defender = domain.saved("defender", "D");
            defender.addProvince(10);
            assertTrue(defender.getSettlementHandler().found("Victim", 10, 0, 0).isSuccess());
            Guild victim = domain.guild(defender, "victim", "V"); victim.setCapital(10, false);
            when(domain.provinces.getIncome(victim, false)).thenReturn(50d);
            War war = new War(77, attacker, defender); war.setTargetSettlementId("Victim");
            int days = Cache.pillageLootDays; double hit = Cache.pillageTradeHitPercent;
            try {
                Cache.pillageLootDays = 2; Cache.pillageTradeHitPercent = -40;
                TreasuryActions.recover(folder);
                PillageApplyService.apply(war);
                assertEquals(100, attacker.getBank().getWealth());
                assertEquals(-40, PillageTradeHit.percent(victim));
                Cache.pillageLootDays = 4; Cache.pillageTradeHitPercent = -80;
                PillageApplyService.apply(war);
                TreasuryActions.recover(folder);
                assertEquals(100, attacker.getBank().getWealth());
                assertEquals(-40, PillageTradeHit.percent(victim));
                assertEquals(1, victim.getPillageHits().size());
            } finally { Cache.pillageLootDays = days; Cache.pillageTradeHitPercent = hit; }
        }
    }
    @Test void uncertainPillageStopsResolutionAndRecoveringSaveDoesNotMintAgain() throws Exception {
        AtomicBoolean save = new AtomicBoolean(false);
        try (var domain = new FactionDomainFixture(); var saves = mockConstruction(Database.class,
                (database, context) -> when(database.saveFactionDurably(any())).thenAnswer(call -> save.get()))) {
            var attacker = domain.saved("attacker", "A");
            var defender = domain.saved("defender", "D");
            defender.addProvince(10);
            assertTrue(defender.getSettlementHandler().found("Victim", 10, 0, 0).isSuccess());
            Guild victim = domain.guild(defender, "victim", "V"); victim.setCapital(10, false);
            when(domain.provinces.getIncome(victim, false)).thenReturn(50d);
            War war = new War(77, attacker, defender); war.setTargetSettlementId("Victim");
            TreasuryActions.recover(folder);
            assertThrows(IllegalStateException.class, () -> PillageApplyService.apply(war));
            double paid = attacker.getBank().getWealth();
            assertThrows(IllegalStateException.class, () -> PillageApplyService.apply(war));
            save.set(true); TreasuryActions.recover(folder); EconomyRuntime.recovered();
            PillageApplyService.apply(war);
            assertEquals(paid, attacker.getBank().getWealth());
            assertEquals(1, victim.getPillageHits().size());
        }
    }
}
