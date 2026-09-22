package me.Plugins.SimpleFactions.Objects;

import org.bukkit.Location;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattlePlacementValidator;

public final class BankPlacementValidator {
	private static final String PROVINCE_RESOLVE_ERROR = "§a[SimpleFactions] §cError! could not resolve province";
	private static final String NO_PROVINCE_ERROR = "§cThis location has no province!";
	private static final String GUILD_CAPITAL_ERROR = "§cYour bank must be placed in your guild capital province.";
	private static final String FACTION_CAPITAL_ERROR = "§cYour faction bank must be placed in your capital province.";

	private BankPlacementValidator() {
	}

	public static String failureReasonForGuild(Guild guild, Location location) {
		return failureReasonForGuild(guild, BattlePlacementValidator.provinceAt(location));
	}

	public static String failureReasonForFaction(Faction faction, Location location) {
		return failureReasonForFaction(faction, BattlePlacementValidator.provinceAt(location));
	}

	public static String failureReasonForGuild(Guild guild, int provinceAtLocation) {
		if (guild == null || !guild.hasCapital()) {
			return null;
		}
		int capital = guild.getCapital();
		if (capital <= 0) {
			return null;
		}
		return failureReasonForCapital(capital, provinceAtLocation, GUILD_CAPITAL_ERROR);
	}

	public static String failureReasonForFaction(Faction faction, int provinceAtLocation) {
		if (faction == null || !faction.hasCapital()) {
			return null;
		}
		int capital = faction.getCapital();
		if (capital <= 0) {
			return null;
		}
		return failureReasonForCapital(capital, provinceAtLocation, FACTION_CAPITAL_ERROR);
	}

	private static String failureReasonForCapital(int capital, int provinceAtLocation, String wrongProvinceMessage) {
		if (provinceAtLocation == -2) {
			return PROVINCE_RESOLVE_ERROR;
		}
		if (provinceAtLocation <= 0) {
			return NO_PROVINCE_ERROR;
		}
		if (provinceAtLocation != capital) {
			return wrongProvinceMessage;
		}
		return null;
	}
}
