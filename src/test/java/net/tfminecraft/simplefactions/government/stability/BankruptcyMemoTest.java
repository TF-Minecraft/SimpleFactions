package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.ledger.Ledger;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class BankruptcyMemoTest {
	private MockedStatic<Bukkit> bukkit;
	private Ledger previousLedger;
	private final AtomicBoolean serverThread = new AtomicBoolean(true);
	private final AtomicBoolean ledgerOpen = new AtomicBoolean(true);

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
		bukkit.when(Bukkit::isPrimaryThread).thenAnswer(call -> serverThread.get());
		previousLedger = DenarEconomy.getLedger();
		Ledger ledger = mock(Ledger.class);
		when(ledger.isOpen()).thenAnswer(call -> ledgerOpen.get());
		DenarEconomy.setLedger(ledger);
	}

	@AfterEach
	void tearDown() {
		DenarEconomy.setLedger(previousLedger);
		bukkit.close();
	}

	@Test
	void aServerRunReadsEachTreasuryOnceAndANestedRunReadsItsOwn() {
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
	void anAsyncRunReadsLiveSoABankRemovedMeanwhileIsSeen() {
		serverThread.set(false);
		Guild realm = mock(Guild.class);
		AtomicBoolean hasBank = new AtomicBoolean(true);
		// isBankrupt() is false once the bank is gone, as Guild.isBankrupt() reads it.
		when(realm.isBankrupt()).thenAnswer(call -> hasBank.get());
		BankruptcyMemo.Run run = BankruptcyMemo.begin();
		try {
			assertTrue(BankruptcyMemo.isBankrupt(realm));
			// The server thread runs /f delbank while the preview is still flooding.
			hasBank.set(false);
			assertFalse(BankruptcyMemo.isBankrupt(realm));
		} finally {
			BankruptcyMemo.end(run);
		}
		verify(realm, times(2)).isBankrupt();
	}

	@Test
	void anAsyncRunInsideAServerRunStillReadsLiveAndTheServerRunResumes() {
		Guild realm = mock(Guild.class);
		AtomicBoolean bankrupt = new AtomicBoolean(true);
		when(realm.isBankrupt()).thenAnswer(call -> bankrupt.get());
		BankruptcyMemo.Run outer = BankruptcyMemo.begin();
		try {
			assertTrue(BankruptcyMemo.isBankrupt(realm));
			serverThread.set(false);
			BankruptcyMemo.Run inner = BankruptcyMemo.begin();
			try {
				bankrupt.set(false);
				assertFalse(BankruptcyMemo.isBankrupt(realm));
			} finally {
				BankruptcyMemo.end(inner);
			}
			assertTrue(BankruptcyMemo.isBankrupt(realm));
		} finally {
			BankruptcyMemo.end(outer);
		}
	}

	@Test
	void aClosedLedgerDropsTheRunsAnswersAndEveryAskReadsLive() {
		Guild first = mock(Guild.class);
		Guild second = mock(Guild.class);
		AtomicBoolean firstBankrupt = new AtomicBoolean(true);
		when(first.isBankrupt()).thenAnswer(call -> firstBankrupt.get());
		// Reading the second treasury fails, and the ledger closes for good.
		when(second.isBankrupt()).thenAnswer(call -> {
			ledgerOpen.set(false);
			return false;
		});
		BankruptcyMemo.Run run = BankruptcyMemo.begin();
		try {
			assertTrue(BankruptcyMemo.isBankrupt(first));
			assertFalse(BankruptcyMemo.isBankrupt(second));
			// A closed ledger reads every balance as zero, so the first realm is no longer bankrupt.
			firstBankrupt.set(false);
			assertFalse(BankruptcyMemo.isBankrupt(first));
			assertFalse(BankruptcyMemo.isBankrupt(second));
			// Answers from before the close are gone even if the ledger came back.
			ledgerOpen.set(true);
			firstBankrupt.set(true);
			assertTrue(BankruptcyMemo.isBankrupt(first));
			firstBankrupt.set(false);
			assertTrue(BankruptcyMemo.isBankrupt(first));
		} finally {
			BankruptcyMemo.end(run);
		}
		verify(first, times(3)).isBankrupt();
		verify(second, times(2)).isBankrupt();
	}

	@Test
	void noLedgerAtAllReadsLive() {
		DenarEconomy.setLedger(null);
		Guild realm = mock(Guild.class);
		when(realm.isBankrupt()).thenReturn(true);
		BankruptcyMemo.Run run = BankruptcyMemo.begin();
		try {
			assertTrue(BankruptcyMemo.isBankrupt(realm));
			assertTrue(BankruptcyMemo.isBankrupt(realm));
		} finally {
			BankruptcyMemo.end(run);
		}
		verify(realm, times(2)).isBankrupt();
	}

	@Test
	void noServerAtAllReadsLive() {
		bukkit.when(Bukkit::getServer).thenReturn(null);
		Guild realm = mock(Guild.class);
		when(realm.isBankrupt()).thenReturn(true);
		BankruptcyMemo.Run run = BankruptcyMemo.begin();
		try {
			assertTrue(BankruptcyMemo.isBankrupt(realm));
			assertTrue(BankruptcyMemo.isBankrupt(realm));
		} finally {
			BankruptcyMemo.end(run);
		}
		verify(realm, times(2)).isBankrupt();
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
