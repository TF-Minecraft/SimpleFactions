package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.BannerPatterns;
import net.tfminecraft.simplefactions.war.truce.TruceService;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * Coalition rules and lifecycle: who may form, join, leave and lead one, what it may
 * target, and how it ends. Every change is saved straight away.
 */
public final class CoalitionService {
	public record Result(boolean ok, String message) {
		static Result ok(String message) {
			return new Result(true, message);
		}

		static Result fail(String message) {
			return new Result(false, message);
		}
	}

	public static final String DISABLED = "§cCoalitions are disabled.";
	public static final String NOT_IN_COALITION = "§cYou are not in a coalition.";
	public static final String NOT_LEADER = "§cOnly the coalition leader can do that.";
	public static final String NO_FACTION = "§cNo faction by that name.";
	public static final String NO_COALITION = "§cNo coalition by that name.";

	private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9 '\\-]+");
	/** Longest gap one tick may add to a paused countdown, so a stalled server cannot freeze it. */
	private static final long MAX_PAUSE_STEP = 10L * 60L * 1000L;

	private static long lastTick;

	private CoalitionService() {}

	// ---- Queries ----

	public static Coalition of(Faction faction) {
		if (faction == null) return null;
		for (Coalition coalition : CoalitionRegistry.all()) {
			if (coalition.isMember(faction.getId())) return coalition;
		}
		return null;
	}

	/** By id, or by name with or without its colours. */
	public static Coalition byName(String nameOrId) {
		if (nameOrId == null) return null;
		Coalition byId = CoalitionRegistry.get(nameOrId);
		if (byId != null) return byId;
		String wanted = plainName(nameOrId);
		for (Coalition coalition : CoalitionRegistry.all()) {
			if (plainName(coalition.getName()).equalsIgnoreCase(wanted)) return coalition;
		}
		return null;
	}

	public static List<Coalition> targeting(Faction faction) {
		List<Coalition> result = new ArrayList<>();
		if (faction == null) return result;
		for (Coalition coalition : CoalitionRegistry.all()) {
			if (coalition.isTarget(faction.getId())) result.add(coalition);
		}
		return result;
	}

	public static boolean sameCoalition(Faction a, Faction b) {
		Coalition coalition = of(a);
		return coalition != null && b != null && coalition.isMember(b.getId());
	}

	public static Faction leader(Coalition coalition) {
		return FactionManager.getByString(coalition.getLeaderId());
	}

	public static Faction target(Coalition coalition) {
		return FactionManager.getByString(coalition.getTargetId());
	}

	public static List<Faction> members(Coalition coalition) {
		List<Faction> result = new ArrayList<>();
		for (String id : coalition.getMemberIds()) {
			Faction faction = FactionManager.getByString(id);
			if (faction != null) result.add(faction);
		}
		return result;
	}

	public static boolean isIndependent(Faction faction) {
		return faction != null && RelationManager.getOverlord(faction) == null;
	}

	/** True once the target stopped being independent or existing, until a new one is chosen. */
	public static boolean targetLost(Coalition coalition) {
		return coalition.getTargetLostAt() != null;
	}

	public static String displayName(Coalition coalition) {
		return StringFormatter.formatHex(coalition.getName());
	}

	/** The name without colour codes, as players type it. */
	public static String plainName(String raw) {
		return ChatColor.stripColor(HEX.matcher(raw).replaceAll("")).trim();
	}

	/** The coalition's own banner, or its leader's while it has none. */
	public static ItemStack banner(Coalition coalition) {
		if (coalition.getBannerPatterns() != null) return BannerPatterns.toItem(coalition.getBannerPatterns());
		Faction leader = leader(coalition);
		if (leader == null || leader.getBanner() == null) return new ItemStack(Material.WHITE_BANNER);
		return new ItemStack(leader.getBanner());
	}

	/** Time left before a coalition that lost its target disbands, or null when it has a target. */
	public static Duration targetLossRemaining(Coalition coalition) {
		if (coalition.getTargetLostAt() == null) return null;
		long deadline = coalition.getTargetLostAt() + CoalitionSettings.days(CoalitionSettings.targetLossDays)
				+ coalition.getTargetLossPausedMillis();
		return Duration.ofMillis(Math.max(0, deadline - CoalitionRegistry.now()));
	}

