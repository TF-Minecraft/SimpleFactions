package net.tfminecraft.simplefactions.war.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What became of one website action, as posted to {@code /wars/plugin/actions/applied}:
 * {@code applied} with no reason, or {@code rejected} with a plain sentence for the player.
 */
record WarActionResult(long id, String status, String reason) {
	static final String APPLIED = "applied";
	static final String REJECTED = "rejected";
	/** Writes {@code "reason": null}, so the website can tell "no reason" from a missing field. */
	static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

	static WarActionResult applied(long id) {
		return new WarActionResult(id, APPLIED, null);
	}

	static WarActionResult rejected(long id, String reason) {
		return new WarActionResult(id, REJECTED, reason);
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("status", status);
		json.addProperty("reason", reason);
		return json;
	}

	/** Null for a row that is not one of ours. */
	static WarActionResult fromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject row = element.getAsJsonObject();
		Long id = WarAction.longValue(row.get("id"));
		String status = WarAction.stringValue(row.get("status"));
		if (id == null || !(APPLIED.equals(status) || REJECTED.equals(status))) {
			return null;
		}
		return new WarActionResult(id, status, WarAction.stringValue(row.get("reason")));
	}
}
