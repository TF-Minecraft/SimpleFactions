package me.Plugins.SimpleFactions.War.campaign.schedule;

import java.util.List;

import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignScheduleLogger;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortControlService;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.enums.Terrain;

public final class CampaignBattlePlacer {
	private CampaignBattlePlacer() {
	}

	public static void placeBattle(
			CampaignScheduleBuildContext ctx,
			War war,
			ScheduleLeg leg,
			int provinceId,
			BattleTrigger trigger,
			CampaignCoalition advancing,
			String fortInstallationId,
			String portInstallationId) {
		if (ctx == null || war == null || leg == null || trigger == null || provinceId <= 0) {
			LogManager.line(
					"SKIP placeBattle invalid args leg=%s trigger=%s province=%d",
					leg,
					trigger,
					provinceId);
			return;
		}

		ProvinceManager provinceManager = SimpleFactions.getInstance().getProvinceManager();
		Province province = provinceManager.get(provinceId);
		boolean isSea = province != null && province.isValid() && province.getTerrain() == Terrain.SEA;

		LogManager.line(
				"placeBattle leg=%s trigger=%s province=%d sea=%s fort=%s port=%s",
				leg,
				trigger,
				provinceId,
				isSea,
				fortInstallationId,
				portInstallationId);

		if (isSea && trigger != BattleTrigger.NAVAL) {
			LogManager.line("  SKIP sea province for non-NAVAL trigger at province=%d", provinceId);
			return;
		}

		switch (trigger) {
			case NAVAL -> placeNaval(ctx, leg, provinceId, portInstallationId);
			case FORT_ZOC -> placeFortSiege(ctx, war, leg, provinceId, advancing, fortInstallationId);
			case OBJECTIVE -> placeOrUpgradeObjective(ctx, leg, provinceId);
			case CADENCE, BORDER -> appendFieldIfAbsent(ctx, leg, provinceId, trigger);
		}
	}

	private static void placeNaval(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			int provinceId,
			String portInstallationId) {
		if (portInstallationId == null || portInstallationId.isBlank()) {
			LogManager.line("  SKIP NAVAL blank port at province=%d", provinceId);
			return;
		}
		var portIds = ctx.portIdsFor(leg);
		if (portIds.contains(portInstallationId)) {
			LogManager.line("  SKIP NAVAL duplicate port=%s on leg=%s", portInstallationId, leg);
			return;
		}
		ScheduledCampaignBattle naval = new ScheduledCampaignBattle(
				provinceId,
				CampaignBattleKind.NAVAL,
				false,
				null,
				portInstallationId);
		if (leg == ScheduleLeg.INVASION) {
			ctx.invasion().add(0, naval);
			LogManager.line(
					"  PLACE NAVAL prepend invasion index=0 province=%d port=%s",
					provinceId,
					portInstallationId);
		} else {
			insertOrdered(ctx, leg, naval, "NAVAL");
		}
		portIds.add(portInstallationId);
	}

