package net.tfminecraft.simplefactions.war.campaign.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattlePlacementValidator;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.tlibs.utils.TimeFormatter;

public final class CampaignScheduleCountdown {
	private CampaignScheduleCountdown() {}

	public static Optional<String> formatNextMilestone(War war, Instant now) {
		if (war == null || now == null) {
			return Optional.empty();
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			return Optional.empty();
		}

		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase == BattleSchedulePhase.SCHEDULED) {
			Instant scheduledAt = war.getScheduledBattleAt();
			if (scheduledAt != null) {
				if (!now.isBefore(scheduledAt) && (battle == null || !battle.hasStarted())) {
					if (battle != null) {
						String startError = BattlePlacementValidator.validateForStart(battle);
						if (startError != null) {
							return Optional.of("Cannot start: " + startError);
						}
					}
				}
				return formatCountdown("Starts in %s", scheduledAt, now);
			}
			return Optional.empty();
		}

		if (phase != BattleSchedulePhase.VOTING) {
			return Optional.empty();
		}

		LocalDate battleDay = war.getBattleDay();
		if (battleDay == null) {
			return Optional.empty();
		}

		if (!BattleScheduleService.isOnBattleDay(war, now)) {
			Instant target = battleDay.atStartOfDay(BattleWindowService.SCHEDULE_ZONE).toInstant();
			return formatCountdown("Battle day in %s", target, now);
		}

		if (BattleScheduleService.isBeforeVoteClose(war, now)) {
			Instant target = BattleWindowService.atScheduleHour(battleDay, Cache.warVoteCloseHour);
			if (target != null) {
				return formatCountdown("Vote closes in %s", target, now);
			}
		}

		return Optional.empty();
	}

	private static Optional<String> formatCountdown(String label, Instant target, Instant now) {
		if (target == null) {
			return Optional.empty();
		}
		long seconds = Math.max(0L, target.getEpochSecond() - now.getEpochSecond());
		if (seconds == 0L) {
			return Optional.of("Starting now");
		}
		return Optional.of(String.format(label, TimeFormatter.formatTime((int) seconds)));
	}
}
