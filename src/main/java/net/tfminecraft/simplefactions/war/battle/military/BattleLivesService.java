package net.tfminecraft.simplefactions.war.battle.military;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.LifeType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.wounds.BattleForce;
import net.tfminecraft.simplefactions.war.wounds.ForceKind;

/**
 * A campaign battle side's lives: the lives its forces bring minus the soldiers on its roster,
 * at least {@code min_side_lives}, unless staff set the starting lives themselves. The forces
 * are recorded on the side when the battle starts, so its deaths can be shared out later.
 */
public final class BattleLivesService {
	public record SideLivesPreview(
			int slots,
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
		battle.setLifeType(LifeType.COLLECTIVE);
		for (String battleSideId : List.of(BattleTemplate.ATTACKER_SIDE, BattleTemplate.DEFENDER_SIDE)) {
			BattleSide battleSide = battle.getSideById(battleSideId);
			Side warSide = CampaignBattleSides.warSideFor(war, battle, battleSideId);
			if (battleSide != null && warSide != null) {
				List<BattleForce> forces = BattleForces.build(war, provinceId(war, battle), warSide, battle, battleSide);
				battleSide.setForces(forces);
				battleSide.setLives(livesFor(battleSide, forces));
			}
		}
	}

	public static SideLivesPreview previewCampaignSideLives(War war, Battle battle, String battleSideId) {
		if (war == null || battle == null || battleSideId == null) {
			return new SideLivesPreview(0, 0, 0, 0, 0);
		}
		Side warSide = CampaignBattleSides.warSideFor(war, battle, battleSideId);
		BattleSide battleSide = battle.getSideById(battleSideId);
		if (warSide == null || battleSide == null) {
			return new SideLivesPreview(0, 0, 0, 0, 0);
		}
		List<BattleForce> forces = BattleForces.build(war, provinceId(war, battle), warSide, battle, battleSide);
		int slots = 0;
		int mercenarySlots = 0;
		for (BattleForce force : forces) {
			slots += force.slots();
			if (force.kind() == ForceKind.MERCENARY) {
				mercenarySlots += force.slots();
			}
		}
		return new SideLivesPreview(slots, BattleForce.lives(forces), countRosterFighters(battleSide),
				livesFor(battleSide, forces), mercenarySlots);
	}

	private static int livesFor(BattleSide side, List<BattleForce> forces) {
		if (side.getLivesOverride() != null) {
			return side.getLivesOverride();
		}
		return computeSideLives(BattleForce.lives(forces), countRosterFighters(side));
	}

	/** Pool lives minus the roster, at least {@code min_side_lives}; 0 when the side brought nothing. */
	public static int computeSideLives(int poolLives, int rosterFighters) {
		if (poolLives <= 0) {
			return 0;
		}
		return (int) Math.max(Cache.warBattleMinSideLives, (long) poolLives - rosterFighters);
	}

	public static int countRosterFighters(BattleSide side) {
		if (side == null) {
			return 0;
		}
		Set<UUID> uniqueMembers = new HashSet<>();
		for (Warband warband : side.getBands()) {
			if (warband != null) {
				uniqueMembers.addAll(warband.getMemberIds());
			}
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

	private static Integer provinceId(War war, Battle battle) {
		return battle.getProvinceId() != null ? battle.getProvinceId() : war.getScheduledBattleProvinceId();
	}
}
