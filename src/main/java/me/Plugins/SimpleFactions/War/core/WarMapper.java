package me.Plugins.SimpleFactions.War.core;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Database.CivilWarMemberMoveData;
import me.Plugins.SimpleFactions.Database.CivilWarVassalEndData;
import me.Plugins.SimpleFactions.Database.CommitmentData;
import me.Plugins.SimpleFactions.Database.CampaignRaidData;
import me.Plugins.SimpleFactions.Database.ParticipantData;
import me.Plugins.SimpleFactions.Database.ScheduledCampaignBattleData;
import me.Plugins.SimpleFactions.Database.SideData;
import me.Plugins.SimpleFactions.Database.WarData;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.War.campaign.WarCampaignService;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarStatus;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarMemberMove;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarWartimeVassalEnd;

public final class WarMapper {
	private WarMapper() {}

	public static WarData toData(War war) {
		WarData data = new WarData();
		data.schemaVersion = CampaignCoalitionService.SCHEMA_VERSION;
		data.id = war.getId();
		data.status = war.getStatus().toJson();
		if (war.getGoal() != null) {
			data.goal = war.getGoal().toJson();
		}
		if (war.getWarType() != null) {
			data.warType = war.getWarType().toJson();
		}
		data.attackerLeaderId = war.getAttackerLeaderId();
		data.defenderLeaderId = war.getDefenderLeaderId();
		data.targetTitleId = war.getTargetTitleId();
		data.subjectFactionId = war.getSubjectFactionId();
		data.relationTypeId = war.getRelationTypeId();
		data.internalWar = war.isInternalWar();
		data.internalTopLiegeId = war.getInternalTopLiegeId();
		data.governmentLawId = war.getGovernmentLawId();
		data.leadershipLawId = war.getLeadershipLawId();
		data.targetSettlementId = war.getTargetSettlementId();
		data.movementId = war.getMovementId();
		writeCivilWarSnapshot(data, war);
		data.objectiveProvinceId = war.getObjectiveProvinceId();
		data.campaignStartProvinceId = war.getCampaignStartProvinceId();
		data.campaignProvinces = war.getCampaignProvinces() == null
				? null
				: new ArrayList<>(war.getCampaignProvinces());
		data.cursorIndex = war.getCursorIndex();
		data.initiativeAttacker = war.getInitiativeAttacker();
		data.initiativeDefender = war.getInitiativeDefender();
		data.initiativeHolder = war.getInitiativeHolder() != null ? war.getInitiativeHolder().name() : BelligerentRole.ATTACKER.name();
		CampaignCoalition holderCoalition = war.getInitiativeHolderCoalition();
		if (holderCoalition == null) {
			holderCoalition = CampaignCoalitionService.belligerentRoleToCoalition(war.getInitiativeHolder());
		}
		data.initiativeHolderCoalition = holderCoalition != null ? holderCoalition.toJson() : CampaignCoalition.AGGRESSOR.toJson();
		CampaignPushTarget pushTarget = war.getPushTarget();
		if (pushTarget == null) {
			pushTarget = CampaignCoalitionService.derivePushTargetFromLegacyPhase(
					war.getCampaignPhase(),
					war.getObjectiveHeldBy());
		}
		data.pushTarget = pushTarget.toJson();
		data.postBattleChoicePhase = war.getPostBattleChoicePhase() != null
				? war.getPostBattleChoicePhase().toJson()
				: PostBattleChoicePhase.NONE.toJson();
		data.postBattleWinnerCoalition = war.getPostBattleWinnerCoalition() != null
				? war.getPostBattleWinnerCoalition().toJson()
				: null;
		data.postBattleChoiceResolved = war.isPostBattleChoiceResolved();
		data.lastBattleOffensiveCoalition = war.getLastBattleOffensiveCoalition() != null
				? war.getLastBattleOffensiveCoalition().toJson()
				: null;
		data.holdPeaceProposalActive = war.isHoldPeaceProposalActive();
		data.occupiedByAttacker = war.getOccupiedByAttacker() == null
				? new ArrayList<>()
				: new ArrayList<>(war.getOccupiedByAttacker());
		data.occupiedByDefender = war.getOccupiedByDefender() == null
				? new ArrayList<>()
				: new ArrayList<>(war.getOccupiedByDefender());
		data.lastBattleOccupied = war.getLastBattleOccupied() == null
				? new ArrayList<>()
				: new ArrayList<>(war.getLastBattleOccupied());
		data.campaignPhase = war.getCampaignPhase() != null ? war.getCampaignPhase().toJson() : CampaignCoalitionService.deriveLegacyPhaseFromPushTarget(
				pushTarget,
				war.getObjectiveHeldBy()).toJson();
		data.objectiveHeldBy = war.getObjectiveHeldBy() != null ? war.getObjectiveHeldBy().toJson() : ObjectiveHolder.DEFENDER.toJson();
		data.whitePeaceProposedByAttacker = war.isWhitePeaceProposedByAttacker();
		data.whitePeaceProposedByDefender = war.isWhitePeaceProposedByDefender();
		data.forcedWhitePeaceByAttacker = war.isForcedWhitePeaceByAttacker();
		data.forcedWhitePeaceByDefender = war.isForcedWhitePeaceByDefender();
		data.campaignBattlesFought = war.getCampaignBattlesFought();
		data.campaignBattleSchedule = serializeSchedule(war.getCampaignBattleSchedule());
		data.campaignScheduleIndex = war.getCampaignScheduleIndex();
		data.campaignCounterSchedule = serializeSchedule(war.getCampaignCounterSchedule());
		data.campaignCounterScheduleIndex = war.getCampaignCounterScheduleIndex();
		data.fortControllers = serializeFortControllers(war.getFortControllers());
		data.wartimeInstallationOwners = war.getWartimeInstallationOwners() == null
				|| war.getWartimeInstallationOwners().isEmpty()
				? new LinkedHashMap<>()
				: new LinkedHashMap<>(war.getWartimeInstallationOwners());
		if (!war.getConcededScheduleSlots().isEmpty()) {
			data.concededScheduleSlots = new ArrayList<>(war.getConcededScheduleSlots());
		}
		data.locationBattleCounts = war.getLocationBattleCounts() == null
				? new HashMap<>()
				: new HashMap<>(war.getLocationBattleCounts());
		data.battleSchedulePhase = war.getBattleSchedulePhase() != null
				? war.getBattleSchedulePhase().toJson()
				: BattleSchedulePhase.IDLE.toJson();
		if (war.getBattleDay() != null) {
			data.battleDay = war.getBattleDay().toString();
		}
		if (war.getScheduledBattleAt() != null) {
			data.scheduledBattleAt = war.getScheduledBattleAt().toString();
		}
		data.scheduledBattleHour = war.getScheduledBattleHour() > 0 ? war.getScheduledBattleHour() : null;
		data.scheduledBattleProvinceId = war.getScheduledBattleProvinceId();
		if (!war.getSignupRemindersSent().isEmpty()) {
			data.signupRemindersSent = new ArrayList<>(war.getSignupRemindersSent());
		}
		data.battleVotes = serializeBattleVotes(war.getBattleVotes());
		data.battleInstallationPicks = serializeInstallationPicks(war.getBattleInstallationPicks());
		if (war.getBattleInstallationPicksBattleDay() != null) {
			data.battleInstallationPicksBattleDay = war.getBattleInstallationPicksBattleDay().toString();
		}
		if (!war.getCampaignRaidsUsed().isEmpty()) {
			data.campaignRaidsUsed = new LinkedHashMap<>(war.getCampaignRaidsUsed());
		}
		if (war.getActiveCampaignRaid() != null) {
			data.activeCampaignRaid = war.getActiveCampaignRaid().toData();
		}
		data.raidRepairLockUntil = serializeRepairLocks(war.getRaidRepairLockUntil());
		data.autoresolveProposedByAttacker = war.isAutoresolveProposedByAttacker();
		data.autoresolveProposedByDefender = war.isAutoresolveProposedByDefender();
		data.postponementsThisCycle = war.getPostponementsThisCycle();
		data.defenderChoiceResolved = war.isDefenderChoiceResolved();
		data.forceQuorumNextClose = war.isForceQuorumNextClose();
		if (war.getStartedAt() != null) {
			data.startedAt = war.getStartedAt().toString();
		}
		if (war.getEndedAt() != null) {
			data.endedAt = war.getEndedAt().toString();
		}
		if (war.getEndReason() != null) {
			data.endReason = war.getEndReason().toJson();
		}
		data.attackers = serializeSide(war.getAttackers());
		data.defenders = serializeSide(war.getDefenders());
		data.commitments = serializeCommitments(WarCommitmentService.getCommitmentsForWar(war.getId()));
		return data;
	}