	// ---- Checks ----

	/** Why the faction cannot join or form a coalition right now, or null when it can. */
	static String membershipBlock(Faction faction) {
		if (!isIndependent(faction)) return "§cOnly independent factions can be in a coalition.";
		if (of(faction) != null) return "§c" + faction.getName() + " §cis already in a coalition.";
		Long until = CoalitionRegistry.cooldownUntil(faction.getId());
		if (until != null) {
			return "§c" + faction.getName() + " §cleft a coalition recently and can join or form one again in "
					+ TruceService.formatRemaining(Duration.ofMillis(until - CoalitionRegistry.now())) + ".";
		}
		return null;
	}

	/** Why the faction cannot be in a coalition against the target, or null when it can. */
	static String targetBlock(Faction faction, Faction target) {
		if (faction.getId().equalsIgnoreCase(target.getId())) {
			return "§cA coalition cannot target one of its own members.";
		}
		if (!isIndependent(target)) return "§cA coalition can only target an independent faction.";
		if (RelationManager.isAlly(faction, target) || RelationManager.isAlly(target, faction)) {
			return "§c" + faction.getName() + " §cand " + target.getName() + " §care allies.";
		}
		if (RelationManager.hasNonAggressionPact(faction, target)) {
			return "§c" + faction.getName() + " §chas a treaty with " + target.getName() + " §cthat forbids war.";
		}
		if (TruceService.hasTruce(faction, target)) {
			return "§c" + faction.getName() + " §chas a truce with " + target.getName() + "§c.";
		}
		return null;
	}

	/**
	 * A coalition may only be aimed at a realm at least as strong as its strongest member,
	 * so it stays a tool against a dominant realm rather than for piling on a weak one.
	 */
	static String gateBlock(Collection<Faction> members, Faction target) {
		Faction strongest = null;
		double most = -1;
		for (Faction member : members) {
			double realm = CoalitionCost.realmPrestige(member);
			if (realm > most) {
				most = realm;
				strongest = member;
			}
		}
		double targetRealm = CoalitionCost.realmPrestige(target);
		if (strongest == null || targetRealm >= CoalitionSettings.gateRatio * most) return null;
		return "§c" + target.getName() + " §cis not strong enough to be a coalition target: its realm prestige ("
				+ Math.round(targetRealm) + ") must be at least that of the strongest member, "
				+ strongest.getName() + " §c(" + Math.round(most) + ").";
	}

	/** Why the two may not ally while one is in a coalition aimed at the other, or null when they may. */
	public static String allianceBlock(Faction a, Faction b) {
		Coalition ofA = of(a);
		Coalition ofB = of(b);
		if ((ofA != null && ofA.isTarget(b.getId())) || (ofB != null && ofB.isTarget(a.getId()))) {
			return "§cYou cannot ally a faction your coalition is aimed at, or one in a coalition aimed at you.";
		}
		return null;
	}

	private static String capacityBlock(Faction faction, boolean leader) {
		double cost = CoalitionCost.cost(faction, leader);
		double available = faction.getDiplomacyHandler().getAvailableCapacity();
		if (available >= cost) return null;
		return "§c" + faction.getName() + " §cneeds " + Math.round(cost)
				+ " free diplomatic capacity for a coalition seat (has " + Math.round(available) + ").";
	}

	private static String nameBlock(String raw) {
		String plain = raw == null ? "" : plainName(raw);
		if (plain.length() < CoalitionSettings.nameMinLength || plain.length() > CoalitionSettings.nameMaxLength) {
			return "§cA coalition name must be " + CoalitionSettings.nameMinLength + " to "
					+ CoalitionSettings.nameMaxLength + " characters long.";
		}
		if (!ALLOWED.matcher(plain).matches()) {
			return "§cA coalition name may only use letters, numbers, spaces, apostrophes and hyphens.";
		}
		if (byName(plain) != null) return "§cThere is already a coalition with that name.";
		return null;
	}

	private static Coalition ledBy(Faction faction) {
		Coalition coalition = of(faction);
		return coalition != null && coalition.isLeader(faction.getId()) ? coalition : null;
	}

	// ---- Player actions ----

