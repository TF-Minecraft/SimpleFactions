package me.Plugins.SimpleFactions.War.campaign.raid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import me.Plugins.SimpleFactions.Database.CampaignRaidData;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidKind;

public class CampaignRaid {
	private String id;
	private String displayName;
	private int warId;
	private LocalDate battleDay;
	private CampaignCoalition attackerCoalition;
	private String launcherFactionId;
	private String sourceInstallationId;
	private String targetInstallationId;
	private RaidKind raidKind;
	private CampaignRaidState state;
	private Instant musterEndsAt;
	private Instant fightEndsAt;
	private String battleId;
	private Set<String> musterParticipantIds = new LinkedHashSet<>();
	private Set<Integer> musterRemindersSent = new LinkedHashSet<>();

	public static CampaignRaid fromData(CampaignRaidData data) {
		if (data == null || data.id == null || data.id.isBlank()) {
			return null;
		}
		CampaignRaid raid = new CampaignRaid();
		raid.id = data.id;
		raid.displayName = data.displayName;
		raid.warId = data.warId;
		if (data.battleDay != null && !data.battleDay.isBlank()) {
			raid.battleDay = LocalDate.parse(data.battleDay);
		}
		raid.attackerCoalition = CampaignCoalition.fromJson(data.attackerCoalition);
		raid.launcherFactionId = data.launcherFactionId;
		raid.sourceInstallationId = data.sourceInstallationId;
		raid.targetInstallationId = data.targetInstallationId;
		raid.raidKind = raidKindFromJson(data.raidKind);
		raid.state = CampaignRaidState.fromJson(data.state);
		if (data.musterEndsAt != null && !data.musterEndsAt.isBlank()) {
			raid.musterEndsAt = Instant.parse(data.musterEndsAt);
		}
		if (data.fightEndsAt != null && !data.fightEndsAt.isBlank()) {
			raid.fightEndsAt = Instant.parse(data.fightEndsAt);
		}
		raid.battleId = data.battleId;
		if (data.musterParticipantIds != null) {
			raid.musterParticipantIds.addAll(data.musterParticipantIds);
		}
		if (data.musterRemindersSent != null) {
			for (Integer offset : data.musterRemindersSent) {
				if (offset != null) {
					raid.musterRemindersSent.add(offset);
				}
			}
		}
		return raid;
	}

	public CampaignRaidData toData() {
		CampaignRaidData data = new CampaignRaidData();
		data.id = id;
		data.displayName = displayName;
		data.warId = warId;
		if (battleDay != null) {
			data.battleDay = battleDay.toString();
		}
		if (attackerCoalition != null) {
			data.attackerCoalition = attackerCoalition.toJson();
		}
		data.launcherFactionId = launcherFactionId;
		data.sourceInstallationId = sourceInstallationId;
		data.targetInstallationId = targetInstallationId;
		if (raidKind != null) {
			data.raidKind = raidKind.name();
		}
		if (state != null) {
			data.state = state.toJson();
		}
		if (musterEndsAt != null) {
			data.musterEndsAt = musterEndsAt.toString();
		}
		if (fightEndsAt != null) {
			data.fightEndsAt = fightEndsAt.toString();
		}
		data.battleId = battleId;
		if (musterParticipantIds != null && !musterParticipantIds.isEmpty()) {
			data.musterParticipantIds = new java.util.ArrayList<>(musterParticipantIds);
		}
		if (musterRemindersSent != null && !musterRemindersSent.isEmpty()) {
			data.musterRemindersSent = new java.util.ArrayList<>(musterRemindersSent);
		}
		return data;
	}

	private static RaidKind raidKindFromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return RaidKind.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public int getWarId() {
		return warId;
	}

	public void setWarId(int warId) {
		this.warId = warId;
	}

	public LocalDate getBattleDay() {
		return battleDay;
	}

	public void setBattleDay(LocalDate battleDay) {
		this.battleDay = battleDay;
	}

	public CampaignCoalition getAttackerCoalition() {
		return attackerCoalition;
	}

	public void setAttackerCoalition(CampaignCoalition attackerCoalition) {
		this.attackerCoalition = attackerCoalition;
	}

	public String getLauncherFactionId() {
		return launcherFactionId;
	}

	public void setLauncherFactionId(String launcherFactionId) {
		this.launcherFactionId = launcherFactionId;
	}

	public String getSourceInstallationId() {
		return sourceInstallationId;
	}

	public void setSourceInstallationId(String sourceInstallationId) {
		this.sourceInstallationId = sourceInstallationId;
	}

	public String getTargetInstallationId() {
		return targetInstallationId;
	}

	public void setTargetInstallationId(String targetInstallationId) {
		this.targetInstallationId = targetInstallationId;
	}

	public RaidKind getRaidKind() {
		return raidKind;
	}

	public void setRaidKind(RaidKind raidKind) {
		this.raidKind = raidKind;
	}

	public CampaignRaidState getState() {
		return state;
	}

	public void setState(CampaignRaidState state) {
		this.state = state;
	}

	public Instant getMusterEndsAt() {
		return musterEndsAt;
	}

	public void setMusterEndsAt(Instant musterEndsAt) {
		this.musterEndsAt = musterEndsAt;
	}

	public Instant getFightEndsAt() {
		return fightEndsAt;
	}

	public void setFightEndsAt(Instant fightEndsAt) {
		this.fightEndsAt = fightEndsAt;
	}

	public String getBattleId() {
		return battleId;
	}

	public void setBattleId(String battleId) {
		this.battleId = battleId;
	}

	public Set<String> getMusterParticipantIds() {
		if (musterParticipantIds == null) {
			musterParticipantIds = new LinkedHashSet<>();
		}
		return musterParticipantIds;
	}

	public void setMusterParticipantIds(Set<String> musterParticipantIds) {
		this.musterParticipantIds = musterParticipantIds != null
				? new LinkedHashSet<>(musterParticipantIds)
				: new LinkedHashSet<>();
	}

	public Set<Integer> getMusterRemindersSent() {
		if (musterRemindersSent == null) {
			musterRemindersSent = new LinkedHashSet<>();
		}
		return musterRemindersSent;
	}

	public void setMusterRemindersSent(Set<Integer> musterRemindersSent) {
		this.musterRemindersSent = musterRemindersSent != null
				? new LinkedHashSet<>(musterRemindersSent)
				: new LinkedHashSet<>();
	}

	public void clearMusterRemindersSent() {
		getMusterRemindersSent().clear();
	}
}
