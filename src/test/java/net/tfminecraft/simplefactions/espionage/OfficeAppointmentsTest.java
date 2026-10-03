package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

class OfficeAppointmentsTest {
    @Test
    void newSoloFactionAssignsFounderWithoutChargingOrUsingTheFreeAppointment() {
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
            assertEquals("Founder", state.getSpymaster().playerName);
            assertTrue(state.getSpymaster().automatic);
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
            verify(faction, never()).getBank();
            state.removeSpymaster();
            when(faction.getMembers()).thenReturn(java.util.List.of("Founder", "Member"));
            EspionageService.initializeFounder(faction);
            assertNull(state.getSpymaster());
        }
    }

    private SpecialPositionAssignment assignment(String name) {
        var assignment = new SpecialPositionAssignment();
        assignment.playerName = name;
        assignment.characterId = name;
        return assignment;
    }

    @Test
    void founderDoesNotConsumeFreeAppointmentAndRemovalCannotResetPaidHistory() {
        var state = new EspionageState();
        state.assignFounder(SpecialPosition.SPYMASTER, assignment("Founder"), 80);
        assertTrue(state.getSpymaster().automatic);
        assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
        var faction = mock(Faction.class);
        var bank = mock(Bank.class);
        var balance = new AtomicReference<>(1000.0);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getBank()).thenReturn(bank);
        when(bank.getWealth()).thenAnswer(ignored -> balance.get());
        doAnswer(call -> { balance.updateAndGet(value -> value - (Double) call.getArgument(0)); return null; }).when(bank).withdraw(anyDouble());
        assertTrue(EspionageService.completeAppointment(faction, assignment("First"), 90));
        assertEquals(1000.0, balance.get());
        assertTrue(state.unrestModifiers(System.currentTimeMillis()).isEmpty());
        state.removeSpymaster();
        assertEquals(250.0, EspionageService.appointmentCost(faction));
        assertTrue(EspionageService.completeAppointment(faction, assignment("Second"), 65));
        assertEquals(750.0, balance.get());
        assertEquals(-10, state.unrestModifiers(System.currentTimeMillis()).getFirst().getModifier(), 0.001);
        state.removeSpymaster();
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
        assertEquals(2, restored.appointmentCount(SpecialPosition.SPYMASTER));
        assertEquals(-10, restored.unrestModifiers(System.currentTimeMillis()).getFirst().getModifier(), 0.001);
        verify(bank, times(1)).withdraw(250.0);
    }

    @Test
    void unaffordableAppointmentDoesNotChangeOfficeBalanceHistoryOrStability() {
        var state = new EspionageState();
        var first = assignment("First");
        state.appoint(first, 82);
        var faction = mock(Faction.class);
        var bank = mock(Bank.class);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getBank()).thenReturn(bank);
        when(bank.getWealth()).thenReturn(249.0);
        assertFalse(EspionageService.completeAppointment(faction, assignment("Second"), 20));
        assertSame(first, state.getSpymaster());
        assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
        assertTrue(state.unrestModifiers(System.currentTimeMillis()).isEmpty());
        verify(bank, never()).withdraw(anyDouble());
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
    void existingSavedOfficeCountsAsAlreadyAppointedAndConfigRejectsInvalidCosts() {
        var state = JsonUtil.GSON.fromJson("{\"positions\":{\"SPYMASTER\":{\"playerName\":\"OldSpy\",\"aptitude\":80}}}", EspionageState.class);
        assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
        state.removeSpymaster();
        assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            config.set("espionage.appointments.repeat-cost", 500.0);
            config.set("espionage.appointments.stability-penalty", 12.0);
            config.set("espionage.appointments.penalty-days", 3.0);
            EspionageConfig.load(config);
            assertEquals(500, EspionageConfig.repeatCost());
            assertEquals(12, EspionageConfig.stabilityPenalty());
            assertEquals(3, EspionageConfig.penaltyDays());
            config.set("espionage.appointments.repeat-cost", -1.0);
            config.set("espionage.appointments.stability-penalty", Double.NaN);
            config.set("espionage.appointments.penalty-days", 0.0);
            EspionageConfig.load(config);
            assertEquals(250, EspionageConfig.repeatCost());
            assertEquals(10, EspionageConfig.stabilityPenalty());
            assertEquals(0, EspionageConfig.penaltyDays());
        } finally { EspionageConfig.load(new org.bukkit.configuration.file.YamlConfiguration()); }
    }
}
