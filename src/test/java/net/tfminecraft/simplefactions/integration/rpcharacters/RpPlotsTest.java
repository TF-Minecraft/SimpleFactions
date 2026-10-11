package net.tfminecraft.simplefactions.integration.rpcharacters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import net.tfminecraft.rpcharacters.plots.PlotsApi;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** SimpleFactions asks RPCharacters about hunts and Intrigue only when it can find the plot API. */
class RpPlotsTest {
    MockedStatic<Bukkit> bukkit;
    final PluginManager plugins = mock(PluginManager.class);
    final Player player = mock(Player.class);
    final String api = RpPlots.api;

    @BeforeEach
    void setup() {
        RpPlots.reset();
        PlotsApi.failure = null;
        PlotsApi.huntingAnswer = false;
        PlotsApi.intrigueAnswer = null;
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
    }

    @AfterEach
    void close() {
        bukkit.close();
        RpPlots.api = api;
        RpPlots.reset();
        PlotsApi.failure = null;
    }

    @Test
    void withoutRpCharactersNobodyHuntsAndThereIsNoIntrigue() {
        assertFalse(RpPlots.hunting(player));
        assertNull(RpPlots.intrigue(player));
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
        assertFalse(RpPlots.hunting(player), "enabled but not found");
        bukkit.when(Bukkit::getPluginManager).thenReturn(null);
        assertFalse(RpPlots.hunting(player));
        bukkit.when(Bukkit::getServer).thenReturn(null);
        assertNull(RpPlots.intrigue(player));
    }

    @Test
    void anOlderRpCharactersWithoutPlotsAnswersNothing() {
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
        when(plugins.getPlugin("RPCharacters")).thenReturn(mock(Plugin.class));
        RpPlots.api = "net.tfminecraft.rpcharacters.plots.Missing";
        assertFalse(RpPlots.hunting(player));
        assertNull(RpPlots.intrigue(player));
    }

    @Test
    void theRunningApiAnswersAndItsFailuresCountAsNo() {
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
        when(plugins.getPlugin("RPCharacters")).thenReturn(mock(Plugin.class));
        assertFalse(RpPlots.hunting(null));
        assertNull(RpPlots.intrigue(null));
        PlotsApi.huntingAnswer = true;
        PlotsApi.intrigueAnswer = 57;
        assertTrue(RpPlots.hunting(player));
        assertEquals(57, RpPlots.intrigue(player));
        PlotsApi.failure = new IllegalStateException("boom");
        assertFalse(RpPlots.hunting(player));
        assertNull(RpPlots.intrigue(player));
    }
}
