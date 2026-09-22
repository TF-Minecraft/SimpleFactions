package me.Plugins.SimpleFactions.REST;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class RestServerValidationTest {

	private static JsonElement parse(String json) {
		return JsonParser.parseString(json);
	}

	@Test
	void chronicle_acceptsWellFormedPayload() {
		assertDoesNotThrow(() -> RestServer.validate(
				"chronicle", parse("{\"captured_at\":\"2026-09-01T10:35:00Z\",\"factions\":[]}")));
	}

	@Test
	void chronicle_rejectsArray() {
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("chronicle", parse("[]")));
	}

	@Test
	void chronicle_rejectsMissingCapturedAt() {
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("chronicle", parse("{\"factions\":[]}")));
	}

	@Test
	void chronicle_rejectsMissingFactions() {
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("chronicle", parse("{\"captured_at\":\"x\"}")));
	}

	@Test
	void chronicle_rejectsNonArrayFactions() {
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("chronicle", parse("{\"captured_at\":\"x\",\"factions\":{}}")));
	}

	@Test
	void existingModes_stillValidated() {
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("nation", parse("[]")));
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("guilds", parse("{}")));
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("map_markers", parse("{}")));
		assertDoesNotThrow(() -> RestServer.validate(
				"map_markers", parse("{\"settlements\":[]}")));
	}

	@Test
	void regions_acceptsObjectIncludingEmpty() {
		assertDoesNotThrow(() -> RestServer.validate("regions", parse("{}")));
		assertDoesNotThrow(() -> RestServer.validate(
				"regions", parse("{\"REGION_1\":{\"name\":\"A\",\"provinces\":[1]}}")));
	}

	@Test
	void regions_rejectsArray() {
		assertThrows(IllegalStateException.class,
				() -> RestServer.validate("regions", parse("[]")));
	}
}
