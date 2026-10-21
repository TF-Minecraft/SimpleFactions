package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class BankruptcyMemoTest {

	@Test
	void aRunReadsEachTreasuryOnceAndANestedRunReadsItsOwn() {
		Guild realm = mock(Guild.class);
		AtomicBoolean bankrupt = new AtomicBoolean(true);
		when(realm.isBankrupt()).thenAnswer(call -> bankrupt.get());
		BankruptcyMemo.Run outer = BankruptcyMemo.begin();
		try {
			assertTrue(BankruptcyMemo.isBankrupt(realm));
			bankrupt.set(false);
			assertTrue(BankruptcyMemo.isBankrupt(realm));
			BankruptcyMemo.Run inner = BankruptcyMemo.begin();
			try {
				assertFalse(BankruptcyMemo.isBankrupt(realm));
				bankrupt.set(true);
				assertFalse(BankruptcyMemo.isBankrupt(realm));
			} finally {
				BankruptcyMemo.end(inner);
			}
			assertTrue(BankruptcyMemo.isBankrupt(realm));
			verify(realm, times(2)).isBankrupt();
		} finally {
			BankruptcyMemo.end(outer);
		}
		bankrupt.set(false);
		assertFalse(BankruptcyMemo.isBankrupt(realm));
		bankrupt.set(true);
		assertTrue(BankruptcyMemo.isBankrupt(realm));
		verify(realm, times(4)).isBankrupt();
	}

	@Test
	void unrestThatDecaysDuringARunStillChangesStabilityBetweenProvinces() {
		long[] now = {0};
		Guild realm = mock(Guild.class);
		Faction faction = realmDecaying(realm, now);

		// Unmemoised: what each province would have seen before.
		StabilityReport liveEarly = StateStability.of(faction);
		now[0] = 1;
		StabilityReport liveLate = StateStability.of(faction);
		assertNotEquals(liveEarly.stability, liveLate.stability);

		now[0] = 0;
		BankruptcyMemo.Run run = BankruptcyMemo.begin();
		try {
			StabilityReport early = StateStability.of(faction);
			now[0] = 1;
			StabilityReport late = StateStability.of(faction);
			assertEquals(liveEarly.stability, early.stability);
			assertEquals(liveLate.stability, late.stability);
			assertEquals(liveEarly.status, early.status);
			assertEquals(liveLate.status, late.status);
		} finally {
			BankruptcyMemo.end(run);
		}
		// Two live asks read the treasury twice; the run read it once for both of its asks.
		verify(realm, times(3)).isBankrupt();
	}

	/** A realm whose office unrest wears off as {@code now} advances. */
	private static Faction realmDecaying(Guild realm, long[] now) {
		Faction faction = mock(Faction.class);
		Government government = mock(Government.class);
		when(faction.getId()).thenReturn("decaying");
		when(faction.getProvinces()).thenReturn(new ArrayList<>(List.of(1)));
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getOrCreateMainGuild()).thenReturn(realm);
		when(faction.getGovernment()).thenReturn(government);
		when(government.getStabilityModifiers())
				.thenAnswer(call -> List.of(new StabilityModifier("Office unrest", -50 + 7.5 * now[0], 0)));
		when(realm.isBankrupt()).thenReturn(false);
		return faction;
	}
}
