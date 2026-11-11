package net.tfminecraft.simplefactions.war.campaign.availability;

import java.time.LocalDate;

/**
 * A date the day lock made a battle day. {@code hour} is the battle-day start hour once the time
 * lock fixed it, null before. {@code forced} marks a fallback day nobody had enough marks for;
 * {@code autoresolve} a forced day one side never marked, which is autoresolved instead of fought.
 */
public record LockedBattleDay(LocalDate date, Integer hour, boolean forced, boolean autoresolve) {

	public static LockedBattleDay open(LocalDate date, boolean forced) {
		return new LockedBattleDay(date, null, forced, false);
	}

	public LockedBattleDay withHour(int startHour, boolean resolveInstead) {
		return new LockedBattleDay(date, startHour, forced, resolveInstead);
	}
}
