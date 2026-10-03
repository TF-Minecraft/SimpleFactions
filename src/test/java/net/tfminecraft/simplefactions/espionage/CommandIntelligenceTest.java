package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;

class CommandIntelligenceTest {
    @Test
    void everyFactionAndGuildMenuConstructorGathersOnceWithinACommand() {
        Player player = mock(Player.class);
        try (var service = mockStatic(EspionageService.class)) {
            for (SFGUI type : new SFGUI[]{SFGUI.FACTION_LIST, SFGUI.FACTION_VIEW, SFGUI.GUILD_LIST,
                    SFGUI.GUILD_VIEW, SFGUI.SPECIAL_POSITIONS, SFGUI.SPYMASTER_SETTINGS}) {
                assertTrue(CommandIntelligence.execute(player, () -> {
                    new SFInventoryHolder("id", type);
                    new SFInventoryHolder("id", type, 1);
                    new SFInventoryHolder("id", type, 1, true);
                    new SFInventoryHolder("id", type, "other");
                    new SFInventoryHolder("id", type, 1, true, "other");
                    return true;
                }));
            }
            service.verify(() -> EspionageService.refreshReports(player), times(6));
            new SFInventoryHolder("id", SFGUI.FACTION_VIEW);
            service.verifyNoMoreInteractions();
        }
    }

    @Test
    void commandsWithoutGuiAndClicksCannotGatherAndExceptionClearsContext() {
        Player player = mock(Player.class);
        try (var service = mockStatic(EspionageService.class)) {
            assertTrue(CommandIntelligence.execute(player, () -> true));
            assertThrows(IllegalStateException.class, () -> CommandIntelligence.execute(player, () -> {
                throw new IllegalStateException("Denied command");
            }));
            new SFInventoryHolder("id", SFGUI.FACTION_VIEW);
            assertTrue(CommandIntelligence.execute(null, () -> {
                new SFInventoryHolder("id", SFGUI.FACTION_LIST);
                return true;
            }));
            service.verifyNoInteractions();
        }
    }
}
