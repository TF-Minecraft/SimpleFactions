package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Random;
import java.util.Map;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class RosterAndLedgerPresentationTest {
    @Test void ownRosterExcludesSubjectsAndKeepsRealmBeforeWealthOrderedGuilds() {
        var viewer = mock(Player.class); var faction = mock(Faction.class, RETURNS_DEEP_STUBS);
        when(faction.getEspionage()).thenReturn(new EspionageState());
        var realm = mock(Guild.class); var rich = mock(Guild.class); var poor = mock(Guild.class);
        when(faction.getLeader()).thenReturn("LeaderAccount"); when(faction.getRulerTitle()).thenReturn("Elder");
        when(faction.getGuildHandler().getGuilds()).thenReturn(List.of(poor, rich, realm));
        when(realm.isBase()).thenReturn(true); when(realm.getName()).thenReturn("Main Realm");
        when(realm.getFaction()).thenReturn(faction);
        when(rich.getName()).thenReturn("Rich Guild"); when(rich.getWealth()).thenReturn(1000.0);
        when(poor.getName()).thenReturn("Poor Guild"); when(poor.getWealth()).thenReturn(100.0);
        try (var service = mockStatic(EspionageService.class); var names = mockStatic(CharacterNames.class)) {
            service.when(() -> EspionageService.canViewExact(viewer, faction)).thenReturn(true);
            names.when(() -> CharacterNames.display(viewer, "LeaderAccount")).thenReturn("Rowan");
            String text = String.join("\n", RosterLore.faction(viewer, faction));
            assertTrue(text.startsWith("#e8c55a\u00a7lElder: #f4e4aaRowan"));
            assertTrue(text.indexOf("Main Realm") < text.indexOf("Rich Guild"));
            assertTrue(text.indexOf("Rich Guild") < text.indexOf("Poor Guild"));
            assertFalse(text.contains("Capital"));
            verify(faction, never()).getCompleteMemberList();
            when(faction.getRulerTitle()).thenReturn(null);
            assertTrue(RosterLore.guild(viewer, realm).getFirst().startsWith("#e8c55a\u00a7lLeader:"));
        }
    }

    @Test void lowTierLedgerDoesNotListEveryUnknownCashflowAndKnownFlowsUseTheirSections() {
        var guild = mock(Guild.class); when(guild.getId()).thenReturn("g");
        var report = EspionageService.createReport(Map.of(), 0, new Random(1));
        var empty = IntelligenceLedger.summary(report, guild);
        assertEquals(3, empty.stream().filter(line -> line.contains("Unknown")).count());
        assertTrue(empty.size() < 15);
        report = EspionageService.createReport(Map.of(), 65, new Random(1));
        report.estimates.put("Guild:g:Cashflow:TRADE", new EspionageMath.Estimate(100, 120));
        report.estimates.put("Guild:g:Cashflow:MILITARY_UPKEEP", new EspionageMath.Estimate(-20, -10));
        report.estimates.put("Guild:g:Cashflow:GUILDS", new EspionageMath.Estimate(0, 2));
        report.estimates.put("Guild:g:Cashflow:VEHICLE_UPKEEP", new EspionageMath.Estimate(-2, 0));
        String text = String.join("\n", IntelligenceLedger.summary(report, guild));
        assertTrue(text.indexOf("Income\n") < text.indexOf("Trade:"));
        assertTrue(text.indexOf("Trade:") < text.indexOf("Expenses\n"));
        assertTrue(text.indexOf("Expenses\n") < text.indexOf("Military Upkeep:"));
        assertFalse(text.contains("Guilds:"));
        assertFalse(text.contains("Vehicle Upkeep:"));
        verify(guild, never()).getLedger();
    }
}
