package me.Plugins.SimpleFactions.War.campaign.runtime;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;

public final class BattleScheduleLookups {
	private BattleScheduleLookups() {}

	public static Function<UUID, Faction> uuidToFaction() {
		return uuid -> {
			if (uuid == null) {
				return null;
			}
			Player online = Bukkit.getPlayer(uuid);
			if (online == null) {
				return null;
			}
			return FactionManager.getByMember(online.getName());
		};
	}

	public static Function<UUID, Faction> uuidToFactionForWar(War war) {
		return uuid -> {
			if (uuid == null || war == null) {
				return null;
			}
			Faction rosterMatch = resolveFactionFromRoster(war, uuid);
			if (rosterMatch != null) {
				return rosterMatch;
			}
			if (Bukkit.getServer() == null) {
				return null;
			}
			return uuidToFaction().apply(uuid);
		};
	}

	public static Function<String, UUID> memberNameToUuid() {
		return name -> {
			if (name == null || name.isBlank()) {
				return null;
			}
			if (Bukkit.getServer() == null) {
				return spoofMemberUuid(name);
			}
			return Bukkit.getOfflinePlayer(name).getUniqueId();
		};
	}

	public static UUID spoofMemberUuid(String memberName) {
		if (memberName == null || memberName.isBlank()) {
			return null;
		}
		return UUID.nameUUIDFromBytes(
				("sf-battle-vote:" + memberName.toLowerCase()).getBytes(StandardCharsets.UTF_8));
	}

	public static Function<String, UUID> spoofMemberNameToUuid() {
		return BattleScheduleLookups::spoofMemberUuid;
	}

	private static Faction resolveFactionFromRoster(War war, UUID uuid) {
		for (BelligerentRole role : BelligerentRole.values()) {
			Side side = role == BelligerentRole.ATTACKER ? war.getAttackers() : war.getDefenders();
			if (side == null) {
				continue;
			}
			for (Faction faction : BattleSideMembers.collectParticipatingFactions(side)) {
				for (String memberName : faction.getMembers()) {
					if (memberName == null || memberName.isBlank()) {
						continue;
					}
					if (uuid.equals(spoofMemberUuid(memberName))) {
						return faction;
					}
					if (Bukkit.getServer() != null && uuid.equals(memberNameToUuid().apply(memberName))) {
						return faction;
					}
				}
			}
		}
		return null;
	}
}
