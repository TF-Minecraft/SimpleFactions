package net.tfminecraft.simplefactions.war.battle.military;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.history.BattleResolution;
import net.tfminecraft.simplefactions.war.wounds.BattleForce;
import net.tfminecraft.simplefactions.war.wounds.WarLedger;
import net.tfminecraft.simplefactions.war.wounds.WarWounds;

/**
 * Puts a resolved field battle or siege in its war's ledger: what each force brought (recorded
 * when the battle started, or read now for an autoresolve) and each side's deaths. The wounds
 * follow from that; nothing is taken from a faction until the war ends.
 */
public final class BattleCasualtyService {
	private BattleCasualtyService() {}

	/** Records the battle and returns, per faction regiment, the slots it wounded. */
	public static BattleCasualtyTally applyBattleCasualties(War war, Battle battle, Map<String, Integer> sideCasualties,
			BattleResolution resolution) {
		if (!shouldApply(war, battle)) {
			return BattleCasualtyTally.empty();
		}
		Integer provinceId = battle.getProvinceId() != null ? battle.getProvinceId() : war.getScheduledBattleProvinceId();
		WarLedger.SideRecord attacker = new WarLedger.SideRecord(0, List.of());
		WarLedger.SideRecord defender = new WarLedger.SideRecord(0, List.of());
		for (String battleSideId : List.of(BattleTemplate.ATTACKER_SIDE, BattleTemplate.DEFENDER_SIDE)) {
			Side warSide = CampaignBattleSides.warSideFor(war, battle, battleSideId);
			BattleSide battleSide = battle.getSideById(battleSideId);
			List<BattleForce> forces = battleSide != null && battleSide.hasForces() ? battleSide.getForces()
					: BattleForces.build(war, provinceId, warSide, battle, battleSide);
			int deaths = sideCasualties == null ? 0 : sideCasualties.getOrDefault(battleSideId, 0);
			WarLedger.SideRecord record = new WarLedger.SideRecord(deaths, forces);
			if (warSide == war.getAttackers()) {
				attacker = record;
			} else {
				defender = record;
			}
		}
		BattleCasualtyTally tally = WarWounds.recordBattle(war, battle.getId(), resolution.toJson(), provinceId,
				attacker, defender);
		broadcast(war, tally);
		return tally;
	}

	/** Deaths that wound a side one regiment slot (5 when unset). */
	public static int deathsPerRegimentLoss() {
		return WarWounds.deathsPerSlot();
	}

	public static int regimentLossesForDeaths(int deaths) {
		return deaths <= 0 ? 0 : deaths / deathsPerRegimentLoss();
	}

	public static boolean shouldApply(War war, Battle battle) {
		if (war == null || !war.isActive() || battle == null || battle.getWarId() == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}

	private static void broadcast(War war, BattleCasualtyTally tally) {
		int attacker = 0;
		int defender = 0;
		for (BattleCasualtyTally.Loss loss : tally.losses()) {
			if (WarLedger.ATTACKER.equals(loss.side())) {
				attacker += loss.count();
			} else {
				defender += loss.count();
			}
		}
		if (attacker <= 0 && defender <= 0) {
			return;
		}
		String message = "§7Campaign battle wounds: attacker " + attacker + ", defender " + defender
				+ " regiments. Wounded regiments cannot fight and are lost when the war ends.";
		for (Side side : List.of(war.getAttackers(), war.getDefenders())) {
			for (String memberName : BattleSideMembers.collectEligibleMemberNames(side)) {
				Player player = Members.player(memberName);
				if (player != null && player.isOnline()) {
					player.sendMessage(message);
				}
			}
		}
	}
}
