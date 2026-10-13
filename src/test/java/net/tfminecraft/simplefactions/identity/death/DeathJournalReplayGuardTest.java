package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.tfminecraft.simplefactions.database.DurableJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** E12: replay acts only on the life its journal belongs to (A3, R11, M1). */
class DeathJournalReplayGuardTest {
    @TempDir Path folder;
    final List<String> applied = new ArrayList<>();
    final AtomicReference<DeathJournalStore.Replay> verdict = new AtomicReference<>(DeathJournalStore.Replay.REPLAY);
    /** Rights in the second realm fail until allowed, as a failed faction save would. */
    boolean secondRealmSaves;

    DeathJournalStore store() {
        return new DeathJournalStore(folder, (journal, step) -> {
            if (step.id.equals("rights:second") && !secondRealmSaves) throw new IOException("save failed");
            applied.add(step.id);
            return true;
        }, journal -> verdict.get());
    }

    /** Rights in two realms, a wage claim against each; only the first realm's cleanup applies at death. */
    DeathJournal journal() {
        DeathJournal journal = new DeathJournal();
        journal.deathId = UUID.randomUUID();
        journal.ownerId = UUID.randomUUID();
        journal.characterId = UUID.randomUUID();
        journal.at = 100;
        journal.lockGuildIncarnation = UUID.randomUUID();
        journal.lockGuildRealm = UUID.randomUUID();
        journal.steps.add(new DeathJournal.Step("rights:first", "rights", new JsonObject()));
        journal.steps.add(new DeathJournal.Step("rights:second", "rights", new JsonObject()));
        journal.steps.add(new DeathJournal.Step("online", "online", new JsonObject()));
        journal.claims.add(new DeathJournal.Claim("rights:first", new OwedPayment("cleaned", UUID.randomUUID(),
                journal.characterId, 1000, OwedPayment.Kind.WAGES)));
        journal.claims.add(new DeathJournal.Claim("rights:second", new OwedPayment("uncleaned", UUID.randomUUID(),
                journal.characterId, 400, OwedPayment.Kind.DIVIDENDS)));
        return journal;
    }

    DeathJournal recordInterrupted(DeathJournalStore store) {
        DeathJournal journal = journal();
        assertThrows(IOException.class, () -> store.record(journal));
        return journal;
    }

    DeathJournal saved(DeathJournal journal) throws IOException {
        return DurableJson.read(folder.resolve(journal.deathId + ".json"), DeathJournal.class);
    }

    @Test void currentDeathReplaysItsRemainingSteps() throws Exception {
        var store = store();
        DeathJournal journal = recordInterrupted(store);
        secondRealmSaves = true;
        store.replayAll();
        assertEquals(List.of("rights:first", "rights:second", "online"), applied);
        DeathJournal after = saved(journal);
        assertTrue(after.complete);
        assertNull(after.closedBy);
        assertEquals(List.of(1000L, 400L), store.pendingPayments().stream().map(OwedPayment::cents).toList());
    }

    @Test void unreadableCharacterIsSkippedForNow() throws Exception {
        var store = store();
        DeathJournal journal = recordInterrupted(store);
        verdict.set(DeathJournalStore.Replay.SKIP);
        secondRealmSaves = true;
        store.replayAll();
        assertEquals(List.of("rights:first"), applied);
        assertFalse(saved(journal).complete);
        verdict.set(DeathJournalStore.Replay.REPLAY);
        store.replayAll();
        assertTrue(saved(journal).complete);
    }

    @Test void revivedCharacterClosesTheJournalWithoutApplyingAndCancelsUncleanedClaims() throws Exception {
        var store = store();
        DeathJournal journal = recordInterrupted(store);
        store.applyPayments("day:1", Map.of("cleaned", 300L));
        verdict.set(DeathJournalStore.Replay.CLOSE_REVIVE);
        secondRealmSaves = true;
        store.replayAll();
        assertEquals(List.of("rights:first"), applied, "the revived character keeps its rights");
        DeathJournal after = saved(journal);
        assertTrue(after.complete);
        assertEquals("revive", after.closedBy);
        assertTrue(after.steps.stream().allMatch(step -> step.done));
        assertEquals(300, after.claims.get(0).paid, "a claim whose cleanup applied stays payable");
        assertEquals(400, after.claims.get(1).paid, "the bucket still pays it directly, so the claim is cancelled");
        assertEquals(List.of(700L), store.pendingPayments().stream().map(OwedPayment::cents).toList());
        // No double payment: the cancelled claim cannot be paid, the remaining one only once.
        assertThrows(IOException.class, () -> store.applyPayments("day:2", Map.of("uncleaned", 1L)));
        store.applyPayments("day:2", Map.of("cleaned", 700L));
        assertThrows(IOException.class, () -> store.applyPayments("day:3", Map.of("cleaned", 1L)));
        assertEquals(List.of(), store.pendingPayments());
        // Closed journals never replay again, whatever the character does next.
        verdict.set(DeathJournalStore.Replay.REPLAY);
        store.replayAll();
        assertEquals(List.of("rights:first"), applied);
    }

