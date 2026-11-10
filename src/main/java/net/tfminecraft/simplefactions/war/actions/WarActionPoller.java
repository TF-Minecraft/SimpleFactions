package net.tfminecraft.simplefactions.war.actions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.api.GatewayClient;

/**
 * Fetches the website's pending actions off the main thread, applies them on it, and reports the
 * results off it again. One poll runs at a time, from fetch to report. An action already in the
 * {@link WarActionLedger} is answered with its stored result instead of being applied again.
 */
final class WarActionPoller {
	static final String PENDING_PATH = "/wars/plugin/actions/pending";
	static final String APPLIED_PATH = "/wars/plugin/actions/applied";
	static final int LIMIT = 50;

	/** One request through TFMCWeb; {@link GatewayClient#request} in production. */
	interface Transport {
		GatewayClient.Result request(String method, String path, String json);
	}

	/** What {@code /war admin history status} shows. */
	public record Status(int remembered, Instant lastPoll, int appliedThisRun, String lastError, Instant lastErrorAt) {
	}

	private final Transport transport;
	private final Supplier<String> realm;
	private final WarActionLedger ledger;
	private final WarActionApplier applier;
	private final Executor mainThread;
	private final Executor async;
	private final Runnable beforeReport;
	private final Consumer<String> warn;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();
	private volatile Instant lastPoll;
	private volatile int appliedThisRun;
	private volatile String lastError;
	private volatile Instant lastErrorAt;
	private volatile boolean failing;

	WarActionPoller(Transport transport, Supplier<String> realm, WarActionLedger ledger, WarActionApplier applier,
			Executor mainThread, Executor async, Runnable beforeReport, Consumer<String> warn, Clock clock) {
		this.transport = transport;
		this.realm = realm;
		this.ledger = ledger;
		this.applier = applier;
		this.mainThread = mainThread;
		this.async = async;
		this.beforeReport = beforeReport;
		this.warn = warn;
		this.clock = clock;
	}

	/** From the async timer. Does nothing while the previous poll is still being applied or reported. */
	void poll() {
		if (!running.compareAndSet(false, true)) {
			return;
		}
		boolean handedOver = false;
		try {
			String realmId = realm.get();
			if (realmId == null) {
				fail("TFMCWeb reports no realm, so there is nothing to ask the website for");
				return;
			}
			GatewayClient.Result result = transport.request("GET", PENDING_PATH + "?realm="
					+ URLEncoder.encode(realmId, StandardCharsets.UTF_8) + "&limit=" + LIMIT, null);
			if (!result.ok) {
				fail(PENDING_PATH + ": " + result.error);
				return;
			}
			List<WarAction> actions = parse(result.body);
			if (actions == null) {
				fail(PENDING_PATH + ": the website sent a reply without a list of actions");
				return;
			}
			reached();
			if (actions.isEmpty()) {
				return;
			}
			mainThread.execute(() -> applyAll(actions));
			handedOver = true;
		} catch (RuntimeException failed) {
			fail(PENDING_PATH + ": " + failed);
		} finally {
			if (!handedOver) {
				running.set(false);
			}
		}
	}

	/** On the main thread. */
	void applyAll(List<WarAction> actions) {
		boolean handedOver = false;
		try {
			List<WarActionResult> results = new ArrayList<>();
			boolean applied = false;
			for (WarAction action : actions) {
				WarActionResult known = ledger.get(action.id());
				if (known == null) {
					known = applyOne(action);
					ledger.put(known);
					applied = true;
					appliedThisRun++;
				}
				results.add(known);
			}
			if (applied) {
				// Saved before the website hears of it, so a crash cannot apply an action twice.
				ledger.save();
			}
			async.execute(() -> report(results));
			handedOver = true;
		} catch (RuntimeException failed) {
			fail("applying website actions: " + failed);
		} finally {
			if (!handedOver) {
				running.set(false);
			}
		}
	}

	private WarActionResult applyOne(WarAction action) {
		try {
			return applier.apply(action);
		} catch (RuntimeException failed) {
			warn.accept("Could not apply website action " + action.id() + " (" + action.kind() + "): " + failed);
			return WarActionResult.rejected(action.id(), WarActionResult.APPLY_FAILED);
		}
	}

	/** Off the main thread. The war history goes first, so the website has the new votes when it hears "applied". */
	void report(List<WarActionResult> results) {
		try {
			beforeReport.run();
			JsonArray rows = new JsonArray();
			results.forEach(result -> rows.add(result.toJson()));
			JsonObject body = new JsonObject();
			body.add("results", rows);
			GatewayClient.Result result = transport.request("POST", APPLIED_PATH, WarActionResult.GSON.toJson(body));
			if (!result.ok) {
				// The website delivers them again; the ledger answers without applying twice.
				fail(APPLIED_PATH + ": " + result.error);
			}
		} catch (RuntimeException failed) {
			fail(APPLIED_PATH + ": " + failed);
		} finally {
			running.set(false);
		}
	}

	Status status() {
		return new Status(ledger.size(), lastPoll, appliedThisRun, lastError, lastErrorAt);
	}

	/** A top-level list, or the list under {@code actions}; null for anything else. */
	static List<WarAction> parse(String body) {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(body);
		} catch (RuntimeException unreadable) {
			return null;
		}
		JsonElement list = parsed.isJsonObject() ? parsed.getAsJsonObject().get("actions") : parsed;
		if (list == null || !list.isJsonArray()) {
			return null;
		}
		List<WarAction> actions = new ArrayList<>();
		for (JsonElement row : list.getAsJsonArray()) {
			WarAction action = WarAction.fromJson(row);
			if (action != null) {
				actions.add(action);
			}
		}
		return actions;
	}

	private void reached() {
		lastPoll = clock.instant();
		if (failing) {
			failing = false;
			warn.accept("Website actions reach the server again.");
		}
	}

	/** Logged once per outage, not every five seconds; the status command shows the latest error. */
	private void fail(String error) {
		lastError = error;
		lastErrorAt = clock.instant();
		if (!failing) {
			failing = true;
			warn.accept("Could not fetch or answer website actions. " + error);
		}
	}
}
