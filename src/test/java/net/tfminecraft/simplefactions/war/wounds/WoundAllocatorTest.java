package net.tfminecraft.simplefactions.war.wounds;

import static net.tfminecraft.simplefactions.war.wounds.TestForces.faction;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.law;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.levy;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.mercenary;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.militia;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.paid;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WoundAllocatorTest {
	@Test
	void forcesShareDeathsByTheLivesTheyBrought() {
		// 80 faction lives and 20 mercenary lives share 60 deaths 48 / 12.
		List<Wound> wounds = WoundAllocator.allocate(60, List.of(faction("atk", paid("professional", 16)),
				mercenary("guild", 4)));

		assertEquals(List.of(new Wound(ForceKind.REGIMENTS, "atk", "professional", 0, 48),
				new Wound(ForceKind.MERCENARY, "guild", "mercenary", 0, 12)), wounds);
	}

	@Test
	void militiaFallsFirstInsideAForceThenTheRestInProportion() {
		// The overlord brings 6 militia + 10 professional (80 lives), the levy 3 militia + 1 professional (20).
		List<Wound> wounds = WoundAllocator.allocate(60, List.of(
				faction("overlord", militia(6, 0), paid("professional", 10)),
				levy("subject", militia(3, 0), paid("professional", 1))));

		assertEquals(List.of(new Wound(ForceKind.REGIMENTS, "overlord", "militia", 30, 0),
				new Wound(ForceKind.REGIMENTS, "overlord", "professional", 0, 18),
				new Wound(ForceKind.LEVY, "subject", "militia", 12, 0)), wounds);
	}

	@Test
	void lawAndPaidSlotsShareARegimentsWoundsAndOnlyPaidOnesStay() {
		// 6 law + 4 paid militia, 30 deaths: 18 on law slots (gone after the battle), 12 on paid.
		List<Wound> wounds = WoundAllocator.allocate(30, List.of(faction("f", militia(6, 4))));

		assertEquals(List.of(new Wound(ForceKind.REGIMENTS, "f", "militia", 18, 12)), wounds);
		assertEquals(30, wounds.get(0).lives());
	}

	@Test
	void nobodyLosesMoreThanItBroughtAndNothingMeansNothing() {
		List<Wound> wounds = WoundAllocator.allocate(500, List.of(faction("f", law("garrison", 2), paid("professional", 1))));

		assertEquals(List.of(new Wound(ForceKind.REGIMENTS, "f", "garrison", 10, 0),
				new Wound(ForceKind.REGIMENTS, "f", "professional", 0, 5)), wounds);
		assertTrue(WoundAllocator.allocate(0, List.of(faction("f", paid("p", 1)))).isEmpty());
		assertTrue(WoundAllocator.allocate(5, List.of()).isEmpty());
		assertTrue(WoundAllocator.allocate(5, null).isEmpty());
		assertTrue(WoundAllocator.allocate(5, List.of(faction("f"))).isEmpty());
	}

	@Test
	void largestRemainderSplitIsStableAndCapped() {
		assertArrayEquals(new long[] { 2, 1, 1 }, WoundAllocator.share(4, new long[] { 5, 3, 3 }));
		assertArrayEquals(new long[] { 1, 1, 0 }, WoundAllocator.share(2, new long[] { 1, 1, 1 }));
		assertArrayEquals(new long[] { 0, 0 }, WoundAllocator.share(3, new long[] { 0, 0 }));
		assertArrayEquals(new long[] { 0, 0 }, WoundAllocator.share(-1, new long[] { 1, 1 }));
		assertArrayEquals(new long[] { 1, 2 }, WoundAllocator.share(9, new long[] { 1, 2 }));
	}

	@Test
	void forcesAddUpTheirSlotsAndLives() {
		BattleForce force = faction("f", militia(2, 1), paid("p", 3));
		assertEquals(6, force.slots());
		assertEquals(30, force.lives());
		assertEquals(0, new BattleForce(ForceKind.LEVY, "x", -3, null).lives());
		assertEquals(Integer.MAX_VALUE, new BattleForce(ForceKind.LEVY, "x", Integer.MAX_VALUE,
				List.of(paid("p", 3))).lives());
		assertEquals(Integer.MAX_VALUE, BattleForce.lives(List.of(
				new BattleForce(ForceKind.LEVY, "x", Integer.MAX_VALUE, List.of(paid("p", 1))),
				new BattleForce(ForceKind.LEVY, "y", Integer.MAX_VALUE, List.of(paid("p", 1))))));
		assertEquals(6, BattleForce.regimentSlots(List.of(force, mercenary("g", 4))));
		assertEquals(0, new ForcePart("p", false, -2, -1).slots());
	}
}
