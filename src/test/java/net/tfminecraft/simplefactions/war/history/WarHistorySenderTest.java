package net.tfminecraft.simplefactions.war.history;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Kind;

/** The sender against a recorded transport: what is posted, and what an ack or a failure leaves behind. */
class WarHistorySenderTest {
	private static final Instant NOW = Instant.parse("2026-11-10T09:50:00Z");

	@TempDir
	Path dir;
	private final List<String> warnings = new ArrayList<>();
	private final List<String[]> posts = new ArrayList<>();
	private final List<GatewayClient.Result> replies = new ArrayList<>();
	private final AtomicReference<String> realm = new AtomicReference<>("dev");
	private WarHistoryOutbox outbox;
	private WarHistorySender sender;

	@BeforeEach
	void setup() {
		outbox = new WarHistoryOutbox(dir.resolve("outbox.jsonl"), warnings::add);
		sender = new WarHistorySender(outbox, (path, json) -> {
			posts.add(new String[] {path, json});
			return replies.isEmpty() ? GatewayClient.Result.fail("no reply") : replies.remove(0);
		}, realm::get, warnings::add, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void queue(Kind kind, String id) {
		JsonObject item = new JsonObject();
		item.addProperty("id", id);
		outbox.append(kind, id, item);
	}

	private static GatewayClient.Result ok(String body) {
		return GatewayClient.Result.success(body);
	}

	@Test
	void nothingPendingPostsNothing() {
		sender.flush();
		assertTrue(posts.isEmpty());
		WarHistorySender.Status status = sender.status();
		assertEquals(0, status.events());
		assertNull(status.lastSuccess());
		assertNull(status.lastError());
	}

	@Test
	void eventsAndRecordsArePostedInBatchesWithTheRealmAndOnlyAckedOnesLeave() {
		for (int i = 0; i < WarHistorySender.MAX_EVENTS + 2; i++) {
			queue(Kind.EVENT, "e" + i);
		}
		queue(Kind.BATTLE, "r1");
		queue(Kind.BATTLE, "r2");
		replies.add(ok("{\"acked\":[\"r1\",\"r2\"]}"));
		replies.add(ok("{\"acked\":[\"e0\",\"e1\",7,{}],\"rejected\":[{\"id\":\"e1\",\"reason\":\"no war_key\"}]}"));

		sender.flush();

		assertEquals(2, posts.size());
		// Records go first, so a battle's fighters are stored before its result arrives.
		assertEquals(WarHistorySender.BATTLES_PATH, posts.get(0)[0]);
		assertEquals(2, JsonParser.parseString(posts.get(0)[1]).getAsJsonObject().getAsJsonArray("records").size());
		assertEquals(WarHistorySender.EVENTS_PATH, posts.get(1)[0]);
		JsonObject events = JsonParser.parseString(posts.get(1)[1]).getAsJsonObject();
		assertEquals("dev", events.get("realm_id").getAsString());
		assertEquals(WarHistorySender.MAX_EVENTS, events.getAsJsonArray("events").size());
		assertEquals("e0", events.getAsJsonArray("events").get(0).getAsJsonObject().get("id").getAsString());

		WarHistorySender.Status status = sender.status();
		assertEquals(WarHistorySender.MAX_EVENTS, status.events(), "Unacked events stay");
		assertEquals(0, status.battles());
		assertEquals(NOW, status.lastSuccess());
		assertEquals(List.of("The website rejected war history from /wars/plugin/events: "
				+ "{\"id\":\"e1\",\"reason\":\"no war_key\"}"), warnings);
	}

	@Test
	void withoutARealmTheBodyLeavesItOutForTheWebsiteToReadAsMain() {
		realm.set(null);
		queue(Kind.EVENT, "e1");
		replies.add(ok("{\"acked\":[\"e1\"],\"rejected\":\"none\"}"));

		sender.flush();

		assertEquals("{\"events\":[{\"id\":\"e1\"}]}", posts.get(0)[1]);
		assertEquals(0, outbox.size());
	}

	@Test
	void aFailureKeepsEverythingAndIsLoggedOncePerOutage() {
		queue(Kind.EVENT, "e1");
		queue(Kind.BATTLE, "r1");
		replies.add(GatewayClient.Result.fail("TFMCWeb gateway unavailable: missing"));
		replies.add(ok("not json"));

		sender.flush();
		sender.flush();

		assertEquals(4, posts.size());
		assertEquals(2, outbox.size());
		assertEquals(List.of("Could not send war history, keeping it for later. "
				+ "/wars/plugin/battles: TFMCWeb gateway unavailable: missing"), warnings);
		WarHistorySender.Status status = sender.status();
		assertEquals("/wars/plugin/events: no reply", status.lastError());
		assertEquals(NOW, status.lastErrorAt());

		replies.add(ok("{\"acked\":[\"r1\"]}"));
		replies.add(ok("{\"acked\":[\"e1\"]}"));
		sender.flush();

		assertEquals(0, outbox.size());
		assertEquals("War history reaches the website again.", warnings.get(1));
		assertEquals(2, warnings.size());
	}

	@Test
	void aReplyWithoutAckedIdsKeepsTheBatch() {
		queue(Kind.EVENT, "e1");
		replies.add(ok("[\"e1\"]"));
		replies.add(ok("{\"acked\":\"e1\"}"));
		replies.add(ok("{}"));

		sender.flush();
		sender.flush();
		sender.flush();

		assertEquals(1, outbox.size());
		assertEquals("/wars/plugin/events: the website sent a reply without acked ids", sender.status().lastError());
	}

	@Test
	void aFlushStillRunningMakesTheNextOneANoOp() {
		queue(Kind.EVENT, "e1");
		List<Integer> nested = new ArrayList<>();
		WarHistorySender[] self = new WarHistorySender[1];
		self[0] = new WarHistorySender(outbox, (path, json) -> {
			int before = posts.size();
			posts.add(new String[] {path, json});
			self[0].flush();
			nested.add(posts.size() - before);
			return ok("{\"acked\":[\"e1\"]}");
		}, realm::get, warnings::add, Clock.fixed(NOW, ZoneOffset.UTC));

		self[0].flush();

		assertEquals(List.of(1), nested, "The nested flush posted nothing");
		assertEquals(0, outbox.size());
	}
}
