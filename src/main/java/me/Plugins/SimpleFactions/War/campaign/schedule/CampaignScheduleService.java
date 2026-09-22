package me.Plugins.SimpleFactions.War.campaign.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortControlService;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.installation.Installation;

public final class CampaignScheduleService {
	public enum ScheduleLeg {
		INVASION,
		COUNTER
	}

	private CampaignScheduleService() {
	}

	/** True when the invasion leg schedule is non-empty (GUI / route rendering). */
	public static boolean hasSchedule(War war) {
		return war != null
				&& war.getCampaignBattleSchedule() != null
				&& !war.getCampaignBattleSchedule().isEmpty();
	}

	public static boolean hasActiveSchedule(War war) {
		if (war == null) {
			return false;
		}
		List<ScheduledCampaignBattle> schedule = activeScheduleList(war);
		return schedule != null && !schedule.isEmpty();
	}

	public static ScheduleLeg activeLeg(War war) {
		return switch (CampaignCapabilityService.effectivePushTarget(war)) {
			case TOWARD_AGGRESSOR_CAPITAL -> ScheduleLeg.COUNTER;
			case TOWARD_OBJECTIVE, RETAKE_OBJECTIVE -> ScheduleLeg.INVASION;
		};
	}

	static List<ScheduledCampaignBattle> activeScheduleList(War war) {
		return activeLeg(war) == ScheduleLeg.COUNTER
				? war.getCampaignCounterSchedule()
				: war.getCampaignBattleSchedule();
	}

	static int activeScheduleIndex(War war) {
		return activeLeg(war) == ScheduleLeg.COUNTER
				? war.getCampaignCounterScheduleIndex()
				: war.getCampaignScheduleIndex();
	}

	public static int getActiveScheduleIndex(War war) {
		return activeScheduleIndex(war);
	}

	public static List<ScheduledCampaignBattle> scheduleListForLeg(War war, ScheduleLeg leg) {
		if (war == null) {
			return List.of();
		}
		return leg == ScheduleLeg.COUNTER
				? war.getCampaignCounterSchedule()
				: war.getCampaignBattleSchedule();
	}

	public static int scheduleIndexForLeg(War war, ScheduleLeg leg) {
		if (war == null) {
			return 0;
		}
		return leg == ScheduleLeg.COUNTER
				? war.getCampaignCounterScheduleIndex()
				: war.getCampaignScheduleIndex();
	}

	public static boolean hasScheduleForLeg(War war, ScheduleLeg leg) {
		List<ScheduledCampaignBattle> schedule = scheduleListForLeg(war, leg);
		return schedule != null && !schedule.isEmpty();
	}

	static void setActiveScheduleIndex(War war, int index) {
		if (activeLeg(war) == ScheduleLeg.COUNTER) {
			war.setCampaignCounterScheduleIndex(index);
		} else {
			war.setCampaignScheduleIndex(index);
		}
	}

	static void setActiveScheduleList(War war, List<ScheduledCampaignBattle> schedule) {
		if (activeLeg(war) == ScheduleLeg.COUNTER) {
			war.setCampaignCounterSchedule(schedule);
		} else {
			war.setCampaignBattleSchedule(schedule);
		}
	}

	public static Optional<ScheduledCampaignBattle> slotAt(War war, int index) {
		return slotAt(war, index, ScheduleLeg.INVASION);
	}

	public static Optional<ScheduledCampaignBattle> slotAt(War war, int index, ScheduleLeg leg) {
		if (war == null || leg == null) {
			return Optional.empty();
		}
		List<ScheduledCampaignBattle> schedule = scheduleListForLeg(war, leg);
		if (schedule == null || index < 0 || index >= schedule.size()) {
			return Optional.empty();
		}
		return Optional.of(schedule.get(index));
	}

	private static Optional<ScheduledCampaignBattle> activeSlotAt(War war, int index) {
		if (war == null) {
			return Optional.empty();
		}
		List<ScheduledCampaignBattle> schedule = activeScheduleList(war);
		if (schedule == null || index < 0 || index >= schedule.size()) {
			return Optional.empty();
		}
		return Optional.of(schedule.get(index));
	}

	public static Optional<ScheduledCampaignBattle> slotForProvince(War war, int provinceId) {
		if (!hasActiveSchedule(war)) {
			return Optional.empty();
		}
		Optional<ScheduledCampaignBattle> current = currentSlotWithoutReSiege(war);
		if (current.isPresent() && current.get().provinceId() == provinceId) {
			return current;
		}
		List<ScheduledCampaignBattle> schedule = activeScheduleList(war);
		int fromIndex = Math.max(0, activeScheduleIndex(war));
		for (int i = fromIndex; i < schedule.size(); i++) {
			ScheduledCampaignBattle slot = schedule.get(i);
			if (slot.provinceId() == provinceId) {
				return Optional.of(slot);
			}
		}
		return Optional.empty();
	}

	public static Optional<Integer> firstOnAxisScheduleProvince(War war) {
		return firstOnAxisScheduleIndex(war, ScheduleLeg.INVASION)
				.flatMap(index -> slotAt(war, index).map(ScheduledCampaignBattle::provinceId));
	}

	public static Optional<Integer> firstOnAxisScheduleIndex(War war) {
		return firstOnAxisScheduleIndex(war, ScheduleLeg.INVASION);
	}

