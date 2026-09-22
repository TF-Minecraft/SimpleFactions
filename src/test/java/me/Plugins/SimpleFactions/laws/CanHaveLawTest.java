package me.Plugins.SimpleFactions.laws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;

class CanHaveLawTest {

	@Test
	void emptyRequirements_defaultCompatibility_available() {
		Law law = law("trade", "free_trade", List.of(), Map.of());
		Faction faction = factionWithCurrent("trade", law("trade", "mercantilism", List.of(), Map.of()));
		assertTrue(CanHaveLaw.canHave(faction, law));
		assertNull(CanHaveLaw.blockReason(faction, law));
	}

	@Test
	void hasLaw_passesWhenCurrent() {
		Law wanted = law("borders", "open_borders", List.of("has_law free_trade"), Map.of());
		Law trade = law("trade", "free_trade", List.of(), Map.of());
		Faction faction = factionWithCurrent("trade", trade);
		assertTrue(CanHaveLaw.canHave(faction, wanted));
	}

	@Test
	void hasLaw_failsWhenMissing() {
		Law wanted = law("borders", "open_borders", List.of("has_law free_trade"), Map.of());
		Law trade = law("trade", "isolationism", List.of(), Map.of());
		Faction faction = factionWithCurrent("trade", trade);
		assertFalse(CanHaveLaw.canHave(faction, wanted));
		assertEquals("§cRequires law: free_trade.", CanHaveLaw.blockReason(faction, wanted));
	}

	@Test
	void notLaw_passesWhenAbsent() {
		Law wanted = law("borders", "open_borders", List.of("not_law isolationism"), Map.of());
		Law trade = law("trade", "free_trade", List.of(), Map.of());
		Faction faction = factionWithCurrent("trade", trade);
		assertTrue(CanHaveLaw.canHave(faction, wanted));
	}

	@Test
	void notLaw_failsWhenPresent() {
		Law wanted = law("borders", "open_borders", List.of("not_law isolationism"), Map.of());
		Law trade = law("trade", "isolationism", List.of(), Map.of());
		Faction faction = factionWithCurrent("trade", trade);
		assertFalse(CanHaveLaw.canHave(faction, wanted));
		assertEquals("§cCannot have law: isolationism.", CanHaveLaw.blockReason(faction, wanted));
	}

	@Test
	void unknownRequirement_unavailable() {
		Law wanted = law("trade", "free_trade", List.of("prestige 100"), Map.of());
		Faction faction = factionWithCurrent("trade", law("trade", "mercantilism", List.of(), Map.of()));
		assertFalse(CanHaveLaw.canHave(faction, wanted));
		assertEquals("§cInvalid law requirement.", CanHaveLaw.blockReason(faction, wanted));
	}

	@Test
	void compatibilityZero_unavailable() {
		Law wanted = law("trade", "isolationism", List.of(), Map.of("free_trade", 0));
		Law current = law("trade", "free_trade", List.of(), Map.of());
		Faction faction = factionWithCurrent("trade", current);
		assertFalse(CanHaveLaw.canHave(faction, wanted));
		assertEquals(
				"§cIncompatible with the current law in this group.",
				CanHaveLaw.blockReason(faction, wanted));
	}

	@Test
	void compatibilityPositive_available() {
		Law wanted = law("trade", "isolationism", List.of(), Map.of("free_trade", 1));
		Law current = law("trade", "free_trade", List.of(), Map.of());
		Faction faction = factionWithCurrent("trade", current);
		assertTrue(CanHaveLaw.canHave(faction, wanted));
	}

	@Test
	void currentLawItself_available() {
		Law current = law("trade", "isolationism", List.of("has_law missing"), Map.of("free_trade", 0));
		Faction faction = factionWithCurrent("trade", current);
		assertTrue(CanHaveLaw.canHave(faction, current));
	}

	@Test
	void noCurrentInGroup_skipsCompatibility() {
		Law wanted = law("trade", "isolationism", List.of(), Map.of("free_trade", 0));
		Faction faction = mock(Faction.class);
		LawHandler handler = mock(LawHandler.class);
		LawGroup group = mock(LawGroup.class);
		when(faction.getLawHandler()).thenReturn(handler);
		when(handler.getCurrentLaws()).thenReturn(List.of());
		when(handler.getGroup("trade")).thenReturn(group);
		when(group.getCurrent()).thenReturn(null);
		assertTrue(CanHaveLaw.canHave(faction, wanted));
	}

	private static Faction factionWithCurrent(String groupId, Law current) {
		Faction faction = mock(Faction.class);
		LawHandler handler = mock(LawHandler.class);
		LawGroup group = mock(LawGroup.class);
		when(faction.getLawHandler()).thenReturn(handler);
		when(handler.getCurrentLaws()).thenReturn(List.of(current));
		when(handler.getGroup(groupId)).thenReturn(group);
		when(group.getCurrent()).thenReturn(current);
		return faction;
	}

	private static Law law(String group, String id, List<String> requirements, Map<String, Integer> compatibility) {
		Law law = mock(Law.class);
		when(law.getGroup()).thenReturn(group);
		when(law.getId()).thenReturn(id);
		when(law.getRequirements()).thenReturn(requirements);
		Map<String, Integer> table = new HashMap<>(compatibility);
		when(law.getCompatibility(anyString())).thenAnswer(invocation -> {
			String other = invocation.getArgument(0);
			return table.getOrDefault(other, 1);
		});
		return law;
	}
}
