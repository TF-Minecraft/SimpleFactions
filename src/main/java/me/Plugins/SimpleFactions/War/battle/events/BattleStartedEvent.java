package me.Plugins.SimpleFactions.War.battle.events;

import java.util.Set;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.Plugins.SimpleFactions.War.battle.enums.BattleType;

public class BattleStartedEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final String battleId;
	private final BattleType battleType;
	private final Integer warId;
	private final Set<UUID> participantIds;

	public BattleStartedEvent(
			String battleId,
			BattleType battleType,
			Integer warId,
			Set<UUID> participantIds) {
		this.battleId = battleId;
		this.battleType = battleType;
		this.warId = warId;
		this.participantIds = participantIds == null || participantIds.isEmpty()
				? Set.of() : Set.copyOf(participantIds);
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

	public Set<UUID> getParticipantIds() {
		return participantIds;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
