package me.Plugins.SimpleFactions.government.movement;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;

public final class MovementOutcomeService {
	private MovementOutcomeService() {}

	public static void apply(Movement movement, MovementOutcomeSource source) {
		if (movement == null || source == null) {
			return;
		}
		LogManager.movement(
				"OUTCOME movementId=%s faction=%s source=%s power=%.1f",
				movement.getId(),
				movement.getFaction() != null ? movement.getFaction().getId() : "-",
				source.name(),
				movement.getPower());
		Faction faction = movement.getFaction();
		if (faction == null) {
			return;
		}
		Government government = faction.getGovernment();
		if (government != null) {
			StabilityModifier modifier = stabilityFor(movement, source);
			if (modifier != null) {
				government.addStabilityModifier(modifier);
			}
		}
		for (Cause cause : orderedCauses(movement)) {
			Proposal proposal = cause.getProposal();
			if (proposal == null) {
				continue;
			}
			proposal.apply(cause);
		}
		if (government != null) {
			government.endMovement(movement);
		}
	}

	static List<Cause> orderedCauses(Movement movement) {
		List<Cause> leaders = new ArrayList<>();
		List<Cause> rest = new ArrayList<>();
		if (movement == null || movement.getCauses() == null) {
			return leaders;
		}
		for (Cause cause : movement.getCauses()) {
			if (cause == null) {
				continue;
			}
			if (isLeaderChange(cause)) {
				leaders.add(cause);
			} else {
				rest.add(cause);
			}
		}
		leaders.addAll(rest);
		return leaders;
	}

	static StabilityModifier stabilityFor(Movement movement, MovementOutcomeSource source) {
		if (source == MovementOutcomeSource.ACCEPTED) {
			double magnitude = movement == null ? 0 : movement.getStabilityEffect();
			return new StabilityModifier("Caved to Movement", magnitude, 1);
		}
		if (source != MovementOutcomeSource.WAR || movement == null || movement.getCauses() == null) {
			return null;
		}
		boolean coup = false;
		boolean civilWar = false;
		for (Cause cause : movement.getCauses()) {
			if (isLeaderChange(cause)) {
				coup = true;
			} else if (isLawOrTax(cause)) {
				civilWar = true;
			}
		}
		if (coup) {
			return new StabilityModifier("Coup", -75, 1);
		}
		if (civilWar) {
			return new StabilityModifier("Civil War", -75, 1);
		}
		return null;
	}

	private static boolean isLeaderChange(Cause cause) {
		if (cause == null) {
			return false;
		}
		if (cause.getAction() == Action.CHANGE_LEADER) {
			return true;
		}
		return actionOf(cause) == Action.CHANGE_LEADER;
	}

	private static boolean isLawOrTax(Cause cause) {
		if (cause == null) {
			return false;
		}
		Action action = cause.getAction();
		if (action == Action.LAW_CHANGE || action == Action.TAX_CHANGE) {
			return true;
		}
		Proposal proposal = cause.getProposal();
		if (proposal != null && (proposal.isLawProposal() || proposal.isTaxProposal())) {
			return true;
		}
		Action fromProposal = actionOf(cause);
		return fromProposal == Action.LAW_CHANGE || fromProposal == Action.TAX_CHANGE;
	}

	private static Action actionOf(Cause cause) {
		Proposal proposal = cause.getProposal();
		if (proposal == null || proposal.getPoliticalAction() == null) {
			return null;
		}
		return proposal.getPoliticalAction().getAction();
	}
}
