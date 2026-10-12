package net.tfminecraft.simplefactions.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.events.SimpleFactionsReadyEvent;

class SimpleFactionsApiTest {
    @Test
    void playerAndCharacterLookupsAreUnavailableUntilReadyAndUseDistinctCharacters() {
        SimpleFactions previous = SimpleFactions.plugin;
        var factions = new java.util.ArrayList<>(net.tfminecraft.simplefactions.managers.FactionManager.factions);
        try (var domain = new net.tfminecraft.simplefactions.testsupport.FactionDomainFixture()) {
            var faction = domain.saved("realm", "Alice");
            var guild = faction.getOrCreateMainGuild();
            var player = mock(org.bukkit.entity.Player.class);
            when(player.getName()).thenReturn("Alice");
            var character = net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.characterOf("Alice");
            var plugin = mock(SimpleFactions.class);
            SimpleFactions.plugin = plugin;
            assertTrue(SimpleFactionsApi.getByMember(player).isEmpty());
            assertTrue(SimpleFactionsApi.getByLeader(character).isEmpty());
            assertTrue(SimpleFactionsApi.getGuildByMember(player).isEmpty());
            assertTrue(SimpleFactionsApi.getGuildByLeader(character).isEmpty());
            when(plugin.isStarted()).thenReturn(true);
            assertSame(faction, SimpleFactionsApi.getByMember(player).orElseThrow());
            assertSame(faction, SimpleFactionsApi.getByMember(character).orElseThrow());
            assertSame(faction, SimpleFactionsApi.getByLeader(player).orElseThrow());
            assertSame(faction, SimpleFactionsApi.getByLeader(character).orElseThrow());
            assertSame(guild, SimpleFactionsApi.getGuildByMember(player).orElseThrow());
            assertSame(guild, SimpleFactionsApi.getGuildByMember(character).orElseThrow());
            assertSame(guild, SimpleFactionsApi.getGuildByLeader(player).orElseThrow());
            assertSame(guild, SimpleFactionsApi.getGuildByLeader(character).orElseThrow());
            assertTrue(SimpleFactionsApi.getGuildByMember(java.util.UUID.randomUUID()).isEmpty());
            var unknown = mock(org.bukkit.entity.Player.class);
            when(unknown.getName()).thenReturn("Nobody");
            assertTrue(SimpleFactionsApi.getByMember(unknown).isEmpty(), "ready, but in no faction");
            assertTrue(SimpleFactionsApi.getByMember(java.util.UUID.randomUUID()).isEmpty());
            assertNull(net.tfminecraft.simplefactions.managers.FactionManager.getByMember(unknown));
            assertNull(net.tfminecraft.simplefactions.managers.FactionManager.getByMember(java.util.UUID.randomUUID()));
            assertEquals("guild:" + guild.getIncarnation(), guild.getBank().accountId());
            assertEquals(java.util.UUID.fromString(guild.getIncarnation()), guild.getIncarnationId());
        } finally {
            SimpleFactions.plugin = previous;
            net.tfminecraft.simplefactions.managers.FactionManager.factions.clear();
            net.tfminecraft.simplefactions.managers.FactionManager.factions.addAll(factions);
        }
    }

    @Test
    void readyOnlyOnceThePluginHasStarted() {
        SimpleFactions previous = SimpleFactions.plugin;
        try (var fixture = new net.tfminecraft.simplefactions.testsupport.GuiTestFixture()) {
            SimpleFactions.plugin = null;
            assertFalse(SimpleFactionsApi.ready());
            SimpleFactions plugin = mock(SimpleFactions.class);
            SimpleFactions.plugin = plugin;
            assertFalse(SimpleFactionsApi.ready());
            when(plugin.isStarted()).thenReturn(true);
            assertTrue(SimpleFactionsApi.ready());
        } finally {
            SimpleFactions.plugin = previous;
        }
    }

    @Test
    void theReadyEventHasItsOwnHandlers() {
        SimpleFactionsReadyEvent event = new SimpleFactionsReadyEvent();
        assertNotNull(event.getHandlers());
        assertSame(SimpleFactionsReadyEvent.getHandlerList(), event.getHandlers());
    }
}
