package net.tfminecraft.simplefactions.identity.death;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.DividendBreakdown;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarMemberMove;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

/** The guild a character's GUILD and FACTION locks follow, alive and after death. */
class LockGuildTest {
    @TempDir Path folder;
    FactionDomainFixture fixture;
    MockedConstruction<Database> databases;
    MockedConstruction<Ledger> ledgers;
    final Map<String, CharacterInfo> characters = new HashMap<>();
    final Map<UUID, UUID> deathIds = new HashMap<>();
    final AtomicBoolean saveWorks = new AtomicBoolean(true);
    long clock = 1000;

    @BeforeEach void setup() throws Exception {
        fixture = new FactionDomainFixture();
        databases = mockConstruction(Database.class, (database, context) ->
                when(database.saveFactionDurably(any())).thenAnswer(call -> saveWorks.get()));
        ledgers = mockConstruction(Ledger.class, (ledger, context) -> {
            when(ledger.getHistory()).thenReturn(mock(net.tfminecraft.simplefactions.guild.income.LedgerHistory.class));
            when(ledger.getDividendBreakdown()).thenReturn(new DividendBreakdown(0, 0, 0, 0, 1, 0));
        });
        Members.Resolver resolver = mock(Members.Resolver.class, CALLS_REAL_METHODS);
        when(resolver.info(anyString())).thenAnswer(call -> Optional.ofNullable(characters.get(call.getArgument(0))));
        when(resolver.online(anyString())).thenReturn(Optional.empty());
        Members.setResolver(resolver);
        CharacterIdentity identity = Bukkit.getServicesManager().load(CharacterIdentity.class);
        when(identity.info(any(UUID.class))).thenAnswer(call ->
                Optional.ofNullable(characters.get(call.getArgument(0).toString())));
        when(identity.deathId(any(UUID.class))).thenAnswer(call -> Optional.ofNullable(deathIds.get(call.getArgument(0))));
        when(fixture.ui.plugin.getSessionManager()).thenReturn(new SessionManager());
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        Files.createDirectories(folder.resolve("Data"));
        CharacterDeaths.startup(folder);
    }

    @AfterEach void close() {
        CharacterDeaths.stop();
        Members.useSharedIdentity();
        WarbandManager.resetForTests();
        WarbandMembershipService.resetForTests();
        ledgers.close();
        databases.close();
        fixture.close();
    }

    CharacterRef character() {
        CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        status(ref, CharacterStatus.ALIVE);
        return ref;
    }

    void status(CharacterRef ref, CharacterStatus status) {
        characters.put(key(ref), new CharacterInfo(ref, "Character", status, null, Set.of(), 0, Instant.EPOCH));
    }

    static String key(CharacterRef ref) {
        return ref.characterId().toString();
    }

    UUID kill(CharacterRef ref) throws IOException {
        status(ref, CharacterStatus.DEAD);
        UUID death = UUID.randomUUID();
        deathIds.put(ref.characterId(), death);
        CharacterDeaths.onDeath(ref, death, Instant.ofEpochMilli(clock++));
        return death;
    }

    Optional<Guild> lockGuild(CharacterRef ref) {
        return SimpleFactionsApi.lockGuild(ref.characterId());
    }

    /** A guild that keeps a living member, so the dead owner's death does not dissolve it. */
    Guild guild(Faction faction, String id, CharacterRef... members) {
        CharacterRef keeper = character();
        Guild guild = fixture.guild(faction, id, key(keeper));
        for (CharacterRef member : members) guild.addMember(key(member));
        return guild;
    }

    Faction realm(String id) {
        Faction faction = fixture.saved(id, key(character()));
        faction.getOrCreateMainGuild();
        return faction;
    }

    void restart() throws IOException {
        CharacterDeaths.stop();
        CharacterDeaths.startup(folder);
    }