	public static War fromData(WarData data) {
		if (data == null || data.attackers == null || data.defenders == null) return null;

		Faction atkLeader = FactionManager.getByString(data.attackers.leader);
		Faction defLeader = FactionManager.getByString(data.defenders.leader);
		if (atkLeader == null || defLeader == null) return null;

		War war = new War(data.id, atkLeader, defLeader);
		war.setSchemaVersion(data.schemaVersion > 0 ? data.schemaVersion : 2);

		war.setGoal(WarGoalType.fromJson(data.goal));
		war.setWarType(WarType.fromJson(data.warType));
		war.setStatus(WarStatus.fromJson(data.status));
		if (data.attackerLeaderId != null) {
			war.setAttackerLeaderId(data.attackerLeaderId);
		}
		if (data.defenderLeaderId != null) {
			war.setDefenderLeaderId(data.defenderLeaderId);
		}
		war.setTargetTitleId(data.targetTitleId);
		war.setSubjectFactionId(data.subjectFactionId);
		war.setRelationTypeId(data.relationTypeId);
		if (data.internalWar != null) {
			war.setInternalWar(data.internalWar);
			war.setInternalTopLiegeId(data.internalTopLiegeId);
		}
		war.setGovernmentLawId(data.governmentLawId);
		war.setLeadershipLawId(data.leadershipLawId);
		war.setTargetSettlementId(data.targetSettlementId);
		war.setMovementId(data.movementId);
		war.setCivilWarSnapshot(readCivilWarSnapshot(data));
		war.setObjectiveProvinceId(data.objectiveProvinceId);
		war.setCampaignStartProvinceId(data.campaignStartProvinceId);
		war.setCampaignProvinces(data.campaignProvinces == null ? null : new ArrayList<>(data.campaignProvinces));
		war.setCursorIndex(data.cursorIndex);
		List<ScheduledCampaignBattle> campaignSchedule = deserializeSchedule(data.campaignBattleSchedule);
		List<ScheduledCampaignBattle> counterSchedule = deserializeSchedule(data.campaignCounterSchedule);
		boolean hasCounterScheduleField = data.campaignCounterSchedule != null;
		int attackerDefault = defaultInitiativeFuel(campaignSchedule);
		int defenderDefault = hasCounterScheduleField
				? WarCampaignService.initiativeFuelForLegCount(counterSchedule.size())
				: attackerDefault;
		war.setInitiativeAttacker(data.initiativeAttacker != null ? data.initiativeAttacker : attackerDefault);
		war.setInitiativeDefender(data.initiativeDefender != null ? data.initiativeDefender : defenderDefault);
		war.setOccupiedByAttacker(data.occupiedByAttacker == null ? new ArrayList<>() : new ArrayList<>(data.occupiedByAttacker));
		war.setOccupiedByDefender(data.occupiedByDefender == null ? new ArrayList<>() : new ArrayList<>(data.occupiedByDefender));
		war.setLastBattleOccupied(data.lastBattleOccupied == null ? new ArrayList<>() : new ArrayList<>(data.lastBattleOccupied));
		CampaignPhase phase = CampaignPhase.fromJson(data.campaignPhase);
		war.setCampaignPhase(phase != null ? phase : CampaignPhase.INVASION);
		ObjectiveHolder holder = ObjectiveHolder.fromJson(data.objectiveHeldBy);
		war.setObjectiveHeldBy(holder != null ? holder : ObjectiveHolder.DEFENDER);
		applyCampaignRuntimeFields(war, data);
		war.setWhitePeaceProposedByAttacker(data.whitePeaceProposedByAttacker);
		war.setWhitePeaceProposedByDefender(data.whitePeaceProposedByDefender);
		war.setForcedWhitePeaceByAttacker(data.forcedWhitePeaceByAttacker);
		war.setForcedWhitePeaceByDefender(data.forcedWhitePeaceByDefender);
		war.setCampaignBattlesFought(data.campaignBattlesFought != null ? data.campaignBattlesFought : 0);
		war.setCampaignBattleSchedule(campaignSchedule);
		war.setCampaignScheduleIndex(data.campaignScheduleIndex != null ? data.campaignScheduleIndex : 0);
		war.setCampaignCounterSchedule(counterSchedule);
		war.setCampaignCounterScheduleIndex(
				data.campaignCounterScheduleIndex != null ? data.campaignCounterScheduleIndex : 0);
		war.setFortControllers(deserializeFortControllers(data.fortControllers));
		war.setWartimeInstallationOwners(
				data.wartimeInstallationOwners == null
						? new LinkedHashMap<>()
						: new LinkedHashMap<>(data.wartimeInstallationOwners));
		war.setConcededScheduleSlots(data.concededScheduleSlots);
		war.setLocationBattleCounts(data.locationBattleCounts);
		BattleSchedulePhase schedulePhase = BattleSchedulePhase.fromJson(data.battleSchedulePhase);
		war.setBattleSchedulePhase(schedulePhase != null ? schedulePhase : BattleSchedulePhase.IDLE);
		if (data.battleDay != null && !data.battleDay.isBlank()) {
			war.setBattleDay(LocalDate.parse(data.battleDay));
		}
		if (data.scheduledBattleAt != null && !data.scheduledBattleAt.isBlank()) {
			war.setScheduledBattleAt(Instant.parse(data.scheduledBattleAt));
		}
		war.setScheduledBattleHour(data.scheduledBattleHour != null ? data.scheduledBattleHour : 0);
		war.setScheduledBattleProvinceId(data.scheduledBattleProvinceId);
		war.setSignupRemindersSent(data.signupRemindersSent == null
				? new LinkedHashSet<>()
				: new LinkedHashSet<>(data.signupRemindersSent));
		war.setBattleVotes(deserializeBattleVotes(data.battleVotes));
		war.setBattleInstallationPicks(deserializeInstallationPicks(data.battleInstallationPicks));
		if (data.battleInstallationPicksBattleDay != null && !data.battleInstallationPicksBattleDay.isBlank()) {
			war.setBattleInstallationPicksBattleDay(LocalDate.parse(data.battleInstallationPicksBattleDay));
		}
		war.setCampaignRaidsUsed(data.campaignRaidsUsed);
		if (data.activeCampaignRaid != null) {
			war.setActiveCampaignRaid(CampaignRaid.fromData(data.activeCampaignRaid));
		}
		war.setRaidRepairLockUntil(deserializeRepairLocks(data.raidRepairLockUntil));
		war.setAutoresolveProposedByAttacker(data.autoresolveProposedByAttacker);
		war.setAutoresolveProposedByDefender(data.autoresolveProposedByDefender);
		war.setPostponementsThisCycle(data.postponementsThisCycle != null ? data.postponementsThisCycle : 0);
		war.setDefenderChoiceResolved(data.defenderChoiceResolved);
		if (data.postBattleChoiceResolved != null) {
			war.setPostBattleChoiceResolved(data.postBattleChoiceResolved);
		}
		war.setForceQuorumNextClose(data.forceQuorumNextClose);
		if (data.startedAt != null) {
			war.setStartedAt(Instant.parse(data.startedAt));
		}
		if (data.endedAt != null) {
			war.setEndedAt(Instant.parse(data.endedAt));
		}
		war.setEndReason(WarEndReason.fromJson(data.endReason));

		war.getAttackers().getMainParticipants().clear();
		war.getDefenders().getMainParticipants().clear();
		loadParticipants(data.attackers, war.getAttackers());
		loadParticipants(data.defenders, war.getDefenders());
		WarCommitmentService.restoreCommitments(war.getId(), deserializeCommitments(data.commitments, war.getId()));

		return war;
	}

