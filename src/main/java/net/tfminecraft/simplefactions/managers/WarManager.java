package net.tfminecraft.simplefactions.managers;

import net.tfminecraft.simplefactions.identity.Members;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.diplomacy.coalition.Coalition;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionRegistry;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionService;
import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionWars;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.WarRequest;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.core.CallToArmsEligibility;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.core.WarDeclareHelper;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.combat.WarCombatTeardownService;
import net.tfminecraft.simplefactions.war.campaign.WarCampaignService;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignDeclareValidator;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignNavyGate;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarUntangleService;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.resolution.WarOutcomeService;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.installation.WartimeInstallationService;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.declare.WarValidationResult;
import net.tfminecraft.simplefactions.war.truce.TruceService;

public class WarManager {
	private WarManager() {}
	private static List<War> wars = new ArrayList<>();
	private static String lastDeclareError;
	// The goat-horn enum needs a live registry, so tests can substitute this.
	static Consumer<Player> declaredHorn = WarManager::playDeclaredHorn;
	
	public static String getLastDeclareError() {
		return lastDeclareError;
	}
	
	public static War declareWar(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId) {
		return declareWar(attacker, defender, goal, targetTitleId, subjectFactionId, null);
	}

	public static War declareWar(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId) {
		return declareWar(attacker, defender, goal, targetTitleId, subjectFactionId, relationTypeId, null, null);
	}

	public static War declareWar(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId,
			String governmentLawId,
			String leadershipLawId) {
		return declareWar(
				attacker, defender, goal, targetTitleId, subjectFactionId, relationTypeId,
				governmentLawId, leadershipLawId, null);
	}

	public static War declareWar(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String targetTitleId,
			String subjectFactionId,
			String relationTypeId,
			String governmentLawId,
			String leadershipLawId,
			String targetSettlementId) {
		if (!Cache.provincesEnabled) {
			lastDeclareError = Cache.PROVINCES_DISABLED_MESSAGE;
			return null;
		}
		WarDeclareRequest request = new WarDeclareRequest(
				attacker, defender, goal, targetTitleId, subjectFactionId, relationTypeId,
				governmentLawId, leadershipLawId, targetSettlementId);
		WarValidationResult validation = new WarGoalValidator().validate(request);
		if (!validation.isValid()) {
			lastDeclareError = validation.getMessage();
			return null;
		}

		lastDeclareError = null;

		WarValidationResult military = CampaignDeclareValidator.validateAttackerCanDeclare(attacker);
		if (!military.isValid()) {
			lastDeclareError = military.getMessage();
			return null;
		}

		WarType warType = WarDeclareHelper.warTypeForGoal(goal);
		String storedTitleId = goal == WarGoalType.DE_JURE_ANNEX ? targetTitleId : null;
		War war = new War(
				newId(),
				new Side(attacker),
				new Side(defender),
				goal,
				warType,
				storedTitleId,
				null,
				Instant.now());
		if (goal == WarGoalType.TRANSFER_SUBJECT) {
			war.setSubjectFactionId(subjectFactionId);
		}
		if (goal == WarGoalType.SUBJUGATE) {
			war.setRelationTypeId(relationTypeId);
		}
		if (goal == WarGoalType.CHANGE_GOVERNMENT) {
			war.setGovernmentLawId(governmentLawId);
			war.setLeadershipLawId(leadershipLawId);
		}
		if (goal == WarGoalType.PILLAGE) {
			war.setTargetSettlementId(targetSettlementId);
		}
		if (!populateCampaignIfNeeded(war)) {
			lastDeclareError = "§cCould not declare war: no campaign route could be generated.";
			return null;
		}
		WarValidationResult navy = CampaignNavyGate.validateDeclareAfterPopulate(war);
		if (!navy.isValid()) {
			lastDeclareError = navy.getMessage();
			return null;
		}
		WarCommitmentService.commitAllParticipants(war);
		addWar(war);
		logWarDeclared(war, attacker, defender);
		CoalitionWars.announce(war);
		return war;
	}

