package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;

/** A held realm passes to the heir its dead leader's will names (HeldRealms.inherit). */
class HeldRealmsHeirTest {
    private static final long AT = 1_000_000L;
    private static final long WAIT = 24L * 3_600_000L;
    private static final long WEEK = HeldRealms.HOLD.toMillis();
    @TempDir Path folder;
    private FactionDomainFixture fixture;
    private TestCharacters characters;
    private MockedConstruction<Database> databases;
    private final Map<String, String> wills = new HashMap<>();
    private boolean estateOpen = true;
    private int failures;
    private String ruler;
    private String heir;
    private Faction realm;
    private Guild main;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new FactionDomainFixture();
        characters = TestCharacters.install();
        databases = mockConstruction(Database.class,
                (database, context) -> when(database.saveFactionDurably(any())).thenAnswer(call -> failures-- <= 0));
        UUID egg = UUID.randomUUID();
        ruler = characters.character(egg, "Omeleth");
        heir = characters.character(egg, "Omeleth the Younger");
        realm = fixture.saved("realm", ruler);
        main = realm.getOrCreateMainGuild();
        characters.kill(ruler);
        main.removeDeadCharacter(ruler);
        realm.succeedDeadLeader(ruler, "");
        HeldRealms.hold(realm, ruler, AT);
        wills.put(ruler, heir);
        HeldRealms.heirs = wills::get;
        HeldRealms.estates = key -> estateOpen;
        Cache.heldRealmHeirWaitHours = 24;
        Files.createDirectories(folder.resolve("Data"));
        Files.writeString(folder.resolve("Data/realm.json"), "{}");
    }

    @AfterEach
    void tearDown() {
        HeldRealms.resetForTests();
        Cache.heldRealmHeirWaitHours = 24;
        databases.close();
        fixture.close();
    }

    @Test
    void theWillHeirTakesOnTheRealmOnceTheWaitIsOver() {
        Player younger = fixture.player("younger");
        characters.play(younger, heir);
        HeldRealms.closeExpired(AT + WAIT - 1, folder);
        assertTrue(HeldRealms.isHeld(realm), "a revive inside the wait still gives it back");

        HeldRealms.closeExpired(AT + WAIT, folder);
        assertEquals(heir, realm.getLeader());
        assertTrue(realm.isMember(heir));
        assertFalse(HeldRealms.isHeld(realm));
        assertNull(main.getDeadLeader());
        verify(younger).sendMessage("§aOmeleth left you " + realm.getName() + " §ain their will. You now lead it.");

        HeldRealms.closeExpired(AT + WEEK, folder);
        assertSame(realm, FactionManager.getByString("realm"), "a realm with a leader never closes");
    }

    @Test
    void aReviveInsideTheWaitGivesTheRealmBackFirst() {
        characters.revive(ruler);
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertEquals(ruler, realm.getLeader());
        assertFalse(realm.isMember(heir));
    }

    @Test
    void theHeirTakesItOnWhenTheyStartPlaying() {
        String stranger = characters.character(UUID.randomUUID(), "Bo Reed");
        HeldRealms.heirStarted(null, AT + WAIT);
        HeldRealms.heirStarted(heir, AT + WAIT - 1);
        HeldRealms.heirStarted(stranger, AT + WAIT);
        assertTrue(HeldRealms.isHeld(realm), "too early, or not the heir");

        HeldRealms.heirStarted(heir, AT + WAIT);
        assertEquals(heir, realm.getLeader());
    }

    @Test
    void anHeirInAnotherFactionIsToldToLeaveItFirst() {
        Faction other = fixture.saved("other", characters.character(UUID.randomUUID(), "Bo Reed"));
        other.addMember(heir);
        Player younger = fixture.player("younger");
        characters.play(younger, heir);
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertTrue(HeldRealms.isHeld(realm));
        verify(younger, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        HeldRealms.heirStarted(heir, AT + WAIT);
        verify(younger).sendMessage("§eOmeleth left you " + realm.getName() + " §ein their will. Leave "
                + other.getName() + " §ewithin 6 days to take it on.");

        main.restoreLeaderless(ruler, AT, 0, null);
        HeldRealms.heirStarted(heir, AT + WAIT);
        verify(younger).sendMessage("§eOmeleth left you " + realm.getName() + " §ein their will. Leave "
                + other.getName() + " §esoon to take it on.");

        main.restoreLeaderless(ruler, AT, AT, null);
        HeldRealms.heirStarted(heir, AT + WEEK - 1);
        verify(younger).sendMessage("§eOmeleth left you " + realm.getName() + " §ein their will. Leave "
                + other.getName() + " §ewithin 1 day to take it on.");

        other.getOrCreateMainGuild().getMembers().remove(heir);
        HeldRealms.closeExpired(AT + WEEK - 1, folder);
        assertEquals(heir, realm.getLeader());
    }

    @Test
    void noLivingHeirOrAStoppedEstateLeavesTheRealmToClose() {
        Player younger = fixture.player("younger");
        characters.play(younger, heir);
        estateOpen = false;
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertTrue(HeldRealms.isHeld(realm), "staff held or reverted the estate");

        estateOpen = true;
        characters.kill(heir);
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertTrue(HeldRealms.isHeld(realm), "the heir died too");

        wills.put(ruler, ruler);
        HeldRealms.closeExpired(AT + WAIT, folder);
        wills.put(ruler, "not-a-character");
        HeldRealms.closeExpired(AT + WAIT, folder);
        wills.remove(ruler);
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertTrue(HeldRealms.isHeld(realm), "no usable will");
        verify(younger, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());

        HeldRealms.closeExpired(AT + WEEK, folder);
        assertNull(FactionManager.getByString("realm"));
    }

    @Test
    void aRealmStaffLetSomeoneIntoOrThatIsClosingIsNotPassedOn() {
        String kira = characters.character(UUID.randomUUID(), "Kira Vale");
        realm.addMember(kira);
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertFalse(realm.isMember(heir), "staff let someone in");

        main.getMembers().remove(kira);
        main.markClosing(AT + WAIT);
        HeldRealms.heirStarted(heir, AT + WAIT);
        assertFalse(realm.isMember(heir), "its closure has begun");
    }

    @Test
    void aLeaderWhoseRecordsCannotBeReadKeepsTheRealmHeld() {
        String unknown = net.tfminecraft.simplefactions.identity.Members.key(UUID.randomUUID());
        main.restoreLeaderless(unknown, AT, AT, null);
        wills.put(unknown, heir);
        HeldRealms.heirStarted(heir, AT + WAIT);
        assertTrue(HeldRealms.isHeld(realm));
        main.restoreLeaderless(null, AT, AT, null);
        HeldRealms.heirStarted(heir, AT + WAIT);
        assertTrue(HeldRealms.isHeld(realm));
    }

    @Test
    void aFailedSavePutsTheHoldBackForTheNextHour() {
        failures = 1;
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertTrue(HeldRealms.isHeld(realm));
        assertEquals(ruler, main.getDeadLeader());
        assertEquals(AT, main.getLeaderlessSince());
        assertEquals(AT, main.getEmptySince());
        assertTrue(realm.getMembers().isEmpty());
        assertEquals("", realm.getLeader());

        failures = 2;
        HeldRealms.closeExpired(AT + WAIT, folder);
        assertTrue(HeldRealms.isHeld(realm), "the undo could not be saved either");
        HeldRealms.closeExpired(AT + WAIT + 1, folder);
        assertEquals(heir, realm.getLeader());
    }

    @Test
    void theWaitFollowsTheConfig() {
        Cache.heldRealmHeirWaitHours = 0.05;
        HeldRealms.closeExpired(AT + 180_000L, folder);
        assertEquals(heir, realm.getLeader());
    }

    @Test
    void anHeirWhoQualifiesAfterTheHeldWeekIsTooLate() {
        characters.kill(heir);
        HeldRealms.closeExpired(AT + WAIT, folder);
        characters.revive(heir);
        HeldRealms.heirStarted(heir, AT + WEEK);
        assertTrue(HeldRealms.isHeld(realm), "the week ran out");

        try (var wars = org.mockito.Mockito.mockStatic(net.tfminecraft.simplefactions.managers.WarManager.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            wars.when(() -> net.tfminecraft.simplefactions.managers.WarManager.isAtWar(realm)).thenReturn(true);
            HeldRealms.closeExpired(AT + WEEK + 1, folder);
            assertTrue(HeldRealms.isHeld(realm), "a war keeps it open, but not for the heir");
            assertFalse(realm.isMember(heir));
        }
        HeldRealms.closeExpired(AT + WEEK + 2, folder);
        assertNull(FactionManager.getByString("realm"));
    }
}