	private static int defaultInitiativeFuel(List<ScheduledCampaignBattle> schedule) {
		if (schedule == null || schedule.isEmpty()) {
			return 6;
		}
		return (int) Math.ceil(schedule.size() * Cache.warInitiativeFactor);
	}

	private static Map<String, String> serializeFortControllers(Map<String, CampaignCoalition> controllers) {
		if (controllers == null || controllers.isEmpty()) {
			return new HashMap<>();
		}
		Map<String, String> serialized = new LinkedHashMap<>();
		for (Map.Entry<String, CampaignCoalition> entry : controllers.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			serialized.put(entry.getKey(), entry.getValue().toJson());
		}
		return serialized;
	}

	private static Map<String, CampaignCoalition> deserializeFortControllers(Map<String, String> controllers) {
		if (controllers == null || controllers.isEmpty()) {
			return new HashMap<>();
		}
		Map<String, CampaignCoalition> deserialized = new HashMap<>();
		for (Map.Entry<String, String> entry : controllers.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			CampaignCoalition coalition = CampaignCoalition.fromJson(entry.getValue());
			if (coalition == null) {
				continue;
			}
			deserialized.put(entry.getKey(), coalition);
		}
		return deserialized;
	}

	private static List<ScheduledCampaignBattleData> serializeSchedule(List<ScheduledCampaignBattle> schedule) {
		if (schedule == null || schedule.isEmpty()) {
			return new ArrayList<>();
		}
		List<ScheduledCampaignBattleData> serialized = new ArrayList<>();
		for (ScheduledCampaignBattle slot : schedule) {
			if (slot == null) {
				continue;
			}
			ScheduledCampaignBattleData data = new ScheduledCampaignBattleData();
			data.provinceId = slot.provinceId();
			data.kind = slot.kind() != null ? slot.kind().toJson() : CampaignBattleKind.FIELD.toJson();
			data.required = slot.required();
			data.fortInstallationId = slot.fortInstallationId();
			data.portInstallationId = slot.portInstallationId();
			data.chronologyProvinceId = slot.chronologyProvinceId();
			serialized.add(data);
		}
		return serialized;
	}

