package net.tfminecraft.simplefactions.war.battle.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

/** The {@code /battle info} chat summary: state, timers, and each side's lives and soldiers. */
public final class BattleInfoFormatter {
	private BattleInfoFormatter() {}

	public static List<String> lines(Battle battle, Instant now) {
		List<String> lines = new ArrayList<>();
		String type = battle.getBattleType() == null ? "battle" : battle.getBattleType().name().toLowerCase(Locale.ROOT);
		lines.add("§6Battle §e" + battle.getDisplayName() + " §7(" + battle.getId() + ", " + type
				+ (battle.getWarId() == null ? "" : ", war " + battle.getWarId())
				+ (battle.getProvinceId() == null ? "" : ", province " + battle.getProvinceId()) + ")");
		lines.add("§7State: " + state(battle, now));
		String timeLeft = timeLeft(battle, now);
		if (timeLeft != null) {
			lines.add("§7Time cap in: §e" + timeLeft);
		}
		if (battle.hasStarted() && battle.getBattleType() == BattleType.SIEGE) {
			lines.add("§7Siege hold timer: §e" + battle.getContestHoldRemainingSeconds() + "s");
		}
		Map<String, Integer> casualties = BattleCasualtyLedger.getSideCasualties(battle);
		for (BattleSide side : battle.getSides()) {
			int online = 0;
			for (Warband band : side.getBands()) {
				online += band.getPlayers().size();
			}
			String max = side.getMaxLives() > 0 ? "§7/§e" + side.getMaxLives() : "";
			lines.add("§f" + side.getId() + "§7: §e" + side.getLives() + max + " §7lives, §e"
					+ online + "§7/§e" + side.getAllParticipants() + " §7online, §e"
					+ casualties.getOrDefault(side.getId().toLowerCase(Locale.ROOT), 0) + " §7casualties");
		}
		for (CapturePoint point : battle.getPoints()) {
			BattleSide controller = point.getController();
			lines.add("§7Point §f" + point.getId() + "§7: " + (controller == null ? "§8nobody"
					: "§e" + controller.getId() + " §7" + point.getCaptureProgress() + "%"));
		}
		return lines;
	}

	private static String state(Battle battle, Instant now) {
		if (battle.hasStarted()) {
			Instant started = battle.getStartedAt();
			return started == null ? "§arunning"
					: "§arunning for " + minutes(Duration.between(started, now));
		}
		return battle.isStaffHold() ? "§con hold by staff" : "§enot started";
	}

	private static String timeLeft(Battle battle, Instant now) {
		if (!battle.hasStarted() || battle.getWarId() == null || battle.getStartedAt() == null
				|| !Cache.battleTimeCapEnabled || Cache.battleTimeCapMinutes <= 0
				|| (battle.getBattleType() != BattleType.FIELD && battle.getBattleType() != BattleType.SIEGE)) {
			return null;
		}
		Instant deadline = battle.getStartedAt().plusSeconds(Cache.battleTimeCapMinutes * 60L);
		return minutes(Duration.between(now, deadline));
	}

	private static String minutes(Duration duration) {
		long seconds = Math.max(0, duration.getSeconds());
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}
}
