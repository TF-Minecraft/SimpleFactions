package net.tfminecraft.simplefactions.war.campaign.runtime;

import net.tfminecraft.simplefactions.identity.Members;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
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
			Faction hirer = resolveMercenaryHirer(war, uuid);
			if (hirer != null) {
				return hirer;
			}
			if (Bukkit.getServer() == null) {
				return null;
			}
			return uuidToFaction().apply(uuid);
		};
	}

	/**
	 * The faction an account votes for in this war, or null when it has no vote: a member of a
	 * participating faction votes for that faction, a mercenary for the faction that hired them.
	 */
	public static Faction votingFaction(War war, UUID account) {
		if (war == null || account == null) {
			return null;
		}
		Faction faction = uuidToFactionForWar(war).apply(account);
		return faction != null && war.getSide(faction) != null ? faction : null;
	}

	/**
	 * The faction this player votes for: the character being played when its faction is in the war,
	 * otherwise whatever the account votes for (another character's faction, or a mercenary's hirer).
	 */
	public static Faction voterFaction(War war, Player player) {
		if (war == null || player == null) {
			return null;
		}
		Faction own = FactionManager.getByMember(Members.key(player));
		if (own != null && war.getSide(own) != null) {
			return own;
		}
		return votingFaction(war, player.getUniqueId());
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

	/** The hiring faction when the account plays a mercenary under contract to a side of this war. */
	private static Faction resolveMercenaryHirer(War war, UUID uuid) {
		// MercenaryEngagements.on takes a null side, and a blank name maps to no account.
		for (Side side : new Side[] {war.getAttackers(), war.getDefenders()}) {
			for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
				for (String enlisted : engagement.company().getEnlisted()) {
					if (uuid.equals(spoofMemberUuid(enlisted))
							|| (Bukkit.getServer() != null && uuid.equals(memberNameToUuid().apply(enlisted)))) {
						return engagement.hirer();
					}
				}
			}
		}
		return null;
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
