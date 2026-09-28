package net.tfminecraft.simplefactions.guild.loans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LoanHandlerUsedOfferTest {
	private static final long LATER = System.currentTimeMillis() + 60_000L;

	@Test
	void anAgreementCanBeUsedOnce() {
		LoanHandler loans = new LoanHandler(null);

		assertTrue(loans.useOffer("offer", LATER));
		assertFalse(loans.useOffer("offer", LATER));
		assertFalse(loans.useOffer(null, LATER));
	}

	@Test
	void aUsedAgreementIsSavedAndRestored() {
		LoanHandler before = new LoanHandler(null);
		before.useOffer("offer", LATER);

		LoanHandler after = new LoanHandler(null);
		after.restoreUsedOffers(before.getUsedOffers());

		assertFalse(after.useOffer("offer", LATER));
	}

	@Test
	void anAgreementWhoseBookHasExpiredIsForgotten() {
		LoanHandler loans = new LoanHandler(null);
		loans.restoreUsedOffers(Map.of("old", System.currentTimeMillis() - 1L, "current", LATER));

		assertEquals(Map.of("current", LATER), loans.getUsedOffers());
	}

	@Test
	void anAgreementWhoseLoanIsStillOpenCannotBeUsed() {
		LoanHandler loans = new LoanHandler(null);
		loans.issueLoan(new Loan("open", 100.0, null, null, System.currentTimeMillis(), 30, 6.0, 2.0, true));

		assertFalse(loans.useOffer("open", LATER));
	}
}
