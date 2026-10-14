package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Startup-created held shells wait without consuming captured cleanup or hiding loaded save failures. */
class FactionDeathsHeldCampaignShellTest {
    private static final String ID = "campaign_w1_attacker";

    @AfterEach void reset() {
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        BattleManager.resetForTests();
    }

    private static String band(String id, CharacterRef leader, CharacterRef member) {
        return FactionDeathsHeldWarbandReplayTest.band(leader, member)
                .replace("\"legacy\"", "\"" + id + "\"")
                .replace("\"character-identity-version\":1,", "\"character-identity-version\":1,"
                        + "\"faction\":true,\"campaignSideId\":\"attacker\",\"extension\":{\"kept\":null,\"history\":[1,2]},");
    }

    private static String conflicting(String valid, String id) {
        return valid.replace("\"name\":\"" + id + "\",", "\"name\":\"" + id + "\",\"name\":\"duplicate\",");
    }

    private static void retainedFixture(Path file, String phase, boolean leaderDies) throws IOException {
        String proof = System.getProperty("sf.held-shell.proof");
        if (proof == null) return;
        Path target = Path.of(proof).resolve(leaderDies ? "leader" : "member").resolve(phase);
        Files.createDirectories(target);
        Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualCampaignLoaderShellKeepsCapturedStepPendingUntilRepair(boolean leaderDies) throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity();
                var wars = mockStatic(WarManager.class)) {
            reset();
            identity.bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(mock(BossBar.class));
            Faction attacker = mock(Faction.class), defender = mock(Faction.class);
            when(attacker.getId()).thenReturn("attacker");
            when(defender.getId()).thenReturn("defender");
            when(attacker.getName()).thenReturn("Attacker");
            when(defender.getName()).thenReturn("Defender");
            War war = new War(1, attacker, defender);
            wars.when(() -> WarManager.getById(1)).thenReturn(war);
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            String valid = band(ID, leader, member);
            Path file = files.write("Warbands/warband_" + ID + ".json", valid);
            WarbandManager.addWarband(WarbandCharacters.read(file.toFile()));
            var dead = leaderDies ? leader : member;
            FactionDeathsHeldWarbandReplayTest.kill(identity, dead);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(dead, UUID.randomUUID(), 1000);
            assertEquals(ID, journal.steps.getLast().plan.get("warband").getAsString());
            assertEquals(leaderDies, journal.steps.getLast().plan.get("leader").getAsBoolean());
            if (leaderDies) assertEquals(member.characterId().toString(), journal.steps.getLast().plan.get("successorCharacter").getAsString());
            journal.steps.add(new DeathJournal.Step("after-held", "online", new JsonObject()));
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            try (var disk = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
                disk.when(() -> DurableJson.write(eq(file), any(), any(Gson.class))).thenThrow(new IOException("disk"));
                assertThrows(IOException.class, () -> store.record(journal));
            }
            retainedFixture(file, "before-restart", leaderDies);
            retainedFixture(files.root.resolve("deaths/" + journal.deathId + ".json"), "before-restart", leaderDies);
            Path battleFile = files.write("Battles/battle_w1.json", "{\"id\":\"w1\",\"warId\":1,\"started\":true,"
                    + "\"sides\":[{\"id\":\"attacker\",\"warbandIds\":[\"" + ID + "\"]},{\"id\":\"defender\"}]}");
            files.write("Warbands/warband_" + ID + ".json", conflicting(valid, ID));
            String heldBytes = Files.readString(file);

            // Drive the actual loadAll -> ensureEnrolled -> enrollSide path twice, not a stand-in shell.
            for (int restart = 0; restart < 2; restart++) {
                FactionDeathsHeldWarbandReplayTest.restart(files.root);
                BattlePersistenceService.loadAll();
                assertNotNull(BattleManager.getByString("w1"));
                assertTrue(WarbandCharacters.isHeld(file.toFile()));
                Warband shell = WarbandManager.getByString(ID);
                assertNotNull(shell, "campaign enrolment recreates the same-ID shell");
                assertTrue(shell.isFaction());
                assertTrue(shell.isPendingLeader());
                assertEquals(0, shell.getMemberCount());
                assertTrue(shell.getStoredFields().keySet().isEmpty(), "rejected shell persist cannot establish a snapshot");
                assertSame(shell, BattleManager.getByString("w1").getSideById("attacker").getBands().getFirst());
                var replay = new DeathJournalStore(files.root.resolve("deaths"), new FactionDeaths(files.root)::apply);
                assertDoesNotThrow(replay::replayAll, "an inert held shell must not fail startup reconciliation");
                var pending = replay.find(journal.deathId).orElseThrow();
                assertFalse(pending.complete);
                assertFalse(pending.steps.getFirst().done, "captured warband cleanup remains pending");
                assertTrue(pending.steps.getLast().done, "later independent steps still complete");
                replay.replayAll();
                assertEquals(heldBytes, Files.readString(file));
                assertTrue(shell.getStoredFields().keySet().isEmpty());
                retainedFixture(file, "held-restart-" + restart, leaderDies);
                retainedFixture(battleFile, "held-restart-" + restart, leaderDies);
                retainedFixture(files.root.resolve("deaths/" + journal.deathId + ".json"), "held-restart-" + restart, leaderDies);
            }

            // Stopped repair and a validated real reload retain the original character/successor.
            FactionDeathsHeldWarbandReplayTest.restart(files.root);
            files.write("Warbands/warband_" + ID + ".json", valid);
            identity.heirs.put(member.ownerId(), identity.character(member.ownerId(), "Later heir"));
            BattlePersistenceService.loadAll();
            Warband repaired = WarbandManager.getByString(ID);
            assertNotNull(repaired);
            assertFalse(repaired.getStoredFields().keySet().isEmpty());
            var replay = new DeathJournalStore(files.root.resolve("deaths"), new FactionDeaths(files.root)::apply);
            replay.replayAll();
            assertTrue(replay.find(journal.deathId).orElseThrow().complete);
            Warband reread = WarbandCharacters.read(file.toFile());
            assertFalse(repaired.hasMember(dead.ownerId()));
            assertFalse(reread.hasMember(dead.ownerId()));
            assertEquals(leaderDies ? member.ownerId() : leader.ownerId(), reread.getLeaderId());
            assertEquals(java.util.Optional.of(leaderDies ? member : leader), reread.getMemberCharacter(reread.getLeaderId()));
            assertEquals(repaired.getStoredFields().get("extension"), reread.getStoredFields().get("extension"));
            assertTrue(reread.getStoredFields().getAsJsonObject("extension").get("kept").isJsonNull());
            String after = Files.readString(file);
            replay.replayAll();
            FactionDeathsHeldWarbandReplayTest.restart(files.root);
            BattlePersistenceService.loadAll();
            replay.replayAll();
            assertEquals(after, Files.readString(file), "completed cleanup is idempotent across another real reload");
            retainedFixture(file, "repaired", leaderDies);
            retainedFixture(files.root.resolve("deaths/" + journal.deathId + ".json"), "repaired", leaderDies);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loadedHeldSnapshotStillRefusesDeathReplay(boolean leaderDies) throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            String valid = band(ID, leader, member);
            Path file = files.write("Warbands/warband_" + ID + ".json", valid);
            Warband loaded = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(loaded);
            var dead = leaderDies ? leader : member;
            FactionDeathsHeldWarbandReplayTest.kill(identity, dead);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(dead, UUID.randomUUID(), 1000);
            journal.steps.add(new DeathJournal.Step("after-held", "online", new JsonObject()));
            files.write("Warbands/warband_" + ID + ".json", conflicting(valid, ID));
            String before = Files.readString(file);
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            assertThrows(IOException.class, () -> store.record(journal));
            assertTrue(WarbandCharacters.isHeld(file.toFile()));
            assertFalse(loaded.getStoredFields().keySet().isEmpty());
            assertThrows(IOException.class, store::replayAll, "loaded held bands still propagate a preserving-save failure");
            assertFalse(store.find(journal.deathId).orElseThrow().complete);
            assertFalse(store.find(journal.deathId).orElseThrow().steps.getLast().done);
            assertEquals(before, Files.readString(file));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unheldFreshBandStillPersistsDeathCleanup(boolean leaderDies) throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            Path other = files.write("Warbands/warband_other.json", "{\"id\":\"other\",\"id\":\"conflict\"}");
            assertThrows(IOException.class, () -> WarbandCharacters.read(other.toFile()));
            Warband fresh = Warband.createWithMemberIds("fresh", leader.ownerId(), true);
            fresh.addMember(leader.ownerId(), leader);
            fresh.addMember(member.ownerId(), member);
            WarbandManager.addWarband(fresh);
            assertTrue(fresh.getStoredFields().keySet().isEmpty());
            var dead = leaderDies ? leader : member;
            FactionDeathsHeldWarbandReplayTest.kill(identity, dead);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(dead, UUID.randomUUID(), 1000);
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            store.record(journal);
            assertTrue(store.find(journal.deathId).orElseThrow().complete);
            Path file = files.root.resolve("Warbands/warband_fresh.json");
            Warband reread = WarbandCharacters.read(file.toFile());
            assertFalse(reread.hasMember(dead.ownerId()));
            assertEquals(leaderDies ? member.ownerId() : leader.ownerId(), reread.getLeaderId());
            assertFalse(fresh.getStoredFields().keySet().isEmpty());
            assertTrue(WarbandCharacters.isHeld(other.toFile()), "another file's hold never applies to this fresh band");
        }
    }
}
