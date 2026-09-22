package me.Plugins.SimpleFactions.mercenary.company;

import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

/**
 * The credit score system for violence, in the shape of
 * {@link me.Plugins.SimpleFactions.Guild.loans.CreditCalculator}: per-event
 * maximums as constants, weighted factors clamped to 0..1, so severity scales
 * rather than snapping. An early breach on a barely served contract hurts far
 * more than one that almost finished.
 *
 * <p>With bankruptcy as a legitimate exit and refunds capped at whatever the
 * contract says, reputation is the only thing that makes a repeat market work
 * instead of a one-shot scam market.
 */
public final class MercenaryReputationCalculator {

    private MercenaryReputationCalculator() {}

    /* =========================
       CONFIG (tweak freely)
       ========================= */

    private static final int MAX_COMPLETION_BONUS = 15;
    private static final int MAX_ABSENCE_PENALTY = 10;
    private static final int MAX_BREACH_PENALTY = 40;
    private static final int MAX_BANKRUPTCY_PENALTY = 40;

    private static final double DURATION_WEIGHT = 0.6;
    private static final double SIZE_WEIGHT = 0.4;

    private static final double SERVICE_WEIGHT = 0.7;
    private static final double TIME_WEIGHT = 0.3;

    /** A fourteen day contract is the longest the lock allows, so it is full credit. */
    private static final double LONG_CONTRACT_DAYS = 14.0;
    /** Ten slots is a large company, so hiring that many is full credit. */
    private static final double LARGE_CONTRACT_SLOTS = 10.0;

    /* =========================
       PUBLIC API
       ========================= */

    /** Ran its course with nobody ever missing a battle. Bigger jobs earn more. */
    public static int calculateCompletionBonus(MercenaryContract contract) {
        if (contract == null || !contract.hasCleanAttendance()) {
            return 0;
        }
        double durationFactor = clamp01(contract.getDurationDays() / LONG_CONTRACT_DAYS);
        double sizeFactor = clamp01(contract.getSlots() / LARGE_CONTRACT_SLOTS);

        double scoreFactor =
                (durationFactor * DURATION_WEIGHT) +
                (sizeFactor * SIZE_WEIGHT);

        return (int) Math.round(MAX_COMPLETION_BONUS * clamp01(scoreFactor));
    }

    /** Proportional to the fraction of promised slots that did not show up. */
    public static int calculateAbsencePenalty(MercenaryContract contract, int absentSlots) {
        if (contract == null || absentSlots <= 0 || contract.getSlots() <= 0) {
            return 0;
        }
        double absentFraction = clamp01(absentSlots / (double) contract.getSlots());

        return -(int) Math.round(MAX_ABSENCE_PENALTY * absentFraction);
    }

    /**
     * The company dropped below the slots it promised. Weighted the way
     * {@code calculateDefaultPenalty} weights an early default: walking out on day
     * one is close to fraud, walking out on the last day is close to a rounding error.
     */
    public static int calculateBreachPenalty(MercenaryContract contract) {
        return -(int) Math.round(MAX_BREACH_PENALTY * breachSeverity(contract));
    }

    /**
     * There is no mechanical punishment for bankruptcy by design, so reputation is
     * the entire consequence and it is scaled the same way a breach is.
     */
    public static int calculateBankruptcyPenalty(MercenaryContract contract) {
        return -(int) Math.round(MAX_BANKRUPTCY_PENALTY * breachSeverity(contract));
    }

    /* =========================
       DISPLAY
       ========================= */

    /** Dark red to bright green, the same gradient the loan credit score uses. */
    public static String display(int reputation) {
        double s = Math.max(0, Math.min(100, reputation));
        double t = s / 100.0;

        int startR = 139, startG = 0,   startB = 0;
        int endR   = 0,   endG   = 255, endB   = 0;

        int r = (int) Math.round(startR + (endR - startR) * t);
        int g = (int) Math.round(startG + (endG - startG) * t);
        int b = (int) Math.round(startB + (endB - startB) * t);

        return StringFormatter.formatHex(String.format("#%02X%02X%02X", r, g, b)
                + reputation + "/100 (" + band(reputation) + ")");
    }

    /** Plain language, because a bare number does not tell a hirer whether to sign. */
    public static String band(int reputation) {
        if (reputation >= 80) return "Trusted";
        if (reputation >= 60) return "Reliable";
        if (reputation >= 35) return "Unproven";
        return "Notorious";
    }

    /* =========================
       HELPERS
       ========================= */

    private static double breachSeverity(MercenaryContract contract) {
        if (contract == null) {
            return 1.0;
        }
        double served = serviceProgress(contract);

        // Less of the job done = worse
        double serviceFactor = 1.0 - served;

        // Earlier in the window = worse
        double timeFactor = 1.0 - served;

        double severity =
                (serviceFactor * SERVICE_WEIGHT) +
                (timeFactor * TIME_WEIGHT);

        return clamp01(severity);
    }

    private static double serviceProgress(MercenaryContract contract) {
        int duration = contract.getDurationDays();
        if (duration <= 0) return 1.0;
        return clamp01(contract.getDaysServed() / (double) duration);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
