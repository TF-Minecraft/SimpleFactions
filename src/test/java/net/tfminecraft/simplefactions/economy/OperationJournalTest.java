package net.tfminecraft.simplefactions.economy;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationJournalTest {
    @TempDir Path directory;
    OperationJournal.Intent intent(String id, String account, long cents) {
        JsonObject values = new JsonObject();
        values.addProperty("level", 2);
        return new OperationJournal.Intent(id, "test:operation", List.of(new Leg(Denars.MINT, -cents), new Leg(account, cents)), values);
    }

    @Test void crashAfterPostingRestoresOriginalEffectsAndAcknowledgesOnce() throws Exception {
        try (var fixture = new GuiTestFixture()) {
            String account = Denars.guild(UUID.randomUUID());
            var journal = new OperationJournal(directory);
            var original = intent("buy", account, 20);
            original.changes().addProperty("level", 99);
            assertTrue(journal.resume("absent", value -> fail()).isEmpty());
            assertThrows(IOException.class, () -> journal.execute(original, value -> { throw new IOException("partial save"); }));
            assertEquals(20, Denars.balance(account));
            AtomicInteger raw = new AtomicInteger();
            journal.recoverAppliedEffects(value -> {
                assertEquals(2, value.changes().get("level").getAsInt());
                raw.incrementAndGet();
            });
            assertEquals(1, raw.get());
            assertEquals(Result.CONFLICT, journal.execute(intent("buy", account, 30), value -> fail()));
            var reopened = new OperationJournal(directory);
            assertEquals(Result.APPLIED, reopened.resume("buy", value -> assertEquals(original, value)).orElseThrow());
            assertEquals(Result.APPLIED, reopened.execute(original, value -> fail("already completed")));
            reopened.recover(value -> fail("already completed"));
            reopened.recoverAppliedEffects(value -> fail("completed prepass"));
            assertEquals(20, Denars.balance(account));
        }
    }

    @Test void unavailableConflictsAndRefusedAreNotEffects() throws Exception {
        var journal = new OperationJournal(directory);
        var operation = intent("pending", Denars.guild(UUID.randomUUID()), 1);
        try (var denars = mockStatic(Denars.class)) {
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.UNAVAILABLE);
            assertEquals(Result.UNAVAILABLE, journal.execute(operation, value -> fail()));
            journal.recoverAppliedEffects(value -> fail());
            assertThrows(IOException.class, () -> journal.recover(value -> fail()));
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.CONFLICT);
            assertThrows(IOException.class, () -> journal.recover(value -> fail()));
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.REFUSED);
            journal.recover(value -> fail());
            assertThrows(IOException.class, () -> journal.recover(value -> fail()));
            denars.when(() -> Denars.status("pending")).thenReturn(java.util.Optional.of(Result.REFUSED));
            journal.recover(value -> fail());
        }
        new OperationJournal(directory.resolve("missing")).recover(value -> fail());
        new OperationJournal(directory.resolve("missing")).recoverAppliedEffects(value -> fail());
    }

    @Test void alteredIntentNeverReachesPostingOrRawTopologyRecovery() throws Exception {
        var journal = new OperationJournal(directory);
        var operation = intent("pending", Denars.guild(UUID.randomUUID()), 1);
        assertThrows(IllegalArgumentException.class, () -> new OperationJournal.Intent("", "why", operation.legs(), new JsonObject()));
        try (var denars = mockStatic(Denars.class)) {
            denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenReturn(Result.UNAVAILABLE);
            journal.execute(operation, value -> fail());
            Path path;
            try (var files = Files.list(directory)) { path = files.findFirst().orElseThrow(); }
            var saved = JsonUtil.GSON.fromJson(Files.readString(path), JsonObject.class);
            saved.getAsJsonObject("intent").getAsJsonObject("changes").addProperty("level", 999);
            DurableJson.write(path, saved);
            assertThrows(IOException.class, () -> journal.resume("pending", value -> fail()));
            assertThrows(IOException.class, () -> journal.recoverAppliedEffects(value -> fail()));
            saved.add("intent", null);
            DurableJson.write(path, saved);
            assertThrows(IOException.class, () -> journal.recover(value -> fail()));
        }
    }
}
