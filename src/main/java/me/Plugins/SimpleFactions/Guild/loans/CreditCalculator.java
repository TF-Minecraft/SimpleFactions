package me.Plugins.SimpleFactions.Guild.loans;

public final class CreditCalculator {

    private CreditCalculator() {}

    /* =========================
       CONFIG (tweak freely)
       ========================= */

    private static final int MAX_DAILY_OVERDUE_PENALTY = 10;
    private static final int MAX_DEFAULT_PENALTY = 40;
    private static final int MAX_PAYOFF_BONUS = 15;

    private static final double PAYMENT_WEIGHT = 0.7;
    private static final double TIME_WEIGHT = 0.3;

    /* =========================
       PUBLIC API
       ========================= */

    public static int calculateDefaultPenalty(Loan loan) {
        double paidRatio = getPaidRatio(loan);
        double timeProgress = getTimeProgress(loan);

        // Less paid = worse
        double paymentFactor = 1.0 - paidRatio;

        // Earlier default = worse
        double timeFactor = 1.0 - timeProgress;

        double severity =
                (paymentFactor * PAYMENT_WEIGHT) +
                (timeFactor * TIME_WEIGHT);

        severity = clamp01(severity);

        return -(int) Math.round(MAX_DEFAULT_PENALTY * severity);
    }

    public static int calculatePayoffBonus(Loan loan) {
        double timeProgress = getTimeProgress(loan);

        // Overdue = no bonus
        if (loan.getDaysUntilDue() < 0) {
            return 0;
        }

        // Peaks at due date
        double closeness = 1.0 - Math.abs(1.0 - timeProgress);
        closeness = clamp01(closeness);

        // Interest satisfaction
        double interestRatio = loan.getPaidInterest() / loan.getAmount();
        double interestFactor = clamp01(interestRatio);

        double scoreFactor =
                (closeness * 0.6) +
                (interestFactor * 0.4);

        return (int) Math.round(MAX_PAYOFF_BONUS * scoreFactor);
    }

    public static int calculateDailyOverduePenalty(Loan loan) {

        if (!loan.isOverdue() || loan.getStatus() == LoanStatus.DEFAULTED) {
            return 0;
        }

        if (loan.getAmount() <= 0) return 0;

        // How much principal remains unpaid
        double principalRemainingRatio =
                clamp01((loan.getAmount() - loan.getPaid()) / loan.getAmount());

        // How late we are (maxed at 14 days)
        int daysOverdue = Math.abs(loan.getDaysUntilDue());
        double latenessFactor = clamp01(daysOverdue / 14.0);

        // Weight principal heavier than time
        double severity =
                (principalRemainingRatio * 0.7) +
                (latenessFactor * 0.3);

        severity = clamp01(severity);

        return -(int) Math.round(MAX_DAILY_OVERDUE_PENALTY * severity);
    }

    /* =========================
       HELPERS
       ========================= */

    private static double getPaidRatio(Loan loan) {
        if (loan.getAmount() <= 0) return 1.0;
        return clamp01(loan.getPaid() / loan.getAmount());
    }

    private static double getTimeProgress(Loan loan) {
        int duration = loan.getDurationInDays();
        if (duration <= 0) return 1.0;

        int remaining = loan.getDaysUntilDue();
        double progress = 1.0 - (remaining / (double) duration);

        return clamp01(progress);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

