package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import net.tfminecraft.simplefactions.database.CivilWarMemberMoveData;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.war.wounds.ForceKind;
import net.tfminecraft.simplefactions.war.wounds.TestForces;
import net.tfminecraft.simplefactions.war.wounds.WarLedger;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;

class WarDataRoundTripTest {
	private static final Gson GSON = new Gson();

	@Test
	void warData_gsonRoundTrip_preservesV3Fields() {
		WarData original = new WarData();
		original.schemaVersion = 3;
		original.id = 12;
		original.status = "active";
		original.initiativeHolder = "DEFENDER";
		original.initiativeHolderCoalition = CampaignSideRole.DEFENDER.toJson();
		original.pushTarget = CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL.toJson();
		original.postBattleChoicePhase = PostBattleChoicePhase.WINNER_PUSH_HOLD.toJson();
		original.postBattleWinnerCoalition = CampaignSideRole.DEFENDER.toJson();
		original.postBattleChoiceResolved = false;
		original.lastBattleOffensiveCoalition = CampaignSideRole.AGGRESSOR.toJson();
		original.holdPeaceProposalActive = true;
		original.defenderChoiceResolved = false;

		String json = GSON.toJson(original);
		WarData restored = GSON.fromJson(json, WarData.class);

		assertNotNull(restored);
		assertEquals(3, restored.schemaVersion);
		assertEquals(CampaignSideRole.DEFENDER.toJson(), restored.initiativeHolderCoalition);
		assertEquals(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL.toJson(), restored.pushTarget);
		assertEquals(PostBattleChoicePhase.WINNER_PUSH_HOLD.toJson(), restored.postBattleChoicePhase);
		assertEquals(CampaignSideRole.DEFENDER.toJson(), restored.postBattleWinnerCoalition);
		assertEquals(false, restored.postBattleChoiceResolved);
		assertEquals(CampaignSideRole.AGGRESSOR.toJson(), restored.lastBattleOffensiveCoalition);
		assertTrue(restored.holdPeaceProposalActive);
	}

	@Test
	void warData_gsonRoundTrip_preservesTheBattleLedger() {
		WarLedger ledger = new WarLedger();
		ledger.addBattle("battle-1", "fought", "2026-08-21T10:00:00Z", 159,
				new WarLedger.SideRecord(12, List.of(
						TestForces.faction("faction_a", TestForces.militia(2, 1), TestForces.paid("professional", 3)),
						TestForces.mercenary("guild_m", 2))),
				new WarLedger.SideRecord(4, TestForces.regiments("faction_b", 2)));
		ledger.addAdjustment(new WarLedger.Adjustment("faction_a", "professional", -3, "Staff", "bug", "2026-08-21T11:00:00Z"));
		ledger.addNote(new WarLedger.Note("2026-08-21T11:00:00Z", "Staff", "fixed"));
		WarData original = new WarData();
		original.id = 9;
		original.ledger = ledger.toData();

		WarData restored = GSON.fromJson(GSON.toJson(original), WarData.class);
		WarLedger reloaded = WarLedger.fromData(restored.ledger);

		assertEquals(ledger.battles(), reloaded.battles());
		assertEquals(ledger.adjustments(), reloaded.adjustments());
		assertEquals(ledger.notes(), reloaded.notes());
		assertEquals(ledger.factionWoundLives(), reloaded.factionWoundLives());
		assertEquals(ForceKind.MERCENARY, reloaded.battle(1).attacker().forces().get(1).kind());
	}

	@Test
	void warData_withoutALedger_loadsAnEmptyOne() {
		WarData restored = GSON.fromJson("{\"id\":9,\"commitments\":[{\"factionId\":\"a\"}]}", WarData.class);

		WarLedger ledger = WarLedger.fromData(restored.ledger);

		assertTrue(ledger.battles().isEmpty());
		assertTrue(ledger.factionWoundLives().isEmpty());
	}

	@Test
	void warData_gsonRoundTrip_preservesCivilWarMemberMoves() {
		WarData original = new WarData();
		original.civilWarWantedLeaderName = "dummy_11";
		CivilWarMemberMoveData row = new CivilWarMemberMoveData();
		row.player = "dummy_11";
		row.originGuildId = "guild-a";
		row.originWasGuildLeader = true;
		original.civilWarMemberMoves = new java.util.ArrayList<>(List.of(row));

		WarData restored = GSON.fromJson(GSON.toJson(original), WarData.class);

		assertEquals("dummy_11", restored.civilWarWantedLeaderName);
		assertEquals(1, restored.civilWarMemberMoves.size());
		assertEquals("guild-a", restored.civilWarMemberMoves.get(0).originGuildId);
		assertTrue(restored.civilWarMemberMoves.get(0).originWasGuildLeader);
	}
}
