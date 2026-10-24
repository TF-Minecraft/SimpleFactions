package net.tfminecraft.simplefactions.integration.rpcharacters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.rpcharacters.database.Database;
import net.tfminecraft.simplefactions.guild.TestCharacters;
import net.tfminecraft.simplefactions.identity.Members;

/** A dead character's will heir comes from its saved RPCharacters file, and anything unreadable is no heir. */
class WillHeirsTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID heir = UUID.randomUUID();
    private final PluginManager plugins = mock(PluginManager.class);
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<Database> database;
    private String dead;

    @BeforeEach
    void setUp() {
        dead = TestCharacters.install().character(owner, "Omeleth");
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
        database = mockStatic(Database.class);
    }

    @AfterEach
    void tearDown() {
        database.close();
        bukkit.close();
    }

    private void saved(Database.SavedCharacter... characters) {
        database.when(() -> Database.readSavedAccount(owner))
                .thenReturn(new Database.SavedAccount(owner, "{}", 0L, List.of(characters)));
    }

    private Database.SavedCharacter file(String key, String json) {
        return new Database.SavedCharacter(new File(owner.toString(), Members.characterId(key) + ".json"), json, 0L);
    }

    @Test
    void readsTheHeirFromTheDeadCharactersOwnFile() {
        String sibling = Members.key(UUID.randomUUID());
        saved(file(sibling, "{\"will-heir\":\"" + UUID.randomUUID() + "\"}"),
                file(dead, "{\"will-heir\":\"" + heir + "\"}"));
        assertEquals(Members.key(heir), WillHeirs.heir(dead));
    }

    @Test
    void noFileNoAccountOrNoPluginIsNoHeir() {
        saved(file(Members.key(UUID.randomUUID()), "{\"will-heir\":\"" + heir + "\"}"));
        assertNull(WillHeirs.heir(dead), "the character has no file");
        database.when(() -> Database.readSavedAccount(owner)).thenReturn(null);
        assertNull(WillHeirs.heir(dead), "the account cannot be read");
        database.when(() -> Database.readSavedAccount(owner)).thenThrow(new IllegalStateException("broken"));
        assertNull(WillHeirs.heir(dead));
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(false);
        assertNull(WillHeirs.heir(dead), "RPCharacters is not running");
        assertNull(WillHeirs.heir(Members.key(UUID.randomUUID())), "an unknown character has no account");
        assertNull(WillHeirs.heir("not-a-character"));
    }

    @Test
    void anythingButAnHeirIdIsNoHeir() {
        assertNull(WillHeirs.parse(null));
        assertNull(WillHeirs.parse("[]"));
        assertNull(WillHeirs.parse("{"));
        assertNull(WillHeirs.parse("{}"));
        assertNull(WillHeirs.parse("{\"will-heir\":null}"));
        assertNull(WillHeirs.parse("{\"will-heir\":{}}"));
        assertNull(WillHeirs.parse("{\"will-heir\":5}"));
        assertNull(WillHeirs.parse("{\"will-heir\":\"nobody\"}"));
        assertEquals(Members.key(heir), WillHeirs.parse("{\"will-heir\":\"" + heir + "\"}"));
    }
}
