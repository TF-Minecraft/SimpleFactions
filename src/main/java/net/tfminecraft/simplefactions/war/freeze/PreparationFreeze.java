package net.tfminecraft.simplefactions.war.freeze;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompanies;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * A postponed battle must not buy either side preparation time. Each postponement
 * freezes every build timer of the factions in that war, and of mercenary companies
 * they host or hire, for {@link Cache#warPostponeFreezeHours}. Raid repair locks are
 * pushed back by the same amount and no new mercenaries can be hired meanwhile.
 * Upkeep and wages keep running.
 */
public final class PreparationFreeze {

	private static Supplier<Collection<War>> activeWars = WarManager::getActive;

	private PreparationFreeze() {
	}

	/** Tests swap the war source; pass null to restore {@link WarManager#getActive()}. */
	static void setActiveWars(Supplier<Collection<War>> source) {
		activeWars = source != null ? source : WarManager::getActive;
	}

	/**
	 * Called whenever a battle day moves back. Stacks: a second postponement adds
	 * another full period on top of what is left.
	 */
	public static void applyPostponement(War war, Instant now) {
		if (war == null || now == null || Cache.warPostponeFreezeHours <= 0) {
			return;
		}
		long hours = Cache.warPostponeFreezeHours;
		Instant current = war.getPreparationFrozenUntil();
		Instant base = current != null && current.isAfter(now) ? current : now;
		war.setPreparationFrozenUntil(base.plus(hours, ChronoUnit.HOURS));
		for (Map.Entry<String, Instant> lock : war.getRaidRepairLockUntil().entrySet()) {
			if (lock.getValue() != null && lock.getValue().isAfter(now)) {
				lock.setValue(lock.getValue().plus(hours, ChronoUnit.HOURS));
			}
		}
		notifyMembers(war, hours);
	}

	/** @return when the latest freeze on this faction ends, or null when it is not frozen */
	public static Instant frozenUntil(Faction faction, Instant now) {
		if (faction == null || faction.getId() == null) {
			return null;
		}
		Instant latest = null;
		for (War war : activeWars.get()) {
			if (war == null || !war.isPreparationFrozen(now)) {
				continue;
			}
			if (!participantIds(war).contains(faction.getId().toLowerCase())) {
				continue;
			}
			latest = later(latest, war.getPreparationFrozenUntil());
		}
		return latest;
	}

	public static boolean isFrozen(Faction faction) {
		return frozenUntil(faction, Instant.now()) != null;
	}

	/** A company freezes with its host faction and with every faction it is serving. */
	public static Instant frozenUntil(MercenaryCompany company, Instant now) {
		if (company == null || !anyWarFrozen(now)) {
			return null;
		}
		Guild guild = company.getGuild();
		Instant latest = guild != null ? frozenUntil(guild.getFaction(), now) : null;
		for (MercenaryContract contract : company.getContractHandler().getActive()) {
			latest = later(latest, frozenUntil(contract.getHirer(), now));
		}
		return latest;
	}

	public static boolean isFrozen(MercenaryCompany company) {
		return frozenUntil(company, Instant.now()) != null;
	}

	/** Freeze covering a player's own projects, through their faction or their company. */
	public static Instant frozenUntil(String playerName, Instant now) {
		if (playerName == null || !anyWarFrozen(now)) {
			return null;
		}
		Instant latest = frozenUntil(FactionManager.getByMember(playerName), now);
		return later(latest, frozenUntil(MercenaryCompanies.findByMember(playerName), now));
	}

	/** Refusal for a hire by this faction, or null when hiring is open. */
	public static String hiringBlockedMessage(Faction hirer, Instant now) {
		Instant until = frozenUntil(hirer, now);
		if (until == null) {
			return null;
		}
		return "§cA battle of yours was postponed. No mercenaries can be hired for "
				+ formatRemaining(until, now) + ".";
	}

	/** Queue lore line for a held timer, or null when the timer is running. */
	public static String frozenLore(Instant until) {
		if (until == null) {
			return null;
		}
		return "§cFrozen: battle postponed (" + formatRemaining(until, Instant.now()) + " left)";
	}

	public static String formatRemaining(Instant until, Instant now) {
		long millis = until.toEpochMilli() - now.toEpochMilli();
		long minutes = Math.max(1, (millis + 59_999) / 60_000);
		long hours = minutes / 60;
		long mins = minutes % 60;
		if (hours > 0) return hours + "h " + mins + "m";
		return mins + "m";
	}

	/** Cheap check first: every build timer asks this each second. */
	private static boolean anyWarFrozen(Instant now) {
		for (War war : activeWars.get()) {
			if (war != null && war.isPreparationFrozen(now)) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> participantIds(War war) {
		Set<String> ids = new LinkedHashSet<>();
		for (Faction faction : BattleSideMembers.collectParticipatingFactions(war.getAttackers())) {
			if (faction.getId() != null) ids.add(faction.getId().toLowerCase());
		}
		for (Faction faction : BattleSideMembers.collectParticipatingFactions(war.getDefenders())) {
			if (faction.getId() != null) ids.add(faction.getId().toLowerCase());
		}
		return ids;
	}

	private static Instant later(Instant a, Instant b) {
		if (a == null) return b;
		if (b == null) return a;
		return b.isAfter(a) ? b : a;
	}

	private static void notifyMembers(War war, long hours) {
		if (Bukkit.getServer() == null) {
			return;
		}
		Set<String> members = new LinkedHashSet<>();
		members.addAll(BattleSideMembers.collectEligibleMemberNames(war.getAttackers()));
		members.addAll(BattleSideMembers.collectEligibleMemberNames(war.getDefenders()));
		String message = "§eThe battle was postponed. For the next " + hours + "h your regiments, installations, "
				+ "war upgrades, mercenary companies and vehicle projects are frozen, and no mercenaries can be hired.";
		for (String member : members) {
			Player player = Bukkit.getPlayerExact(member);
			if (player != null) {
				player.sendMessage(message);
			}
		}
	}
}
