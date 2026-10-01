package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.objects.Faction;

class RelationManagerDiplomaticCostTest {

	private List<Faction> previousFactions;

	@BeforeEach
	void clearFactions() {
		previousFactions = new ArrayList<>(FactionManager.factions);
		FactionManager.factions.clear();
	}

	@AfterEach
	void restoreFactions() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(previousFactions);
	}

	@Test
	void relationCost_usesDiminishingReturnsAbove100Prestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		RelationType type = mock(RelationType.class);
		when(type.getBaseCost()).thenReturn(1.0);
		when(type.isSettable()).thenReturn(true);
		when(type.isVassalage()).thenReturn(false);
		assertEquals(Math.sqrt(200.0), RelationManager.getDiplomaticCost(origin, target, type), 1e-9);
	}

	@Test
	void attitudeCost_usesDiminishingReturnsAbove100Prestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		Attitude attitude = mock(Attitude.class);
		when(attitude.getBaseCost()).thenReturn(0.25);
		assertEquals(0.25 * Math.sqrt(200.0), RelationManager.getDiplomaticCost(origin, target, attitude), 1e-9);
	}

	@Test
	void relationCost_preservesLinearScalingThrough100Prestige() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(100.0);
		RelationType type = mock(RelationType.class);
		when(type.getBaseCost()).thenReturn(3.5);
		when(type.isSettable()).thenReturn(true);
		when(type.isVassalage()).thenReturn(false);
		assertEquals(35.0, RelationManager.getDiplomaticCost(origin, target, type), 1e-9);
	}

	@Test
	void zeroAttitudeCost_isZero() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(target.getPrestige()).thenReturn(200.0);
		Attitude attitude = mock(Attitude.class);
		when(attitude.getBaseCost()).thenReturn(0.0);
		assertEquals(0.0, RelationManager.getDiplomaticCost(origin, target, attitude), 1e-9);
	}

	@Test
	void blocScaling_raisesCostByTheBlocsShareOfAllPrestige() {
		Faction origin = factionWithPrestige(300.0);
		Faction target = factionWithPrestige(900.0);
		factionWithPrestige(1200.0);
		RelationType type = allianceType(1.5, 8.0);
		// The two hold 1200 of 2400 prestige, so the multiplier is 1 + 8 * 0.5.
		assertEquals(1.5 * Math.sqrt(900.0) * 5.0, RelationManager.getDiplomaticCost(origin, target, type), 1e-9);
	}

	@Test
	void blocScaling_chargesSmallBlocsLessThanLargeOnes() {
		Faction small = factionWithPrestige(400.0);
		Faction alsoSmall = factionWithPrestige(400.0);
		Faction large = factionWithPrestige(3000.0);
		Faction alsoLarge = factionWithPrestige(3000.0);
		RelationType type = allianceType(1.5, 8.0);
		double smallCost = RelationManager.getDiplomaticCost(small, alsoSmall, type);
		double largeCost = RelationManager.getDiplomaticCost(large, alsoLarge, type);
		// Without bloc scaling the large pair would pay sqrt(3000/400) times more; with it they pay more still.
		assertTrue(largeCost / smallCost > Math.sqrt(3000.0 / 400.0) * 2);
	}

	@Test
	void blocScaling_isIgnoredForRelationsWithoutIt() {
		Faction origin = factionWithPrestige(300.0);
		Faction target = factionWithPrestige(900.0);
		RelationType type = allianceType(2.0, 0.0);
		assertEquals(2.0 * Math.sqrt(900.0), RelationManager.getDiplomaticCost(origin, target, type), 1e-9);
	}

	@Test
	void blocShare_ignoresNegativePrestigeAndNeverExceedsOne() {
		Faction origin = factionWithPrestige(500.0);
		Faction target = factionWithPrestige(500.0);
		factionWithPrestige(-200.0);
		assertEquals(1.0, RelationManager.blocShare(origin, target), 1e-9);
	}

	@Test
	void blocShare_isZeroWhenNoFactionHasPrestige() {
		Faction origin = factionWithPrestige(0.0);
		Faction target = factionWithPrestige(0.0);
		assertEquals(0.0, RelationManager.blocShare(origin, target), 1e-9);
	}

	private static Faction factionWithPrestige(double prestige) {
		Faction faction = mock(Faction.class);
		when(faction.getPrestige()).thenReturn(prestige);
		FactionManager.factions.add(faction);
		return faction;
	}

	private static RelationType allianceType(double baseCost, double blocScaling) {
		RelationType type = mock(RelationType.class);
		when(type.getBaseCost()).thenReturn(baseCost);
		when(type.getBlocScaling()).thenReturn(blocScaling);
		when(type.isSettable()).thenReturn(true);
		when(type.isVassalage()).thenReturn(false);
		return type;
	}
}
