package net.tfminecraft.simplefactions.war.battle.engine.win;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;

/**
 * Field battles with capture points can be won by control. The side holding more points
 * than any other gains control at a rate set by its share of the points, much faster when
 * it holds them all; the first side to the target wins.
 */
public final class FieldControlService {
	/** Battles tick every 4 server ticks. */
	static final int TICKS_PER_SECOND = 5;
	/** A point counts as held from this capture progress up, the same bar as spawning on it. */
	public static final int HOLD_PROGRESS = 50;
	static final double[] MILESTONES = { 0.5, 0.75, 0.9 };

	/** The side holding the most points, how many it holds, and how many points there are. */
	public record Lead(BattleSide side, int held, int total) {
	}

	private FieldControlService() {
	}

	public static boolean isActive(Battle battle) {
		return battle != null
				&& Cache.battleControlEnabled
				&& battle.hasStarted()
				&& battle.getBattleType() == BattleType.FIELD
				&& battle.isCapturePointsEnabled()
				&& !battle.getPointManager().getPoints().isEmpty();
	}

	public static boolean isHeldBy(CapturePoint point, BattleSide side) {
		return point != null
				&& side != null
				&& point.getController() != null
				&& point.getController().getId() != null
				&& point.getController().getId().equalsIgnoreCase(side.getId())
				&& point.getCaptureProgress() >= HOLD_PROGRESS;
	}

	public static int heldBy(List<CapturePoint> points, BattleSide side) {
		int held = 0;
		for (CapturePoint point : points) {
			if (isHeldBy(point, side)) {
				held++;
			}
		}
		return held;
	}

	/** The side holding more points than every other side, or null when nobody does. */
	public static Lead lead(Battle battle) {
		List<CapturePoint> points = battle.getPointManager().getPoints();
		BattleSide best = null;
		int bestHeld = 0;
		boolean shared = false;
		for (BattleSide side : battle.getSides()) {
			if (side == null) {
				continue;
			}
			int held = heldBy(points, side);
			if (held > bestHeld) {
				best = side;
				bestHeld = held;
				shared = false;
			} else if (held > 0 && held == bestHeld) {
				shared = true;
			}
		}
		if (best == null || shared) {
			return null;
		}
		return new Lead(best, bestHeld, points.size());
	}

	public static double ratePerSecond(int held, int total) {
		if (held <= 0 || total <= 0) {
			return 0.0;
		}
		double rate = Cache.battleControlPerSecond * held / total;
		return held >= total ? rate * Cache.battleControlAllPointsMultiplier : rate;
	}

	public static int target() {
		return Math.max(1, Cache.battleControlTarget);
	}

	public static void tick(Battle battle) {
		if (!isActive(battle)) {
			return;
		}
		Lead lead = lead(battle);
		if (lead == null) {
			return;
		}
		String sideId = lead.side().getId();
		int target = target();
		double before = battle.getControl(sideId);
		double after = Math.min(target, before + ratePerSecond(lead.held(), lead.total()) / TICKS_PER_SECOND);
		battle.setControl(sideId, after);
		if (after >= target) {
			BattleEndSupport.endBattle(battle, sideId, BattleEndReason.CONTROL);
			return;
		}
		for (double milestone : MILESTONES) {
			double mark = milestone * target;
			if (before < mark && after >= mark) {
				announce(battle, lead.side(), milestone);
			}
		}
	}

	/** The side with the most control, or null when nobody has any or the most is shared. */
	public static String controlLeader(Battle battle) {
		String leader = null;
		double best = 0.0;
		boolean shared = false;
		for (BattleSide side : battle.getSides()) {
			if (side == null) {
				continue;
			}
			double score = battle.getControl(side.getId());
			if (score > best) {
				leader = side.getId();
				best = score;
				shared = false;
			} else if (score > 0.0 && score == best) {
				shared = true;
			}
		}
		return shared ? null : leader;
	}

	/** Sidebar lines for one player, top line first; empty when control is not in play. */
	public static List<String> scoreboardLines(Battle battle, BattleSide viewer) {
		List<String> lines = new ArrayList<>();
		if (!isActive(battle)) {
			return lines;
		}
		Lead lead = lead(battle);
		int enemies = 0;
		for (BattleSide side : battle.getSides()) {
			if (side != null && !isSameSide(side, viewer)) {
				enemies++;
			}
		}
		int target = target();
		for (BattleSide side : battle.getSides()) {
			if (side == null) {
				continue;
			}
			String label;
			if (viewer == null) {
				label = "§e" + side.getId() + " control";
			} else if (isSameSide(side, viewer)) {
				label = "§aFriendly control";
			} else {
				label = enemies > 1 ? "§cEnemy control (" + side.getId() + ")" : "§cEnemy control";
			}
			String gaining = lead != null && isSameSide(side, lead.side()) ? " §e▲" : "";
			lines.add(label + "§f: " + (int) Math.floor(battle.getControl(side.getId())) + "/" + target + gaining);
		}
		return lines;
	}

	static String milestoneMessage(boolean friendly, double milestone) {
		int percent = (int) Math.round(milestone * 100);
		return friendly
				? "§aYour side has §e" + percent + "%§a of the control needed to win the battle."
				: "§cThe enemy has §e" + percent + "%§c of the control needed to win the battle. Retake the points!";
	}

	// Keep the legacy text representation used by the other battle messages.
	@SuppressWarnings("deprecation")
	private static void announce(Battle battle, BattleSide side, double milestone) {
		for (Player player : battle.getAllParticipants()) {
			player.sendMessage(milestoneMessage(side.hasPlayer(player), milestone));
			player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
		}
	}

	private static boolean isSameSide(BattleSide side, BattleSide other) {
		return side != null && other != null && side.getId() != null && side.getId().equalsIgnoreCase(other.getId());
	}
}