	private static List<ScheduledCampaignBattle> deserializeSchedule(List<ScheduledCampaignBattleData> schedule) {
		if (schedule == null || schedule.isEmpty()) {
			return new ArrayList<>();
		}
		List<ScheduledCampaignBattle> deserialized = new ArrayList<>();
		for (ScheduledCampaignBattleData data : schedule) {
			if (data == null || data.provinceId <= 0) {
				continue;
			}
			deserialized.add(new ScheduledCampaignBattle(
					data.provinceId,
					CampaignBattleKind.fromJson(data.kind),
					data.required,
					data.fortInstallationId,
					data.portInstallationId,
					data.chronologyProvinceId));
		}
		return deserialized;
	}

	private static SideData serializeSide(Side side) {
		SideData data = new SideData();
		data.leader = side.getLeader().getId();
		for (Participant participant : side.getMainParticipants()) {
			data.participants.add(serializeParticipant(participant));
		}
		return data;
	}

	private static ParticipantData serializeParticipant(Participant participant) {
		ParticipantData data = new ParticipantData();
		data.leader = participant.getLeader().getId();
		data.civilWar = participant.isCivilWar();

		for (Faction subject : participant.getSubjects()) {
			data.subjects.add(subject.getId());
		}
		for (Map.Entry<Faction, Boolean> entry : participant.getAllies().entrySet()) {
			data.allies.put(entry.getKey().getId(), entry.getValue());
		}
		if (participant.getBackers() != null) {
			for (Faction backer : participant.getBackers()) {
				if (backer != null && backer.getId() != null) {
					data.backers.add(backer.getId());
				}
			}
		}
		return data;
	}

