package me.Plugins.SimpleFactions.Diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Diplomacy.DiplomacyQueries.DiplomacyListEntry;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

class DiplomacyQueriesTest {

	@Test
	void hasOfficialRelation_falseForNullSelfAndDefaultType() {
		Faction origin = faction("a", "Alpha");
		Faction target = faction("b", "Beta");
		Relation defaultRelation = relation(true);
		DiplomacyHandler diplomacy = handler(false, false);
		when(origin.getRelation("b")).thenReturn(defaultRelation);
		when(origin.getDiplomacyHandler()).thenReturn(diplomacy);

		assertFalse(DiplomacyQueries.hasOfficialRelation(null, target));
		assertFalse(DiplomacyQueries.hasOfficialRelation(origin, null));
		assertFalse(DiplomacyQueries.hasOfficialRelation(origin, origin));
		assertFalse(DiplomacyQueries.hasOfficialRelation(origin, target));
	}

	@Test
	void hasOfficialRelation_trueForNonDefaultType() {
		Faction origin = faction("a", "Alpha");
		Faction target = faction("b", "Beta");
		Relation ally = relation(false);
		DiplomacyHandler diplomacy = handler(false, false);
		when(origin.getRelation("b")).thenReturn(ally);
		when(origin.getDiplomacyHandler()).thenReturn(diplomacy);

		assertTrue(DiplomacyQueries.hasOfficialRelation(origin, target));
	}

	@Test
	void hasOfficialRelation_ignoresAttitudeOnlyDefault() {
		Faction origin = faction("a", "Alpha");
		Faction target = faction("b", "Beta");
		Relation defaultRelation = relation(true);
		DiplomacyHandler diplomacy = handler(false, false);
		when(origin.getRelation("b")).thenReturn(defaultRelation);
		when(origin.getDiplomacyHandler()).thenReturn(diplomacy);

		assertFalse(DiplomacyQueries.hasOfficialRelation(origin, target));
	}

	@Test
	void hasOfficialRelation_trueForTradeOrTreatyOnDefaultType() {
		Faction origin = faction("a", "Alpha");
		Faction target = faction("b", "Beta");
		Relation defaultRelation = relation(true);
		when(origin.getRelation("b")).thenReturn(defaultRelation);

		DiplomacyHandler trade = handler(true, false);
		when(origin.getDiplomacyHandler()).thenReturn(trade);
		assertTrue(DiplomacyQueries.hasOfficialRelation(origin, target));

		DiplomacyHandler treaty = handler(false, true);
		when(origin.getDiplomacyHandler()).thenReturn(treaty);
		assertTrue(DiplomacyQueries.hasOfficialRelation(origin, target));
	}

	@Test
	void officialPartners_sortedAndExcludesDefault() {
		Faction origin = faction("a", "Alpha");
		Faction zeta = faction("z", "Zeta");
		Faction beta = faction("b", "Beta");
		Faction gamma = faction("g", "Gamma");

		Relation ally = relation(false);
		Relation none = relation(true);
		when(origin.getRelation("z")).thenReturn(ally);
		when(origin.getRelation("b")).thenReturn(ally);
		when(origin.getRelation("g")).thenReturn(none);
		DiplomacyHandler diplomacy = handler(false, false);
		when(origin.getDiplomacyHandler()).thenReturn(diplomacy);

		List<Faction> previous = FactionManager.factions;
		FactionManager.factions = new ArrayList<>(List.of(origin, zeta, beta, gamma));
		try {
			List<Faction> partners = DiplomacyQueries.officialPartners(origin);
			assertEquals(List.of(beta, zeta), partners);
		} finally {
			FactionManager.factions = previous;
		}
	}

	@Test
	void ownDirectory_relatedThenSeparatorsThenOthers() {
		Faction origin = faction("a", "Alpha");
		Faction allyFaction = faction("b", "Beta");
		Faction noneFaction = faction("c", "Charlie");

		Relation ally = relation(false);
		Relation none = relation(true);
		when(origin.getRelation("b")).thenReturn(ally);
		when(origin.getRelation("c")).thenReturn(none);
		DiplomacyHandler diplomacy = handler(false, false);
		when(origin.getDiplomacyHandler()).thenReturn(diplomacy);

		List<Faction> previous = FactionManager.factions;
		FactionManager.factions = new ArrayList<>(List.of(origin, allyFaction, noneFaction));
		try {
			List<DiplomacyListEntry> entries = DiplomacyQueries.ownDirectory(origin);
			assertEquals(2 + DiplomacyQueries.SEPARATOR_COUNT, entries.size());
			assertEquals(allyFaction, entries.get(0).getFaction());
			for (int i = 1; i <= DiplomacyQueries.SEPARATOR_COUNT; i++) {
				assertTrue(entries.get(i).isSeparator());
			}
			assertEquals(noneFaction, entries.get(1 + DiplomacyQueries.SEPARATOR_COUNT).getFaction());
		} finally {
			FactionManager.factions = previous;
		}
	}

	private static Faction faction(String id, String name) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(name);
		return faction;
	}

	private static Relation relation(boolean defaultType) {
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.isDefault()).thenReturn(defaultType);
		when(relation.getType()).thenReturn(type);
		return relation;
	}

	private static DiplomacyHandler handler(boolean trade, boolean treaty) {
		DiplomacyHandler handler = mock(DiplomacyHandler.class);
		when(handler.hasTradeRelation("b")).thenReturn(trade);
		when(handler.hasTreatyRelation("b")).thenReturn(treaty);
		return handler;
	}
}
