package net.tfminecraft.simplefactions.war.history;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Item;
import net.tfminecraft.simplefactions.war.history.WarHistoryOutbox.Kind;

/** The outbox file on a real disk: what survives a restart, what an ack removes. */
class WarHistoryOutboxTest {
	@TempDir
	Path dir;
	private final List<String> warnings = new ArrayList<>();

	private Path file() {
		return dir.resolve("WarHistory").resolve("outbox.jsonl");
	}

	private WarHistoryOutbox outbox() {
		return new WarHistoryOutbox(file(), warnings::add);
	}

	private static JsonObject item(String id) {
		JsonObject item = new JsonObject();
		item.addProperty("id", id);
		item.add("missing", null);
		return item;
	}

	@Test
	void appendedItemsSurviveARestartInOrderAndOnlyOnce() throws Exception {
		WarHistoryOutbox outbox = outbox();
		assertTrue(outbox.append(Kind.EVENT, "e1", item("e1")));
		assertTrue(outbox.append(Kind.BATTLE, "b1", item("b1")));
		assertTrue(outbox.append(Kind.EVENT, "e2", item("e2")));
		assertFalse(outbox.append(Kind.EVENT, "e1", item("e1")), "Already waiting");
		assertFalse(outbox.append(null, "x", item("x")));
		assertFalse(outbox.append(Kind.EVENT, null, item("x")));
		assertFalse(outbox.append(Kind.EVENT, "x", null));
		assertTrue(outbox.append(Kind.BATTLE, "e1", item("e1")), "Ids are per kind");

		assertEquals(4, Files.readAllLines(file()).size());
		WarHistoryOutbox reloaded = outbox();
		reloaded.load();
		assertEquals(List.of("e1", "e2"), ids(reloaded.peek(Kind.EVENT, 10)));
		assertEquals(List.of("b1", "e1"), ids(reloaded.peek(Kind.BATTLE, 10)));
		assertEquals(List.of("e1"), ids(reloaded.peek(Kind.EVENT, 1)));
		assertEquals("{\"id\":\"e1\",\"missing\":null}", reloaded.peek(Kind.EVENT, 1).get(0).json());
		assertEquals(4, reloaded.size());
		assertEquals(2, reloaded.size(Kind.EVENT));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void removingAckedItemsRewritesTheFileWithTheRest() throws Exception {
		WarHistoryOutbox outbox = outbox();
		outbox.append(Kind.EVENT, "e1", item("e1"));
		outbox.append(Kind.EVENT, "e2", item("e2"));
		outbox.append(Kind.BATTLE, "b1", item("b1"));

		assertEquals(1, outbox.remove(Kind.EVENT, java.util.Arrays.asList("e1", "unknown", null)));
		assertEquals(0, outbox.remove(Kind.EVENT, List.of("b1")), "A battle id does not ack an event");

		List<String> lines = Files.readAllLines(file());
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).startsWith("{\"kind\":\"event\",\"id\":\"e2\",\"item\":"));
		assertTrue(lines.get(1).startsWith("{\"kind\":\"battle\",\"id\":\"b1\""));
		try (var files = Files.list(file().getParent())) {
			assertEquals(1, files.count(), "No temp file is left behind");
		}
	}

	@Test
	void unreadableLinesAreSkippedAndAMissingFileIsEmpty() throws Exception {
		WarHistoryOutbox outbox = outbox();
		outbox.load();
		assertEquals(0, outbox.size());

		Files.createDirectories(file().getParent());
		Files.writeString(file(), String.join("\n",
				"{\"kind\":\"event\",\"id\":\"ok\",\"item\":{\"a\":1}}",
				"",
				"not json",
				"[1,2]",
				"{\"kind\":\"other\",\"id\":\"x\",\"item\":{}}",
				"{\"id\":\"x\",\"item\":{}}",
				"{\"kind\":\"event\",\"item\":{}}",
				"{\"kind\":\"event\",\"id\":{},\"item\":{}}",
				"{\"kind\":\"event\",\"id\":\"x\"}",
				"{\"kind\":\"event\",\"id\":\"x\",\"item\":3}",
				"{\"kind\":\"event\",\"id\":7,\"item\":{\"b\":2}}") + "\n", StandardCharsets.UTF_8);

		outbox.load();

		assertEquals(List.of("ok", "7"), ids(outbox.peek(Kind.EVENT, 10)));
		assertEquals(List.of("Skipped 8 unreadable war history outbox lines."), warnings);
	}

	@Test
	void diskFailuresAreLoggedAndTheItemsStayInMemory() throws Exception {
		// A regular file where the folder should be: nothing can be written beneath it.
		Files.writeString(dir.resolve("WarHistory"), "in the way");
		WarHistoryOutbox outbox = outbox();

		assertTrue(outbox.append(Kind.EVENT, "e1", item("e1")));
		outbox.append(Kind.EVENT, "e2", item("e2"));
		outbox.remove(Kind.EVENT, List.of("e1"));

		assertEquals(List.of("e2"), ids(outbox.peek(Kind.EVENT, 10)));
		assertTrue(warnings.get(0).startsWith("Could not write to the war history outbox: "), warnings.toString());
		assertTrue(warnings.get(2).startsWith("Could not rewrite the war history outbox: "), warnings.toString());
	}

	@Test
	void anUnreadableFileIsReportedAndLeavesTheOutboxEmpty() throws Exception {
		Files.createDirectories(file());
		WarHistoryOutbox outbox = outbox();

		outbox.load();

		assertEquals(0, outbox.size());
		assertTrue(warnings.get(0).startsWith("Could not read the war history outbox: "));
	}

	@Test
	void aLargeBacklogWarnsOnceUntilItDrains() {
		WarHistoryOutbox outbox = new WarHistoryOutbox(file(), warnings::add, 2);
		outbox.append(Kind.EVENT, "e1", item("e1"));
		outbox.append(Kind.EVENT, "e2", item("e2"));
		assertTrue(warnings.isEmpty());

		outbox.append(Kind.EVENT, "e3", item("e3"));
		outbox.append(Kind.EVENT, "e4", item("e4"));
		assertEquals(List.of("The war history outbox holds 3 items. Is the website reachable through TFMCWeb?"), warnings);

		outbox.remove(Kind.EVENT, List.of("e4"));
		outbox.append(Kind.EVENT, "e5", item("e5"));
		assertEquals(1, warnings.size(), "Still over the limit: no second warning");

		outbox.remove(Kind.EVENT, List.of("e5", "e3"));
		outbox.append(Kind.EVENT, "e6", item("e6"));
		outbox.append(Kind.EVENT, "e7", item("e7"));
		assertEquals(2, warnings.size(), "Drained below the limit, so a new backlog warns again");

		WarHistoryOutbox reloaded = new WarHistoryOutbox(file(), warnings::add, 2);
		reloaded.load();
		assertEquals(3, warnings.size(), "A backlog found at startup warns too");
	}

	@Test
	void parseKeepsTheItemExactly() {
		Item item = WarHistoryOutbox.parse("{\"kind\":\"battle\",\"id\":\"r\",\"item\":{\"x\":null,\"y\":\"é\"}}");
		assertEquals(Kind.BATTLE, item.kind());
		assertEquals("{\"x\":null,\"y\":\"é\"}", item.json());
		assertNull(WarHistoryOutbox.parse("{\"kind\":5,\"id\":\"r\",\"item\":{}}"));
		assertNull(WarHistoryOutbox.parse("{\"kind\":\"event\",\"id\":\"r\",\"item\":{}}junk{"));
		assertEquals(new JsonPrimitive("x"), new JsonPrimitive(WarHistoryOutbox.parse(
				"{\"kind\":\"event\",\"id\":\"x\",\"item\":{}}").id()));
	}

	private static List<String> ids(List<Item> items) {
		List<String> ids = new ArrayList<>();
		for (Item item : items) {
			ids.add(item.id());
		}
		return ids;
	}
}
