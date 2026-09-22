package me.Plugins.SimpleFactions.Guild.loans;

import java.util.UUID;

import me.Plugins.SimpleFactions.Database.LoanData;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Utils.Formatter;

public class Loan {
    private String id;
    private double amount;
    private double paidInterest;
    private double unpaidInterest;
    private Guild issuer;
    private Guild borrower;
    private long issueDate;
    private long dueDate;
    private double interestRate;
    private double paid;
    private boolean autoPay;
    private double overdueFee;

    private double tempPayment = 0; //Used by the ledger to earmark a payment without processing during that calculation cycle
    private double tempInterestPayment = 0; //Used by the ledger to earmark an interest payment without processing during that calculation cycle

    private boolean defaulted = false;
    private boolean pausedInterest = false;

    private LoanStatus status = LoanStatus.ACTIVE; //internal tracker so penalties/bonuses only apply once

    public Loan(double amount, Guild issuer, Guild borrower, long issueDate, int durationInDays, double interestRate, double overdueFee, boolean autoPay) {
        id = UUID.randomUUID().toString();
        this.amount = amount;
        this.paidInterest = 0;
        this.unpaidInterest = 0;
        this.issuer = issuer;
        this.borrower = borrower;
        this.issueDate = issueDate;
        this.dueDate = issueDate + durationInDays * 24 * 60 * 60 * 1000L;
        this.interestRate = interestRate;
        this.paid = 0.0;
        this.autoPay = autoPay;
        this.overdueFee = overdueFee;
    }

    public Loan(LoanData data) {
        this.id = data.id;
        this.amount = data.amount;
        this.paidInterest = data.paidInterest;
        this.unpaidInterest = data.unpaidInterest;
        this.issuer = FactionManager.getGuildByString(data.issuer);
        this.borrower = FactionManager.getGuildByString(data.borrower);
        this.issueDate = data.issueDate;
        this.dueDate = data.dueDate;
        this.interestRate = data.interestRate;
        this.overdueFee = data.overdueFee != null ? data.overdueFee : 0.0;
        this.paid = data.paid;
        this.autoPay = data.autoPay;
        this.defaulted = data.defaulted;
        this.pausedInterest = data.pausedInterest;
        this.status = data.status != null ? LoanStatus.valueOf(data.status) : LoanStatus.ACTIVE;
        if(issuer == null || borrower == null) {
            throw new IllegalArgumentException("Invalid issuer or borrower ID in LoanData");
        }
    }

    public String getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public double getPaidInterest() {
        return paidInterest;
    }

    public double getUnpaidInterest() {
        return unpaidInterest;
    }

    public Guild getIssuer() {
        return issuer;
    }

    public Guild getBorrower() {
        return borrower;
    }

    public long getIssueDate() {
        return issueDate;
    }