	public static Result create(Faction creator, String name, Faction target) {
		if (!CoalitionSettings.enabled) return Result.fail(DISABLED);
		String block = membershipBlock(creator);
		if (block == null) block = nameBlock(name);
		if (block == null && target == null) block = NO_FACTION;
		if (block == null) block = targetBlock(creator, target);
		if (block == null) block = gateBlock(List.of(creator), target);
		if (block == null) block = capacityBlock(creator, true);
		if (block != null) return Result.fail(block);
		long now = CoalitionRegistry.now();
		Coalition coalition = new Coalition(CoalitionRegistry.newId(), name.trim(), creator.getId(), target.getId(), now);
		coalition.setSingleMemberSince(now);
		CoalitionRegistry.add(coalition);
		CoalitionRegistry.save();
		LogManager.relations("COALITION CREATE %s by %s against %s", coalition.getId(), creator.getId(), target.getId());
		notifyFaction(target, "§c" + creator.getName() + " §chas formed the " + displayName(coalition)
				+ " §ccoalition against you.");
		return Result.ok("§aFormed the " + displayName(coalition) + " §acoalition against " + target.getName() + "§a.");
	}

	public static Result invite(Faction leader, Faction invitee) {
		if (!CoalitionSettings.enabled) return Result.fail(DISABLED);
		Coalition coalition = ledBy(leader);
		if (coalition == null) return Result.fail(of(leader) == null ? NOT_IN_COALITION : NOT_LEADER);
		if (invitee == null) return Result.fail(NO_FACTION);
		if (coalition.isMember(invitee.getId())) return Result.fail("§cThat faction is already in your coalition.");
		if (coalition.isTarget(invitee.getId())) return Result.fail("§cYou cannot invite your coalition's target.");
		if (!isIndependent(invitee)) return Result.fail("§cOnly independent factions can be in a coalition.");
		if (of(invitee) != null) return Result.fail("§c" + invitee.getName() + " §cis already in a coalition.");
		coalition.invite(invitee.getId(), CoalitionRegistry.now() + CoalitionSettings.days(CoalitionSettings.inviteExpiryDays));
		CoalitionRegistry.save();
		Faction target = target(coalition);
		notifyFaction(invitee, "§e" + leader.getName() + " §einvites you to join the " + displayName(coalition)
				+ " §ecoalition against " + (target == null ? "its target" : target.getName())
				+ "§e. Type §a/coalition join " + plainName(coalition.getName()) + " §eto accept.");
		return Result.ok("§aInvited " + invitee.getName() + " §ato the coalition.");
	}

	public static Result join(Faction joiner, Coalition coalition) {
		if (!CoalitionSettings.enabled) return Result.fail(DISABLED);
		if (coalition == null) return Result.fail(NO_COALITION);
		if (!coalition.isInvited(joiner.getId(), CoalitionRegistry.now())) {
			return Result.fail("§cYou have not been invited to that coalition.");
		}
		String block = membershipBlock(joiner);
		Faction target = target(coalition);
		if (block == null && (target == null || targetLost(coalition))) block = "§cThat coalition has lost its target.";
		if (block == null) block = targetBlock(joiner, target);
		List<Faction> members = members(coalition);
		if (block == null) {
			List<Faction> withJoiner = new ArrayList<>(members);
			withJoiner.add(joiner);
			block = gateBlock(withJoiner, target);
		}
		if (block == null) {
			for (Faction member : members) {
				if (WarManager.existsHostile(joiner, member)) {
					block = "§cYou are at war with " + member.getName() + "§c, a member of that coalition.";
					break;
				}
			}
		}
		if (block == null) block = capacityBlock(joiner, false);
		if (block != null) return Result.fail(block);
		notifyMembers(coalition, "§a" + joiner.getName() + " §ajoined the " + displayName(coalition) + " §acoalition.");
		coalition.addMember(joiner.getId(), CoalitionRegistry.now());
		coalition.setSingleMemberSince(null);
		CoalitionRegistry.save();
		return Result.ok("§aYou joined the " + displayName(coalition) + " §acoalition.");
	}

	public static Result leave(Faction member) {
		Coalition coalition = of(member);
		if (coalition == null) return Result.fail(NOT_IN_COALITION);
		if (CoalitionWars.joinedThroughCoalition(member)) {
			return Result.fail("§cYou cannot leave while fighting in a war you joined through the coalition.");
		}
		removeMember(coalition, member, true);
		notifyMembers(coalition, "§7" + member.getName() + " §7left the " + displayName(coalition) + " §7coalition.");
		return Result.ok("§7You left the " + displayName(coalition) + " §7coalition.");
	}

