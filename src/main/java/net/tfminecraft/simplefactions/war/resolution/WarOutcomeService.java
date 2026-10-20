package net.tfminecraft.simplefactions.war.resolution;

import java.util.LinkedHashSet;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.ProvinceHandler;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDemand;
import net.tfminecraft.simplefactions.war.core.WarDeclareHelper;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.declare.DeJureAnnexEligibility;
import net.tfminecraft.simplefactions.war.declare.WarDemandRules;
import net.tfminecraft.simplefactions.war.declare.OpenMarketEligibility;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.installation.InstallationTransferService;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.MovementOutcomeService;
import net.tfminecraft.simplefactions.government.movement.MovementOutcomeSource;
import net.tfminecraft.simplefactions.laws.Law;

public final class WarOutcomeService {
	private WarOutcomeService() {}

	public static void apply(War war, WarEndReason reason) {
		if (war == null || reason == null) {
			return;
		}
		switch (reason) {
			case ATTACKER_VICTORY -> {
				if (CivilWarBorderLock.isCivilWar(war)) {
					applyAttackerGoal(war);
				} else {
					applyDemands(war, true, war.getGoal(), () -> applyAttackerGoal(war));
					WarReparationsService.applyFromWar(war, reason);
				}
			}
			case DEFENDER_VICTORY -> {
				if (CivilWarBorderLock.isCivilWar(war)) {
					endMovementEmpty(war);
					// The ruler's single goal on a rebel vassal; the untangle has already restored the realm.
					applyDemands(war, false, null, () -> {});
				} else {
					WarGoalType counter = war.getDefenderCounterGoal();
					applyDemands(war, false, counter, () -> {
						if (counter != null) {
							applyDefenderCounterGoal(war);
						}
					});
					WarReparationsService.applyFromWar(war, reason);
				}
			}
			case WHITE_PEACE, ADMIN_END -> {
				if (CivilWarBorderLock.isCivilWar(war)) {
					endMovementEmpty(war);
				}
			}
		}
	}

	/**
	 * Applies the winning side's first demand and then its second. A demand that changes who
	 * a faction answers to goes last, so it cannot pull the target into the winner's realm
	 * before an annexation or law change on that same target lands.
	 */
	private static void applyDemands(War war, boolean attackerSide, WarGoalType firstGoal, Runnable first) {
		WarDemand second = war.getSecondDemand(attackerSide);
		if (second == null || !stillValid(war, attackerSide, second)) {
			first.run();
			return;
		}
		if (WarDemandRules.isRelational(firstGoal) && !WarDemandRules.isRelational(second.getGoal())) {
			applySecondDemand(war, second);
			first.run();
			return;
		}
		first.run();
		applySecondDemand(war, second);
	}

	/**
	 * A second demand still needs both factions in the war on the same sides, and must not
	 * clash with a first demand that changed after it was set.
	 */
	private static boolean stillValid(War war, boolean attackerSide, WarDemand demand) {
		return containsId(WarDemandRules.beneficiaries(war, attackerSide), demand.getBeneficiaryId())
				&& containsId(WarDemandRules.targets(war, attackerSide), demand.getTargetId())
				&& !WarDemandRules.conflicts(WarDemandRules.firstDemand(war, attackerSide), demand);
	}

	private static boolean containsId(java.util.List<Faction> factions, String id) {
		return id != null && factions.stream().anyMatch(faction -> id.equalsIgnoreCase(faction.getId()));
	}

