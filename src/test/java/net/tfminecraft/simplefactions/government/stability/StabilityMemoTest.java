package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.objects.Faction;

class StabilityMemoTest {

	@Test
	void aRunAssessesEachRealmOnceAndANestedRunKeepsItsOwnReports() {
		Faction realm = mock(Faction.class);
		Faction other = mock(Faction.class);
		AtomicInteger assessed = new AtomicInteger();
		StabilityMemo.Run outer = StabilityMemo.begin();
		try {
			StabilityReport first = StabilityMemo.report(realm, () -> fresh(assessed));
			assertSame(first, StabilityMemo.report(realm, () -> fresh(assessed)));
			StabilityMemo.Run inner = StabilityMemo.begin();
			StabilityReport nestedRun;
			try {
				nestedRun = StabilityMemo.report(realm, () -> fresh(assessed));
				assertNotSame(first, nestedRun);
				assertSame(nestedRun, StabilityMemo.report(realm, () -> fresh(assessed)));
			} finally {
				StabilityMemo.end(inner);
			}
			assertSame(first, StabilityMemo.report(realm, () -> fresh(assessed)));
			// Assessing one realm may ask about another while the first is still being assessed.
			StabilityReport nested = StabilityMemo.report(other, () -> {
				StabilityMemo.report(realm, () -> fresh(assessed));
				return fresh(assessed);
			});
			assertSame(nested, StabilityMemo.report(other, () -> fresh(assessed)));
			assertNotSame(StabilityMemo.report(null, () -> fresh(assessed)), StabilityMemo.report(null, () -> fresh(assessed)));
			assertEquals(5, assessed.get());
		} finally {
			StabilityMemo.end(outer);
		}
		assertNotSame(StabilityMemo.report(realm, () -> fresh(assessed)), StabilityMemo.report(realm, () -> fresh(assessed)));
	}

	private static StabilityReport fresh(AtomicInteger assessed) {
		assessed.incrementAndGet();
		return new StabilityReport();
	}
}
