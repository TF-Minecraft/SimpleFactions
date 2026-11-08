package net.tfminecraft.simplefactions.war.battle.engine.core;

import java.time.Instant;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandLeaveBlock;
import net.tfminecraft.simplefactions.war.battle.engine.capture.BattleCapturePoints;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService;
import net.tfminecraft.simplefactions.war.battle.engine.win.SiegeContestService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.ui.BattleStaffNotices;
import net.tfminecraft.simplefactions.war.battle.ui.BattleWatchService;

/**
 * Staff controls for a running battle: restart it from its start, stop it without a result,
 * end it now, hold it, and correct a side's lives. None of them fire the battle start or end
 * events again, so mercenary pay, stats and loot are not counted twice.
 */
public final class BattleStaffService {
	public record Result(boolean ok, String message) {
		static Result ok(String message) {
			return new Result(true, message);
		}

		static Result error(String message) {
			return new Result(false, message);
		}
	}

	private BattleStaffService() {}

	/** Puts the running battle back to its start: lives, casualties, points, timers and spawns. */
	public static Result restart(Battle battle, String by) {
		Result refusal = requireRunningFieldOrSiege(battle, "restarted");
		if (refusal != null) {
			return refusal;
		}
		resetToStart(battle);
		battle.setStartedAt(Instant.now());
		if (battle.getBattleType() == BattleType.SIEGE) {
			battle.setContestHoldRemainingSeconds(BattleContestSetup.getEffectiveDurationSeconds(battle));
		}
		for (Player player : battle.getAllParticipants()) {
			BattleRespawnRouting.clear(player.getUniqueId());
			BattleSide side = battle.getSideByPlayer(player);
			if (side.getSpawn() != null) {
				player.teleport(side.getSpawn());
			}
			player.sendTitle("§eBATTLE RESTARTED", "§7Lives and points are back to the start", 5, 50, 5);
		}
		for (BattleSide side : battle.getSides()) {
			side.updateBossBar(battle.getAllParticipants());
		}
		BattlePersistenceService.persistBattle(battle);
		return done(battle, by, "RESTART", "restarted " + battle.getDisplayName()
				+ ". Lives: " + livesSummary(battle) + ".");
	}

	/** Stops the battle with no winner and no casualties, and holds it until staff start it again. */
	public static Result abort(Battle battle, String by) {
		Result refusal = requireRunningFieldOrSiege(battle, "stopped");
		if (refusal != null) {
			return refusal;
		}
		resetToStart(battle);
		RaidAttackerEliminationService.clearBattleState(battle);
		for (Player player : battle.getAllParticipants()) {
			BattleRespawnRouting.clear(player.getUniqueId());
			player.sendTitle("§eBATTLE STOPPED", "§7Staff stopped the battle. Nothing was lost.", 5, 50, 5);
		}
		battle.end();
		battle.setStaffHold(true);
		BattleWatchService.clearBattle(battle.getId());
		BattlePersistenceService.persistBattle(battle);
		return done(battle, by, "ABORT", "stopped " + battle.getDisplayName()
				+ " with no result. It stays on hold until staff start it again"
				+ (battle.getWarId() == null ? " (/battle edit)." : " (/war admin schedule " + battle.getWarId()
						+ " battlestart)."));
	}

	/** Ends the running battle now; {@code winnerSideId} null means no winner. */
	public static Result end(Battle battle, String winnerSideId, String by) {
		if (battle == null || !battle.hasStarted()) {
			return Result.error("That battle is not running.");
		}
		if (winnerSideId != null && battle.getSideById(winnerSideId) == null) {
			return Result.error("No side " + winnerSideId + " in this battle.");
		}
		String winner = winnerSideId == null ? null : battle.getSideById(winnerSideId).getId();
		BattleEndSupport.endBattle(battle, winner, winner == null ? BattleEndReason.TIMER : BattleEndReason.SIDE_WIN);
		if (battle.getWarId() == null) {
			BattlePersistenceService.persistBattle(battle);
		}
		return done(battle, by, "END", "ended " + battle.getDisplayName()
				+ (winner == null ? " with no winner." : " with " + winner + " as the winner."));
	}

