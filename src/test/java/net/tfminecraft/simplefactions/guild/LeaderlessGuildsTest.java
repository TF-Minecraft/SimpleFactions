package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import net.kyori.adventure.text.Component;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;

class LeaderlessGuildsTest {
    private FactionDomainFixture fixture;
    private TestCharacters characters;
    private MockedConstruction<Database> databases;
    private String ruler;
    private String founder;
    private Faction realm;
    private Guild smiths;
    private Player rulerPlayer;
    private int failures;

    @BeforeEach
    void setUp() {
        fixture = new FactionDomainFixture();
        characters = TestCharacters.install();
        databases = mockConstruction(Database.class,
                (database, context) -> when(database.saveFactionDurably(any())).thenAnswer(call -> failures-- <= 0));
        ruler = characters.character(UUID.randomUUID(), "Queen Ada");
        founder = characters.character(UUID.randomUUID(), "Grim Hollow");
        realm = fixture.saved("realm", ruler);
        realm.getOrCreateMainGuild();
        smiths = fixture.guild(realm, "smiths", founder);
        characters.kill(founder);
        smiths.removeDeadCharacter(founder);
        smiths.markLeaderless(founder, 1_000L);
        rulerPlayer = fixture.player("ruler");
        characters.play(rulerPlayer, ruler);
    }

    @AfterEach
    void tearDown() {
        LeaderlessGuilds.resetForTests();
        databases.close();
        fixture.close();
    }

    private String citizen(String name) {
        String key = characters.character(UUID.randomUUID(), name);
        realm.getOrCreateMainGuild().addMember(key);
        return key;
    }

    @Test
    void deadLeaderIsShownUntilTheSeatIsFilled() {
        assertTrue(smiths.isLeaderless());
        assertEquals(founder, smiths.getDeadLeader());
        assertEquals("§7Grim Hollow (dead)", LeaderlessGuilds.deadLeaderLabel(smiths));
        assertEquals(List.of(smiths), LeaderlessGuilds.appointable(ruler));
        assertTrue(LeaderlessGuilds.appointable(founder).isEmpty());

        smiths.setLeader(ruler);
        assertFalse(smiths.isLeaderless());
        assertNull(smiths.getDeadLeader());
        assertNull(LeaderlessGuilds.deadLeaderLabel(smiths));
    }

    @Test
    void markingAgainKeepsTheFirstTimeAndAnOfferMadeSince() {
        smiths.offerLeadership(ruler);
        smiths.markLeaderless(UUID.randomUUID().toString(), 5_000L);
        assertEquals(1_000L, smiths.getLeaderlessSince());
        assertEquals(founder, smiths.getDeadLeader());
        assertEquals(ruler, smiths.getLeaderOffer());
    }

