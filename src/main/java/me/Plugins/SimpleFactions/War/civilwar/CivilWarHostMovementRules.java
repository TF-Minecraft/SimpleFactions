package me.Plugins.SimpleFactions.War.civilwar;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Member;

public final class CivilWarHostMovementRules {
	private CivilWarHostMovementRules() {}

	public static boolean isOneProvinceHost(Faction host) {
		return host != null && host.getProvinces() != null && host.getProvinces().size() == 1;
	}

	public static boolean blocksHostGuildStart(Faction host, String leader) {
		if (!isOneProvinceHost(host) || leader == null) {
			return false;
		}
		Member relation = host.getRelationToFaction(leader);
		return relation == Member.GUILD_LEADER || relation == Member.GUILD_MEMBER;
	}

	public static boolean blocksHostGuildJoin(Faction host) {
		return isOneProvinceHost(host);
	}
}