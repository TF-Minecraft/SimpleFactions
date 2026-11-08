package net.tfminecraft.simplefactions.war.core;

import java.util.Locale;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;

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
			case "schedule" -> args.length >= 4 && knownWar(args[2]) ? schedule(lower(args[3])) : null;
			case "reparations" -> args.length >= 4 && FactionManager.getByString(args[2]) != null
					&& FactionManager.getByString(args[3]) != null
					? "makes one faction pay part of its income to another for days." : null;
			case "truce" -> truce(lower(args[2]));
			default -> null;
		};
	}

	/** Unknown or malformed war ids skip the prompt, so the command itself says what is wrong. */
	private static boolean knownWar(String id) {
		return WarCommandHelper.parseWarId(id).map(WarManager::getById).isPresent();
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
			case "add", "skip-to-battle-day" -> forward;
			case "reset" -> CampaignClock.getOffset().isNegative() ? forward : null;
			default -> null;
		};
	}

	private static String schedule(String action) {
		return switch (action) {
			case "setday", "skipday" ->
					"moves the battle day, which clears every installation commitment and raid of this war.";
			case "closevote" -> "closes the battle vote now and fixes its result.";
			case "forcequorum" -> "makes the next vote close count even without enough voters.";
			case "battledelete" -> "deletes the prepared battle and its placement, then builds a fresh one.";
			case "battlestart" -> "starts the battle now and teleports its soldiers.";
			case "winbattle" -> "applies the battle result: occupation, casualties and the campaign step.";
			case "choice", "battlechoice", "defenderchoice", "pushchoice", "holdchoice" ->
					"locks in the post-battle choice for the winning side.";
			case "battlerestart" -> "puts every soldier back at their spawn and resets lives, casualties,"
					+ " capture points and the battle timer. Dropped items and broken vehicles stay.";
			case "battleabort" -> "stops the battle with no winner and no casualties, and holds it until staff"
					+ " start it again.";
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
}
