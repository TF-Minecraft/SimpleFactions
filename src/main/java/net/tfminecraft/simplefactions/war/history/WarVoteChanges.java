package net.tfminecraft.simplefactions.war.history;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import net.tfminecraft.simplefactions.war.core.War;

/**
 * Coalesces {@code votes_changed}: a war's first change in five seconds is sent at once, later ones
 * wait for the end of that window and go out as one event with the state as it is then. Runs on
 * the main thread: the in-game clicks, staff commands and applied web actions, and a one-second
 * timer that sends what waited.
 */
final class WarVoteChanges {
	static final long WINDOW_MILLIS = 5_000L;

	/** Real time, not the campaign clock: this only paces the posts. Tests swap it. */
	static LongSupplier clock = System::currentTimeMillis;

	private static final Map<String, Long> lastSent = new HashMap<>();
	private static final Map<String, War> waiting = new LinkedHashMap<>();

	private WarVoteChanges() {
	}

	static synchronized void changed(War war) {
		String key = WarHistoryJson.warKey(war);
		long now = clock.getAsLong();
		Long last = lastSent.get(key);
		if (last != null && now - last < WINDOW_MILLIS) {
			waiting.put(key, war);
			return;
		}
		send(key, war, now);
	}

	/** Sends every waiting war whose window has passed; forgets windows that are over. */
	static synchronized void flushDue() {
		long now = clock.getAsLong();
		Iterator<Map.Entry<String, War>> due = waiting.entrySet().iterator();
		Map<String, War> ready = new LinkedHashMap<>();
		while (due.hasNext()) {
			Map.Entry<String, War> entry = due.next();
			Long last = lastSent.get(entry.getKey());
			if (last == null || now - last >= WINDOW_MILLIS) {
				ready.put(entry.getKey(), entry.getValue());
				due.remove();
			}
		}
		lastSent.values().removeIf(last -> now - last >= WINDOW_MILLIS);
		ready.forEach((key, war) -> {
			// An ended war's last state went with war_ended.
			if (war.isActive()) {
				send(key, war, now);
			}
		});
	}

	private static void send(String key, War war, long now) {
		lastSent.put(key, now);
		WarHistoryEvents.votesChangedNow(war);
	}

	static synchronized int waitingForTests() {
		return waiting.size();
	}

	static synchronized void clear() {
		lastSent.clear();
		waiting.clear();
	}

	static synchronized void resetForTests() {
		clear();
		clock = System::currentTimeMillis;
	}
}
