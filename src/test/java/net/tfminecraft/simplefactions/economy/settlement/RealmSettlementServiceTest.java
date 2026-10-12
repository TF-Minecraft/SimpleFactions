package net.tfminecraft.simplefactions.economy.settlement;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.identity.death.CharacterDeaths;
import net.tfminecraft.simplefactions.identity.death.OwedPayment;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleSettlement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealmSettlementServiceTest {
    @Test void preparedDayRecoversBeforeAnotherDayAndPublishedPlanDoesNotRecalculateRoutes() throws Exception {
        try (var disk = new PersistenceFilesFixture(); var fixture = new FactionDomainFixture();
                var deaths = mockStatic(CharacterDeaths.class); var vehicles = mockStatic(VehicleSettlement.class);
                var playerEconomy = mockStatic(net.tfminecraft.simplefactions.player.PlayerEconomyManager.class)) {
            playerEconomy.when(net.tfminecraft.simplefactions.player.PlayerEconomyManager::get).thenReturn(new net.tfminecraft.simplefactions.player.PlayerEconomyManager());
            when(fixture.ui.plugin.getDataFolder()).thenReturn(disk.root.toFile());
            var faction = fixture.saved("realm", UUID.randomUUID().toString());
            Field offline = FactionCleanup.class.getDeclaredField("offlineDays");
            offline.setAccessible(true);
            Object original = offline.get(null);
            offline.set(null, new HashMap<>());
            try {
                var database = mock(Database.class);
                when(database.saveFactionDurably(any())).thenReturn(true);
                new DailyPreparationJournal(disk.root.resolve("day-preparation"), database).prepare(5, 1000);
                deaths.when(CharacterDeaths::pendingPayments).thenReturn(List.of(new OwedPayment("owed", UUID.randomUUID(), UUID.randomUUID(), 100, OwedPayment.Kind.WAGES)));
                var service = new RealmSettlementService(disk.root, database);
                service.recover();
                assertEquals(6, FactionManager.day);
                assertEquals(0, FactionManager.timer);
                verify(fixture.provinces).recalculate();
                service.settle(5);
                verify(fixture.provinces, times(1)).recalculate();
                service.recover();
                when(fixture.ui.plugin.refreshTrackProvinces()).thenReturn(true);
                service.settle(6);
                verify(fixture.provinces, times(1)).recalculate();
                assertEquals(7, FactionManager.day);
            } finally { offline.set(null, original); }
        }
    }
}
