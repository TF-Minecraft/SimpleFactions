package me.Plugins.SimpleFactions.War.declare;

import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.settlement.Settlement;

/**
 * Validates war declare requests before {@code WarManager.addWar}.
 * <p>
 * Step 56.05 wiring example:
 * <pre>
 * WarValidationResult result = new WarGoalValidator().validate(request);
 * if (!result.isValid()) {
 *   player.sendMessage(result.getMessage());
 *   return;
 * }
 * War w = WarManager.addWar(...);
 * </pre>
 */
public class WarGoalValidator {

	public WarValidationResult validate(WarDeclareRequest request) {
		WarValidationResult shared = validateShared(request);
		if (!shared.isValid()) {
			return shared;
		}

		WarValidationResult internalGoals = validateInternalPeerGoals(request);
		if (!internalGoals.isValid()) {
			return internalGoals;
		}

		return switch (request.getGoal()) {
			case DE_JURE_ANNEX -> validateDeJureAnnex(request);
			case SUBJUGATE -> validateSubjugate(request);
			case TRANSFER_SUBJECT -> validateTransferSubject(request);
			case WAR -> WarValidationResult.ok();
			case TRIBUTARY -> validateTributary(request);
			case USURP -> validateUsurp(request);
			case OPEN_MARKET -> validateOpenMarket(request);
			case CHANGE_GOVERNMENT -> validateChangeGovernment(request);
			case PILLAGE -> validatePillage(request);
			case OVERTHROW, CHANGE_LAW, CHANGE_TAX, FORCE_PEACE ->
					WarValidationResult.fail("§cThis war goal cannot be declared yet.");
		};
	}

	private WarValidationResult validateShared(WarDeclareRequest request) {
		Faction attacker = request.getAttacker();
		Faction defender = request.getDefender();
		if (attacker.getId().equalsIgnoreCase(defender.getId())) {
			return WarValidationResult.fail("§cYou cannot declare war on your own faction.");
		}
		War sharedWar = WarManager.findSharedActiveWar(attacker, defender);
		if (sharedWar != null) {
			Side attackerSide = sharedWar.getSide(attacker);
			Side defenderSide = sharedWar.getSide(defender);
			if (attackerSide != null && attackerSide.equals(defenderSide)) {
				return WarValidationResult.fail("§cYou are already allied with that faction in an active war.");
			}
			return WarValidationResult.fail("§cYou are already at war with that faction.");
		}
		if (RelationManager.sameRealm(attacker, defender)
				&& !InterVassalQueries.isInternalPeer(attacker, defender)) {
			boolean usurpOverlord = request.getGoal() == WarGoalType.USURP
					&& RelationManager.isOverlord(attacker, defender);
			if (!usurpOverlord) {
				return WarValidationResult.fail("§cYou cannot declare war on a faction in the same realm.");
			}
		}
		for (Faction ally : RelationManager.getAllies(attacker)) {
			if (ally != null && ally.getId().equalsIgnoreCase(defender.getId())) {
				return WarValidationResult.fail("§cYou cannot declare war on an ally.");
			}
		}
		if (RelationManager.hasNonAggressionPact(attacker, defender)) {
			return WarValidationResult.fail("§cYou cannot declare war while a non-aggression pact is in effect.");
		}
		if (request.getGoal() != WarGoalType.SUBJUGATE
				&& request.getGoal() != WarGoalType.WAR
				&& RelationManager.isTributaryOf(attacker, defender)) {
			return WarValidationResult.fail("§cYou cannot declare war on your tributary.");
		}
		return WarValidationResult.ok();
	}

	private WarValidationResult validateInternalPeerGoals(WarDeclareRequest request) {
		if (!InterVassalQueries.isInternalPeer(request.getAttacker(), request.getDefender())) {
			return WarValidationResult.ok();
		}
		return switch (request.getGoal()) {
			case USURP -> WarValidationResult.fail("§cUsurp can only target your direct overlord.");
			case OVERTHROW, CHANGE_LAW, CHANGE_TAX, FORCE_PEACE ->
					WarValidationResult.fail("§cThis war goal cannot be declared yet.");
			default -> WarValidationResult.ok();
		};
	}