	public static War startCivilWar(
			Faction attacker,
			Faction defender,
			WarGoalType goal,
			String movementId,
			List<Faction> extraAttackerMains,
			List<Faction> foreignBackers,
			CivilWarSnapshot snapshot) {
		lastDeclareError = null;
		if (!Cache.provincesEnabled) {
			lastDeclareError = Cache.PROVINCES_DISABLED_MESSAGE;
			return null;
		}
		if (attacker == null || defender == null || goal == null) {
			lastDeclareError = CivilWarCopy.COULD_NOT_START;
			return null;
		}

		WarType warType = WarDeclareHelper.warTypeForGoal(goal);
		Side attackers = new Side(attacker);
		Side defenders = new Side(defender);
		Participant leaderPart = attackers.getMainParticipants().get(0);
		if (extraAttackerMains != null) {
			for (Faction extra : extraAttackerMains) {
				if (extra == null || extra.getId() == null) {
					continue;
				}
				if (extra.getId().equalsIgnoreCase(attacker.getId())) {
					continue;
				}
				attackers.addNewParticipant(extra, leaderPart);
			}
		}
		if (foreignBackers != null) {
			for (Faction backer : foreignBackers) {
				if (backer == null || backer.getId() == null) {
					continue;
				}
				if (backer.getId().equalsIgnoreCase(attacker.getId())) {
					continue;
				}
				boolean extraMain = false;
				if (extraAttackerMains != null) {
					for (Faction extra : extraAttackerMains) {
						if (extra != null
								&& extra.getId() != null
								&& extra.getId().equalsIgnoreCase(backer.getId())) {
							extraMain = true;
							break;
						}
					}
				}
				if (extraMain) {
					continue;
				}
				leaderPart.addBacker(backer);
			}
		}
		for (Participant participant : attackers.getMainParticipants()) {
			participant.setCivilWar(true);
		}
		for (Participant participant : defenders.getMainParticipants()) {
			participant.setCivilWar(true);
		}

		War war = new War(
				newId(),
				attackers,
				defenders,
				goal,
				warType,
				null,
				null,
				Instant.now());
		war.setMovementId(movementId);
		if (snapshot != null) {
			war.setCivilWarSnapshot(snapshot);
		}
		if (!populateCampaignIfNeeded(war)) {
			lastDeclareError = "§cCould not declare war: no campaign route could be generated.";
			return null;
		}
		WarValidationResult navy = CampaignNavyGate.validateDeclareAfterPopulate(war);
		if (!navy.isValid()) {
			lastDeclareError = navy.getMessage();
			return null;
		}
		// Anything that touched the rebels since the regiment split may have handed their law slots back.
		// Register the snapshot before refreshing, so the military recognizes the active rebellion.
		wars.add(war);
		refreshTempRebelSlots(war);
		WarCommitmentService.commitAllParticipants(war);
		announceAndPersist(war);
		logWarDeclared(war, attacker, defender);
		return war;
	}

	private static void logWarDeclared(War war, Faction attacker, Faction defender) {
		LogManager.war(
				"DECLARE warId=%d type=%s goal=%s attacker=%s defender=%s movementId=%s",
				war.getId(),
				war.getWarType(),
				war.getGoal(),
				attacker != null ? attacker.getId() : "-",
				defender != null ? defender.getId() : "-",
				war.getMovementId() != null ? war.getMovementId() : "-");
	}

	static boolean populateCampaignIfNeeded(War war) {
		if (war.getWarType() == WarType.RAID) {
			return true;
		}
		ProvinceManager pm = resolveProvinceManager();
		if (pm == null) {
			return false;
		}
		return new WarCampaignService(pm).populateCampaign(war);
	}

	public static boolean regenerateCampaign(War war) {
		ProvinceManager pm = resolveProvinceManager();
		if (pm == null) {
			return false;
		}
		return regenerateCampaign(war, pm);
	}

