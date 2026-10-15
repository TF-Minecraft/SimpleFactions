package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.tfminecraft.simplefactions.database.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.DividendBreakdown;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters;
import net.tfminecraft.simplefactions.war.battle.warband.*;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

class FactionDeathsTest {
    @TempDir Path folder;
    FactionDomainFixture fixture;
    MockedConstruction<Database> databases;
    MockedConstruction<Ledger> ledgers;
    final Map<String, CharacterInfo> characters = new HashMap<>();
    final AtomicBoolean saveWorks = new AtomicBoolean(true);
    final CharacterRef dead = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final String key = dead.characterId().toString();
    FactionDeaths effects;
    DeathJournalStore store;

    @BeforeEach void setup() throws Exception {
        fixture = new FactionDomainFixture();
        databases = mockConstruction(Database.class, (database, context) ->
                when(database.saveFactionDurably(any())).thenAnswer(call -> saveWorks.get()));
        ledgers = mockConstruction(Ledger.class, (ledger, context) -> {
            when(ledger.getHistory()).thenReturn(mock(net.tfminecraft.simplefactions.guild.income.LedgerHistory.class));
            when(ledger.getDividendBreakdown()).thenReturn(new DividendBreakdown(0, 0, 0, 0, 1, 2.55));
        });
        Members.Resolver resolver = mock(Members.Resolver.class, CALLS_REAL_METHODS);
        when(resolver.info(anyString())).thenAnswer(call -> Optional.ofNullable(characters.get(call.getArgument(0))));
        when(resolver.online(anyString())).thenReturn(Optional.empty());
        Members.setResolver(resolver);
        characters.put(key, info(dead, CharacterStatus.DEAD));
        when(fixture.ui.plugin.getSessionManager()).thenReturn(new SessionManager());
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        Files.createDirectories(folder.resolve("Data"));
        effects = new FactionDeaths(folder);
        store = new DeathJournalStore(folder.resolve("deaths"), effects::apply);
    }

    @AfterEach void close() {
        Members.useSharedIdentity();
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        ledgers.close();
        databases.close();
        fixture.close();
    }

    static CharacterInfo info(CharacterRef ref, CharacterStatus status) {
        return new CharacterInfo(ref, "Character", status, null, Set.of(), 0, Instant.EPOCH);
    }

    String living() {
        CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        String member = ref.characterId().toString();
        characters.put(member, info(ref, CharacterStatus.ALIVE));
        return member;
    }

    Faction realm(String leader) {
        Faction faction = fixture.saved("realm", leader);
        faction.getOrCreateMainGuild();
        return faction;
    }

    DeathJournal capture() throws Exception { return effects.capture(dead, UUID.randomUUID(), 1000); }
    DeathJournal.Step step(DeathJournal journal, String kind) {
        return journal.steps.stream().filter(value -> value.kind.equals(kind)).findFirst().orElseThrow();
    }

    @Test void capturedSuccessionAndFinancialClaimsSurviveRightsSaveBeforeSuccession() throws Exception {
        Faction faction = realm(key);
        Guild guild = faction.getOrCreateMainGuild();
        String oldest = living(), younger = living();
        guild.addMember(oldest);
        guild.addMember(younger);
        guild.getInvites().add(key);
        MercenaryCompanyData company = new MercenaryCompanyData();
        company.name = "Company";
        company.enlisted.addAll(List.of(key, oldest));
        company.pendingWages.put(key, 12.34);
        company.pendingWages.put(oldest, 7.0);
        company.activeWageOverrides.put(key, 10.0);
        company.peacetimeWageOverrides.put(key, 2.0);
        guild.setCompany(new MercenaryCompany(guild, company, null));
        var sessions = fixture.ui.plugin.getSessionManager();
        sessions.newSession(fixture.player("Account"), faction);
        DeathJournal journal = capture();
        assertTrue(effects.referencedCharacters().contains(key));
        assertEquals(oldest, step(journal, "succession").plan.get("successor").getAsString());
        var interrupted = new DeathJournalStore(folder.resolve("deaths"), (record, stage) -> {
            if (stage.kind.equals("succession")) throw new IOException("interrupted after rights saved");
            return effects.apply(record, stage);
        });
        assertThrows(IOException.class, () -> interrupted.record(journal));
        assertFalse(guild.getMembers().contains(key));
        assertFalse(guild.getInvites().contains(key));
        assertEquals("", faction.getLeader());
        assertFalse(guild.getCompany().getPendingWages().containsKey(key));
        assertEquals(7.0, guild.getCompany().getPendingWages().get(oldest));
        assertFalse(guild.getCompany().serialize().activeWageOverrides.containsKey(key));
        assertFalse(guild.getCompany().serialize().peacetimeWageOverrides.containsKey(key));
        assertEquals(Set.of(1234L, 255L), new HashSet<>(store.pendingPayments().stream().map(OwedPayment::cents).toList()));
        // Reordering the live collection must not replace the successor recorded before effects.
        guild.getMembers().remove(oldest);
        guild.getMembers().add(oldest);
        store.replayAll();
        assertEquals(oldest, faction.getLeader());
        store.replayAll();
        assertEquals(oldest, faction.getLeader());
        assertTrue(DurableJson.read(folder.resolve("deaths/" + journal.deathId + ".json"), DeathJournal.class).complete);
    }