	private WarValidationResult validateDeJureAnnex(WarDeclareRequest request) {
		String targetTitleId = request.getTargetTitleId();
		if (targetTitleId == null || targetTitleId.isBlank()) {
			return WarValidationResult.fail("§cSpecify a de jure title to annex.");
		}

		Title title = TitleLoader.getById(targetTitleId);
		if (title == null) {
			return WarValidationResult.fail("§cThat title does not exist.");
		}
		if (CivilWarBorderLock.isLocked(request.getDefender())) {
			return WarValidationResult.fail(CivilWarCopy.DECLARE_VS_CIVIL_WAR);
		}

		DeJureAnnexEligibility.DeJureTitleOption option =
				DeJureAnnexEligibility.evaluate(request.getAttacker(), request.getDefender(), title);
		if (!option.eligible()) {
			return WarValidationResult.fail(option.blockReason());
		}
		return WarValidationResult.ok();
	}

	private WarValidationResult validateUsurp(WarDeclareRequest request) {
		Faction attacker = request.getAttacker();
		Faction defender = request.getDefender();
		if (defender.getHighestTitle() == null) {
			return WarValidationResult.fail("§cThat faction has no title to usurp.");
		}
		if (!canUsurpByRank(attacker.getTier().getTier(), defender.getTier().getTier())) {
			return WarValidationResult.fail("§cYou cannot usurp a faction of lower rank.");
		}
		return WarValidationResult.ok();
	}

	private WarValidationResult validateOpenMarket(WarDeclareRequest request) {
		String applyId = Cache.openMarketApplyDefenderLaw;
		if (applyId == null || applyId.isBlank()
				|| OpenMarketEligibility.resolve(request.getDefender(), applyId) == null) {
			return WarValidationResult.fail("§cOpen Market is not configured.");
		}
		if (OpenMarketEligibility.hasAnyCurrentLaw(
				request.getDefender(), Cache.openMarketDefenderMustNotHave)) {
			return WarValidationResult.fail("§cThey already have an open market.");
		}
		if (OpenMarketEligibility.hasAnyCurrentLaw(
				request.getAttacker(), Cache.openMarketAttackerMustNotHave)) {
			return WarValidationResult.fail("§cYou cannot force open markets with your current trade law.");
		}
		return WarValidationResult.ok();
	}

	private WarValidationResult validateChangeGovernment(WarDeclareRequest request) {
		Faction defender = request.getDefender();
		String governmentLawId = request.getGovernmentLawId();
		if (ChangeGovernmentEligibility.group(defender, ChangeGovernmentEligibility.GOVERNMENT_GROUP) == null
				|| !ChangeGovernmentEligibility.lawInGroup(
						defender, ChangeGovernmentEligibility.GOVERNMENT_GROUP, governmentLawId)) {
			return WarValidationResult.fail("§cChange Government is not configured.");
		}
		String leadershipLawId = request.getLeadershipLawId();
		if (leadershipLawId != null && !leadershipLawId.isBlank()) {
			if (ChangeGovernmentEligibility.group(defender, ChangeGovernmentEligibility.LEADERSHIP_GROUP) == null
					|| !ChangeGovernmentEligibility.lawInGroup(
							defender, ChangeGovernmentEligibility.LEADERSHIP_GROUP, leadershipLawId)) {
				return WarValidationResult.fail("§cThat leadership law is not available.");
			}
		}
		if (ChangeGovernmentEligibility.combinationEqualsCurrent(defender, governmentLawId, leadershipLawId)) {
			return WarValidationResult.fail("§cThey already have that government.");
		}
		return WarValidationResult.ok();
	}

	private WarValidationResult validatePillage(WarDeclareRequest request) {
		String settlementId = request.getTargetSettlementId();
		if (settlementId == null || settlementId.isBlank()) {
			return WarValidationResult.fail("§cSpecify a settlement to pillage.");
		}
		Settlement settlement = PillageEligibility.findSettlement(settlementId);
		if (settlement == null) {
			return WarValidationResult.fail("§cThat settlement does not exist.");
		}
		PillageEligibility.PillageSettlementOption option =
				PillageEligibility.evaluate(request.getAttacker(), request.getDefender(), settlement);
		if (!option.eligible()) {
			return WarValidationResult.fail(option.blockReason());
		}
		return WarValidationResult.ok();
	}

	private WarValidationResult validateTributary(WarDeclareRequest request) {
		if (RelationLoader.getType("tributary") == null) {
			return WarValidationResult.fail("§cTributary diplomacy is not configured.");
		}
		Faction defender = request.getDefender();
		if (RelationManager.getOverlord(defender) != null) {
			return WarValidationResult.fail("§cYou can only make independent factions tributary.");
		}
		return WarValidationResult.ok();
	}

