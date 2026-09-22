package me.Plugins.SimpleFactions.War.declare;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.api.GatewayClient;

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

	private WarDeclareCodeService() {}

	public static void setGateway(Gateway replacement) {
		gateway = replacement == null ? DEFAULT_GATEWAY : replacement;
	}

	public static void resetGateway() {
		gateway = DEFAULT_GATEWAY;
	}

	/** A code the leader has already validated, waiting to be spent on a declare. */
	public static final class Session {
		public final String code;
		public final String attackerId;
		public final String defenderId;
		public final WarGoalType goal;

		public Session(String code, String attackerId, String defenderId, WarGoalType goal) {
			this.code = code;
			this.attackerId = attackerId;
			this.defenderId = defenderId;
			this.goal = goal;
		}
	}

	public static final class Result {
		public final boolean ok;
		public final WarGoalType goal;
		public final String error;

		private Result(boolean ok, WarGoalType goal, String error) {
			this.ok = ok;
			this.goal = goal;
			this.error = error;
		}

		public static Result success(WarGoalType goal) {
			return new Result(true, goal, null);
		}

		public static Result fail(String error) {
			return new Result(false, null, error == null ? "Could not check the code" : error);
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

	/** Non-consuming. Returns the goal the code pins the declare to. */
	public static Result validate(String code, String attackerId, String defenderId) {
		JsonObject body = requestBody(code, attackerId, defenderId);
		if (body == null) {
			return Result.fail("§cThat is not a valid war code.");
		}
		GatewayClient.Result response = gateway.post(VALIDATE_PATH, body.toString());
		if (response == null || !response.ok) {
			return Result.fail(reason(response));
		}
		WarGoalType goal = readGoal(response.body);
		// The backend refuses movement-origin goals at mint, but routeGoal has no branch
		// for them either, so a stale row must fail loudly here rather than silently open
		// nothing at all.
		if (goal == null || goal.isMovementOrigin()) {
			return Result.fail("§cThat code carries a war goal this server cannot declare.");
		}
		return Result.success(goal);
	}

	/** Consuming. Only call this once the war object exists. */
	public static Result redeem(String code, String attackerId, String defenderId, int warId) {
		JsonObject body = requestBody(code, attackerId, defenderId);
		if (body == null) {
			return Result.fail("§cThat is not a valid war code.");
		}
		body.addProperty("war_id", String.valueOf(warId));
		GatewayClient.Result response = gateway.post(REDEEM_PATH, body.toString());
		if (response == null || !response.ok) {
			return Result.fail(reason(response));
		}
		return Result.success(readGoal(response.body));
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
	 * map, so the confirm step re-checks that the session really covers this request.
	 */
	public static boolean covers(Session session, WarDeclareRequest request) {
		if (session == null || request == null) {
			return false;
		}
		if (session.goal != request.getGoal()) {
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

	private static JsonObject requestBody(String code, String attackerId, String defenderId) {
		String trimmed = code == null ? "" : code.trim();
		if (trimmed.isEmpty() || attackerId == null || defenderId == null) {
			return null;
		}
		JsonObject body = new JsonObject();
		body.addProperty("code", trimmed);
		body.addProperty("attacker_faction_id", attackerId);
		body.addProperty("defender_faction_id", defenderId);
		// realm_id is deliberately absent: TFMCWeb injects it, because the plugin does
		// not know which realm it is running in.
		return body;
	}

	private static WarGoalType readGoal(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			JsonObject object = JsonParser.parseString(json).getAsJsonObject();
			if (!object.has("goal") || object.get("goal").isJsonNull()) {
				return null;
			}
			return WarGoalType.fromJson(object.get("goal").getAsString());
		} catch (Exception e) {
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