	public static Result kick(Faction leader, Faction member) {
		Coalition coalition = ledBy(leader);
		if (coalition == null) return Result.fail(of(leader) == null ? NOT_IN_COALITION : NOT_LEADER);
		if (member == null || !coalition.isMember(member.getId())) return Result.fail("§cThat faction is not in your coalition.");
		if (coalition.isLeader(member.getId())) return Result.fail("§cYou cannot remove yourself. Leave or disband instead.");
		removeMember(coalition, member, true);
		notifyFaction(member, "§cYou were removed from the " + displayName(coalition) + " §ccoalition.");
		notifyMembers(coalition, "§7" + member.getName() + " §7was removed from the coalition.");
		return Result.ok("§7Removed " + member.getName() + " §7from the coalition.");
	}

	public static Result transferLeadership(Faction leader, Faction member) {
		Coalition coalition = ledBy(leader);
		if (coalition == null) return Result.fail(of(leader) == null ? NOT_IN_COALITION : NOT_LEADER);
		if (member == null || !coalition.isMember(member.getId())) return Result.fail("§cThat faction is not in your coalition.");
		if (coalition.isLeader(member.getId())) return Result.fail("§cYou already lead the coalition.");
		setLeader(coalition, member);
		return Result.ok("§a" + member.getName() + " §anow leads the coalition.");
	}

	public static Result retarget(Faction leader, Faction target) {
		if (!CoalitionSettings.enabled) return Result.fail(DISABLED);
		Coalition coalition = ledBy(leader);
		if (coalition == null) return Result.fail(of(leader) == null ? NOT_IN_COALITION : NOT_LEADER);
		if (!targetLost(coalition)) return Result.fail("§cYou can only choose a new target after losing the old one.");
		if (target == null) return Result.fail(NO_FACTION);
		List<Faction> members = members(coalition);
		for (Faction member : members) {
			String block = targetBlock(member, target);
			if (block != null) return Result.fail(block);
		}
		String gate = gateBlock(members, target);
		if (gate != null) return Result.fail(gate);
		coalition.setTargetId(target.getId());
		coalition.setTargetLostAt(null);
		coalition.setTargetLossPausedMillis(0);
		// The offensive code was approved for the old target.
		coalition.setOffensive(false);
		CoalitionRegistry.save();
		notifyMembers(coalition, "§eThe " + displayName(coalition) + " §ecoalition now targets " + target.getName() + "§e.");
		notifyFaction(target, "§cThe " + displayName(coalition) + " §ccoalition is now aimed at you.");
		return Result.ok("§aThe coalition now targets " + target.getName() + "§a.");
	}

	public static Result disband(Faction leader) {
		Coalition coalition = ledBy(leader);
		if (coalition == null) return Result.fail(of(leader) == null ? NOT_IN_COALITION : NOT_LEADER);
		if (CoalitionWars.activeCoalitionWar(coalition)) {
			return Result.fail("§cYou cannot disband the coalition while one of its wars is running.");
		}
		dissolve(coalition, "§7The " + displayName(coalition) + " §7coalition was disbanded by its leader.", true);
		return Result.ok("§7Disbanded the coalition.");
	}

	public static Result setBanner(Faction leader, ItemStack held) {
		Coalition coalition = ledBy(leader);
		if (coalition == null) return Result.fail(of(leader) == null ? NOT_IN_COALITION : NOT_LEADER);
		if (held == null || !held.getType().toString().endsWith("_BANNER")) {
			return Result.fail("§cHold a banner in your main hand.");
		}
		coalition.setBannerPatterns(BannerPatterns.fromItem(held));
		CoalitionRegistry.save();
		return Result.ok("§aCoalition banner changed.");
	}

	public static Result resetBanner(Faction leader) {
		Coalition coalition = ledBy(leader);
		if (coalition == null) return Result.fail(of(leader) == null ? NOT_IN_COALITION : NOT_LEADER);
		coalition.setBannerPatterns(null);
		CoalitionRegistry.save();
		return Result.ok("§aThe coalition shows its leader's banner again.");
	}

