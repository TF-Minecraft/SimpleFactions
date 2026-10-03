package net.tfminecraft.simplefactions.espionage;

import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Cashflow;

/** Financial estimates are captured with the daily report, never read live in foreign menus. */
public final class IntelligenceLedger {
    private IntelligenceLedger() {}
    public static String key(Guild guild, String metric) { return "Guild:" + guild.getId() + ":" + metric; }

    static void capture(Map<String, Double> values, Guild guild) {
        double income = 0, expenses = 0;
        for (Cashflow flow : Cashflow.values()) {
            double value = guild.getLedger().getIncome(flow);
            values.put(key(guild, "Cashflow:" + flow.name()), value);
            income += Math.max(0, value);
            expenses += Math.max(0, -value);
        }
        values.put(key(guild, "Income total"), income);
        values.put(key(guild, "Expense total"), expenses);
        if (!guild.isBase()) {
            values.put(key(guild, "Dividend rate"), guild.getDividendPercent());
            var dividends = guild.getLedger().getDividendBreakdown();
            values.put(key(guild, "Dividend pool"), dividends.pool());
            values.put(key(guild, "Dividend tax"), dividends.tax());
            values.put(key(guild, "Dividend per member"), dividends.perMember());
        }
    }

    public static String value(IntelligenceReport report, Guild guild, String metric, String units) {
        var range = report == null ? null : report.estimate(key(guild, metric));
        if (range == null) return "\u00a77Unknown";
        String color = range.midpoint() < 0 || metric.equals("Expense total") || metric.equals("Dividend tax") ? "#cf493a" : "#7fbd73";
        return color + range.display() + units;
    }

    public static List<String> summary(IntelligenceReport report, Guild guild) {
        var lore = new java.util.ArrayList<>(List.of("#4c5250\u00a7oAdded to the bank at the", "#4c5250\u00a7ostart of a new day", "", "#4fd945Income"));
        cashflowLines(lore, report, guild, false);
        lore.add("#d6cf69Total: " + value(report, guild, "Income total", "d/day"));
        lore.add(""); lore.add("#cf493aExpenses");
        cashflowLines(lore, report, guild, true);
        lore.add("#d6cf69Total: " + value(report, guild, "Expense total", "d/day"));
        lore.add(""); lore.add("#f2e5c2Net Income");
        lore.add("#d6cf69Total: " + value(report, guild, "Income", "d/day"));
        lore.add(""); lore.add("\u00a77Click to inspect the reported accounts.");
        return lore;
    }

    private static void cashflowLines(List<String> lore, IntelligenceReport report, Guild guild, boolean expenses) {
        if (report == null) return;
        for (Cashflow flow : Cashflow.values()) {
            String metric = "Cashflow:" + flow.name();
            var range = report.estimate(key(guild, metric));
            if (range != null && range.midpoint() != 0 && (range.midpoint() < 0) == expenses)
                lore.add(flow.getDisplay() + ": " + value(report, guild, metric, "d/day"));
        }
    }
}
