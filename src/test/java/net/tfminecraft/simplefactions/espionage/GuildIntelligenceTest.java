package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Represents;

class GuildIntelligenceTest {
    @Test
    void financialDetailsAreCapturedOnceAndForeignDisplayDoesNotReadLiveLedger() {
        var guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        when(guild.getId()).thenReturn("guild");
        when(guild.isBase()).thenReturn(true);
        when(guild.getLedger().getIncome(Cashflow.TRADE)).thenReturn(100.0);
        when(guild.getLedger().getIncome(Cashflow.CITIZENS)).thenReturn(30.0);
        when(guild.getLedger().getIncome(Cashflow.MILITARY_UPKEEP)).thenReturn(-20.0);
        Map<String, Double> values = new HashMap<>();
        IntelligenceLedger.capture(values, guild);
        assertEquals(130, values.get("Guild:guild:Income total"));
        assertEquals(20, values.get("Guild:guild:Expense total"));
        values.put("Guild:guild:Income", 110.0);
        var report = EspionageService.createReport(values, 120, new Random(5));
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(report), IntelligenceReport.class);
        clearInvocations(guild.getLedger());
        when(guild.getLedger().getIncome(Cashflow.TRADE)).thenReturn(1_000_000.0);
        clearInvocations(guild.getLedger());
        assertEquals(IntelligenceLedger.summary(report, guild), IntelligenceLedger.summary(restored, guild));
        assertFalse(IntelligenceLedger.value(restored, guild, "Cashflow:TRADE", "d/day").contains("1000000"));
        verifyNoInteractions(guild.getLedger());
        assertTrue(IntelligenceLedger.value(null, guild, "Income", "d/day").endsWith("Unknown"));
    }

    @Test
    void dividendPercentUsesValidBoundedRangeOrUnknown() {
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        var report = new IntelligenceReport();
        report.quality = "Detailed estimates";
        report.estimates.put("Guild:guild:Dividend rate", new EspionageMath.Estimate(80, 200));
        assertEquals("#7fbd7380 to 100%", IntelligenceLedger.value(report, guild, "Dividend rate", "%"));
        report.estimates.put("Guild:guild:Dividend rate", new EspionageMath.Estimate(0, 200));
        assertEquals("\u00a77Unknown", IntelligenceLedger.value(report, guild, "Dividend rate", "%"));
        report.estimates.put("Guild:guild:Cashflow:MILITARY_UPKEEP", new EspionageMath.Estimate(-3, 2));
        assertEquals("#cf493a-3 to 0d", IntelligenceLedger.value(report, guild, "Cashflow:MILITARY_UPKEEP", "d"));
        report.estimates.put("Guild:guild:Cashflow:TRADE", new EspionageMath.Estimate(-3, 3));
        assertEquals("#7fbd730 to 3d", IntelligenceLedger.value(report, guild, "Cashflow:TRADE", "d"));
    }

    @Test
    void guildRostersReuseTheFactionSampleAndRemainStableAcrossSerialization() {
        var target = mock(Faction.class, RETURNS_DEEP_STUBS);
        var guild = mock(Guild.class);
        var members = java.util.stream.IntStream.range(0, 10).mapToObj(index -> "Account" + index).toList();
        when(target.getMembers()).thenReturn(members);
        when(target.getGuildHandler().getGuilds()).thenReturn(List.of(guild));
        when(guild.getId()).thenReturn("guild");
        when(guild.isMember(anyString())).thenReturn(true);
        when(guild.isLeader("Account0")).thenReturn(true);
        var report = new IntelligenceReport();
        report.quality = "Detailed estimates";
        try (var names = mockStatic(CharacterNames.class); var represents = mockStatic(Represents.class)) {
            names.when(() -> CharacterNames.of(anyString())).thenAnswer(call -> "Character " + call.getArgument(0).toString().substring(7));
            represents.when(() -> Represents.represents(eq(target), anyString())).thenReturn("Guild");
            EspionageService.captureMembers(report, target, 65, new Random(2));
        }
        assertEquals(6, report.members.size());
        assertTrue(report.guildMembers.get("guild").stream().allMatch(name -> report.members.contains(name + " \u00a77— Guild")));
        assertFalse(report.guildMembers.get("guild").contains("Character 0"));
        assertFalse(report.members.stream().anyMatch(name -> name.contains("Account")));
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(report), IntelligenceReport.class);
        assertEquals(report.guildMembers, restored.guildMembers);
    }

    @Test
    void reportDateUsesTheLoreYearAndEraForTheSavedDay() {
        int offset = net.tfminecraft.rpcharacters.Cache.calendarYearOffset;
        String era = net.tfminecraft.rpcharacters.Cache.calendarEraSuffix;
        try {
            net.tfminecraft.rpcharacters.Cache.calendarYearOffset = 1675;
            net.tfminecraft.rpcharacters.Cache.calendarEraSuffix = "AE";
            var report = new IntelligenceReport();
            report.quality = "Detailed estimates";
            report.day = LocalDate.of(2026, 10, 3).toEpochDay();
            assertEquals("03/10/351 AE", report.loreDate());
        } finally {
            net.tfminecraft.rpcharacters.Cache.calendarYearOffset = offset;
            net.tfminecraft.rpcharacters.Cache.calendarEraSuffix = era;
        }
    }
}
