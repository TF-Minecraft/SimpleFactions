package net.tfminecraft.simplefactions.economy.settlement;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.denareconomy.ledger.Ledger;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementJournalTest {
    @TempDir Path directory;

    @Test
    void replayAfterPostingAndPartialEffectsUsesSavedPlanAndNeverPaysTwice() throws Exception {
        org.slf4j.jul.JDK14LoggerFactory.LOGGER = Logger.getAnonymousLogger();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            Ledger previous = DenarEconomy.getLedger();
            Path db = directory.resolve("ledger.db");
            Ledger ledger = Ledger.open(db.toFile(), Logger.getAnonymousLogger(), account -> false);
            DenarEconomy.setLedger(ledger);
            String guild = Denars.guild(UUID.randomUUID());
            var changes = new JsonObject();
            changes.addProperty("wagesRemaining", 50);
            var plan = new SettlementJournal.Plan(1, List.of(new Leg(guild, -200), new Leg(Denars.MINT, 200)), changes);
            changes.addProperty("wagesRemaining", 999);
            plan.changes().addProperty("wagesRemaining", 998);
            var journal = new SettlementJournal(directory.resolve("settlements"));
            AtomicInteger calculated = new AtomicInteger();
            try {
                assertThrows(IOException.class, () -> journal.settle(1, () -> {
                    calculated.incrementAndGet(); return plan;
                }, recorded -> {
                    DurableJson.write(directory.resolve("wages.json"), recorded.changes());
                    throw new IOException("crash after first guild save");
                }));
                assertEquals(-200, Denars.balance(guild));
                ledger.close();
                ledger = Ledger.open(db.toFile(), Logger.getAnonymousLogger(), account -> false);
                DenarEconomy.setLedger(ledger);
                var recovered = new SettlementJournal(directory.resolve("settlements"));
                AtomicInteger applied = new AtomicInteger();
                recovered.recover(recorded -> {
                    assertEquals(50, recorded.changes().get("wagesRemaining").getAsInt());
                    DurableJson.write(directory.resolve("wages.json"), recorded.changes());
                    applied.incrementAndGet();
                });
                assertEquals(Result.APPLIED, recovered.settle(1, () -> { fail("must not recalculate"); return null; }, recorded -> fail("done")));
                recovered.recover(recorded -> fail("already done"));
                assertEquals(1, calculated.get());
                assertEquals(1, applied.get());
                assertEquals(-200, Denars.balance(guild));
                assertEquals(200, Denars.balance(Denars.MINT));
            } finally {
                ledger.close();
                DenarEconomy.setLedger(previous);
            }
        }
    }

    @Test
    void savedIntentSurvivesUnavailableBeforePostingAndRefusalNeverAppliesEffects() throws Exception {
        var journal = new SettlementJournal(directory);
        var plan = new SettlementJournal.Plan(2, List.of(), new JsonObject());
        assertEquals(List.of(new Leg(Denars.MINT, 0)), plan.legs());
        try (var denars = mockStatic(Denars.class)) {
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.UNAVAILABLE);
            assertEquals(Result.UNAVAILABLE, journal.settle(2, () -> plan, p -> fail()));
            assertTrue(Files.exists(directory.resolve("2.json")));
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.REFUSED);
            assertThrows(IOException.class, () -> journal.recover(p -> fail()));
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.APPLIED);
            assertEquals(Result.APPLIED, journal.settle(2, () -> { fail(); return null; }, p -> {}));
            assertThrows(IOException.class, () -> journal.recover(p -> fail()));
        }
    }

    @Test
    void corruptedPlanOrChangedDayNeverReachesLedger() throws Exception {
        var journal = new SettlementJournal(directory);
        assertThrows(IllegalArgumentException.class, () -> new SettlementJournal.Plan(-1, List.of(), new JsonObject()));
        assertThrows(IOException.class, () -> journal.settle(1, () -> new SettlementJournal.Plan(2, List.of(), new JsonObject()), p -> fail()));
        try (var denars = mockStatic(Denars.class)) {
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.UNAVAILABLE);
            journal.settle(1, () -> new SettlementJournal.Plan(1, List.of(), new JsonObject()), p -> fail());
            Path path = directory.resolve("1.json");
            JsonObject saved = JsonUtil.GSON.fromJson(Files.readString(path), JsonObject.class);
            saved.getAsJsonObject("plan").getAsJsonObject("changes").addProperty("tampered", true);
            DurableJson.write(path, saved);
            assertThrows(IOException.class, () -> journal.recover(p -> fail()));
            saved.add("plan", null);
            DurableJson.write(path, saved);
            assertThrows(IOException.class, () -> journal.recover(p -> fail()));
        }
        new SettlementJournal(directory.resolve("absent")).recover(p -> fail());
    }
}
