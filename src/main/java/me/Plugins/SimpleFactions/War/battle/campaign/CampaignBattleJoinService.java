package me.Plugins.SimpleFactions.War.battle.campaign;


import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandBattleService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryEngagements;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

public final class CampaignBattleJoinService {
	private CampaignBattleJoinService() {}

	public record CampaignBattleContext(Battle battle, String sideId, War war) {}

	public static CampaignBattleContext findCampaignBattleForWarband(Warband warband) {
		if (warband == null) {
			return null;
		}
		for (Battle battle : BattleManager.get()) {
			if (battle == null || battle.getWarId() == null) {
				continue;
			}
			War war = WarManager.getById(battle.getWarId());
			if (war == null || !war.isActive()) {
				continue;
			}
			for (BattleSide side : battle.getSides()) {
				for (Warband band : side.getBands()) {
					if (band != null && band.getId().equalsIgnoreCase(warband.getId())) {
						return new CampaignBattleContext(battle, side.getId(), war);
					}
				}
			}
		}
		return null;
	}

	public static Side resolveWarSide(War war, String battleSideId) {
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

	public static String validateJoin(War war, Battle battle, Warband warband, String sideId) {
		if (war == null || battle == null || warband == null) {
			return "Invalid campaign battle join";
		}
		if (!warbandSideMatches(warband, sideId)) {
			return "Warband is not on this battle side";
		}
		return validateRosterHasRoom(war, battle, sideId, warband, warband.getMemberCount());
	}

	public static String validateWarbandMemberJoin(
			War war,
			Battle battle,
			String sideId,
			Warband warband,
			Player joiningPlayer) {
		if (joiningPlayer == null) {
			return "Invalid campaign warband join";
		}
		return validateWarbandMemberJoin(war, battle, sideId, warband, joiningPlayer.getName(), joiningPlayer.getUniqueId());
	}

	public static String validateWarbandMemberJoin(
			War war,
			Battle battle,
			String sideId,
			Warband warband,
			String joiningPlayerName) {
		return validateWarbandMemberJoin(war, battle, sideId, warband, joiningPlayerName, null);
	}

	public static String validateWarbandMemberJoin(
			War war,
			Battle battle,
			String sideId,
			Warband warband,
			String joiningPlayerName,
			java.util.UUID playerId) {
		if (war == null || battle == null || warband == null || joiningPlayerName == null) {
			return "Invalid campaign warband join";
		}
		Faction faction = FactionManager.getByMember(joiningPlayerName);
		if (faction == null) {
			return "You must be in a faction to join this campaign battle";
		}
		Side battleSide = resolveWarSide(war, sideId);
		if (battleSide == null) {
			return "Your faction is not on this battle side";
		}
		String mercenaryError = validateMercenaryRoster(
				war, battle, sideId, battleSide, joiningPlayerName, playerId);
		if (mercenaryError != null) {
			return mercenaryError;
		}
		Side playerSide = rosterSideFor(war, joiningPlayerName, faction);
		if (playerSide == null || playerSide != battleSide) {
			return MercenaryEngagements.forPlayer(war, joiningPlayerName) != null
					? "You are under contract to the other host"
					: "Your faction is not on this battle side";
		}
		if (!warbandSideMatches(warband, sideId)) {
			return "Warband is not on this battle side";
		}
		if (battle.hasStarted()) {
			return CampaignWarbandBattleService.validateMidBattleJoin(
					war, battle, sideId, warband, joiningPlayerName, playerId);
		}
		return validateRosterHasRoom(war, battle, sideId, warband, 1);
	}

	public static Side rosterSideFor(War war, String playerName, Faction faction) {
		if (war == null) {
			return null;
		}
		Side contracted = MercenaryEngagements.sideFor(war, playerName);
		if (contracted != null) {
			return contracted;
		}
		return faction == null ? null : war.getSide(faction);
	}

	public static String validateMercenaryRoster(
			War war,
			Battle battle,
			String sideId,
			Side battleSide,
			String joiningPlayerName,
			java.util.UUID playerId) {
		MercenaryEngagements.Engagement engagement =
				MercenaryEngagements.forPlayer(war, joiningPlayerName);
		if (engagement == null) {
			return null;
		}
		Side contracted = engagement.hirer() == null ? null : war.getSide(engagement.hirer());
		if (contracted == null || contracted != battleSide) {
			return "You are under contract to the other host";
		}
		Side opposing = war.getOppositeSide(engagement.hirer());
		if (!me.Plugins.SimpleFactions.mercenary.contract.MercenaryLoyalty.canDeployAgainst(
				joiningPlayerName, opposing)) {
			return "You cannot march on your own realm";
		}
		BattleSide roster = battle.getSideById(sideId);
		boolean alreadyOn = playerId != null && roster != null && isOnRoster(roster, playerId);
		int covering = MercenaryEngagements.coveringMembers(engagement, roster);
		if (!alreadyOn && covering >= engagement.promisedSlots()) {
			return "Every hired slot is already covered";
		}
		return null;
	}

	private static boolean isOnRoster(BattleSide side, java.util.UUID playerId) {
		for (Warband band : side.getBands()) {
			if (band != null && band.hasMember(playerId)) {
				return true;
			}
		}
		return false;
	}

	public static boolean warbandSideMatches(Warband warband, String sideId) {
		if (warband == null || sideId == null) {
			return false;
		}
		if (warband.isFaction() && warband.getCampaignSideId() != null) {
			return sideId.equalsIgnoreCase(warband.getCampaignSideId());
		}
		return true;
	}

	public static String validateRosterHasRoom(
			War war,
			Battle battle,
			String sideId,
			Warband warband,
			int additionalMembers) {
		if (additionalMembers <= 0) {
			return null;
		}
		if (battle.hasStarted()) {
			return null;
		}
		int cap = previewSidePoolLives(war, battle, sideId);
		int rosterAfter = countSideRoster(battle, sideId) + additionalMembers;
		if (rosterAfter > cap) {
			return "Cannot join: side roster is full (max " + cap + " players for this battle)";
		}
		return null;
	}

	public static int countSideRoster(Battle battle, String sideId) {
		BattleSide side = battle != null ? battle.getSideById(sideId) : null;
		if (side == null) {
			return 0;
		}
		int total = 0;
		for (Warband band : side.getBands()) {
			if (band != null) {
				total += band.getMemberCount();
			}
		}
		return total;
	}

	/** Max side roster size before battle start (pool lives from committed regiments). */
	public static int previewSidePoolLives(War war, Battle battle, String sideId) {
		return BattleLivesService.previewCampaignSideLives(war, battle, sideId).poolLives();
	}

	/** @deprecated use {@link #previewSidePoolLives(War, Battle, String)} */
	public static int previewSideLivesCap(War war, Battle battle, String sideId) {
		return previewSidePoolLives(war, battle, sideId);
	}
}
