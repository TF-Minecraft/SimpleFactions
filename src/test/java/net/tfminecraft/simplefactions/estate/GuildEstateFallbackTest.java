package net.tfminecraft.simplefactions.estate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.DividendBreakdown;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.identity.death.CharacterDeaths;
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
import net.tfminecraft.tlibs.character.estate.DeathGuild;
import net.tfminecraft.tlibs.character.estate.EstateFallback;
import net.tfminecraft.tlibs.character.estate.Estates;
import net.tfminecraft.tlibs.character.estate.GuildLookup;
import net.tfminecraft.tlibs.character.estate.GuildRef;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

/** Death guilds and guild lookups for estates, from the journals and live guilds only (E5, E10). */
class GuildEstateFallbackTest {
    @TempDir Path folder;
    FactionDomainFixture fixture;
    MockedConstruction<Database> databases;
    MockedConstruction<Ledger> ledgers;
    final Map<String, CharacterInfo> characters = new HashMap<>();
    final Map<UUID, UUID> deathIds = new HashMap<>();
    final AtomicBoolean saveWorks = new AtomicBoolean(true);
    final GuildEstateFallback fallback = new GuildEstateFallback();
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
        net.tfminecraft.simplefactions.guild.LeaderlessGuilds.resetForTests();
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
        characters.put(ref.characterId().toString(), info(ref, CharacterStatus.ALIVE));
        return ref;
    }

    static CharacterInfo info(CharacterRef ref, CharacterStatus status) {
        return new CharacterInfo(ref, "Character", status, null, Set.of(), 0, Instant.EPOCH);
    }

    static String key(CharacterRef ref) {
        return ref.characterId().toString();
    }

    UUID kill(CharacterRef ref) throws IOException {
        characters.put(key(ref), info(ref, CharacterStatus.DEAD));
        UUID death = UUID.randomUUID();
        deathIds.put(ref.characterId(), death);
        CharacterDeaths.onDeath(ref, death, Instant.ofEpochMilli(clock++));
        return death;
    }

    Faction realm(String id, CharacterRef leader) {
        Faction faction = fixture.saved(id, key(leader));
        faction.getOrCreateMainGuild();
        return faction;
    }

    Guild guild(Faction faction, String id, CharacterRef leader, CharacterRef... members) {
        Guild guild = fixture.guild(faction, id, key(leader));
        for (CharacterRef member : members) guild.addMember(key(member));
        return guild;
    }

    /** A realm whose ruling guild is missing; removeGuild would recreate it while updating wealth. */
    static void withoutRulingGuild(Faction faction) {
        faction.getGuildHandler().restoreTopology(faction.getGuildHandler().getGuilds().stream()
                .filter(guild -> !guild.isBase()).toList());
        assertNull(faction.getGuildHandler().getGuild(faction.getId()));
    }

    static UUID ruling(Faction faction) {
        return faction.getGuildHandler().getGuild(faction.getId()).getIncarnationId();
    }

    @Test void deathInALedGuildNamesItsLeaderAndTheRealmAtDeath() throws Exception {
        CharacterRef ruler = character();
        CharacterRef master = character();
        CharacterRef owner = character();
        Faction faction = realm("realm", ruler);
        Guild smiths = guild(faction, "smiths", master, owner);
        UUID death = kill(owner);
        assertEquals(DeathGuild.guild(smiths.getIncarnationId(), true, Optional.of(master.characterId()),
                Optional.of(ruling(faction))), fallback.deathGuild(owner.characterId(), death));
    }

    @Test void deathInTheRulingGuildNamesTheRealmsLeader() throws Exception {
        CharacterRef ruler = character();
        CharacterRef owner = character();
        Faction faction = realm("realm", ruler);
        faction.getOrCreateMainGuild().addMember(key(owner));
        faction.getOrCreateMainGuild().addMember(key(ruler));
        UUID death = kill(owner);
        var answer = (DeathGuild.Guild) fallback.deathGuild(owner.characterId(), death);
        assertEquals(ruling(faction), answer.incarnation());
        assertEquals(Optional.of(ruler.characterId()), answer.leader());
        assertEquals(Optional.of(ruling(faction)), answer.realm());
    }

    @Test void disbandedGuildIsGoneButKeepsItsRealm() throws Exception {
        CharacterRef master = character();
        CharacterRef owner = character();
        Faction faction = realm("realm", character());
        Guild smiths = guild(faction, "smiths", master, owner);
        UUID death = kill(owner);
        faction.getGuildHandler().removeGuild(smiths.getId());
        assertEquals(DeathGuild.guild(smiths.getIncarnationId(), false, Optional.empty(), Optional.of(ruling(faction))),
                fallback.deathGuild(owner.characterId(), death));
        assertEquals(GuildLookup.GONE, fallback.guild(smiths.getIncarnationId()));
        assertInstanceOf(GuildLookup.Live.class, fallback.guild(ruling(faction)), "its realm is still live");
    }

    @Test void leaderlessGuildIsLiveWithoutALeader() throws Exception {
        CharacterRef master = character();
        CharacterRef owner = character();
        Faction faction = realm("realm", character());
        Guild smiths = guild(faction, "smiths", master, owner);
        UUID death = kill(owner);
        smiths.setLeader("");
        var answer = (DeathGuild.Guild) fallback.deathGuild(owner.characterId(), death);
        assertTrue(answer.live());
        assertEquals(Optional.empty(), answer.leader());
    }

    @Test void aHeldRealmIsUnavailableUntilStaffTakeItOn() throws Exception {
        CharacterRef ruler = character();
        Faction faction = realm("realm", ruler);
        UUID death = kill(ruler);
        Guild main = faction.getGuildHandler().getGuild("realm");
        assertTrue(main.isHeld());
        assertEquals(DeathGuild.UNAVAILABLE, fallback.deathGuild(ruler.characterId(), death));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guild(main.getIncarnationId()));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guildByName(main.getName()), "by name as by incarnation");
        CharacterRef heir = character();
        faction.addMember(key(heir));
        faction.setLeader(key(heir));
        var answer = (DeathGuild.Guild) fallback.deathGuild(ruler.characterId(), death);
        assertTrue(answer.live());
        assertEquals(Optional.of(heir.characterId()), answer.leader());
    }

    @Test void anUnsavedClosureIsUnavailableUntilItsRealmIsSaved() throws Exception {
        CharacterRef master = character();
        Faction faction = realm("realm", character());
        Guild smiths = guild(faction, "smiths", master);
        UUID death = kill(master);
        assertTrue(smiths.isLeaderless());
        var open = (DeathGuild.Guild) fallback.deathGuild(master.characterId(), death);
        assertTrue(open.live());
        assertEquals(Optional.empty(), open.leader());
        long due = smiths.getEmptySince() + net.tfminecraft.simplefactions.guild.LeaderlessGuilds.CLOSE_AFTER.toMillis();
        saveWorks.set(false);
        net.tfminecraft.simplefactions.guild.LeaderlessGuilds.closeExpired(due);
        assertNull(faction.getGuildHandler().getGuild("smiths"));
        assertEquals(DeathGuild.UNAVAILABLE, fallback.deathGuild(master.characterId(), death));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guild(smiths.getIncarnationId()));
        net.tfminecraft.simplefactions.guild.LeaderlessGuilds.closeExpired(due + 1);
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guild(smiths.getIncarnationId()));
        saveWorks.set(true);
        net.tfminecraft.simplefactions.guild.LeaderlessGuilds.closeExpired(due + 2);
        assertEquals(GuildLookup.GONE, fallback.guild(smiths.getIncarnationId()));
        assertFalse(((DeathGuild.Guild) fallback.deathGuild(master.characterId(), death)).live());
    }

    @Test void journalWithoutTheRealmFieldFindsItInTheStoredPlan() throws Exception {
        CharacterRef owner = character();
        Faction faction = realm("realm", character());
        Guild smiths = guild(faction, "smiths", character(), owner);
        UUID death = kill(owner);
        Path file = folder.resolve("deaths/" + death + ".json");
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(ruling(faction).toString(), json.get("lockGuildRealm").getAsString());
        json.remove("lockGuildRealm");
        Files.writeString(file, json.toString());
        assertEquals(Optional.of(ruling(faction)),
                ((DeathGuild.Guild) fallback.deathGuild(owner.characterId(), death)).realm());
        assertEquals(smiths.getIncarnationId(), ((DeathGuild.Guild) fallback.deathGuild(owner.characterId(), death)).incarnation());
    }

    @Test void guildlessDeathAndDeathsSimpleFactionsNeverSawAnswerNone() throws Exception {
        CharacterRef loner = character();
        UUID death = kill(loner);
        assertEquals(DeathGuild.NONE, fallback.deathGuild(loner.characterId(), death));
        assertEquals(DeathGuild.NONE, fallback.deathGuild(character().characterId(), UUID.randomUUID()));
    }

    @Test void referencedDeathWithoutAJournalIsUnavailable() throws Exception {
        CharacterRef owner = character();
        guild(realm("realm", character()), "smiths", character(), owner);
        characters.put(key(owner), info(owner, CharacterStatus.DEAD));
        assertEquals(DeathGuild.UNAVAILABLE, fallback.deathGuild(owner.characterId(), UUID.randomUUID()),
                "the reconcile has not captured it yet");
    }

    @Test void nothingAnswersBeforeSimpleFactionsIsReady() throws Exception {
        CharacterRef owner = character();
        Faction faction = realm("realm", character());
        Guild smiths = guild(faction, "smiths", character(), owner);
        UUID death = kill(owner);
        when(fixture.ui.plugin.isStarted()).thenReturn(false);
        assertEquals(DeathGuild.UNAVAILABLE, fallback.deathGuild(owner.characterId(), death));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guild(smiths.getIncarnationId()));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guildByName("smiths"));
        when(fixture.ui.plugin.isStarted()).thenReturn(true);
        assertEquals(DeathGuild.UNAVAILABLE, fallback.deathGuild(null, death));
        assertEquals(DeathGuild.UNAVAILABLE, fallback.deathGuild(owner.characterId(), null));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guild(null));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guildByName(null));
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guildByName(" "));
    }

    @Test void liveGuildCarriesItsTreasuryRealmAndLeader() {
        CharacterRef ruler = character();
        CharacterRef master = character();
        Faction faction = realm("realm", ruler);
        Guild smiths = guild(faction, "smiths", master);
        assertEquals(GuildLookup.live(new GuildRef(smiths.getIncarnationId(), "smiths", Optional.of(ruling(faction)),
                Optional.of(master.characterId()), "guild:" + smiths.getIncarnationId())), fallback.guild(smiths.getIncarnationId()));
        var realmTreasury = ((GuildLookup.Live) fallback.guild(ruling(faction))).guild();
        assertEquals("guild:" + ruling(faction), realmTreasury.treasuryAccount());
        assertEquals(Optional.of(ruler.characterId()), realmTreasury.leader());
        assertEquals("realm", realmTreasury.name());
    }

    @Test void guildsAreFoundByNameOrIncarnationAndSharedNamesCannotBeToldApart() {
        Faction first = realm("first", character());
        Faction second = realm("second", character());
        Guild smiths = guild(first, "smiths", character());
        assertEquals(smiths.getIncarnationId(), ((GuildLookup.Live) fallback.guildByName(" SMITHS ")).guild().incarnation());
        assertEquals(smiths.getIncarnationId(),
                ((GuildLookup.Live) fallback.guildByName(smiths.getIncarnation())).guild().incarnation());
        assertEquals(GuildLookup.GONE, fallback.guildByName("masons"));
        assertEquals(GuildLookup.GONE, fallback.guildByName(UUID.randomUUID().toString()));
        guild(second, "smiths", character());
        assertEquals(GuildLookup.UNAVAILABLE, fallback.guildByName("smiths"));
    }

    @Test void lookupsNeverCreateARulingGuild() {
        Faction bare = fixture.saved("bare", key(character()));
        Guild smiths = guild(bare, "smiths", character());
        withoutRulingGuild(bare);
        smiths.setName("");
        GuildRef found = ((GuildLookup.Live) fallback.guild(smiths.getIncarnationId())).guild();
        assertEquals(Optional.empty(), found.realm());
        assertEquals("smiths", found.name(), "a blank name falls back to the guild ID");
        assertNull(bare.getGuildHandler().getGuild("bare"));
        assertEquals(DeathGuild.NONE, fallback.deathGuild(character().characterId(), UUID.randomUUID()));
        assertNull(bare.getGuildHandler().getGuild("bare"));
    }

    @Test void realmWithoutARulingGuildAtDeathFallsBackToTheStoredPlan() throws Exception {
        CharacterRef owner = character();
        Faction bare = fixture.saved("bare", key(character()));
        guild(bare, "smiths", character(), owner);
        withoutRulingGuild(bare);
        UUID death = kill(owner);
        var json = JsonParser.parseString(Files.readString(folder.resolve("deaths/" + death + ".json"))).getAsJsonObject();
        assertFalse(json.has("lockGuildRealm"), "no ruling guild when the death was captured");
        // The capture's own snapshot then created it, and its stored plan lists the guild.
        assertEquals(Optional.of(ruling(bare)), ((DeathGuild.Guild) fallback.deathGuild(owner.characterId(), death)).realm());
    }

    @Test void civilWarDeathRecordsTheHostRealm() throws Exception {
        Faction host = realm("host", character());
        Faction rebels = realm("rebels", character());
        Guild origin = guild(host, "origin", character());
        CharacterRef member = character();
        rebels.getOrCreateMainGuild().addMember(key(member));
        CivilWarSnapshot snapshot = new CivilWarSnapshot();
        snapshot.setHostFactionId("host");
        snapshot.setTempRebelFactionId("rebels");
        snapshot.getMemberMoves().add(new CivilWarMemberMove(key(member), "origin", false));
        War war = mock(War.class);
        when(war.isActive()).thenReturn(true);
        when(war.getCivilWarSnapshot()).thenReturn(snapshot);
        WarManager.get().add(war);
        UUID death = kill(member);
        var answer = (DeathGuild.Guild) fallback.deathGuild(member.characterId(), death);
        assertEquals(origin.getIncarnationId(), answer.incarnation());
        assertEquals(Optional.of(ruling(host)), answer.realm());
        CharacterRef stayed = character();
        rebels.getOrCreateMainGuild().addMember(key(stayed));
        UUID other = kill(stayed);
        assertEquals(Optional.of(ruling(rebels)), ((DeathGuild.Guild) fallback.deathGuild(stayed.characterId(), other)).realm());
    }

    @Test void registeredFallbackAnswersThroughTheTLibsFacade() throws Exception {
        CharacterRef owner = character();
        Faction faction = realm("realm", character());
        Guild smiths = guild(faction, "smiths", character(), owner);
        UUID death = kill(owner);
        assertEquals(DeathGuild.UNAVAILABLE, Estates.deathGuild(owner.characterId(), death));
        EstateBridge.registerFallback(fixture.ui.plugin);
        assertInstanceOf(GuildEstateFallback.class, Bukkit.getServicesManager().load(EstateFallback.class));
        assertEquals(smiths.getIncarnationId(), ((DeathGuild.Guild) Estates.deathGuild(owner.characterId(), death)).incarnation());
    }
}
