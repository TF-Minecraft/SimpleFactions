package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.ledger.Ledger;
import net.tfminecraft.simplefactions.objects.Faction;

class StabilityMemoTest {
	private MockedStatic<Bukkit> bukkit;
	private Ledger previousLedger;
	private final AtomicBoolean serverThread = new AtomicBoolean(true);
	private final AtomicBoolean ledgerOpen = new AtomicBoolean(true);
	private final AtomicInteger builds = new AtomicInteger();
	private final Supplier<StabilityReport> build = () -> {
		builds.incrementAndGet();
		return new StabilityReport();
	};

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
	void aServerRunBuildsEachReportOnceAndANestedRunBuildsItsOwn() {
		Faction faction = mock(Faction.class);
		Faction other = mock(Faction.class);
		StabilityMemo.Run outer = StabilityMemo.begin();
		StabilityReport first;
		try {
			first = StabilityMemo.report(faction, build);
			assertSame(first, StabilityMemo.report(faction, build));
			assertNotSame(first, StabilityMemo.report(other, build));
			assertEquals(2, builds.get());
			StabilityMemo.Run inner = StabilityMemo.begin();
			try {
				StabilityReport nested = StabilityMemo.report(faction, build);
				assertNotSame(first, nested);
				assertSame(nested, StabilityMemo.report(faction, build));
			} finally {
				StabilityMemo.end(inner);
			}
			assertSame(first, StabilityMemo.report(faction, build));
			assertEquals(3, builds.get());
		} finally {
			StabilityMemo.end(outer);
		}
		assertNotSame(first, StabilityMemo.report(faction, build));
		assertEquals(4, builds.get());
	}

	@Test
	void offTheServerThreadAndWithoutAFactionEveryAskIsLive() {
		Faction faction = mock(Faction.class);
		StabilityMemo.Run server = StabilityMemo.begin();
		try {
			StabilityMemo.report(null, build);
			StabilityMemo.report(null, build);
			assertEquals(2, builds.get());
			serverThread.set(false);
			StabilityMemo.Run async = StabilityMemo.begin();
			try {
				assertNotSame(StabilityMemo.report(faction, build), StabilityMemo.report(faction, build));
				assertEquals(4, builds.get());
			} finally {
				StabilityMemo.end(async);
			}
			StabilityReport kept = StabilityMemo.report(faction, build);
			assertSame(kept, StabilityMemo.report(faction, build));
		} finally {
			StabilityMemo.end(server);
		}
		bukkit.when(Bukkit::getServer).thenReturn(null);
		serverThread.set(true);
		StabilityMemo.Run noServer = StabilityMemo.begin();
		try {
			assertNotSame(StabilityMemo.report(faction, build), StabilityMemo.report(faction, build));
		} finally {
			StabilityMemo.end(noServer);
		}
	}

	@Test
	void aLedgerThatClosesDropsEveryCachedReportAndReadsLiveFromThen() {
		Faction faction = mock(Faction.class);
		Faction closing = mock(Faction.class);
		StabilityMemo.Run run = StabilityMemo.begin();
		try {
			StabilityReport cached = StabilityMemo.report(faction, build);
			assertSame(cached, StabilityMemo.report(faction, build));
			// This build's treasury read closes the ledger, so its report is not kept.
			StabilityReport failed = StabilityMemo.report(closing, () -> {
				ledgerOpen.set(false);
				return build.get();
			});
			assertNotSame(failed, StabilityMemo.report(closing, build));
			assertNotSame(cached, StabilityMemo.report(faction, build), "reports from before the failure are stale");
			assertNotSame(StabilityMemo.report(faction, build), StabilityMemo.report(faction, build));
			ledgerOpen.set(true);
			StabilityReport fresh = StabilityMemo.report(faction, build);
			assertNotSame(cached, fresh);
			assertSame(fresh, StabilityMemo.report(faction, build));
		} finally {
			StabilityMemo.end(run);
		}
		DenarEconomy.setLedger(null);
		StabilityMemo.Run noLedger = StabilityMemo.begin();
		try {
			assertNotSame(StabilityMemo.report(faction, build), StabilityMemo.report(faction, build));
		} finally {
			StabilityMemo.end(noLedger);
		}
	}

	@Test
	void stateStabilityReadsThroughTheRun() {
		Faction faction = mock(Faction.class);
		StabilityMemo.Run run = StabilityMemo.begin();
		try {
			assertSame(StateStability.of(faction), StateStability.of(faction));
		} finally {
			StabilityMemo.end(run);
		}
		assertNotSame(StateStability.of(faction), StateStability.of(faction));
	}
}
