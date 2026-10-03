package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.Cache;

class IntelligenceRangesTest {
    private IntelligenceReport report(String metric, long lower, long upper) {
        var report = new IntelligenceReport();
        report.quality = "Detailed estimates";
        report.estimates.put(metric, new EspionageMath.Estimate(lower, upper));
        return report;
    }

    @Test
    void cachedStabilityUsesBoundedMidpointForBothStateAndRangeColor() {
        var report = report("Stability", 80, 200);
        assertEquals(new EspionageMath.Estimate(80, 100), report.estimate("Stability"));
        assertEquals("#45c46fStable State", IntelligenceReport.stabilityState(report));
        assertEquals("#45c46f80 to 100%", IntelligenceReport.stabilityRange(report));
        assertEquals(new EspionageMath.Estimate(80, 200), report.estimates.get("Stability"),
                "Display sanitization must not rewrite the daily snapshot");
        var struggling = report("Stability", 40, 70);
        assertEquals("#c76734Struggling State", IntelligenceReport.stabilityState(struggling));
        assertEquals("#c7673440 to 70%", IntelligenceReport.stabilityRange(struggling));
    }

    @Test
    void uninformativeOrInvalidStabilityIsUnknownRatherThanAnInventedState() {
        for (long[] bounds : new long[][]{{0, 200}, {0, 100}, {201, 300}, {-100, -1}, {100, 100}, {100, 200}, {80, 20}}) {
            var report = report("Stability", bounds[0], bounds[1]);
            assertEquals("Unknown", report.display("Stability"));
            assertEquals("\u00a77Unknown State", IntelligenceReport.stabilityState(report));
            assertEquals("\u00a77Unknown", IntelligenceReport.stabilityRange(report));
        }
        assertEquals("\u00a77Unknown State", IntelligenceReport.stabilityState(null));
        assertEquals("\u00a77Unknown", IntelligenceReport.stabilityRange(null));
    }

    @Test
    void generatedStabilityRangesAreBoundedUsefulAndContainTheActualValue() {
        var random = new Random(41);
        int known = 0;
        int unknown = 0;
        for (int stability : new int[]{0, 1, 19, 40, 60, 75, 99, 100}) {
            for (int margin : new int[]{1, 30, 65, 100}) {
                for (int sample = 0; sample < 30; sample++) {
                    var report = EspionageService.createReport(Map.of("Stability", (double) stability), margin, random);
                    var range = report.estimate("Stability");
                    if (range == null) { unknown++; continue; }
                    known++;
                    assertTrue(range.lower() >= 0 && range.upper() <= 100);
                    assertTrue(range.lower() <= stability && range.upper() >= stability);
                    assertTrue(range.lower() < range.upper() && range.upper() - range.lower() <= 100 * EspionageConfig.settings(report.tier()).boundedFraction());
                }
            }
        }
        assertTrue(known > 0 && unknown > 0);
    }

    @Test
    void countAndOutputRangesCannotBeNegativeOrExceedPublicGuildCapacity() {
        for (String metric : new String[]{"Members", "Professional army", "Levies", "Mercenaries", "Installations", "Prosperity", "Guild:test:Trade power"}) {
            assertEquals(new EspionageMath.Estimate(0, 3), report(metric, -5, 3).estimate(metric));
            assertEquals("Unknown", report(metric, -10, -2).display(metric));
            assertEquals("Unknown", report(metric, 0, 1000).display(metric));
        }
        int previous = Cache.maxMembers;
        try {
            Cache.maxMembers = 64;
            assertEquals(new EspionageMath.Estimate(50, 64), report("Guild:test:Members", 50, 100).estimate("Guild:test:Members"));
            assertEquals("Unknown", report("Guild:test:Members", 0, 100).display("Guild:test:Members"));
            assertEquals("Unknown", report("Guild:test:Members", 80, 100).display("Guild:test:Members"));
        } finally { Cache.maxMembers = previous; }
    }

    @Test
    void legitimateDebtAndDeficitsRemainSignedButBroadGuessesAreUnknown() {
        for (String metric : new String[]{"Wealth", "Daily net income", "Administrative power", "Guild:test:Wealth", "Guild:test:Income"}) {
            assertEquals("-100 to -50", report(metric, -100, -50).display(metric));
            assertEquals("Unknown", report(metric, -500, 500).display(metric));
            assertEquals("Unknown", report(metric, 0, 1000).display(metric));
            assertEquals("20 to 70", report(metric, 20, 70).display(metric));
        }
    }

    @Test
    void missingAndLegacyReportsUseUnknownWithoutChangingTheirData() {
        var report = new IntelligenceReport();
        report.quality = "Detailed estimates";
        report.quality = "Hidden";
        assertEquals("Unknown", report.qualityLabel());
        assertEquals("Hidden", report.quality);
        assertEquals("Unknown", report.display("Wealth"));
        report.estimates = null;
        assertEquals("Unknown", report.display("Wealth"));
        assertEquals("Rumours", EspionageMath.quality(0));
    }
}
