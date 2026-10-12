package net.tfminecraft.simplefactions.war.battle.events;

import java.util.Set;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.tfminecraft.simplefactions.war.battle.enums.BattleType;

public class BattleStartedEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final String battleId;
	private final BattleType battleType;
	private final Integer warId;
	private final Set<UUID> participantIds;
	private final Set<net.tfminecraft.tlibs.character.CharacterRef> participants;

	public BattleStartedEvent(
			String battleId,
			BattleType battleType,
			Integer warId,
			Set<UUID> participantIds) {
		this(battleId, battleType, warId, participantIds, Set.of());
	}

	public BattleStartedEvent(String battleId, BattleType battleType, Integer warId,
			Set<UUID> participantIds, Set<net.tfminecraft.tlibs.character.CharacterRef> participants) {
		this.participants = Set.copyOf(participants);
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

	/** Characters captured when participation began. */
	public Set<net.tfminecraft.tlibs.character.CharacterRef> getParticipants() { return participants; }

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
