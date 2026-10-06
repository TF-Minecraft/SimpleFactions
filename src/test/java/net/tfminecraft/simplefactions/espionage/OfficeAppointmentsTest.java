package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.objects.Faction;

class OfficeAppointmentsTest {
    @Test
    void newFactionWaitsForTheLeadersCharacterWithoutUsingTheFreeAppointment() {
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getLeader()).thenReturn("Founder");
        when(faction.isLeader("Founder")).thenReturn(true);
        when(faction.getMembers()).thenReturn(java.util.List.of("Founder"));
        var offline = mock(org.bukkit.OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
            bukkit.when(() -> org.bukkit.Bukkit.getOfflinePlayer("Founder")).thenReturn(offline);
            EspionageService.initializeFounder(faction);
            assertNull(state.getSpymaster());
            assertTrue(state.hasPendingFounder());
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
            verify(faction, never()).getBank();
            state.removeSpymaster();
            when(faction.getMembers()).thenReturn(java.util.List.of("Founder", "Member"));
            EspionageService.initializeFounder(faction);
            assertNull(state.getSpymaster());
            assertTrue(state.hasPendingFounder(), "The leader still takes the office once others have joined");
        }
    }

    @Test
    void vacantOfficeInALargerFactionFallsToTheLeadersCharacterAtFullAptitude() throws Exception {
        var faction = mock(Faction.class);
        var leader = mock(org.bukkit.entity.Player.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getLeader()).thenReturn("Leader");
        when(faction.isLeader("Leader")).thenReturn(true);
        when(faction.getMembers()).thenReturn(java.util.List.of("Leader", "Member", "Other"));
        when(leader.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        var registry = mock(CharacterAptitudes.class);
        when(registry.aptitude(eq("leader-character"), any())).thenReturn(72);
        var field = EspionageService.class.getDeclaredField("characterAptitudes");
        field.setAccessible(true);
        var previous = field.get(null);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class);
             var characters = mockStatic(OfficeCharacters.class);
             var databases = mockConstruction(net.tfminecraft.simplefactions.database.Database.class)) {
            field.set(null, registry);
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Leader")).thenReturn(leader);
            characters.when(() -> OfficeCharacters.activeCharacterId(leader)).thenReturn("leader-character");
            var holder = EspionageService.spymaster(faction);
            assertNotNull(holder);
            assertEquals("Leader", holder.playerName);
            assertEquals("leader-character", holder.characterId);
            assertTrue(holder.automatic);
            assertEquals(72, EspionageService.effectiveAptitude(faction, holder));
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER), "The default holding keeps the free appointment");
            assertFalse(state.hasPendingFounder());
            verify(databases.constructed().getFirst()).saveFaction(faction);
            verify(leader).sendMessage(contains("you hold the keys"));
        } finally { field.set(null, previous); }
    }

    private SpecialPositionAssignment assignment(String name) {
        var assignment = new SpecialPositionAssignment();
        assignment.playerName = name;
        assignment.characterId = name;
        return assignment;
    }

    @Test
    void appointmentsAreFreeAndOnlyReplacementsBringUnrest() {
        long day = 86_400_000L;
        var state = new EspionageState();
        state.assignFounder(SpecialPosition.SPYMASTER, assignment("Founder"), 80);
        assertTrue(state.getSpymaster().automatic);
        assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
        var faction = mock(Faction.class);
        when(faction.getEspionage()).thenReturn(state);
        assertEquals(0, EspionageService.nextAppointmentAt(faction), "A founder never delays the first appointment");
        assertTrue(EspionageService.completeAppointment(faction, assignment("First"), 90, 10 * day));
        assertTrue(state.unrestModifiers(10 * day).isEmpty());
        state.removeSpymaster();
        assertTrue(EspionageService.completeAppointment(faction, assignment("Second"), 65, 12 * day));
        assertEquals(-10, state.unrestModifiers(12 * day).getFirst().getModifier(), 0.001);
        state.removeSpymaster();
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
        assertEquals(2, restored.appointmentCount(SpecialPosition.SPYMASTER));
        assertEquals(-10, restored.unrestModifiers(12 * day).getFirst().getModifier(), 0.001);
        verify(faction, never()).getBank();
    }

    @Test
    void anotherAppointmentWaitsForTheCooldownEvenAfterRemoval() {
        long day = 86_400_000L;
        var state = new EspionageState();
        var first = assignment("First");
        var faction = mock(Faction.class);
        when(faction.getEspionage()).thenReturn(state);
        assertTrue(EspionageService.completeAppointment(faction, first, 82, 10 * day));
        assertEquals(12 * day, EspionageService.nextAppointmentAt(faction));
        assertFalse(EspionageService.completeAppointment(faction, assignment("Second"), 20, 12 * day - 1));
        assertSame(first, state.getSpymaster());
        assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
        assertTrue(state.unrestModifiers(12 * day - 1).isEmpty());
        state.removeSpymaster();
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
        when(faction.getEspionage()).thenReturn(restored);
        assertFalse(EspionageService.completeAppointment(faction, assignment("Second"), 20, 11 * day),
                "Removing the holder and restarting must not skip the wait");
        assertTrue(EspionageService.completeAppointment(faction, assignment("Second"), 20, 12 * day));
        assertEquals(14 * day, EspionageService.nextAppointmentAt(faction));
        restored.waiveAppointmentWait(SpecialPosition.SPYMASTER);
        assertEquals(0, EspionageService.nextAppointmentAt(faction));
    }

    @Test
    void newHolderBuildsUpToFullAptitudeOverTheConfiguredDays() {
        long day = 86_400_000L;
        var faction = mock(Faction.class);
        when(faction.isMemberIgnoreCase("Spy")).thenReturn(true);
        when(faction.getMembers()).thenReturn(java.util.List.of("Leader", "Spy"));
        var holder = assignment("Spy");
        new EspionageState().appoint(holder, 80, 10 * day);
        assertEquals(10 * day, holder.appointedAt);
        assertEquals(20, EspionageService.effectiveAptitude(faction, holder, 10 * day));
        assertEquals(50, EspionageService.effectiveAptitude(faction, holder, 10 * day + 7 * day / 2));
        assertEquals(80, EspionageService.effectiveAptitude(faction, holder, 17 * day));
        assertEquals(80, EspionageService.effectiveAptitude(faction, holder, 30 * day));
        assertEquals(20, EspionageService.effectiveAptitude(faction, holder, 9 * day), "Clock skew never drops below the start");
        assertEquals(7 * day / 2, EspionageService.buildUpRemaining(holder, 10 * day + 7 * day / 2));
        assertEquals(0, EspionageService.buildUpRemaining(holder, 17 * day));
        var founder = assignment("Spy");
        new EspionageState().assignFounder(SpecialPosition.SPYMASTER, founder, 80);
        assertEquals(80, EspionageService.effectiveAptitude(faction, founder, 10 * day), "Founders and older saves start established");
        when(faction.isLeader("Spy")).thenReturn(true);
        when(faction.getMembers()).thenReturn(java.util.List.of("Spy"));
        assertEquals(20, EspionageService.effectiveAptitude(faction, holder, 10 * day), "An appointed leader builds up like anyone else");
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            config.set("espionage.appointments.build-up-days", 0.0);
            EspionageConfig.load(config);
            assertEquals(80, EspionageService.effectiveAptitude(faction, holder, 10 * day));
            assertEquals(0, EspionageService.buildUpRemaining(holder, 10 * day));
            config.set("espionage.appointments.build-up-days", 7.0);
            config.set("espionage.appointments.starting-aptitude", 1.0);
            EspionageConfig.load(config);
            assertFalse(EspionageConfig.buildsUp());
            assertEquals(80, EspionageService.effectiveAptitude(faction, holder, 10 * day));
            assertEquals(0, EspionageService.buildUpRemaining(holder, 10 * day), "Full from the start leaves nothing to build");
        } finally { EspionageConfig.load(new org.bukkit.configuration.file.YamlConfiguration()); }
    }

    @Test
    void unrestFadesOverRealTimeAndExpiresEvenAcrossDowntime() {
        long week = 7 * 86_400_000L;
        var state = new EspionageState();
        state.addUnrest(SpecialPosition.SPYMASTER, 10, 7, 1000);
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
        assertEquals(-10, restored.unrestModifiers(1000).getFirst().getModifier());
        assertEquals(-5, restored.unrestModifiers(1000 + week / 2).getFirst().getModifier());
        assertTrue(restored.unrestModifiers(1000 + week).isEmpty());
        assertTrue(restored.unrestModifiers(1000 + week * 2).isEmpty());
    }

    @Test
    void existingSavedOfficeCountsAsAlreadyAppointedAndConfigRejectsInvalidValues() {
        var state = JsonUtil.GSON.fromJson("{\"positions\":{\"SPYMASTER\":{\"playerName\":\"OldSpy\",\"aptitude\":80}}}", EspionageState.class);
        assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
        state.removeSpymaster();
        assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
        assertEquals(0, state.lastAppointedAt(SpecialPosition.SPYMASTER));
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            config.set("espionage.appointments.stability-penalty", 12.0);
            config.set("espionage.appointments.penalty-days", 3.0);
            config.set("espionage.appointments.build-up-days", 3.5);
            config.set("espionage.appointments.starting-aptitude", 0.5);
            config.set("espionage.appointments.change-cooldown-days", 1.0);
            EspionageConfig.load(config);
            assertEquals(12, EspionageConfig.stabilityPenalty());
            assertEquals(3, EspionageConfig.penaltyDays());
            assertEquals(3.5, EspionageConfig.buildUpDays());
            assertEquals(0.5, EspionageConfig.startingAptitude());
            assertEquals(1, EspionageConfig.changeCooldownDays());
            config.set("espionage.appointments.stability-penalty", Double.NaN);
            config.set("espionage.appointments.penalty-days", 0.0);
            config.set("espionage.appointments.build-up-days", -1.0);
            config.set("espionage.appointments.starting-aptitude", 2.0);
            config.set("espionage.appointments.change-cooldown-days", Double.NaN);
            EspionageConfig.load(config);
            assertEquals(10, EspionageConfig.stabilityPenalty());
            assertEquals(0, EspionageConfig.penaltyDays());
            assertEquals(7, EspionageConfig.buildUpDays());
            assertEquals(0.25, EspionageConfig.startingAptitude());
            assertEquals(2, EspionageConfig.changeCooldownDays());
            config.set("espionage.appointments.change-cooldown-days", 0.0);
            EspionageConfig.load(config);
            var faction = mock(Faction.class);
            var waiting = new EspionageState();
            waiting.appoint(assignment("Spy"), 50, 1000);
            when(faction.getEspionage()).thenReturn(waiting);
            assertEquals(0, EspionageService.nextAppointmentAt(faction));
        } finally { EspionageConfig.load(new org.bukkit.configuration.file.YamlConfiguration()); }
    }
}
