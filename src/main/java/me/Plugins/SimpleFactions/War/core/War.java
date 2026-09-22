package me.Plugins.SimpleFactions.War.core;


import me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.declare.InterVassalQueries;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarStatus;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class War {
	private int id;
	private int schemaVersion = 3;
	private Side attackers;
	private Side defenders;
	private WarGoalType goal;
	private WarType warType;
	private WarStatus status = WarStatus.ACTIVE;
	private String attackerLeaderId;
	private String defenderLeaderId;
	private String targetTitleId;
	private String subjectFactionId;
	private String relationTypeId;
	private boolean internalWar;
	private String internalTopLiegeId;
	private String governmentLawId;
	private String leadershipLawId;
	private String targetSettlementId;
	private String movementId;
	private me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot civilWarSnapshot;
	private boolean pillageNaturalNavyRequired;
	private Integer objectiveProvinceId;
	private Integer campaignStartProvinceId;
	private List<Integer> campaignProvinces;
	private int cursorIndex;
	private int initiativeAttacker;
	private int initiativeDefender;
	private BelligerentRole initiativeHolder = BelligerentRole.ATTACKER;
	private List<Integer> occupiedByAttacker;
	private List<Integer> occupiedByDefender;
	private List<Integer> lastBattleOccupied;
	private CampaignPhase campaignPhase = CampaignPhase.INVASION;
	private ObjectiveHolder objectiveHeldBy = ObjectiveHolder.DEFENDER;
	private boolean whitePeaceProposedByAttacker;
	private boolean whitePeaceProposedByDefender;
	private boolean forcedWhitePeaceByAttacker;
	private boolean forcedWhitePeaceByDefender;
	private int campaignBattlesFought;
	private List<ScheduledCampaignBattle> campaignBattleSchedule = new ArrayList<>();
	private int campaignScheduleIndex;
	private List<ScheduledCampaignBattle> campaignCounterSchedule = new ArrayList<>();
	private int campaignCounterScheduleIndex;
	private Map<String, CampaignCoalition> fortControllers = new HashMap<>();
	private Map<String, String> wartimeInstallationOwners = new LinkedHashMap<>();
	private Set<String> concededScheduleSlots = new LinkedHashSet<>();
	private Map<String, Integer> locationBattleCounts = new HashMap<>();
	private BattleSchedulePhase battleSchedulePhase = BattleSchedulePhase.IDLE;
	private LocalDate battleDay;
	private Instant scheduledBattleAt;
	private int scheduledBattleHour;
	private Integer scheduledBattleProvinceId;
	private Set<Integer> signupRemindersSent = new LinkedHashSet<>();
	private Map<UUID, Set<Integer>> battleVotes = new HashMap<>();
	private Map<String, LinkedHashSet<String>> battleInstallationPicks = new LinkedHashMap<>();
	private LocalDate battleInstallationPicksBattleDay;
	private Map<String, String> campaignRaidsUsed = new LinkedHashMap<>();
	private CampaignRaid activeCampaignRaid;
	private Map<String, Instant> raidRepairLockUntil = new LinkedHashMap<>();
	private boolean autoresolveProposedByAttacker;
	private boolean autoresolveProposedByDefender;
	private int postponementsThisCycle;
	private boolean defenderChoiceResolved;
	private CampaignCoalition initiativeHolderCoalition = CampaignCoalition.AGGRESSOR;
	private CampaignPushTarget pushTarget = CampaignPushTarget.TOWARD_OBJECTIVE;
	private PostBattleChoicePhase postBattleChoicePhase = PostBattleChoicePhase.NONE;
	private CampaignCoalition postBattleWinnerCoalition;
	private boolean postBattleChoiceResolved = true;
	private CampaignCoalition lastBattleOffensiveCoalition;
	private boolean holdPeaceProposalActive;
	private boolean forceQuorumNextClose;
	private Instant startedAt;
	private Instant endedAt;
	private WarEndReason endReason;

	public War(Faction attacker, Faction defender) {
		this(WarManager.newId(), attacker, defender);
	}

	public War(int i, Faction attacker, Faction defender) {
		id = i;
		attackers = new Side(attacker);
		defenders = new Side(defender);
		attackerLeaderId = attacker.getId();
		defenderLeaderId = defender.getId();
		status = WarStatus.ACTIVE;
		startedAt = Instant.now();
		schemaVersion = 2;
		snapshotInternalWar(attacker, defender);
	}

	public War(
			int id,
			Side attackers,
			Side defenders,
			WarGoalType goal,
			WarType warType,
			String targetTitleId,
			Integer objectiveProvinceId,
			Instant startedAt) {
		this.id = id;
		this.attackers = attackers;
		this.defenders = defenders;
		this.goal = goal;
		this.warType = warType;
		this.targetTitleId = targetTitleId;
		this.objectiveProvinceId = objectiveProvinceId;
		this.cursorIndex = 0;
		this.attackerLeaderId = attackers.getLeader().getId();
		this.defenderLeaderId = defenders.getLeader().getId();
		this.status = WarStatus.ACTIVE;
		this.startedAt = startedAt != null ? startedAt : Instant.now();
		this.schemaVersion = 2;
		if (attackers != null && defenders != null) {
			snapshotInternalWar(attackers.getLeader(), defenders.getLeader());
		}
	}

	public int getId() {
		return id;
	}

	/** Battle-system identifier; same as {@link #getId()}. */
	public int getWarId() {
		return id;
	}

	public int getSchemaVersion() {
		return schemaVersion;
	}

	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	public Side getAttackers() {
		return attackers;
	}

	public Side getDefenders() {
		return defenders;
	}

	public WarGoalType getGoal() {
		return goal;
	}

	public void setGoal(WarGoalType goal) {
		this.goal = goal;
	}

	public WarType getWarType() {
		return warType;
	}

	public void setWarType(WarType warType) {
		this.warType = warType;
	}

	public WarStatus getStatus() {
		return status;
	}

	void setStatus(WarStatus status) {
		this.status = status;
	}

	public String getAttackerLeaderId() {
		return attackerLeaderId;
	}

	void setAttackerLeaderId(String attackerLeaderId) {
		this.attackerLeaderId = attackerLeaderId;
	}

	public String getDefenderLeaderId() {
		return defenderLeaderId;
	}

	void setDefenderLeaderId(String defenderLeaderId) {
		this.defenderLeaderId = defenderLeaderId;
	}

	public String getTargetTitleId() {
		return targetTitleId;
	}

	public void setTargetTitleId(String targetTitleId) {
		this.targetTitleId = targetTitleId;
	}

	public String getSubjectFactionId() {
		return subjectFactionId;
	}

	public void setSubjectFactionId(String subjectFactionId) {
		this.subjectFactionId = subjectFactionId;
	}

	public String getRelationTypeId() {
		return relationTypeId;
	}

	public void setRelationTypeId(String relationTypeId) {
		this.relationTypeId = relationTypeId;
	}

	public boolean isInternalWar() {
		return internalWar;
	}

	public void setInternalWar(boolean internalWar) {
		this.internalWar = internalWar;
	}

	public String getInternalTopLiegeId() {
		return internalTopLiegeId;
	}

	public void setInternalTopLiegeId(String internalTopLiegeId) {
		this.internalTopLiegeId = internalTopLiegeId;
	}

	private void snapshotInternalWar(Faction attacker, Faction defender) {
		if (!InterVassalQueries.isInternalPeer(attacker, defender)) {
			return;
		}
		this.internalWar = true;
		this.internalTopLiegeId = InterVassalQueries.topLiegeId(attacker);
	}

	public String getGovernmentLawId() {
		return governmentLawId;
	}

	public void setGovernmentLawId(String governmentLawId) {
		this.governmentLawId = governmentLawId;
	}

	public String getLeadershipLawId() {
		return leadershipLawId;
	}

	public void setLeadershipLawId(String leadershipLawId) {
		this.leadershipLawId = leadershipLawId;
	}

	public String getTargetSettlementId() {
		return targetSettlementId;
	}

	public void setTargetSettlementId(String targetSettlementId) {
		this.targetSettlementId = targetSettlementId;
	}

	public String getMovementId() {
		return movementId;
	}

	public void setMovementId(String movementId) {
		this.movementId = movementId;
	}

	public me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot getCivilWarSnapshot() {
		return civilWarSnapshot;
	}

	public void setCivilWarSnapshot(me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot civilWarSnapshot) {
		this.civilWarSnapshot = civilWarSnapshot;
	}

	public boolean isPillageNaturalNavyRequired() {
		return pillageNaturalNavyRequired;
	}

	public void setPillageNaturalNavyRequired(boolean pillageNaturalNavyRequired) {
		this.pillageNaturalNavyRequired = pillageNaturalNavyRequired;
	}

	public Integer getObjectiveProvinceId() {
		return objectiveProvinceId;
	}

	public void setObjectiveProvinceId(Integer objectiveProvinceId) {
		this.objectiveProvinceId = objectiveProvinceId;
	}

	public Integer getCampaignStartProvinceId() {
		return campaignStartProvinceId;
	}

	public void setCampaignStartProvinceId(Integer campaignStartProvinceId) {
		this.campaignStartProvinceId = campaignStartProvinceId;
	}

	public List<Integer> getCampaignProvinces() {
		return campaignProvinces;
	}

	public void setCampaignProvinces(List<Integer> campaignProvinces) {
		this.campaignProvinces = campaignProvinces;
	}

	public int getCursorIndex() {
		return cursorIndex;
	}

	public void setCursorIndex(int cursorIndex) {
		this.cursorIndex = cursorIndex;
	}

	public int getInitiativeAttacker() {
		return initiativeAttacker;
	}

	public void setInitiativeAttacker(int initiativeAttacker) {
		this.initiativeAttacker = initiativeAttacker;
	}

	public int getInitiativeDefender() {
		return initiativeDefender;
	}

	public void setInitiativeDefender(int initiativeDefender) {
		this.initiativeDefender = initiativeDefender;
	}

	public BelligerentRole getInitiativeHolder() {
		return initiativeHolder;
	}

	public void setInitiativeHolder(BelligerentRole initiativeHolder) {
		this.initiativeHolder = initiativeHolder;
		this.initiativeHolderCoalition = initiativeHolder == BelligerentRole.DEFENDER
				? CampaignCoalition.DEFENDER
				: CampaignCoalition.AGGRESSOR;
	}

	public List<Integer> getOccupiedByAttacker() {
		return occupiedByAttacker;
	}

	public void setOccupiedByAttacker(List<Integer> occupiedByAttacker) {
		this.occupiedByAttacker = occupiedByAttacker;
	}

	public List<Integer> getOccupiedByDefender() {
		return occupiedByDefender;
	}

	public void setOccupiedByDefender(List<Integer> occupiedByDefender) {
		this.occupiedByDefender = occupiedByDefender;
	}

	public List<Integer> getLastBattleOccupied() {
		return lastBattleOccupied;
	}

	public void setLastBattleOccupied(List<Integer> lastBattleOccupied) {
		this.lastBattleOccupied = lastBattleOccupied;
	}

	public CampaignPhase getCampaignPhase() {
		return campaignPhase;
	}

	public void setCampaignPhase(CampaignPhase campaignPhase) {
		this.campaignPhase = campaignPhase;
	}

	public ObjectiveHolder getObjectiveHeldBy() {
		return objectiveHeldBy;
	}

	public void setObjectiveHeldBy(ObjectiveHolder objectiveHeldBy) {
		this.objectiveHeldBy = objectiveHeldBy;
	}

	public boolean isWhitePeaceProposedByAttacker() {
		return whitePeaceProposedByAttacker;
	}

	public void setWhitePeaceProposedByAttacker(boolean whitePeaceProposedByAttacker) {
		this.whitePeaceProposedByAttacker = whitePeaceProposedByAttacker;
	}

	public boolean isWhitePeaceProposedByDefender() {
		return whitePeaceProposedByDefender;
	}

	public void setWhitePeaceProposedByDefender(boolean whitePeaceProposedByDefender) {
		this.whitePeaceProposedByDefender = whitePeaceProposedByDefender;
	}

	public boolean isForcedWhitePeaceByAttacker() {
		return forcedWhitePeaceByAttacker;
	}

	public void setForcedWhitePeaceByAttacker(boolean forcedWhitePeaceByAttacker) {
		this.forcedWhitePeaceByAttacker = forcedWhitePeaceByAttacker;
	}

	public boolean isForcedWhitePeaceByDefender() {
		return forcedWhitePeaceByDefender;
	}

	public void setForcedWhitePeaceByDefender(boolean forcedWhitePeaceByDefender) {
		this.forcedWhitePeaceByDefender = forcedWhitePeaceByDefender;
	}

	public int getCampaignBattlesFought() {
		return campaignBattlesFought;
	}

	public void setCampaignBattlesFought(int campaignBattlesFought) {
		this.campaignBattlesFought = campaignBattlesFought;
	}

	public List<ScheduledCampaignBattle> getCampaignBattleSchedule() {
		return campaignBattleSchedule;
	}

	public void setCampaignBattleSchedule(List<ScheduledCampaignBattle> campaignBattleSchedule) {
		if (campaignBattleSchedule == null || campaignBattleSchedule.isEmpty()) {
			this.campaignBattleSchedule = new ArrayList<>();
			return;
		}
		this.campaignBattleSchedule = new ArrayList<>(campaignBattleSchedule);
	}

	public int getCampaignScheduleIndex() {
		return campaignScheduleIndex;
	}

	public void setCampaignScheduleIndex(int campaignScheduleIndex) {
		this.campaignScheduleIndex = Math.max(0, campaignScheduleIndex);
	}

	public List<ScheduledCampaignBattle> getCampaignCounterSchedule() {
		return campaignCounterSchedule;
	}

	public void setCampaignCounterSchedule(List<ScheduledCampaignBattle> campaignCounterSchedule) {
		if (campaignCounterSchedule == null || campaignCounterSchedule.isEmpty()) {
			this.campaignCounterSchedule = new ArrayList<>();
			return;
		}
		this.campaignCounterSchedule = new ArrayList<>(campaignCounterSchedule);
	}

	public int getCampaignCounterScheduleIndex() {
		return campaignCounterScheduleIndex;
	}

	public void setCampaignCounterScheduleIndex(int campaignCounterScheduleIndex) {
		this.campaignCounterScheduleIndex = Math.max(0, campaignCounterScheduleIndex);
	}

	public Map<String, CampaignCoalition> getFortControllers() {
		return Collections.unmodifiableMap(fortControllers);
	}

	public void setFortControllers(Map<String, CampaignCoalition> fortControllers) {
		if (fortControllers == null || fortControllers.isEmpty()) {
			this.fortControllers = new HashMap<>();
			return;
		}
		this.fortControllers = new HashMap<>(fortControllers);
	}

	public void putFortController(String fortInstallationId, CampaignCoalition coalition) {
		if (fortInstallationId == null || fortInstallationId.isBlank() || coalition == null) {
			return;
		}
		fortControllers.put(fortInstallationId, coalition);
	}

	public Map<String, String> getWartimeInstallationOwners() {
		return wartimeInstallationOwners;
	}

	public void setWartimeInstallationOwners(Map<String, String> wartimeInstallationOwners) {
		if (wartimeInstallationOwners == null || wartimeInstallationOwners.isEmpty()) {
			this.wartimeInstallationOwners = new LinkedHashMap<>();
			return;
		}
		this.wartimeInstallationOwners = new LinkedHashMap<>(wartimeInstallationOwners);
	}

	public void putWartimeInstallationOwner(String installationId, String originalFactionId) {
		if (installationId == null || installationId.isBlank() || originalFactionId == null || originalFactionId.isBlank()) {
			return;
		}
		if (wartimeInstallationOwners.containsKey(installationId)) {
			return;
		}
		wartimeInstallationOwners.put(installationId, originalFactionId);
	}

	public void clearWartimeInstallationOwners() {
		wartimeInstallationOwners.clear();
	}

	public Set<String> getConcededScheduleSlots() {
		return Collections.unmodifiableSet(concededScheduleSlots);
	}

	public void setConcededScheduleSlots(Collection<String> slots) {
		concededScheduleSlots = new LinkedHashSet<>();
		if (slots != null) {
			for (String slot : slots) {
				if (slot != null && !slot.isBlank()) {
					concededScheduleSlots.add(slot);
				}
			}
		}
	}

	public void addConcededScheduleSlot(String slotKey) {
		if (slotKey != null && !slotKey.isBlank()) {
			concededScheduleSlots.add(slotKey);
		}
	}

	public int getLocationBattleCount(String locationKey) {
		if (locationKey == null || locationKey.isBlank()) {
			return 0;
		}
		return locationBattleCounts.getOrDefault(locationKey, 0);
	}

	public void recordLocationBattle(String locationKey) {
		if (locationKey == null || locationKey.isBlank()) {
			return;
		}
		locationBattleCounts.put(locationKey, getLocationBattleCount(locationKey) + 1);
	}

	public Map<String, Integer> getLocationBattleCounts() {
		return locationBattleCounts;
	}

	public void setLocationBattleCounts(Map<String, Integer> locationBattleCounts) {
		this.locationBattleCounts = locationBattleCounts != null
				? new HashMap<>(locationBattleCounts)
				: new HashMap<>();
	}

	public BattleSchedulePhase getBattleSchedulePhase() {
		return battleSchedulePhase;
	}

	public void setBattleSchedulePhase(BattleSchedulePhase battleSchedulePhase) {
		this.battleSchedulePhase = battleSchedulePhase != null ? battleSchedulePhase : BattleSchedulePhase.IDLE;
	}

	public LocalDate getBattleDay() {
		return battleDay;
	}

	public void setBattleDay(LocalDate battleDay) {
		this.battleDay = battleDay;
	}

	public Instant getScheduledBattleAt() {
		return scheduledBattleAt;
	}

	public void setScheduledBattleAt(Instant scheduledBattleAt) {
		this.scheduledBattleAt = scheduledBattleAt;
	}

	public int getScheduledBattleHour() {
		return scheduledBattleHour;
	}

	public void setScheduledBattleHour(int scheduledBattleHour) {
		this.scheduledBattleHour = scheduledBattleHour;
	}

	public Integer getScheduledBattleProvinceId() {
		return scheduledBattleProvinceId;
	}

	public void setScheduledBattleProvinceId(Integer scheduledBattleProvinceId) {
		this.scheduledBattleProvinceId = scheduledBattleProvinceId;
	}

	public Set<Integer> getSignupRemindersSent() {
		return signupRemindersSent;
	}

	public void setSignupRemindersSent(Set<Integer> signupRemindersSent) {
		this.signupRemindersSent = signupRemindersSent != null
				? new LinkedHashSet<>(signupRemindersSent)
				: new LinkedHashSet<>();
	}

	public void clearSignupRemindersSent() {
		signupRemindersSent.clear();
	}

	public Map<UUID, Set<Integer>> getBattleVotes() {
		if (battleVotes == null) {
			battleVotes = new HashMap<>();
		}
		return battleVotes;
	}

	public void setBattleVotes(Map<UUID, Set<Integer>> battleVotes) {
		this.battleVotes = battleVotes != null ? battleVotes : new HashMap<>();
	}

	public Map<String, LinkedHashSet<String>> getBattleInstallationPicks() {
		if (battleInstallationPicks == null) {
			battleInstallationPicks = new LinkedHashMap<>();
		}
		return battleInstallationPicks;
	}

	public void setBattleInstallationPicks(Map<String, LinkedHashSet<String>> battleInstallationPicks) {
		if (battleInstallationPicks == null || battleInstallationPicks.isEmpty()) {
			this.battleInstallationPicks = new LinkedHashMap<>();
			return;
		}
		Map<String, LinkedHashSet<String>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, LinkedHashSet<String>> entry : battleInstallationPicks.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
		}
		this.battleInstallationPicks = copy;
	}

	public LocalDate getBattleInstallationPicksBattleDay() {
		return battleInstallationPicksBattleDay;
	}

	public void setBattleInstallationPicksBattleDay(LocalDate battleInstallationPicksBattleDay) {
		this.battleInstallationPicksBattleDay = battleInstallationPicksBattleDay;
	}

	public Map<String, String> getCampaignRaidsUsed() {
		if (campaignRaidsUsed == null) {
			campaignRaidsUsed = new LinkedHashMap<>();
		}
		return campaignRaidsUsed;
	}

	public void setCampaignRaidsUsed(Map<String, String> campaignRaidsUsed) {
		if (campaignRaidsUsed == null || campaignRaidsUsed.isEmpty()) {
			this.campaignRaidsUsed = new LinkedHashMap<>();
			return;
		}
		this.campaignRaidsUsed = new LinkedHashMap<>(campaignRaidsUsed);
	}

	public CampaignRaid getActiveCampaignRaid() {
		return activeCampaignRaid;
	}

	public void setActiveCampaignRaid(CampaignRaid activeCampaignRaid) {
		this.activeCampaignRaid = activeCampaignRaid;
	}

	public Map<String, Instant> getRaidRepairLockUntil() {
		if (raidRepairLockUntil == null) {
			raidRepairLockUntil = new LinkedHashMap<>();
		}
		return raidRepairLockUntil;
	}

	public void setRaidRepairLockUntil(Map<String, Instant> raidRepairLockUntil) {
		if (raidRepairLockUntil == null || raidRepairLockUntil.isEmpty()) {
			this.raidRepairLockUntil = new LinkedHashMap<>();
			return;
		}
		Map<String, Instant> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Instant> entry : raidRepairLockUntil.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			copy.put(entry.getKey(), entry.getValue());
		}
		this.raidRepairLockUntil = copy;
	}

	public boolean isAutoresolveProposedByAttacker() {
		return autoresolveProposedByAttacker;
	}

	public void setAutoresolveProposedByAttacker(boolean autoresolveProposedByAttacker) {
		this.autoresolveProposedByAttacker = autoresolveProposedByAttacker;
	}

	public boolean isAutoresolveProposedByDefender() {
		return autoresolveProposedByDefender;
	}

	public void setAutoresolveProposedByDefender(boolean autoresolveProposedByDefender) {
		this.autoresolveProposedByDefender = autoresolveProposedByDefender;
	}

	public int getPostponementsThisCycle() {
		return postponementsThisCycle;
	}

	public void setPostponementsThisCycle(int postponementsThisCycle) {
		this.postponementsThisCycle = postponementsThisCycle;
	}

	public boolean isDefenderChoiceResolved() {
		return defenderChoiceResolved;
	}

	public void setDefenderChoiceResolved(boolean defenderChoiceResolved) {
		this.defenderChoiceResolved = defenderChoiceResolved;
	}

	public CampaignCoalition getInitiativeHolderCoalition() {
		return initiativeHolderCoalition;
	}

	public void setInitiativeHolderCoalition(CampaignCoalition initiativeHolderCoalition) {
		this.initiativeHolderCoalition = initiativeHolderCoalition;
	}

	public CampaignPushTarget getPushTarget() {
		return pushTarget;
	}

	public void setPushTarget(CampaignPushTarget pushTarget) {
		this.pushTarget = pushTarget;
	}

	public PostBattleChoicePhase getPostBattleChoicePhase() {
		return postBattleChoicePhase;
	}

	public void setPostBattleChoicePhase(PostBattleChoicePhase postBattleChoicePhase) {
		this.postBattleChoicePhase = postBattleChoicePhase != null
				? postBattleChoicePhase
				: PostBattleChoicePhase.NONE;
	}

	public CampaignCoalition getPostBattleWinnerCoalition() {
		return postBattleWinnerCoalition;
	}

	public void setPostBattleWinnerCoalition(CampaignCoalition postBattleWinnerCoalition) {
		this.postBattleWinnerCoalition = postBattleWinnerCoalition;
	}

	public boolean isPostBattleChoiceResolved() {
		return postBattleChoiceResolved;
	}

	public void setPostBattleChoiceResolved(boolean postBattleChoiceResolved) {
		this.postBattleChoiceResolved = postBattleChoiceResolved;
		this.defenderChoiceResolved = postBattleChoiceResolved;
	}

	public CampaignCoalition getLastBattleOffensiveCoalition() {
		return lastBattleOffensiveCoalition;
	}

	public void setLastBattleOffensiveCoalition(CampaignCoalition lastBattleOffensiveCoalition) {
		this.lastBattleOffensiveCoalition = lastBattleOffensiveCoalition;
	}

	public boolean isHoldPeaceProposalActive() {
		return holdPeaceProposalActive;
	}

	public void setHoldPeaceProposalActive(boolean holdPeaceProposalActive) {
		this.holdPeaceProposalActive = holdPeaceProposalActive;
	}

	public boolean isForceQuorumNextClose() {
		return forceQuorumNextClose;
	}

	public void setForceQuorumNextClose(boolean forceQuorumNextClose) {
		this.forceQuorumNextClose = forceQuorumNextClose;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	void setEndedAt(Instant endedAt) {
		this.endedAt = endedAt;
	}

	public WarEndReason getEndReason() {
		return endReason;
	}

	void setEndReason(WarEndReason endReason) {
		this.endReason = endReason;
	}

	public boolean isActive() {
		return status == WarStatus.ACTIVE;
	}

	public void end(WarEndReason reason) {
		status = WarStatus.ENDED;
		endReason = reason;
		endedAt = Instant.now();
	}

	public void update() {
		for (Participant p : getParticipants()) {
			p.update(this);
		}
	}

	public List<Participant> getParticipants() {
		List<Participant> list = new ArrayList<>();
		list.addAll(attackers.getMainParticipants());
		list.addAll(defenders.getMainParticipants());
		return list;
	}

	public String getName() {
		Faction attacker = attackers.getLeader();
		Faction defender = defenders.getLeader();
		return StringFormatter.formatHex(attacker.getName() + " #a83116vs. " + defender.getName());
	}

	public Participant getParticipant(Faction f) {
		for (Participant p : attackers.getMainParticipants()) {
			if (p.getLeader().getId().equalsIgnoreCase(f.getId())) return p;
		}
		for (Participant p : defenders.getMainParticipants()) {
			if (p.getLeader().getId().equalsIgnoreCase(f.getId())) return p;
		}
		return null;
	}

	public Side getSide(Faction f) {
		for (Participant p : attackers.getMainParticipants()) {
			if (p.getLeader().getId().equalsIgnoreCase(f.getId())) return attackers;
			if (p.getSubjects().contains(f)) return attackers;
			if (p.isJoinedSecondary(f)) return attackers;
		}
		for (Participant p : defenders.getMainParticipants()) {
			if (p.getLeader().getId().equalsIgnoreCase(f.getId())) return defenders;
			if (p.getSubjects().contains(f)) return defenders;
			if (p.isJoinedSecondary(f)) return defenders;
		}
		return null;
	}

	public Side getSide(Participant p) {
		return getSide(p.getLeader());
	}

	public Side getOppositeSide(Faction f) {
		Side same = getSide(f);
		if (same == null) return null;
		if (same.equals(attackers)) return defenders;
		if (same.equals(defenders)) return attackers;
		return null;
	}

	public HashMap<Faction, WarGoal> getWarGoalsOn(Faction p) {
		HashMap<Faction, WarGoal> map = new HashMap<>();
		Side side = getSide(p);
		if (side == null) return map;
		List<Participant> list;
		if (side.equals(defenders)) {
			list = attackers.getMainParticipants();
		} else {
			list = defenders.getMainParticipants();
		}
		for (Participant par : list) {
			if (par.hasWarGoal(p)) map.put(par.getLeader(), par.getWarGoal(p));
		}
		return map;
	}

	public boolean isMainParticipant(Faction f) {
		return getParticipant(f) != null;
	}

	public boolean isParticipating(Faction f) {
		if (f == null) {
			return false;
		}
		return attackers.isParticipating(f) || defenders.isParticipating(f);
	}

	public String getType(Faction f) {
		for (Participant p : attackers.getMainParticipants()) {
			if (p.getLeader().getId().equalsIgnoreCase(f.getId())) return "main_attacker";
		}
		for (Participant p : defenders.getMainParticipants()) {
			if (p.getLeader().getId().equalsIgnoreCase(f.getId())) return "main_defender";
		}
		return "secondary_participant";
	}

	public boolean canBeCalled(Faction caller, Faction target) {
		return CallToArmsEligibility.canCall(this, caller, target).allowed();
	}

	public Faction getEnemy(Faction f) {
		if (attackers.isParticipating(f)) return defenders.getLeader();
		if (defenders.isParticipating(f)) return attackers.getLeader();
		return null;
	}

	public boolean call(Faction caller, Faction joiner) {
		Participant p = getParticipant(caller);
		if (p == null) return false;
		if (!p.getAllies().containsKey(joiner)) return false;
		if (p.getAllies().get(joiner) == true) return false;
		p.getAllies().put(joiner, true);
		return true;
	}
}