	/** Holds a prepared battle so the scheduler does not start it, or releases the hold. */
	public static Result hold(Battle battle, boolean on, String by) {
		if (battle == null) {
			return Result.error("No such battle.");
		}
		if (battle.hasStarted()) {
			return Result.error("That battle is already running. Stop it with abort instead.");
		}
		battle.setStaffHold(on);
		BattlePersistenceService.persistBattle(battle);
		return done(battle, by, on ? "HOLD" : "RELEASE", on
				? "put " + battle.getDisplayName() + " on hold. It will not start on schedule."
				: "released the hold on " + battle.getDisplayName() + ". It starts on schedule again.");
	}

	/** Adds, removes or sets a side's lives during the battle. */
	public static Result adjustLives(Battle battle, String sideId, String operation, int amount, String by) {
		if (battle == null || !battle.hasStarted()) {
			return Result.error("Lives can only be corrected while the battle runs. Use setlives before it starts.");
		}
		BattleSide side = battle.getSideById(sideId);
		if (side == null) {
			return Result.error("No side " + sideId + " in this battle.");
		}
		if (amount < 0) {
			return Result.error("The amount must be 0 or more.");
		}
		int before = side.getLives();
		long after = switch (operation.toLowerCase(Locale.ROOT)) {
			case "add" -> (long) before + amount;
			case "remove" -> (long) before - amount;
			case "set" -> amount;
			default -> Long.MIN_VALUE;
		};
		if (after == Long.MIN_VALUE) {
			return Result.error("Use add, remove or set.");
		}
		int lives = (int) Math.max(0, Math.min(Integer.MAX_VALUE, after));
		side.restoreLives(lives, side.getMaxLives());
		side.updateBossBar(battle.getAllParticipants());
		BattlePersistenceService.persistBattle(battle);
		return done(battle, by, "LIVES", "changed " + side.getId() + " lives in " + battle.getDisplayName()
				+ " from " + before + " to " + lives + ".");
	}

	private static void resetToStart(Battle battle) {
		for (BattleSide side : battle.getSides()) {
			side.resetLivesToStart();
		}
		BattleCasualtyLedger.clear(battle);
		SiegeContestService.clearBattleState(battle);
		FieldWinService.clearEmptySideTracking(battle);
		battle.restorePointStarts();
		if (battle.getBattleType() == BattleType.FIELD && battle.isCapturePointsEnabled() && battle.isSequentialCapture()) {
			BattleCapturePoints.syncLinearChain(battle);
		}
		CampaignWarbandLeaveBlock.clearBattle(battle.getId());
	}

	private static Result requireRunningFieldOrSiege(Battle battle, String verb) {
		if (battle == null || !battle.hasStarted()) {
			return Result.error("That battle is not running.");
		}
		if (battle.getBattleType() == BattleType.RAID) {
			return Result.error("Raids cannot be " + verb + ". End the raid instead.");
		}
		return null;
	}

	private static String livesSummary(Battle battle) {
		StringBuilder summary = new StringBuilder();
		for (BattleSide side : battle.getSides()) {
			if (!summary.isEmpty()) {
				summary.append(", ");
			}
			summary.append(side.getId()).append(' ').append(side.getLives());
		}
		return summary.toString();
	}

	private static Result done(Battle battle, String by, String action, String text) {
		LogManager.war("STAFF_BATTLE_%s battle=%s war=%s by=%s: %s", action, battle.getId(), battle.getWarId(), by, text);
		BattleStaffNotices.toStaff("§6[Battle] §e" + by + " §6" + text, by);
		return Result.ok("§a" + Character.toUpperCase(text.charAt(0)) + text.substring(1));
	}
}
