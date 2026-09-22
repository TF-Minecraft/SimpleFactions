package net.tfminecraft.simplefactions.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.DividendBreakdown;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;

public final class PostSettlementPayouts {

	@FunctionalInterface
	public interface PlayerUuidLookup {
		UUID uuidOf(String playerName);
	}

	private PostSettlementPayouts() {}

	public static void apply(
			DailyGuildTransfers buffer,
			PlayerBank playerBank,
			PlayerEconomyManager economy,
			PlayerUuidLookup uuids) {
		if (buffer == null) {
			return;
		}
		Set<Guild> guilds = new HashSet<>();
		guilds.addAll(buffer.getPendingDividendPools().keySet());
		guilds.addAll(buffer.getPlayerPayouts().keySet());
		for (Guild guild : guilds) {
			settleGuild(guild, buffer, playerBank, economy, uuids);
		}
	}

	private static void settleGuild(
			Guild guild,
			DailyGuildTransfers buffer,
			PlayerBank playerBank,
			PlayerEconomyManager economy,
			PlayerUuidLookup uuids) {
		if (guild == null || guild.getBank() == null) {
			return;
		}
		if (guild.isBankrupt()) {
			return;
		}
		double pool = buffer.getPendingDividendPools().getOrDefault(guild, 0.0);
		Map<UUID, Double> payouts = buffer.getPlayerPayouts().getOrDefault(guild, Map.of());
		double wages = 0.0;
		for (double amount : payouts.values()) {
			if (amount > 0) {
				wages += amount;
			}
		}
		double needed = pool + wages;
		if (needed <= 0) {
			return;
		}
		double available = Math.max(0.0, guild.getBank().getWealth());
		double scale = PayoutClamp.scaleFactor(available, needed);
		if (scale <= 0.0) {
			return;
		}

		double actualPool = Formatter.formatDouble(pool * scale);
		payDividends(guild, actualPool, playerBank, economy, uuids);
		payPlayerPayouts(guild, payouts, scale, playerBank, economy);
	}

	private static void payDividends(
			Guild guild,
			double pool,
			PlayerBank playerBank,
			PlayerEconomyManager economy,
			PlayerUuidLookup uuids) {
		if (pool <= 0 || guild.isBase()) {
			return;
		}
		DividendBreakdown breakdown = guild.getLedger().breakdownForPool(pool);
		double tax = breakdown.tax();
		double payout = breakdown.payout();
		Faction faction = guild.getFaction();
		Guild capital = faction == null ? null : faction.getOrCreateMainGuild();
		double withdrawn = 0.0;
		if (tax > 0 && capital != null && capital.getBank() != null && capital != guild) {
			capital.getBank().deposit(tax);
			withdrawn += tax;
		}
		java.util.List<String> eligible = guild.getDividendEligibleMembers();
		double perMember = breakdown.perMember();
		if (perMember > 0 && eligible != null) {
			for (String name : eligible) {
				if (name == null || name.isBlank()) {
					continue;
				}
				UUID uuid = uuids == null ? null : uuids.uuidOf(name);
				if (uuid == null) {
					continue;
				}
				if (playerBank != null) {
					playerBank.depositToBank(uuid, perMember);
				}
				if (economy != null) {
					economy.getLedger(uuid).add(PlayerCashflow.DIVIDEND_PAYOUT, perMember);
				}
				withdrawn += perMember;
			}
		}
		withdrawn = Formatter.formatDouble(withdrawn);
		if (withdrawn > 0) {
			Bank bank = guild.getBank();
			double pay = Math.min(withdrawn, Math.max(0.0, bank.getWealth()));
			if (pay > 0) {
				bank.withdraw(pay);
			}
		}
	}

	private static void payPlayerPayouts(
			Guild guild,
			Map<UUID, Double> payouts,
			double scale,
			PlayerBank playerBank,
			PlayerEconomyManager economy) {
		if (payouts == null || payouts.isEmpty() || playerBank == null) {
			return;
		}
		double withdrawn = 0.0;
		for (Map.Entry<UUID, Double> entry : payouts.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
				continue;
			}
			double amount = Formatter.formatDouble(entry.getValue() * scale);
			if (amount <= 0) {
				continue;
			}
			playerBank.depositToBank(entry.getKey(), amount);
			// Wages are deliberately not taxed as citizen income for now; the seam is
			// the PlayerCashflow entry, so taxing them later means reading this one line.
			if (economy != null) {
				economy.getLedger(entry.getKey()).add(PlayerCashflow.WAGES, amount);
			}
			withdrawn += amount;
		}
		withdrawn = Formatter.formatDouble(withdrawn);
		if (withdrawn > 0) {
			Bank bank = guild.getBank();
			double pay = Math.min(withdrawn, Math.max(0.0, bank.getWealth()));
			if (pay > 0) {
				bank.withdraw(pay);
			}
		}
	}
}
