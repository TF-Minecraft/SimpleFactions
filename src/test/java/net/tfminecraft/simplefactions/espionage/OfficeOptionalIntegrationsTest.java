package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URLClassLoader;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.objects.Faction;

class OfficeOptionalIntegrationsTest {
    @Test void roleplayAttributesRemainAvailableWithoutMmocore() {
        var plugins = mock(PluginManager.class);
        when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
        var player = mock(Player.class);
        var data = mock(net.tfminecraft.rpcharacters.objects.PlayerData.class);
        var character = mock(net.tfminecraft.rpcharacters.objects.RPCharacter.class);
        var attributes = mock(net.tfminecraft.rpcharacters.objects.attributes.AttributeData.class);
        when(data.getActiveCharacter()).thenReturn(character);
        when(character.getId()).thenReturn("active-character");
        when(character.getAttributeData()).thenReturn(attributes);
        when(attributes.getAmount(any(net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier.class))).thenReturn(12);
        try (var bukkit = mockStatic(Bukkit.class);
             var players = mockStatic(net.tfminecraft.rpcharacters.managers.PlayerManager.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            players.when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(player)).thenReturn(data);
            assertEquals("active-character", OfficeCharacters.activeCharacterId(player));
            var values = OfficeCharacters.attributes(player);
            assertEquals(EspionageConfig.DEFAULT_WEIGHTS.keySet(), values.keySet());
            assertTrue(values.values().stream().allMatch(value -> value == 12));
            verify(plugins).isPluginEnabled("MMOCore");
        }
    }

    @Test void absentRoleplayLeavesFounderPendingAndDoesNotRevokeLoadedHolder() {
        var plugins = mock(PluginManager.class);
        var founder = mock(Player.class);
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getLeader()).thenReturn("Founder");
        when(faction.isLeader("Founder")).thenReturn(true);
        when(faction.getMembers()).thenReturn(List.of("Founder"));
        when(founder.getUniqueId()).thenReturn(UUID.randomUUID());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(() -> Bukkit.getPlayerExact("Founder")).thenReturn(founder);
            EspionageService.initializeFounder(faction);
            assertTrue(state.hasPendingFounder());
            assertNull(state.getSpymaster());
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
            var holder = new SpecialPositionAssignment();
            holder.playerId = founder.getUniqueId(); holder.characterId = "founder"; holder.playerName = "Founder";
            state.assignFounder(SpecialPosition.SPYMASTER, holder, 80);
            assertSame(holder, EspionageService.spymaster(faction));
            assertTrue(EspionageService.stabilityModifiers(faction, 0).isEmpty());
        }
    }

    @Test void officeServiceLoadsAndReflectsWithoutOptionalPluginClasses() throws Exception {
        var classes = EspionageService.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new java.net.URL[]{classes}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.tfminecraft.rpcharacters.") || name.startsWith("net.Indyuce.mmocore."))
                    throw new ClassNotFoundException("Optional plugin absent: " + name);
                if (name.startsWith("net.tfminecraft.simplefactions.espionage.")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            var service = Class.forName(EspionageService.class.getName(), true, loader);
            assertDoesNotThrow(service::getDeclaredMethods);
            var bridge = Class.forName(OfficeCharacters.class.getName(), true, loader);
            var active = bridge.getDeclaredMethod("activeCharacterId", Player.class);
            active.setAccessible(true);
            assertNull(active.invoke(null, new Object[]{null}));
        }
    }
}