    @Test
    void appointedCitizenLeadsOnlyAfterAccepting() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);

        assertTrue(LeaderlessGuilds.handle(rulerPlayer, new String[] {"appoint", "smiths", "Kira", "Vale"}));
        assertEquals(kira, smiths.getLeaderOffer());
        assertTrue(smiths.isLeaderless());
        verify(kiraPlayer).sendMessage(any(Component.class));
        assertSame(smiths, LeaderlessGuilds.offeredTo(kira));

        assertTrue(LeaderlessGuilds.handle(kiraPlayer, new String[] {"lead", "smiths"}));
        assertEquals(kira, smiths.getLeader());
        assertTrue(smiths.isMember(kira));
        assertFalse(realm.getOrCreateMainGuild().isMember(kira));
        assertFalse(smiths.isLeaderless());
        assertNull(smiths.getLeaderOffer());
        verify(kiraPlayer).sendMessage("§aYou now lead smiths");
    }

    @Test
    void acceptingMovesTheCitizenOutOfTheirOldGuild() {
        String kira = citizen("Kira Vale");
        Guild masons = fixture.guild(realm, "masons", characters.character(UUID.randomUUID(), "Mason Boss"));
        masons.addMember(kira);
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);
        smiths.offerLeadership(kira);

        LeaderlessGuilds.accept(kiraPlayer, "smiths");
        assertFalse(masons.isMember(kira));
        assertEquals(kira, smiths.getLeader());
    }

    @Test
    void onlyTheRealmLeaderAppointsALivingCitizenWhoLeadsNoGuild() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);

        LeaderlessGuilds.appoint(kiraPlayer, "smiths", "Kira Vale");
        verify(kiraPlayer).sendMessage(contains("Only the leader of realm"));

        LeaderlessGuilds.appoint(rulerPlayer, "nowhere", "Kira Vale");
        verify(rulerPlayer).sendMessage("§cNo guild by the id nowhere");

        String outsider = characters.character(UUID.randomUUID(), "Outsider");
        assertTrue(outsider != null);
        LeaderlessGuilds.appoint(rulerPlayer, "smiths", "Outsider");
        verify(rulerPlayer).sendMessage("§cOutsider is not a member of realm");

        LeaderlessGuilds.appoint(rulerPlayer, "smiths", "Queen Ada");
        verify(rulerPlayer).sendMessage("§cQueen Ada already leads a guild");

        String fallen = citizen("Fallen One");
        characters.kill(fallen);
        LeaderlessGuilds.appoint(rulerPlayer, "smiths", "Fallen One");
        verify(rulerPlayer).sendMessage("§cFallen One is not alive");

        LeaderlessGuilds.handle(rulerPlayer, new String[] {"appoint", "smiths"});
        verify(rulerPlayer).sendMessage("§cUse /guild appoint <guild> <character name>");
        assertNull(smiths.getLeaderOffer());

        smiths.setLeader(kira);
        LeaderlessGuilds.appoint(rulerPlayer, "smiths", "Kira Vale");
        verify(rulerPlayer).sendMessage("§csmiths §calready has a leader");
    }

    @Test
    void acceptIsRefusedWithoutAStandingOffer() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);

        LeaderlessGuilds.accept(kiraPlayer, "smiths");
        verify(kiraPlayer).sendMessage("§cYou have no leadership offer from that guild");

        smiths.offerLeadership(kira);
        realm.getOrCreateMainGuild().kick(kira);
        LeaderlessGuilds.accept(kiraPlayer, "smiths");
        verify(kiraPlayer).sendMessage("§cYou are no longer a member of realm");
        assertTrue(smiths.isLeaderless());
    }

    @Test
    void declineClearsOnlyThatCharactersOffer() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);

        assertFalse(LeaderlessGuilds.decline(kiraPlayer, smiths));
        smiths.offerLeadership(kira);
        assertTrue(LeaderlessGuilds.decline(kiraPlayer, smiths));
        assertNull(smiths.getLeaderOffer());
        verify(rulerPlayer).sendMessage("§cKira Vale declined the leadership of smiths");
    }

    @Test
    void remindersGoToTheOfferedCharacterAndTheRealmLeader() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);
        smiths.offerLeadership(kira);

        LeaderlessGuilds.remind(kiraPlayer);
        verify(kiraPlayer, atLeastOnce()).sendMessage(any(Component.class));
        LeaderlessGuilds.remind(rulerPlayer);
        verify(rulerPlayer).sendMessage(contains("Appoint one with §f/guild appoint smiths <name>"));
    }

    @Test
    void emptyLeaderlessGuildClosesAfterThirtyDays() {
        long due = 1_000L + LeaderlessGuilds.CLOSE_AFTER.toMillis();
        LeaderlessGuilds.closeExpired(due - 1);
        assertSame(smiths, FactionManager.getGuildByString("smiths"));

        LeaderlessGuilds.closeExpired(due);
        assertNull(FactionManager.getGuildByString("smiths"));
        assertSame(realm, FactionManager.getByString("realm"));
    }

    @Test
    void leaderlessGuildWithMembersStaysOpen() {
        smiths.addMember(citizen("Kira Vale"));
        LeaderlessGuilds.closeExpired(Long.MAX_VALUE);
        assertSame(smiths, FactionManager.getGuildByString("smiths"));
    }

    @Test
    void leaderlessStateIsSavedAndLoaded() {
        smiths.offerLeadership(ruler);
        GuildData data = new GuildData();
        data.id = "copy";
        data.name = "copy";
        data.leader = "";
        data.type = "guild";
        data.rgb = "7,8,9";
        data.capital = -1;
        data.banner = new java.util.ArrayList<>(List.of("white"));
        data.deadLeader = smiths.getDeadLeader();
        data.leaderlessSince = smiths.getLeaderlessSince();
        data.emptySince = smiths.getEmptySince();
        data.leaderOffer = smiths.getLeaderOffer();
        Guild copy = new Guild(data, realm);
        assertTrue(copy.isLeaderless());
        assertEquals(founder, copy.getDeadLeader());
        assertEquals(1_000L, copy.getLeaderlessSince());
        assertEquals(1_000L, copy.getEmptySince());
        assertEquals(ruler, copy.getLeaderOffer());

        data.leaderlessSince = null;
        data.emptySince = null;
        Guild bare = new Guild(data, realm);
        assertFalse(bare.isLeaderless());
        assertEquals(0, bare.getEmptySince());
    }

    @Test
    void handlesOnlyGuildAppointAndLead() {
        assertTrue(LeaderlessGuilds.handles("guild", new String[] {"appoint"}));
        assertTrue(LeaderlessGuilds.handles("GUILD", new String[] {"lead", "smiths"}));
        assertFalse(LeaderlessGuilds.handles("guild", new String[] {"lead"}));
        assertFalse(LeaderlessGuilds.handles("faction", new String[] {"appoint"}));
        assertFalse(LeaderlessGuilds.handles("guild", new String[0]));
        verify(rulerPlayer, never()).sendMessage(any(Component.class));
    }

    @Test
    void acceptIsRefusedToACurrentLeaderAndAnnouncedToTheRealm() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);
        Guild masons = fixture.guild(realm, "masons", kira);
        smiths.offerLeadership(kira);
        LeaderlessGuilds.accept(kiraPlayer, "smiths");
        verify(kiraPlayer).sendMessage("§cYou already lead a guild");

        masons.setLeader(characters.character(UUID.randomUUID(), "Mason Boss"));
        Player outsider = fixture.player("outsider");
        characters.play(outsider, characters.character(UUID.randomUUID(), "Stranger"));
        when(org.bukkit.Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(rulerPlayer, kiraPlayer, outsider));
        LeaderlessGuilds.accept(kiraPlayer, "smiths");
        assertEquals(kira, smiths.getLeader());
        verify(rulerPlayer).sendMessage("§aKira Vale now leads smiths§a!");
        verify(kiraPlayer, never()).sendMessage("§aKira Vale now leads smiths§a!");
        verify(outsider, never()).sendMessage("§aKira Vale now leads smiths§a!");
    }

    @Test
    void aFailedSaveUndoesTheAcceptanceAndKeepsTheOffer() {
        String first = citizen("First Member");
        String kira = citizen("Kira Vale");
        String last = citizen("Last Member");
        Guild base = realm.getOrCreateMainGuild();
        int index = base.getMembers().indexOf(kira);
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);
        smiths.offerLeadership(kira);
        failures = 1;

        LeaderlessGuilds.accept(kiraPlayer, "smiths");
        verify(kiraPlayer).sendMessage("§cThe guild could not be saved. Nothing changed; try again shortly.");
        assertTrue(smiths.isLeaderless());
        assertEquals(founder, smiths.getDeadLeader());
        assertEquals(1_000L, smiths.getLeaderlessSince());
        assertEquals(1_000L, smiths.getEmptySince());
        assertEquals(kira, smiths.getLeaderOffer());
        assertFalse(smiths.isMember(kira));
        assertEquals(index, base.getMembers().indexOf(kira));
        assertTrue(base.isMember(first) && base.isMember(last));
    }

    @Test
    void failedSavesUndoAppointmentsAndRefusals() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);
        failures = 1;
        LeaderlessGuilds.appoint(rulerPlayer, "smiths", "Kira Vale");
        verify(rulerPlayer).sendMessage("§cThe guild could not be saved. Nothing changed; try again shortly.");
        assertNull(smiths.getLeaderOffer());

        smiths.offerLeadership(kira);
        failures = 1;
        assertTrue(LeaderlessGuilds.decline(kiraPlayer, smiths));
        verify(kiraPlayer).sendMessage("§cThe guild could not be saved. Nothing changed; try again shortly.");
        assertEquals(kira, smiths.getLeaderOffer());
    }

    @Test
    void theThirtyDaysCountFromWhenTheGuildWasLastEmpty() {
        long day = java.time.Duration.ofDays(1).toMillis();
        String kira = citizen("Kira Vale");
        smiths.addMember(kira);
        LeaderlessGuilds.closeExpired(40 * day);
        assertEquals(0, smiths.getEmptySince());
        smiths.kick(kira);
        LeaderlessGuilds.closeExpired(41 * day);
        assertEquals(41 * day, smiths.getEmptySince());
        LeaderlessGuilds.closeExpired(70 * day);
        assertSame(smiths, FactionManager.getGuildByString("smiths"));
        LeaderlessGuilds.closeExpired(71 * day);
        assertNull(FactionManager.getGuildByString("smiths"));
    }

    @Test
    void leaderlessGuildWithMembersStartsUnemptyAndCannotLeaveTheRealm() {
        Guild crew = fixture.guild(realm, "crew", characters.character(UUID.randomUUID(), "Crew Boss"));
        crew.addMember(citizen("Deckhand"));
        crew.markLeaderless(crew.getLeader(), 5L);
        assertEquals(0, crew.getEmptySince());
        assertFalse(crew.trackEmpty(6L));
        assertFalse(smiths.canBeEvicted(rulerPlayer));
        assertFalse(smiths.canBeElevated(rulerPlayer));
        verify(rulerPlayer, times(2)).sendMessage("§cAppoint a leader for this guild first");
        assertFalse(smiths.canBeElevated(null));
    }

    @Test
    void releasingTheRealmsGuildsKeepsLeaderlessOnesForItsLeader() {
        realm.dissolve(new java.util.ArrayList<>(), new java.util.ArrayList<>(realm.getGuildHandler().getGuilds()));
        assertSame(realm, smiths.getFaction());
        assertTrue(smiths.isLeaderless());
        assertSame(smiths, FactionManager.getGuildByString("smiths"));
    }

    @Test
    void fillingAGuildWithoutAnOfferStillClearsTheVacancy() {
        String kira = citizen("Kira Vale");
        assertNull(LeaderlessGuilds.fill(smiths, kira));
        assertEquals(kira, smiths.getLeader());
        assertFalse(smiths.isLeaderless());
        assertEquals(0, smiths.getEmptySince());
    }

    @Test
    void anUndoThatCannotBeSavedEitherAsksStaffToCheck() {
        String kira = citizen("Kira Vale");
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);
        smiths.offerLeadership(kira);
        failures = 2;
        LeaderlessGuilds.accept(kiraPlayer, "smiths");
        verify(kiraPlayer).sendMessage(LeaderlessGuilds.UNCERTAIN);
        assertTrue(smiths.isLeaderless());
        assertEquals(kira, smiths.getLeaderOffer());
    }

    @Test
    void aClosureWhoseRealmLaterDisappearsIsForgotten() {
        long due = 1_000L + LeaderlessGuilds.CLOSE_AFTER.toMillis();
        failures = 1;
        LeaderlessGuilds.closeExpired(due);
        assertTrue(LeaderlessGuilds.closing(smiths.getIncarnationId()));
        assertFalse(LeaderlessGuilds.closing(null));
        FactionManager.factions.remove(realm);
        LeaderlessGuilds.closeExpired(due + 1);
        assertFalse(LeaderlessGuilds.closing(smiths.getIncarnationId()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = net.tfminecraft.simplefactions.government.movement.Action.class,
            names = {"INDEPENDENCE", "NATIONHOOD"})
    void aSuccessfulBreakawayLeavesLeaderlessGuildsWithTheRealm(
            net.tfminecraft.simplefactions.government.movement.Action action) {
        var cause = org.mockito.Mockito.mock(net.tfminecraft.simplefactions.government.movement.cause.Cause.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(cause.getPool().getGuilds()).thenReturn(new java.util.ArrayList<>(List.of(smiths)));
        var proposal = org.mockito.Mockito.mock(net.tfminecraft.simplefactions.government.proposal.Proposal.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(proposal.isPoliticalActionProposal()).thenReturn(true);
        when(proposal.getPoliticalAction().getAction()).thenReturn(action);
        realm.applyPoliticalAction(cause, proposal);
        assertSame(realm, smiths.getFaction());
        assertTrue(smiths.isLeaderless());
        assertSame(smiths, FactionManager.getGuildByString("smiths"));
    }
}
