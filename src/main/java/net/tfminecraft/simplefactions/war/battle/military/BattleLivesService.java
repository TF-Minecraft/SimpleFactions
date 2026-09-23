package net.tfminecraft.simplefactions.war.battle.military;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

public final class BattleLivesService {
	private static final Logger LOGGER = Logger.getLogger(BattleLivesService.class.getName());

	public record SideLivesPreview(
			int committedRegiments,
			int poolLives,
			int rosterFighters,
			int sideLives,
			int mercenarySlots) {
	}

	private BattleLivesService() {}

	public static void applyCampaignLives(Battle battle) {
		if (!shouldApply(battle)) {
			return;
		}
		War war = WarManager.getById(battle.getWarId());
		if (war == null || !war.isActive()) {
			return;
		}
		Integer provinceId = resolveProvinceId(war, battle);
		if (provinceId == null) {
			return;
		}

		battle.setLifeType(LifeType.COLLECTIVE);
		applySideLives(
				battle,
				BattleTemplate.ATTACKER_SIDE,
				war,
				war.getAttackers(),
				provinceId);
		applySideLives(
				battle,
				BattleTemplate.DEFENDER_SIDE,
				war,
				war.getDefenders(),
				provinceId);
	}

	public static SideLivesPreview previewCampaignSideLives(War war, Battle battle, String battleSideId) {
		if (war == null || battle == null || battleSideId == null) {
			return new SideLivesPreview(0, 0, 0, 0, 0);
		}
		Integer provinceId = resolveProvinceId(war, battle);
		Side warSide = resolveWarSide(war, battleSideId);
		BattleSide battleSide = battle.getSideById(battleSideId);
		if (warSide == null || battleSide == null) {
			return new SideLivesPreview(0, 0, 0, 0, 0);
		}
		int committedRegiments = 0;
		if (provinceId != null) {
			committedRegiments = BattlePoolService.totalCommittedRegiments(war, provinceId, warSide);
		}
		int mercenarySlots = mercenarySlots(war, warSide, battleSide);
		committedRegiments += mercenarySlots;
		int rosterFighters = countRosterFighters(battleSide);
		int poolLives = Cache.warBattleLivesPerRegiment * committedRegiments;
		int sideLives = computeSideLives(committedRegiments, rosterFighters);
		return new SideLivesPreview(committedRegiments, poolLives, rosterFighters, sideLives, mercenarySlots);
	}

	public static int computeSideLives(int committedRegiments, int rosterFighters) {
		if (committedRegiments <= 0) {
			return 0;
		}
		int raw = Cache.warBattleLivesPerRegiment * committedRegiments - rosterFighters;
		return Math.max(Cache.warBattleMinSideLives, raw);
	}

	public static int countRosterFighters(BattleSide side) {
		if (side == null) {
			return 0;
		}
		Set<UUID> uniqueMembers = new HashSet<>();
		for (Warband warband : side.getBands()) {
			if (warband == null) {
				continue;
			}
			uniqueMembers.addAll(warband.getMemberIds());
		}
		return uniqueMembers.size();
	}

	private static boolean shouldApply(Battle battle) {
		if (battle == null || battle.getWarId() == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}

	private static void applySideLives(
			Battle battle,
			String battleSideId,
			War war,
			Side warSide,
			int provinceId) {
		BattleSide battleSide = battle.getSideById(battleSideId);
		if (battleSide == null || warSide == null) {
			return;
		}
		int committedRegiments = BattlePoolService.totalCommittedRegiments(war, provinceId, warSide);
		int mercenarySlots = mercenarySlots(war, warSide, battleSide);
		committedRegiments += mercenarySlots;
		if (committedRegiments <= 0) {
			LOGGER.info(
					"Campaign battle " + battle.getId() + " side " + battleSideId
							+ " has zero committed regiments; applying min side lives floor");
		}
		int rosterFighters = countRosterFighters(battleSide);
		int sideLives = computeSideLives(committedRegiments, rosterFighters);
		battleSide.setLives(sideLives);
	}

	static int mercenarySlots(War war, Side warSide, BattleSide battleSide) {
		if (war == null || warSide == null || battleSide == null) {
			return 0;
		}
		int total = 0;
		for (net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements.Engagement engagement
				: net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements.on(war, warSide)) {
			total += net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements
					.coveringMembers(engagement, battleSide);
		}
		return total;
	}

	static Integer resolveProvinceId(War war, Battle battle) {
		if (battle == null) {
			return null;
		}
		Integer provinceId = battle.getProvinceId();
		if (provinceId == null && war != null) {
			provinceId = war.getScheduledBattleProvinceId();
		}
		return provinceId;
	}

	static Side resolveWarSide(War war, String battleSideId) {
		if (war == null || battleSideId == null) {
			return null;
		}
		if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(battleSideId)) {
			return war.getAttackers();
		}
		if (BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(battleSideId)) {
			return war.getDefenders();
		}
		return null;
	}
}