	private static void placeFortSiege(
			CampaignScheduleBuildContext ctx,
			War war,
			ScheduleLeg leg,
			int triggerProvinceId,
			CampaignCoalition advancing,
			String fortInstallationId) {
		if (leg == ScheduleLeg.INVASION && ctx.objectiveAxisIndex() >= 0) {
			int triggerIndex = axisIndexOf(ctx, triggerProvinceId);
			if (triggerIndex > ctx.objectiveAxisIndex()) {
				LogManager.line(
						"  SKIP SIEGE trigger=%d past objective axisIndex=%d > objectiveAxis=%d",
						triggerProvinceId,
						triggerIndex,
						ctx.objectiveAxisIndex());
				return;
			}
		}
		OperationalFort fort = ctx.fortIndex().fortForProvince(triggerProvinceId).orElse(null);
		if (fort == null || fort.id() == null) {
			LogManager.line("  SKIP SIEGE no fort ZOC at trigger=%d", triggerProvinceId);
			return;
		}
		if (fortInstallationId != null && !fortInstallationId.isBlank() && !fortInstallationId.equals(fort.id())) {
			LogManager.line(
					"  SKIP SIEGE fort id mismatch expected=%s resolved=%s trigger=%d",
					fortInstallationId,
					fort.id(),
					triggerProvinceId);
			return;
		}
		if (ctx.scheduledFortIds().contains(fort.id())) {
			LogManager.line(
					"  SKIP SIEGE fort already scheduled id=%s trigger=%d",
					fort.id(),
					triggerProvinceId);
			return;
		}
		if (!FortControlService.isEnemyControlled(war, fort.id(), advancing)) {
			LogManager.line(
					"  SKIP SIEGE fort not enemy-controlled id=%s trigger=%d",
					fort.id(),
					triggerProvinceId);
			return;
		}
		boolean homeOnAxis = axisIndexOf(ctx, fort.province()) < Integer.MAX_VALUE;
		Integer chronologyProvinceId = !homeOnAxis && triggerProvinceId != fort.province()
				? triggerProvinceId
				: null;
		ScheduledCampaignBattle siege = new ScheduledCampaignBattle(
				fort.province(),
				CampaignBattleKind.SIEGE,
				false,
				fort.id(),
				null,
				chronologyProvinceId);
		LogManager.line(
				"  SIEGE resolved fort=%s home=%d trigger=%d homeOnAxis=%s chrono=%s",
				fort.id(),
				fort.province(),
				triggerProvinceId,
				homeOnAxis,
				chronologyProvinceId);
		insertOrdered(ctx, leg, siege, "SIEGE");
		removeOptionalFieldsReplacedBySiege(ctx, leg, siege);
		ctx.scheduledFortIds().add(fort.id());
	}

	private static void placeOrUpgradeObjective(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			int provinceId) {
		List<ScheduledCampaignBattle> schedule = ctx.scheduleFor(leg);
		for (int index = 0; index < schedule.size(); index++) {
			ScheduledCampaignBattle slot = schedule.get(index);
			if (slot.provinceId() != provinceId || slot.kind() != CampaignBattleKind.FIELD) {
				continue;
			}
			if (!slot.required()) {
				schedule.set(index, new ScheduledCampaignBattle(
						slot.provinceId(),
						CampaignBattleKind.FIELD,
						true,
						slot.fortInstallationId(),
						slot.portInstallationId()));
				LogManager.line(
						"  UPGRADE OBJECTIVE existing FIELD at province=%d index=%d",
						provinceId,
						index);
			} else {
				LogManager.line(
						"  SKIP OBJECTIVE already required FIELD at province=%d index=%d",
						provinceId,
						index);
			}
			return;
		}
		ScheduledCampaignBattle objective = new ScheduledCampaignBattle(provinceId, CampaignBattleKind.FIELD, true, null);
		insertOrdered(ctx, leg, objective, "OBJECTIVE");
	}

	private static void appendFieldIfAbsent(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			int provinceId,
			BattleTrigger trigger) {
		List<ScheduledCampaignBattle> schedule = ctx.scheduleFor(leg);
		for (ScheduledCampaignBattle slot : schedule) {
			if (slot.provinceId() == provinceId && slot.kind() == CampaignBattleKind.FIELD) {
				LogManager.line(
						"  SKIP %s duplicate FIELD at province=%d",
						trigger,
						provinceId);
				return;
			}
		}
		if (!isObjectiveProvince(ctx, provinceId) && siegeOwnsFightTile(schedule, provinceId)) {
			LogManager.line(
					"  SKIP %s FIELD at province=%d; siege already owns that fight-order tile",
					trigger,
					provinceId);
			return;
		}
		ScheduledCampaignBattle field = new ScheduledCampaignBattle(provinceId, CampaignBattleKind.FIELD, false, null);
		insertOrdered(ctx, leg, field, trigger.name());
	}

