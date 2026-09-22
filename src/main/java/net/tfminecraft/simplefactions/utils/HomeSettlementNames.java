package net.tfminecraft.simplefactions.utils;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.settlement.Settlement;

public final class HomeSettlementNames {

	private HomeSettlementNames() {}

	public static String of(Faction faction) {
		if (faction == null || !faction.hasCapital()) {
			return "None";
		}
		return ofProvince(faction, faction.getCapital());
	}

	public static String of(Guild guild) {
		if (guild == null || !guild.hasCapital()) {
			return "None";
		}
		return ofProvince(guild.getFaction(), guild.getCapital());
	}

	public static String ofProvince(Faction owner, int provinceId) {
		if (owner != null && owner.getSettlementHandler() != null) {
			Settlement settlement = owner.getSettlementHandler().getByProvince(provinceId);
			if (settlement != null && settlement.getName() != null && !settlement.getName().isBlank()) {
				return settlement.getName();
			}
		}
		Title title = TitleLoader.getByProvince(provinceId);
		if (title != null && title.getName() != null && !title.getName().isBlank()) {
			return title.getName();
		}
		return "Province " + provinceId;
	}
}
