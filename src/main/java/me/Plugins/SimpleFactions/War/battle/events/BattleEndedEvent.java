package me.Plugins.SimpleFactions.War.battle.events;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.Plugins.SimpleFactions.War.battle.enums.BattleEndReason;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

public class BattleEndedEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final String battleId;
	private final BattleType battleType;
	private final Integer warId;
	private final String winningSideId;
	private final Map<String, Integer> sideCasualties;
	private final Set<UUID> participantIds;
	private final BattleEndReason endReason;
	private final boolean campaignRaid;
	private final boolean lootEnabled;

	public BattleEndedEvent(
			String battleId,
			BattleType battleType,
			Integer warId,
			String winningSideId,
			Map<String, Integer> sideCasualties,
			Set<UUID> participantIds) {
		this(
				battleId,
				battleType,
				warId,
				winningSideId,
				sideCasualties,
				participantIds,
				inferEndReason(winningSideId),
				false,
				true);
	}

	public BattleEndedEvent(
			String battleId,
			BattleType battleType,
			Integer warId,
			String winningSideId,
			Map<String, Integer> sideCasualties,
			Set<UUID> participantIds,
			BattleEndReason endReason) {
		this(
				battleId,
				battleType,
				warId,
				winningSideId,
				sideCasualties,
				participantIds,
				endReason,
				false,
				true);
	}

	public BattleEndedEvent(
			String battleId,
			BattleType battleType,
			Integer warId,
			String winningSideId,
			Map<String, Integer> sideCasualties,
			Set<UUID> participantIds,
			BattleEndReason endReason,
			boolean campaignRaid) {
		this(
				battleId,
				battleType,
				warId,
				winningSideId,
				sideCasualties,
				participantIds,
				endReason,
				campaignRaid,
				true);
	}

	public BattleEndedEvent(
			String battleId,
			BattleType battleType,
			Integer warId,
			String winningSideId,
			Map<String, Integer> sideCasualties,
			Set<UUID> participantIds,
			BattleEndReason endReason,
			boolean campaignRaid,
			boolean lootEnabled) {
		this.battleId = battleId;
		this.battleType = battleType;
		this.warId = warId;
		this.winningSideId = winningSideId;
		if (sideCasualties == null || sideCasualties.isEmpty()) {
			this.sideCasualties = Map.of();
		} else {
			this.sideCasualties = Collections.unmodifiableMap(new HashMap<>(sideCasualties));
		}
		if (participantIds == null || participantIds.isEmpty()) {
			this.participantIds = Set.of();
		} else {
			this.participantIds = Set.copyOf(participantIds);
		}
		this.endReason = endReason != null ? endReason : inferEndReason(winningSideId);
		this.campaignRaid = campaignRaid;
		this.lootEnabled = lootEnabled;
	}

	private static BattleEndReason inferEndReason(String winningSideId) {
		if (winningSideId == null || winningSideId.isBlank()) {
			return BattleEndReason.TIMER;
		}
		return BattleEndReason.SIDE_WIN;
	}

	public String getBattleId() {
		return battleId;
	}

	public BattleType getBattleType() {
		return battleType;
	}

	public Integer getWarId() {
		return warId;
	}

	public String getWinningSideId() {
		return winningSideId;
	}

	public Map<String, Integer> getSideCasualties() {
		return sideCasualties;
	}

	public Set<UUID> getParticipantIds() {
		return participantIds;
	}

	public BattleEndReason getEndReason() {
		return endReason;
	}

	public boolean hasWinner() {
		return winningSideId != null && !winningSideId.isBlank();
	}

	public boolean isCampaignRaid() {
		return campaignRaid;
	}

	public boolean isLootEnabled() {
		return lootEnabled;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
