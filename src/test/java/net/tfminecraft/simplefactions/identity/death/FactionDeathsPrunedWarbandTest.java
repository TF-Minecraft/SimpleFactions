package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import net.tfminecraft.simplefactions.database.Database;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Only the plugin's own successful prune permits a loaded band to start a new file. */
class FactionDeathsPrunedWarbandTest {
    @AfterEach void reset() {
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        BattleManager.resetForTests();
    }

    private static void kill(SharedIdentity identity, CharacterRef ref) {
        identity.infos.put(ref.characterId(), new net.tfminecraft.tlibs.character.CharacterInfo(ref, "Member",
                CharacterStatus.DEAD, null, java.util.Set.of(), 0, java.time.Instant.EPOCH));
    }

    private static String bandJson(String id, CharacterRef leader, CharacterRef member) {
        return "{\"id\":\"" + id + "\",\"name\":\"Host\",\"leaderId\":\"" + leader.ownerId()
                + "\",\"memberIds\":[\"" + leader.ownerId() + "\",\"" + member.ownerId() + "\"],\"invitedIds\":[],"
                + "\"locked\":false,\"faction\":true,\"campaignSideId\":\"attacker\",\"character-identity-version\":1,"
                + "\"memberCharacters\":{\"" + leader.ownerId() + "\":{\"ownerId\":\"" + leader.ownerId()
                + "\",\"characterId\":\"" + leader.characterId() + "\"},\"" + member.ownerId()
                + "\":{\"ownerId\":\"" + member.ownerId() + "\",\"characterId\":\"" + member.characterId() + "\"}},"
                + "\"invitedCharacters\":{}}";
    }

    /** In-process: the Side Selection GUI's own-side click runs side.removeBand(getByLeader(p)) (BattleManager:771). */
    @Test void sideLeaveThenAutosavePruneThenMemberDeath() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            identity.bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(mock(BossBar.class));
            var leaderPlayer = identity.connect("leader");
            var leader = identity.character(leaderPlayer.getUniqueId(), "Leader");
            UUID memberAccount = identity.account("member");
            var member = identity.character(memberAccount, "Member");
            identity.play(leaderPlayer, leader);
            Path file = files.write("Warbands/warband_campaign_w1_attacker.json", bandJson("campaign_w1_attacker", leader, member));
            Warband band = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(band);
            Battle battle = BattleFactory.createBlank(BattleType.FIELD, "front");
            battle.setLocked(false);
            BattleManager.addBattle(battle);
            battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(band);
            BattlePersistenceService.saveAll();
            assertTrue(Files.exists(file), "referenced band is saved");
            assertTrue(band.isLeader(leaderPlayer));

            battle.getSideById(BattleTemplate.ATTACKER_SIDE).removeBand(WarbandManager.getByLeader(leaderPlayer));
            BattlePersistenceService.saveAll();
            assertFalse(Files.exists(file), "autosave prunes the unreferenced band's file");
            assertSame(band, WarbandManager.getByString("campaign_w1_attacker"), "faction band stays loaded");
            assertSame(band, WarbandManager.getByMemberId(memberAccount), "account still structurally enrolled");
            assertFalse(band.isIdentityHeld());

