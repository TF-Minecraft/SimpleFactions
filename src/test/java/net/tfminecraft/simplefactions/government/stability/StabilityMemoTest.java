package net.tfminecraft.simplefactions.government.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.objects.Faction;

class StabilityMemoTest {
	private MockedStatic<Bukkit> bukkit;
	private final AtomicBoolean serverThread = new AtomicBoolean(true);
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
	}

	@AfterEach
	void tearDown() {
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
