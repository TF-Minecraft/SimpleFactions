package net.tfminecraft.simplefactions.war.battle.engine.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.simplefactions.war.battle.warband.Warband;

public final class BattleParticipantCollector {
	private BattleParticipantCollector() {}

	public static Set<UUID> collect(Battle battle) {
		if (battle == null) {
			return Set.of();
		}

		Set<UUID> ids = new LinkedHashSet<>();
		for (BattleSide side : battle.getSides()) {
			for (Warband warband : side.getBands()) {
				for (UUID memberId : warband.getMemberIds()) {
					if (!warband.isDummyMember(memberId)) {
						ids.add(memberId);
					}
				}
			}
		}
		return Set.copyOf(ids);
	}
}
