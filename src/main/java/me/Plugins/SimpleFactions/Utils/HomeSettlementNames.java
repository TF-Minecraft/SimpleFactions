package me.Plugins.SimpleFactions.Utils;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.settlement.Settlement;

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