	static void applySecondDemand(War war, WarDemand demand) {
		Faction beneficiary = demand.getBeneficiary();
		Faction target = demand.getTarget();
		if (beneficiary == null || target == null || demand.getGoal() == null) {
			return;
		}
		if (demand.getGoal() == WarGoalType.SUBJUGATE && WarDemandRules.isCivilWar(war)) {
			// The ruler re-types a restored vassal; any subject type is allowed, not only war-pickable ones.
			RelationType type = RelationLoader.getType(demand.getRelationTypeId());
			if (type != null && type.isVassalage()) {
				RelationManager.setRelationForced(type, target, beneficiary);
			}
			return;
		}
		War demandWar = new War(
				war.getId(),
				new Side(beneficiary),
				new Side(target),
				demand.getGoal(),
				WarDeclareHelper.warTypeForGoal(demand.getGoal()),
				demand.getTargetTitleId(),
				war.getObjectiveProvinceId(),
				war.getStartedAt());
		demandWar.setRelationTypeId(demand.getRelationTypeId());
		demandWar.setSubjectFactionId(demand.getSubjectFactionId());
		demandWar.setGovernmentLawId(demand.getGovernmentLawId());
		demandWar.setLeadershipLawId(demand.getLeadershipLawId());
		applyAttackerGoal(demandWar);
	}

	private static void applyDefenderCounterGoal(War war) {
		War counterWar = new War(
				war.getId(),
				war.getDefenders(),
				war.getAttackers(),
				war.getDefenderCounterGoal(),
				WarDeclareHelper.warTypeForGoal(war.getDefenderCounterGoal()),
				null,
				war.getObjectiveProvinceId(),
				war.getStartedAt());
		counterWar.setRelationTypeId(war.getDefenderCounterRelationTypeId());
		applyAttackerGoal(counterWar);
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
			// A vassal's usurp is won through its movement, which knows who leads the cause.
			case USURP -> {
				if (war.getMovementId() != null && !war.getMovementId().isBlank()) {
					applyMovementWar(war);
				} else {
					applyUsurp(war);
				}
			}
			case DE_JURE_ANNEX -> applyDeJureAnnex(war);
			case OPEN_MARKET -> applyOpenMarket(war);
			case CHANGE_GOVERNMENT -> applyChangeGovernment(war);
			case PILLAGE -> PillageApplyService.apply(war);
			case OVERTHROW, CHANGE_LAW, CHANGE_TAX, FORCE_PEACE, INDEPENDENCE, NATIONHOOD -> applyMovementWar(war);
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
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		RelationType tributary = RelationLoader.getType("tributary");
		if (tributary == null) {
			return;
		}
		RelationManager.setRelationForced(tributary, defender, attacker);
	}

	private static void applySubjugate(War war) {
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
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
		Relation relation = attacker.getRelation(defender.getId());
		if (relation == null || relation.getType() == null || relation.getType().getId() == null) {
			return false;
		}
		return type.getId().equalsIgnoreCase(relation.getType().getId());
	}

	private static void applyTransferSubject(War war) {
		Faction attacker = war.getAttackers().getLeader();
		String subjectId = war.getSubjectFactionId();
		if (subjectId == null || subjectId.isBlank()) {
			return;
		}
		Faction subject = FactionManager.getByString(subjectId);
		if (subject == null) {
			return;
		}
		RelationManager.transferSubject(subject, attacker);
	}

	private static void applyUsurp(War war) {
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		FactionManager.usurp(null, attacker, defender);
	}

	private static void applyDeJureAnnex(War war) {
		Faction attacker = war.getAttackers().getLeader();
		Faction defender = war.getDefenders().getLeader();
		String titleId = war.getTargetTitleId();
		if (titleId == null || titleId.isBlank()) {
			return;
		}
		Title title = TitleLoader.getById(titleId);
		if (title == null) {
			return;
		}
		Set<Faction> formerHolders = new LinkedHashSet<>();
		// Holders keep their settlements, capitals and the road between them.
		for (Integer provinceId : DeJureAnnexEligibility.partition(attacker, defender, title).taken()) {
			Faction holder = DeJureAnnexEligibility.ownerOfProvince(provinceId);
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
		Faction defender = war.getDefenders().getLeader();
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
		Faction defender = war.getDefenders().getLeader();
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
