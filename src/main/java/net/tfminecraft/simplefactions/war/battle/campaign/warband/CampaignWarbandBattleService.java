package net.tfminecraft.simplefactions.war.battle.campaign.warband;


import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService;
import net.tfminecraft.simplefactions.war.battle.engine.win.SiegeWinService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.ui.BattleInventoryManager;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;

public final class CampaignWarbandBattleService {
	private CampaignWarbandBattleService() {
	}

	public static String validateMidBattleJoin(
			net.tfminecraft.simplefactions.war.core.War war,
			Battle battle,
			String sideId,
			Warband warband,
			String joiningPlayerName,
			UUID playerId) {
		if (war == null || battle == null || warband == null || joiningPlayerName == null) {
			return "Invalid campaign warband join";
		}
		if (!battle.hasStarted()) {
			return null;
		}
		net.tfminecraft.simplefactions.objects.Faction faction =
				net.tfminecraft.simplefactions.managers.FactionManager.getByMember(joiningPlayerName);
		if (faction == null) {
			return "You must be in a faction to join this campaign battle";
		}
		net.tfminecraft.simplefactions.war.core.Side battleSide =
				CampaignBattleJoinService.resolveWarSide(war, battle, sideId);
		if (battleSide == null) {
			return "Your faction is not on this battle side";
		}
		String mercenaryError = CampaignBattleJoinService.validateMercenaryRoster(
				war, battle, sideId, battleSide, joiningPlayerName, playerId);
		if (mercenaryError != null) {
			return mercenaryError;
		}
		net.tfminecraft.simplefactions.war.core.Side playerSide =
				CampaignBattleJoinService.rosterSideFor(war, joiningPlayerName, faction);
		if (playerSide == null || playerSide != battleSide) {
			return MercenaryEngagements.forPlayer(war, joiningPlayerName) != null
					? "You are under contract to the other host"
					: "Your faction is not on this battle side";
		}
		if (playerId != null
				&& CampaignWarbandLeaveBlock.isBlocked(battle.getId(), warband.getId(), playerId)) {
			return "You cannot rejoin this warband for this battle";
		}
		BattleSide battleSideEntity = battle.getSideById(sideId);
		if (battleSideEntity == null || battleSideEntity.getLives() <= 0) {
			return "Cannot join: this side has no lives remaining in the battle";
		}
		return null;
	}

	public static void onMemberJoined(Player player, Warband warband, CampaignBattleJoinService.CampaignBattleContext ctx) {
		if (player == null || warband == null || ctx == null) {
			return;
		}
		Battle battle = ctx.battle();
		if (!battle.hasStarted()) {
			return;
		}
		BattleSide side = battle.getSideById(ctx.sideId());
		if (side == null) {
			return;
		}
		if (battle.isCampaignRaid()) {
			BattleManager.currentBattle.put(player, battle);
			if (battle.hasTeleport() && side.getSpawn() != null) {
				player.teleport(side.getSpawn());
			}
			return;
		}
		side.tickLife();
		side.updateBossBar(battle.getAllParticipants());
		BattleManager.currentBattle.put(player, battle);
		side.addBossBarPlayer(player);
		if (battle.hasTeleport() && side.getSpawn() != null) {
			player.teleport(side.getSpawn());
		}
		if (battle.getBattleType() == BattleType.FIELD && battle.getPointManager().getPoints().size() > 0) {
			new org.bukkit.scheduler.BukkitRunnable() {
				@Override
				public void run() {
					BattleInventoryManager inv = new BattleInventoryManager();
					inv.spawnList(player, battle);
				}
			}.runTaskLater(net.tfminecraft.simplefactions.SimpleFactions.plugin, 2L);
		}
	}

	public static void processLeave(Player player, Warband warband, boolean voluntaryLeave) {
		if (player == null || warband == null) {
			return;
		}
		UUID playerId = player.getUniqueId();
		boolean wasLeader = playerId.equals(warband.getLeaderId());
		CampaignBattleJoinService.CampaignBattleContext ctx =
				CampaignBattleJoinService.findCampaignBattleForWarband(warband);

		if (ctx != null && ctx.battle().hasStarted() && voluntaryLeave) {
			CampaignWarbandLeaveBlock.block(ctx.battle().getId(), warband.getId(), playerId);
		}
		detachFromBattle(player, warband);
		warband.removeMember(playerId);

		if (wasLeader) {
			UUID nextLeader = warband.getOldestRealMemberId(null);
			if (nextLeader != null) {
				warband.setLeaderId(nextLeader);
			} else {
				warband.resetToPendingLeader();
			}
		}

		if (ctx != null && ctx.battle().hasStarted() && warband.getRealMemberCount() == 0) {
			checkSideAutoLose(ctx.battle(), ctx.sideId());
		}
		BattlePersistenceService.persistWarband(warband);
		if (ctx != null) {
			BattlePersistenceService.persistBattle(ctx.battle());
		}
	}

	private static void checkSideAutoLose(Battle battle, String sideId) {
		if (battle == null || !battle.hasStarted() || sideId == null) {
			return;
		}
		BattleSide side = battle.getSideById(sideId);
		if (side == null) {
			return;
		}
		side.setLives(0);
		if (battle.getBattleType() == BattleType.SIEGE) {
			SiegeWinService.checkSiegeWin(battle);
		} else if (battle.getBattleType() == BattleType.FIELD) {
			FieldWinService.checkFieldWin(battle);
		}
	}

	private static void detachFromBattle(Player player, Warband warband) {
		BattleManager.currentBattle.remove(player);
		BattleSide side = findSideForWarband(warband);
		if (side != null) {
			side.removeBossBarPlayer(player);
		}
	}

	private static BattleSide findSideForWarband(Warband warband) {
		for (Battle battle : BattleManager.get()) {
			for (BattleSide side : battle.getSides()) {
				if (side.getBands().contains(warband)) {
					return side;
				}
			}
		}
		return null;
	}

	public static boolean isWarSideMainLeader(
			net.tfminecraft.simplefactions.war.core.War war,
			Warband warband,
			String playerName) {
		if (war == null || warband == null || playerName == null || playerName.isBlank()) {
			return false;
		}
		String sideId = warband.getCampaignSideId();
		if (sideId == null) {
			return false;
		}
		net.tfminecraft.simplefactions.war.core.Side side = CampaignBattleJoinService.resolveWarSide(war, sideId);
		if (side == null) {
			return false;
		}
		for (net.tfminecraft.simplefactions.war.core.Participant par : side.getMainParticipants()) {
			if (par.getLeader() != null
					&& playerName.equalsIgnoreCase(par.getLeader().getLeader())) {
				return true;
			}
		}
		return false;
	}
}