    public long getDueDate() {
        return dueDate;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public double getOverdueFee() {
        return overdueFee;
    }

    public double getPaid() {
        return paid;
    }

    public boolean hasDefaulted() {
        return defaulted;
    }

    public boolean isInterestPaused() {
        return pausedInterest;
    }

    public boolean isAutoPay() {
        return autoPay;
    }

    public void setDefaulted(boolean defaulted) {
        this.defaulted = defaulted;
        if(status == LoanStatus.ACTIVE && defaulted) {
            status = LoanStatus.DEFAULTED;
            int penalty = CreditCalculator.calculateDefaultPenalty(this);
            borrower.getLoanHandler().changeCreditScore(penalty);
        }
    }

    public void setPausedInterest(boolean pausedInterest) {
        this.pausedInterest = pausedInterest;
    }

    public void setAutoPay(boolean autoPay) {
        this.autoPay = autoPay;
    }

    public void tickDay() {
        double dailyInterest = getDailyInterestChange();
        if (isOverdue() && status == LoanStatus.ACTIVE) {
            int penalty = CreditCalculator.calculateDailyOverduePenalty(this);
            borrower.getLoanHandler().changeCreditScore(penalty);
        }
        if (!autoPay) {
            // Interest compounds onto the loan
            unpaidInterest += dailyInterest;
        }
        if(tempPayment > 0) {
            makePayment(tempPayment, false);
            tempPayment = 0;
        }
        if(tempInterestPayment > 0) {
            tempInterestPayment -= dailyInterest; //Remove today's interest from the temp payment to avoid double counting
            if(tempInterestPayment > 0) {
                paidInterest += tempInterestPayment;
                unpaidInterest = Math.max(0, unpaidInterest - tempInterestPayment);
                paid += tempInterestPayment;
            }
            tempInterestPayment = 0;
        }
    }
        
    public void setTempPayment(double amount) {
        this.tempPayment = amount;
    }

    public void setTempInterestPayment(double amount) {
        this.tempInterestPayment = amount;
    }

    public boolean isPaidOff() {
        return Formatter.formatDouble(getTotalOwed()) <= 0;
    }

    public double getDailyInterestRate() {
        return interestRate / 7.0;
    }

    public double getDailyInterestChange() {
        if(isPaidOff()) return 0.0;
        double today = pausedInterest ? 0 : getTotalOwed() * getDailyInterestRate() / 100.0;
        if(isOverdue()) today += getDailyOverdueFee();
        return today;
    }

    public double getDailyOverdueFee() {
        if(isPaidOff()) return 0.0;
        return isOverdue() ? getTotalOwed() * getDailyInterest() / 100.0 : 0.0;
    }

    public double getDailyInterest() {
        if(isPaidOff()) return 0.0;
        double today = getDailyInterestChange();
        if(unpaidInterest > 0) {
            today+=Math.min(unpaidInterest, getDailyPayment(false));
        }
        return today;
    }

    public double getTotalOwed() {
        return amount + unpaidInterest + paidInterest - paid;
    }

    public int getDaysUntilDue() {
        long now = System.currentTimeMillis();
        long diff = dueDate - now;
        return (int) (diff / (1000 * 60 * 60 * 24));
    }

    public int getDurationInDays() {
        long diff = dueDate - issueDate;
        return (int) (diff / (1000 * 60 * 60 * 24));
    }

    public boolean isOverdue() {
        return getDaysUntilDue() < 0 && !isPaidOff();
    }

    public double getDailyPayment(boolean subtractInterest) {
        if(isPaidOff()) return 0.0;
        int daysUntilDue = getDaysUntilDue();
        if (daysUntilDue <= 0) return getTotalOwed(); // Due or overdue
        double payment = (getTotalOwed() / getDaysUntilDue());
        if(subtractInterest && unpaidInterest > 0) {
            payment -= Math.min(unpaidInterest, payment);
        }
        return payment;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public double makePayment(double paymentAmount, boolean ledger) {
        if(isPaidOff()) return 0.0;
        if (paymentAmount <= 0) return 0.0;

        double totalOwed = getTotalOwed();
        if (totalOwed <= 0) return 0.0;

        // Clamp payment to what is owed
        double actualPayment = Math.min(paymentAmount, totalOwed);

        double remaining = actualPayment;

        if (unpaidInterest > 0) {
            double interestPaid = Math.min(remaining, unpaidInterest);
            unpaidInterest -= interestPaid;
            if (ledger) issuer.getLedger().addInterestPaymentEntry(borrower.getId(), interestPaid);
            paidInterest += interestPaid; // Move paid interest to the main interest pool
            remaining -= interestPaid;
        }

        if (ledger) issuer.getLedger().addLoanPaymentEntry(borrower.getId(), remaining);

        paid += actualPayment;

        if(status == LoanStatus.ACTIVE && isPaidOff()) {
            status = LoanStatus.PAID_OFF;
            int bonus = CreditCalculator.calculatePayoffBonus(this);
            borrower.getLoanHandler().changeCreditScore(bonus);
        }

        return actualPayment;
    }

    public double getEstimatedCost() {
        if (isPaidOff()) return 0.0;

        int daysRemaining = Math.max(0, getDaysUntilDue());
        if (daysRemaining == 0) return getTotalOwed();

        double remainingPrincipal = Math.max(0, amount - paid);

        double dailyRate = getDailyInterestRate() / 100.0;

        // Average principal over time (linear payoff assumption)
        double averagePrincipal = remainingPrincipal / 2.0;

        double estimatedInterest =
                averagePrincipal * dailyRate * daysRemaining;

        return Formatter.formatDouble(remainingPrincipal + estimatedInterest);
    }

    public boolean validate(Loan loan) {
        if(loan.getAmount() != this.amount) return false;
        if(loan.getInterestRate() != this.interestRate) return false;
        if(loan.getDurationInDays() != this.getDurationInDays()) return false;
        if(loan.isAutoPay() != this.autoPay) return false;
        if(loan.getOverdueFee() != this.overdueFee) return false;
        return true;
    }
}
