package net.tfminecraft.simplefactions.espionage;

import java.util.Set;
import net.tfminecraft.simplefactions.Cache;

/** Uses public metric limits and cached estimates only, never a target's exact values. */
public final class IntelligenceRanges {
    private static final Set<String> NONNEGATIVE = Set.of("Members", "Professional army", "Levies",
            "Mercenaries", "Installations", "Stability", "Prosperity");
    private IntelligenceRanges() {}

    public static boolean nonnegative(String metric) {
        return NONNEGATIVE.contains(metric) || metric.equals("Legitimacy") || metric.equals("Council size")
                || metric.startsWith("Regiment:") || metric.startsWith("Installation:")
                || metric.startsWith("Tax:") || metric.startsWith("Training:")
                || metric.contains(":Branch:") || metric.contains(":Upgrade:")
                || metric.endsWith(":Members") || metric.endsWith(":Trade power")
                || metric.endsWith(":Income total") || metric.endsWith(":Expense total")
                || metric.contains(":Dividend ") || metric.startsWith("Position:") || cashflow(metric) != null
                        && cashflow(metric) != net.tfminecraft.simplefactions.guild.income.Cashflow.VEHICLE_FEES && !nonpositive(metric);
    }

    private static net.tfminecraft.simplefactions.guild.income.Cashflow cashflow(String metric) {
        int start = metric.indexOf(":Cashflow:");
        if (start < 0) return null;
        try { return net.tfminecraft.simplefactions.guild.income.Cashflow.valueOf(metric.substring(start + 10)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean nonpositive(String metric) {
        var flow = cashflow(metric);
        if (flow == null) return false;
        return switch (flow) {
            case GUILD_PAYMENTS, DIVIDEND_PAYMENT, DIVIDEND_PAYOUT, TARIFF_PAYMENTS, HUB_TAX_PAYMENTS,
                    LOAN_PAYMENTS, INTEREST_PAYMENTS, TRIBUTE_PAYMENTS, OVERLORD_TAX, WAR_REPARATIONS_PAYMENT,
                    MERCENARY_PAYMENTS, REFUND_PAYMENTS, WAGE_PAYMENTS, TRADE_UPKEEP, UPGRADES_UPKEEP,
                    PENALTIES, INSTALLATIONS, VEHICLE_UPKEEP, MILITARY_UPKEEP, NODES, SUPPLY_HUBS -> true;
            default -> false;
        };
    }

    public static EspionageMath.Estimate reasonable(String metric, EspionageMath.Estimate raw) {
        return reasonable(metric, raw, IntelligenceTier.RELIABLE);
    }

    public static EspionageMath.Estimate reasonable(String metric, EspionageMath.Estimate raw, IntelligenceTier tier) {
        if (tier == IntelligenceTier.UNKNOWN) return null;
        if (raw == null || raw.lower() >= raw.upper()) return null;
        long minimum = nonnegative(metric) ? 0 : Long.MIN_VALUE;
        Long maximum = null;
        if (metric.equals("Stability") || metric.equals("Legitimacy") || metric.startsWith("Tax:") || metric.endsWith(":Dividend rate") || metric.startsWith("Position:")) maximum = 100L;
        else if (metric.startsWith("Guild:") && metric.endsWith(":Members") && Cache.maxMembers > 0)
            maximum = (long) Cache.maxMembers;
        boolean boundedDomain = maximum != null;
        if (nonpositive(metric)) maximum = 0L;
        long lower = Math.max(minimum, raw.lower());
        long upper = maximum == null ? raw.upper() : Math.min(maximum, raw.upper());
        if (lower >= upper) return null; // Bounds must not invent an exact value.
        var range = new EspionageMath.Estimate(lower, upper);
        double width = (double) upper - lower;
        if (boundedDomain) {
            if (width > (maximum - (double) minimum) * EspionageConfig.settings(tier).boundedFraction()) return null;
        } else if (lower < 0 && upper > 0 || width >= EspionageConfig.settings(tier).relativeWidth() * Math.max(2, Math.abs(range.midpoint()))) {
            return null; // An effectively unbounded or sign-ambiguous guess is not useful intelligence.
        }
        return range;
    }
}
