package me.Plugins.SimpleFactions.War.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignProgressionService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

public final class WarDebugFormatter {
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private WarDebugFormatter() {}

	public static List<String> formatStatusLines(War war) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("id", war.getId());
		summary.put("status", war.getStatus() != null ? war.getStatus().toJson() : null);
		summary.put("goal", war.getGoal() != null ? war.getGoal().toJson() : null);
		summary.put("warType", war.getWarType() != null ? war.getWarType().toJson() : null);
		summary.put("attackerLeaderId", war.getAttackerLeaderId());
		summary.put("defenderLeaderId", war.getDefenderLeaderId());
		summary.put("targetTitleId", war.getTargetTitleId());
		summary.put("subjectFactionId", war.getSubjectFactionId());
		summary.put("relationTypeId", war.getRelationTypeId());
		summary.put("governmentLawId", war.getGovernmentLawId());
		summary.put("leadershipLawId", war.getLeadershipLawId());
		summary.put("targetSettlementId", war.getTargetSettlementId());
		summary.put("movementId", war.getMovementId());
		summary.put("objectiveProvinceId", war.getObjectiveProvinceId());
		summary.put("campaignStartProvinceId", war.getCampaignStartProvinceId());
		summary.put("campaignProvinces", war.getCampaignProvinces());
		summary.put("cursorIndex", war.getCursorIndex());
		summary.put("initiativeAttacker", war.getInitiativeAttacker());
		summary.put("initiativeDefender", war.getInitiativeDefender());
		summary.put("initiativeHolder", war.getInitiativeHolder() != null ? war.getInitiativeHolder().name() : null);
		summary.put("campaignPhase", war.getCampaignPhase() != null ? war.getCampaignPhase().toJson() : null);
		summary.put("objectiveHeldBy", war.getObjectiveHeldBy() != null ? war.getObjectiveHeldBy().toJson() : null);
		summary.put("whitePeaceProposedByAttacker", war.isWhitePeaceProposedByAttacker());
		summary.put("whitePeaceProposedByDefender", war.isWhitePeaceProposedByDefender());
		summary.put("campaignBattlesFought", war.getCampaignBattlesFought());
		summary.put("campaignScheduleIndex", war.getCampaignScheduleIndex());
		summary.put("campaignBattleSchedule", serializeCampaignSchedule(war, CampaignScheduleService.ScheduleLeg.INVASION));
		summary.put("campaignCounterScheduleIndex", war.getCampaignCounterScheduleIndex());
		summary.put("campaignCounterSchedule", serializeCampaignSchedule(war, CampaignScheduleService.ScheduleLeg.COUNTER));
		summary.put("occupiedByAttacker", war.getOccupiedByAttacker());
		summary.put("occupiedByDefender", war.getOccupiedByDefender());
		summary.put("lastBattleOccupied", war.getLastBattleOccupied());
		summary.put("cursorProvinceId", resolveCursorProvinceId(war));
		summary.put("nextBattleNodes", CampaignProgressionService.resolveNextBattleNodes(war));
		summary.put("battleSchedulePhase", war.getBattleSchedulePhase() != null ? war.getBattleSchedulePhase().toJson() : null);
		summary.put("battleDay", war.getBattleDay() != null ? war.getBattleDay().toString() : null);
		summary.put("scheduledBattleAt", war.getScheduledBattleAt() != null ? war.getScheduledBattleAt().toString() : null);
		summary.put("scheduledBattleHour", war.getScheduledBattleHour() > 0 ? war.getScheduledBattleHour() : null);
		summary.put("scheduledBattleProvinceId", war.getScheduledBattleProvinceId());
		summary.put("battleVoteCount", war.getBattleVotes() != null ? war.getBattleVotes().size() : 0);
		summary.put("autoresolveProposedByAttacker", war.isAutoresolveProposedByAttacker());
		summary.put("autoresolveProposedByDefender", war.isAutoresolveProposedByDefender());
		summary.put("postponementsThisCycle", war.getPostponementsThisCycle());
		summary.put("defenderChoiceResolved", war.isDefenderChoiceResolved());
		summary.put("forceQuorumNextClose", war.isForceQuorumNextClose() ? true : null);
		summary.put("startedAt", war.getStartedAt() != null ? war.getStartedAt().toString() : null);
		List<Map<String, Object>> commitmentRows = serializeCommitmentRows(
				WarManager.getCommitmentsForWar(war.getId()));
		summary.put("commitmentRows", commitmentRows);
		summary.put("commitments", commitmentRows.size());
		return List.of("§7" + GSON.toJson(summary));
	}

	private static List<Map<String, Object>> serializeCommitmentRows(List<WarCommitment> commitments) {
		List<Map<String, Object>> rows = new ArrayList<>();
		if (commitments == null || commitments.isEmpty()) {
			return rows;
		}
		for (WarCommitment commitment : commitments) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("factionId", commitment.factionId());
			if (commitment.sourceFactionId() != null) {
				row.put("sourceFactionId", commitment.sourceFactionId());
			}
			row.put("regimentId", commitment.regimentId());
			row.put("count", commitment.count());
			rows.add(row);
		}
		return rows;
	}

	private static List<Map<String, Object>> serializeCampaignSchedule(
			War war,
			CampaignScheduleService.ScheduleLeg leg) {
		List<Map<String, Object>> rows = new ArrayList<>();
		if (war == null) {
			return rows;
		}
		List<ScheduledCampaignBattle> schedule = CampaignScheduleService.scheduleListForLeg(war, leg);
		if (schedule == null) {
			return rows;
		}
		for (int index = 0; index < schedule.size(); index++) {
			ScheduledCampaignBattle slot = schedule.get(index);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("index", index);
			row.put("provinceId", slot.provinceId());
			row.put("kind", slot.kind() != null ? slot.kind().toJson() : null);
			row.put("required", slot.required());
			row.put("fortInstallationId", slot.fortInstallationId());
			row.put("portInstallationId", slot.portInstallationId());
			rows.add(row);
		}
		return rows;
	}

	private static Integer resolveCursorProvinceId(War war) {
		List<Integer> axis = war.getCampaignProvinces();
		if (axis == null || axis.isEmpty()) {
			return null;
		}
		int index = war.getCursorIndex();
		if (index < 0 || index >= axis.size()) {
			return null;
		}
		return axis.get(index);
	}
}
