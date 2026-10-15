package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.InviteCommands;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.identity.LeaderCharacterListener;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.tlibs.character.CharacterRevivedEvent;

class HeldRealmsTest {
    private static final long AT = 1_000_000L;
    private static final long WEEK = HeldRealms.HOLD.toMillis();
    @TempDir Path folder;
    private FactionDomainFixture fixture;
    private TestCharacters characters;
    private MockedConstruction<Database> databases;
    private int failures;
    private Faction failFor;
    private String ruler;
    private Faction realm;
    private Guild main;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new FactionDomainFixture();
        characters = TestCharacters.install();
        databases = mockConstruction(Database.class,
                (database, context) -> when(database.saveFactionDurably(any()))
                        .thenAnswer(call -> call.getArgument(0) != failFor && failures-- <= 0));
        ruler = characters.character(UUID.randomUUID(), "Queen Ada");
        realm = fixture.saved("realm", ruler);
        main = realm.getOrCreateMainGuild();
        characters.kill(ruler);
        main.removeDeadCharacter(ruler);
        realm.succeedDeadLeader(ruler, "");
        HeldRealms.hold(realm, ruler, AT);
        Files.createDirectories(folder.resolve("Data"));
        Files.writeString(folder.resolve("Data/realm.json"), "{}");
    }

    @AfterEach
    void tearDown() {
        HeldRealms.resetForTests();
        databases.close();
        fixture.close();
    }

    private Faction survivor() {
        Faction survivor = fixture.saved("survivor", characters.character(UUID.randomUUID(), "Bo Reed"));
        survivor.getOrCreateMainGuild();
        fixture.subject(survivor, realm);
        return survivor;
    }

    @Test
    void aHeldRealmShowsItsDeadLeaderUntilStaffSeatSomeone() {
        assertTrue(realm.getMembers().isEmpty());
        assertTrue(HeldRealms.isHeld(realm));
        assertFalse(main.isLeaderless());
        assertFalse(HeldRealms.isHeld(null));
        assertEquals("§7Queen Ada (dead)", LeaderlessGuilds.deadLeaderLabel(main));

        String kira = characters.character(UUID.randomUUID(), "Kira Vale");
        realm.addMember(kira);
        realm.setLeader(kira);
        assertFalse(HeldRealms.isHeld(realm));
        assertNull(main.getDeadLeader());
        assertNull(LeaderlessGuilds.deadLeaderLabel(main));
    }

    @Test
    void aRealmNobodyTakesOnClosesAfterAWeek() {
        Faction survivor = survivor();
        UUID incarnation = main.getIncarnationId();
        assertFalse(HeldRealms.unsettled(null));
        assertFalse(HeldRealms.unsettled(UUID.randomUUID()));
        assertTrue(HeldRealms.unsettled(incarnation), "estates wait while the realm is held");

        HeldRealms.closeExpired(AT + WEEK - 1, folder);
        assertSame(realm, FactionManager.getByString("realm"));
        assertTrue(HeldRealms.unsettled(incarnation));

        HeldRealms.closeExpired(AT + WEEK, folder);
        assertNull(FactionManager.getByString("realm"));
        assertFalse(Files.exists(folder.resolve("Data/realm.json")));
        assertFalse(survivor.getDiplomacyHandler().getRelations().containsKey("realm"));
        assertFalse(HeldRealms.unsettled(incarnation));
    }

    @Test
    void aMemberStopsTheClockAndLeavingStartsItAgain() {
        String kira = characters.character(UUID.randomUUID(), "Kira Vale");
        realm.addMember(kira);
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertSame(realm, FactionManager.getByString("realm"));
        assertEquals(0, main.getEmptySince());
        assertTrue(HeldRealms.isHeld(realm), "the seat stays empty until staff fill it");

        main.getMembers().remove(kira);
        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertEquals(AT + WEEK + 1, main.getEmptySince());
        assertSame(realm, FactionManager.getByString("realm"));
        HeldRealms.closeExpired(AT + 2 * WEEK + 1, folder);
        assertNull(FactionManager.getByString("realm"));
    }

    @Test
    void aClosureThatHasBegunFinishesEvenIfTheLeaderIsRevived() {
        Faction survivor = survivor();
        UUID incarnation = main.getIncarnationId();
        failFor = survivor;
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertSame(realm, FactionManager.getByString("realm"));
        assertEquals(AT + WEEK, main.getClosingSince());
        assertTrue(HeldRealms.unsettled(incarnation));

        assertTrue(HeldRealms.isClosing(realm));
        assertFalse(HeldRealms.isClosing(null));
        characters.revive(ruler);
        HeldRealms.revived(ruler);
        assertTrue(HeldRealms.isHeld(realm), "a closure that has begun is not undone");
        realm.addMember(characters.character(UUID.randomUUID(), "Kira Vale"));
        failFor = null;
        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertNull(FactionManager.getByString("realm"));
        assertFalse(HeldRealms.unsettled(incarnation));
        assertFalse(survivor.getDiplomacyHandler().getRelations().containsKey("realm"));
    }

    @Test
    void aRealmThatCannotBeSavedAsClosingIsLeftUntouchedUntilTheNextHour() {
        Faction survivor = survivor();
        failures = 1;
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertSame(realm, FactionManager.getByString("realm"));
        assertEquals(0, main.getClosingSince());
        assertTrue(survivor.getDiplomacyHandler().getRelations().containsKey("realm"));
        assertTrue(HeldRealms.unsettled(main.getIncarnationId()));
        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertNull(FactionManager.getByString("realm"));
    }

    @Test
    void lettingSomeoneInStopsTheWeekAtOnce() {
        Guild smiths = fixture.guild(realm, "smiths", characters.character(UUID.randomUUID(), "Bo Reed"));
        smiths.getMembers().clear();
        main.restoreLeaderless(ruler, AT, AT, null);
        smiths.addMember(characters.character(UUID.randomUUID(), "Kira Vale"));
        assertEquals(0, main.getEmptySince());
        smiths.getMembers().clear();
        realm.addMember(characters.character(UUID.randomUUID(), "Lin Moss"));
        main.getMembers().clear();
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertSame(realm, FactionManager.getByString("realm"), "the week starts again from the next check");
        assertEquals(AT + WEEK, main.getEmptySince());
    }

    @Test
    void closingRemovesTradeAgreementsAndTreaties() {
        Faction survivor = survivor();
        survivor.getDiplomacyHandler().getTradeRelations().put("realm", null);
        survivor.getDiplomacyHandler().getTreatyRelations().put("realm", null);
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertFalse(survivor.getDiplomacyHandler().getTradeRelations().containsKey("realm"));
        assertFalse(survivor.getDiplomacyHandler().getTreatyRelations().containsKey("realm"));
    }

    @Test
    void aClosureThatFailsAfterLeavingMemoryIsFinishedLater() throws Exception {
        UUID incarnation = main.getIncarnationId();
        Files.delete(folder.resolve("Data/realm.json"));
        Files.delete(folder.resolve("Data"));
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertNull(FactionManager.getByString("realm"));
        assertTrue(HeldRealms.unsettled(incarnation));

        Files.createDirectories(folder.resolve("Data"));
        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertFalse(HeldRealms.unsettled(incarnation));
    }

    @Test
    void aNewRealmThatTookTheNameIsLeftAlone() throws Exception {
        UUID incarnation = main.getIncarnationId();
        Files.delete(folder.resolve("Data/realm.json"));
        Files.delete(folder.resolve("Data"));
        HeldRealms.closeExpired(AT + WEEK, folder);
        Files.createDirectories(folder.resolve("Data"));
        Faction newcomer = fixture.saved("realm", characters.character(UUID.randomUUID(), "Kira Vale"));
        newcomer.getOrCreateMainGuild();
        Files.writeString(folder.resolve("Data/realm.json"), "new");

        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertSame(newcomer, FactionManager.getByString("realm"));
        assertEquals("new", Files.readString(folder.resolve("Data/realm.json")));
        assertFalse(HeldRealms.unsettled(incarnation));
    }

    @Test
    void aRealmAtWarClosesOnlyOnceTheWarIsOver() {
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class, CALLS_REAL_METHODS)) {
            wars.when(() -> WarManager.isAtWar(realm)).thenReturn(true);
            HeldRealms.closeExpired(AT + WEEK, folder);
            assertSame(realm, FactionManager.getByString("realm"));
            assertTrue(HeldRealms.unsettled(main.getIncarnationId()), "estates keep waiting");
        }
        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertNull(FactionManager.getByString("realm"));
    }

    @Test
    void nobodyCanJoinAHeldRealmOnAnOldInvitation() {
        String kira = characters.character(UUID.randomUUID(), "Kira Vale");
        realm.getInvited().add(kira);
        HeldRealms.hold(realm, ruler, AT);
        assertTrue(realm.getInvited().isEmpty());

        realm.getInvited().add(kira);
        Player kiraPlayer = fixture.player("kira");
        characters.play(kiraPlayer, kira);
        InviteCommands.handle(kiraPlayer, "faction", new String[] {"join", "realm"});
        verify(kiraPlayer).sendMessage("§c" + realm.getName() + " §chas no living members, so nobody can join it.");
        assertFalse(realm.isMember(kira));
    }

    @Test
    void menusShowTheDeadLeaderWhileTheRealmIsHeld() {
        Player viewer = fixture.player("viewer");
        assertEquals("§7Queen Ada (dead)", HeldRealms.leaderName(viewer, realm));
        String kira = characters.character(UUID.randomUUID(), "Kira Vale");
        realm.addMember(kira);
        realm.setLeader(kira);
        assertEquals(Members.display(viewer, kira), HeldRealms.leaderName(viewer, realm));
    }

    @Test
    void aRevivedLeaderWhoseSaveFailsAtTheDeadlineKeepsTheRealm() {
        characters.revive(ruler);
        failures = 1;
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertSame(realm, FactionManager.getByString("realm"));
        assertTrue(HeldRealms.isHeld(realm));
        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertEquals(ruler, realm.getLeader());
    }

    @Test
    void aRevivedLeaderGetsTheRealmBack() {
        Player ada = fixture.player("ada");
        characters.revive(ruler);
        characters.play(ada, ruler);
        HeldRealms.revived(ruler);
        assertEquals(ruler, realm.getLeader());
        assertTrue(main.isMember(ruler));
        assertFalse(HeldRealms.isHeld(realm));
        verify(ada).sendMessage("§aYou lead " + realm.getName() + " §aagain.");
    }

    @Test
    void theHourlyCheckReturnsTheRealmWhenTheReviveWasMissed() {
        characters.revive(ruler);
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertSame(realm, FactionManager.getByString("realm"));
        assertEquals(ruler, realm.getLeader());
    }

    @Test
    void theReviveEventReturnsTheRealm() {
        characters.revive(ruler);
        new LeaderCharacterListener().onRevived(
                new CharacterRevivedEvent(characters.info(ruler).orElseThrow().ref(), UUID.randomUUID()));
        assertEquals(ruler, realm.getLeader());
    }

    @Test
    void nobodyCanDeclareWarOnAHeldRealm() {
        Faction attacker = fixture.saved("attacker", characters.character(UUID.randomUUID(), "Bo Reed"));
        attacker.getOrCreateMainGuild();
        var result = new WarGoalValidator().validate(new WarDeclareRequest(attacker, realm, WarGoalType.WAR, null, null));
        assertFalse(result.isValid());
        assertEquals("§c" + realm.getName() + " §chas no living members and cannot be attacked.", result.getMessage());
    }

    @Test
    void aFailedSaveKeepsTheRealmHeld() {
        characters.revive(ruler);
        failures = 1;
        HeldRealms.revived(ruler);
        assertTrue(HeldRealms.isHeld(realm));
        assertEquals(ruler, main.getDeadLeader());
        assertEquals(AT, main.getLeaderlessSince());
        assertEquals(AT, main.getEmptySince());
        assertTrue(realm.getMembers().isEmpty());
        assertEquals("", realm.getLeader());

        failures = 2;
        HeldRealms.revived(ruler);
        assertTrue(HeldRealms.isHeld(realm));
        HeldRealms.revived(ruler);
        assertEquals(ruler, realm.getLeader());
    }

    @Test
    void aReviveDoesNotTakeBackARealmThatMovedOn() {
        HeldRealms.revived(ruler);
        assertTrue(HeldRealms.isHeld(realm), "still dead");

        characters.revive(ruler);
        Faction other = fixture.saved("other", characters.character(UUID.randomUUID(), "Bo Reed"));
        other.addMember(ruler);
        HeldRealms.revived(ruler);
        assertTrue(HeldRealms.isHeld(realm), "joined another realm since");

        other.getOrCreateMainGuild().getMembers().remove(ruler);
        String kira = characters.character(UUID.randomUUID(), "Kira Vale");
        realm.addMember(kira);
        HeldRealms.revived(kira);
        HeldRealms.revived(ruler);
        assertTrue(HeldRealms.isHeld(realm), "staff let someone in");
        assertFalse(realm.isMember(ruler));
    }
}
