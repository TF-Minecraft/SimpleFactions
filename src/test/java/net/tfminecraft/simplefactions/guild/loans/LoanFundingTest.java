package net.tfminecraft.simplefactions.guild.loans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LoanFundingTest {

	/** A loan due today that carried a day of unpaid interest over from before auto-pay was on. */
	private static Loan dueWithCarriedInterest() {
		Loan loan = new Loan(700.0, null, null, System.currentTimeMillis(), 1, 7.0, 0.0, false);
		loan.tickDay();
		loan.setAutoPay(true);
		return loan;
	}

	@Test
	void aDueLoansCarriedInterestIsChargedOnce() {
		Loan loan = dueWithCarriedInterest();
		assertTrue(loan.getUnpaidInterest() > 0);

		LoanFunding.Funded funded = LoanFunding.plan(List.of(loan), 1_000_000.0).get(loan);

		// Everything owed, plus today's interest, and the carried interest only once.
		assertEquals(loan.getTotalOwed() + loan.getDailyInterestChange(), funded.total(), 1e-9);
		assertEquals(loan.getTotalOwed() - loan.getUnpaidInterest(), funded.principal(), 1e-9);
	}

	@Test
	void repaymentsAreFundedBeforeInterest() {
		Loan first = dueWithCarriedInterest();
		Loan second = dueWithCarriedInterest();
		double budget = first.getDailyPayment(true) + second.getDailyPayment(true) + 1.0;

		var plan = LoanFunding.plan(List.of(first, second), budget);

		assertEquals(first.getDailyPayment(true), plan.get(first).principal(), 1e-9);
		assertEquals(second.getDailyPayment(true), plan.get(second).principal(), 1e-9);
		assertEquals(1.0, plan.get(first).interest() + plan.get(second).interest(), 1e-9);
	}

	@Test
	void loansWithoutAutoPayAreNotCharged() {
		Loan manual = new Loan(700.0, null, null, System.currentTimeMillis(), 30, 7.0, 0.0, false);

		assertTrue(LoanFunding.plan(List.of(manual), 1_000.0).isEmpty());
	}

	@Test
	void anUnusableBudgetFundsNothing() {
		Loan loan = dueWithCarriedInterest();

		assertEquals(0.0, LoanFunding.plan(List.of(loan), Double.NaN).get(loan).total());
		assertEquals(0.0, LoanFunding.plan(List.of(loan), -5.0).get(loan).total());
	}
}
