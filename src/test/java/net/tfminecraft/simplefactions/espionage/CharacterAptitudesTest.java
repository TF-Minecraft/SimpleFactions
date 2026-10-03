package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

class CharacterAptitudesTest {
    @TempDir Path directory;

    @Test
    void characterKeepsAptitudeAcrossFactionsDeletionAndServerRestart() throws Exception {
        Path file = directory.resolve("character-aptitudes.json");
        var registry = new CharacterAptitudes(file);
        assertEquals(88, registry.aptitude("character-a", () -> 88));
        Faction first = mock(Faction.class), second = mock(Faction.class);
        when(first.getEspionage()).thenReturn(new EspionageState());
        when(second.getEspionage()).thenReturn(new EspionageState());
        var assignment = new SpecialPositionAssignment();
        assignment.characterId = "character-a";
        first.getEspionage().appoint(assignment, registry.aptitude("character-a", () -> fail("Reroll")));
        first.getEspionage().removeSpymaster();
        var transferred = new SpecialPositionAssignment();
        transferred.characterId = "character-a";
        second.getEspionage().appoint(transferred, registry.aptitude("character-a", () -> fail("Faction swap reroll")));
        assertEquals(88, transferred.aptitude);
        var restarted = new CharacterAptitudes(file);
        restarted.load();
        assertEquals(88, restarted.aptitude("character-a", () -> fail("Restart reroll")));
        assertEquals(7, restarted.aptitude("character-b", () -> 7));
    }

    @Test
    void firstDevRollMigratesAndSurvivesAfterItsFactionDisappears() throws Exception {
        Faction old = mock(Faction.class);
        when(old.getId()).thenReturn("old");
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.characterId = "legacy-character";
        holder.playerId = UUID.randomUUID();
        state.appoint(holder, () -> 41);
        state.removeSpymaster();
        when(old.getEspionage()).thenReturn(state);
        Path file = directory.resolve("migrated.json");
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(FactionManager::getCopy).thenReturn(List.of(old));
            EspionageService.loadAptitudes(file);
            factions.when(FactionManager::getCopy).thenReturn(List.of());
            EspionageService.loadAptitudes(file);
        }
        var registry = new CharacterAptitudes(file);
        registry.load();
        assertEquals(41, registry.aptitude("legacy-character", () -> fail("Migration reroll")));
    }
}
