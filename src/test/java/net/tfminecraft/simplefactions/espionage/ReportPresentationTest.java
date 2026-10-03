package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class ReportPresentationTest {
    @AfterEach void defaults() { EspionageConfig.load(new YamlConfiguration()); }

    @Test void missingReportsAreBotchedAndColouredNamesProduceCleanWindowTitles() {
        assertEquals("Botched report", new IntelligenceReport().qualityLabel());
        assertFalse(new IntelligenceReport().allows("members"));
        assertEquals("\u00a77Ledger for Holy Order", net.tfminecraft.simplefactions.managers.inventory.MenuTitles.legacy("Ledger for \u00a7x\u00a71\u00a72\u00a73\u00a74\u00a75\u00a76Holy Order"));
        assertEquals("\u00a77Loans - Holy Order", net.tfminecraft.simplefactions.managers.inventory.MenuTitles.legacy("Loans - \u00a76Holy Order\u00a7"));
    }

    @Test void installationAndUpgradeRangesRespectTheirConfiguredMaximums() {
        var report = EspionageService.createReport(Map.of(), 100, new Random(1));
        for (String metric : List.of("Installation:fort:Level", "Guild:g:Upgrade:workshop")) {
            report.estimates.put(metric, new EspionageMath.Estimate(4, 200));
            report.maximums.put(metric, 5L);
            assertEquals(new EspionageMath.Estimate(4, 5), report.estimate(metric));
            assertEquals(200, report.estimates.get(metric).upper());
            report.estimates.put(metric, new EspionageMath.Estimate(0, 200));
            assertEquals("Unknown", report.display(metric));
        }
    }

    @Test void onlyMaskedHoldersAllowForeignAccessToPreviouslyPrivateMenus() {
        var outsider = mock(Player.class); var faction = mock(Faction.class);
        try (var factions = mockStatic(net.tfminecraft.simplefactions.managers.FactionManager.class)) {
            factions.when(() -> net.tfminecraft.simplefactions.managers.FactionManager.getByString("f")).thenReturn(faction);
            var holder = new net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder("f", net.tfminecraft.simplefactions.enums.SFGUI.MILITARY_VIEW);
            assertTrue(EspionageAccess.denied(outsider, holder));
            holder.markReported();
            assertFalse(EspionageAccess.denied(outsider, holder));
            var privateOffice = new net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder("f", net.tfminecraft.simplefactions.enums.SFGUI.SPYMASTER_SETTINGS);
            privateOffice.markReported();
            assertTrue(EspionageAccess.denied(outsider, privateOffice));
        }
    }

    @Test void evenTheWorstMarginProducesRumoursAndUsefulLowTierFields() {
        for (int margin : new int[]{Integer.MIN_VALUE, -500, -1, 0, 1}) {
            var report = EspionageService.createReport(Map.of("Members", 100.0, "Professional army", 100.0), margin, new Random(8));
            assertEquals(IntelligenceTier.RUMOURS, report.tier());
            assertNotNull(report.estimate("Members"));
            assertEquals("Unknown", report.display("Professional army"));
        }
        var config = new YamlConfiguration();
        config.set("espionage.intelligence.tiers.rumours.minimum-margin", 0);
        EspionageConfig.load(config);
        assertEquals(0, EspionageConfig.settings(IntelligenceTier.RUMOURS).minimumMargin());
    }

    @Test void nonNumericTrainingAndRolesObeyTheSameTierAfterSerializationAndReload() {
        var report = EspionageService.createReport(Map.of(), 65, new Random(1));
        report.details.put("training", List.of("Professional Infantry"));
        report.details.put("guild-leader:g", List.of("Lady Raven"));
        report.details.put("leader-offices", List.of("Spymaster"));
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(report), IntelligenceReport.class);
        assertEquals(List.of("Professional Infantry"), restored.details("training", "training"));
        var config = new YamlConfiguration();
        for (String field : List.of("training", "guild-leader", "office-holder"))
            config.set("espionage.intelligence.minimum-tiers." + field, "detailed");
        EspionageConfig.load(config);
        assertTrue(restored.details("training", "training").isEmpty());
        assertTrue(restored.details("guild-leader", "guild-leader:g").isEmpty());
        assertTrue(restored.details("office-holder", "leader-offices").isEmpty());
    }

    @Test void rosterGroupsUseEstimatedWealthAndNeverLeakUnsampledRolesWhenTheirGateChanges() {
        var viewer = mock(Player.class);
        var faction = mock(Faction.class, RETURNS_DEEP_STUBS);
        var poor = mock(Guild.class); var rich = mock(Guild.class);
        when(faction.getLeader()).thenReturn("Leader");
        when(faction.getGuildHandler().getGuilds()).thenReturn(List.of(poor, rich));
        when(poor.getId()).thenReturn("poor"); when(poor.getName()).thenReturn("Poor Guild");
        when(rich.getId()).thenReturn("rich"); when(rich.getName()).thenReturn("Rich Guild");
        var report = EspionageService.createReport(Map.of(), 65, new Random(1));
        report.roster = List.of(new IntelligenceReport.RosterMember("Known Member", "rich", "Rich Guild", true, false, List.of()),
                new IntelligenceReport.RosterMember("Secret Spy", "poor", "Poor Guild", false, false, List.of(SpecialPosition.SPYMASTER)),
                new IntelligenceReport.RosterMember("Guild Baron", "rich", "Rich Guild", false, true, List.of()));
        try (var service = mockStatic(EspionageService.class); var names = mockStatic(CharacterNames.class, CALLS_REAL_METHODS)) {
            service.when(() -> EspionageService.report(viewer, faction)).thenReturn(report);
            service.when(() -> EspionageService.visibleValue(eq(viewer), eq(faction), anyString(), any()))
                    .thenAnswer(call -> call.getArgument(2).toString().contains("rich") ? 100.0 : 10.0);
            names.when(() -> CharacterNames.display(viewer, "Leader")).thenReturn("King Rowan");
            var lore = RosterLore.faction(viewer, faction);
            String text = String.join("\n", lore);
            assertTrue(text.startsWith("#e8c55a\u00a7lLeader:"));
            assertTrue(text.indexOf("Rich Guild") < text.indexOf("Poor Guild"));
            assertTrue(text.indexOf("Guild Baron") < text.indexOf("Known Member"));
            assertTrue(text.contains("Spymaster: #c2dacaSecret Spy"));
            var config = new YamlConfiguration();
            config.set("espionage.intelligence.minimum-tiers.office-holder", "detailed");
            config.set("espionage.intelligence.minimum-tiers.guild-leader", "detailed");
            EspionageConfig.load(config);
            text = String.join("\n", RosterLore.faction(viewer, faction));
            assertFalse(text.contains("Secret Spy"));
            assertFalse(text.contains("Guild Baron"));
            assertTrue(text.contains("Known Member"));
        }
        verify(poor, never()).getWealth(); verify(rich, never()).getWealth();
        verify(poor, never()).getMembers(); verify(rich, never()).getMembers();
    }
}
