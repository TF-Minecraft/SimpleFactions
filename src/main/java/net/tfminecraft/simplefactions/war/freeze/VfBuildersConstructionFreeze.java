package net.tfminecraft.simplefactions.war.freeze;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.vfbuilders.api.ConstructionFreeze;

/** Holds VFBuilders projects of players whose war is in its buildup or had a battle postponed. */
public final class VfBuildersConstructionFreeze implements ConstructionFreeze {

	/**
	 * Only call once VFBuilders is enabled and its ConstructionFreeze API is on the
	 * classpath; this class cannot load without it.
	 */
	public static void register(Plugin plugin) {
		Bukkit.getServicesManager().register(
				ConstructionFreeze.class, new VfBuildersConstructionFreeze(), plugin, ServicePriority.Normal);
		plugin.getLogger().info("VFBuilders projects freeze with war buildups and postponed battles");
	}

	@Override
	public String freezeReason(UUID constructorUuid) {
		return reason(constructorKey(constructorUuid));
	}

	/**
	 * VFBuilders 2.2.0 and later pass the constructor's character; a project from before
	 * then, or started without a current character, has none and falls back to the account.
	 */
	@Override
	public String freezeReason(UUID constructorUuid, UUID constructorCharacterId) {
		if (constructorCharacterId == null) {
			return freezeReason(constructorUuid);
		}
		return reason(Members.key(constructorCharacterId));
	}

	private static String reason(String key) {
		Instant now = CampaignClock.now();
		PreparationFreeze.Freeze freeze = PreparationFreeze.freeze(key, now);
		if (freeze == null) {
			return null;
		}
		return PreparationFreeze.reasonText(freeze, now);
	}

	/** Projects belong to accounts: the character playing now, else the account's legacy heir. */
	static String constructorKey(UUID constructorUuid) {
		Player online = Bukkit.getPlayer(constructorUuid);
		if (online != null) {
			return Members.key(online);
		}
		return Characters.legacyHeir(constructorUuid).map(Members::key).orElse(null);
	}
}
