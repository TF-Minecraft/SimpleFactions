package me.Plugins.SimpleFactions.Map.export;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiCopy;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

public final class WarMapExporter {
	private WarMapExporter() {
	}

	public static JsonArray exportWars(List<War> wars) {
		JsonArray exported = new JsonArray();
		if (wars == null) {
			return exported;
		}
		for (War war : wars) {
			if (!shouldExport(war)) {
				continue;
			}
			exported.add(exportWar(war));
		}
		return exported;
	}

	static boolean shouldExport(War war) {
		if (war == null || !war.isActive()) {
			return false;
		}
		if (war.getWarType() == WarType.RAID) {
			return false;
		}
		List<Integer> axis = war.getCampaignProvinces();
		return axis != null && !axis.isEmpty();
	}

	static JsonObject exportWar(War war) {
		JsonObject row = new JsonObject();
		row.addProperty("id", String.valueOf(war.getId()));
		row.addProperty("name", war.getName());
		if (war.getWarType() != null) {
			row.addProperty("war_type", war.getWarType().toJson());
		}
		if (war.getGoal() != null) {
			row.addProperty("goal", war.getGoal().toJson());
		}
		if (war.getStatus() != null) {
			row.addProperty("status", war.getStatus().toJson());
		}
		row.addProperty("attacker_leader_id", war.getAttackerLeaderId());
		row.addProperty("defender_leader_id", war.getDefenderLeaderId());
		row.add("belligerents", exportBelligerents(war));

		JsonArray axis = new JsonArray();
		for (int provinceId : war.getCampaignProvinces()) {
			axis.add(provinceId);
		}
		row.add("campaign_provinces", axis);
		row.addProperty("cursor_index", war.getCursorIndex());

		Integer objectiveProvinceId = war.getObjectiveProvinceId();
		if (objectiveProvinceId != null) {
			row.addProperty("objective_province_id", objectiveProvinceId);
		}

		row.addProperty("push_target", resolvePushTarget(war).toJson());
		row.addProperty("campaign_schedule_index", war.getCampaignScheduleIndex());
		row.addProperty("campaign_counter_schedule_index", war.getCampaignCounterScheduleIndex());
		row.add("campaign_battle_schedule", exportSchedule(war, ScheduleLeg.INVASION));

		JsonArray counterSchedule = exportSchedule(war, ScheduleLeg.COUNTER);
		if (!counterSchedule.isEmpty()) {
			row.add("campaign_counter_schedule", counterSchedule);
		}

		JsonObject attackerCapital = exportCapitalCoords(war.getAttackers() != null ? war.getAttackers().getLeader() : null);
		if (attackerCapital != null) {
			row.add("attacker_capital", attackerCapital);
		}
		JsonObject defenderCapital = exportCapitalCoords(war.getDefenders() != null ? war.getDefenders().getLeader() : null);
		if (defenderCapital != null) {
			row.add("defender_capital", defenderCapital);
		}
		row.add("occupied_by_attacker", OccupationMapExport.toIntArray(war.getOccupiedByAttacker()));
		row.add("occupied_by_defender", OccupationMapExport.toIntArray(war.getOccupiedByDefender()));
		return row;
	}

	private static CampaignPushTarget resolvePushTarget(War war) {
		CampaignPushTarget pushTarget = war.getPushTarget();
		if (pushTarget != null) {
			return pushTarget;
		}
		return CampaignCoalitionService.derivePushTargetFromLegacyPhase(
				war.getCampaignPhase(),
				war.getObjectiveHeldBy());
	}

	private static JsonArray exportBelligerents(War war) {
		Set<String> ids = new LinkedHashSet<>();
		collectBelligerentIds(war.getAttackers(), ids);
		collectBelligerentIds(war.getDefenders(), ids);
		JsonArray belligerents = new JsonArray();
		for (String id : ids) {
			belligerents.add(id);
		}
		return belligerents;
	}

	private static void collectBelligerentIds(Side side, Set<String> ids) {
		if (side == null) {
			return;
		}
		for (Participant participant : side.getMainParticipants()) {
			for (Faction faction : participant.getAllParticipatingFactions()) {
				if (faction != null && faction.getId() != null) {
					ids.add(faction.getId());
				}
			}
		}
	}

	private static JsonArray exportSchedule(War war, ScheduleLeg leg) {
		JsonArray schedule = new JsonArray();
		List<ScheduledCampaignBattle> slots = CampaignScheduleService.scheduleListForLeg(war, leg);
		if (slots == null || slots.isEmpty()) {
			return schedule;
		}
		ScheduleLeg activeLeg = CampaignScheduleService.activeLeg(war);
		int activeIndex = CampaignScheduleService.scheduleIndexForLeg(war, leg);
		String legJson = leg == ScheduleLeg.COUNTER ? "counter" : "invasion";
		for (int index = 0; index < slots.size(); index++) {
			schedule.add(exportSlot(war, slots.get(index), index, legJson, activeLeg, leg, activeIndex));
		}
		return schedule;
	}

	private static JsonObject exportSlot(
			War war,
			ScheduledCampaignBattle slot,
			int index,
			String legJson,
			ScheduleLeg activeLeg,
			ScheduleLeg leg,
			int activeIndex) {
		JsonObject row = new JsonObject();
		row.addProperty("schedule_index", index);
		row.addProperty("leg", legJson);
		row.addProperty("province_id", slot.provinceId());
		row.addProperty("kind", slot.kind().toJson());
		row.addProperty("kind_label", CampaignUiCopy.formatBattleKind(slot.kind()));
		row.addProperty("battle_type", slot.battleType().name());
		row.addProperty("required", slot.required());
		row.addProperty("status", resolveSlotStatus(index, activeIndex, leg, activeLeg));
		row.addProperty(
				"display_name",
				BattleNamingService.resolveScheduledDisplayName(
						war,
						leg,
						index,
						slot,
						slot.provinceId()));
		if (slot.fortInstallationId() != null) {
			row.addProperty("fort_installation_id", slot.fortInstallationId());
		} else {
			row.add("fort_installation_id", JsonNull.INSTANCE);
		}
		if (slot.portInstallationId() != null) {
			row.addProperty("port_installation_id", slot.portInstallationId());
		} else {
			row.add("port_installation_id", JsonNull.INSTANCE);
		}
		return row;
	}

	static String resolveSlotStatus(int index, int activeIndex, ScheduleLeg leg, ScheduleLeg activeLeg) {
		if (index < activeIndex) {
			return "fought";
		}
		if (index == activeIndex && leg == activeLeg) {
			return "next";
		}
		return "upcoming";
	}

	static JsonObject exportCapitalCoords(Faction leader) {
		if (leader == null) {
			return null;
		}
		int provinceId = leader.getCapital();
		if (provinceId <= 0) {
			return null;
		}
		JsonObject row = new JsonObject();
		row.addProperty("province_id", provinceId);
		SettlementHandler handler = leader.getSettlementHandler();
		if (handler != null) {
			Settlement settlement = handler.getByProvince(provinceId);
			if (settlement != null) {
				row.addProperty("center_x", settlement.getCenterX());
				row.addProperty("center_z", settlement.getCenterZ());
			}
		}
		return row;
	}
}
