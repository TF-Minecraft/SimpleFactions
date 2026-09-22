package me.Plugins.SimpleFactions.War.campaign.ui;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignProgressionService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignRetreatService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;

import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.pathfinder.BelligerentTerritory;
import me.Plugins.SimpleFactions.War.pathfinder.ProvinceOwnerLookup;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignScheduleCountdown;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiCopy;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public final class CampaignRouteRenderer {
	private CampaignRouteRenderer() {}

	public static List<CampaignRouteEntry> buildRouteEntries(War war) {
		if (war == null) {
			return List.of();
		}
		List<CampaignRouteEntry> entries = new ArrayList<>();
		appendLegEntries(war, entries, ScheduleLeg.INVASION);
		appendLegEntries(war, entries, ScheduleLeg.COUNTER);
		entries.sort(routeEntryComparator());
		return entries;
	}

	private static Comparator<CampaignRouteEntry> routeEntryComparator() {
		return Comparator
				.comparingInt((CampaignRouteEntry entry) ->
						entry.axisIndex() < 0 ? Integer.MAX_VALUE : entry.axisIndex())
				.thenComparingInt(entry -> entry.scheduleLeg() == ScheduleLeg.INVASION ? 0 : 1)
				.thenComparingInt(CampaignRouteEntry::scheduleIndex);
	}

	public static boolean isBorderFirstBattleSlot(War war, CampaignRouteEntry entry) {
		if (war == null || entry == null || !entry.hasBattleSlot()) {
			return false;
		}
		if (entry.scheduleLeg() != ScheduleLeg.INVASION) {
			return false;
		}
		if (!CampaignScheduleService.hasScheduleForLeg(war, ScheduleLeg.INVASION)) {
			return false;
		}
		List<ScheduledCampaignBattle> invasion = CampaignScheduleService.scheduleListForLeg(war, ScheduleLeg.INVASION);
		int firstLandIndex = firstLandInvasionIndex(invasion);
		return firstLandIndex >= 0 && entry.scheduleIndex() == firstLandIndex;
	}

	private static int firstLandInvasionIndex(List<ScheduledCampaignBattle> invasion) {
		if (invasion == null) {
			return -1;
		}
		for (int index = 0; index < invasion.size(); index++) {
			CampaignBattleKind kind = invasion.get(index).kind();
			if (kind != CampaignBattleKind.NAVAL && kind != CampaignBattleKind.NAVAL_INVASION) {
				return index;
			}
		}
		return -1;
	}

	private static void appendLegEntries(War war, List<CampaignRouteEntry> entries, ScheduleLeg leg) {
		if (!CampaignScheduleService.hasScheduleForLeg(war, leg)) {
			return;
		}
		List<ScheduledCampaignBattle> schedule = CampaignScheduleService.scheduleListForLeg(war, leg);
		for (int i = 0; i < schedule.size(); i++) {
			ScheduledCampaignBattle slot = schedule.get(i);
			int sortProvinceId = slot.sortProvinceId();
			entries.add(new CampaignRouteEntry(slot.provinceId(), axisIndexFor(war, sortProvinceId), i, leg));
		}
	}

	private static int axisIndexFor(War war, int provinceId) {
		List<Integer> axis = war.getCampaignProvinces();
		if (axis == null || axis.isEmpty()) {
			return -1;
		}
		return axis.indexOf(provinceId);
	}

	public static List<Integer> actionProvinceIds(War war) {
		return CampaignProgressionService.resolveNextBattleNodes(war);
	}

	public static Material resolveMaterial(
			War war,
			Faction viewer,
			int provinceId,
			ProvinceOwnerLookup owners) {
		return resolveOwnershipMaterial(war, viewer, provinceId, owners);
	}

	public static Material resolveRouteEntryMaterial(
			War war,
			Faction viewer,
			CampaignRouteEntry entry,
			ScheduledCampaignBattle slot,
			ProvinceOwnerLookup owners) {
		if (slot != null) {
			if (slot.kind() == CampaignBattleKind.NAVAL) {
				return Material.TRIDENT;
			}
			if (slot.kind() == CampaignBattleKind.NAVAL_INVASION) {
				return Material.IRON_SWORD;
			}
			if (entry.hasBattleSlot()
					&& entry.scheduleLeg() == CampaignScheduleService.activeLeg(war)
					&& entry.scheduleIndex() == CampaignScheduleService.getActiveScheduleIndex(war)) {
				return Material.GREEN_CONCRETE;
			}
			if (entry.hasBattleSlot()
					&& entry.scheduleIndex() < CampaignScheduleService.scheduleIndexForLeg(war, entry.scheduleLeg())) {
				return Material.GRAY_CONCRETE;
			}
		}
		return resolveOwnershipMaterial(war, viewer, entry.provinceId(), owners);
	}

	public static Material resolveOwnershipMaterial(
			War war,
			Faction viewer,
			int provinceId,
			ProvinceOwnerLookup owners) {
		BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners);
		Side viewerSide = war.getSide(viewer);
		if (viewerSide == null) {
			return Material.RED_CONCRETE;
		}
		boolean viewerIsAttacker = viewerSide.equals(war.getAttackers());
		boolean provinceOwnedByAttacker = territory.isAttackerSide(provinceId);
		if (territory.isNeutral(provinceId)) {
			return Material.GRAY_CONCRETE;
		}
		if (viewerIsAttacker == provinceOwnedByAttacker) {
			return Material.BLUE_CONCRETE;
		}
		return Material.RED_CONCRETE;
	}

	public static boolean isCursorIndex(War war, int axisIndex) {
		return war != null && war.getCursorIndex() == axisIndex;
	}

	public static List<String> buildRouteLore(War war, int provinceId, ProvinceOwnerLookup owners) {
		return buildRouteLore(war, new CampaignRouteEntry(provinceId, -1, -1), owners);
	}

	public static List<String> buildRouteLore(War war, CampaignRouteEntry entry, ProvinceOwnerLookup owners) {
		List<String> lore = new ArrayList<>();
		if (war == null || entry == null) {
			return lore;
		}
		int provinceId = entry.provinceId();

		String objectiveLine = resolveObjectiveLine(war, entry);
		if (objectiveLine != null) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.OBJECTIVE + objectiveLine));
		}

		if (entry.hasBattleSlot()) {
			CampaignScheduleService.slotAt(war, entry.scheduleIndex(), entry.scheduleLeg())
					.ifPresent(slot -> appendBattleKindLine(lore, slot));
		}

		appendRealmLines(lore, war, provinceId, owners);

		boolean conceded = entry.hasBattleSlot()
				&& CampaignRetreatService.isSlotConceded(war, entry.scheduleLeg(), entry.scheduleIndex());
		boolean fought = entry.hasBattleSlot() && isFoughtSlot(war, entry);
		if (entry.hasBattleSlot() && !conceded && !fought) {
			CampaignBattleIconLore.appendSoldiers(lore, war);
		}

		if (conceded) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.MUTED + CampaignUiCopy.RETREATED_LABEL));
		} else if (fought) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.MUTED + CampaignUiCopy.FOUGHT_LABEL));
		} else if (entry.hasBattleSlot()
				&& entry.scheduleLeg() == CampaignScheduleService.activeLeg(war)
				&& entry.scheduleIndex() == CampaignScheduleService.getActiveScheduleIndex(war)) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.NEXT_BATTLE + "Next battle"));
			CampaignScheduleCountdown.formatNextMilestone(war, CampaignClock.now())
					.ifPresent(text -> lore.add(StringFormatter.formatHex(CampaignUiCopy.MUTED + text)));
			CampaignBattleIconLore.appendVehiclesIfLocked(lore, war);
		}
		return lore;
	}

	static boolean isFoughtSlot(War war, CampaignRouteEntry entry) {
		return entry.hasBattleSlot()
				&& entry.scheduleIndex() < CampaignScheduleService.scheduleIndexForLeg(war, entry.scheduleLeg());
	}

	private static void appendBattleKindLine(List<String> lore, ScheduledCampaignBattle slot) {
		String label = CampaignUiCopy.formatBattleKind(slot.kind());
		if (label != null) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.BATTLE_KIND + label));
		}
	}

	private static String resolveObjectiveLine(War war, CampaignRouteEntry entry) {
		if (entry.hasBattleSlot()) {
			Optional<ScheduledCampaignBattle> slot = CampaignScheduleService.slotAt(
					war,
					entry.scheduleIndex(),
					entry.scheduleLeg());
			if (slot.isEmpty()) {
				return null;
			}
			ScheduledCampaignBattle battle = slot.get();
			if (battle.kind() != CampaignBattleKind.FIELD || !battle.required()) {
				return null;
			}
		}
		return resolveObjectiveLineForProvince(war, entry.provinceId());
	}

	private static String resolveObjectiveLineForProvince(War war, int provinceId) {
		Integer attackerCapital = war.getAttackers().getLeader().getCapital();
		if (attackerCapital != null && attackerCapital > 0 && attackerCapital == provinceId) {
			return "Attacker Capital";
		}
		Integer defenderCapital = war.getDefenders().getLeader().getCapital();
		if (defenderCapital != null && defenderCapital > 0 && defenderCapital == provinceId) {
			return "Defender Capital";
		}
		Integer objectiveId = war.getObjectiveProvinceId();
		if (objectiveId == null || objectiveId != provinceId) {
			return null;
		}
		ObjectiveHolder holder = war.getObjectiveHeldBy();
		if (holder == ObjectiveHolder.ATTACKER) {
			return "Attacker Target Region";
		}
		return "Defender Target Region";
	}

	private static void appendRealmLines(
			List<String> lore,
			War war,
			int provinceId,
			ProvinceOwnerLookup owners) {
		BelligerentTerritory territory = BelligerentTerritory.fromWar(war, owners);
		Faction owner = TitleManager.getByProvince(provinceId);
		if (owner == null) {
			return;
		}

		boolean attackerSide = territory.isAttackerSide(provinceId);
		boolean defenderSide = territory.isDefenderSide(provinceId);
		if (!attackerSide && !defenderSide) {
			return;
		}

		Faction sideLeader = attackerSide
				? war.getAttackers().getLeader()
				: war.getDefenders().getLeader();
		String leaderName = sideLeader.getName();
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Part of " + CampaignUiCopy.VALUE + leaderName + CampaignUiCopy.LABEL + " Realm"));

		if (!normalizeId(owner.getId()).equals(normalizeId(sideLeader.getId()))) {
			lore.add(StringFormatter.formatHex(CampaignUiCopy.MUTED + "(Owned by " + owner.getName() + ")"));
		}
	}

	private static String normalizeId(String id) {
		return id == null ? null : id.toLowerCase(Locale.ROOT);
	}
}
