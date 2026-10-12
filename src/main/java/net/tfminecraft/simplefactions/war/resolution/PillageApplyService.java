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
		Settlement settlement = PillageEligibility.findSettlement(war.getTargetSettlementId(), war.getDefenders().getLeader());
		if (settlement == null) {
			return;
		}
		List<Guild> hit = guildsInSettlement(settlement, FactionManager.getAllGuilds());
        applyTreasury(war, hit, snapshotLoot(hit, PillageApplyService::liveTradeIncome, Cache.pillageLootDays), true);
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

    static void depositLoot(War war, double loot) { applyTreasury(war, List.of(), loot, false); }

    private static void applyTreasury(War war, List<Guild> hit, double loot, boolean penalties) {
        if (war == null || war.getAttackers() == null || !Double.isFinite(loot) || loot < 0) return;
        Guild attacker = war.getAttackers().getLeader().getOrCreateMainGuild();
        if (attacker.getBank() == null && !penalties) return;
        double payment = attacker.getBank() == null ? 0 : loot;
        String id = "sf:pillage:" + war.getId() + ":" + war.getStartedAt() + ":" + attacker.getIncarnationId();
        Boolean resumed = net.tfminecraft.simplefactions.economy.TreasuryActions.resume(id, null);
        if (resumed != null) {
            if (!resumed) throw new IllegalStateException("Pillage treasury recovery is pending: " + id);
            return;
        }
        var plan = new net.tfminecraft.simplefactions.economy.TreasuryActions.Plan(id, "Pillage spoils", null, attacker)
                .transfer(net.tfminecraft.denareconomy.api.Denars.MINT,
                        net.tfminecraft.simplefactions.economy.TreasuryActions.account(attacker),
                        net.tfminecraft.simplefactions.economy.Money.cents(payment));
        if (penalties) for (Guild guild : hit) {
            List<net.tfminecraft.simplefactions.government.StabilityModifier> after = new ArrayList<>(guild.getPillageHits());
            after.removeIf(modifier -> modifier != null && PillageTradeHit.NAME.equals(modifier.getName()));
            after.add(new net.tfminecraft.simplefactions.government.StabilityModifier(PillageTradeHit.NAME,
                    Cache.pillageTradeHitPercent, PillageTradeHit.decayFromConfig()));
            plan.capture(guild).effect("pillage", guild, "", guild.getPillageHits(), after);
        }
        if (!plan.commit(null)) throw new IllegalStateException("Pillage treasury action did not complete: " + id);
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
