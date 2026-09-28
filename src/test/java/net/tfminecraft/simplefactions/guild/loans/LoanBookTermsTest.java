package net.tfminecraft.simplefactions.guild.loans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoanBookTermsTest {

	private static String page(String amount, String duration, String interest, String fee) {
		return "[LOAN TERMS]\nAmount (d): " + amount + "\nDuration(days): " + duration
				+ "\nInterest (%): " + interest + "\nDaily Payments: \nYes\nOverdue Fee (%): " + fee + "\n";
	}

	@Test
	void theDefaultTermsAreAccepted() {
		Loan loan = LoanBook.createLoanFromString(page("1000", "30", "6", "2"), null, null, "offer-1");

		assertNotNull(loan);
		assertEquals("offer-1", loan.getId());
		assertEquals(1000.0, loan.getAmount());
		assertEquals(30, loan.getDurationInDays());
		assertEquals(6.0, loan.getInterestRate());
		assertTrue(loan.isAutoPay());
	}

	@Test
	void notANumberAndInfinityAreRejected() {
		assertNull(LoanBook.createLoanFromString(page("NaN", "30", "6", "2"), null, null));
		assertNull(LoanBook.createLoanFromString(page("Infinity", "30", "6", "2"), null, null));
		assertNull(LoanBook.createLoanFromString(page("1000", "30", "NaN", "2"), null, null));
		assertNull(LoanBook.createLoanFromString(page("1000", "30", "Infinity", "2"), null, null));
		assertNull(LoanBook.createLoanFromString(page("1000", "30", "6", "NaN"), null, null));
	}

	@Test
	void interestAndFeesAboveTheLimitsAreRejected() {
		// 70,000,000% a week turned a 1 denar loan into 100,000 denars of interest the next day.
		assertNull(LoanBook.createLoanFromString(page("1", "1", "70000000", "0"), null, null));
		assertNull(LoanBook.createLoanFromString(page("1000", "30", "100.01", "2"), null, null));
		assertNull(LoanBook.createLoanFromString(page("1000", "30", "6", "100.01"), null, null));
		assertNotNull(LoanBook.createLoanFromString(page("1000", "30", "100", "100"), null, null));
	}

	@Test
	void durationMustBeOneToAYear() {
		assertNull(LoanBook.createLoanFromString(page("1000", "0", "6", "2"), null, null));
		assertNull(LoanBook.createLoanFromString(page("1000", "366", "6", "2"), null, null));
		assertNull(LoanBook.createLoanFromString(page("1000", "1e10", "6", "2"), null, null));
		assertNotNull(LoanBook.createLoanFromString(page("1000", "365", "6", "2"), null, null));
	}

	@Test
	void aLoanWithoutAnIdGetsARandomOne() {
		Loan loan = LoanBook.createLoanFromString(page("1000", "30", "6", "2"), null, null);

		assertNotNull(loan.getId());
	}
}
