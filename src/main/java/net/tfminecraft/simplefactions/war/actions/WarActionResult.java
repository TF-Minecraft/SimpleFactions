package net.tfminecraft.simplefactions.war.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What became of one website action, as posted to {@code /wars/plugin/actions/applied}:
 * {@code applied} with no reason, or {@code rejected} with a plain sentence for the player.
 * {@code retry} says whether the same action may work later (a battle is being fought, the
 * server failed), so the website and the bot need not guess from the sentence.
 */
record WarActionResult(long id, String status, String reason, boolean retry) {
	static final String APPLIED = "applied";
	static final String REJECTED = "rejected";
	static final String APPLY_FAILED = "The server could not apply this action. Try again.";
	/** Refusals that can pass later without the player changing anything. */
	static final java.util.Set<String> RETRYABLE = java.util.Set.of(
			net.tfminecraft.simplefactions.war.core.CallToArmsService.BATTLE_UNDERWAY, APPLY_FAILED);
	/** Writes {@code "reason": null}, so the website can tell "no reason" from a missing field. */
	static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

	static WarActionResult applied(long id) {
		return new WarActionResult(id, APPLIED, null, false);
	}

	static WarActionResult rejected(long id, String reason) {
		return new WarActionResult(id, REJECTED, reason, RETRYABLE.contains(reason));
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("status", status);
		json.addProperty("reason", reason);
		json.addProperty("retry", retry);
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
		String reason = WarAction.stringValue(row.get("reason"));
		// Ledgers written before the flag: work it out from the reason, as a new result would.
		boolean retry = row.has("retry") && row.get("retry").isJsonPrimitive()
				? row.get("retry").getAsBoolean()
				: REJECTED.equals(status) && RETRYABLE.contains(reason);
		return new WarActionResult(id, status, reason, retry);
	}
}