	/** Set by a redeemed offensive code or by staff. */
	public static void setOffensive(Coalition coalition, boolean offensive) {
		coalition.setOffensive(offensive);
		CoalitionRegistry.save();
		String state = offensive ? "§cis now offensive" : "§eis now defensive";
		notifyMembers(coalition, "§eThe " + displayName(coalition) + " §ecoalition " + state + "§e.");
		Faction target = target(coalition);
		if (target != null) notifyFaction(target, "§eThe " + displayName(coalition) + " §ecoalition against you " + state + "§e.");
	}

	// ---- Staff actions ----

	public static void adminDisband(Coalition coalition) {
		dissolve(coalition, "§7The " + displayName(coalition) + " §7coalition was disbanded by staff.", false);
	}

	public static Result adminKick(Coalition coalition, Faction member) {
		if (member == null || !coalition.isMember(member.getId())) return Result.fail("§cThat faction is not in the coalition.");
		removeMember(coalition, member, false);
		notifyFaction(member, "§cStaff removed you from the " + displayName(coalition) + " §ccoalition.");
		return Result.ok("§7Removed " + member.getName() + " §7from " + displayName(coalition) + "§7.");
	}

	public static Result adminSetLeader(Coalition coalition, Faction member) {
		if (member == null || !coalition.isMember(member.getId())) return Result.fail("§cThat faction is not in the coalition.");
		setLeader(coalition, member);
		return Result.ok("§a" + member.getName() + " §anow leads " + displayName(coalition) + "§a.");
	}

	// ---- Lifecycle ----

	/**
	 * Applies every rule that depends on time or on the rest of the world: members that
	 * lost independence, a missing leader, expired invites, a lost target and its
	 * countdown, and the single-member timeout.
	 */
	public static void tick() {
		long now = CoalitionRegistry.now();
		long elapsed = lastTick == 0 ? 0 : Math.max(0, Math.min(now - lastTick, MAX_PAUSE_STEP));
		lastTick = now;
		refreshAll(now, elapsed);
	}

	/** A relation changed, so someone may have lost their independence. Only the timer advances the pause clock. */
	public static void onRelationChanged() {
		refreshAll(CoalitionRegistry.now(), 0);
	}

	private static void refreshAll(long now, long elapsed) {
		// Without the factions loaded every member would look gone and every coalition would end.
		if (FactionManager.loading || FactionManager.factions.isEmpty()) return;
		boolean changed = false;
		for (Coalition coalition : CoalitionRegistry.all()) {
			changed |= refresh(coalition, now, elapsed);
		}
		if (changed) CoalitionRegistry.save();
	}

	/** Drops a deleted faction everywhere, since its id may be taken again. */
	public static void forget(String factionId) {
		for (Coalition coalition : CoalitionRegistry.all()) {
			coalition.removeMember(factionId);
			coalition.removeInvite(factionId);
			// A new faction may take the id; it must not become the target unseen.
			if (coalition.isTarget(factionId)) coalition.setTargetId(factionId + " (disbanded)");
		}
		CoalitionRegistry.clearCooldown(factionId);
		onRelationChanged();
		CoalitionRegistry.save();
	}

	private static boolean refresh(Coalition coalition, long now, long elapsed) {
		boolean changed = false;
		for (String id : coalition.getMemberIds()) {
			Faction member = FactionManager.getByString(id);
			if (member != null && isIndependent(member)) continue;
			coalition.removeMember(id);
			changed = true;
			String name = member == null ? id : member.getName();
			notifyMembers(coalition, "§7" + name + " §7is no longer independent and left the "
					+ displayName(coalition) + " §7coalition.");
		}
		if (coalition.getMembers().isEmpty()) {
			dissolve(coalition, null, false);
			return true;
		}
		if (!coalition.isMember(coalition.getLeaderId())) {
			succeed(coalition);
			changed = true;
		}
		changed |= coalition.getInvites().values().removeIf(until -> until <= now);
		changed |= refreshTarget(coalition, now, elapsed);
		if (CoalitionRegistry.get(coalition.getId()) == null) return true;
		return refreshSingleMember(coalition, now) || changed;
	}