	public static Optional<Integer> firstOnAxisScheduleIndex(War war, ScheduleLeg leg) {
		if (!hasScheduleForLeg(war, leg) || war.getCampaignProvinces() == null) {
			return Optional.empty();
		}
		List<Integer> axis = war.getCampaignProvinces();
		List<ScheduledCampaignBattle> schedule = scheduleListForLeg(war, leg);
		for (int i = 0; i < schedule.size(); i++) {
			if (axis.contains(schedule.get(i).provinceId())) {
				return Optional.of(i);
			}
		}
		return Optional.empty();
	}

	public static String resolveInstallationName(String installationId) {
		if (installationId == null || installationId.isBlank()) {
			return null;
		}
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			for (Installation installation : faction.getInstallationHandler().getAll()) {
				if (installationId.equals(installation.getId())) {
					return installation.getName();
				}
			}
		}
		return installationId;
	}

	private static Optional<ScheduledCampaignBattle> currentSlotWithoutReSiege(War war) {
		if (!hasActiveSchedule(war)) {
			return Optional.empty();
		}
		return activeSlotAt(war, activeScheduleIndex(war));
	}

	public static Optional<ScheduledCampaignBattle> currentSlot(War war) {
		if (!hasActiveSchedule(war)) {
			return Optional.empty();
		}
		ensureReSiegeInsert(war);
		return activeSlotAt(war, activeScheduleIndex(war));
	}

	/** Current slot on the active leg without inserting a re-siege battle. */
	public static Optional<ScheduledCampaignBattle> slotAtActiveIndex(War war) {
		if (!hasActiveSchedule(war)) {
			return Optional.empty();
		}
		return activeSlotAt(war, activeScheduleIndex(war));
	}

	public static void advanceIndex(War war) {
		if (war == null) {
			return;
		}
		setActiveScheduleIndex(war, activeScheduleIndex(war) + 1);
	}

	public static void insertSiegeAtCurrentIndex(War war, OperationalFort fort, int axisProvinceId) {
		if (war == null || fort == null || fort.id() == null) {
			return;
		}
		List<ScheduledCampaignBattle> schedule = new ArrayList<>(activeScheduleList(war));
		int index = Math.max(0, Math.min(activeScheduleIndex(war), schedule.size()));
		schedule.add(index, new ScheduledCampaignBattle(
				axisProvinceId,
				CampaignBattleKind.SIEGE,
				false,
				fort.id()));
		setActiveScheduleList(war, schedule);
	}

	public static void ensureReSiegeInsert(War war) {
		ensureReSiegeInsert(war, FortZocIndex.fromGameState());
	}

	static void ensureReSiegeInsert(War war, FortZocIndex fortIndex) {
		if (!hasActiveSchedule(war) || !CampaignCapabilityService.isValidWar(war) || fortIndex == null) {
			return;
		}
		CampaignCoalition advancing = CampaignCoalitionService.getInitiativeHolderCoalition(war);
		if (advancing == null) {
			return;
		}

		int index = activeScheduleIndex(war);
		Optional<ScheduledCampaignBattle> current = activeSlotAt(war, index);

		for (int axisIndex : advancingAxisIndices(war)) {
			int provinceId = war.getCampaignProvinces().get(axisIndex);
			Optional<OperationalFort> fort = fortIndex.fortForProvince(provinceId);
			if (fort.isEmpty()) {
				continue;
			}
			OperationalFort operationalFort = fort.get();
			if (!FortControlService.isEnemyControlled(war, operationalFort.id(), advancing)) {
				continue;
			}
			if (current.isPresent() && isSiegeForFort(current.get(), operationalFort.id())) {
				return;
			}
			if (scheduleAlreadyHasSiegeForFort(war, index, operationalFort.id())) {
				return;
			}
			insertSiegeAtCurrentIndex(war, operationalFort, provinceId);
			return;
		}
	}

	private static boolean scheduleAlreadyHasSiegeForFort(War war, int fromIndex, String fortInstallationId) {
		List<ScheduledCampaignBattle> schedule = activeScheduleList(war);
		for (int i = fromIndex; i < schedule.size(); i++) {
			if (isSiegeForFort(schedule.get(i), fortInstallationId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSiegeForFort(ScheduledCampaignBattle slot, String fortInstallationId) {
		if (slot == null || slot.kind() != CampaignBattleKind.SIEGE) {
			return false;
		}
		if (fortInstallationId == null) {
			return slot.fortInstallationId() != null;
		}
		return fortInstallationId.equals(slot.fortInstallationId());
	}

	private static List<Integer> advancingAxisIndices(War war) {
		List<Integer> indices = new ArrayList<>();
		int cursor = war.getCursorIndex();
		CampaignPushTarget pushTarget = CampaignCapabilityService.effectivePushTarget(war);
		int objectiveIndex = CampaignCapabilityService.objectiveIndex(war);

		switch (pushTarget) {
			case TOWARD_OBJECTIVE -> {
				if (objectiveIndex < 0) {
					return indices;
				}
				int step = Integer.compare(objectiveIndex, cursor);
				if (step == 0) {
					indices.add(cursor);
					return indices;
				}
				for (int i = cursor + step; ; i += step) {
					indices.add(i);
					if (i == objectiveIndex) {
						break;
					}
				}
			}
			case TOWARD_AGGRESSOR_CAPITAL -> {
				for (int i = cursor - 1; i >= 0; i--) {
					indices.add(i);
				}
			}
			case RETAKE_OBJECTIVE -> {
				if (objectiveIndex >= 0) {
					indices.add(objectiveIndex);
				}
			}
		}
		return indices;
	}
}
