package net.tfminecraft.simplefactions.war.freeze;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;


import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompanies;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * A war's factions must not build up between the declaration and the first battle. While a war is
 * in its buildup, every faction in it, and the mercenary companies they host or hire, is frozen:
 * running build timers pause and new ones cannot be started. Hiring mercenaries, upkeep and wages
 * keep running. Battles are no longer postponed, but a postponement freeze saved before that still
 * runs out as it was set.
 */
public final class PreparationFreeze {

	/** Why a faction is frozen. */
	public enum Reason {
		/** Until the war's first battle starts. */
		BUILDUP,
		/** Until a fixed time after a postponement (only from saves made before postponements went). */
		POSTPONED
	}

	/** A freeze in force: {@code until} is null for a buildup, which ends with the first battle. */
	public record Freeze(Reason reason, Instant until) {
	}

	private static Supplier<Collection<War>> activeWars = WarManager::getActive;

	private PreparationFreeze() {
	}

	/** Tests swap the war source; pass null to restore {@link WarManager#getActive()}. */
	static void setActiveWars(Supplier<Collection<War>> source) {
		activeWars = source != null ? source : WarManager::getActive;
	}

	/** The freeze on this faction, or null when it is free to build. A buildup outranks a postponement. */
	public static Freeze freeze(Faction faction, Instant now) {
		if (faction == null || faction.getId() == null) {
			return null;
		}
		Freeze latest = null;
		for (War war : activeWars.get()) {
			if (war == null || !war.isPreparationFrozen(now)) {
				continue;
			}
			if (!participantIds(war).contains(faction.getId().toLowerCase())) {
				continue;
			}
			latest = stronger(latest, of(war, now));
		}
		return latest;
	}

	public static boolean isFrozen(Faction faction) {
		return freeze(faction, CampaignClock.now()) != null;
	}

	/** A company freezes with its host faction and with every faction it is serving. */
	public static Freeze freeze(MercenaryCompany company, Instant now) {
		if (company == null || !anyWarFrozen(now)) {
			return null;
		}
		Guild guild = company.getGuild();
		Freeze latest = guild != null ? freeze(guild.getFaction(), now) : null;
		for (MercenaryContract contract : company.getContractHandler().getActive()) {
			latest = stronger(latest, freeze(contract.getHirer(), now));
		}
		return latest;
	}

	public static boolean isFrozen(MercenaryCompany company) {
		return freeze(company, CampaignClock.now()) != null;
	}

	/** Freeze covering a player's own projects, through their faction or their company. */
	public static Freeze freeze(String memberKey, Instant now) {
		if (memberKey == null || !anyWarFrozen(now)) {
			return null;
		}
		Freeze latest = freeze(FactionManager.getByMember(memberKey), now);
		return stronger(latest, freeze(MercenaryCompanies.findByMember(memberKey), now));
	}

	/** Refusal, without colour, for starting something new while frozen; null when it may start. */
	public static String refusal(Freeze freeze, String what, Instant now) {
		if (freeze == null) {
			return null;
		}
		if (freeze.reason() == Reason.BUILDUP) {
			return "Your war is in its buildup. No new " + what + " can be started until the first battle.";
		}
		return "A battle of yours was postponed. No new " + what + " can be started for "
				+ formatRemaining(freeze.until(), now) + ".";
	}

	public static String refusal(Faction faction, String what) {
		Instant now = CampaignClock.now();
		return refusal(freeze(faction, now), what, now);
	}

	public static String refusal(MercenaryCompany company, String what) {
		Instant now = CampaignClock.now();
		return refusal(freeze(company, now), what, now);
	}

	public static String refusalForMember(String memberKey, String what) {
		Instant now = CampaignClock.now();
		return refusal(freeze(memberKey, now), what, now);
	}

	/** Queue lore line for a held timer, or null when the timer is running. */
	public static String frozenLore(Freeze freeze) {
		if (freeze == null) {
			return null;
		}
		return "§cFrozen: " + reasonText(freeze, CampaignClock.now());
	}

	/** {@code war buildup, until the first battle} or {@code battle postponed (3h 0m left)}. */
	public static String reasonText(Freeze freeze, Instant now) {
		if (freeze.reason() == Reason.BUILDUP) {
			return "war buildup, until the first battle";
		}
		return "battle postponed (" + formatRemaining(freeze.until(), now) + " left)";
	}

	public static String formatRemaining(Instant until, Instant now) {
		long millis = until.toEpochMilli() - now.toEpochMilli();
		long minutes = Math.max(1, (millis + 59_999) / 60_000);
		long hours = minutes / 60;
		long mins = minutes % 60;
		if (hours > 0) return hours + "h " + mins + "m";
		return mins + "m";
	}

	private static Freeze of(War war, Instant now) {
		if (war.isBuildupFrozen()) {
			return new Freeze(Reason.BUILDUP, null);
		}
		return new Freeze(Reason.POSTPONED, war.getPreparationFrozenUntil());
	}

	/** A buildup lasts until the first battle, so it outlasts any postponement. */
	private static Freeze stronger(Freeze a, Freeze b) {
		if (a == null) return b;
		if (b == null) return a;
		if (a.reason() == Reason.BUILDUP) return a;
		if (b.reason() == Reason.BUILDUP) return b;
		return b.until().isAfter(a.until()) ? b : a;
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
}
