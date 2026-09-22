package me.Plugins.SimpleFactions.Objects;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Utils.Formatter;

/**
 * Assembles the prestige breakdown from its computed components.
 *
 * The list is rebuilt from scratch on every call so repeated recomputes are idempotent.
 * The bonus multiplies only the lines below it; it must never see a previous bonus line
 * or it compounds toward pct/(1-pct) instead of pct.
 */
public final class PrestigeBreakdown {
	private PrestigeBreakdown() {}

	public static List<Modifier> build(
			List<Modifier> persistent,
			double members,
			double wealth,
			double trade,
			double provinces,
			double titles,
			double subjects,
			double bonusPercent) {
		List<Modifier> modifiers = new ArrayList<>();
		if(persistent != null) {
			for(Modifier m : persistent) {
				if(m != null && m.isPersistent()) modifiers.add(m);
			}
		}
		modifiers.add(new Modifier("Members", Formatter.formatDouble(members), false));
		modifiers.add(new Modifier("Wealth", Formatter.formatDouble(wealth), false));
		if(trade > 0) modifiers.add(new Modifier("Trade", Formatter.formatDouble(trade), false));
		if(provinces > 0) modifiers.add(new Modifier("Provinces", Formatter.formatDouble(provinces), false));
		if(titles > 0) modifiers.add(new Modifier("Titles", Formatter.formatDouble(titles), false));
		if(subjects > 0) modifiers.add(new Modifier("Subjects", Formatter.formatDouble(subjects), false));
		if(bonusPercent > 0) {
			double extra = total(modifiers)*(bonusPercent/100.0);
			modifiers.add(new Modifier(bonusPercent+"% Bonus", Formatter.formatDouble(extra), false));
		}
		return modifiers;
	}

	public static Double total(List<Modifier> modifiers) {
		double sum = 0.0;
		if(modifiers != null) {
			for(Modifier m : modifiers) {
				if(m != null && m.getAmount() != null) sum += m.getAmount();
			}
		}
		return Formatter.formatDouble(sum);
	}
}
