package net.tfminecraft.simplefactions.war.core;

import java.util.Locale;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignDurationParser;
import net.tfminecraft.simplefactions.war.history.WarHistoryAdmin;

/**
 * Which {@code /war admin} commands have effects staff cannot take back, and what those
 * effects are. Null means the command runs without asking.
 */
public final class WarAdminRisks {
	private WarAdminRisks() {}

	/** {@code args} start at {@code admin} and carry no trailing {@code confirm}. */
	public static String describe(String[] args) {
		if (args == null || args.length < 3) {
			return null;
		}
		String sub = lower(args[1]);
		return switch (sub) {
			case "end" -> knownWar(args[2]) ? "ends the war at once, with no winner." : null;
			case "win" -> args.length >= 4 && knownWar(args[2])
					? "ends the war as a " + lower(args[3]) + " victory." : null;
			case "path" -> knownWar(args[2])
					? "rebuilds the campaign route and resets its progression and occupation." : null;
			case "devmode" -> devmode(lower(args[2]));
			case "time" -> time(args);
			case "schedule" -> args.length >= 4 && knownWar(args[2]) ? scheduleIfApplies(args) : null;
			case "buildup" -> args.length >= 4 && knownWar(args[2]) ? buildup(args) : null;
			case "reparations" -> args.length >= 4 && FactionManager.getByString(args[2]) != null
					&& FactionManager.getByString(args[3]) != null
					? "makes one faction pay part of its income to another for days." : null;
			case "truce" -> truce(lower(args[2]));
			case "history" -> lower(args[2]).equals("upload-battles") ? WarHistoryAdmin.UPLOAD_RISK : null;
			case "forceinstallation" -> WarForceInstallation.risk(args);
			default -> null;
		};
	}

	/** Unknown or malformed war ids skip the prompt, so the command itself says what is wrong. */
	private static boolean knownWar(String id) {
		return WarCommandHelper.parseWarId(id).map(WarManager::getById).isPresent();
	}

	/** The battle tools only ask when the war's battle is running and, to restart or stop it, not a raid. */
	private static String scheduleIfApplies(String[] args) {
		String action = lower(args[3]);
		War war = WarCommandHelper.parseWarId(args[2]).map(WarManager::getById).orElseThrow();
		if (war.getBattleSchedulePhase() == BattleSchedulePhase.BUILDUP && WarCommandManager.BUILDUP_REFUSED.contains(action)) {
			return null;
		}
		if (action.equals("setscheduled")) {
			return args.length >= 5 && pastInstant(args[4])
					? "that time has already passed, so the battle starts within a minute and teleports its soldiers."
					: null;
		}
		if (action.equals("battlestart") || action.equals("battlehold")) {
			var battle = net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByWarId(
					WarCommandHelper.parseWarId(args[2]).orElseThrow());
			if (action.equals("battlehold")) {
				return args.length >= 5 && lower(args[4]).equals("off") && battle != null && battle.isStaffHold()
						&& net.tfminecraft.simplefactions.war.battle.engine.core.BattleStaffService.startsOnRelease(battle)
								? "its start time has passed, so releasing the hold starts the battle within a minute"
										+ " and teleports its soldiers."
								: null;
			}
			return battle != null && battle.hasStarted() ? null : schedule(action);
		}
		if (action.equals("battlerestart") || action.equals("battleabort") || action.equals("battleend")) {
			var battle = net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByWarId(
					WarCommandHelper.parseWarId(args[2]).orElseThrow());
			boolean raid = battle != null
					&& battle.getBattleType() == net.tfminecraft.simplefactions.war.battle.enums.BattleType.RAID;
			if (battle == null || !battle.hasStarted() || (raid && !action.equals("battleend"))) {
				return null;
			}
			if (action.equals("battleend") && args.length >= 5 && !lower(args[4]).equals("nowinner")
					&& battle.getSideById(args[4]) == null) {
				return null;
			}
		}
		return schedule(action);
	}

