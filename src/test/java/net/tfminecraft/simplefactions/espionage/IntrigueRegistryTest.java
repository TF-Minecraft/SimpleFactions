package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.managers.FactionManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Intrigue is the permanent Spymaster roll, shared with plots. */
class IntrigueRegistryTest {
    @TempDir Path directory;

    @Test
    void intrigueIsKnownOnlyOnceRolledAndNeverRerolled() throws Exception {
        Field field = EspionageService.class.getDeclaredField("characterAptitudes");
        field.setAccessible(true);
        Object previous = field.get(null);
        try (var factions = mockStatic(FactionManager.class); var characters = mockStatic(OfficeCharacters.class)) {
            field.set(null, null);
            assertNull(EspionageService.knownIntrigue("c1"));
            factions.when(FactionManager::getCopy).thenReturn(List.of());
            characters.when(() -> OfficeCharacters.attributes(any())).thenReturn(Map.of());
            EspionageService.loadAptitudes(directory.resolve("aptitudes.json"));
            assertNull(EspionageService.knownIntrigue("c1"));
            assertNull(EspionageService.knownIntrigue(null));
            int rolled = EspionageService.intrigue(mock(Player.class), "c1");
            assertEquals(rolled, EspionageService.knownIntrigue("c1"));
            assertEquals(rolled, EspionageService.intrigue(mock(Player.class), "c1"));
        } finally {
            field.set(null, previous);
        }
    }
}
