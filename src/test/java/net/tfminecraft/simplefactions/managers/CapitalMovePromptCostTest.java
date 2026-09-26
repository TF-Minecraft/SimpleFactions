package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

class CapitalMovePromptCostTest {

	@Test
	void firstChangeIsFreeAndLaterOnesCost() {
		Faction faction = mock(Faction.class);
		when(faction.getCapitalMoves()).thenReturn(0);
		assertEquals(0.0, CapitalMovePrompt.costFor(faction));

		when(faction.getCapitalMoves()).thenReturn(1);
		assertEquals(Cache.capitalMoveCost, CapitalMovePrompt.costFor(faction));
	}

	@Test
	void freeChangeNeedsNoBank() {
		Faction faction = mock(Faction.class);
		when(faction.getBank()).thenReturn(null);

		assertNull(CapitalMovePrompt.checkFunds(faction, 0));
		assertNotNull(CapitalMovePrompt.checkFunds(faction, 100));
	}

	@Test
	void paidChangeNeedsEnoughInTheFactionBank() {
		Faction faction = mock(Faction.class);
		Bank bank = mock(Bank.class);
		when(faction.getBank()).thenReturn(bank);

		when(bank.getWealth()).thenReturn(99.99);
		assertNotNull(CapitalMovePrompt.checkFunds(faction, 100));

		when(bank.getWealth()).thenReturn(100.0);
		assertNull(CapitalMovePrompt.checkFunds(faction, 100));
	}
}
