package net.tfminecraft.simplefactions.war.battle.campaign.warband;


import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import java.time.Duration;
import java.time.Instant;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;

public final class BattleWarbandRetreatService {
	public enum RetreatResult {
		SUCCESS,
		REJECTED_NOT_IN_WARBAND,
		REJECTED_NOT_LEADER,
		REJECTED_PENDING_LEADER,
		REJECTED_NOT_IN_BATTLE,
		REJECTED_BATTLE_NOT_STARTED,
		REJECTED_NOT_CAMPAIGN_BATTLE,
		REJECTED_RAID,
		REJECTED_WRONG_BATTLE_TYPE,
		REJECTED_WAR_INACTIVE,
		REJECTED_TOO_EARLY,
		REJECTED_NO_OPPONENT
	}

	private BattleWarbandRetreatService() {
	}

	public static boolean canRetreat(Player player, Instant now) {
		return retreatRejection(player, now) == null;
	}

	public static RetreatResult retreatRejection(Player player, Instant now) {
		if (player == null) {
			return RetreatResult.REJECTED_NOT_IN_WARBAND;
		}
		Warband warband = WarbandManager.getByLeader(player);
		if (warband == null) {
			if (WarbandManager.getByMemberId(player.getUniqueId()) != null) {
				return RetreatResult.REJECTED_NOT_LEADER;
			}
			return RetreatResult.REJECTED_NOT_IN_WARBAND;
		}
		if (!player.getUniqueId().equals(warband.getLeaderId())) {
			return RetreatResult.REJECTED_NOT_LEADER;
		}
		if (warband.isPendingLeader()) {
			return RetreatResult.REJECTED_PENDING_LEADER;
		}

		CampaignBattleJoinService.CampaignBattleContext ctx =
				CampaignBattleJoinService.findCampaignBattleForWarband(warband);
		if (ctx == null) {
			return RetreatResult.REJECTED_NOT_IN_BATTLE;
		}

		War war = ctx.war();
		Battle battle = ctx.battle();
		if (war == null || !war.isActive()) {
			return RetreatResult.REJECTED_WAR_INACTIVE;
		}
		if (!battle.hasStarted()) {
			return RetreatResult.REJECTED_BATTLE_NOT_STARTED;
		}
		if (battle.getWarId() == null) {
			return RetreatResult.REJECTED_NOT_CAMPAIGN_BATTLE;
		}
		if (battle.isCampaignRaid() || battle.getBattleType() == BattleType.RAID) {
			return RetreatResult.REJECTED_RAID;
		}
		BattleType battleType = battle.getBattleType();
		if (battleType != BattleType.FIELD && battleType != BattleType.SIEGE) {
			return RetreatResult.REJECTED_WRONG_BATTLE_TYPE;
		}
		if (battle.getStartedAt() == null) {
			return RetreatResult.REJECTED_TOO_EARLY;
		}
		long elapsedSeconds = Duration.between(battle.getStartedAt(), now).getSeconds();
		if (elapsedSeconds < Cache.battleRetreatMinElapsedSeconds) {
			return RetreatResult.REJECTED_TOO_EARLY;
		}

		String opponentSideId = opponentSideId(battle, ctx.sideId());
		if (opponentSideId == null) {
			return RetreatResult.REJECTED_NO_OPPONENT;
		}
		return null;
	}

	public static RetreatResult retreat(Player player, Instant now) {
		RetreatResult rejection = retreatRejection(player, now);
		if (rejection != null) {
			return rejection;
		}

		Warband warband = WarbandManager.getByLeader(player);
		CampaignBattleJoinService.CampaignBattleContext ctx =
				CampaignBattleJoinService.findCampaignBattleForWarband(warband);
		Battle battle = ctx.battle();
		String opponentSideId = opponentSideId(battle, ctx.sideId());
		BattleEndSupport.endBattle(battle, opponentSideId, BattleEndReason.RETREAT);
		return RetreatResult.SUCCESS;
	}

	public static String opponentSideId(Battle battle, String retreatingSideId) {
		if (battle == null || retreatingSideId == null) {
			return null;
		}
		String opponentId = opposingBattleSideId(battle, retreatingSideId);
		if (opponentId == null) {
			return null;
		}
		BattleSide opponent = battle.getSideById(opponentId);
		return opponent != null ? opponentId : null;
	}

