package net.tfminecraft.simplefactions.guild.loans;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How much of each automatic loan payment a borrower's money covers on one day. Repayments are
 * funded first and interest second, each in loan order, until the budget runs out. Whatever is
 * not funded stays owed on the loan, so a lender is never credited money the borrower lacks.
 */
public final class LoanFunding {

    /** The repayment and interest a borrower can pay towards one loan today. */
    public record Funded(double principal, double interest) {
        public double total() {
            return principal + interest;
        }
    }

    public static final Funded NONE = new Funded(0.0, 0.0);

    private LoanFunding() {
    }

    /** True for a loan the daily settlement charges automatically. */
    public static boolean isCharged(Loan loan) {
        return loan != null && loan.isAutoPay() && !loan.isPaidOff();
    }

    /** Splits budget across the automatic payments on these loans. */
    public static Map<Loan, Funded> plan(List<Loan> loansTaken, double budget) {
        Map<Loan, Funded> plan = new LinkedHashMap<>();
        if (loansTaken == null) return plan;
        double left = Double.isFinite(budget) ? Math.max(0.0, budget) : 0.0;
        Map<Loan, Double> principal = new LinkedHashMap<>();
        for (Loan loan : loansTaken) {
            if (!isCharged(loan)) continue;
            double paid = Math.min(due(loan.getDailyPayment(true)), left);
            left -= paid;
            principal.put(loan, paid);
        }
        for (Map.Entry<Loan, Double> entry : principal.entrySet()) {
            double paid = Math.min(due(entry.getKey().getDailyInterest()), left);
            left -= paid;
            plan.put(entry.getKey(), new Funded(entry.getValue(), paid));
        }
        return plan;
    }

    private static double due(double amount) {
        return Double.isFinite(amount) && amount > 0 ? amount : 0.0;
    }
}