	private static void loadParticipants(SideData data, Side side) {
		for (ParticipantData participantData : data.participants) {
			Faction leader = FactionManager.getByString(participantData.leader);
			if (leader == null) continue;

			List<Faction> subjects = new ArrayList<>();
			for (String id : participantData.subjects) {
				Faction faction = FactionManager.getByString(id);
				if (faction != null) subjects.add(faction);
			}

			Map<Faction, Boolean> allies = new HashMap<>();
			for (Map.Entry<String, Boolean> entry : participantData.allies.entrySet()) {
				Faction faction = FactionManager.getByString(entry.getKey());
				if (faction != null) allies.put(faction, entry.getValue());
			}

			List<Faction> backers = new ArrayList<>();
			if (participantData.backers != null) {
				for (String id : participantData.backers) {
					Faction faction = FactionManager.getByString(id);
					if (faction != null) {
						backers.add(faction);
					}
				}
			}

			Participant participant = new Participant(
					leader, subjects, allies, backers, new HashMap<>(), participantData.civilWar);
			side.getMainParticipants().add(participant);
		}
	}

	private static List<CommitmentData> serializeCommitments(List<WarCommitment> commitments) {
		if (commitments == null || commitments.isEmpty()) {
			return null;
		}
		List<CommitmentData> serialized = new ArrayList<>();
		for (WarCommitment commitment : commitments) {
			CommitmentData data = new CommitmentData();
			data.factionId = commitment.factionId();
			data.sourceFactionId = commitment.sourceFactionId();
			data.regimentId = commitment.regimentId();
			data.count = commitment.count();
			if (commitment.committedAt() != null) {
				data.committedAt = commitment.committedAt().toString();
			}
			serialized.add(data);
		}
		return serialized;
	}

