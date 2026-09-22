package me.Plugins.SimpleFactions.War.resolution;

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

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

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

	private static Faction mockFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		return faction;
	}
}
