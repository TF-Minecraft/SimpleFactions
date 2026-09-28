package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BankAmountTest {

	@Test
	void acceptsPositiveWholeCents() {
		assertEquals(10.0, BankAmount.parse("10"));
		assertEquals(2.5, BankAmount.parse("2.50"));
		assertEquals(0.01, BankAmount.parse(" 0.01 "));
		assertEquals(1.0, BankAmount.parse("1.000"));
	}

	@Test
	void rejectsFractionsOfACent() {
		// Each 0.005 rounded to nothing on one side and 0.01 on the other.
		assertNull(BankAmount.parse("0.005"));
		assertNull(BankAmount.parse("1e-3"));
		assertNull(BankAmount.parse("10.001"));
	}

	@Test
	void rejectsAmountsTooLargeToKeepTheirCents() {
		assertNull(BankAmount.parse("12345678901234567.89"));
		assertEquals(1_000_000_000.25, BankAmount.parse("1000000000.25"));
	}

	@Test
	void rejectsAnythingElse() {
		for (String text : new String[] { "0", "-5", "NaN", "Infinity", "abc", "", "1e400", null }) {
			assertNull(BankAmount.parse(text), text);
		}
	}
}
