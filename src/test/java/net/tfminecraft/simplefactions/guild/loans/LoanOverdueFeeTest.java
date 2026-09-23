package net.tfminecraft.simplefactions.guild.loans;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoanOverdueFeeTest {

	@Test
	void overdueDayChargesInterestAndTheDailyFeeWithoutRecursing() {
		// A negative duration puts the due date in the past.
		Loan loan = new Loan(100.0, null, null, System.currentTimeMillis(), -1, 7.0, 2.0, false);

		assertEquals(1.0, loan.getDailyInterestChange() - loan.getDailyOverdueFee(), 1e-9);
		assertEquals(2.0, loan.getDailyOverdueFee(), 1e-9);
		assertEquals(3.0, loan.getDailyInterestChange(), 1e-9);
		assertEquals(3.0, loan.getDailyInterest(), 1e-9);
	}
}
