package net.tfminecraft.simplefactions.war.actions;

import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * One pending row from {@code GET /wars/plugin/actions/pending}: a player's vote or answer to a
 * call to arms, sent from the website or Discord. Fields the row lacks are null; the applier
 * rejects what it cannot use.
 */
record WarAction(long id, String kind, String warKey, UUID account, JsonObject payload) {
	static final String CALL_TO_ARMS_ANSWER = "call_to_arms_answer";
	static final String DAY_VOTE = "day_vote";
	static final String HOUR_VOTE = "hour_vote";

	/** Null when the row has no id, so it cannot be answered at all. */
	static WarAction fromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject row = element.getAsJsonObject();
		Long id = longValue(row.get("id"));
		if (id == null) {
			return null;
		}
		JsonElement payload = row.get("payload");
		return new WarAction(id, stringValue(row.get("kind")), stringValue(row.get("war_key")),
				uuidValue(row.get("account_uuid")), payload != null && payload.isJsonObject() ? payload.getAsJsonObject() : null);
	}

	static String stringValue(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
	}

	/** A whole number, also when sent as a string (Postgres bigints sometimes are). */
	static Long longValue(JsonElement element) {
		if (!(element instanceof JsonPrimitive primitive)) {
			return null;
		}
		try {
			return primitive.getAsBigDecimal().longValueExact();
		} catch (ArithmeticException | NumberFormatException notWhole) {
			return null;
		}
	}

	private static UUID uuidValue(JsonElement element) {
		String text = stringValue(element);
		if (text == null) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException unreadable) {
			return null;
		}
	}
}
