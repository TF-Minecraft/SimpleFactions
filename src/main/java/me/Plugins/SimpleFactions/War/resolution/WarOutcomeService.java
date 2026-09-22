package me.Plugins.SimpleFactions.War.resolution;

import java.util.LinkedHashSet;
import java.util.Set;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.ProvinceHandler;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.declare.DeJureAnnexEligibility;
import me.Plugins.SimpleFactions.War.declare.OpenMarketEligibility;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.installation.InstallationTransferService;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeService;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeSource;
import me.Plugins.SimpleFactions.laws.Law;

public final class WarOutcomeService {
	private WarOutcomeService() {}

	public static void apply(War war, WarEndReason reason) {
		if (war == null || reason == null) {
			return;
		}
		switch (reason) {
			case ATTACKER_VICTORY -> applyAttackerGoal(war);
			case DEFENDER_VICTORY -> {
				if (CivilWarBorderLock.isCivilWar(war)) {
					endMovementEmpty(war);
				} else {
					WarReparationsService.applyFromWar(war);
				}
			}
			case WHITE_PEACE, ADMIN_END -> {
				if (CivilWarBorderLock.isCivilWar(war)) {
					endMovementEmpty(war);
				}
			}
		}
	}

	static void applyAttackerGoal(War war) {
		WarGoalType goal = war.getGoal();
		if (goal == null) {
			return;
		}
		switch (goal) {
			case TRIBUTARY -> applyTributary(war);
			case SUBJUGATE -> applySubjugate(war);
			case TRANSFER_SUBJECT -> applyTransferSubject(war);
			case USURP -> applyUsurp(war);
			case DE_JURE_ANNEX -> applyDeJureAnnex(war);
			case OPEN_MARKET -> applyOpenMarket(war);
			case CHANGE_GOVERNMENT -> applyChangeGovernment(war);
			case PILLAGE -> PillageApplyService.apply(war);
			case OVERTHROW, CHANGE_LAW, CHANGE_TAX, FORCE_PEACE -> applyMovementWar(war);
			case WAR -> {
			}
		}
	}

	private static void applyMovementWar(War war) {
		String movementId = war.getMovementId();
		if (movementId == null || movementId.isBlank()) {
			return;
		}
		Movement movement = FactionManager.getMovementById(movementId);
		if (movement == null) {
			return;
		}
		MovementOutcomeService.apply(movement, MovementOutcomeSource.WAR);
	}

	private static void endMovementEmpty(War war) {
		String movementId = war.getMovementId();
		if (movementId == null || movementId.isBlank()) {
			return;
		}
		Movement movement = FactionManager.getMovementById(movementId);
		if (movement == null || movement.getFaction() == null || movement.getFaction().getGovernment() == null) {
			return;
		}
		movement.getFaction().getGovernment().endMovement(movement);
	}

	private static void applyTributary(War war) {
		if (war.getAttackers() == null || war.getDefenders() == null) {
			return;
		}
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		if (attacker == null || defender == null) {
			return;
		}
		RelationType tributary = RelationLoader.getType("tributary");
		if (tributary == null) {
			return;
		}
		RelationManager.setRelationForced(tributary, defender, attacker);
	}

	private static void applySubjugate(War war) {
		if (war.getAttackers() == null || war.getDefenders() == null) {
			return;
		}
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		if (attacker == null || defender == null) {
			return;
		}
		String typeId = war.getRelationTypeId();
		if (typeId == null || typeId.isBlank()) {
			return;
		}
		RelationType type = RelationLoader.getType(typeId);
		if (!RelationLoader.isWarPickableVassal(type)) {
			return;
		}
		if (war.isInternalWar()) {
			RelationManager.transferSubject(defender, attacker);
			if (!chosenTypeAlreadySet(attacker, defender, type)) {
				RelationManager.setRelationForced(type, defender, attacker);
			}
			return;
		}
		RelationManager.setRelationForced(type, defender, attacker);
	}