	private static List<WarCommitment> deserializeCommitments(List<CommitmentData> commitments, int warId) {
		if (commitments == null || commitments.isEmpty()) {
			return List.of();
		}
		List<WarCommitment> deserialized = new ArrayList<>();
		for (CommitmentData data : commitments) {
			if (data == null || data.factionId == null || data.regimentId == null || data.count <= 0) {
				continue;
			}
			Instant committedAt = data.committedAt != null && !data.committedAt.isBlank()
					? Instant.parse(data.committedAt)
					: Instant.EPOCH;
			deserialized.add(new WarCommitment(
					warId,
					data.factionId,
					data.sourceFactionId,
					data.regimentId,
					data.count,
					committedAt));
		}
		return deserialized;
	}

	private static void applyCampaignRuntimeFields(War war, WarData data) {
		CampaignCoalition coalition = CampaignCoalition.fromJson(data.initiativeHolderCoalition);
		if (coalition == null) {
			coalition = CampaignCoalitionService.belligerentRoleToCoalition(
					parseInitiativeHolder(data.initiativeHolder));
		}
		CampaignPushTarget pushTarget = CampaignPushTarget.fromJson(data.pushTarget);
		if (pushTarget == null) {
			pushTarget = CampaignCoalitionService.derivePushTargetFromLegacyPhase(
					war.getCampaignPhase(),
					war.getObjectiveHeldBy());
		}
		PostBattleChoicePhase choicePhase = PostBattleChoicePhase.fromJson(data.postBattleChoicePhase);
		if (choicePhase == null) {
			choicePhase = PostBattleChoicePhase.NONE;
		}
		war.setPushTarget(pushTarget);
		war.setPostBattleChoicePhase(choicePhase);
		war.setPostBattleWinnerCoalition(CampaignCoalition.fromJson(data.postBattleWinnerCoalition));
		war.setLastBattleOffensiveCoalition(CampaignCoalition.fromJson(data.lastBattleOffensiveCoalition));
		war.setHoldPeaceProposalActive(data.holdPeaceProposalActive);
		CampaignCoalitionService.setInitiativeHolderCoalition(war, coalition);
		war.setCampaignPhase(CampaignCoalitionService.deriveLegacyPhaseFromPushTarget(
				pushTarget,
				war.getObjectiveHeldBy()));
	}

