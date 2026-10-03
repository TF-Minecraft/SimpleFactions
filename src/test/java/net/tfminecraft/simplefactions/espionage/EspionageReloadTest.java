package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Random;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class EspionageReloadTest {
    @Test void unauthorizedCommandCannotReloadConfigOrResetReports() {
        var sender = mock(CommandSender.class);
        try (var files = mockStatic(SpecialPositionsConfigFile.class); var service = mockStatic(EspionageService.class)) {
            assertTrue(EspionageCommands.reload(sender, new String[]{"reloadespionage"}));
            verify(sender).hasPermission(EspionageConfig.reloadPermission());
            verify(sender).sendMessage(contains("permission"));
            files.verifyNoInteractions();
            service.verifyNoInteractions();
        }
    }

    @Test void staffCommandReloadsSettingsBeforeRegeneratingAndSupportsConsole() {
        var sender = mock(CommandSender.class);
        when(sender.hasPermission(EspionageConfig.reloadPermission())).thenReturn(true);
        try (var files = mockStatic(SpecialPositionsConfigFile.class); var service = mockStatic(EspionageService.class)) {
            service.when(EspionageService::regenerateReports).thenReturn(12);
            assertTrue(EspionageCommands.reload(sender, new String[]{"reloadespionage"}));
            files.verify(() -> SpecialPositionsConfigFile.load(net.tfminecraft.simplefactions.SimpleFactions.plugin));
            service.verify(EspionageService::regenerateReports);
            verify(sender).sendMessage(contains("12 intelligence reports regenerated"));
        }
    }

    @Test void resetOnlyReplacesDailyReportsAndRollsPreservingOfficeHistoryAndUnrest() {
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.characterId = "character";
        state.appoint(holder, 88);
        holder.offenseReduction = 50;
        state.addUnrest(SpecialPosition.SPYMASTER, 10, 7, 1000);
        var report = state.report("target", 100, 1, () -> EspionageService.createReport(Map.of("Wealth", 100.0), 100, new Random(1)));
        var rolls = state.rolls(1, new Random(1));
        state.resetReportsAndRolls();
        assertNull(state.cachedReport("target", 100, 1));
        assertNotSame(report, state.report("target", 100, 1, IntelligenceReport::new));
        assertNotSame(rolls, state.rolls(1, new Random(2)));
        assertSame(holder, state.getSpymaster());
        assertEquals(88, holder.aptitude);
        assertEquals(50, holder.offenseReduction);
        assertEquals(1, state.appointmentCount(SpecialPosition.SPYMASTER));
        assertFalse(state.unrestModifiers(1000).isEmpty());
    }
}
