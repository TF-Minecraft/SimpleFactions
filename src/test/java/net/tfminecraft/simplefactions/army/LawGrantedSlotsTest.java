package net.tfminecraft.simplefactions.army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawEffect;

class LawGrantedSlotsTest {

	@Test
	void ownLawsGrantFactionScopeOnly() {
		Law centralized = law(Scope.FACTION, "professional", 2);
		Law levyFocus = law(Scope.VASSALS, "professional", 2);

		Map<String, Integer> granted = Military.grantedSlots(List.of(centralized, levyFocus), List.of());

		assertEquals(Map.of("professional", 2), granted);
	}

	@Test
	void overlordLawsGrantVassalScopeOnly() {
		Law overlordLevyFocus = law(Scope.VASSALS, "professional", 2);
		Law overlordDecentralized = law(Scope.FACTION, "militia", 6);
		Law ownDecentralized = law(Scope.FACTION, "militia", 6);

		Map<String, Integer> granted = Military.grantedSlots(
				List.of(ownDecentralized), List.of(overlordLevyFocus, overlordDecentralized));

		assertEquals(Map.of("militia", 6, "professional", 2), granted);
	}

	@Test
	void noRegimentLawsGrantNothing() {
		assertTrue(Military.grantedSlots(List.of(mock(Law.class)), null).isEmpty());
	}

	@Test
	void switchingLawsSwapsGrantedSlotsAndKeepsPaid() {
		Regiment militia = slots(9, 6);

		militia.setGrantedSlots(0);
		assertEquals(3, militia.getCurrentSlots());
		assertEquals(0, militia.getFreeSlots());

		militia.setGrantedSlots(6);
		assertEquals(9, militia.getCurrentSlots());
		assertEquals(6, militia.getFreeSlots());
		assertEquals(3, militia.getPaidSlots());
	}

	@Test
	void grantAddsSlotsOnTopOfRecruited() {
		Regiment professional = slots(3, 0);

		professional.setGrantedSlots(2);

		assertEquals(5, professional.getCurrentSlots());
		assertEquals(2, professional.getFreeSlots());
		assertEquals(3, professional.getPaidSlots());
	}

	private static Regiment slots(int current, int free) {
		Regiment regiment = mock(Regiment.class, CALLS_REAL_METHODS);
		regiment.setCurrentSlots(current);
		regiment.setFreeSlots(free);
		return regiment;
	}

	private static Law law(Scope scope, String regimentId, int amount) {
		Regiment regiment = mock(Regiment.class);
		when(regiment.getId()).thenReturn(regimentId);
		Map<Regiment, Integer> regiments = new LinkedHashMap<>();
		regiments.put(regiment, amount);
		LawEffect effect = mock(LawEffect.class);
		when(effect.hasRegiments()).thenReturn(true);
		when(effect.getRegiments()).thenReturn(regiments);
		Law law = mock(Law.class);
		when(law.getScopedEffects()).thenReturn(Map.of(scope, effect));
		return law;
	}
}
