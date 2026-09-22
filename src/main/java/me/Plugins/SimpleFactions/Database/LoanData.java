package me.Plugins.SimpleFactions.Database;

import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Guild.loans.LoanStatus;

public class LoanData {
    public String id;
    public Double amount;
    public Double paidInterest;
    public Double unpaidInterest;
    public String issuer;
    public String borrower;
    public Long issueDate;
    public Long dueDate;
    public Double interestRate;
    public Double overdueFee;
    public Double paid;
    public Boolean autoPay;

    public Double tempPayment = 0.0; //Used by the ledger to earmark a payment without processing during that calculation cycle
    public Double tempInterestPayment = 0.0; //Used by the ledger to earmark an interest payment without processing during that calculation cycle

    public Boolean defaulted = false;
    public Boolean pausedInterest = false;

    public String status;

    public LoanData(Loan loan) {
        this.id = loan.getId();
        this.amount = loan.getAmount();
        this.paidInterest = loan.getPaidInterest();
        this.unpaidInterest = loan.getUnpaidInterest();
        this.issuer = loan.getIssuer().getId();
        this.borrower = loan.getBorrower().getId();
        this.issueDate = loan.getIssueDate();
        this.dueDate = loan.getDueDate();
        this.interestRate = loan.getInterestRate();
        this.paid = loan.getPaid();
        this.autoPay = loan.isAutoPay();
        this.defaulted = loan.hasDefaulted();
        this.pausedInterest = loan.isInterestPaused();
        this.status = loan.getStatus().name();
        this.overdueFee = loan.getOverdueFee();
    }
}
