package net.tfminecraft.simplefactions.war.history;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Item;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Kind;

/**
 * Posts pending war history to the website, off the main thread. Only items the website acks
 * leave the outbox; a missing TFMCWeb, an error or an unreadable reply keeps everything.
 */
final class WarHistorySender {
	static final String EVENTS_PATH = "/wars/plugin/events";
	static final String BATTLES_PATH = "/wars/plugin/battles";
	static final int MAX_EVENTS = 50;
	/** Battle records carry every fighter, so fewer per post keeps the body small. */
	static final int MAX_BATTLES = 10;

	/** Sends one JSON body; {@link GatewayClient#request} in production. */
	interface Transport {
		GatewayClient.Result post(String path, String json);
	}

	/** What {@code /war admin history status} shows. */
	record Status(int events, int battles, Instant lastSuccess, String lastError, Instant lastErrorAt) {
	}

	private final WarHistoryOutbox outbox;
	private final Transport transport;
	private final Supplier<String> realm;
	private final Consumer<String> warn;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();
	private volatile Instant lastSuccess;
	private volatile String lastError;
	private volatile Instant lastErrorAt;
	private volatile boolean failing;

	WarHistorySender(WarHistoryOutbox outbox, Transport transport, Supplier<String> realm,
			Consumer<String> warn, Clock clock) {
		this.outbox = outbox;
		this.transport = transport;
		this.realm = realm;
		this.warn = warn;
		this.clock = clock;
	}

	/** One batch of events, then one of battle records. A flush still running makes this a no-op. */
	void flush() {
		if (!running.compareAndSet(false, true)) {
			return;
		}
		try {
			// Records first: a battle's record is saved before its result, so the website then has the
			// fighters when the battle_resolved event arrives and its Discord post can name the best.
			send(Kind.BATTLE, BATTLES_PATH, "records", MAX_BATTLES);
			send(Kind.EVENT, EVENTS_PATH, "events", MAX_EVENTS);
		} finally {
			running.set(false);
		}
	}

	Status status() {
		return new Status(outbox.size(Kind.EVENT), outbox.size(Kind.BATTLE), lastSuccess, lastError, lastErrorAt);
	}

	private void send(Kind kind, String path, String field, int max) {
		List<Item> batch = outbox.peek(kind, max);
		if (batch.isEmpty()) {
			return;
		}
		GatewayClient.Result result = transport.post(path, body(field, batch));
		if (!result.ok) {
			fail(path + ": " + result.error);
			return;
		}
		JsonObject reply = parse(result.body);
		if (reply == null || !reply.has("acked") || !reply.get("acked").isJsonArray()) {
			fail(path + ": the website sent a reply without acked ids");
			return;
		}
		List<String> acked = new ArrayList<>();
		for (JsonElement id : reply.getAsJsonArray("acked")) {
			if (id.isJsonPrimitive()) {
				acked.add(id.getAsString());
			}
		}
		outbox.remove(kind, acked);
		logRejected(path, reply.get("rejected"));
		lastSuccess = clock.instant();
		if (failing) {
			failing = false;
			warn.accept("War history reaches the website again.");
		}
	}

	private String body(String field, List<Item> batch) {
		StringBuilder body = new StringBuilder("{");
		String realmId = realm.get();
		if (realmId != null) {
			body.append("\"realm_id\":").append(WarHistoryJson.GSON.toJson(realmId)).append(',');
		}
		body.append('"').append(field).append("\":[");
		for (int i = 0; i < batch.size(); i++) {
			body.append(i == 0 ? "" : ",").append(batch.get(i).json());
		}
		return body.append("]}").toString();
	}

	/** The website acks a malformed item so it cannot block the queue; it is only logged here. */
	private void logRejected(String path, JsonElement rejected) {
		if (rejected == null || !rejected.isJsonArray()) {
			return;
		}
		JsonArray list = rejected.getAsJsonArray();
		for (JsonElement entry : list) {
			warn.accept("The website rejected war history from " + path + ": " + entry);
		}
	}

	/** Logged once per outage, not every ten seconds; the status command shows the latest error. */
	private void fail(String error) {
		lastError = error;
		lastErrorAt = clock.instant();
		if (!failing) {
			failing = true;
			warn.accept("Could not send war history, keeping it for later. " + error);
		}
	}

	private static JsonObject parse(String body) {
		try {
			JsonElement parsed = JsonParser.parseString(body);
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException unreadable) {
			return null;
		}
	}
}