	private static boolean refreshTarget(Coalition coalition, long now, long elapsed) {
		Faction target = target(coalition);
		boolean valid = isIndependent(target);
		if (!valid && coalition.getTargetLostAt() == null) {
			coalition.setTargetLostAt(now);
			coalition.setTargetLossPausedMillis(0);
			notifyMembers(coalition, "§cThe " + displayName(coalition) + " §ccoalition has lost its target. The leader has "
					+ CoalitionSettings.targetLossDays + " days to choose a new one with §e/coalition target§c, or it disbands.");
			return true;
		}
		if (valid && coalition.getTargetLostAt() != null) {
			coalition.setTargetLostAt(null);
			coalition.setTargetLossPausedMillis(0);
			notifyMembers(coalition, "§a" + target.getName() + " §ais independent again and remains the coalition's target.");
			return true;
		}
		if (coalition.getTargetLostAt() == null) return false;
		if (elapsed > 0 && CoalitionWars.activeCoalitionWar(coalition)) {
			coalition.setTargetLossPausedMillis(coalition.getTargetLossPausedMillis() + elapsed);
			return true;
		}
		if (targetLossRemaining(coalition).isZero()) {
			dissolve(coalition, "§cThe " + displayName(coalition) + " §ccoalition disbanded: it found no new target in time.", false);
			return true;
		}
		return false;
	}

	private static boolean refreshSingleMember(Coalition coalition, long now) {
		if (coalition.getMembers().size() > 1) {
			if (coalition.getSingleMemberSince() == null) return false;
			coalition.setSingleMemberSince(null);
			return true;
		}
		if (coalition.getSingleMemberSince() == null) {
			coalition.setSingleMemberSince(now);
			return true;
		}
		if (now < coalition.getSingleMemberSince() + CoalitionSettings.days(CoalitionSettings.singleMemberDays)) return false;
		dissolve(coalition, "§cThe " + displayName(coalition) + " §ccoalition disbanded: no other faction joined it.", false);
		return true;
	}

	private static void removeMember(Coalition coalition, Faction member, boolean cooldown) {
		coalition.removeMember(member.getId());
		if (cooldown) {
			CoalitionRegistry.setCooldown(member.getId(),
					CoalitionRegistry.now() + CoalitionSettings.days(CoalitionSettings.leaveCooldownDays));
		}
		if (coalition.getMembers().isEmpty()) {
			dissolve(coalition, null, false);
			return;
		}
		if (coalition.isLeader(member.getId())) succeed(coalition);
		refreshSingleMember(coalition, CoalitionRegistry.now());
		CoalitionRegistry.save();
	}

	/** The remaining member with the most realm prestige takes over. */
	private static void succeed(Coalition coalition) {
		Faction next = null;
		for (Faction member : members(coalition)) {
			if (next == null || CoalitionCost.realmPrestige(member) > CoalitionCost.realmPrestige(next)) next = member;
		}
		setLeader(coalition, next);
	}

	private static void setLeader(Coalition coalition, Faction member) {
		coalition.setLeaderId(member.getId());
		CoalitionRegistry.save();
		notifyMembers(coalition, "§e" + member.getName() + " §enow leads the " + displayName(coalition) + " §ecoalition.");
	}

	private static void dissolve(Coalition coalition, String message, boolean cooldown) {
		CoalitionRegistry.remove(coalition);
		long until = CoalitionRegistry.now() + CoalitionSettings.days(CoalitionSettings.leaveCooldownDays);
		for (String id : coalition.getMemberIds()) {
			if (cooldown) CoalitionRegistry.setCooldown(id, until);
		}
		if (message != null) notifyMembers(coalition, message);
		CoalitionRegistry.save();
		LogManager.relations("COALITION DISBAND %s", coalition.getId());
	}

	// ---- Messages ----

	public static void notifyMembers(Coalition coalition, String message) {
		for (Faction member : members(coalition)) notifyFaction(member, message);
	}

	/** Sends the message to every online member of the faction. */
	public static void notifyFaction(Faction faction, String message) {
		if (faction == null) return;
		for (String member : faction.getMembers()) {
			Player player = Members.player(member);
			if (player != null && player.isOnline()) player.sendMessage(message);
		}
	}

	/** Test hook: forgets when the timer last ran. */
	static void resetTimer() {
		lastTick = 0;
	}
}
