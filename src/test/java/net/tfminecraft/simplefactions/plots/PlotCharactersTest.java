package net.tfminecraft.simplefactions.plots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.tfminecraft.rpcharacters.enums.Status;
import net.tfminecraft.rpcharacters.identity.DisplayIdentityService;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.permadeath.PermadeathService;
import net.tfminecraft.rpcharacters.permadeath.PermakillCause;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class PlotCharactersTest {
    @TempDir Path directory;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerManager> players;
    private final PluginManager plugins = mock(PluginManager.class);
    private final Player player = mock(Player.class);
    private final UUID id = UUID.randomUUID();
    private final PlayerData data = mock(PlayerData.class);
    private final RPCharacter character = mock(RPCharacter.class);

    @BeforeEach
    void setup() {
        bukkit = mockStatic(Bukkit.class);
        players = mockStatic(PlayerManager.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
        Plugin rp = mock(Plugin.class);
        when(rp.getDataFolder()).thenReturn(directory.toFile());
        when(plugins.getPlugin("RPCharacters")).thenReturn(rp);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Player");
        players.when(() -> PlayerManager.get(player)).thenReturn(data);
        players.when(() -> PlayerManager.get(id)).thenReturn(data);
        when(data.getActiveCharacter()).thenReturn(character);
        when(data.getCharacters()).thenReturn(List.of(character));
        when(character.getId()).thenReturn("c1");
        when(character.getName()).thenReturn("Ana");
        when(character.getStatus()).thenReturn(Status.ALIVE);
    }

    @AfterEach
    void close() {
        players.close();
        bukkit.close();
    }

    @Test
    void enabledNeedsTheServerAndRpCharacters() {
        assertTrue(PlotCharacters.enabled());
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(false);
        assertFalse(PlotCharacters.enabled());
        assertNull(PlotCharacters.active(player));
        assertFalse(PlotCharacters.dead(new PlotCharacter("c1", id, "Player", "Ana")));
        assertNull(PlotCharacters.offline(mock(OfflinePlayer.class)));
        assertFalse(PlotCharacters.permakill(player, "c1", null));
        assertEquals("Ana", PlotCharacters.seenAs(player, new PlotCharacter("c1", id, "Player", "Ana")));
        bukkit.when(Bukkit::getPluginManager).thenReturn(null);
        assertFalse(PlotCharacters.enabled());
        bukkit.when(Bukkit::getServer).thenReturn(null);
        assertFalse(PlotCharacters.enabled());
    }

    @Test
    void theActiveLivingCharacterIsThePlotCharacter() {
        PlotCharacter active = PlotCharacters.active(player);
        assertEquals("c1", active.characterId);
        assertEquals("Ana", active.name);
        assertEquals(id, active.playerId);
        assertNull(PlotCharacters.active(null));
        assertTrue(PlotCharacters.plays(player, active));
        assertFalse(PlotCharacters.plays(player, null));
        assertFalse(PlotCharacters.plays(player, new PlotCharacter("c2", id, "Player", "B")));
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        assertSame(player, PlotCharacters.online(active));
        assertNull(PlotCharacters.online(new PlotCharacter("c2", id, "Player", "B")));
        assertNull(PlotCharacters.online(null));
        assertNull(PlotCharacters.online(new PlotCharacter("c1", null, "Player", "Ana")));
        when(character.getStatus()).thenReturn(Status.DEAD);
        assertNull(PlotCharacters.active(player));
        assertTrue(PlotCharacters.dead(active));
        assertFalse(PlotCharacters.dead(new PlotCharacter("c9", id, "Player", "X")));
        assertFalse(PlotCharacters.dead(new PlotCharacter("c1", null, "Player", "Ana")));
        assertFalse(PlotCharacters.dead(null));
        players.when(() -> PlayerManager.get(id)).thenReturn(null);
        assertFalse(PlotCharacters.dead(active));
        when(data.getActiveCharacter()).thenReturn(null);
        assertNull(PlotCharacters.active(player));
        players.when(() -> PlayerManager.get(player)).thenReturn(null);
        assertNull(PlotCharacters.active(player));
    }

    @Test
    void masksAndAliasesHideTheCharacterName() {
        PlotCharacter ana = new PlotCharacter("c1", id, "Player", "Ana");
        try (var identity = mockStatic(DisplayIdentityService.class)) {
            identity.when(() -> DisplayIdentityService.resolveDisplay(player)).thenReturn("§7Masked figure");
            assertEquals("Masked figure", PlotCharacters.seenAs(player, ana));
            identity.when(() -> DisplayIdentityService.resolveDisplay(player)).thenReturn(" ");
            assertEquals("Ana", PlotCharacters.seenAs(player, ana));
            identity.when(() -> DisplayIdentityService.resolveDisplay(player)).thenReturn(null);
            assertEquals("Ana", PlotCharacters.seenAs(player, ana));
        }
    }

    @Test
    void onlyTheStillActiveCharacterIsKilled() {
        try (var deaths = mockStatic(PermadeathService.class)) {
            Player killer = mock(Player.class);
            deaths.when(() -> PermadeathService.killCharacter(player, character, PermakillCause.OTHER, killer)).thenReturn(true);
            assertTrue(PlotCharacters.permakill(player, "c1", killer));
            assertFalse(PlotCharacters.permakill(player, "c2", killer));
            when(data.getActiveCharacter()).thenReturn(null);
            assertFalse(PlotCharacters.permakill(player, "c1", killer));
            players.when(() -> PlayerManager.get(player)).thenReturn(null);
            assertFalse(PlotCharacters.permakill(player, "c1", killer));
        }
    }

    @Test
    void offlineAccountsAreReadFromTheirSavedActiveCharacter() throws Exception {
        OfflinePlayer account = mock(OfflinePlayer.class);
        when(account.getUniqueId()).thenReturn(id);
        when(account.getName()).thenReturn("Player");
        assertNull(PlotCharacters.offline(null));
        assertNull(PlotCharacters.offline(account));
        Path folder = directory.resolve("data/characterdata").resolve(id.toString());
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("a.json"), "{broken");
        Files.writeString(folder.resolve("b.json"), "{\"id\":\"old\",\"name\":\"Old\",\"active\":\"false\",\"status\":\"ALIVE\"}");
        Files.writeString(folder.resolve("c.json"), "{\"id\":\"dead\",\"name\":\"Dead\",\"active\":\"true\",\"status\":\"DEAD\"}");
        Files.writeString(folder.resolve("d.json"), "{\"id\":\" \",\"name\":{},\"active\":\"true\"}");
        Files.writeString(folder.resolve("e.txt"), "ignored");
        assertNull(PlotCharacters.offline(account));
        Files.writeString(folder.resolve("f.json"), "{\"id\":\"live\",\"name\":\"Live\",\"active\":\"true\",\"status\":\"ALIVE\"}");
        PlotCharacter live = PlotCharacters.offline(account);
        assertEquals("live", live.characterId);
        assertEquals("Live", live.name);
        assertEquals("Player", live.playerName);
        try (var files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.list(any())).thenThrow(new java.io.IOException("locked"));
            assertNull(PlotCharacters.offline(account));
        }
        Files.writeString(folder.resolve("g.json"), "{\"id\":\"no-name\",\"active\":\"true\"}");
    }
}
