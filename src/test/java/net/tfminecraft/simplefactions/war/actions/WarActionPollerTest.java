package net.tfminecraft.simplefactions.war.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.api.GatewayClient;

/** Fetching, applying and answering website actions, with a fake website and inline threads. */
class WarActionPollerTest {
	private static final String ACCOUNT = "6f1d7c56-1111-4222-8333-944455556666";

	@TempDir
	Path dir;
	private final List<String> requests = new ArrayList<>();
	private final List<String> bodies = new ArrayList<>();
	private final Deque<GatewayClient.Result> replies = new ArrayDeque<>();
	private final List<String> warnings = new ArrayList<>();
	private final List<Runnable> mainQueue = new ArrayList<>();
	private WarActionLedger ledger;
	private WarActionApplier applier;
	private Executor mainThread = Runnable::run;
	private Executor async = Runnable::run;
	private Runnable beforeReport = () -> requests.add("flush");
	private String realm = "dev";

	@BeforeEach
	void setup() {
		ledger = new WarActionLedger(dir.resolve("WarHistory/actions-applied.json"), warnings::add);
		applier = mock(WarActionApplier.class);
		when(applier.apply(any())).thenAnswer(call -> WarActionResult.applied(call.<WarAction>getArgument(0).id()));
	}

	private WarActionPoller poller() {
		return new WarActionPoller((method, path, json) -> {
			requests.add(method + " " + path);
			if (json != null) {
				bodies.add(json);
			}
			GatewayClient.Result reply = replies.poll();
			return reply != null ? reply : GatewayClient.Result.success("{}");
		}, () -> realm, ledger, applier, mainThread, async, () -> beforeReport.run(), warnings::add,
				Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
	}

	private static String row(long id, String kind) {
		return "{\"id\":" + id + ",\"realm_id\":\"dev\",\"war_key\":\"w3-1762771200\",\"kind\":\"" + kind
				+ "\",\"account_uuid\":\"" + ACCOUNT + "\",\"discord_user_id\":null,\"source\":\"web\","
				+ "\"payload\":{\"picks\":[21]},\"status\":\"pending\"}";
	}

	private void pending(String... rows) {
		replies.add(GatewayClient.Result.success("{\"actions\":[" + String.join(",", rows) + "]}"));
	}

	private JsonObject lastBody() {
		return JsonParser.parseString(bodies.get(bodies.size() - 1)).getAsJsonObject();
	}

	@Test
	void pendingActionsAreAppliedOnTheMainThreadAndTheirResultsPosted() {
		doReturn(WarActionResult.rejected(2, "The vote on the battle hour is closed."))
				.when(applier).apply(argThat(action -> action != null && action.id() == 2));
		pending(row(1, "hour_vote"), row(2, "hour_vote"));
		WarActionPoller poller = poller();

		poller.poll();

		assertEquals(List.of("GET " + WarActionPoller.PENDING_PATH + "?realm=dev&limit=50", "flush",
				"POST " + WarActionPoller.APPLIED_PATH), requests);
		assertEquals("{\"results\":[{\"id\":1,\"status\":\"applied\",\"reason\":null},"
				+ "{\"id\":2,\"status\":\"rejected\",\"reason\":\"The vote on the battle hour is closed.\"}]}",
				bodies.get(0));
		verify(applier).apply(argThat(action -> action.id() == 1 && action.kind().equals("hour_vote")
				&& action.warKey().equals("w3-1762771200") && action.account().equals(UUID.fromString(ACCOUNT))
				&& action.payload().getAsJsonArray("picks").get(0).getAsInt() == 21));
		assertEquals(2, poller.status().appliedThisRun());
		assertEquals(Instant.EPOCH, poller.status().lastPoll());
		WarActionLedger reloaded = new WarActionLedger(dir.resolve("WarHistory/actions-applied.json"), warnings::add);
		reloaded.load();
		assertEquals(WarActionResult.rejected(2, "The vote on the battle hour is closed."), reloaded.get(2),
				"Saved before it was reported");
	}

	@Test
	void anActionDeliveredAgainIsAnsweredFromTheLedgerAndNeverAppliedTwice() {
		replies.add(GatewayClient.Result.success("[" + row(5, "day_vote") + "]"));
		replies.add(GatewayClient.Result.fail("website down"));
		WarActionPoller poller = poller();
		poller.poll();
		assertTrue(warnings.get(0).contains(WarActionPoller.APPLIED_PATH + ": website down"), warnings.toString());

		pending(row(5, "day_vote"));
		poller.poll();

		verify(applier, times(1)).apply(any());
		assertEquals("applied", lastBody().getAsJsonArray("results").get(0).getAsJsonObject().get("status").getAsString());
		assertEquals(1, poller.status().appliedThisRun());
		assertEquals(1, poller.status().remembered());

		pending(row(5, "day_vote"));
		WarActionLedger afterRestart = new WarActionLedger(dir.resolve("WarHistory/actions-applied.json"), warnings::add);
		afterRestart.load();
		ledger = afterRestart;
		poller().poll();
		verify(applier, times(1)).apply(any());
	}

	@Test
	void nothingIsPostedWhenNothingIsPendingAndRowsWithoutAnIdAreSkipped() {
		pending();
		WarActionPoller poller = poller();
		poller.poll();
		assertEquals(1, requests.size());

		pending("{\"kind\":\"day_vote\"}", "{\"id\":\"x\"}", "7", row(9, "day_vote"));
		poller.poll();
		verify(applier, times(1)).apply(any());
		assertEquals(1, lastBody().getAsJsonArray("results").size());
	}

	@Test
	void stringIdsAndMissingFieldsAreReadAsTheWebsiteSendsThem() {
		pending("{\"id\":\"12\",\"kind\":\"day_vote\",\"account_uuid\":\"not-a-uuid\",\"payload\":[1]}",
				"{\"id\":13,\"kind\":7,\"war_key\":null}");
		poller().poll();

		verify(applier).apply(argThat(action -> action.id() == 12 && action.account() == null
				&& action.payload() == null && action.warKey() == null && action.kind().equals("day_vote")));
		verify(applier).apply(argThat(action -> action.id() == 13 && action.kind() == null));
	}

	@Test
	void anOutageIsLoggedOnceAndItsEndOnce() {
		replies.add(GatewayClient.Result.fail("TFMCWeb gateway unavailable: down"));
		replies.add(GatewayClient.Result.fail("TFMCWeb gateway unavailable: down"));
		replies.add(GatewayClient.Result.success("not json"));
		replies.add(GatewayClient.Result.success("{\"rows\":[]}"));
		WarActionPoller poller = poller();

		for (int i = 0; i < 4; i++) {
			poller.poll();
		}

		assertEquals(1, warnings.size(), warnings.toString());
		assertEquals("Could not fetch or answer website actions. " + WarActionPoller.PENDING_PATH
				+ ": TFMCWeb gateway unavailable: down", warnings.get(0));
		assertEquals(WarActionPoller.PENDING_PATH + ": the website sent a reply without a list of actions",
				poller.status().lastError());
		assertEquals(Instant.EPOCH, poller.status().lastErrorAt());

		pending();
		poller.poll();
		assertEquals("Website actions reach the server again.", warnings.get(1));
		assertEquals(Instant.EPOCH, poller.status().lastPoll());
	}

	@Test
	void withoutARealmNothingIsAsked() {
		realm = null;
		WarActionPoller poller = poller();
		poller.poll();

		assertTrue(requests.isEmpty());
		assertTrue(poller.status().lastError().contains("no realm"));
	}

	@Test
	void theRealmIsEncodedIntoTheQuery() {
		realm = "test realm";
		pending();
		poller().poll();
		assertEquals("GET " + WarActionPoller.PENDING_PATH + "?realm=test+realm&limit=50", requests.get(0));
	}

	@Test
	void onePollRunsAtATimeFromFetchToReport() {
		mainThread = mainQueue::add;
		pending(row(1, "day_vote"));
		pending(row(2, "day_vote"));
		WarActionPoller poller = poller();

		poller.poll();
		poller.poll();
		assertEquals(1, requests.size(), "The second waits for the first to be applied and reported");

		mainQueue.remove(0).run();
		poller.poll();
		assertEquals(4, requests.size(), "Report done, so the next poll fetched again");
	}

	@Test
	void aFailingApplyIsRejectedAndLogged() {
		doThrow(new IllegalStateException("boom")).when(applier).apply(any());
		pending(row(3, "call_to_arms_answer"));

		poller().poll();

		JsonObject result = lastBody().getAsJsonArray("results").get(0).getAsJsonObject();
		assertEquals("rejected", result.get("status").getAsString());
		assertEquals("The server could not apply this action. Try again.", result.get("reason").getAsString());
		assertEquals("Could not apply website action 3 (call_to_arms_answer): java.lang.IllegalStateException: boom",
				warnings.get(0));
	}

	@Test
	void aSchedulerThatRefusesTheWorkFreesThePoller() {
		mainThread = work -> {
			throw new IllegalStateException("plugin disabled");
		};
		pending(row(1, "day_vote"));
		pending();
		WarActionPoller poller = poller();
		poller.poll();
		assertTrue(poller.status().lastError().contains("plugin disabled"));
		poller.poll();
		assertEquals(2, requests.size(), "Not stuck");

		mainThread = Runnable::run;
		async = work -> {
			throw new IllegalStateException("no async");
		};
		pending(row(2, "day_vote"));
		pending();
		poller = poller();
		poller.poll();
		assertTrue(poller.status().lastError().startsWith("applying website actions: "));
		poller.poll();
		assertEquals(4, requests.size(), "Not stuck either");
	}

	@Test
	void aFailingReportStillFreesThePoller() {
		beforeReport = () -> {
			throw new IllegalStateException("history broke");
		};
		pending(row(1, "day_vote"));
		pending();
		WarActionPoller poller = poller();
		poller.poll();
		assertEquals(WarActionPoller.APPLIED_PATH + ": java.lang.IllegalStateException: history broke",
				poller.status().lastError());
		poller.poll();
		assertEquals(2, requests.size());
	}

	@Test
	void theLedgerKeepsTheLatestThousandAndSurvivesARestart() {
		for (long id = 1; id <= WarActionLedger.MAX + 5; id++) {
			ledger.put(WarActionResult.applied(id));
		}
		ledger.save();
		WarActionLedger reloaded = new WarActionLedger(dir.resolve("WarHistory/actions-applied.json"), warnings::add);
		reloaded.load();

		assertEquals(WarActionLedger.MAX, reloaded.size());
		assertNull(reloaded.get(5), "The oldest are forgotten");
		assertEquals(WarActionResult.applied(6), reloaded.get(6));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void anUnreadableLedgerIsLoggedAndOddRowsSkipped() throws Exception {
		Path file = dir.resolve("WarHistory/actions-applied.json");
		java.nio.file.Files.createDirectories(file.getParent());
		java.nio.file.Files.writeString(file, "{\"results\":[{\"id\":1,\"status\":\"applied\"},{\"id\":2,\"status\":\"lost\"},"
				+ "{\"status\":\"applied\"},3,{\"id\":4,\"status\":\"rejected\",\"reason\":\"Too late.\"}]}");
		ledger.load();
		assertEquals(2, ledger.size());
		assertEquals(WarActionResult.rejected(4, "Too late."), ledger.get(4));
		assertNull(ledger.get(1).reason());

		java.nio.file.Files.writeString(file, "[]");
		ledger.load();
		assertEquals(0, ledger.size());

		java.nio.file.Files.writeString(file, "{\"results\":");
		ledger.load();
		assertEquals(0, ledger.size());
		assertTrue(warnings.get(0).startsWith("Could not read actions-applied.json"), warnings.toString());
	}

	@Test
	void aLedgerThatCannotBeSavedIsLogged() throws Exception {
		Path blocked = dir.resolve("blocked");
		java.nio.file.Files.writeString(blocked, "a file, not a folder");
		WarActionLedger broken = new WarActionLedger(blocked.resolve("actions-applied.json"), warnings::add);
		broken.put(WarActionResult.applied(1));

		broken.save();

		assertTrue(warnings.get(0).startsWith("Could not save actions-applied.json: "), warnings.toString());
		assertNull(WarActionResult.fromJson(null));
	}
}