	static boolean regenerateCampaign(War war, ProvinceManager pm) {
		if (war == null || !war.isActive() || pm == null) {
			return false;
		}
		if (war.getWarType() == WarType.RAID) {
			return false;
		}
		if (!new WarCampaignService(pm).populateCampaign(war)) {
			return false;
		}
		persist(war);
		return true;
	}

	private static ProvinceManager resolveProvinceManager() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin == null) {
			return null;
		}
		return plugin.getProvinceManager();
	}

	public static List<WarCommitment> getCommitmentsForWar(int warId) {
		return WarCommitmentService.getCommitmentsForWar(warId);
	}

	static void clearCommitments(int warId) {
		WarCommitmentService.clearCommitments(warId);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	public static War addWar(War w) {
		wars.add(w);
		announceAndPersist(w);
		return w;
	}

	private static void announceAndPersist(War w) {
		notifyWarDeclared(BattleSideMembers.collectEligibleMemberNames(w.getAttackers()));
		notifyWarDeclared(BattleSideMembers.collectEligibleMemberNames(w.getDefenders()));
		persist(w);
	}

	@SuppressWarnings("deprecation")
	private static void notifyWarDeclared(Iterable<String> members) {
		if (members == null) return;
		for (String member : members) {
			if (member == null || member.isBlank()) continue;
			Player p = Members.player(member);
			if (p != null && p.isOnline()) {
				p.sendTitle("§cWar Declared!", "§e/war list §7to view", 10, 120, 10);
				declaredHorn.accept(p);
			}
		}
	}

	private static void playDeclaredHorn(Player player) {
		player.playSound(player, Sound.ITEM_GOAT_HORN_SOUND_2, SoundCategory.MASTER, 10f, 0.6f);
	}

	public static void persist(War war) {
		new Database().saveWar(war);
	}
	
	public static War findSharedActiveWar(Faction a, Faction b) {
		if (a == null || b == null) {
			return null;
		}
		for (War war : wars) {
			if (!war.isActive()) {
				continue;
			}
			if (war.isParticipating(a) && war.isParticipating(b)) {
				return war;
			}
		}
		return null;
	}

	public static boolean isAtWar(Faction f) {
		if (f == null) {
			return false;
		}
		for (War war : wars) {
			if (war.isActive() && war.isParticipating(f)) {
				return true;
			}
		}
		return false;
	}

	public static boolean exists(Faction attacker, Faction defender) {
		return findSharedActiveWar(attacker, defender) != null;
	}

	public static boolean existsHostile(Faction attacker, Faction defender) {
		War war = findSharedActiveWar(attacker, defender);
		if (war == null) {
			return false;
		}
		Side attackerSide = war.getSide(attacker);
		Side defenderSide = war.getSide(defender);
		return attackerSide != null && defenderSide != null && !attackerSide.equals(defenderSide);
	}

	public static void start() {
		wars = (new Database()).loadWars();
		// Factions loaded before the wars, so civil war rebels were just given their law slots.
		for (War war : getActive()) {
			refreshTempRebelSlots(war);
		}
	}

	private static void refreshTempRebelSlots(War war) {
		if (war.getCivilWarSnapshot() == null) return;
		Faction rebels = FactionManager.getByString(war.getCivilWarSnapshot().getTempRebelFactionId());
		if (rebels != null && rebels.getMilitary() != null) rebels.getMilitary().refreshLawSlots();
	}

	public static void endWar(War w) {
		endWar(w, WarEndReason.ADMIN_END);
	}

	public static void endWar(War w, WarEndReason reason) {
		if (w == null || reason == null) {
			return;
		}
		// Read the sides while the war is still active and the rebels still exist.
		TruceService.onWarEnded(w, reason);
		FactionManager.getMap().enqueueOccupationFromWar(w);
		WartimeInstallationService.revert(w);
		CivilWarUntangleService.restore(w, reason);
		WarOutcomeService.apply(w, reason);
		WarCombatTeardownService.teardownCombatForWar(w);
		w.end(reason);
		BattleInstallationPickService.clearForNewBattleDay(w);
		CampaignRaidService.clearForNewBattleDay(w);
		clearCommitments(w.getId());
		for (War war : wars) {
			if (war.getId() == w.getId()) {
				wars.remove(war);
				(new Database()).deleteWar(w);
				break;
			}
		}
		refreshTempRebelSlots(w);
		notifyWarEnded(w, reason);
	}

	private static void notifyWarEnded(War w, WarEndReason reason) {
		String message = switch (reason) {
			case WHITE_PEACE -> "§7The war has ended in white peace.";
			case ATTACKER_VICTORY -> "§7The war has ended. The attacking side wins.";
			case DEFENDER_VICTORY -> "§7The war has ended. The defending side wins.";
			default -> "§7The war has ended.";
		};
		LinkedHashSet<String> members = new LinkedHashSet<>();
		members.addAll(BattleSideMembers.collectEligibleMemberNames(w.getAttackers()));
		members.addAll(BattleSideMembers.collectEligibleMemberNames(w.getDefenders()));
		for (String member : members) {
			Player p = Members.player(member);
			if (p != null && p.isOnline()) {
				p.sendMessage(message);
			}
		}
	}

	public static List<War> getActive() {
		List<War> active = new ArrayList<>();
		for (War war : wars) {
			if (war.isActive()) active.add(war);
		}
		return active;
	}
	
	public static List<War> get(){
		return wars;
	}
	
	public static War getById(int i) {
		for(War w : wars) {
			if(w.getId() == i) return w;
		}
		return null;
	}
	
	public static int newId() {
		int i = 0;
		while(getById(i) != null || new java.io.File("plugins/SimpleFactions/Wars", "war_" + i + ".json").exists()) i++;
		return i;
	}
	
	public static War getByFaction(Faction f) {
		for(War w : wars) {
			if(w.getSide(f) != null) return w;
		}
		return null;
	}
	
	public static void sendRequest(Player sender, Faction origin, Faction target, War w) {
		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(w, origin, target);
		if(!result.allowed()) {
			sender.sendMessage(result.message());
			return;
		}
		Player p = Members.player(target.getLeader());
		if(p == null || !p.isOnline()) {
			sender.sendMessage("§cCannot send request, target faction leader is not online!");
			return;
		}
		// A second request must not silently replace one the leader is still reading.
		if(RequestManager.hasRequest(p)) {
			sender.sendMessage("§cThat leader already has a pending request.");
			return;
		}
		// An ally is always called as an ally; only a non-ally answers as a coalition member.
		Coalition coalition = RelationManager.getAllies(origin).contains(target)
				? null : CoalitionWars.callingCoalition(w, origin, target);
		sender.sendMessage("§aSent a call to arms to "+target.getName());
		p.sendMessage(FactionManager.getByLeader(Members.key(sender)).getName()+" §7is requesting that you aid them in their war against "+w.getEnemy(origin).getName());
		if(coalition != null) {
			p.sendMessage("§7They call on you as a fellow member of the "+CoalitionService.displayName(coalition)+" §7coalition. Declining costs more stability than refusing an ally.");
		}
		String warning = CoalitionWars.acceptWarning(w, origin, target);
		if(warning != null) p.sendMessage(warning);
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Type §c/faction decline §7to decline");
		p.sendMessage("§7Request will time out in "+WarRequest.expiryText());
		RequestManager.addRequest(sender, p, new WarRequest(FactionManager.getByLeader(Members.key(sender)).getOrCreateMainGuild(), w, target.getId(),
				coalition == null ? null : coalition.getId()));
	}
	
	public static void acceptRequest(Player p) {
		if (!(RequestManager.getRequest(p) instanceof WarRequest req)) {
			return;
		}
		Faction reciever = FactionManager.getByLeader(Members.key(p));
		if(reciever == null) {
			p.sendMessage("§cYou do not have a faction");
			return;
		}
		Faction origin = req.getFaction();
		War war = req.getWar();
		String requestedFaction = req.getTargetFactionId();
		if (war == null || !war.isActive() || getById(war.getId()) != war
				|| origin == null || FactionManager.getByString(origin.getId()) != origin
				|| FactionManager.getByString(reciever.getId()) != reciever
				|| (requestedFaction != null && !requestedFaction.equalsIgnoreCase(reciever.getId()))
				|| !(RelationManager.getAllies(origin).contains(reciever) || CoalitionWars.canCallMember(war, origin, reciever))
				|| !CallToArmsEligibility.canCall(war, origin, reciever).allowed()
				|| !war.call(origin, reciever)) {
			p.sendMessage("§cCould not join the war.");
			return;
		}
		WarCommitmentService.commitFaction(war, reciever);
		WarCommitmentService.snapshotLevyForFighter(war, reciever);
		//A secondary only counts as a participant once it has joined, so contracts are re-checked here
		net.tfminecraft.simplefactions.mercenary.contract.MercenaryLoyaltyWatcher.onWarJoined(reciever);
		Player sp = Members.player(origin.getLeader());
		if(sp != null && sp.isOnline()) sp.sendMessage(reciever.getName()+" §aaccepted your call to arms");
		p.sendMessage("§aYour faction has joined the "+war.getName());
		notifyWarDeclared(reciever.getMembers());
		persist(war);
		// The joiner may be a coalition's target, which lets that coalition in.
		CoalitionWars.announce(war);
	}

	// Refused call to arms. Decay matches the war-end hits in WarOutcomeService.
	public static void declineCallToArms(Player called, WarRequest req) {
		if (req == null) return;
		if (called != null && called.isOnline()) {
			called.sendMessage("§7You declined the call to arms.");
		}
		// No penalty for refusing a war that has already ended.
		if (req.getWar() == null || !req.getWar().isActive()) return;
		Faction receiver = calledFaction(called, req);
		if (receiver != null && receiver.getGovernment() != null) {
			receiver.getGovernment().addStabilityModifier(new StabilityModifier(
					"Declined Call to Arms",
					req.isCoalitionCall() ? Cache.warDeclinedCoalitionStabilityPenalty : Cache.warDeclinedAllyStabilityPenalty,
					1));
		}
		String who = receiver != null ? receiver.getName() : "They";
		tellCaller(req, who + " §cdeclined your call to arms");
		Coalition coalition = CoalitionRegistry.get(req.getCoalitionId());
		Faction coalitionLeader = coalition == null ? null : CoalitionService.leader(coalition);
		if (coalitionLeader != null && req.getFaction() != null
				&& !coalitionLeader.getId().equalsIgnoreCase(req.getFaction().getId())) {
			CoalitionService.notifyFaction(coalitionLeader, who + " §cdeclined a coalition call to arms from "
					+ req.getFaction().getName() + "§c.");
		}
	}

	// A call to arms that runs out is not a decline: nothing is lost and the caller may send it again.
	public static void expireCallToArms(Player called, WarRequest req) {
		if (req == null) return;
		if (called != null && called.isOnline()) {
			called.sendMessage("§7A call to arms expired without an answer.");
		}
		if (req.getWar() == null || !req.getWar().isActive()) return;
		Faction receiver = calledFaction(called, req);
		String who = receiver != null ? receiver.getName() : "your ally";
		tellCaller(req, "§7Your call to arms to " + who + " §7expired. You can send it again.");
	}

	// Requests made before the target was stored fall back to the called leader's faction.
	// A stored target that no longer exists penalises nobody.
	private static Faction calledFaction(Player called, WarRequest req) {
		if (req.getTargetFactionId() != null) return FactionManager.getByString(req.getTargetFactionId());
		return called != null ? FactionManager.getByLeader(Members.key(called)) : null;
	}

	private static void tellCaller(WarRequest req, String message) {
		Faction origin = req.getFaction();
		if (origin == null || origin.getLeader() == null) return;
		Player caller = Members.player(origin.getLeader());
		if (caller != null && caller.isOnline()) caller.sendMessage(message);
	}
}