	private static String opposingBattleSideId(Battle battle, String retreatingSideId) {
		War war = battle.getWarId() != null ? WarManager.getById(battle.getWarId()) : null;
		Side retreating = CampaignBattleSides.warSideFor(war, battle, retreatingSideId);
		if (war != null && retreating != null) {
			Side opponent = retreating == war.getAttackers() ? war.getDefenders() : war.getAttackers();
			return CampaignBattleSides.battleSideFor(war, battle, opponent);
		}
		if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(retreatingSideId)) {
			return BattleTemplate.DEFENDER_SIDE;
		}
		if (BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(retreatingSideId)) {
			return BattleTemplate.ATTACKER_SIDE;
		}
		return null;
	}

	public static long remainingSecondsUntilRetreat(Battle battle, Instant now) {
		if (battle == null || battle.getStartedAt() == null || now == null) {
			return Cache.battleRetreatMinElapsedSeconds;
		}
		long elapsedSeconds = Duration.between(battle.getStartedAt(), now).getSeconds();
		return Math.max(0L, Cache.battleRetreatMinElapsedSeconds - elapsedSeconds);
	}

	public static final class Messages {
		public static final String NOT_IN_WARBAND = "§cYou need to lead a warband to retreat.";
		public static final String NOT_LEADER = "§cOnly the warband leader can retreat.";
		public static final String PENDING_LEADER = "§cYour warband has no leader yet.";
		public static final String NOT_IN_BATTLE = "§cYou are not in an active campaign battle.";
		public static final String BATTLE_NOT_STARTED = "§cThe battle has not started yet.";
		public static final String NOT_CAMPAIGN = "§cYou can only retreat from campaign battles.";
		public static final String RAID = "§cYou cannot retreat from a raid.";
		public static final String WRONG_BATTLE_TYPE = "§cYou cannot retreat from this battle type.";
		public static final String WAR_INACTIVE = "§cWar not found.";
		public static final String NO_OPPONENT = "§cCould not resolve the opposing battle side.";
		public static final String SUCCESS = "§aYour warband has retreated. The battle is lost.";

		private Messages() {
		}

		public static String messageForResult(RetreatResult result) {
			return messageForResult(result, null, null);
		}

		public static String messageForResult(RetreatResult result, Player player, Instant now) {
			if (result == null || result == RetreatResult.SUCCESS) {
				return result == RetreatResult.SUCCESS ? SUCCESS : null;
			}
			return switch (result) {
				case REJECTED_NOT_IN_WARBAND -> NOT_IN_WARBAND;
				case REJECTED_NOT_LEADER -> NOT_LEADER;
				case REJECTED_PENDING_LEADER -> PENDING_LEADER;
				case REJECTED_NOT_IN_BATTLE -> NOT_IN_BATTLE;
				case REJECTED_BATTLE_NOT_STARTED -> BATTLE_NOT_STARTED;
				case REJECTED_NOT_CAMPAIGN_BATTLE -> NOT_CAMPAIGN;
				case REJECTED_RAID -> RAID;
				case REJECTED_WRONG_BATTLE_TYPE -> WRONG_BATTLE_TYPE;
				case REJECTED_WAR_INACTIVE -> WAR_INACTIVE;
				case REJECTED_NO_OPPONENT -> NO_OPPONENT;
				case REJECTED_TOO_EARLY -> buildTooEarlyMessage(player, now);
				default -> null;
			};
		}

		private static String buildTooEarlyMessage(Player player, Instant now) {
			if (player == null || now == null) {
				return "§cYou cannot retreat yet.";
			}
			Warband warband = WarbandManager.getByLeader(player);
			if (warband == null) {
				return "§cYou cannot retreat yet.";
			}
			CampaignBattleJoinService.CampaignBattleContext ctx =
					CampaignBattleJoinService.findCampaignBattleForWarband(warband);
			if (ctx == null) {
				return "§cYou cannot retreat yet.";
			}
			Battle battle = ctx.battle();
			long remainingSeconds = BattleWarbandRetreatService.remainingSecondsUntilRetreat(battle, now);
			long remainingMinutes = (remainingSeconds + 59L) / 60L;
			if (remainingMinutes <= 1L) {
				return "§cYou cannot retreat for another minute.";
			}
			return "§cYou cannot retreat for another " + remainingMinutes + " minutes.";
		}
	}

	public static final class ConfirmHandler {
		private ConfirmHandler() {
		}

		public static void handleConfirm(Player player, boolean confirmed) {
			if (player == null) {
				return;
			}
			if (FactionManager.inv != null) {
				FactionManager.inv.confirming.remove(player);
			}
			if (!confirmed) {
				player.closeInventory();
				return;
			}
			Instant now = Instant.now();
			RetreatResult result = BattleWarbandRetreatService.retreat(player, now);
			String message = Messages.messageForResult(result, player, now);
			if (message != null) {
				player.sendMessage(message);
			}
			if (result == RetreatResult.SUCCESS) {
				player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			}
		}
	}
}
