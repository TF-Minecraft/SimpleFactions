package net.tfminecraft.simplefactions.war.declare;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.war.resolution.PillageApplyService;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsService;

/**
 * What a war would win, shown while a leader browses the declare menus, so they can
 * judge a war before asking staff for a code. Money figures use today's trade income.
 */
public final class WarGoalPreview {
	private WarGoalPreview() {}

	/** Every decisive war ends in reparations from the loser, whatever the goal. */
	public static List<String> reparations(Faction attacker, Faction defender) {
		List<String> lines = new ArrayList<>();
		String percent = percent(Cache.warReparationsIncomePercent);
		int days = Cache.warReparationsDays;
		double won = dailyReparations(defender);
		lines.add("§aIf you win: §7" + defender.getName() + " §7and its subjects pay you");
		lines.add("§7" + percent + "% of their trade income for " + days + " days");
		lines.add("§7(about §e" + money(won) + "d §7a day, §e" + money(won * days) + "d §7in total)");
		double lost = dailyReparations(attacker);
		lines.add("§cIf you lose: §7you and your subjects pay them");
		lines.add("§7the same share (about §e" + money(lost) + "d §7a day)");
		return lines;
	}

	/** The reparations a defeated faction and its subjects would pay a day, at today's income. */
	public static double dailyReparations(Faction payer) {
		double total = 0;
		for (Faction faction : WarReparationsService.payerAndVassals(payer)) {
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				TradeBreakdown trade = guild.getTradeBreakdown();
				total += trade == null ? 0 : Math.max(0, trade.getIncome());
			}
		}
		return total * Cache.warReparationsIncomePercent / 100.0;
	}

	/** The defender's relation toward the attacker after a subjugation or tributary win. */
	public static List<String> subjectEffects(RelationType type, Faction attacker, Faction defender) {
		List<String> lines = new ArrayList<>();
		if (type == null) {
			return lines;
		}
		if (type.hasGiveModifiers()) {
			lines.add("§7You get:");
			for (FactionModifier mod : type.getGiveModifiers()) {
				lines.add("§7- " + new FactionModifier(defender, mod).getString(attacker));
			}
		}
		if (type.hasRecieveModifiers()) {
			lines.add("§7They get:");
			for (FactionModifier mod : type.getRecieveModifiers()) {
				lines.add("§7- " + new FactionModifier(attacker, mod).getString(defender));
			}
		}
		return lines;
	}

	public static List<String> usurp(Faction defender) {
		List<String> lines = new ArrayList<>();
		Title title = defender.getHighestTitle();
		if (title != null) {
			lines.add("§7Title: §f" + title.getName());
		}
		int subjects = RelationManager.getSubjects(defender).size();
		if (subjects > 0) {
			lines.add("§7Their §f" + subjects + " §7" + (subjects == 1 ? "subject becomes" : "subjects become") + " yours");
		}
		lines.add("§7They become your subject");
		return lines;
	}

	public static List<String> pillage(Settlement settlement) {
		List<String> lines = new ArrayList<>();
		List<Guild> guilds = PillageApplyService.guildsIn(settlement);
		lines.add("§7Loot: §e" + money(PillageApplyService.previewLoot(guilds)) + "d");
		lines.add("§7(" + Cache.pillageLootDays + " days of trade income from " + guilds.size()
				+ (guilds.size() == 1 ? " guild)" : " guilds)"));
		lines.add("§7Their guilds there lose " + percent(Math.abs(Cache.pillageTradeHitPercent)) + "%");
		lines.add("§7of their trade income, recovering over " + Cache.pillageTradeHitDays + " days");
		return lines;
	}

	/** The richest settlement the attacker may pillage, or null when none is eligible. */
	public static String bestPillage(List<PillageEligibility.PillageSettlementOption> options) {
		double best = -1;
		String name = null;
		for (PillageEligibility.PillageSettlementOption option : options) {
			if (!option.eligible()) {
				continue;
			}
			double loot = PillageApplyService.previewLoot(PillageApplyService.guildsIn(option.settlement()));
			if (loot > best) {
				best = loot;
				name = option.settlement().getName();
			}
		}
		return name == null ? null : "§7Best loot: §e" + money(best) + "d §7from " + name;
	}

	private static String money(double amount) {
		return Formatter.formatMoney(amount);
	}

	private static String percent(double percent) {
		return percent == Math.rint(percent) ? String.valueOf((long) percent) : String.valueOf(percent);
	}
}