    @Test void laterDeathClosesTheEarlierJournalAsRedeath() throws Exception {
        var store = store();
        DeathJournal journal = recordInterrupted(store);
        verdict.set(DeathJournalStore.Replay.CLOSE_REDEATH);
        store.replayAll();
        DeathJournal after = saved(journal);
        assertEquals("redeath", after.closedBy);
        assertEquals(400, after.claims.get(1).paid);
        assertEquals(1000, store.pendingPayments().getFirst().cents());
    }

    @Test void theDeathBeingRecordedIsAppliedWithoutAskingTheGuard() throws Exception {
        verdict.set(DeathJournalStore.Replay.SKIP);
        secondRealmSaves = true;
        var store = store();
        DeathJournal journal = journal();
        store.record(journal);
        assertTrue(saved(journal).complete);
        assertEquals(3, applied.size());
    }

    @Test void olderJarsReadAClosedJournalAsCompleteAndItsCancelledClaimsAsPaid() throws Exception {
        var store = store();
        DeathJournal journal = recordInterrupted(store);
        verdict.set(DeathJournalStore.Replay.CLOSE_REVIVE);
        store.replayAll();
        var json = JsonParser.parseString(Files.readString(folder.resolve(journal.deathId + ".json"))).getAsJsonObject();
        // 4.1.0 reads these fields only: complete stops its replay, paid == cents removes the claim.
        assertTrue(json.get("complete").getAsBoolean());
        json.getAsJsonArray("steps").forEach(step -> assertTrue(step.getAsJsonObject().get("done").getAsBoolean()));
        var cancelled = json.getAsJsonArray("claims").get(1).getAsJsonObject();
        assertEquals(cancelled.getAsJsonObject("payment").get("cents").getAsLong(), cancelled.get("paid").getAsLong());
        // Its Gson ignores the fields it does not know.
        OlderJournal older = DurableJson.read(folder.resolve(journal.deathId + ".json"), OlderJournal.class);
        assertTrue(older.complete);
        assertEquals(journal.deathId, older.deathId);
        assertEquals(journal.lockGuildIncarnation, older.lockGuildIncarnation);
        assertTrue(json.has("lockGuildRealm"));
    }

    /** The 4.1.0 journal shape, without {@code lockGuildRealm} or {@code closedBy}. */
    static final class OlderJournal {
        UUID deathId;
        UUID lockGuildIncarnation;
        boolean complete;
    }

    @Test void journalWrittenByAnOlderJarLoadsAndReplays() throws Exception {
        var store = store();
        DeathJournal journal = recordInterrupted(store);
        Path file = folder.resolve(journal.deathId + ".json");
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertFalse(json.has("closedBy"));
        assertNotNull(json.remove("lockGuildRealm"));
        Files.writeString(file, json.toString());
        secondRealmSaves = true;
        store.replayAll();
        assertTrue(saved(journal).complete);
    }

    @Test void closingMarkersMustBeKnownAndComplete() throws Exception {
        var store = store();
        DeathJournal journal = journal();
        secondRealmSaves = true;
        store.record(journal);
        Path file = folder.resolve(journal.deathId + ".json");
        DeathJournal stored = saved(journal);
        stored.closedBy = "staff";
        DurableJson.write(file, stored);
        assertThrows(IOException.class, store::replayAll);
        stored.closedBy = "revive";
        stored.complete = false;
        DurableJson.write(file, stored);
        assertThrows(IOException.class, store::replayAll);
        stored.complete = true;
        DurableJson.write(file, stored);
        store.replayAll();
        stored.closedBy = "redeath";
        DurableJson.write(file, stored);
        store.replayAll();
    }

    @Test void findReadsOnlyAWrittenJournal() throws Exception {
        var store = store();
        assertTrue(store.find(UUID.randomUUID()).isEmpty());
        DeathJournal journal = recordInterrupted(store);
        assertEquals(journal.characterId, store.find(journal.deathId).orElseThrow().characterId);
    }
}