    @Test void livingRevivedDeadAndMissingCharactersEachGetTheirOwnAnswer() throws Exception {
        CharacterRef owner = character();
        Guild smiths = guild(realm("realm"), "smiths", owner);
        assertSame(smiths, lockGuild(owner).orElseThrow());
        when(fixture.ui.plugin.isStarted()).thenReturn(false);
        assertTrue(lockGuild(owner).isEmpty(), "unavailable until SimpleFactions is ready");
        when(fixture.ui.plugin.isStarted()).thenReturn(true);

        kill(owner);
        assertFalse(smiths.isMember(key(owner)));
        assertSame(smiths, lockGuild(owner).orElseThrow(), "the guild at death keeps the dead owner's locks");
        restart();
        assertSame(smiths, lockGuild(owner).orElseThrow(), "rebuilt from the journal at start");

        status(owner, CharacterStatus.ALIVE);
        assertTrue(lockGuild(owner).isEmpty(), "a revived character lost their guild at death");
        smiths.addMember(key(owner));
        assertSame(smiths, lockGuild(owner).orElseThrow());

        status(owner, CharacterStatus.MISSING);
        assertTrue(lockGuild(owner).isEmpty());
        characters.remove(key(owner));
        assertTrue(lockGuild(owner).isEmpty(), "deleted, unknown or quarantined characters have no info");
        assertTrue(SimpleFactionsApi.lockGuild(null).isEmpty());
    }

    @Test void disbandingAndRecreatingTheGuildDoesNotInheritADeadOwnersLocks() throws Exception {
        CharacterRef owner = character();
        Faction faction = realm("realm");
        Guild original = guild(faction, "smiths", owner);
        kill(owner);
        assertSame(original, lockGuild(owner).orElseThrow());
        faction.getGuildHandler().removeGuild(original.getId());
        Guild recreated = guild(faction, "smiths");
        assertEquals(original.getId(), recreated.getId());
        assertTrue(lockGuild(owner).isEmpty(), "a new incarnation is a different guild");
    }

    @Test void ownerWhoDiedAgainWithoutAGuildLeavesNoGuildAccess() throws Exception {
        CharacterRef owner = character();
        Guild smiths = guild(realm("realm"), "smiths", owner);
        kill(owner);
        assertSame(smiths, lockGuild(owner).orElseThrow());
        status(owner, CharacterStatus.ALIVE);
        kill(owner);
        assertTrue(lockGuild(owner).isEmpty(), "the guildless death removed the entry");
        restart();
        assertTrue(lockGuild(owner).isEmpty(), "the most recent journal wins at start");
    }

    @Test void deathFoundByTheBootReconcileRecordsItsGuildWhileNotReady() throws Exception {
        CharacterRef owner = character();
        Guild smiths = guild(realm("realm"), "smiths", owner);
        CharacterDeaths.stop();
        when(fixture.ui.plugin.isStarted()).thenReturn(false);
        status(owner, CharacterStatus.DEAD);
        UUID death = UUID.randomUUID();
        deathIds.put(owner.characterId(), death);
        assertNull(FactionManager.getGuildByMember(owner.characterId()), "the gated lookups answer nothing during boot");

        CharacterDeaths.startup(folder);
        assertFalse(smiths.isMember(key(owner)), "the reconcile cleaned up the death");
        DeathJournal journal = DurableJson.read(folder.resolve("deaths/" + death + ".json"), DeathJournal.class);
        assertEquals(smiths.getIncarnationId(), journal.lockGuildIncarnation);
        assertEquals(Optional.of(smiths.getIncarnationId()), CharacterDeaths.lockGuildIncarnation(owner.characterId()));
        when(fixture.ui.plugin.isStarted()).thenReturn(true);
        assertSame(smiths, lockGuild(owner).orElseThrow());
    }

    @Test void journalWrittenBeforeTheFieldExistedLoadsAndDerivesItsGuildFromTheSnapshot() throws Exception {
        CharacterRef owner = character();
        Guild smiths = guild(realm("realm"), "smiths", owner);
        UUID death = kill(owner);
        Path file = folder.resolve("deaths/" + death + ".json");
        var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertNotNull(json.remove("lockGuildIncarnation"));
        Files.writeString(file, json.toString());
        restart();
        assertSame(smiths, lockGuild(owner).orElseThrow());
        assertTrue(DurableJson.read(file, DeathJournal.class).complete);
    }

    @Test void memberOfTwoGuildsFollowsTheFirstMatchAliveAndDead() throws Exception {
        CharacterRef owner = character();
        Guild first = guild(realm("first"), "first-guild", owner);
        Guild second = guild(realm("second"), "second-guild", owner);
        Guild living = FactionManager.getGuildByMember(key(owner));
        assertSame(first, living);
        assertSame(living, lockGuild(owner).orElseThrow());
        kill(owner);
        assertSame(living, lockGuild(owner).orElseThrow());
        assertNotSame(second, lockGuild(owner).orElseThrow());
    }

