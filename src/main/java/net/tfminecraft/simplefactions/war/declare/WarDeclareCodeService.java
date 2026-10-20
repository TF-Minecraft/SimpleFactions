package net.tfminecraft.simplefactions.war.declare;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.api.GatewayClient;

/**
 * Staff-minted war declare codes, checked against ProvinceSystem through TFMCWeb.
 *
 * <p>Validation and redemption are two calls on purpose. {@code WarManager.declareWar}
 * can still refuse after a code was accepted - {@code WarGoalValidator},
 * {@code CampaignDeclareValidator} and {@code CampaignNavyGate} all run inside it -
 * and a navy-gate rejection must not burn a staff-approved ticket.
 *
 * <p>The gateway call sits behind {@link Gateway} so tests can drive the gate without
 * a live backend, the same way {@code MercenaryStatService.setApplier} works.
 */
public final class WarDeclareCodeService {

	private static final String VALIDATE_PATH = "/wars/declare-codes/validate";
	private static final String REDEEM_PATH = "/wars/declare-codes/redeem";

	/** Seam over the TFMCWeb gateway. Called off the main thread. */
	public interface Gateway {
		GatewayClient.Result post(String path, String jsonBody);
	}

	private static final Gateway DEFAULT_GATEWAY =
			(path, body) -> GatewayClient.request("POST", path, body);

	private static Gateway gateway = DEFAULT_GATEWAY;

	private static final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

	/**
	 * War is no longer offered: every decisive war already ends in reparations from the
	 * loser. Wars declared with it before keep loading and resolving.
	 */
	public static final String RETIRED_WAR_GOAL =
			"§cWar is no longer a war goal. The loser of every war now pays war reparations.";

	private WarDeclareCodeService() {}

	public static void setGateway(Gateway replacement) {
		gateway = replacement == null ? DEFAULT_GATEWAY : replacement;
	}

	public static void resetGateway() {
		gateway = DEFAULT_GATEWAY;
	}

	/** What a code was minted for. Each kind is only accepted where it belongs. */
	public enum Kind {
		WAR_DECLARE("war_declare"),
		COALITION_OFFENSIVE("coalition_offensive");

		private final String json;

		Kind(String json) {
			this.json = json;
		}

		public String json() {
			return json;
		}
	}

	/** A code the leader has already validated, waiting to be spent on a declare. */
	public static final class Session {
		public final String code;
		public final String attackerId;
		public final String defenderId;

		public Session(String code, String attackerId, String defenderId) {
			this.code = code;
			this.attackerId = attackerId;
			this.defenderId = defenderId;
		}
	}

	public static final class Result {
		public final boolean ok;
		public final String error;

		private Result(boolean ok, String error) {
			this.ok = ok;
			this.error = error;
		}

		public static Result success() {
			return new Result(true, null);
		}

		public static Result fail(String error) {
			return new Result(false, error == null ? "Could not check the code" : error);
		}
	}

	/**
	 * True when this player must supply a code. Staff bypass by permission, which is
	 * also what makes failing closed on an unreachable API safe.
	 */
	public static boolean isRequired(Player player) {
		if (!Cache.warRequireDeclareCode) {
			return false;
		}
		return player == null || !Permissions.isAdmin(player);
	}

	/** Non-consuming check of a war declaration code. Codes no longer pin a war goal. */
	public static Result validate(String code, String attackerId, String defenderId) {
		return validate(code, attackerId, defenderId, Kind.WAR_DECLARE);
	}

	/**
	 * Non-consuming. For a coalition code the attacker is the coalition leader and the
	 * defender is the coalition's target.
	 */
	public static Result validate(String code, String attackerId, String defenderId, Kind kind) {
		return call(VALIDATE_PATH, requestBody(code, attackerId, defenderId, kind), kind);
	}

	/** Consuming. Only call this once the war object exists. */
	public static Result redeem(String code, String attackerId, String defenderId, int warId) {
		JsonObject body = requestBody(code, attackerId, defenderId, Kind.WAR_DECLARE);
		if (body != null) {
			body.addProperty("war_id", String.valueOf(warId));
		}
		return call(REDEEM_PATH, body, Kind.WAR_DECLARE);
	}

	/** Consuming. Spends an offensive coalition code for the leader and target it was minted for. */
	public static Result redeemCoalition(String code, String leaderId, String targetId) {
		return call(REDEEM_PATH, requestBody(code, leaderId, targetId, Kind.COALITION_OFFENSIVE),
				Kind.COALITION_OFFENSIVE);
	}

	private static Result call(String path, JsonObject body, Kind kind) {
		if (body == null) {
			return Result.fail("§cThat is not a valid war code.");
		}
		GatewayClient.Result response = gateway.post(path, body.toString());
		if (response == null || !response.ok) {
			return Result.fail(reason(response));
		}
		JsonObject answer = parse(response.body);
		if (answer == null) {
			return Result.fail("§cThe war code service gave an answer this server cannot read.");
		}
		return kindMatches(answer, kind) ? Result.success() : Result.fail(wrongKind(kind));
	}

	/**
	 * A backend from before code kinds answers without one; it only ever minted war
	 * declaration codes, so a missing kind may stand for those and nothing else.
	 */
	private static boolean kindMatches(JsonObject answer, Kind kind) {
		if (!answer.has("kind") || answer.get("kind").isJsonNull()) {
			return kind == Kind.WAR_DECLARE;
		}
		return kind.json().equalsIgnoreCase(answer.get("kind").getAsString());
	}

	private static String wrongKind(Kind kind) {
		return kind == Kind.WAR_DECLARE
				? "§cThat code is a coalition code, not a war declaration code."
				: "§cThat code is a war declaration code, not a coalition code.";
	}

	public static void openSession(Player player, Session session) {
		if (player == null || session == null) {
			return;
		}
		sessions.put(player.getUniqueId(), session);
	}

	public static Session session(Player player) {
		return player == null ? null : sessions.get(player.getUniqueId());
	}

	public static Session clearSession(Player player) {
		return player == null ? null : sessions.remove(player.getUniqueId());
	}

	/**
	 * The war button is the front door, but {@code pendingWarDeclares} is a separate
	 * map, so the confirm step re-checks that the session really covers this pairing.
	 * The leader picks the goals; the code only covers who may fight whom.
	 */
	public static boolean covers(Session session, WarDeclareRequest request) {
		if (session == null || request == null) {
			return false;
		}
		return sameId(session.attackerId, request.getAttacker())
				&& sameId(session.defenderId, request.getDefender());
	}

	private static boolean sameId(String id, Faction faction) {
		if (id == null || faction == null || faction.getId() == null) {
			return false;
		}
		return id.equalsIgnoreCase(faction.getId());
	}

	private static JsonObject requestBody(String code, String attackerId, String defenderId, Kind kind) {
		String trimmed = code == null ? "" : code.trim();
		if (trimmed.isEmpty() || attackerId == null || defenderId == null) {
			return null;
		}
		JsonObject body = new JsonObject();
		body.addProperty("code", trimmed);
		body.addProperty("attacker_faction_id", attackerId);
		body.addProperty("defender_faction_id", defenderId);
		body.addProperty("kind", kind.json());
		// realm_id is deliberately absent: TFMCWeb injects it, because the plugin does
		// not know which realm it is running in.
		return body;
	}

	private static JsonObject parse(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			JsonElement element = JsonParser.parseString(json);
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String reason(GatewayClient.Result response) {
		if (response == null || response.error == null || response.error.isBlank()) {
			return "§cCould not reach the war code service.";
		}
		return "§c" + response.error;
	}
}
