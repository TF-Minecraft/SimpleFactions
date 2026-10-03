package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.tfminecraft.rpcharacters.enums.Status;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.permadeath.CharacterPermakillEvent;
import net.tfminecraft.rpcharacters.permadeath.PermakillCause;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

class OfficeVacancyTest {
    @AfterEach void defaults() { EspionageConfig.load(new YamlConfiguration()); }

    @Test void vacancyPersistsUntilFilledAndReturnsOnRemoval() {
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        for (long now : new long[]{0, 365L * 86_400_000}) {
            var penalties = EspionageService.stabilityModifiers(faction, now);
            assertEquals(1, penalties.size());
            assertEquals(-10, penalties.getFirst().getModifier());
            assertEquals("Vacant Spymaster", penalties.getFirst().getName());
        }
        var holder = new SpecialPositionAssignment(); holder.playerName = "Spy";
        when(faction.isMemberIgnoreCase("Spy")).thenReturn(true);
        state.appoint(holder, 80);
        assertTrue(EspionageService.stabilityModifiers(faction, 0).isEmpty());
        state.removeSpymaster();
        assertEquals(-10, EspionageService.stabilityModifiers(faction, Long.MAX_VALUE).getFirst().getModifier());
        var config = new YamlConfiguration();
        config.set("positions.spymaster.vacancy-stability-penalty", 0);
        EspionageConfig.load(config);
        assertTrue(EspionageService.stabilityModifiers(faction, 0).isEmpty());
        config.set("positions.spymaster.vacancy-stability-penalty", 15);
        EspionageConfig.load(config);
        assertEquals(-15, EspionageService.stabilityModifiers(faction, 0).getFirst().getModifier());
    }

    @Test void ineligibleHolderCountsAsVacantAndReplacementUnrestRemainsIndependent() {
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        var holder = new SpecialPositionAssignment(); holder.playerName = "FormerSpy";
        state.appoint(holder, 80);
        state.addUnrest(SpecialPosition.SPYMASTER, 10, 7, 1000);
        assertEquals(2, EspionageService.stabilityModifiers(faction, 1000).size());
        assertEquals(1, EspionageService.stabilityModifiers(faction, 1000 + 8L * 86_400_000).size());
        when(faction.isMemberIgnoreCase("FormerSpy")).thenReturn(true);
        assertTrue(EspionageService.stabilityModifiers(faction, 1000 + 8L * 86_400_000).isEmpty());
    }

    @Test void confirmedDeathRevokesOnlyTheAppointedCharacterAndKeepsAppointmentHistory() {
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        var holder = new SpecialPositionAssignment(); holder.playerName = "Spy"; holder.characterId = "appointed";
        state.appoint(holder, 80);
        var character = mock(RPCharacter.class);
        when(character.getId()).thenReturn("other");
        when(character.getStatus()).thenReturn(Status.DEAD);
        try (var factions = mockStatic(FactionManager.class); var databases = mockConstruction(Database.class)) {
            factions.when(FactionManager::getCopy).thenReturn(List.of(faction));
            EspionageService.characterDied(null, character.getId(), "Spy");
            assertSame(holder, state.getSpymaster());
            when(character.getId()).thenReturn("appointed");
            EspionageService.characterDied(null, character.getId(), "Spy");
            assertNull(state.getSpymaster());
            assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
            verify(databases.constructed().getFirst()).saveFaction(faction);
        }
    }

    @Test void deathListenerIgnoresCancellationAndWaitsForConfirmedCharacterStatus() {
        var character = mock(RPCharacter.class); var owner = mock(Player.class);
        var event = new CharacterPermakillEvent(owner, character, PermakillCause.OTHER);
        event.setCancelled(true);
        var plugin = mock(SimpleFactions.class, RETURNS_DEEP_STUBS);
        var previous = SimpleFactions.plugin; SimpleFactions.plugin = plugin;
        var task = new AtomicReference<Runnable>();
        try (var service = mockStatic(EspionageService.class)) {
            var listener = new OfficeCharacterDeathListener();
            listener.onCharacterDeath(event);
            verify(plugin, never()).getServer();
            event.setCancelled(false);
            when(plugin.getServer().getScheduler().runTask(eq(plugin), any(Runnable.class)))
                    .thenAnswer(call -> { task.set(call.getArgument(1)); return null; });
            listener.onCharacterDeath(event);
            service.verifyNoInteractions();
            when(character.getStatus()).thenReturn(Status.ALIVE);
            task.get().run();
            service.verifyNoInteractions();
            when(character.getStatus()).thenReturn(Status.DEAD);
            when(character.getId()).thenReturn("appointed");
            when(character.getName()).thenReturn("Spy");
            task.get().run();
            service.verify(() -> EspionageService.characterDied(owner, "appointed", "Spy"));
        } finally { SimpleFactions.plugin = previous; }
    }
}
