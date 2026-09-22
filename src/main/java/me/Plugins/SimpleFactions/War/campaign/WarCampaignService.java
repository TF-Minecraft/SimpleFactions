package me.Plugins.SimpleFactions.War.campaign;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignNavyGate;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.campaign.ObjectiveProvincePicker;
import me.Plugins.SimpleFactions.War.pathfinder.BelligerentTerritory;
import me.Plugins.SimpleFactions.War.pathfinder.PathfinderResult;
import me.Plugins.SimpleFactions.War.pathfinder.ProvincePathfinder;
import me.Plugins.SimpleFactions.War.pathfinder.TitleManagerProvinceOwnerLookup;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleBuilder;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleTrimmer;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleValidator;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortControlService;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.PortSeaZocIndex;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignScheduleLogger;
import me.Plugins.SimpleFactions.Managers.LogManager;

public class WarCampaignService {
	private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(WarCampaignService.class.getName());

	private final ObjectiveProvincePicker picker;
	private final ProvincePathfinder pathfinder;

	public WarCampaignService(ProvinceManager pm) {
		this.picker = new ObjectiveProvincePicker(pm);
		this.pathfinder = new ProvincePathfinder(pm, new TitleManagerProvinceOwnerLookup());
	}

	public boolean populateCampaign(War war) {
		if (war == null || !war.isActive()) {
			return false;
		}

		LogManager.beginSession("populateCampaign warId=" + war.getId());
		LogManager.war("POPULATE warId=%d attacker=%s defender=%s",
				war.getId(),
				war.getAttackers() != null && war.getAttackers().getLeader() != null
						? war.getAttackers().getLeader().getId()
						: "-",
				war.getDefenders() != null && war.getDefenders().getLeader() != null
						? war.getDefenders().getLeader().getId()
						: "-");
		try {
			return populateCampaignInternal(war);
		} finally {
			LogManager.flush();
		}
	}

