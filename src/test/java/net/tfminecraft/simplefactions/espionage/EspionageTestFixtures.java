package net.tfminecraft.simplefactions.espionage;

import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;

final class EspionageTestFixtures {
    private EspionageTestFixtures() {}

    /** A stable character ID for a test name; offices match members by this key. */
    static UUID characterOf(String name) {
        return UUID.nameUUIDFromBytes(("character-" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** The member key of {@link #characterOf(String)}. */
    static String key(String name) {
        return Members.key(characterOf(name));
    }

    /** Binds the holder to the named test character. */
    static SpecialPositionAssignment bind(SpecialPositionAssignment holder, String name) {
        holder.playerName = name;
        holder.characterId = characterOf(name).toString();
        return holder;
    }

    static void protect(Faction faction) {
        var state = new EspionageState();
        var holder = bind(new SpecialPositionAssignment(), "Spy");
        state.appoint(holder, 50);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.isMember(key("Spy"))).thenReturn(true);
    }
}