    @Test void failedFactionSaveKeepsClaimsUnavailableUntilAbsoluteCleanupCanRepeat() throws Exception {
        Faction faction = realm(key);
        faction.getOrCreateMainGuild().addMember(living());
        DeathJournal journal = capture();
        saveWorks.set(false);
        assertThrows(IOException.class, () -> store.record(journal));
        assertTrue(store.pendingPayments().isEmpty());
        assertEquals("", faction.getLeader());
        saveWorks.set(true);
        store.replayAll();
        assertFalse(faction.getLeader().isBlank());
        assertEquals(1, store.pendingPayments().size());
    }

    @Test void failedSuccessionSaveIsRetriedEvenWhenMemoryAlreadyHasItsFinalLeaderOrEmptySeat() throws Exception {
        for (boolean empty : List.of(false, true)) {
            FactionManager.factions.clear();
            Faction faction = realm(living());
            Guild guild = fixture.guild(faction, "guild", key);
            String successor = living();
            if (!empty) guild.addMember(successor);
            DeathJournal journal = capture();
            var interrupted = new DeathJournalStore(folder.resolve("deaths"), (record, stage) -> {
                if (stage.kind.equals("succession")) saveWorks.set(false);
                return effects.apply(record, stage);
            });
            saveWorks.set(true);
            assertThrows(IOException.class, () -> interrupted.record(journal));
            if (empty) assertTrue(guild.isLeaderless());
            else assertEquals(successor, guild.getLeader());
            int savesBeforeReplay = databases.constructed().size();
            saveWorks.set(true);
            store.replayAll();
            assertTrue(databases.constructed().size() > savesBeforeReplay);
            assertTrue(DurableJson.read(folder.resolve("deaths/" + journal.deathId + ".json"), DeathJournal.class).complete);
        }
    }

    @Test void ordinaryEmptyGuildStaysOpenWithoutALeaderAndItsPaymentSurvives() throws Exception {
        Faction faction = realm(living());
        Guild guild = fixture.guild(faction, "company", key);
        guild.refreshDividendEligibility();
        UUID incarnation = UUID.fromString(guild.getIncarnation());
        DeathJournal journal = capture();
        store.record(journal);
        assertTrue(faction.getGuildHandler().getGuilds().contains(guild));
        assertTrue(guild.isLeaderless());
        assertEquals(key, guild.getDeadLeader());
        assertEquals(journal.at, guild.getLeaderlessSince());
        assertTrue(guild.getMembers().isEmpty());
        assertEquals(incarnation, store.pendingPayments().getFirst().guildIncarnation());
        assertSame(faction, FactionManager.getByString("realm"));
    }

    @Test void emptyRulingGuildPreservesLivingSubguildAndBlocksUntilStaffAppoint() throws Exception {
        Faction faction = realm(key);
        Guild livingGuild = fixture.guild(faction, "survivors", living());
        DeathJournal journal = capture();
        store.record(journal);
        assertEquals("", faction.getLeader());
        assertTrue(faction.getGuildHandler().getGuilds().contains(livingGuild));
        assertFalse(DurableJson.read(folder.resolve("deaths/" + journal.deathId + ".json"), DeathJournal.class).complete);
        faction.succeedDeadLeader(key, livingGuild.getLeader());
        store.replayAll();
        assertTrue(DurableJson.read(folder.resolve("deaths/" + journal.deathId + ".json"), DeathJournal.class).complete);
    }

