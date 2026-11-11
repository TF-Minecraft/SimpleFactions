package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.objects.Faction;

class InactiveSpymasterTest {
    private MockedStatic<InactivityService> inactivity;

    @BeforeEach void setUp() {
        inactivity = mockStatic(InactivityService.class);
        inactivity.when(() -> InactivityService.isMemberInactive(anyString())).thenReturn(true);
    }

    @AfterEach void tearDown() {
        inactivity.close();
    }

    private Faction faction(String spy, boolean leader) {
        var faction = mock(Faction.class);
        when(faction.getId()).thenReturn("realm");
        when(faction.getEspionage()).thenReturn(new EspionageState());
        var holder = new SpecialPositionAssignment();
        EspionageTestFixtures.bind(holder, spy);
        holder.playerId = UUID.randomUUID();
        when(faction.isMember(EspionageTestFixtures.key(spy))).thenReturn(true);
        when(faction.isLeader(EspionageTestFixtures.key(spy))).thenReturn(leader);
        faction.getEspionage().appoint(holder, 50);
        return faction;
    }

    @Test void anInactiveSpymasterNoLongerGuardsTheRealm() {
        assertFalse(EspionageService.hasSpymaster(faction("Spy", false)));
    }

    @Test void anInactiveLeaderKeepsTheirOwnSpymasterPost() {
        assertTrue(EspionageService.hasSpymaster(faction("Leader", true)));
    }

    @Test void anActiveSpymasterStillGuardsTheRealm() {
        inactivity.when(() -> InactivityService.isMemberInactive(anyString())).thenReturn(false);
        assertTrue(EspionageService.hasSpymaster(faction("Spy", false)));
    }
}
