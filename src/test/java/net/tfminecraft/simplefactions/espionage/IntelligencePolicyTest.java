package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.objects.Faction;

class IntelligencePolicyTest {
    @AfterEach void defaults() { EspionageConfig.load(new YamlConfiguration()); }

    @Test void armyAndCashflowsAreGatedAtGenerationAndWhenReadingOldSnapshots() {
        var values = Map.of("Professional army", 100.0, "Guild:guild:Cashflow:TRADE", 100.0, "Members", 100.0);
        var broad = EspionageService.createReport(values, 40, new Random(1));
        assertEquals("Unknown", broad.display("Professional army"));
        assertEquals("Unknown", broad.display("Guild:guild:Cashflow:TRADE"));
        assertFalse(broad.estimates.containsKey("Professional army"));
        // Old data cannot bypass a disclosure rule after reloading configuration.
        broad.estimates.put("Professional army", new EspionageMath.Estimate(90, 110));
        assertEquals("Unknown", broad.display("Professional army"));
        var reliable = EspionageService.createReport(values, 65, new Random(1));
        assertNotEquals("Unknown", reliable.display("Professional army"));
        var config = new YamlConfiguration();
        config.set("espionage.intelligence.minimum-tiers.professional-army", "detailed");
        config.set("espionage.intelligence.minimum-tiers.cashflows.TRADE", "broad");
        EspionageConfig.load(config);
        assertEquals("Unknown", reliable.display("Professional army"));
        assertTrue(broad.allows("Guild:guild:Cashflow:TRADE"));
        config.set("espionage.intelligence.minimum-tiers.professional-army", "typo");
        EspionageConfig.load(config);
        assertFalse(EspionageConfig.allows(IntelligenceTier.DETAILED, "Professional army"));
        assertFalse(EspionageConfig.allows(IntelligenceTier.DETAILED, "Unconfigured private metric"));
    }

    @Test void lowerTiersGiveBroaderUsefulRangesAndThresholdsAreConfigurable() {
        var rumours = EspionageService.createReport(Map.of("Members", 100.0), 1, new Random(8));
        var detailed = EspionageService.createReport(Map.of("Members", 100.0), 100, new Random(8));
        assertNotNull(rumours.estimate("Members"));
        var broad = rumours.estimate("Members");
        var narrow = detailed.estimate("Members");
        assertTrue(broad.upper() - broad.lower() > narrow.upper() - narrow.lower());
        assertTrue(broad.lower() >= 0 && broad.lower() <= 100 && broad.upper() >= 100);
        var config = new YamlConfiguration();
        config.set("espionage.intelligence.tiers.reliable.minimum-margin", 80);
        EspionageConfig.load(config);
        assertEquals(IntelligenceTier.BROAD, EspionageConfig.tier(79));
        assertEquals(IntelligenceTier.RELIABLE, EspionageConfig.tier(80));
        config.set("espionage.intelligence.minimum-tiers.wealth", "unknown");
        EspionageConfig.load(config);
        assertEquals("Unknown", EspionageService.createReport(Map.of("Wealth", 100.0), 500, new Random(1)).display("Wealth"));
    }

    @Test void foreignOfficeIdentityAndAptitudeHaveIndependentThresholdsAndAreSnapshots() {
        var target = mock(Faction.class);
        var state = new EspionageState();
        when(target.getEspionage()).thenReturn(state);
        when(target.isMemberIgnoreCase("Account")).thenReturn(true);
        when(target.getMembers()).thenReturn(List.of("Account"));
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Account";
        state.appoint(holder, 80);
        try (var names = mockStatic(CharacterNames.class)) {
            names.when(() -> CharacterNames.of("Account")).thenReturn("Lady Raven");
            for (int margin : new int[]{30, 65, 100}) {
                var report = EspionageService.createReport(Map.of(), margin, new Random(1));
                EspionageService.captureOffices(report, target, new Random(1));
                assertEquals(margin < 65 ? "Unknown" : "Lady Raven", report.officeHolder(SpecialPosition.SPYMASTER));
                String key = IntelligenceReport.officeAptitudeKey(SpecialPosition.SPYMASTER);
                if (margin < 100) assertEquals("Unknown", report.display(key));
                else {
                    var range = report.estimate(key);
                    assertNotNull(range);
                    assertTrue(range.lower() <= 80 && range.upper() >= 80 && range.upper() <= 100);
                    holder.aptitude = 1;
                    var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(report), IntelligenceReport.class);
                    assertEquals(range, restored.estimate(key));
                    assertEquals("Lady Raven", restored.officeHolder(SpecialPosition.SPYMASTER));
                }
            }
        }
        var missingTier = new IntelligenceReport();
        missingTier.officeHolders.put(SpecialPosition.SPYMASTER, "Secret");
        assertEquals("Unknown", missingTier.officeHolder(SpecialPosition.SPYMASTER));
    }

    @Test void malformedNumericSettingsCannotCreateNonFiniteRollsOrInvalidTiers() {
        var config = new YamlConfiguration();
        config.set("espionage.checks.aptitude-multiplier", Double.POSITIVE_INFINITY);
        config.set("espionage.checks.luck-draws", 0);
        config.set("espionage.intelligence.tiers.rumours.minimum-margin", 500);
        config.set("espionage.intelligence.tiers.broad.minimum-margin", 1);
        config.set("espionage.intelligence.tiers.detailed.uncertainty", -1);
        EspionageConfig.load(config);
        assertEquals(1.25, EspionageConfig.rollMultiplier());
        assertEquals(3, EspionageConfig.luckDraws());
        int last = 0;
        for (var tier : IntelligenceTier.values()) if (tier != IntelligenceTier.UNKNOWN) {
            assertTrue(EspionageConfig.settings(tier).minimumMargin() > last);
            last = EspionageConfig.settings(tier).minimumMargin();
        }
        assertEquals(.1, EspionageConfig.settings(IntelligenceTier.DETAILED).uncertainty());
    }
}
