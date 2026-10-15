package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
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

import net.tfminecraft.simplefactions.database.Database;
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
    private String ruler;
    private Faction realm;
    private Guild main;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new FactionDomainFixture();
        characters = TestCharacters.install();
        databases = mockConstruction(Database.class,
                (database, context) -> when(database.saveFactionDurably(any())).thenAnswer(call -> failures-- <= 0));
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
        assertFalse(HeldRealms.closing(null, AT + WEEK));
        assertFalse(HeldRealms.closing(UUID.randomUUID(), AT + WEEK));
        assertFalse(HeldRealms.closing(incarnation, AT + WEEK - 1));

        HeldRealms.closeExpired(AT + WEEK - 1, folder);
        assertSame(realm, FactionManager.getByString("realm"));
        assertTrue(HeldRealms.closing(incarnation, AT + WEEK));

        HeldRealms.closeExpired(AT + WEEK, folder);
        assertNull(FactionManager.getByString("realm"));
        assertFalse(Files.exists(folder.resolve("Data/realm.json")));
        assertFalse(survivor.getDiplomacyHandler().getRelations().containsKey("realm"));
        assertFalse(HeldRealms.closing(incarnation, AT + WEEK));
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
    void anUnfinishedClosureIsFinishedLaterAndHoldsEstatesMeanwhile() {
        Faction survivor = survivor();
        UUID incarnation = main.getIncarnationId();
        failures = 1;
        HeldRealms.closeExpired(AT + WEEK, folder);
        assertNull(FactionManager.getByString("realm"));
        assertTrue(HeldRealms.closing(incarnation, AT));

        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertFalse(HeldRealms.closing(incarnation, AT));
        assertFalse(survivor.getDiplomacyHandler().getRelations().containsKey("realm"));
    }

    @Test
    void aNewRealmThatTookTheNameIsLeftAlone() throws Exception {
        survivor();
        UUID incarnation = main.getIncarnationId();
        failures = 1;
        HeldRealms.closeExpired(AT + WEEK, folder);
        Faction newcomer = fixture.saved("realm", characters.character(UUID.randomUUID(), "Kira Vale"));
        newcomer.getOrCreateMainGuild();
        Files.writeString(folder.resolve("Data/realm.json"), "new");

        HeldRealms.closeExpired(AT + WEEK + 1, folder);
        assertSame(newcomer, FactionManager.getByString("realm"));
        assertEquals("new", Files.readString(folder.resolve("Data/realm.json")));
        assertFalse(HeldRealms.closing(incarnation, AT));
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
