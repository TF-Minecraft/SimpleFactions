package me.Plugins.SimpleFactions.War.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import me.Plugins.SimpleFactions.Database.CivilWarMemberMoveData;
import me.Plugins.SimpleFactions.Database.CommitmentData;
import me.Plugins.SimpleFactions.Database.WarData;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;

class WarDataRoundTripTest {
	private static final Gson GSON = new Gson();

	@Test
	void warData_gsonRoundTrip_preservesV3Fields() {
		WarData original = new WarData();
		original.schemaVersion = 3;
		original.id = 12;
		original.status = "active";
		original.initiativeHolder = "DEFENDER";
		original.initiativeHolderCoalition = CampaignCoalition.DEFENDER.toJson();
		original.pushTarget = CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL.toJson();
		original.postBattleChoicePhase = PostBattleChoicePhase.WINNER_PUSH_HOLD.toJson();
		original.postBattleWinnerCoalition = CampaignCoalition.DEFENDER.toJson();
		original.postBattleChoiceResolved = false;
		original.lastBattleOffensiveCoalition = CampaignCoalition.AGGRESSOR.toJson();
		original.holdPeaceProposalActive = true;
		original.defenderChoiceResolved = false;

		String json = GSON.toJson(original);
		WarData restored = GSON.fromJson(json, WarData.class);

		assertNotNull(restored);
		assertEquals(3, restored.schemaVersion);
		assertEquals(CampaignCoalition.DEFENDER.toJson(), restored.initiativeHolderCoalition);
		assertEquals(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL.toJson(), restored.pushTarget);
		assertEquals(PostBattleChoicePhase.WINNER_PUSH_HOLD.toJson(), restored.postBattleChoicePhase);
		assertEquals(CampaignCoalition.DEFENDER.toJson(), restored.postBattleWinnerCoalition);
		assertEquals(false, restored.postBattleChoiceResolved);
		assertEquals(CampaignCoalition.AGGRESSOR.toJson(), restored.lastBattleOffensiveCoalition);
		assertTrue(restored.holdPeaceProposalActive);
	}

	@Test
	void warData_gsonRoundTrip_preservesCommitments() {
		WarData original = new WarData();
		original.id = 9;
		CommitmentData ownRow = new CommitmentData();
		ownRow.factionId = "faction_a";
		ownRow.regimentId = "professional";
		ownRow.count = 7;
		ownRow.committedAt = "2026-08-21T10:00:00Z";

		CommitmentData levyRow = new CommitmentData();
		levyRow.factionId = "faction_a";
		levyRow.sourceFactionId = "subject_a";
		levyRow.regimentId = "levy";
		levyRow.count = 4;
		levyRow.committedAt = "2026-08-21T10:00:00Z";

		original.commitments = new java.util.ArrayList<>(List.of(ownRow, levyRow));

		String json = GSON.toJson(original);
		WarData restored = GSON.fromJson(json, WarData.class);

		assertNotNull(restored.commitments);
		assertEquals(2, restored.commitments.size());
		assertEquals("professional", restored.commitments.get(0).regimentId);
		assertEquals(Integer.valueOf(7), Integer.valueOf(restored.commitments.get(0).count));
		assertEquals("subject_a", restored.commitments.get(1).sourceFactionId);
		assertEquals(Integer.valueOf(4), Integer.valueOf(restored.commitments.get(1).count));
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