	private WarValidationResult validateSubjugate(WarDeclareRequest request) {
		Faction attacker = request.getAttacker();
		Faction defender = request.getDefender();

		if (!attacker.canHaveVassals()) {
			return WarValidationResult.fail("§cYour faction cannot have vassals!");
		}

		if (!FactionManager.factions.contains(defender)) {
			return WarValidationResult.fail("§cInvalid war target.");
		}

		String attackerOverlord = RelationManager.getOverlord(attacker);
		if (attackerOverlord != null && attackerOverlord.equalsIgnoreCase(defender.getId())) {
			return WarValidationResult.fail("§cYou cannot subjugate your overlord.");
		}

		String overlord = RelationManager.getOverlord(defender);
		if (overlord != null && overlord.equalsIgnoreCase(attacker.getId())) {
			return WarValidationResult.fail("§cThat faction is already your subject.");
		}
		if (overlord != null && !InterVassalQueries.isInternalPeer(attacker, defender)) {
			return WarValidationResult.fail("§cThat faction is already a subject of someone else.");
		}

		if (RelationManager.isOnOverlordPath(attacker, defender)) {
			return WarValidationResult.fail("§cThis relation would cause a loop.");
		}

		String relationTypeId = request.getRelationTypeId();
		if (relationTypeId == null || relationTypeId.isBlank()) {
			return WarValidationResult.fail("§cSpecify a subject type.");
		}
		RelationType type = RelationLoader.getType(relationTypeId);
		if (!RelationLoader.isWarPickableVassal(type)) {
			return WarValidationResult.fail("§cThat subject type cannot be chosen for war.");
		}
		if (RelationManager.atLimit(attacker, type)) {
			return WarValidationResult.fail("§cYou have reached the limit for this relation type.");
		}

		return WarValidationResult.ok();
	}

	private WarValidationResult validateTransferSubject(WarDeclareRequest request) {
		String subjectFactionId = request.getSubjectFactionId();
		if (subjectFactionId == null || subjectFactionId.isBlank()) {
			return WarValidationResult.fail("§cSpecify a valid subject faction.");
		}

		Faction subject = FactionManager.getByString(subjectFactionId);
		if (subject == null) {
			return WarValidationResult.fail("§cSpecify a valid subject faction.");
		}

		Faction attacker = request.getAttacker();
		Faction defender = request.getDefender();

		if (!attacker.canHaveVassals()) {
			return WarValidationResult.fail("§cYour faction cannot have vassals!");
		}

		if (subject.getId().equalsIgnoreCase(attacker.getId())) {
			return WarValidationResult.fail("§cYou cannot transfer your own faction as a subject.");
		}

		String overlord = RelationManager.getOverlord(subject);
		if (overlord == null) {
			return WarValidationResult.fail("§cThat faction is not a subject of the defender.");
		}
		if (overlord.equalsIgnoreCase(attacker.getId())) {
			return WarValidationResult.fail("§cThat faction is already your subject.");
		}
		if (!RelationManager.isOnOverlordPath(subject, defender)) {
			return WarValidationResult.fail("§cThat faction is not a subject of the defender.");
		}
		if (CivilWarBorderLock.isLocked(defender) || CivilWarBorderLock.isLocked(subject)) {
			return WarValidationResult.fail(CivilWarCopy.DECLARE_VS_CIVIL_WAR);
		}

		Faction liege = FactionManager.getByString(overlord);
		if (liege != null) {
			Relation relation = liege.getRelation(subject.getId());
			if (relation != null && relation.getType() != null
					&& RelationManager.atLimit(attacker, relation.getType())) {
				return WarValidationResult.fail("§cYou have reached the limit for this relation type.");
			}
		}

		return WarValidationResult.ok();
	}

	static boolean canAnnexByRank(int attackerTierLevel, int titleTierLevel) {
		return attackerTierLevel >= titleTierLevel;
	}

	public static boolean canUsurpByRank(int attackerTierLevel, int defenderTierLevel) {
		return attackerTierLevel <= defenderTierLevel;
	}

	static boolean titleProvincesContainSettlement(
			Set<Integer> titleProvinces,
			List<SettlementProbe> settlements,
			List<Integer> capitals) {
		for (SettlementProbe settlement : settlements) {
			if (titleProvinces.contains(settlement.centerProvince())) {
				return true;
			}
		}
		for (Integer capital : capitals) {
			if (capital != null && capital > 0 && titleProvinces.contains(capital)) {
				return true;
			}
		}
		return false;
	}

	record SettlementProbe(int centerProvince) {}
}
