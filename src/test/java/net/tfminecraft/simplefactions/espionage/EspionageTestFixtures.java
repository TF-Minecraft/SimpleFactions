package net.tfminecraft.simplefactions.espionage;

import static org.mockito.Mockito.when;
import net.tfminecraft.simplefactions.objects.Faction;

final class EspionageTestFixtures {
    private EspionageTestFixtures() {}

    static void protect(Faction faction) {
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Spy";
        state.appoint(holder, 50);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.isMemberIgnoreCase("Spy")).thenReturn(true);
    }
}
