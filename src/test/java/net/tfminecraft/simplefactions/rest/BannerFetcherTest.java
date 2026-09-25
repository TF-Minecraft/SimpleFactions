package net.tfminecraft.simplefactions.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class BannerFetcherTest {

	private static final Executor DIRECT = Runnable::run;

	/** Holds tasks until run() so a test can look at the state in between. */
	private static final class Queue implements Executor {
		private final List<Runnable> tasks = new ArrayList<>();

		@Override
		public void execute(Runnable task) {
			tasks.add(task);
		}

		void run() {
			List<Runnable> pending = new ArrayList<>(tasks);
			tasks.clear();
			pending.forEach(Runnable::run);
		}
	}

	@Test
	void deliversPatternsOnTheMainExecutor() {
		Queue async = new Queue();
		Queue main = new Queue();
		AtomicReference<List<String>> delivered = new AtomicReference<>();

		assertTrue(BannerFetcher.fetch("t:deliver", delivered::set,
				() -> List.of("RED.BASE", "WHITE.CROSS"), async, main));
		assertNull(delivered.get());

		async.run();
		assertNull(delivered.get());

		main.run();
		assertEquals(List.of("RED.BASE", "WHITE.CROSS"), delivered.get());
	}

	@Test
	void secondFetchForSameKeyIsRefusedWhileRunning() {
		Queue async = new Queue();
		AtomicInteger requests = new AtomicInteger();

		assertTrue(BannerFetcher.fetch("t:dedupe", p -> {}, () -> {
			requests.incrementAndGet();
			return List.of("RED.BASE");
		}, async, DIRECT));
		assertFalse(BannerFetcher.fetch("t:dedupe", p -> {}, () -> {
			requests.incrementAndGet();
			return List.of("RED.BASE");
		}, async, DIRECT));
		assertTrue(BannerFetcher.fetch("t:dedupe-other", p -> {}, () -> List.of("RED.BASE"), async, DIRECT));

		async.run();
		assertEquals(1, requests.get());
		assertTrue(BannerFetcher.fetch("t:dedupe", p -> {}, () -> List.of("RED.BASE"), DIRECT, DIRECT));
	}

	@Test
	void keyIsHeldUntilTheMainThreadCallbackRan() {
		Queue main = new Queue();
		assertTrue(BannerFetcher.fetch("t:held", p -> {}, () -> List.of("RED.BASE"), DIRECT, main));
		assertFalse(BannerFetcher.fetch("t:held", p -> {}, () -> List.of("RED.BASE"), DIRECT, DIRECT));

		main.run();
		assertTrue(BannerFetcher.fetch("t:held", p -> {}, () -> List.of("RED.BASE"), DIRECT, DIRECT));
	}

	@Test
	void failedOrEmptyResultDeliversNull() {
		AtomicReference<List<String>> delivered = new AtomicReference<>(List.of("x"));
		BannerFetcher.fetch("t:null", delivered::set, () -> null, DIRECT, DIRECT);
		assertNull(delivered.get());

		delivered.set(List.of("x"));
		BannerFetcher.fetch("t:empty", delivered::set, List::of, DIRECT, DIRECT);
		assertNull(delivered.get());
	}

	@Test
	void keyIsReleasedWhenTheSourceThrows() {
		try {
			BannerFetcher.fetch("t:throws", p -> {}, () -> {
				throw new IllegalStateException("boom");
			}, DIRECT, DIRECT);
		} catch (IllegalStateException expected) {
			// thrown on the async thread in production; the key must still be freed
		}
		assertTrue(BannerFetcher.fetch("t:throws", p -> {}, () -> List.of("RED.BASE"), DIRECT, DIRECT));
	}

	@Test
	void placeholderIsAMutableWhiteBanner() {
		List<String> patterns = BannerFetcher.placeholder();
		assertEquals(List.of("WHITE.BASE"), patterns);
		patterns.clear();
		assertEquals(List.of("WHITE.BASE"), BannerFetcher.placeholder());
		assertTrue(BannerFetcher.isPlaceholder(BannerFetcher.placeholder()));
		assertFalse(BannerFetcher.isPlaceholder(List.of("WHITE.BASE", "RED.CROSS")));
		assertFalse(BannerFetcher.isPlaceholder(null));
	}
}