	private static BelligerentRole parseInitiativeHolder(String value) {
		if (value == null || value.isBlank()) {
			return BelligerentRole.ATTACKER;
		}
		try {
			return BelligerentRole.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return BelligerentRole.ATTACKER;
		}
	}

	private static Map<String, List<String>> serializeInstallationPicks(
			Map<String, LinkedHashSet<String>> picks) {
		Map<String, List<String>> serialized = new LinkedHashMap<>();
		if (picks == null) {
			return serialized;
		}
		for (Map.Entry<String, LinkedHashSet<String>> entry : picks.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
					|| entry.getValue().isEmpty()) {
				continue;
			}
			serialized.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return serialized;
	}

	private static Map<String, LinkedHashSet<String>> deserializeInstallationPicks(
			Map<String, List<String>> picks) {
		Map<String, LinkedHashSet<String>> deserialized = new LinkedHashMap<>();
		if (picks == null) {
			return deserialized;
		}
		for (Map.Entry<String, List<String>> entry : picks.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
					|| entry.getValue().isEmpty()) {
				continue;
			}
			LinkedHashSet<String> ids = new LinkedHashSet<>();
			for (String installationId : entry.getValue()) {
				if (installationId != null && !installationId.isBlank()) {
					ids.add(installationId);
				}
			}
			if (!ids.isEmpty()) {
				deserialized.put(entry.getKey(), ids);
			}
		}
		return deserialized;
	}

	private static Map<String, String> serializeRepairLocks(Map<String, Instant> locks) {
		Map<String, String> serialized = new LinkedHashMap<>();
		if (locks == null) {
			return serialized;
		}
		for (Map.Entry<String, Instant> entry : locks.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			serialized.put(entry.getKey(), entry.getValue().toString());
		}
		return serialized;
	}

	private static Map<String, Instant> deserializeRepairLocks(Map<String, String> locks) {
		Map<String, Instant> deserialized = new LinkedHashMap<>();
		if (locks == null) {
			return deserialized;
		}
		for (Map.Entry<String, String> entry : locks.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()
					|| entry.getValue() == null || entry.getValue().isBlank()) {
				continue;
			}
			deserialized.put(entry.getKey(), Instant.parse(entry.getValue()));
		}
		return deserialized;
	}

	private static Map<String, List<Integer>> serializeBattleVotes(Map<UUID, Set<Integer>> votes) {
		Map<String, List<Integer>> serialized = new LinkedHashMap<>();
		if (votes == null) {
			return serialized;
		}
		for (Map.Entry<UUID, Set<Integer>> entry : votes.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
				continue;
			}
			serialized.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
		}
		return serialized;
	}

	private static Map<UUID, Set<Integer>> deserializeBattleVotes(Map<String, List<Integer>> votes) {
		Map<UUID, Set<Integer>> deserialized = new HashMap<>();
		if (votes == null) {
			return deserialized;
		}
		for (Map.Entry<String, List<Integer>> entry : votes.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			try {
				UUID playerId = UUID.fromString(entry.getKey());
				deserialized.put(playerId, new HashSet<>(entry.getValue()));
			} catch (IllegalArgumentException ignored) {
				// skip invalid UUID keys from corrupted saves
			}
		}
		return deserialized;
	}

	private static void writeCivilWarSnapshot(WarData data, War war) {
		CivilWarSnapshot snapshot = war.getCivilWarSnapshot();
		if (snapshot == null) {
			return;
		}
		data.civilWarHostFactionId = snapshot.getHostFactionId();
		data.civilWarTempRebelFactionId = snapshot.getTempRebelFactionId();
		if (snapshot.getTransferredProvinces() != null && !snapshot.getTransferredProvinces().isEmpty()) {
			data.civilWarTransferredProvinces = new LinkedHashMap<>();
			for (Map.Entry<Integer, String> entry : snapshot.getTransferredProvinces().entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				data.civilWarTransferredProvinces.put(String.valueOf(entry.getKey()), entry.getValue());
			}
		}
		if (snapshot.getWartimeVassalEnds() != null && !snapshot.getWartimeVassalEnds().isEmpty()) {
			data.civilWarVassalEnds = new ArrayList<>();
			for (CivilWarWartimeVassalEnd end : snapshot.getWartimeVassalEnds()) {
				if (end == null) {
					continue;
				}
				CivilWarVassalEndData row = new CivilWarVassalEndData();
				row.factionId = end.factionId();
				row.formerOverlordId = end.formerOverlordId();
				row.relationTypeId = end.relationTypeId();
				data.civilWarVassalEnds.add(row);
			}
		}
		data.civilWarHostOldCapitalId = snapshot.getHostOldCapitalId();
		data.civilWarRebelCapitalId = snapshot.getRebelCapitalId();
		data.civilWarWantedLeaderName = snapshot.getWantedLeaderName();
		data.civilWarRebelMainGuildOwnName = snapshot.getRebelMainGuildOwnName();
		data.civilWarMovedTitleId = snapshot.getMovedTitleId();
		if (snapshot.getMemberMoves() != null && !snapshot.getMemberMoves().isEmpty()) {
			data.civilWarMemberMoves = new ArrayList<>();
			for (CivilWarMemberMove move : snapshot.getMemberMoves()) {
				if (move == null) {
					continue;
				}
				CivilWarMemberMoveData row = new CivilWarMemberMoveData();
				row.player = move.player();
				row.originGuildId = move.originGuildId();
				row.originWasGuildLeader = move.originWasGuildLeader();
				data.civilWarMemberMoves.add(row);
			}
		}
	}

	private static CivilWarSnapshot readCivilWarSnapshot(WarData data) {
		if (data.civilWarHostFactionId == null
				&& data.civilWarTempRebelFactionId == null
				&& (data.civilWarTransferredProvinces == null || data.civilWarTransferredProvinces.isEmpty())
				&& (data.civilWarVassalEnds == null || data.civilWarVassalEnds.isEmpty())
				&& (data.civilWarMemberMoves == null || data.civilWarMemberMoves.isEmpty())
				&& data.civilWarWantedLeaderName == null
				&& data.civilWarRebelMainGuildOwnName == null
				&& data.civilWarMovedTitleId == null) {
			return null;
		}
		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId(data.civilWarHostFactionId);
		snapshot.setTempRebelFactionId(data.civilWarTempRebelFactionId);
		if (data.civilWarTransferredProvinces != null) {
			Map<Integer, String> transferred = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : data.civilWarTransferredProvinces.entrySet()) {
				if (entry.getKey() == null || entry.getKey().isBlank()) {
					continue;
				}
				try {
					transferred.put(Integer.parseInt(entry.getKey()), entry.getValue());
				} catch (NumberFormatException ignored) {
					// skip corrupted province keys
				}
			}
			snapshot.setTransferredProvinces(transferred);
		}
		if (data.civilWarVassalEnds != null) {
			List<CivilWarWartimeVassalEnd> ends = new ArrayList<>();
			for (CivilWarVassalEndData row : data.civilWarVassalEnds) {
				if (row == null) {
					continue;
				}
				ends.add(new CivilWarWartimeVassalEnd(row.factionId, row.formerOverlordId, row.relationTypeId));
			}
			snapshot.setWartimeVassalEnds(ends);
		}
		snapshot.setHostOldCapitalId(data.civilWarHostOldCapitalId);
		snapshot.setRebelCapitalId(data.civilWarRebelCapitalId);
		snapshot.setWantedLeaderName(data.civilWarWantedLeaderName);
		snapshot.setRebelMainGuildOwnName(data.civilWarRebelMainGuildOwnName);
		snapshot.setMovedTitleId(data.civilWarMovedTitleId);
		if (data.civilWarMemberMoves != null) {
			List<CivilWarMemberMove> moves = new ArrayList<>();
			for (CivilWarMemberMoveData row : data.civilWarMemberMoves) {
				if (row == null) {
					continue;
				}
				moves.add(new CivilWarMemberMove(row.player, row.originGuildId, row.originWasGuildLeader));
			}
			snapshot.setMemberMoves(moves);
		}
		return snapshot;
	}
}
