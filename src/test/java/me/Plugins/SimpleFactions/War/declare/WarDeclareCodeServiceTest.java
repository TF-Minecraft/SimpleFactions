package me.Plugins.SimpleFactions.War.declare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.api.GatewayClient;

/**
 * The declare gate is the only thing standing between a leader and a war, so it is
 * driven here through the {@code Gateway} seam rather than against a live backend.
 */
class WarDeclareCodeServiceTest {

	/** Records what the plugin sent and replies with whatever the test scripted. */
	private static final class FakeGateway implements WarDeclareCodeService.Gateway {
		private final List<String> paths = new ArrayList<>();
		private final List<String> bodies = new ArrayList<>();
		private GatewayClient.Result reply = GatewayClient.Result.success("{}");

		@Override
		public GatewayClient.Result post(String path, String jsonBody) {
			paths.add(path);
			bodies.add(jsonBody);
			return reply;
		}

		private JsonObject lastBody() {
			return JsonParser.parseString(bodies.get(bodies.size() - 1)).getAsJsonObject();
		}
	}

	private FakeGateway gateway;
	private boolean savedRequireCode;

	@BeforeEach
	void setUp() {
		gateway = new FakeGateway();
		WarDeclareCodeService.setGateway(gateway);
		savedRequireCode = Cache.warRequireDeclareCode;
	}

	@AfterEach
	void tearDown() {
		WarDeclareCodeService.resetGateway();
		Cache.warRequireDeclareCode = savedRequireCode;
	}

	@Test
	void aValidCodeReturnsTheGoalItWasMintedFor() {
		gateway.reply = GatewayClient.Result.success("{\"valid\":true,\"goal\":\"transfer_subject\"}");

		WarDeclareCodeService.Result result =
				WarDeclareCodeService.validate("AAAA-BBBB-CCCC", "rhun", "arnor");

		assertTrue(result.ok);
		assertEquals(WarGoalType.TRANSFER_SUBJECT, result.goal);
		assertEquals("/wars/declare-codes/validate", gateway.paths.get(0));
	}

	@Test
	void theRequestCarriesThePairingButNeverARealm() {
		gateway.reply = GatewayClient.Result.success("{\"goal\":\"war\"}");

		WarDeclareCodeService.validate("  aaaa-bbbb-cccc  ", "rhun", "arnor");

		JsonObject body = gateway.lastBody();
		assertEquals("aaaa-bbbb-cccc", body.get("code").getAsString());
		assertEquals("rhun", body.get("attacker_faction_id").getAsString());
		assertEquals("arnor", body.get("defender_faction_id").getAsString());
		// TFMCWeb injects realm_id on the way out; the plugin does not know its realm,
		// and guessing one here would let a dev code be spent on the live realm.
		assertFalse(body.has("realm_id"));
	}

	@Test
	void anUnreachableApiFailsClosed() {
		gateway.reply = GatewayClient.Result.fail("Could not reach API: connection refused");

		WarDeclareCodeService.Result result =
				WarDeclareCodeService.validate("AAAA-BBBB-CCCC", "rhun", "arnor");

		assertFalse(result.ok);
		assertNull(result.goal);
		assertTrue(result.error.contains("connection refused"), result.error);
	}

	@Test
	void aBackendRejectionIsShownToTheLeaderVerbatim() {
		gateway.reply = GatewayClient.Result.fail("This code has already been used");

		WarDeclareCodeService.Result result =
				WarDeclareCodeService.validate("AAAA-BBBB-CCCC", "rhun", "arnor");

		assertFalse(result.ok);
		assertEquals("§cThis code has already been used", result.error);
	}

	@Test
	void aMovementOriginGoalIsRefusedEvenWhenTheBackendAllowedIt() {
		// The backend rejects these at mint, but routeGoal has no branch for them, so a
		// stale row would otherwise send the leader to a GUI that never opens.
		for (String goal : List.of("overthrow", "change_law", "change_tax", "force_peace")) {
			gateway.reply = GatewayClient.Result.success("{\"goal\":\"" + goal + "\"}");
			assertTrue(WarGoalType.fromJson(goal).isMovementOrigin(), goal);

			WarDeclareCodeService.Result result =
					WarDeclareCodeService.validate("AAAA-BBBB-CCCC", "rhun", "arnor");

			assertFalse(result.ok, goal);
			assertNull(result.goal, goal);
		}
	}

