package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FactionDeathsWarbandPersistenceTest {
    @AfterEach void reset() {
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        BattleManager.resetForTests();
    }

    @Test void migratedBandKeepsRawSlotsSurvivorRolesAndFixedPlanAfterDeathAutosaveAndRestart() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var player = identity.connect("survivor");
            var survivor = identity.character(player.getUniqueId(), "Survivor");
            UUID deadAccount = identity.account("dead");
            var dead = identity.character(deadAccount, "Dead", CharacterStatus.DEAD);
            UUID held = identity.account("held"), invited = identity.account("invited");
            identity.heirs.put(deadAccount, dead);
            JsonObject original = JsonParser.parseString("{\"id\":\"legacy\",\"name\":\"legacy\","
                    + "\"leaderId\":\"" + survivor.ownerId() + "\",\"memberIds\":[\"" + survivor.ownerId()
                    + "\",\"" + deadAccount + "\",\"" + held + "\"],\"invitedIds\":[\"" + invited
                    + "\"],\"locked\":true,\"faction\":false,\"extension\":{\"nested\":[null,{\"keep\":true}]}}")
                    .getAsJsonObject();
            JsonObject members = new JsonObject();
            JsonObject survivorSlot = new Gson().toJsonTree(survivor).getAsJsonObject();
            survivorSlot.add("extension", JsonParser.parseString("{\"nested\":[null,{\"future\":7}]}"));
            members.add(survivor.ownerId().toString(), survivorSlot);
            members.add(held.toString(), JsonNull.INSTANCE);
            UUID orphan = UUID.randomUUID();
            members.add(orphan.toString(), JsonParser.parseString("{\"future\":null}"));
            members.add("metadata", JsonParser.parseString("{\"future\":null}"));
            original.add("memberCharacters", members);
            JsonObject invites = new JsonObject();
            invites.add(invited.toString(), JsonNull.INSTANCE);
            original.add("invitedCharacters", invites);
            Path file = files.write("Warbands/warband_legacy.json", new Gson().newBuilder().serializeNulls().create().toJson(original));
            Warband band = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(band);
            identity.play(player, survivor);
            Battle battle = new Battle("front");
            BattleSide side = mock(BattleSide.class);
            when(side.getBands()).thenReturn(List.of(band));
            battle.getSides().add(side);
            BattleManager.addBattle(battle);
            Path journalFolder = file.getParent().resolve("character-identity-v1/warband_legacy.json");
            String source = Files.readString(journalFolder.resolve("source.json"));
            String plan = Files.readString(journalFolder.resolve("plan.json"));
            var deaths = new FactionDeaths(files.root);
            var death = deaths.capture(dead, UUID.randomUUID(), 1000);
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            store.record(death);
            assertTrue(store.find(death.deathId).orElseThrow().complete);
            JsonObject written = read(file);
            assertTrue(written.has("character-identity-version"), "Death cleanup must retain the migration marker");
            assertEquals(1, written.get("character-identity-version").getAsInt());
            assertEquals(original.get("extension"), written.get("extension"));
            assertEquals(survivorSlot, written.getAsJsonObject("memberCharacters").get(survivor.ownerId().toString()));
            assertEquals(members.get(orphan.toString()), written.getAsJsonObject("memberCharacters").get(orphan.toString()));
            assertEquals(members.get("metadata"), written.getAsJsonObject("memberCharacters").get("metadata"));
            assertEquals(JsonNull.INSTANCE, written.getAsJsonObject("memberCharacters").get(held.toString()));
            assertEquals(invites, written.get("invitedCharacters"));
            assertFalse(written.getAsJsonObject("memberCharacters").has(deadAccount.toString()));
            assertEquals(2, written.getAsJsonArray("memberIds").size());
            assertFalse(band.hasMember(deadAccount));
            BattlePersistenceService.saveAll();
            assertFalse(band.isIdentityHeld());
            assertTrue(band.hasMember(player));
            assertTrue(band.isLeader(player));
            assertEquals(List.of(player), band.getOnlineMembers());
            assertEquals(List.of(player), battle.getPlayers());
            identity.heirs.put(survivor.ownerId(), identity.character(survivor.ownerId(), "Later heir"));
            identity.heirs.put(held, identity.character(held, "Later held heir"));
            WarbandManager.resetForTests();
            Warband restarted = WarbandCharacters.read(file.toFile());
            assertEquals(Optional.of(survivor), restarted.getMemberCharacter(survivor.ownerId()));
            assertTrue(restarted.hasMember(held));
            assertTrue(restarted.getMemberCharacter(held).isEmpty());
            assertFalse(restarted.hasMember(deadAccount));
            assertEquals(source, Files.readString(journalFolder.resolve("source.json")));
            assertEquals(plan, Files.readString(journalFolder.resolve("plan.json")));
            String after = Files.readString(file);
            store.replayAll();
            assertEquals(after, Files.readString(file));
        }
    }

    @Test void failedDeathSaveStaysPendingAndHeldUntilValidatedRestartThenReplaysOnce() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var player = identity.connect("survivor");
            var survivor = identity.character(player.getUniqueId(), "Survivor");
            var dead = identity.character(identity.account("dead"), "Dead", CharacterStatus.DEAD);
            identity.heirs.put(survivor.ownerId(), survivor);
            identity.heirs.put(dead.ownerId(), dead);
            Path file = files.write("Warbands/warband_legacy.json", "{\"id\":\"legacy\",\"leaderId\":\""
                    + dead.ownerId() + "\",\"memberIds\":[\"" + dead.ownerId() + "\",\"" + survivor.ownerId()
                    + "\"],\"invitedIds\":[],\"extension\":{\"kept\":null}}");
            Warband band = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(band);
            identity.play(player, survivor);
            String before = Files.readString(file);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(dead, UUID.randomUUID(), 1000);
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            try (var disk = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
                disk.when(() -> DurableJson.write(eq(file), any(), any(Gson.class)))
                        .thenThrow(new IOException("interrupted warband publication"));
                assertThrows(IOException.class, () -> store.record(journal));
            }
            assertTrue(band.isIdentityHeld());
            assertFalse(band.hasMember(player));
            assertTrue(band.getOnlineMembers().isEmpty());
            assertFalse(store.find(journal.deathId).orElseThrow().complete);
            assertFalse(store.find(journal.deathId).orElseThrow().steps.getLast().done);
            assertThrows(IOException.class, store::replayAll);
            assertEquals(before, Files.readString(file));
            // Startup reload is the production recovery boundary; no in-process hold reset.
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
            Warband restarted = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(restarted);
            var restartedStore = new DeathJournalStore(files.root.resolve("deaths"), new FactionDeaths(files.root)::apply);
            restartedStore.replayAll();
            assertTrue(restartedStore.find(journal.deathId).orElseThrow().complete);
            assertFalse(restarted.hasMember(dead.ownerId()));
            assertEquals(survivor.ownerId(), restarted.getLeaderId());
            assertTrue(restarted.isLeader(player));
            assertEquals(read(file).get("extension"), JsonParser.parseString(before).getAsJsonObject().get("extension"));
            String after = Files.readString(file);
            restartedStore.replayAll();
            assertEquals(after, Files.readString(file));
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test void refusedSecondBandKeepsDeathRoutingWithTheRecordedCharacter() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity();
                var persistence = mockStatic(BattlePersistenceService.class)) {
            reset();
            var player = identity.connect("owner");
            var recorded = identity.character(player.getUniqueId(), "Recorded", CharacterStatus.DEAD);
            var other = identity.character(player.getUniqueId(), "Other");
            var survivor = identity.character(identity.account("survivor"), "Survivor");
            Warband original = Warband.createWithMemberIds("original", player.getUniqueId(), true, survivor.ownerId());
            original.restoreMemberCharacters(java.util.Map.of(player.getUniqueId(), recorded, survivor.ownerId(), survivor));
            WarbandManager.addWarband(original);
            Warband second = Warband.createWithMemberIds("second", UUID.randomUUID(), false);
            WarbandManager.addWarband(second);
            identity.play(player, other);
            assertNotNull(CampaignWarbandSignupService.signupMember(player.getUniqueId(), SharedIdentity.key(other), second, null, player));
            var deaths = new FactionDeaths(files.root);
            DeathJournal otherDeath = deaths.capture(other, UUID.randomUUID(), 1000);
            assertFalse(otherDeath.steps.getLast().plan.has("warband"));
            DeathJournal recordedDeath = deaths.capture(recorded, UUID.randomUUID(), 1000);
            assertEquals("original", recordedDeath.steps.getLast().plan.get("warband").getAsString());
            assertEquals(survivor.ownerId().toString(), recordedDeath.steps.getLast().plan.get("successorOwner").getAsString());
            assertTrue(deaths.apply(recordedDeath, recordedDeath.steps.getLast()));
            assertFalse(original.hasMember(player.getUniqueId()));
            assertEquals(survivor.ownerId(), original.getLeaderId());
            assertFalse(second.hasMember(player.getUniqueId()));
            assertNull(WarbandMembershipService.getInstance().getPendingRejoin(player.getUniqueId()));
            assertFalse(WarbandCharacters.read(files.root.resolve("Warbands/warband_original.json").toFile())
                    .hasMember(player.getUniqueId()));
        }
    }
}