    @Test void electiveVacancyStartsElectionAndReplayDoesNotEraseLaterBallots() throws Exception {
        fixture.lawGroup("constitution", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
        Faction faction = realm(key);
        fixture.guild(faction, "survivors", living());
        assertTrue(faction.getGovernment().hasLeaderElections());
        DeathJournal journal = capture();
        store.record(journal);
        assertEquals("", faction.getLeader());
        assertTrue(faction.getGovernment().getElection().isActive());
        long started = faction.getGovernment().serialize().lastElectionDate;
        faction.getGovernment().beginDeathElection(started);
        assertEquals(started, faction.getGovernment().serialize().lastElectionDate);
    }

    @Test void completelyEmptyRealmDeletesItsFileAndRelationsButPreservesClaims() throws Exception {
        Faction faction = realm(key);
        Faction survivor = fixture.saved("survivor", living());
        survivor.getOrCreateMainGuild();
        fixture.subject(survivor, faction);
        Files.writeString(folder.resolve("Data/realm.json"), "old faction");
        DeathJournal journal = capture();
        store.record(journal);
        assertNull(FactionManager.getByString("realm"));
        assertFalse(Files.exists(folder.resolve("Data/realm.json")));
        assertFalse(store.pendingPayments().isEmpty());
        effects.apply(journal, step(journal, "succession"));
        fixture.saved("realm", living()).getOrCreateMainGuild();
        assertThrows(IOException.class, () -> effects.apply(journal, step(journal, "succession")));
    }

    @Test void changedRealmMembershipAndQuarantinePreventDestructiveSuccession() throws Exception {
        Faction faction = realm(key);
        DeathJournal journal = capture();
        effects.apply(journal, step(journal, "rights"));
        faction.getOrCreateMainGuild().addMember(living());
        assertFalse(effects.apply(journal, step(journal, "succession")));
        faction.getOrCreateMainGuild().getMembers().clear();
        faction.setLeader(Members.QUARANTINED + "unresolved");
        assertFalse(effects.apply(journal, step(journal, "succession")));
        step(journal, "succession").plan.addProperty("emptyRealm", false);
        assertFalse(effects.apply(journal, step(journal, "succession")));
    }

    @Test void missingDeadOrReplacedSuccessorNeverSelectsANewPersonDuringReplay() throws Exception {
        Faction faction = realm(living());
        Guild guild = fixture.guild(faction, "guild", key);
        String successor = living();
        guild.addMember(successor);
        DeathJournal journal = capture();
        effects.apply(journal, step(journal, "rights"));
        CharacterInfo chosen = characters.get(successor);
        characters.put(successor, info(chosen.ref(), CharacterStatus.DEAD));
        assertFalse(effects.apply(journal, step(journal, "succession")));
        assertEquals("", guild.getLeader());
        assertTrue(guild.isLeaderless());
        String staff = living();
        guild.setLeader(staff);
        assertTrue(effects.apply(journal, step(journal, "succession")));
        assertEquals(staff, guild.getLeader());
        faction.getGuildHandler().removeGuild(guild.getId());
        assertTrue(effects.apply(journal, step(journal, "succession")));
        FactionManager.factions.clear();
        assertTrue(effects.apply(journal, step(journal, "rights")));
        assertTrue(effects.apply(journal, step(journal, "succession")));
        assertThrows(IOException.class, () -> effects.apply(journal, new DeathJournal.Step("bad", "unknown", new JsonObject())));
    }

    @Test void successionNeverDisplacesALivingSeatHolderButFillsABlankSeat() throws Exception {
        Faction faction = realm(living());
        Guild guild = fixture.guild(faction, "guild", key);
        String successor = living();
        guild.addMember(successor);
        DeathJournal journal = capture();
        // Revived and holding the seat again: this death's succession leaves them in it.
        characters.put(key, info(dead, CharacterStatus.ALIVE));
        assertTrue(effects.apply(journal, step(journal, "succession")));
        assertEquals(key, guild.getLeader());
        characters.put(key, info(dead, CharacterStatus.DEAD));
        guild.setLeader("");
        assertTrue(effects.apply(journal, step(journal, "succession")));
        assertEquals(successor, guild.getLeader());
    }

    @Test void scanningForReferencesNeverCreatesARulingGuild() {
        Faction bare = fixture.saved("bare", key);
        bare.getGuildHandler().restoreTopology(List.of());
        assertTrue(effects.referencedCharacters().contains(key));
        assertNull(bare.getGuildHandler().getGuild("bare"));
    }

    @Test void successfulOfficeCleanupPreservesTheExistingPrivateVacancyNotice() throws Exception {
        Faction faction = realm(key);
        faction.getOrCreateMainGuild().addMember(living());
        var holder = new net.tfminecraft.simplefactions.espionage.SpecialPositionAssignment();
        holder.playerId = dead.ownerId();
        holder.characterId = key;
        faction.getEspionage().assignFounder(net.tfminecraft.simplefactions.espionage.SpecialPosition.SPYMASTER, holder, 50);
        holder.characterBoundAt = 0;
        var owner = fixture.player("Owner");
        when(owner.getUniqueId()).thenReturn(dead.ownerId());
        store.record(capture());
        assertNull(faction.getEspionage().getSpymaster());
        verify(owner).sendMessage(contains("passing of Character"));
        store.replayAll();
        verify(owner).sendMessage(contains("passing of Character"));
    }

    @Test void politicalCleanupRemovesOnlyTheDeadCharactersRights() {
        Faction faction = realm(living());
        var government = faction.getGovernment();
        var council = government.getCouncil();
        String other = living();
        council.getMembers().addAll(List.of(key, other));
        council.toggleRefuse(key);
        var proposal = new net.tfminecraft.simplefactions.government.proposal.Proposal(key, government);
        council.getProposalHandler().getProposals().add(proposal);
        var election = government.getElection();
        election.restoreFromData(Map.of("LEADER", List.of(key, other)),
                Map.of("LEADER", Map.of(key, other, other, key, "survivor", other)),
                Map.of("LEADER", Map.of(key, 3, other, 1)), List.of(key, other));
        var movement = mock(net.tfminecraft.simplefactions.government.movement.Movement.class);
        var cause = mock(net.tfminecraft.simplefactions.government.movement.cause.Cause.class);
        var supporters = new net.tfminecraft.simplefactions.government.movement.Pool();
        supporters.getCitizens().addAll(List.of(key, other));
        var causePool = new net.tfminecraft.simplefactions.government.movement.Pool();
        causePool.getCitizens().addAll(List.of(key, other));
        when(movement.getLeader()).thenReturn(key);
        when(movement.getSupporters()).thenReturn(supporters);
        when(movement.getCauses()).thenReturn(List.of(cause));
        when(cause.getLeader()).thenReturn(key);
        when(cause.getPool()).thenReturn(causePool);
        government.getMovements().add(movement);
        government.removeDeadCharacter(key);
        assertEquals(List.of(other), council.getMembers());
        assertFalse(council.refuses(key));
        assertTrue(council.getProposalHandler().getProposals().isEmpty());
        assertEquals(List.of(other), election.serializeCandidates().get("LEADER"));
        assertEquals(Map.of("survivor", other), election.serializeVotes().get("LEADER"));
        assertEquals(Map.of(other, 1), election.serializePreviousVotes().get("LEADER"));
        assertEquals(List.of(other), election.getStoredEligibleVoters());
        assertEquals(List.of(other), supporters.getCitizens());
        assertEquals(List.of(other), causePool.getCitizens());
        verify(movement).setLeader(null);
        verify(cause).setLeader(null);
    }

    @Test void ordinaryGuildChoosesItsFirstLivingRemainingMember() throws Exception {
        Faction faction = realm(living());
        Guild guild = fixture.guild(faction, "guild", key);
        String fallen = UUID.randomUUID().toString();
        characters.put(fallen, info(new CharacterRef(UUID.randomUUID(), UUID.fromString(fallen)), CharacterStatus.DEAD));
        String oldest = living(), younger = living();
        guild.getMembers().addAll(List.of(fallen, oldest, younger));
        store.record(capture());
        assertEquals(oldest, guild.getLeader());
    }

    @Test void invalidFinancialAmountRefusesToCaptureBeforeAnyRightsAreRemoved() throws Exception {
        Faction faction = realm(key);
        when(faction.getOrCreateMainGuild().getLedger().getDividendBreakdown())
                .thenReturn(new DividendBreakdown(0, 0, 0, 0, 1, Double.NaN));
        assertThrows(IOException.class, this::capture);
        assertTrue(faction.getOrCreateMainGuild().getMembers().contains(key));
    }

    @Test void warbandLeadershipAndInterruptedSaveReplayUseTheOriginalCharacters() throws Exception {
        String successorKey = living();
        CharacterRef successor = characters.get(successorKey).ref();
        UUID unmapped = UUID.randomUUID();
        var band = Warband.createWithMemberIds("surviving-band", dead.ownerId(), true, unmapped, successor.ownerId());
        band.restoreMemberCharacters(Map.of(dead.ownerId(), dead, successor.ownerId(), successor));
        WarbandManager.addWarband(band);
        assertTrue(effects.referencedCharacters().contains(key));
        DeathJournal journal = capture();
        Path warbandFile = folder.resolve("Warbands/warband_surviving-band.json");
        WarbandCharacters.save(warbandFile.toFile(), band);
        try (var disk = mockStatic(DurableJson.class, CALLS_REAL_METHODS)) {
            disk.when(() -> DurableJson.write(eq(warbandFile), any(), any(com.google.gson.Gson.class)))
                    .thenThrow(new IOException("interrupted save"));
            assertThrows(IOException.class, () -> store.record(journal));
        }
        assertFalse(band.hasMember(dead.ownerId()));
        assertEquals(successor.ownerId(), band.getLeaderId());
        assertTrue(WarbandCharacters.isHeld(warbandFile.toFile()));
        assertFalse(store.find(journal.deathId).orElseThrow().complete);
        assertThrows(IOException.class, store::replayAll);
        WarbandManager.resetForTests();
        var restarted = WarbandCharacters.read(warbandFile.toFile());
        WarbandManager.addWarband(restarted);
        store.replayAll();
        var saved = WarbandCharacters.read(warbandFile.toFile());
        assertFalse(saved.hasMember(dead.ownerId()));
        assertEquals(successor.ownerId(), saved.getLeaderId());
        assertEquals(Optional.of(successor), saved.getMemberCharacter(successor.ownerId()));
        assertTrue(saved.hasMember(unmapped));
        assertTrue(saved.getMemberCharacter(unmapped).isEmpty());
        assertTrue(store.find(journal.deathId).orElseThrow().complete);
        var empty = Warband.createWithMemberIds("vacant-band", dead.ownerId(), true);
        empty.restoreMemberCharacters(Map.of(dead.ownerId(), dead));
        WarbandManager.addWarband(empty);
        store.record(capture());
        assertTrue(empty.isPendingLeader());
    }

    @Test void deathClearsOnlyItsOwnPendingRejoin() throws Exception {
        var band = Warband.createWithMemberIds("band", dead.ownerId(), true);
        band.restoreMemberCharacters(Map.of(dead.ownerId(), dead));
        WarbandManager.addWarband(band);
        var membership = WarbandMembershipService.getInstance();
        membership.handleQuit(dead.ownerId());
        assertTrue(membership.getPendingRejoin(dead.ownerId()).isFor(dead));
        store.record(capture());
        assertNull(membership.getPendingRejoin(dead.ownerId()));
        assertTrue(band.isPendingLeader());
        CharacterRef other = new CharacterRef(dead.ownerId(), UUID.randomUUID());
        band.addMember(dead.ownerId());
        band.restoreMemberCharacters(Map.of(dead.ownerId(), other));
        membership.handleQuit(dead.ownerId());
        store.record(capture());
        assertTrue(membership.getPendingRejoin(dead.ownerId()).isFor(other));
    }

    @Test void lastMemberDeathAlsoDisbandsAnAlreadyVacantRealm() throws Exception {
        Faction faction = realm("");
        assertTrue(faction.getOrCreateMainGuild().getMembers().isEmpty());
        fixture.guild(faction, "last", key);
        store.record(capture());
        assertNull(FactionManager.getByString("realm"));
    }

    @Test void warbandRemovalUsesCapturedCharacterAfterSessionHasEnded() throws Exception {
        CharacterRef survivor = characters.get(living()).ref();
        var band = Warband.createWithMemberIds("band", survivor.ownerId(), true, dead.ownerId());
        band.restoreMemberCharacters(Map.of(survivor.ownerId(), survivor, dead.ownerId(), dead));
        WarbandManager.addWarband(band);
        var account = fixture.player("Account");
        when(account.getUniqueId()).thenReturn(dead.ownerId());
        assertTrue(Members.session(account).isEmpty());
        DeathJournal journal = capture();
        store.record(journal);
        assertFalse(band.hasMember(dead.ownerId()));
        Path warbandFile = folder.resolve("Warbands/warband_band.json");
        assertFalse(WarbandCharacters.read(warbandFile.toFile()).hasMember(dead.ownerId()));
        var step = step(journal, "online");
        CharacterRef other = new CharacterRef(dead.ownerId(), UUID.randomUUID());
        band.addMember(dead.ownerId(), other);
        assertTrue(effects.apply(journal, step));
        assertTrue(band.hasMember(dead.ownerId()));
        assertEquals(Optional.of(other), band.getMemberCharacter(dead.ownerId()));
        assertEquals(Optional.of(other), WarbandCharacters.read(warbandFile.toFile()).getMemberCharacter(dead.ownerId()));
        WarbandManager.resetForTests();
        assertTrue(effects.apply(journal, step));
    }
}