	private static void removeOptionalFieldsReplacedBySiege(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			ScheduledCampaignBattle siege) {
		List<ScheduledCampaignBattle> schedule = ctx.scheduleFor(leg);
		int sortTile = siege.sortProvinceId();
		int home = siege.provinceId();
		schedule.removeIf(slot -> {
			if (slot.kind() != CampaignBattleKind.FIELD || slot.required()) {
				return false;
			}
			if (slot.provinceId() != sortTile && slot.provinceId() != home) {
				return false;
			}
			if (isObjectiveProvince(ctx, slot.provinceId())) {
				return false;
			}
			LogManager.line(
					"  DROP optional FIELD at province=%d; siege %s owns fight-order tile %d",
					slot.provinceId(),
					siege.fortInstallationId(),
					sortTile);
			return true;
		});
	}

	private static boolean siegeOwnsFightTile(List<ScheduledCampaignBattle> schedule, int provinceId) {
		for (ScheduledCampaignBattle slot : schedule) {
			if (slot.kind() == CampaignBattleKind.SIEGE
					&& (slot.provinceId() == provinceId || slot.sortProvinceId() == provinceId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isObjectiveProvince(CampaignScheduleBuildContext ctx, int provinceId) {
		int objectiveIndex = ctx.objectiveAxisIndex();
		return objectiveIndex >= 0
				&& objectiveIndex < ctx.axis().size()
				&& ctx.axis().get(objectiveIndex) == provinceId;
	}

	private static void insertOrdered(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			ScheduledCampaignBattle slot,
			String reason) {
		List<ScheduledCampaignBattle> schedule = ctx.scheduleFor(leg);
		for (int index = 0; index < schedule.size(); index++) {
			if (shouldComeBefore(ctx, leg, slot, schedule.get(index))) {
				schedule.add(index, slot);
				LogManager.line(
						"  PLACE %s at index=%d | %s | %s",
						reason,
						index,
						CampaignScheduleLogger.formatSlot(slot, ctx.axis()),
						CampaignScheduleLogger.fightOrderSummary(ctx, leg, slot));
				return;
			}
		}
		schedule.add(slot);
		LogManager.line(
				"  PLACE %s at index=%d (append) | %s | %s",
				reason,
				schedule.size() - 1,
				CampaignScheduleLogger.formatSlot(slot, ctx.axis()),
				CampaignScheduleLogger.fightOrderSummary(ctx, leg, slot));
	}

	private static boolean shouldComeBefore(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			ScheduledCampaignBattle candidate,
			ScheduledCampaignBattle existing) {
		int candidateKey = fightOrderKey(ctx, leg, candidate);
		int existingKey = fightOrderKey(ctx, leg, existing);
		if (candidateKey != existingKey) {
			return candidateKey < existingKey;
		}
		return kindPriority(candidate) < kindPriority(existing);
	}

	private static int fightOrderKey(
			CampaignScheduleBuildContext ctx,
			ScheduleLeg leg,
			ScheduledCampaignBattle slot) {
		int axisIndex = axisIndexOf(ctx, slot.sortProvinceId());
		return leg == ScheduleLeg.INVASION ? axisIndex : -axisIndex;
	}

	private static int axisIndexOf(CampaignScheduleBuildContext ctx, int provinceId) {
		int index = ctx.axis().indexOf(provinceId);
		return index >= 0 ? index : Integer.MAX_VALUE;
	}

	private static int kindPriority(ScheduledCampaignBattle slot) {
		if (slot.kind() == CampaignBattleKind.SIEGE) {
			return 1;
		}
		if (slot.kind() == CampaignBattleKind.FIELD && !slot.required()) {
			return 2;
		}
		if (slot.kind() == CampaignBattleKind.FIELD && slot.required()) {
			return 3;
		}
		if (slot.kind() == CampaignBattleKind.NAVAL) {
			return 4;
		}
		return 5;
	}
}
