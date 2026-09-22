package me.Plugins.SimpleFactions.Managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.DiplomacyHandler;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Objects.Faction;

class RelationManagerTradeEmbargoTest {

	@Test
	void hasTradeEmbargo_trueOnlyFromOriginTowardTarget() {
		Faction owner = mock(Faction.class);
		Faction customer = mock(Faction.class);
		when(owner.getId()).thenReturn("owner");
		when(customer.getId()).thenReturn("customer");

		RelationType embargo = mock(RelationType.class);
		when(embargo.blocksShops()).thenReturn(true);

		DiplomacyHandler ownerHandler = mock(DiplomacyHandler.class);
		DiplomacyHandler customerHandler = mock(DiplomacyHandler.class);
		when(owner.getDiplomacyHandler()).thenReturn(ownerHandler);
		when(customer.getDiplomacyHandler()).thenReturn(customerHandler);
		when(ownerHandler.getTradeRelation("customer")).thenReturn(embargo);
		when(customerHandler.getTradeRelation("owner")).thenReturn(null);

		assertTrue(RelationManager.hasTradeEmbargo(owner, customer));
		assertFalse(RelationManager.hasTradeEmbargo(customer, owner));
	}

	@Test
	void hasTradeEmbargo_falseWhenMissingFactionsOrOverlay() {
		assertFalse(RelationManager.hasTradeEmbargo(null, mock(Faction.class)));
		assertFalse(RelationManager.hasTradeEmbargo(mock(Faction.class), null));

		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getId()).thenReturn("target");
		when(origin.getDiplomacyHandler()).thenReturn(null);
		assertFalse(RelationManager.hasTradeEmbargo(origin, target));

		DiplomacyHandler handler = mock(DiplomacyHandler.class);
		when(origin.getDiplomacyHandler()).thenReturn(handler);
		when(handler.getTradeRelation("target")).thenReturn(null);
		assertFalse(RelationManager.hasTradeEmbargo(origin, target));
	}
}
