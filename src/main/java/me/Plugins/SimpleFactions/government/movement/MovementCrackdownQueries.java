package me.Plugins.SimpleFactions.government.movement;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public final class MovementCrackdownQueries {

	public static final String GROUP_ID = "assembly";
	public static final String LAW_NONE = "none";
	public static final String LAW_RIGHT_TO_ASSEMBLY = "right_to_assembly";
	public static final String LAW_STATE_CRACKDOWNS = "state_crackdowns";

	private MovementCrackdownQueries() {}

	public static String assemblyLawId(Faction host) {
		if (host == null || host.getLawHandler() == null) {
			return LAW_NONE;
		}
		LawHandler handler = host.getLawHandler();
		LawGroup group = handler.getGroup(GROUP_ID);
		if (group == null || group.getCurrent() == null) {
			return LAW_NONE;
		}
		Law current = group.getCurrent();
		if (current.getId() == null || current.getId().isBlank()) {
			return LAW_NONE;
		}
		return current.getId();
	}

	public static boolean phaseAllowed(String lawId, Phase phase) {
		if (phase == null) {
			return false;
		}
		String id = lawId == null || lawId.isBlank() ? LAW_NONE : lawId;
		if (LAW_STATE_CRACKDOWNS.equalsIgnoreCase(id)) {
			return true;
		}
		if (LAW_RIGHT_TO_ASSEMBLY.equalsIgnoreCase(id)) {
			return phase == Phase.REBELLIOUS;
		}
		return phase.getIndex() >= Phase.AGITATED.getIndex();
	}

	public static boolean canCrush(Faction host, Movement movement) {
		if (host == null || movement == null || movement.getPhase() == null) {
			return false;
		}
		if (movement.isFrozen()) {
			return false;
		}
		return phaseAllowed(assemblyLawId(host), movement.getPhase());
	}

	public static String denyReason(Faction host, Movement movement) {
		if (canCrush(host, movement)) {
			return null;
		}
		if (movement != null && movement.isFrozen()) {
			return "The movement cannot be disbanded while frozen.";
		}
		if (LAW_RIGHT_TO_ASSEMBLY.equalsIgnoreCase(assemblyLawId(host))) {
			return "Your assembly law only allows this when the movement is rebellious.";
		}
		return "The movement is not agitated enough.";
	}
}
