package net.tfminecraft.simplefactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.identity.LeaderCharacters;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

class SimpleFactionsShutdownTest {
    @Test void failedStartupDoesNotSavePartiallyRestoredFactionsOrTimer() throws Exception {
        var plugin = mock(SimpleFactions.class, CALLS_REAL_METHODS);
        var database = mock(Database.class);
        var field = SimpleFactions.class.getDeclaredField("db");
        field.setAccessible(true); field.set(plugin, database);
        var previous = FactionManager.factions;
        var faction = mock(Faction.class);
        FactionManager.factions = new ArrayList<>(List.of(faction));
        try (var manager = mockStatic(FactionManager.class)) {
            plugin.saveLoadedFactions();
            verifyNoInteractions(database);
            manager.when(FactionManager::isLoaded).thenReturn(true);
            manager.when(FactionManager::getTimer).thenReturn(100);
            manager.when(FactionManager::getDay).thenReturn(4);
            plugin.saveLoadedFactions();
            verify(database).saveTimer(100, 4);
            verify(database).saveFaction(faction);
        } finally { FactionManager.factions = previous; }
    }

    @Test void shutdownSavesFactionsBeforeReleasingTheLeaderCharacterProbe() throws Exception {
        var plugin = mock(SimpleFactions.class, CALLS_REAL_METHODS);
        var database = mock(Database.class);
        var field = SimpleFactions.class.getDeclaredField("db");
        field.setAccessible(true); field.set(plugin, database);
        var previous = FactionManager.factions;
        var faction = mock(Faction.class);
        FactionManager.factions = new ArrayList<>(List.of(faction));
        var savedAs = new AtomicReference<String>();
        doAnswer(call -> {
            savedAs.set(LeaderCharacters.resolve("rushork", "Grunk the Bold", "rushork").name());
            return null;
        }).when(database).saveFaction(faction);
        LeaderCharacters.setProbe(player -> "Grunk the Second");
        try (var manager = mockStatic(FactionManager.class)) {
            manager.when(FactionManager::isLoaded).thenReturn(true);
            plugin.saveFactionsForShutdown();
            assertEquals("Grunk the Second", savedAs.get());
            assertNull(LeaderCharacters.resolve("rushork", "Grunk the Bold", "rushork").name());
        } finally {
            FactionManager.factions = previous;
            LeaderCharacters.reset();
        }
    }
}
