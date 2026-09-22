package me.Plugins.SimpleFactions.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.enums.FactionModifiers;

class FactionModifierResolveTest {

	@Test
	void stringParse_staysFlat() {
		FactionModifier mod = new FactionModifier("diplomatic_capacity_multiplier(10)");
		Faction owner = mock(Faction.class);
		when(owner.getPrestige()).thenReturn(100.0);
		assertEquals(10.0, mod.resolve(owner), 1e-9);
		assertEquals(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER, mod.getType());
	}

	@Test
	void relativePrestige_usesPartner() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("type", "diplomatic_capacity_multiplier");
		map.put("scale", "relative_prestige");
		map.put("at_weaker", -4);
		map.put("at_equal", 10);
		map.put("at_stronger", 20);
		FactionModifier template = FactionModifier.fromYamlEntry(map);
		Faction them = mock(Faction.class);
		when(them.getPrestige()).thenReturn(100.0);
		Faction us = mock(Faction.class);
		when(us.getPrestige()).thenReturn(100.0);
		FactionModifier tagged = new FactionModifier(them, template);
		assertEquals(10.0, tagged.resolve(us), 1e-9);
	}

	@Test
	void relativePrestige_slightlyAboveEqual_isNotCap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("type", "diplomatic_capacity_multiplier");
		map.put("scale", "relative_prestige");
		map.put("at_weaker", -4);
		map.put("at_equal", 10);
		map.put("at_stronger", 20);
		FactionModifier template = FactionModifier.fromYamlEntry(map);
		Faction them = mock(Faction.class);
		when(them.getPrestige()).thenReturn(101.0);
		Faction us = mock(Faction.class);
		when(us.getPrestige()).thenReturn(100.0);
		double value = new FactionModifier(them, template).resolve(us);
		assertTrue(value > 10.0);
		assertTrue(value < 11.0);
	}
}