            kill(identity, member);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(member, UUID.randomUUID(), 1000);
            assertEquals("campaign_w1_attacker", journal.steps.getLast().plan.get("warband").getAsString());
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            assertDoesNotThrow(() -> store.record(journal));
            assertTrue(store.find(journal.deathId).orElseThrow().complete);
            assertFalse(WarbandCharacters.isHeld(file.toFile()));
            assertFalse(band.hasMember(memberAccount));
            assertTrue(band.isLeader(leaderPlayer));
            var rewritten = WarbandCharacters.read(file.toFile());
            assertFalse(rewritten.hasMember(memberAccount));
            assertEquals(java.util.Optional.of(leader), rewritten.getMemberCharacter(leader.ownerId()));
            BattlePersistenceService.saveAll();
            assertFalse(Files.exists(file), "unreferenced band is pruned again, as before");
        }
    }

    /** Startup: a campaign battle file rejected by isCampaignBattleValid leaves its faction band loaded and unreferenced. */
    @Test void startupOrphanThenAutosavePruneThenMemberDeath() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            identity.bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(mock(BossBar.class));
            var leaderPlayer = identity.connect("leader");
            var leader = identity.character(leaderPlayer.getUniqueId(), "Leader");
            UUID memberAccount = identity.account("member");
            var member = identity.character(memberAccount, "Member");
            Path file = files.write("Warbands/warband_campaign_w99_attacker.json", bandJson("campaign_w99_attacker", leader, member));
            Path battleFile = files.write("Battles/battle_w99.json", "{\"id\":\"w99\",\"warId\":99,\"sides\":[{\"id\":\"attacker\",\"warbandIds\":[\"campaign_w99_attacker\"]}]}");
            BattlePersistenceService.loadAll();
            assertFalse(Files.exists(battleFile), "invalid campaign battle file is discarded");
            Warband band = WarbandManager.getByString("campaign_w99_attacker");
            assertNotNull(band, "its faction band is still loaded");
            BattlePersistenceService.saveAll();
            assertFalse(Files.exists(file), "first autosave prunes the orphan's file");
            assertSame(band, WarbandManager.getByString("campaign_w99_attacker"));

            kill(identity, member);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(member, UUID.randomUUID(), 1000);
            assertTrue(journal.steps.getLast().plan.has("warband"));
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            assertDoesNotThrow(() -> store.record(journal));
            assertTrue(store.find(journal.deathId).orElseThrow().complete);
            assertFalse(WarbandCharacters.isHeld(file.toFile()));
            assertFalse(band.hasMember(memberAccount));
            assertFalse(WarbandCharacters.read(file.toFile()).hasMember(memberAccount));
            String cleaned = Files.readString(file);

            // Restart keeps the completed cleanup idempotent.
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
            BattleManager.resetForTests();
            var restarted = new DeathJournalStore(files.root.resolve("deaths"), new FactionDeaths(files.root)::apply);
            restarted.replayAll();
            assertTrue(restarted.find(journal.deathId).orElseThrow().complete);
            assertEquals(cleaned, Files.readString(file));
        }
    }

    /** The missing-source guard still holds a referenced band whose file someone else deleted. */
    @Test void externalDeletionOfReferencedBandStillHolds() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            identity.bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(mock(BossBar.class));
            var leaderPlayer = identity.connect("leader");
            var leader = identity.character(leaderPlayer.getUniqueId(), "Leader");
            UUID memberAccount = identity.account("member");
            var member = identity.character(memberAccount, "Member");
            identity.play(leaderPlayer, leader);
            Path file = files.write("Warbands/warband_campaign_w1_attacker.json", bandJson("campaign_w1_attacker", leader, member));
            Warband band = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(band);
            Battle battle = BattleFactory.createBlank(BattleType.FIELD, "front");
            BattleManager.addBattle(battle);
            battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(band);
            BattlePersistenceService.saveAll();
            Files.delete(file);
            BattlePersistenceService.saveAll();
            assertTrue(band.isIdentityHeld(), "external deletion of a referenced band is still held");
            assertFalse(Files.exists(file));
            kill(identity, member);
            var deaths = new FactionDeaths(files.root);
            var journal = deaths.capture(member, UUID.randomUUID(), 1000);
            var store = new DeathJournalStore(files.root.resolve("deaths"), deaths::apply);
            assertThrows(IOException.class, () -> store.record(journal));
            assertFalse(store.find(journal.deathId).orElseThrow().complete);
        }
    }

    /** A pruned file whose band is referenced again under a case-variant id is not reset. */
    @Test void caseVariantReferencedBandKeepsItsSnapshot() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            identity.bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(mock(BossBar.class));
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            Path file = files.write("Warbands/warband_Band.json", bandJson("Band", leader, member));
            Warband band = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(band);
            Battle battle = BattleFactory.createBlank(BattleType.FIELD, "front");
            BattleManager.addBattle(battle);
            battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(band);
            Path stray = files.write("Warbands/warband_band.json", bandJson("band", leader, member));
            BattlePersistenceService.saveAll();
            assertFalse(Files.exists(stray), "the unreferenced case variant is pruned");
            assertTrue(Files.exists(file));
            assertFalse(band.getStoredFields().keySet().isEmpty(), "only an exact id match is reset");
            Files.delete(file);
            BattlePersistenceService.saveAll();
            assertTrue(band.isIdentityHeld(), "referenced band keeps its missing-source guard");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinarySaveAfterSideLeaveOrStartupOrphanCreatesFreshFile(boolean startup) throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            identity.bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(mock(BossBar.class));
            var leaderPlayer = identity.connect("leader");
            var leader = identity.character(leaderPlayer.getUniqueId(), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            identity.play(leaderPlayer, leader);
            String id = "campaign_w99_attacker";
            String original = bandJson(id, leader, member).replace("\"name\":\"Host\",",
                    "\"name\":\"Host\",\"extension\":{\"unknown\":null},");
            Path file = files.write("Warbands/warband_" + id + ".json", original);
            Warband band;
            if (startup) {
                files.write("Battles/battle_w99.json", "{\"id\":\"w99\",\"warId\":99,\"sides\":[{\"id\":\"attacker\",\"warbandIds\":[\"" + id + "\"]}]}");
                BattlePersistenceService.loadAll();
                band = WarbandManager.getByString(id);
                assertNotNull(band);
            } else {
                band = WarbandCharacters.read(file.toFile());
                WarbandManager.addWarband(band);
                Battle battle = BattleFactory.createBlank(BattleType.FIELD, "front");
                battle.setLocked(false);
                BattleManager.addBattle(battle);
                var side = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
                side.addBand(band);
                BattlePersistenceService.saveAll();
                assertTrue(JsonParser.parseString(Files.readString(file)).getAsJsonObject().has("extension"));
                side.removeBand(WarbandManager.getByLeader(leaderPlayer));
            }
            BattlePersistenceService.saveAll();
            assertFalse(Files.exists(file));
            assertSame(band, WarbandManager.getByMemberId(member.ownerId()));
            new Database().saveWarband(band);
            assertTrue(Files.isRegularFile(file), "ordinary save recreates a file the plugin pruned");
            assertFalse(band.isIdentityHeld());
            assertTrue(band.isLeader(leaderPlayer));
            var fresh = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals(1, fresh.get("character-identity-version").getAsInt());
            assertFalse(fresh.has("extension"), "already pruned unknown data cannot be retained in the fresh file");
            var reread = WarbandCharacters.read(file.toFile());
            assertEquals(java.util.Optional.of(member), reread.getMemberCharacter(member.ownerId()));
            assertTrue(reread.isLeader(leaderPlayer));
        }
    }

    @Test void heldUnreferencedLoadedBandKeepsRawFileAndSnapshot() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            String valid = bandJson("held", leader, member);
            Path file = files.write("Warbands/warband_held.json", valid);
            Warband band = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(band);
            var snapshot = band.getStoredFields().deepCopy();
            String corrupt = valid.replace("\"name\":\"Host\",",
                    "\"name\":\"Host\",\"name\":\"duplicate\",\"unknown\":null,");
            Files.writeString(file, corrupt);
            assertThrows(IOException.class, () -> WarbandCharacters.read(file.toFile()));
            assertTrue(band.isIdentityHeld());
            BattlePersistenceService.saveAll();
            assertEquals(corrupt, Files.readString(file), "held raw bytes are never pruned");
            assertEquals(snapshot, band.getStoredFields(), "held snapshot is never reset");
            assertThrows(IOException.class, () -> WarbandCharacters.save(file.toFile(), band));
            assertEquals(corrupt, Files.readString(file));
        }
    }

    @Test void unsuccessfulPruneDoesNotResetLoadedSnapshot() throws Exception {
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity()) {
            reset();
            var leader = identity.character(identity.account("leader"), "Leader");
            var member = identity.character(identity.account("member"), "Member");
            Path file = files.write("Warbands/warband_retained.json", bandJson("retained", leader, member));
            Warband band = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(band);
            var snapshot = band.getStoredFields().deepCopy();
            var entry = mock(java.io.File.class);
            when(entry.isFile()).thenReturn(true);
            when(entry.getName()).thenReturn("warband_retained.json");
            when(entry.toPath()).thenReturn(file);
            when(entry.delete()).thenReturn(false);
            var prune = BattlePersistenceService.class.getDeclaredMethod("pruneStaleFiles", java.util.Set.class, java.util.Set.class);
            prune.setAccessible(true);
            try (var folders = mockConstruction(java.io.File.class, (folder, context) -> {
                if (context.arguments().getFirst().equals("plugins/SimpleFactions/Warbands")) {
                    when(folder.exists()).thenReturn(true);
                    when(folder.isDirectory()).thenReturn(true);
                    when(folder.listFiles()).thenReturn(new java.io.File[] {entry});
                }
            })) {
                prune.invoke(null, java.util.Set.of(), java.util.Set.of());
            }
            verify(entry).delete();
            assertEquals(snapshot, band.getStoredFields());
            assertTrue(Files.isRegularFile(file));
            assertDoesNotThrow(() -> WarbandCharacters.save(file.toFile(), band));
        }
    }
}
