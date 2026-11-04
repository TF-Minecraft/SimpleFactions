package net.tfminecraft.simplefactions.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.tlibs.character.CharacterRef;

class TaxExemptionsTest {
    @TempDir Path folder;
    private final Set<UUID> permitted = new HashSet<>();
    private final UUID owner = UUID.randomUUID();
    private final CharacterRef npc = new CharacterRef(owner, UUID.randomUUID());
    private final CharacterRef own = new CharacterRef(owner, UUID.randomUUID());

    @BeforeEach
    void setUp() {
        TaxExemptions.reset();
        TaxExemptions.bind(folder);
        TaxExemptions.load();
        TaxExemptions.setPermissionCheck(permitted::contains);
        permitted.add(owner);
    }

    @AfterEach
    void tearDown() {
        TaxExemptions.reset();
    }

    private static String key(CharacterRef ref) {
        return ref.characterId().toString().toLowerCase(Locale.ROOT);
    }

    @Test
    void onlyTheExemptCharacterOfAPermittedOwnerOwesNoTax() throws IOException {
        assertFalse(TaxExemptions.exempt(npc));
        assertTrue(TaxExemptions.set(key(npc), true));
        assertTrue(TaxExemptions.exempt(npc));
        assertFalse(TaxExemptions.exempt(own), "the account's other characters stay taxed");
        assertFalse(TaxExemptions.exempt(null));
        assertFalse(TaxExemptions.isSet(null));

        permitted.clear();
        assertFalse(TaxExemptions.exempt(npc), "no permission (offline, off duty) means taxed");
        assertTrue(TaxExemptions.isSet(key(npc)), "the setting survives losing the permission");
        permitted.add(owner);
        assertTrue(TaxExemptions.exempt(npc));

        assertTrue(TaxExemptions.set(key(npc), false));
        assertFalse(TaxExemptions.exempt(npc));
    }

    @Test
    void changesAreSavedAndReloaded() throws IOException {
        assertTrue(TaxExemptions.set(key(npc), true));
        assertFalse(TaxExemptions.set(key(npc), true), "already on");
        assertTrue(TaxExemptions.set(key(own), true));
        assertTrue(Files.exists(folder.resolve(TaxExemptions.FILE_NAME)));

        TaxExemptions.load();
        List<String> expected = new java.util.ArrayList<>(List.of(key(npc), key(own)));
        expected.sort(null);
        assertEquals(expected, TaxExemptions.keys());

        assertTrue(TaxExemptions.set(key(own), false));
        assertFalse(TaxExemptions.set(key(own), false), "already off");
        TaxExemptions.load();
        assertEquals(List.of(key(npc)), TaxExemptions.keys());
    }

    @Test
    void loadNormalisesIdsAndSkipsJunk() throws IOException {
        Files.writeString(folder.resolve(TaxExemptions.FILE_NAME),
                "{\"characters\":[\"" + npc.characterId().toString().toUpperCase(Locale.ROOT) + "\",\"not-an-id\",null]}");
        TaxExemptions.load();
        assertEquals(List.of(key(npc)), TaxExemptions.keys());
        assertTrue(TaxExemptions.exempt(npc));
    }

    @Test
    void anUnreadableFileExemptsNobodyAndIsNeverOverwritten() throws IOException {
        assertTrue(TaxExemptions.set(key(npc), true));
        Path file = folder.resolve(TaxExemptions.FILE_NAME);
        Files.writeString(file, "{broken");
        TaxExemptions.load();
        assertFalse(TaxExemptions.exempt(npc));
        assertTrue(TaxExemptions.keys().isEmpty());
        assertThrows(IOException.class, () -> TaxExemptions.set(key(own), true));
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void aFailedSaveChangesNothing() throws IOException {
        Path blocker = folder.resolve("blocker");
        Files.writeString(blocker, "a file where the folder should be");
        TaxExemptions.bind(blocker);
        TaxExemptions.load();
        assertThrows(IOException.class, () -> TaxExemptions.set(key(npc), true));
        assertFalse(TaxExemptions.isSet(key(npc)));
        assertFalse(TaxExemptions.exempt(npc));
    }

    @Test
    void anEmptyFileOrNoListExemptsNobody() throws IOException {
        Path file = folder.resolve(TaxExemptions.FILE_NAME);
        Files.writeString(file, "{\"characters\":null}");
        TaxExemptions.load();
        assertTrue(TaxExemptions.keys().isEmpty());
        assertTrue(TaxExemptions.set(key(npc), true), "a file without a list is still writable");

        Files.writeString(file, "");
        try (var plugin = org.mockito.Mockito.mockStatic(net.tfminecraft.simplefactions.SimpleFactions.class)) {
            var instance = org.mockito.Mockito.mock(net.tfminecraft.simplefactions.SimpleFactions.class);
            var logger = org.mockito.Mockito.mock(java.util.logging.Logger.class);
            org.mockito.Mockito.when(instance.getLogger()).thenReturn(logger);
            plugin.when(net.tfminecraft.simplefactions.SimpleFactions::getInstance).thenReturn(instance);
            TaxExemptions.load();
            org.mockito.Mockito.verify(logger).log(org.mockito.ArgumentMatchers.eq(java.util.logging.Level.SEVERE),
                    org.mockito.ArgumentMatchers.contains("no character is tax exempt"), org.mockito.ArgumentMatchers.any(IOException.class));
        }
        assertTrue(TaxExemptions.keys().isEmpty());
        assertThrows(IOException.class, () -> TaxExemptions.set(key(npc), true));
    }

    @Test
    void theOwnerMustBeOnlineWithThePermission() throws IOException {
        TaxExemptions.setPermissionCheck(null);
        assertTrue(TaxExemptions.set(key(npc), true));
        try (var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            assertFalse(TaxExemptions.exempt(npc), "offline owner");
            var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(owner)).thenReturn(player);
            assertFalse(TaxExemptions.exempt(npc), "online without the permission");
            org.mockito.Mockito.when(player.hasPermission(TaxExemptions.PERMISSION)).thenReturn(true);
            assertTrue(TaxExemptions.exempt(npc));
        }
    }

    @Test
    void unboundOrBlankIsRefused() {
        TaxExemptions.reset();
        assertThrows(IOException.class, () -> TaxExemptions.set(key(npc), true));
        assertThrows(IllegalArgumentException.class, () -> TaxExemptions.set(" ", true));
        assertThrows(IllegalArgumentException.class, () -> TaxExemptions.set(null, true));
    }
}
