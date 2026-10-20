package net.tfminecraft.simplefactions.war.declare;

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

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.api.GatewayClient;

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
	void aValidCodeIsAcceptedWhateverGoalAnOlderBackendStillNames() {
		gateway.reply = GatewayClient.Result.success("{\"valid\":true,\"goal\":\"transfer_subject\"}");
		assertTrue(WarDeclareCodeService.validate("AAAA-BBBB-CCCC", "rhun", "arnor").ok);

		gateway.reply = GatewayClient.Result.success("{\"valid\":true,\"kind\":\"war_declare\",\"goal\":null}");
		assertTrue(WarDeclareCodeService.validate("AAAA-BBBB-CCCC", "rhun", "arnor").ok);
		assertEquals("/wars/declare-codes/validate", gateway.paths.get(0));
	}

	@Test
	void theRequestCarriesThePairingAndKindButNeverARealm() {
		gateway.reply = GatewayClient.Result.success("{}");

		WarDeclareCodeService.validate("  aaaa-bbbb-cccc  ", "rhun", "arnor");

		JsonObject body = gateway.lastBody();
		assertEquals("aaaa-bbbb-cccc", body.get("code").getAsString());
		assertEquals("rhun", body.get("attacker_faction_id").getAsString());
		assertEquals("arnor", body.get("defender_faction_id").getAsString());
		assertEquals("war_declare", body.get("kind").getAsString());
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
	void aMissingOrBlankReasonFallsBackToAGenericMessage() {
		gateway.reply = null;
		assertEquals("§cCould not reach the war code service.",
				WarDeclareCodeService.validate("A", "rhun", "arnor").error);
		gateway.reply = GatewayClient.Result.fail(" ");
		assertEquals("§cCould not reach the war code service.",
				WarDeclareCodeService.validate("A", "rhun", "arnor").error);
		assertEquals("Could not check the code", WarDeclareCodeService.Result.fail(null).error);
	}

	@Test
	void anUnreadableAnswerIsNotTreatedAsApproval() {
		for (String reply : new String[] {null, "", "  ", "[1,2]", "{not json"}) {
			gateway.reply = GatewayClient.Result.success(reply);
			WarDeclareCodeService.Result result = WarDeclareCodeService.validate("A", "rhun", "arnor");
			assertFalse(result.ok, String.valueOf(reply));
			assertEquals("§cThe war code service gave an answer this server cannot read.", result.error);
		}
	}

	@Test
	void aCodeOfTheOtherKindIsRefusedEitherWay() {
		gateway.reply = GatewayClient.Result.success("{\"kind\":\"coalition_offensive\"}");
		WarDeclareCodeService.Result war = WarDeclareCodeService.validate("A", "rhun", "arnor");
		assertFalse(war.ok);
		assertEquals("§cThat code is a coalition code, not a war declaration code.", war.error);

		gateway.reply = GatewayClient.Result.success("{\"kind\":\"war_declare\"}");
		WarDeclareCodeService.Result coalition = WarDeclareCodeService.validate(
				"A", "rhun", "arnor", WarDeclareCodeService.Kind.COALITION_OFFENSIVE);
		assertFalse(coalition.ok);
		assertEquals("§cThat code is a war declaration code, not a coalition code.", coalition.error);
		assertEquals("coalition_offensive", gateway.lastBody().get("kind").getAsString());

		// A backend from before kinds only knew war codes, so it can never approve a coalition code.
		gateway.reply = GatewayClient.Result.success("{\"valid\":true}");
		assertFalse(WarDeclareCodeService.validate(
				"A", "rhun", "arnor", WarDeclareCodeService.Kind.COALITION_OFFENSIVE).ok);
		gateway.reply = GatewayClient.Result.success("{\"kind\":null}");
		assertTrue(WarDeclareCodeService.validate("A", "rhun", "arnor").ok);
	}

	@Test
	void aBlankCodeNeverReachesTheBackend() {
		assertFalse(WarDeclareCodeService.validate("   ", "rhun", "arnor").ok);
		assertFalse(WarDeclareCodeService.validate(null, "rhun", "arnor").ok);
		assertFalse(WarDeclareCodeService.validate("A", null, "arnor").ok);
		assertFalse(WarDeclareCodeService.validate("A", "rhun", null).ok);
		assertFalse(WarDeclareCodeService.redeem(" ", "rhun", "arnor", 1).ok);
		assertEquals("§cThat is not a valid war code.",
				WarDeclareCodeService.redeemCoalition(null, "rhun", "arnor").error);

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
	void aCoalitionCodeIsRedeemedForItsLeaderAndTargetWithoutAWar() {
		gateway.reply = GatewayClient.Result.success("{\"ok\":true,\"kind\":\"coalition_offensive\"}");

		WarDeclareCodeService.Result result =
				WarDeclareCodeService.redeemCoalition("CODE", "thalendor", "holy_order");

		assertTrue(result.ok);
		JsonObject body = gateway.lastBody();
		assertEquals("/wars/declare-codes/redeem", gateway.paths.get(0));
		assertEquals("thalendor", body.get("attacker_faction_id").getAsString());
		assertEquals("holy_order", body.get("defender_faction_id").getAsString());
		assertEquals("coalition_offensive", body.get("kind").getAsString());
		assertFalse(body.has("war_id"));
	}

	@Test
	void aSessionCoversTheExactPairingWhateverGoalIsChosen() {
		Faction rhun = faction("rhun");
		Faction arnor = faction("arnor");
		Faction gondor = faction("gondor");
		WarDeclareCodeService.Session session = new WarDeclareCodeService.Session(
				"AAAA-BBBB-CCCC", "rhun", "arnor");

		assertTrue(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(rhun, arnor, WarGoalType.SUBJUGATE)));
		assertTrue(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(rhun, arnor, WarGoalType.PILLAGE)));
		// The war button is one gate, pendingWarDeclares is another map entirely, so a
		// swapped target has to be caught again at confirm time.
		assertFalse(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(rhun, gondor, WarGoalType.SUBJUGATE)));
		assertFalse(WarDeclareCodeService.covers(
				session, WarDeclareRequest.of(gondor, arnor, WarGoalType.SUBJUGATE)));
		assertFalse(WarDeclareCodeService.covers(
				null, WarDeclareRequest.of(rhun, arnor, WarGoalType.SUBJUGATE)));
		assertFalse(WarDeclareCodeService.covers(session, null));
	}

	@Test
	void aSessionIsPerPlayerAndConsumedOnce() {
		Player leader = player();
		Player other = player();
		WarDeclareCodeService.Session session = new WarDeclareCodeService.Session(
				"AAAA-BBBB-CCCC", "rhun", "arnor");

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
