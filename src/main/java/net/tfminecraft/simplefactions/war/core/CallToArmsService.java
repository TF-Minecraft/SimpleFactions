package net.tfminecraft.simplefactions.war.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.diplomacy.coalition.Coalition;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionRegistry;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionService;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionWars;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryLoyaltyWatcher;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.notice.WarNotices;

/**
 * Calls to arms are stored on the war and stay open until the called leader answers or the war
 * ends, so a leader who is offline can still be called and an ally can join late. Only factions
 * that were allies or coalition partners of a participant when the war was declared can be called.
 */
public final class CallToArmsService {
	public static final String NOT_ALLIED_AT_DECLARATION =
			"§cOnly factions allied when the war was declared can be called to arms.";

	/** An open call and the war it belongs to. */
	public record Pending(War war, CallToArms call) {
	}

	private CallToArmsService() {
	}

	/** Records who could be called when the war was declared: every ally and coalition partner of a participant. */
	public static void snapshotCallable(War war) {
		if (war == null) {
			return;
		}
		Set<String> ids = new LinkedHashSet<>();
		List<Faction> participants = new ArrayList<>();
		participants.addAll(BattleSideMembers.collectParticipatingFactions(war.getAttackers()));
		participants.addAll(BattleSideMembers.collectParticipatingFactions(war.getDefenders()));
		for (Faction faction : participants) {
			for (Faction ally : RelationManager.getAllies(faction)) {
				addId(ids, ally);
			}
			Coalition coalition = CoalitionService.of(faction);
			if (coalition != null) {
				for (Faction member : CoalitionService.members(coalition)) {
					addId(ids, member);
				}
			}
		}
		war.setCallableFactionIds(ids);
	}

	/** Null when the target could have been called at declaration; wars from before the snapshot allow anyone. */
	public static String notCallableReason(War war, Faction target) {
		Set<String> callable = war.getCallableFactionIds();
		if (callable == null || target == null || target.getId() == null) {
			return null;
		}
		return callable.contains(key(target)) ? null : NOT_ALLIED_AT_DECLARATION;
	}

	public static void send(Player sender, Faction origin, Faction target, War war) {
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(war, origin, target);
		if (!result.allowed()) {
			sender.sendMessage(result.message());
			return;
		}
		if (war.getCallsToArms().containsKey(key(target))) {
			sender.sendMessage("§c" + target.getName() + " §calready has a call to arms in this war.");
			return;
		}
		// An ally is always called as an ally; only a non-ally answers as a coalition member.
		Coalition coalition = RelationManager.getAllies(origin).contains(target)
				? null : CoalitionWars.callingCoalition(war, origin, target);
		war.getCallsToArms().put(key(target), new CallToArms(target.getId(), origin.getId(),
				coalition == null ? null : coalition.getId(), Instant.now()));
		WarManager.persist(war);
		sender.sendMessage("§aSent a call to arms to " + target.getName()
				+ "§a. It stays open until their leader answers.");
		String leader = target.getLeader();
		WarNotices.send(leader, origin.getName() + " §7calls on you to join their war against "
				+ war.getEnemy(origin).getName() + "§7.");
		if (coalition != null) {
			WarNotices.send(leader, "§7They call on you as a fellow member of the "
					+ CoalitionService.displayName(coalition)
					+ " §7coalition. Declining costs more stability than refusing an ally.");
		}
		String warning = CoalitionWars.acceptWarning(war, origin, target);
		if (warning != null) {
			WarNotices.send(leader, warning);
		}
		WarNotices.send(leader, "§7Answer with §a/war call accept " + war.getId()
				+ " §7or §c/war call decline " + war.getId() + "§7. §e/war calls §7lists every open call.");
	}

	/** Open calls to the faction this player leads, oldest first. */
	public static List<Pending> pendingFor(Player player) {
		Faction faction = player == null ? null : FactionManager.getByLeader(Members.key(player));
		List<Pending> pending = new ArrayList<>();
		if (faction == null || faction.getId() == null) {
			return pending;
		}
		for (War war : WarManager.getActive()) {
			CallToArms call = war.getCallsToArms().get(key(faction));
			if (call != null) {
				pending.add(new Pending(war, call));
			}
		}
		return pending;
	}

