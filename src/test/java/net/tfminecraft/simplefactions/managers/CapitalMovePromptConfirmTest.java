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
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.TestTreasuries;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;

class CapitalMovePromptConfirmTest {
	private static final int CAPITAL = 395;

	private FactionDomainFixture fixture;
	private Player player;
	private Faction faction;
	private Bank bank;
	private Settlement capital;

	@BeforeEach
	void setUp() {
        fixture = new FactionDomainFixture();
        fixture.provincesEnabled(true);
        fixture.provinceData.put(CAPITAL, new Province(CAPITAL, "PLAINS", 50));
        fixture.provinceData.put(393, new Province(393, "PLAINS", 50));
        var data = fixture.data("realm", "leader");
        data.provinces.addAll(java.util.List.of(CAPITAL, 393));
        data.capital = CAPITAL;
        faction = fixture.saved(data);
        faction.getOrCreateMainGuild();
        bank = faction.getBank();
        capital = faction.getSettlementHandler().found("Arclock Cove", CAPITAL, 0, 0).getSettlement();
        faction.getSettlementHandler().found("Fort Yuri", 393, 0, 0);
        player = fixture.player("leader");
	}

	@AfterEach
	void tearDown() {
		fixture.close();
	}

	/** The pending rename as begin() stores it, priced when the menu opens. */
	private CapitalMovePending renameTo(String name) {
		return new CapitalMovePending(
				faction, CAPITAL, CAPITAL, name, true, CapitalMovePrompt.costFor(faction));
	}

	@Test
	void laterRenameChargesTheShownPriceAndCountsIt() {
		faction.setCapitalMoves(1);
		TestTreasuries.set(bank, 500.0);

		assertEquals(Outcome.CHANGED, CapitalMovePrompt.confirm(player, renameTo("The_Ashen_Rose"), true));

		assertTrue(faction.getSettlementHandler().getByProvince(CAPITAL).getName().endsWith("The Ashen Rose"));
		assertEquals(500.0 - Cache.capitalMoveCost, bank.getWealth());
		assertEquals(2, faction.getCapitalMoves());
	}

	@Test
	void firstRenameIsFreeAndCounted() {
		faction.setCapitalMoves(0);

		assertEquals(Outcome.CHANGED, CapitalMovePrompt.confirm(player, renameTo("The_Ashen_Rose"), true));

		assertEquals(0.0, bank.getWealth());
		assertEquals(1, faction.getCapitalMoves());
	}

	@Test
	void cancelChargesNothing() {
		faction.setCapitalMoves(1);
		TestTreasuries.set(bank, 500.0);

		assertEquals(Outcome.CANCELLED, CapitalMovePrompt.confirm(player, renameTo("The_Ashen_Rose"), false));

		assertUnchangedAndUncharged();
        assertEquals(500.0, bank.getWealth());
	}

	@Test
	void bankEmptiedWhileOpenChargesNothing() {
		faction.setCapitalMoves(1);
		CapitalMovePending state = renameTo("The_Ashen_Rose");
		TestTreasuries.set(bank, 10.0);

		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, state, true));

		assertUnchangedAndUncharged();
        assertEquals(10.0, bank.getWealth());
	}

	@Test
	void priceChangedWhileOpenChargesNothing() {
		faction.setCapitalMoves(0);
		CapitalMovePending state = renameTo("The_Ashen_Rose");
		faction.setCapitalMoves(1);
		TestTreasuries.set(bank, 500.0);

		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, state, true));

		assertUnchangedAndUncharged();
        assertEquals(500.0, bank.getWealth());
	}

	@Test
	void capitalMovedWhileOpenChargesNothing() {
		faction.setCapitalMoves(1);
		TestTreasuries.set(bank, 500.0);
		CapitalMovePending state = renameTo("The_Ashen_Rose");
		faction.setCapital(393, true, false);

		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, state, true));

		assertUnchangedAndUncharged();
        assertEquals(500.0, bank.getWealth());
	}

	@Test
	void failedRenameChargesNothing() {
		faction.setCapitalMoves(1);
		TestTreasuries.set(bank, 500.0);

		// Fort Yuri's id belongs to the other settlement, so the rename fails.
		assertEquals(Outcome.REFUSED, CapitalMovePrompt.confirm(player, renameTo("Fort_Yuri"), true));

		assertUnchangedAndUncharged();
        assertEquals(500.0, bank.getWealth());
	}

	private void assertUnchangedAndUncharged() {
		assertTrue(capital.getName().endsWith("Arclock Cove"));
		assertEquals(1, faction.getCapitalMoves());
	}
}
