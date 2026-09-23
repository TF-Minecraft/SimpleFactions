package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import net.tfminecraft.simplefactions.objects.Bank;

class GuildBankTest {

	@Test
	void missingBankIsNotBankruptcyAndDoesNotThrow() {
		Guild guild = mock(Guild.class, Answers.CALLS_REAL_METHODS);
		assertFalse(guild.isBankrupt());
	}

	@Test
	void negativeBalanceIsBankruptcy() {
		Guild guild = mock(Guild.class, Answers.CALLS_REAL_METHODS);
		Bank bank = mock(Bank.class);
		when(bank.getWealth()).thenReturn(-1.0);
		guild.setBank(bank);
		assertTrue(guild.isBankrupt());

		when(bank.getWealth()).thenReturn(0.0);
		assertFalse(guild.isBankrupt());

		guild.setBank(null);
		assertFalse(guild.isBankrupt());
	}
}
