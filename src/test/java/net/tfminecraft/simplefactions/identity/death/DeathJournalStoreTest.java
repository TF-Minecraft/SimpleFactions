package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.tfminecraft.simplefactions.database.DurableJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeathJournalStoreTest {
    @TempDir Path folder;

    DeathJournal journal(long id) {
        DeathJournal journal = new DeathJournal();
        journal.deathId = new UUID(0, id);
        journal.ownerId = UUID.randomUUID();
        journal.characterId = UUID.randomUUID();
        journal.at = 100;
        JsonObject rights = new JsonObject();
        rights.addProperty("originalMember", journal.characterId.toString());
        rights.addProperty("fixedSuccessor", "chosen-before-effects");
        journal.steps.add(new DeathJournal.Step("rights", "rights", rights));
        journal.steps.add(new DeathJournal.Step("succession", "succession", new JsonObject()));
        journal.claims.add(new DeathJournal.Claim("rights", new OwedPayment("wage-" + id,
                UUID.randomUUID(), journal.characterId, 1000, OwedPayment.Kind.WAGES)));
        return journal;
    }

    @Test
    void settlementReceiptRejectsAddingPreviouslyUntouchedClaimsOnRetry() throws Exception {
        var store = new DeathJournalStore(folder, (journal, step) -> true);
        store.record(journal(40));
        store.record(journal(41));
        store.applyPayments("day", Map.of("wage-40", 100L));
        assertThrows(IOException.class, () -> store.applyPayments("day", Map.of("wage-40", 100L, "wage-41", 100L)));
        assertEquals(1900, store.pendingPayments().stream().mapToLong(OwedPayment::cents).sum());
        store.applyPayments("day", Map.of("wage-40", 100L));
        assertEquals(1900, store.pendingPayments().stream().mapToLong(OwedPayment::cents).sum());
    }

    @Test
    void recordsOriginalRightsBeforeEffectsAndReplaysOnlyIncompleteStepsAfterReopen() throws Exception {
        DeathJournal original = journal(1);
        List<String> applied = new ArrayList<>();
        var first = new DeathJournalStore(folder, (journal, step) -> {
            assertTrue(Files.readString(folder.resolve(original.deathId + ".json")).contains("chosen-before-effects"));
            if (step.kind.equals("succession")) throw new IOException("save failed after membership was removed");
            applied.add(step.id);
            return true;
        });
        assertThrows(IOException.class, () -> first.record(original));
        assertEquals(List.of("rights"), applied);
        assertEquals(1000, first.pendingPayments().getFirst().cents());
        var reopened = new DeathJournalStore(folder, (journal, step) -> {
            applied.add(step.id);
            return true;
        });
        reopened.replayAll();
        reopened.replayAll();
        reopened.record(original);
        assertEquals(List.of("rights", "succession"), applied);
        assertTrue(DurableJson.read(folder.resolve(original.deathId + ".json"), DeathJournal.class).complete);
        assertEquals(1000, reopened.pendingPayments().getFirst().cents());
    }

    @Test
    void interruptedAcknowledgmentRepeatsAbsoluteEffectsWithoutRecomputingPlan() throws Exception {
        var original = journal(2);
        AtomicBoolean memberPresent = new AtomicBoolean(true);
        AtomicInteger removals = new AtomicInteger();
        var store = new DeathJournalStore(folder, (journal, step) -> {
            if (step.id.equals("rights") && memberPresent.getAndSet(false)) removals.incrementAndGet();
            return true;
        });
        try (var files = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
            files.when(() -> DurableJson.write(any(Path.class), any())).thenAnswer(call -> {
                DeathJournal writing = call.getArgument(1);
                if (writing.steps.getFirst().done) throw new IOException("ack write failed");
                return call.callRealMethod();
            });
            assertThrows(IOException.class, () -> store.record(original));
        }
        assertFalse(memberPresent.get());
        assertEquals(List.of(), store.pendingPayments());
        store.replayAll();
        assertEquals(1, removals.get());
        assertEquals(1000, store.pendingPayments().getFirst().cents());
    }

    @Test
    void blockedSuccessionDoesNotHideTransferredDebtOrPreventLaterResolution() throws Exception {
        var original = journal(3);
        AtomicBoolean resolved = new AtomicBoolean();
        var store = new DeathJournalStore(folder, (journal, step) -> !step.kind.equals("succession") || resolved.get());
        store.record(original);
        assertFalse(DurableJson.read(folder.resolve(original.deathId + ".json"), DeathJournal.class).complete);
        assertEquals(1, store.pendingPayments().size());
        resolved.set(true);
        store.replayAll();
        assertTrue(DurableJson.read(folder.resolve(original.deathId + ".json"), DeathJournal.class).complete);
    }

    @Test
    void partialSettlementsSurviveReopenAndReplayWithoutLosingTheRemainder() throws Exception {
        var store = new DeathJournalStore(folder, (journal, step) -> true);
        store.record(journal(4));
        store.applyPayments("day:1", Map.of("wage-4", 400L));
        var reopened = new DeathJournalStore(folder, (journal, step) -> true);
        reopened.applyPayments("day:1", Map.of("wage-4", 400L));
        assertEquals(600, reopened.pendingPayments().getFirst().cents());
        assertThrows(IOException.class, () -> reopened.applyPayments("day:1", Map.of("wage-4", 500L)));
        assertThrows(IOException.class, () -> reopened.applyPayments("day:2", Map.of("wage-4", 601L)));
        reopened.applyPayments("day:2", Map.of("wage-4", 600L));
        assertEquals(List.of(), reopened.pendingPayments());
        reopened.applyPayments("day:2", Map.of("wage-4", 600L));
    }

    @Test
    void multiDeathAcknowledgmentRecoversAfterOnlyTheFirstFileSaved() throws Exception {
        var store = new DeathJournalStore(folder, (journal, step) -> true);
        store.record(journal(5));
        store.record(journal(6));
        Map<String, Long> amounts = Map.of("wage-5", 300L, "wage-6", 200L);
        try (var files = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
            files.when(() -> DurableJson.write(eq(folder.resolve(new UUID(0, 6) + ".json")), any()))
                    .thenThrow(new IOException("second disk write failed"));
            assertThrows(IOException.class, () -> store.applyPayments("day:1", amounts));
        }
        store.applyPayments("day:1", amounts);
        assertEquals(List.of(700L, 800L), store.pendingPayments().stream().map(OwedPayment::cents).toList());
    }

    @Test
    void invalidPaymentsAndUntransferredObligationsCannotBeAcknowledged() throws Exception {
        var store = new DeathJournalStore(folder, (journal, step) -> false);
        store.record(journal(7));
        assertThrows(IOException.class, () -> store.applyPayments("day", Map.of("wage-7", 1L)));
        assertThrows(IOException.class, () -> store.applyPayments("day", Map.of("unknown", 1L)));
        assertThrows(IOException.class, () -> store.applyPayments("day", Map.of("wage-7", -1L)));
        assertThrows(IOException.class, () -> store.applyPayments("", Map.of()));
        store.applyPayments("empty", Map.of());
        assertEquals(List.of(), store.pendingPayments());
    }

    @Test
    void conflictingPersistedLocalReceiptFailsWithoutReducingDebt() throws Exception {
        var store = new DeathJournalStore(folder, (journal, step) -> true);
        var record = journal(42);
        store.record(record);
        store.applyPayments("day", Map.of("wage-42", 100L));
        Path file = folder.resolve(record.deathId + ".json");
        record = DurableJson.read(file, DeathJournal.class);
        record.payments.put("day", Map.of("wage-42", 101L));
        DurableJson.write(file, record);
        assertThrows(IOException.class, () -> store.applyPayments("day", Map.of("wage-42", 100L)));
        assertEquals(900, store.pendingPayments().getFirst().cents());
    }

    @Test
    void corruptFilesAndReusedDeathIdsFailWithoutApplyingEffects() throws Exception {
        AtomicInteger effects = new AtomicInteger();
        var store = new DeathJournalStore(folder, (journal, step) -> { effects.incrementAndGet(); return true; });
        assertEquals(List.of(), store.pendingPayments());
        Files.writeString(folder.resolve("ignored.tmp"), "not a journal");
        var original = journal(8);
        store.record(original);
        assertThrows(IOException.class, () -> store.record(journal(8)));
        var file = folder.resolve(original.deathId + ".json");
        Files.writeString(file, "{}");
        assertThrows(IOException.class, store::replayAll);
        original.steps.getFirst().id = null;
        DurableJson.write(file, original);
        assertThrows(IOException.class, store::replayAll);
        original.steps.getFirst().id = "rights";
        original.claims.getFirst().paid = 1001;
        DurableJson.write(file, original);
        assertThrows(IOException.class, store::replayAll);
        assertEquals(2, effects.get());
    }

    @Test
    void failedIntentWriteCannotRemoveAnyRights() throws Exception {
        Path blocked = folder.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        AtomicInteger effects = new AtomicInteger();
        var store = new DeathJournalStore(blocked, (journal, step) -> { effects.incrementAndGet(); return true; });
        assertThrows(IOException.class, () -> store.record(journal(9)));
        assertEquals(0, effects.get());
    }
}
