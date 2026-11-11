package net.tfminecraft.simplefactions.war.campaign.availability;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * Counts marks per side and decides from them: whether a date has enough of each side at one
 * time, and which start hour a battle day gets. An account counts for the side its voting
 * faction is on, so a mercenary counts for the faction that hired them.
 */
public final class AvailabilityTally {

	/** Marks at one start hour of one date. */
	public record HourCount(int hour, int attackers, int defenders) {
		/** How many can fight on both sides at once. */
		public int both() {
			return Math.min(attackers, defenders);
		}

		public int total() {
			return attackers + defenders;
		}
	}

	private AvailabilityTally() {
	}

	/** Each start hour of the date with its marks per side, earliest first. */
	public static List<HourCount> count(War war, LocalDate date) {
		return count(war, date, sides(war));
	}

	public static List<HourCount> count(War war, LocalDate date, Function<UUID, BelligerentRole> sideOf) {
		List<Integer> hours = BattleWindowService.listValidHours();
		int[] attackers = new int[hours.size()];
		int[] defenders = new int[hours.size()];
		for (Map.Entry<UUID, Map<LocalDate, Set<Integer>>> entry : war.getAvailability().marks().entrySet()) {
			Set<Integer> marked = entry.getValue().get(date);
			BelligerentRole side = marked == null ? null : sideOf.apply(entry.getKey());
			if (side == null) {
				continue;
			}
			for (int i = 0; i < hours.size(); i++) {
				if (marked.contains(hours.get(i))) {
					if (side == BelligerentRole.ATTACKER) {
						attackers[i]++;
					} else {
						defenders[i]++;
					}
				}
			}
		}
		List<HourCount> counts = new ArrayList<>();
		for (int i = 0; i < hours.size(); i++) {
			counts.add(new HourCount(hours.get(i), attackers[i], defenders[i]));
		}
		return counts;
	}

	/** The side an account marks for, looked up once per account; null for an account with no vote. */
	public static Function<UUID, BelligerentRole> sides(War war) {
		Map<UUID, BelligerentRole> known = new HashMap<>();
		return account -> {
			if (!known.containsKey(account)) {
				known.put(account, BattleSideMembers.resolveSide(war, BattleScheduleLookups.votingFaction(war, account)));
			}
			return known.get(account);
		};
	}

	/**
	 * How many of a side must be able to come at one time: {@code min_per_side}, or the whole side
	 * when it has fewer active members, and at least one.
	 */
	public static int need(War war, BelligerentRole role) {
		return Math.max(1, Math.min(Cache.warAvailabilityMinPerSide, sideSize(war, role)));
	}

	/**
	 * Accounts that count toward a side's size: members of its factions not inactive for four
	 * weeks, and the mercenaries under contract to it, each account once.
	 */
	public static int sideSize(War war, BelligerentRole role) {
		Side side = role == BelligerentRole.ATTACKER ? war.getAttackers() : war.getDefenders();
		Function<String, UUID> accounts = BattleScheduleLookups.memberNameToUuid();
		Set<UUID> counted = new LinkedHashSet<>();
		for (String member : InactivityService.activeOnly(BattleSideMembers.collectEligibleMemberNames(side))) {
			addAccount(counted, accounts.apply(member));
		}
		for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
			for (String enlisted : InactivityService.activeOnly(engagement.company().getEnlisted())) {
				addAccount(counted, accounts.apply(enlisted));
			}
		}
		return counted.size();
	}

	private static void addAccount(Set<UUID> counted, UUID account) {
		if (account != null) {
			counted.add(account);
		}
	}

	/**
	 * Every account that may mark, with its side: the accounts playing a member of a participating
	 * faction, then the mercenaries under contract to a side; attackers first.
	 */
	public static Map<UUID, BelligerentRole> eligibleAccounts(War war) {
		Map<UUID, BelligerentRole> accounts = new LinkedHashMap<>();
		for (BelligerentRole role : BelligerentRole.values()) {
			Side side = role == BelligerentRole.ATTACKER ? war.getAttackers() : war.getDefenders();
			for (String member : BattleSideMembers.collectEligibleMemberNames(side)) {
				Members.accountId(member).ifPresent(owner -> accounts.putIfAbsent(owner, role));
			}
		}
		for (BelligerentRole role : BelligerentRole.values()) {
			Side side = role == BelligerentRole.ATTACKER ? war.getAttackers() : war.getDefenders();
			for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
				for (String enlisted : new ArrayList<>(engagement.company().getEnlisted())) {
					Members.accountId(enlisted).ifPresent(owner -> accounts.putIfAbsent(owner, role));
				}
			}
		}
		return accounts;
	}

	/** Whether some start hour has at least {@code needAttackers} attackers and {@code needDefenders} defenders. */
	public static boolean meetsNeed(List<HourCount> counts, int needAttackers, int needDefenders) {
		for (HourCount count : counts) {
			if (count.attackers() >= needAttackers && count.defenders() >= needDefenders) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A battle day's start hour: the most who can fight on both sides at once, then the most people
	 * in all, then the earliest.
	 */
	public static int pickHour(List<HourCount> counts) {
		HourCount best = counts.get(0);
		for (HourCount count : counts) {
			if (count.both() > best.both() || (count.both() == best.both() && count.total() > best.total())) {
				best = count;
			}
		}
		return best.hour();
	}

	/** A forced battle day's start hour: the most marks from either side, then the earliest; no marks at all, the first hour. */
	public static int pickForcedHour(List<HourCount> counts) {
		HourCount best = counts.get(0);
		for (HourCount count : counts) {
			if (count.total() > best.total()) {
				best = count;
			}
		}
		return best.hour();
	}

	/** True when one side marked no hour at all on the date. */
	public static boolean oneSideAbsent(List<HourCount> counts) {
		int attackers = 0;
		int defenders = 0;
		for (HourCount count : counts) {
			attackers += count.attackers();
			defenders += count.defenders();
		}
		return attackers == 0 || defenders == 0;
	}
}