	@Test
	void anEmptyOrGoallessAnswerIsNotTreatedAsApproval() {
		gateway.reply = GatewayClient.Result.success("");
		assertFalse(WarDeclareCodeService.validate("A", "rhun", "arnor").ok);

		gateway.reply = GatewayClient.Result.success("{\"valid\":true}");
		assertFalse(WarDeclareCodeService.validate("A", "rhun", "arnor").ok);

		gateway.reply = GatewayClient.Result.success("{\"goal\":\"not_a_goal\"}");
		assertFalse(WarDeclareCodeService.validate("A", "rhun", "arnor").ok);
	}

	@Test
	void aBlankCodeNeverReachesTheBackend() {
		assertFalse(WarDeclareCodeService.validate("   ", "rhun", "arnor").ok);
		assertFalse(WarDeclareCodeService.validate(null, "rhun", "arnor").ok);

		assertTrue(gateway.paths.isEmpty());
	}

	@Test
	void redeemHitsTheOtherPathAndCarriesTheWarId() {
		gateway.reply = GatewayClient.Result.success("{\"ok\":true,\"goal\":\"pillage\"}");

		WarDeclareCodeService.Result result =
				WarDeclareCodeService.redeem("AAAA-BBBB-CCCC", "rhun", "arnor", 42);

		assertTrue(result.ok);
		assertEquals("/wars/declare-codes/redeem", gateway.paths.get(0));
		assertEquals("42", gateway.lastBody().get("war_id").getAsString());
	}

	@Test
	void aSessionOnlyCoversTheExactPairingAndGoal() {
		Faction rhun = faction("rhun");
		Faction arnor = faction("arnor");
		Faction gondor = faction("gondor");
		WarDeclareCodeService.Session session = new WarDeclareCodeService.Session(
				"AAAA-BBBB-CCCC", "rhun", "arnor", WarGoalType.SUBJUGATE);

		assertTrue(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(rhun, arnor, WarGoalType.SUBJUGATE)));
		// The war button is one gate, pendingWarDeclares is another map entirely, so a
		// swapped goal or target has to be caught again at confirm time.
		assertFalse(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(rhun, arnor, WarGoalType.PILLAGE)));
		assertFalse(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(rhun, gondor, WarGoalType.SUBJUGATE)));
		assertFalse(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(gondor, arnor, WarGoalType.SUBJUGATE)));
		assertFalse(WarDeclareCodeService.covers(
				null, WarDeclareRequest.of(rhun, arnor, WarGoalType.SUBJUGATE)));
	}

	@Test
	void aSessionIsPerPlayerAndConsumedOnce() {
		Player leader = player();
		Player other = player();
		WarDeclareCodeService.Session session = new WarDeclareCodeService.Session(
				"AAAA-BBBB-CCCC", "rhun", "arnor", WarGoalType.WAR);

		WarDeclareCodeService.openSession(leader, session);
		assertEquals(session, WarDeclareCodeService.session(leader));
		assertNull(WarDeclareCodeService.session(other));
		assertEquals(session, WarDeclareCodeService.clearSession(leader));
		assertNull(WarDeclareCodeService.session(leader));
	}

	@Test
	void staffBypassTheGateByPermission() {
		Player leader = player();
		Player staff = player();
		when(staff.hasPermission("simplefactions.admin")).thenReturn(true);

		Cache.warRequireDeclareCode = false;
		assertFalse(WarDeclareCodeService.isRequired(leader));
		assertFalse(WarDeclareCodeService.isRequired(staff));

		Cache.warRequireDeclareCode = true;
		assertTrue(WarDeclareCodeService.isRequired(leader));
		// This bypass is what makes failing closed on a downed API safe.
		assertFalse(WarDeclareCodeService.isRequired(staff));
	}

	private static Faction faction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		return faction;
	}

	private static Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}
}