	private static boolean chosenTypeAlreadySet(Faction attacker, Faction defender, RelationType type) {
		if (attacker == null || defender == null || type == null || type.getId() == null) {
			return false;
		}
		Relation relation = attacker.getRelation(defender.getId());
		if (relation == null || relation.getType() == null || relation.getType().getId() == null) {
			return false;
		}
		return type.getId().equalsIgnoreCase(relation.getType().getId());
	}

	private static void applyTransferSubject(War war) {
		if (war.getAttackers() == null) {
			return;
		}
		Faction attacker = war.getAttackers().getLeader();
		String subjectId = war.getSubjectFactionId();
		if (attacker == null || subjectId == null || subjectId.isBlank()) {
			return;
		}
		Faction subject = FactionManager.getByString(subjectId);
		if (subject == null) {
			return;
		}
		RelationManager.transferSubject(subject, attacker);
	}

	private static void applyUsurp(War war) {
		if (war.getAttackers() == null || war.getDefenders() == null) {
			return;
		}
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		if (attacker == null || defender == null) {
			return;
		}
		FactionManager.usurp(null, attacker, defender);
	}

	private static void applyDeJureAnnex(War war) {
		if (war.getAttackers() == null || war.getDefenders() == null) {
			return;
		}
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		String titleId = war.getTargetTitleId();
		if (attacker == null || defender == null || titleId == null || titleId.isBlank()) {
			return;
		}
		Title title = TitleLoader.getById(titleId);
		if (title == null) {
			return;
		}
		Set<Faction> formerHolders = new LinkedHashSet<>();
		for (Integer provinceId : DeJureAnnexEligibility.incomingProvinces(attacker, defender, title)) {
			if (provinceId == null) {
				continue;
			}
			Faction holder = DeJureAnnexEligibility.ownerOfProvince(provinceId);
			if (holder == null || holder.getId().equalsIgnoreCase(attacker.getId())) {
				continue;
			}
			if (holder.getCapital() == provinceId) {
				continue;
			}
			InstallationTransferService.transfer(holder, attacker, provinceId);
			holder.removeProvince(provinceId, false);
			attacker.addProvince(provinceId);
			formerHolders.add(holder);
		}
		for (Faction holder : formerHolders) {
			ProvinceHandler handler = holder.getProvinceHandler();
			if (handler != null) {
				handler.revalidateClaims();
			}
		}
	}

	private static void applyOpenMarket(War war) {
		if (war.getAttackers() == null || war.getDefenders() == null) {
			return;
		}
		Faction defender = war.getDefenders().getLeader();
		if (defender == null) {
			return;
		}
		OpenMarketEligibility.ResolvedLaw resolved =
				OpenMarketEligibility.resolve(defender, Cache.openMarketApplyDefenderLaw);
		if (resolved == null || resolved.law() == null || resolved.group() == null) {
			return;
		}
		defender.applyLaw(resolved.law(), resolved.group());
		if (defender.getGovernment() != null) {
			defender.getGovernment().addStabilityModifier(
					new StabilityModifier("Forced Market Open", -25, 1));
		}
	}

	private static void applyChangeGovernment(War war) {
		if (war.getAttackers() == null || war.getDefenders() == null) {
			return;
		}
		Faction defender = war.getDefenders().getLeader();
		if (defender == null) {
			return;
		}
		boolean resolvedAny = applyLawIfChanged(defender, war.getGovernmentLawId());
		resolvedAny = applyLawIfChanged(defender, war.getLeadershipLawId()) || resolvedAny;
		if (resolvedAny && defender.getGovernment() != null) {
			defender.getGovernment().addStabilityModifier(
					new StabilityModifier("Forced Government Change", -50, 1));
		}
	}

	private static boolean applyLawIfChanged(Faction defender, String lawId) {
		OpenMarketEligibility.ResolvedLaw resolved = OpenMarketEligibility.resolve(defender, lawId);
		if (resolved == null || resolved.law() == null || resolved.group() == null) {
			return false;
		}
		Law current = resolved.group().getCurrent();
		String currentId = current == null ? null : current.getId();
		if (currentId == null || !currentId.equalsIgnoreCase(resolved.law().getId())) {
			defender.applyLaw(resolved.law(), resolved.group());
		}
		return true;
	}
}
