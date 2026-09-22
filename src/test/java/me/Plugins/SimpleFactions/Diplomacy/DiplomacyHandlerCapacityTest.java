package me.Plugins.SimpleFactions.Diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

class DiplomacyHandlerCapacityTest {

	@Test
	void usedCapacity_usesPartnerPrestigeNotSelf() {
		Faction self = mock(Faction.class);
		when(self.getId()).thenReturn("self");
		when(self.getPrestige()).thenReturn(50.0);
		Faction partner = mock(Faction.class);
		when(partner.getId()).thenReturn("partner");
		when(partner.getPrestige()).thenReturn(200.0);

		RelationType type = mock(RelationType.class);
		when(type.getBaseCost()).thenReturn(1.0);
		when(type.isSettable()).thenReturn(true);
		when(type.isVassalage()).thenReturn(false);

		Attitude attitude = mock(Attitude.class);
		when(attitude.getBaseCost()).thenReturn(0.0);

		Relation relation = new Relation(type, attitude);
		DiplomacyHandler handler = new DiplomacyHandler(self);
		handler.getRelations().put("partner", relation);

		try (org.mockito.MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
				org.mockito.Mockito.mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class)) {
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("partner"))
					.thenReturn(partner);
			assertEquals(20.0, handler.getUsedDiplomaticCapacity(), 1e-9);
		}
	}

	@Test
	void attitudeModifiers_areTaggedWithPartner() {
		Faction self = mock(Faction.class);
		when(self.getId()).thenReturn("self");
		when(self.getPrestige()).thenReturn(100.0);
		Faction partner = mock(Faction.class);
		when(partner.getId()).thenReturn("partner");
		when(partner.getPrestige()).thenReturn(100.0);

		RelationType type = mock(RelationType.class);
		when(type.hasRecieveModifiers()).thenReturn(false);
		when(type.hasGiveModifiers()).thenReturn(false);

		java.util.Map<String, Object> map = new HashMap<>();
		map.put("type", "diplomatic_capacity_multiplier");
		map.put("scale", "relative_prestige");
		map.put("at_weaker", -2);
		map.put("at_equal", 4);
		map.put("at_stronger", 8);
		FactionModifier template = FactionModifier.fromYamlEntry(map);
		Attitude attitude = mock(Attitude.class);
		when(attitude.hasRecieveModifiers()).thenReturn(true);
		when(attitude.getRecieveModifiers()).thenReturn(java.util.List.of(template));
		when(partner.getRelation("self")).thenReturn(new Relation(type, attitude));

		Relation relation = new Relation(type, attitude);
		DiplomacyHandler handler = new DiplomacyHandler(self);
		handler.getRelations().put("partner", relation);

		try (org.mockito.MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
				org.mockito.Mockito.mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class)) {
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("partner"))
					.thenReturn(partner);
			factions.when(() -> me.Plugins.SimpleFactions.Managers.FactionManager.getByString("self"))
					.thenReturn(self);
			java.util.List<FactionModifier> mods = handler.getModifiers();
			assertEquals(1, mods.size());
			assertEquals(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER, mods.get(0).getType());
			assertEquals(4.0, mods.get(0).resolve(self), 1e-9);
			assertTrue(mods.get(0).getFrom() == partner);
		}
	}
}