	private static String truce(String action) {
		return switch (action) {
			case "clear" -> "ends the truce between the two factions, so they can declare war on each other again.";
			case "clearcooldown" -> "lets the faction start a civil war again straight away.";
			default -> null;
		};
	}

	private static String devmode(String action) {
		return switch (action) {
			case "on" -> "dev mode fills every campaign warband with dummy soldiers and lets anyone sign up at any hour."
					+ " Never use it on a live server.";
			case "off" -> "removes the dummy soldiers from every campaign warband.";
			default -> null;
		};
	}

	private static String time(String[] args) {
		String action = lower(args[2]);
		String forward = "moves the campaign clock forward for " + activeWars()
				+ ". Votes, battle starts, raids and reminders due in that time happen now,"
				+ " and moving the clock back later does not undo them.";
		return switch (action) {
			case "add" -> validDuration(args) ? forward : null;
			case "skip-to-battle-day" -> args.length >= 4 && knownWar(args[3]) ? forward : null;
			case "reset" -> CampaignClock.getOffset().isNegative() ? forward : null;
			default -> null;
		};
	}

	private static boolean pastInstant(String value) {
		try {
			return !java.time.Instant.parse(value).isAfter(CampaignClock.now());
		} catch (java.time.format.DateTimeParseException invalid) {
			return false;
		}
	}

	private static boolean validDuration(String[] args) {
		try {
			CampaignDurationParser.parse(java.util.Arrays.copyOfRange(args, 3, args.length));
			return true;
		} catch (IllegalArgumentException invalid) {
			return false;
		}
	}

	/** Asks only for commands that will run: a bad date or a war with nothing to change gets its usage line instead. */
	private static String buildup(String[] args) {
		War war = WarCommandHelper.parseWarId(args[2]).map(WarManager::getById).orElseThrow();
		String action = lower(args[3]);
		if (action.equals("setday") && (args.length < 5 || !isDate(args[4]))) {
			return null;
		}
		return switch (action) {
			case "end" -> "lifts the war's buildup freeze now; its battle days can then be from today on.";
			case "setday" -> "makes that day a battle day now, whatever players marked.";
			default -> null;
		};
	}

	private static String schedule(String action) {
		return switch (action) {
			case "skipday" ->
					"drops the next battle day, which clears every installation commitment and raid of this war.";
			case "lockday" -> "makes that day a battle day now, whatever players marked.";
			case "unlockday" -> "takes that battle day back; a battle set for it is called off.";
			case "battledelete" -> "deletes the prepared battle and its placement, then builds a fresh one.";
			case "battlestart" -> "starts the battle now and teleports its soldiers.";
			case "winbattle" -> "applies the battle result: occupation, casualties and the campaign step.";
			case "choice", "battlechoice", "defenderchoice", "pushchoice", "holdchoice" ->
					"locks in the post-battle choice for the winning side.";
			case "battlerestart" -> "puts every soldier back at their spawn and resets lives, casualties,"
					+ " capture points and the battle timer. Dropped items and broken vehicles stay.";
			case "battleabort" -> "stops the battle with no winner and no casualties, and holds it until staff"
					+ " start it again. Mercenary pay already charged for it stays charged.";
			case "battleend" -> "ends the running battle now. Casualties so far count; a named winner wins the battle,"
					+ " and with no winner voting reopens.";
			default -> null;
		};
	}

	private static String activeWars() {
		int count = WarManager.getActive().size();
		return count == 1 ? "the 1 active war" : "all " + count + " active wars";
	}

	private static String lower(String value) {
		return value.toLowerCase(Locale.ROOT);
	}

	private static boolean isDate(String value) {
		try {
			java.time.LocalDate.parse(value);
			return true;
		} catch (java.time.format.DateTimeParseException notADate) {
			return false;
		}
	}
}
