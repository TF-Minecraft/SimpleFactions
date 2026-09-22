package net.tfminecraft.simplefactions.war.civilwar;

import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;

public final class CivilWarGoalMapper {
	private CivilWarGoalMapper() {}

	public static WarGoalType fromFirstCause(Movement movement) {
		if (movement == null || movement.getCauses() == null || movement.getCauses().isEmpty()) {
			return null;
		}
		Cause first = movement.getCauses().get(0);
		if (first == null || first.getAction() == null) {
			return null;
		}
		return fromAction(first.getAction());
	}

	public static WarGoalType fromAction(Action action) {
		if (action == null) {
			return null;
		}
		return switch (action) {
			case CHANGE_LEADER -> WarGoalType.OVERTHROW;
			case LAW_CHANGE -> WarGoalType.CHANGE_LAW;
			case TAX_CHANGE -> WarGoalType.CHANGE_TAX;
			case WHITE_PEACE, SURRENDER -> WarGoalType.FORCE_PEACE;
			default -> null;
		};
	}
}