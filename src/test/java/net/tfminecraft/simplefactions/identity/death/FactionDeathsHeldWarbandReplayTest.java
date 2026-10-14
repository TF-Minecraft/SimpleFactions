package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Held targets retain cleanup for validated restart; missing targets are complete. */
class FactionDeathsHeldWarbandReplayTest {
    @AfterEach void reset() {
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        BattleManager.resetForTests();
    }

    @SuppressWarnings("unchecked")
    static void restart(Path root) throws Exception {
        var field = WarbandCharacters.class.getDeclaredField("HELD");
        field.setAccessible(true);
        ((Set<Path>) field.get(null)).removeIf(path -> path.startsWith(root));
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
    }

    static void kill(SharedIdentity identity, CharacterRef ref) {
        identity.infos.put(ref.characterId(), new net.tfminecraft.tlibs.character.CharacterInfo(ref, "Dead",
                CharacterStatus.DEAD, null, Set.of(), 0, java.time.Instant.EPOCH));
    }

    static String band(CharacterRef leader, CharacterRef member) {
        return "{\"id\":\"legacy\",\"name\":\"legacy\",\"leaderId\":\"" + leader.ownerId() + "\",\"memberIds\":[\""
                + leader.ownerId() + "\",\"" + member.ownerId() + "\"],\"invitedIds\":[],\"character-identity-version\":1,"
                + "\"memberCharacters\":{\"" + leader.ownerId() + "\":{\"ownerId\":\"" + leader.ownerId() + "\",\"characterId\":\""
                + leader.characterId() + "\"},\"" + member.ownerId() + "\":{\"ownerId\":\"" + member.ownerId()
                + "\",\"characterId\":\"" + member.characterId() + "\"}},\"invitedCharacters\":{}}";
    }

    /** A: pending step from a failed death save; at the next start the file is held at read. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void heldStepWaitsForRepairWhileOtherStepsContinue(boolean leaderDies) throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            String valid = band(leader, member);
            Path file = files.write("Warbands/warband_legacy.json", valid);
            WarbandManager.addWarband(WarbandCharacters.read(file.toFile()));
            var dead = leaderDies ? leader : member;
            kill(identity, dead);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(dead, UUID.randomUUID(), 1000);
            assertEquals(leaderDies, journal.steps.getLast().plan.get("leader").getAsBoolean());
            journal.steps.add(new DeathJournal.Step("after-held", "online", new JsonObject()));
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            try (var disk = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
                disk.when(() -> DurableJson.write(eq(file), any(), any(Gson.class))).thenThrow(new IOException("disk"));
                assertThrows(IOException.class, () -> store.record(journal));
            }
            assertFalse(store.find(journal.deathId).orElseThrow().complete);

            // Restart 1: the file is held at read (here: a duplicate key), so the band is not loaded.
            restart(files.root);
            files.write("Warbands/warband_legacy.json", valid.replace("\"name\":\"legacy\",", "\"name\":\"legacy\",\"name\":\"dup\","));
            assertThrows(IOException.class, () -> WarbandCharacters.read(file.toFile()));
            assertTrue(WarbandCharacters.isHeld(file.toFile()));
            var restart1 = new DeathJournalStore(files.root.resolve("deaths"), new FactionDeaths(files.root)::apply);
            restart1.replayAll();
            assertFalse(restart1.find(journal.deathId).orElseThrow().complete, "held target keeps the step pending");
            var pending = restart1.find(journal.deathId).orElseThrow();
            assertFalse(pending.steps.getFirst().done);
            assertTrue(pending.steps.getLast().done, "a held target does not block subsequent steps");
            String heldBytes = Files.readString(file);
            restart1.replayAll();
            assertFalse(restart1.find(journal.deathId).orElseThrow().complete);
            assertEquals(heldBytes, Files.readString(file));

            // Restart 2 after staff repair: the captured cleanup replays once.
            restart(files.root);
            files.write("Warbands/warband_legacy.json", valid);
            Warband repaired = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(repaired);
            var restart2 = new DeathJournalStore(files.root.resolve("deaths"), new FactionDeaths(files.root)::apply);
            restart2.replayAll();
            assertTrue(restart2.find(journal.deathId).orElseThrow().complete);
            assertFalse(repaired.hasMember(dead.ownerId()));
            assertNull(WarbandManager.getByMemberId(dead.ownerId()));
            var reread = WarbandCharacters.read(file.toFile());
            assertFalse(reread.hasMember(dead.ownerId()));
            assertEquals(leaderDies ? member.ownerId() : leader.ownerId(), repaired.getLeaderId());
            assertEquals(repaired.getLeaderId(), reread.getLeaderId());
            String after = Files.readString(file);
            restart2.replayAll();
            assertEquals(after, Files.readString(file));
        }
    }

    /** B: death while the band is held at read: capture cannot see it. */
    @Test void deathDuringReadHoldLeavesDeadLeaderSlotAfterRepair() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            String valid = band(leader, member);
            Path file = files.write("Warbands/warband_legacy.json", valid.replace("\"name\":\"legacy\",", "\"name\":\"legacy\",\"name\":\"dup\","));
            assertThrows(IOException.class, () -> WarbandCharacters.read(file.toFile()));
            kill(identity, leader);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(leader, UUID.randomUUID(), 1000);
            assertFalse(journal.steps.getLast().plan.has("warband"));
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            store.record(journal);
            assertTrue(store.find(journal.deathId).orElseThrow().complete);
            restart(files.root);
            files.write("Warbands/warband_legacy.json", valid);
            Warband repaired = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(repaired);
            store.replayAll();
            assertEquals(leader.ownerId(), repaired.getLeaderId(), "dead leader still recorded after repair");
            assertFalse(repaired.isPendingLeader());
        }
    }

    /** A deleted (not held) band still lets the step complete. */
    @Test void deletedTargetStillCompletes() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            Path file = files.write("Warbands/warband_legacy.json", band(leader, member));
            WarbandManager.addWarband(WarbandCharacters.read(file.toFile()));
            kill(identity, member);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(member, UUID.randomUUID(), 1000);
            restart(files.root);
            Files.delete(file);
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            store.record(journal);
            assertTrue(store.find(journal.deathId).orElseThrow().complete);
        }
    }
}