	public static void list(Player player) {
		List<Pending> pending = pendingFor(player);
		if (pending.isEmpty()) {
			player.sendMessage("§7Your faction has no open calls to arms.");
			return;
		}
		player.sendMessage("§6Open calls to arms:");
		for (Pending entry : pending) {
			Faction caller = FactionManager.getByString(entry.call().callerFactionId());
			String callerName = caller != null ? caller.getName() : entry.call().callerFactionId();
			player.sendMessage("§e#" + entry.war().getId() + " §f" + entry.war().getName() + "§7: called by "
					+ callerName + "§7. §a/war call accept " + entry.war().getId()
					+ " §7or §c/war call decline " + entry.war().getId());
		}
	}

	/** {@code /faction accept} with no other request answers the only open call, if there is one. */
	public static boolean answerOnly(Player player, boolean accept) {
		List<Pending> pending = pendingFor(player);
		if (pending.isEmpty()) {
			return false;
		}
		if (pending.size() > 1) {
			list(player);
			return true;
		}
		War war = pending.get(0).war();
		if (accept) {
			accept(player, war);
		} else {
			decline(player, war);
		}
		return true;
	}

	public static void accept(Player player, War war) {
		Faction receiver = FactionManager.getByLeader(Members.key(player));
		CallToArms call = takeCall(player, receiver, war);
		if (call == null) {
			return;
		}
		Faction origin = FactionManager.getByString(call.callerFactionId());
		// An ended or replaced war object must not be saved again: that would bring back its file.
		boolean current = war.isActive() && WarManager.getById(war.getId()) == war;
		if (!current
				|| origin == null
				|| !(RelationManager.getAllies(origin).contains(receiver) || CoalitionWars.canCallMember(war, origin, receiver))
				|| !CallToArmsEligibility.canCall(war, origin, receiver).allowed()
				|| !war.call(origin, receiver)) {
			if (current) {
				WarManager.persist(war);
			}
			player.sendMessage("§cCould not join the war.");
			return;
		}
		WarCommitmentService.commitFaction(war, receiver);
		WarCommitmentService.snapshotLevyForFighter(war, receiver);
		// A secondary only counts as a participant once it has joined, so contracts are re-checked here.
		MercenaryLoyaltyWatcher.onWarJoined(receiver);
		WarNotices.send(origin.getLeader(), receiver.getName() + " §aaccepted your call to arms: "
				+ war.getName() + "§a.");
		player.sendMessage("§aYour faction has joined the war: " + war.getName());
		WarManager.notifyWarDeclared(receiver.getMembers());
		WarManager.persist(war);
		// The joiner may be a coalition's target, which lets that coalition in.
		CoalitionWars.announce(war);
	}

	/** Refused call to arms. Decay matches the war-end hits in WarOutcomeService. */
	public static void decline(Player player, War war) {
		Faction receiver = FactionManager.getByLeader(Members.key(player));
		CallToArms call = takeCall(player, receiver, war);
		if (call == null) {
			return;
		}
		WarManager.persist(war);
		player.sendMessage("§7You declined the call to arms.");
		if (receiver.getGovernment() != null) {
			receiver.getGovernment().addStabilityModifier(new StabilityModifier(
					"Declined Call to Arms",
					call.isCoalitionCall() ? Cache.warDeclinedCoalitionStabilityPenalty : Cache.warDeclinedAllyStabilityPenalty,
					1));
		}
		Faction origin = FactionManager.getByString(call.callerFactionId());
		if (origin != null) {
			WarNotices.send(origin.getLeader(), receiver.getName() + " §cdeclined your call to arms: "
					+ war.getName() + "§c.");
		}
		Coalition coalition = CoalitionRegistry.get(call.coalitionId());
		Faction coalitionLeader = coalition == null ? null : CoalitionService.leader(coalition);
		if (coalitionLeader != null && origin != null
				&& !coalitionLeader.getId().equalsIgnoreCase(origin.getId())) {
			CoalitionService.notifyFaction(coalitionLeader, receiver.getName()
					+ " §cdeclined a coalition call to arms from " + origin.getName() + "§c.");
		}
	}

	private static CallToArms takeCall(Player player, Faction receiver, War war) {
		if (receiver == null) {
			player.sendMessage("§cOnly a faction leader can answer a call to arms.");
			return null;
		}
		CallToArms call = war == null ? null : war.getCallsToArms().remove(key(receiver));
		if (call == null) {
			player.sendMessage("§cYour faction has no call to arms in that war.");
		}
		return call;
	}

	private static void addId(Set<String> ids, Faction faction) {
		if (faction != null && faction.getId() != null) {
			ids.add(key(faction));
		}
	}

	private static String key(Faction faction) {
		return faction.getId().toLowerCase(Locale.ROOT);
	}
}