	private boolean populateCampaignInternal(War war) {

		Faction defender = war.getDefenders().getLeader();
		Faction attacker = war.getAttackers().getLeader();
		OptionalInt regionalObjective = picker.pickObjective(war, defender);
		if (regionalObjective.isEmpty()) {
			LogManager.line("FAIL no regional objective");
			return false;
		}
		LogManager.line("regionalObjective=%d", regionalObjective.getAsInt());

		int attackerCapital = attacker.getCapital();
		if (attackerCapital <= 0) {
			return false;
		}

		PathfinderResult borderResult = pathfinder.computeCampaignLine(war, regionalObjective.getAsInt());
		if (!borderResult.isFound()) {
			LogManager.line("FAIL pathfinder could not find campaign line");
			return false;
		}

		int borderStart = borderResult.getStartProvinceId();
		LogManager.line(
				"pathfinder borderStart=%d path=%s cost=%s",
				borderStart,
				borderResult.getPath(),
				borderResult.getTotalCost());
		BelligerentTerritory territory = BelligerentTerritory.fromWar(war, new TitleManagerProvinceOwnerLookup());
		int objective = resolveFinalObjective(
				war,
				defender,
				borderStart,
				regionalObjective.getAsInt(),
				territory);
		LogManager.line("finalObjective=%d", objective);

		PathfinderResult rightSegment = pathfinder.findRouteWithFallback(borderStart, objective, territory);
		if (!rightSegment.isFound()) {
			LogManager.line("FAIL no route border->objective");
			return false;
		}

		PathfinderResult leftSegment = pathfinder.findRouteWithFallback(attackerCapital, borderStart, territory);
		if (!leftSegment.isFound()) {
			LogManager.line("FAIL no route capital->border");
			return false;
		}
		LogManager.line("leftSegment=%s rightSegment=%s", leftSegment.getPath(), rightSegment.getPath());

		List<Integer> axis = mergeAxisPaths(leftSegment.getPath(), rightSegment.getPath());
		int cursorIndex = axis.indexOf(borderStart);
		if (cursorIndex < 0) {
			return false;
		}

		war.setObjectiveProvinceId(objective);
		war.setCampaignStartProvinceId(borderStart);
		war.setCampaignProvinces(axis);
		war.setCursorIndex(cursorIndex);

		int objectiveIndex = axis.indexOf(objective);
		if (objectiveIndex < 0) {
			return false;
		}
		FortControlService.initializeAtDeclare(war);
		FortZocIndex fortIndex = FortZocIndex.fromGameState();
		PortSeaZocIndex portIndex = PortSeaZocIndex.fromGameState();
		int capitalIndex = axis.indexOf(attackerCapital);
		CampaignScheduleBuilder.BuiltSchedules built = CampaignScheduleBuilder.buildAll(
				war,
				axis,
				cursorIndex,
				objectiveIndex,
				capitalIndex,
				fortIndex,
				portIndex);
		List<ScheduledCampaignBattle> invasionNatural = built.invasion();
		List<ScheduledCampaignBattle> counterNatural = built.counter();
		CampaignScheduleLogger.logSchedule("Invasion natural", war, axis, invasionNatural);
		CampaignScheduleLogger.logSchedule("Counter natural", war, ScheduleLeg.COUNTER, axis, counterNatural);
		List<ScheduledCampaignBattle> invasionTrimmed;
		List<ScheduledCampaignBattle> counterTrimmed;
		if (war.getGoal() == WarGoalType.PILLAGE) {
			war.setPillageNaturalNavyRequired(CampaignNavyGate.scheduleRequiresNavy(invasionNatural));
			invasionTrimmed = List.of(new ScheduledCampaignBattle(objective, CampaignBattleKind.FIELD, true, null));
			counterTrimmed = List.of();
			LogManager.line("pillage one-battle objective=%d naturalNavy=%s", objective, war.isPillageNaturalNavyRequired());
		} else {
			war.setPillageNaturalNavyRequired(false);
			int maxPerLeg = CampaignScheduleTrimmer.maxBattlesPerLegForGoal(war.getGoal());
			LogManager.line("trim maxPerLeg=%d", maxPerLeg);
			LogManager.section("Trim invasion");
			invasionTrimmed = CampaignScheduleTrimmer.trimInvasion(invasionNatural, maxPerLeg);
			LogManager.section("Trim counter");
			counterTrimmed = CampaignScheduleTrimmer.trimCounter(counterNatural, maxPerLeg);
		}
		CampaignScheduleLogger.logSchedule("Invasion trimmed", war, axis, invasionTrimmed);
		CampaignScheduleLogger.logSchedule("Counter trimmed", war, ScheduleLeg.COUNTER, axis, counterTrimmed);
		war.setCampaignBattleSchedule(invasionTrimmed);
		war.setCampaignCounterSchedule(counterTrimmed);
		boolean validInvasion = CampaignScheduleValidator.isValidInvasionSchedule(war, axis, invasionTrimmed);
		LogManager.line("invasionValidator=%s", validInvasion);
		if (!validInvasion) {
			LOGGER.warning("Invasion schedule violates chronological invariants for war " + war.getId());
		}
		war.setCampaignScheduleIndex(0);
		war.setCampaignCounterScheduleIndex(0);
		applyInitiativeFromLegs(war, invasionTrimmed, counterTrimmed);

		initProgressionState(war);
		initScheduleState(war);
		return true;
	}

	static int resolveFinalObjective(
			War war,
			Faction defender,
			int borderStart,
			int regionalObjective,
			BelligerentTerritory territory,
			ProvincePathfinder pathfinder) {
		if (war != null && war.getGoal() == WarGoalType.PILLAGE) {
			return regionalObjective;
		}
		Faction targetFaction = resolveTargetFaction(war, defender);
		if (targetFaction == null) {
			return regionalObjective;
		}

		int factionCapital = targetFaction.getCapital();
		if (factionCapital <= 0 || factionCapital == regionalObjective) {
			return regionalObjective;
		}

		PathfinderResult toRegional = pathfinder.findRouteWithFallback(borderStart, regionalObjective, territory);
		PathfinderResult toCapital = pathfinder.findRouteWithFallback(borderStart, factionCapital, territory);
		if (!toRegional.isFound() || !toCapital.isFound()) {
			return regionalObjective;
		}
		if (toCapital.getTotalCost() < toRegional.getTotalCost()) {
			return factionCapital;
		}
		return regionalObjective;
	}

