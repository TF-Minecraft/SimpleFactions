package net.tfminecraft.simplefactions.war.resolution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.declare.PillageEligibility;
import net.tfminecraft.simplefactions.settlement.Settlement;

public final class PillageApplyService {
	private PillageApplyService() {}

	public static void apply(War war) {
		if (war == null || war.getTargetSettlementId() == null || war.getTargetSettlementId().isBlank()) {
			return;
		}
		Settlement settlement = PillageEligibility.findSettlement(war.getTargetSettlementId());
		if (settlement == null) {
			return;
		}
		List<Guild> hit = guildsInSettlement(settlement, FactionManager.getAllGuilds());
		double loot = snapshotLoot(hit, PillageApplyService::liveTradeIncome, Cache.pillageLootDays);
		depositLoot(war, loot);
		attachHits(hit, Cache.pillageTradeHitPercent, PillageTradeHit.decayFromConfig());
	}

	static List<Guild> guildsInSettlement(Settlement settlement, List<Guild> all) {
		List<Guild> hit = new ArrayList<>();
		if (settlement == null || all == null) {
			return hit;
		}
		for (Guild guild : all) {
			if (guild == null) {
				continue;
			}
			if (settlement.contains(guild.getCapital())) {
				hit.add(guild);
			}
		}
		return hit;
	}

	static double snapshotLoot(List<Guild> guilds, ToDoubleFunction<Guild> tradeIncome, int lootDays) {
		if (guilds == null || tradeIncome == null || lootDays <= 0) {
			return 0;
		}
		double sum = 0;
		for (Guild guild : guilds) {
			sum += tradeIncome.applyAsDouble(guild);
		}
		return sum * lootDays;
	}

	static void attachHits(List<Guild> guilds, double percent, double decay) {
		if (guilds == null) {
			return;
		}
		for (Guild guild : guilds) {
			PillageTradeHit.attach(guild, percent, decay);
		}
	}

	static void depositLoot(War war, double loot) {
		if (war == null || loot <= 0 || war.getAttackers() == null) {
			return;
		}
		Faction attacker = war.getAttackers().getLeader();
		if (attacker == null) {
			return;
		}
		Bank bank = attacker.getBank();
		if (bank == null) {
			return;
		}
		bank.deposit(loot);
	}

	static double liveTradeIncome(Guild guild) {
		if (guild == null) {
			return 0;
		}
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin != null) {
			ProvinceManager provinces = plugin.getProvinceManager();
			if (provinces != null) {
				return provinces.getIncome(guild, false);
			}
		}
		if (guild.getTradeBreakdown() != null) {
			return guild.getTradeBreakdown().getIncome();
		}
		return 0;
	}
}
