package net.tfminecraft.simplefactions.settlement.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;

class SettlementRenameTest {

	@Test
	void rename_changesNameAndKeepsId() {
		SettlementHandler handler = new SettlementHandler(mock(Faction.class));
		Settlement settlement = handler.found("Arclock Cove", 395, 0, 0).getSettlement();

		CapitalResult result = handler.rename(395, "The_Ashen_Rose", false);

		assertTrue(result.isSuccess());
		assertTrue(settlement.getName().endsWith("The Ashen Rose"));
		assertEquals("Arclock_Cove", settlement.getId());
		assertSame(settlement, handler.getById("Arclock_Cove"));
		assertSame(settlement, handler.getByProvince(395));
	}

	@Test
	void rename_dryRunLeavesNameAlone() {
		SettlementHandler handler = new SettlementHandler(mock(Faction.class));
		Settlement settlement = handler.found("Arclock Cove", 395, 0, 0).getSettlement();

		assertTrue(handler.rename(395, "The_Ashen_Rose", true).isSuccess());

		assertTrue(settlement.getName().endsWith("Arclock Cove"));
	}

	@Test
	void rename_refusesAnotherSettlementsId() {
		SettlementHandler handler = new SettlementHandler(mock(Faction.class));
		handler.found("Arclock Cove", 395, 0, 0);
		handler.found("Fort Yuri", 393, 0, 0);

		assertFalse(handler.rename(395, "Fort_Yuri", false).isSuccess());
	}

	@Test
	void rename_refusesSameNameAndMissingSettlement() {
		SettlementHandler handler = new SettlementHandler(mock(Faction.class));
		handler.found("Arclock Cove", 395, 0, 0);

		assertFalse(handler.rename(395, "Arclock_Cove", false).isSuccess());
		assertFalse(handler.rename(396, "Elsewhere", false).isSuccess());
	}
}
