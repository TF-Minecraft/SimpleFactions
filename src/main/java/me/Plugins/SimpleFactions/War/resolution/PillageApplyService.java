package me.Plugins.SimpleFactions.War.resolution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.declare.PillageEligibility;
import me.Plugins.SimpleFactions.settlement.Settlement;

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
