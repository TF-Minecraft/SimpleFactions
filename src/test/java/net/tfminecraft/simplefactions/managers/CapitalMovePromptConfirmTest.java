package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.CapitalMovePrompt.CapitalMovePending;
import net.tfminecraft.simplefactions.managers.CapitalMovePrompt.Outcome;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;

class CapitalMovePromptConfirmTest {
	private static final int CAPITAL = 395;

	private MockedStatic<FactionManager> factionManager;
	private Player player;
	private Faction faction;
	private Bank bank;
	private Settlement capital;

	@BeforeEach
	void setUp() {
		player = mock(Player.class);
		when(player.getName()).thenReturn("leader");
		faction = mock(Faction.class);
		bank = mock(Bank.class);
		when(faction.getBank()).thenReturn(bank);
		when(faction.getCapital()).thenReturn(CAPITAL);
		SettlementHandler settlements = new SettlementHandler(faction);
		capital = settlements.found("Arclock Cove", CAPITAL, 0, 0).getSettlement();
		settlements.found("Fort Yuri", 393, 0, 0);
		when(faction.getSettlementHandler()).thenReturn(settlements);

		factionManager = mockStatic(FactionManager.class);
		factionManager.when(() -> FactionManager.getByLeader("leader")).thenReturn(faction);
	}

	@AfterEach
	void tearDown() {
		factionManager.close();
	}

	/** The pending rename as begin() stores it, priced when the menu opens. */
	private CapitalMovePending renameTo(String name) {
		return new CapitalMovePending(
				faction, CAPITAL, CAPITAL, name, true, CapitalMovePrompt.costFor(faction));
	}

	@Test
	void laterRenameChargesTheShownPriceAndCountsIt() {
		when(faction.getCapitalMoves()).thenReturn(1);
		when(bank.getWealth()).thenReturn(500.0);

		assertEquals(Outcome.CHANGED, CapitalMovePrompt.confirm(player, renameTo("The_Ashen_Rose"), true));

		assertTrue(capital.getName().endsWith("The Ashen Rose"));
		verify(bank).withdraw(Cache.capitalMoveCost);
		verify(faction).setCapitalMoves(2);
	}

	@Test
	void firstRenameIsFreeAndCounted() {
		when(faction.getCapitalMoves()).thenReturn(0);

		assertEquals(Outcome.CHANGED, CapitalMovePrompt.confirm(player, renameTo("The_Ashen_Rose"), true));

		verify(bank, never()).withdraw(anyDouble());
		verify(faction).setCapitalMoves(1);
	}

	@Test
	void cancelChargesNothing() {
		when(faction.getCapitalMoves()).thenReturn(1);
		when(bank.getWealth()).thenReturn(500.0);

		assertEquals(Outcome.CANCELLED, CapitalMovePrompt.confirm(player, renameTo("The_Ashen_Rose"), false));

		assertUnchangedAndUncharged();
	}

	@Test
	void bankEmptiedWhileOpenChargesNothing() {
		when(faction.getCapitalMoves()).thenReturn(1);
		CapitalMovePending state = renameTo("The_Ashen_Rose");
		when(bank.getWealth()).thenReturn(10.0);

		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, state, true));

		assertUnchangedAndUncharged();
	}

	@Test
	void priceChangedWhileOpenChargesNothing() {
		when(faction.getCapitalMoves()).thenReturn(0);
		CapitalMovePending state = renameTo("The_Ashen_Rose");
		when(faction.getCapitalMoves()).thenReturn(1);
		when(bank.getWealth()).thenReturn(500.0);

		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, state, true));

		assertUnchangedAndUncharged();
	}

	@Test
	void capitalMovedWhileOpenChargesNothing() {
		when(faction.getCapitalMoves()).thenReturn(1);
		when(bank.getWealth()).thenReturn(500.0);
		CapitalMovePending state = renameTo("The_Ashen_Rose");
		when(faction.getCapital()).thenReturn(393);

		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, state, true));

		assertUnchangedAndUncharged();
	}

	@Test
	void failedRenameChargesNothing() {
		when(faction.getCapitalMoves()).thenReturn(1);
		when(bank.getWealth()).thenReturn(500.0);

		// Fort Yuri's id belongs to the other settlement, so the rename fails.
		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, renameTo("Fort_Yuri"), true));

		assertUnchangedAndUncharged();
	}

	private void assertUnchangedAndUncharged() {
		assertTrue(capital.getName().endsWith("Arclock Cove"));
		verify(bank, never()).withdraw(anyDouble());
		verify(faction, never()).setCapitalMoves(anyInt());
	}
}
