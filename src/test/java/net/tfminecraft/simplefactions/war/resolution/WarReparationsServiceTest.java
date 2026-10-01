package net.tfminecraft.simplefactions.war.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;

class WarReparationsServiceTest {
	private Faction payer;
	private Faction payee;
	private List<WarReparationsObligation> obligations;

	@BeforeEach
	void setUp() {
		Cache.warReparationsIncomePercent = 25;
		Cache.warReparationsDays = 10;
		payer = mockFaction("atk");
		payee = mockFaction("def");
		obligations = new ArrayList<>();
		when(payer.getWarReparationsObligations()).thenReturn(obligations);
		doAnswer(invocation -> {
			obligations.add(invocation.getArgument(0));
			return null;
		}).when(payer).addWarReparationsObligation(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void apply_defaultsFromCache() {
		assertTrue(WarReparationsService.apply(payer, payee));
		assertEquals(1, obligations.size());
		assertEquals("def", obligations.get(0).getPayeeFactionId());
		assertEquals(25, obligations.get(0).getIncomePercent());
		assertEquals(10, obligations.get(0).getDaysRemaining());
	}

	@Test
	void apply_overridesPercentAndDays() {
		assertTrue(WarReparationsService.apply(payer, payee, 40.5, 3));
		assertEquals(40.5, obligations.get(0).getIncomePercent());
		assertEquals(3, obligations.get(0).getDaysRemaining());
	}

	@Test
	void apply_rejectsNullSameFactionAndNonPositive() {
		assertFalse(WarReparationsService.apply(null, payee));
		assertFalse(WarReparationsService.apply(payer, null));
		assertFalse(WarReparationsService.apply(payer, payer));
		assertFalse(WarReparationsService.apply(payer, payee, 0, 10));
		assertFalse(WarReparationsService.apply(payer, payee, 25, 0));
		assertTrue(obligations.isEmpty());
	}

	@Test
	void applyFromWar_attackerPaysDefenderWithCacheValues() {
		War war = new War(1, payer, payee);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);

		WarReparationsService.applyFromWar(war);

		assertEquals(1, obligations.size());
		assertEquals("def", obligations.get(0).getPayeeFactionId());
		assertEquals(25, obligations.get(0).getIncomePercent());
		assertEquals(10, obligations.get(0).getDaysRemaining());
	}

	@Test
	void apply_recursivelyAddsObligationToEveryVassalOnce() {
		Faction child = mockFaction("child");
		Faction grandchild = mockFaction("grandchild");
		List<WarReparationsObligation> childObligations = new ArrayList<>();
		List<WarReparationsObligation> grandchildObligations = new ArrayList<>();
		when(child.getWarReparationsObligations()).thenReturn(childObligations);
		when(grandchild.getWarReparationsObligations()).thenReturn(grandchildObligations);
		doAnswer(invocation -> {
			childObligations.add(invocation.getArgument(0));
			return null;
		}).when(child).addWarReparationsObligation(org.mockito.ArgumentMatchers.any());
		doAnswer(invocation -> {
			grandchildObligations.add(invocation.getArgument(0));
			return null;
		}).when(grandchild).addWarReparationsObligation(org.mockito.ArgumentMatchers.any());

		try (MockedStatic<RelationManager> relations = org.mockito.Mockito.mockStatic(RelationManager.class)) {
			relations.when(() -> RelationManager.getSubjects(payer)).thenReturn(List.of(child));
			relations.when(() -> RelationManager.getSubjects(child)).thenReturn(List.of(grandchild));
			relations.when(() -> RelationManager.getSubjects(grandchild)).thenReturn(List.of(payer));
			assertTrue(WarReparationsService.apply(payer, payee));
		}

		assertEquals(1, obligations.size());
		assertEquals(1, childObligations.size());
		assertEquals(1, grandchildObligations.size());
		assertEquals("def", grandchildObligations.get(0).getPayeeFactionId());
	}

	private static Faction mockFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		return faction;
	}
}
