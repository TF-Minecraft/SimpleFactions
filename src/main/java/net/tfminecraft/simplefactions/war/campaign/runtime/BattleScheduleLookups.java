package net.tfminecraft.simplefactions.war.campaign.runtime;

import net.tfminecraft.simplefactions.identity.Members;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;

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
			return FactionManager.getByMember(Members.key(online));
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

	/** Battle votes stay keyed by account, so a member key maps to the account that plays it. */
	public static Function<String, UUID> memberNameToUuid() {
		return key -> {
			if (key == null || key.isBlank()) {
				return null;
			}
			if (Bukkit.getServer() == null) {
				return spoofMemberUuid(key);
			}
			return Members.accountId(key).orElse(null);
		};
	}

	public static UUID spoofMemberUuid(String memberName) {
		if (memberName == null || memberName.isBlank()) {
			return null;
		}
		return UUID.nameUUIDFromBytes(
				("sf-battle-vote:" + memberName.toLowerCase(java.util.Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
	}

	public static Function<String, UUID> spoofMemberNameToUuid() {
		return BattleScheduleLookups::spoofMemberUuid;
	}

	private static Faction resolveFactionFromRoster(War war, UUID uuid) {
		for (BelligerentRole role : BelligerentRole.values()) {
			Side side = role == BelligerentRole.ATTACKER ? war.getAttackers() : war.getDefenders();
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
