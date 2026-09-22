package me.Plugins.SimpleFactions.War.battle.engine.core;

import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;

public final class BattleJoinService {
	private BattleJoinService() {
	}

	/**
	 * @return null on success, or a player-facing error message
	 */
	public static String join(Player leader, Battle battle, String sideId) {
		if (leader == null) {
			return "Only players can join battles";
		}
		String redirect = campaignPlayerJoinRedirect(battle);
		if (redirect != null) {
			return redirect;
		}
		return join(WarbandManager.getByLeader(leader), battle, sideId);
	}

	static String campaignPlayerJoinRedirect(Battle battle) {
		if (battle != null && battle.getWarId() != null) {
			return "Sign up with /warband list - your faction warband is already on this battle";
		}
		return null;
	}

	/**
	 * @return null on success, or a player-facing error message
	 */
	public static String join(Warband warband, Battle battle, String sideId) {
		if (battle == null) {
			return "Battle not found";
		}
		if (sideId == null || sideId.isBlank()) {
			return "Side is required (attacker or defender)";
		}
		if (warband == null) {
			return "You need to lead a warband to join a battle";
		}
		if (battle.hasStarted()) {
			return "Battle has started";
		}
		if (battle.isLocked()) {
			return "Battle is locked";
		}
		if (!warband.isPendingLeader()
				&& BattleManager.getBattleByMemberId(warband.getLeaderId()) != null) {
			return "Already signed up for a battle";
		}
		BattleSide side = battle.getSideById(sideId);
		if (side == null) {
			return "No side with id " + sideId;
		}
		for (Warband band : side.getBands()) {
			if (band.getId().equalsIgnoreCase(warband.getId())) {
				return "Already signed up for this battle";
			}
		}
		if (battle.getWarId() != null) {
			War war = WarManager.getById(battle.getWarId());
			if (war == null || !war.isActive()) {
				return "War not found";
			}
			String campaignError = CampaignBattleJoinService.validateJoin(war, battle, warband, sideId);
			if (campaignError != null) {
				return campaignError;
			}
		}
		side.addBand(warband);
		BattlePersistenceService.persistBattle(battle);
		BattlePersistenceService.persistWarband(warband);
		return null;
	}
}