	private int resolveFinalObjective(
			War war,
			Faction defender,
			int borderStart,
			int regionalObjective,
			BelligerentTerritory territory) {
		return resolveFinalObjective(war, defender, borderStart, regionalObjective, territory, pathfinder);
	}

	static Faction resolveTargetFaction(War war, Faction defender) {
		if (war.getGoal() == WarGoalType.TRANSFER_SUBJECT) {
			String subjectId = war.getSubjectFactionId();
			if (subjectId != null && !subjectId.isBlank()) {
				Faction subject = FactionManager.getByString(subjectId);
				if (subject != null) {
					return subject;
				}
			}
		}
		return defender;
	}

	static List<Integer> mergeAxisPaths(List<Integer> leftPath, List<Integer> rightPath) {
		List<Integer> merged = new ArrayList<>(leftPath);
		if (rightPath.isEmpty()) {
			return merged;
		}
		int startIndex = 0;
		if (!merged.isEmpty() && merged.get(merged.size() - 1).equals(rightPath.get(0))) {
			startIndex = 1;
		}
		for (int i = startIndex; i < rightPath.size(); i++) {
			merged.add(rightPath.get(i));
		}
		return merged;
	}

	static void applyInitiativeFromLegs(
			War war,
			List<ScheduledCampaignBattle> invasion,
			List<ScheduledCampaignBattle> counter) {
		war.setInitiativeAttacker(initiativeFuelForLegCount(invasion == null ? 0 : invasion.size(), true));
		war.setInitiativeDefender(initiativeFuelForLegCount(counter == null ? 0 : counter.size(), false));
	}

	public static int initiativeFuelForLegCount(int slotCount) {
		return initiativeFuelForLegCount(slotCount, false);
	}

	public static int initiativeFuelForLegCount(int slotCount, boolean invasionEmptyFallback) {
		if (slotCount <= 0) {
			return invasionEmptyFallback ? 6 : 0;
		}
		return (int) Math.ceil(slotCount * Cache.warInitiativeFactor);
	}

	@Deprecated
	static void applyInitiativeFromSchedule(War war, List<ScheduledCampaignBattle> schedule) {
		applyInitiativeFromLegs(war, schedule, schedule);
	}

	static void initProgressionState(War war) {
		CampaignCoalitionService.setInitiativeHolderCoalition(war, CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		war.setHoldPeaceProposalActive(false);
		FactionManager.getMap().enqueueOccupationFromWar(war);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
		war.setLastBattleOccupied(new ArrayList<>());
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setWhitePeaceProposedByAttacker(false);
		war.setWhitePeaceProposedByDefender(false);
		war.setCampaignBattlesFought(0);
		war.setSchemaVersion(CampaignCoalitionService.SCHEMA_VERSION);
	}

	static void initScheduleState(War war) {
		Instant started = war.getStartedAt() != null ? war.getStartedAt() : Instant.now();
		if (Cache.warFirstBattleDayAfterDeclare) {
			war.setBattleDay(started.atZone(BattleWindowService.SCHEDULE_ZONE).toLocalDate().plusDays(1));
		} else {
			war.setBattleDay(started.atZone(BattleWindowService.SCHEDULE_ZONE).toLocalDate());
		}
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setScheduledBattleAt(null);
		war.setScheduledBattleHour(0);
		war.setScheduledBattleProvinceId(null);
		war.getBattleVotes().clear();
		war.setAutoresolveProposedByAttacker(false);
		war.setAutoresolveProposedByDefender(false);
		war.setPostponementsThisCycle(0);
		war.setDefenderChoiceResolved(false);
	}
}