    @Test void cleanupThatMustBeRetriedStillUpdatesTheIndex() throws Exception {
        CharacterRef owner = character();
        Guild smiths = guild(realm("realm"), "smiths", owner);
        saveWorks.set(false);
        assertThrows(IOException.class, () -> kill(owner));
        assertSame(smiths, lockGuild(owner).orElseThrow());
    }

    @Test void indexIsUnavailableUntilLoaded() {
        CharacterDeaths.stop();
        assertThrows(IOException.class, CharacterDeaths::indexLockGuilds);
        assertTrue(CharacterDeaths.lockGuildIncarnation(UUID.randomUUID()).isEmpty());
    }

    @Nested class CivilWar {
        Faction host;
        Faction rebels;
        Guild hostMain;
        Guild rebelMain;
        Guild origin;
        Guild rebelGuild;
        CivilWarSnapshot snapshot = new CivilWarSnapshot();
        War war = mock(War.class);

        @BeforeEach void war() {
            host = realm("host");
            rebels = realm("rebels");
            hostMain = host.getOrCreateMainGuild();
            rebelMain = rebels.getOrCreateMainGuild();
            origin = guild(host, "origin");
            rebelGuild = guild(rebels, "rebel-guild");
            snapshot.setHostFactionId("host");
            snapshot.setTempRebelFactionId("rebels");
            when(war.isActive()).thenReturn(true);
            when(war.getCivilWarSnapshot()).thenReturn(snapshot);
            War otherWar = mock(War.class);
            when(otherWar.isActive()).thenReturn(true);
            WarManager.get().add(otherWar);
            WarManager.get().add(war);
        }

        CharacterRef rebel(String originGuildId) {
            CharacterRef member = character();
            rebelMain.addMember(key(member));
            if (originGuildId != null) {
                snapshot.getMemberMoves().add(new CivilWarMemberMove(key(member), originGuildId, false));
            }
            return member;
        }

        UUID recorded(CharacterRef member) throws IOException {
            return new FactionDeaths(folder).capture(member, UUID.randomUUID(), clock++).lockGuildIncarnation;
        }

        @Test void movedMemberFollowsTheOriginGuild() throws Exception {
            CharacterRef member = rebel("origin");
            assertEquals(origin.getIncarnationId(), recorded(member));
            kill(member);
            assertSame(origin, lockGuild(member).orElseThrow());
        }

        @Test void originAmongTheRebelGuildsResolvesAsUntangleWouldAfterMovingThem() throws Exception {
            assertEquals(rebelGuild.getIncarnationId(), recorded(rebel("rebel-guild")));
            assertEquals(rebelMain.getIncarnationId(), recorded(rebel("rebels")));
        }

        @Test void movedMemberWhoseOriginIsGoneFollowsTheHostMainGuild() throws Exception {
            assertEquals(hostMain.getIncarnationId(), recorded(rebel("disbanded")));
        }

        @Test void memberWithoutAMoveKeepsTheRebelMainGuild() throws Exception {
            assertEquals(rebelMain.getIncarnationId(), recorded(rebel(null)));
        }

        @Test void wantedLeaderIsRecordedLikeAnyMovedMember() throws Exception {
            CharacterRef wanted = rebel("origin");
            snapshot.setWantedLeaderName(key(wanted));
            assertEquals(origin.getIncarnationId(), recorded(wanted));
        }

        @Test void relocatedRebelGuildKeepsItsOwnIncarnation() throws Exception {
            CharacterRef member = character();
            rebelGuild.addMember(key(member));
            snapshot.getMemberMoves().add(new CivilWarMemberMove(key(member), "origin", false));
            assertEquals(rebelGuild.getIncarnationId(), recorded(member));
        }

        @Test void missingHostLeavesTheRebelMainGuild() throws Exception {
            CharacterRef member = rebel("origin");
            snapshot.setHostFactionId("vanished");
            assertEquals(rebelMain.getIncarnationId(), recorded(member));
        }

        @Test void endedWarAppliesNoMapping() throws Exception {
            CharacterRef member = rebel("origin");
            when(war.isActive()).thenReturn(false);
            assertEquals(rebelMain.getIncarnationId(), recorded(member));
            when(war.isActive()).thenReturn(true);
            snapshot.setTempRebelFactionId("vanished");
            assertEquals(rebelMain.getIncarnationId(), recorded(member));
        }
    }
}
