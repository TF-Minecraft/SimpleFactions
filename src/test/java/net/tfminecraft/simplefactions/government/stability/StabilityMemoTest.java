package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.objects.Faction;

class StabilityMemoTest {

	@Test
	void aRunAssessesEachRealmOnceAndOnlyTheOuterRunEndsIt() {
		Faction realm = mock(Faction.class);
		Faction other = mock(Faction.class);
		AtomicInteger assessed = new AtomicInteger();
		boolean outer = StabilityMemo.begin();
		try {
			assertTrue(outer);
			StabilityReport first = StabilityMemo.report(realm, () -> fresh(assessed));
			assertSame(first, StabilityMemo.report(realm, () -> fresh(assessed)));
			boolean inner = StabilityMemo.begin();
			assertFalse(inner);
			StabilityMemo.end(inner);
			assertSame(first, StabilityMemo.report(realm, () -> fresh(assessed)));
			// Assessing one realm may ask about another while the first is still being assessed.
			StabilityReport nested = StabilityMemo.report(other, () -> {
				StabilityMemo.report(realm, () -> fresh(assessed));
				return fresh(assessed);
			});
			assertSame(nested, StabilityMemo.report(other, () -> fresh(assessed)));
			assertNotSame(StabilityMemo.report(null, () -> fresh(assessed)), StabilityMemo.report(null, () -> fresh(assessed)));
			assertEquals(4, assessed.get());
		} finally {
			StabilityMemo.end(outer);
		}
		assertNotSame(StabilityMemo.report(realm, () -> fresh(assessed)), StabilityMemo.report(realm, () -> fresh(assessed)));
		assertTrue(StabilityMemo.begin());
		StabilityMemo.end(true);
	}

	private static StabilityReport fresh(AtomicInteger assessed) {
		assessed.incrementAndGet();
		return new StabilityReport();
	}
}
