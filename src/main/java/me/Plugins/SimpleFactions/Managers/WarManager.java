package me.Plugins.SimpleFactions.Managers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Request.WarRequest;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.CallToArmsEligibility;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarCommitment;
import me.Plugins.SimpleFactions.War.core.WarDeclareHelper;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.combat.WarCombatTeardownService;
import me.Plugins.SimpleFactions.War.campaign.WarCampaignService;
import me.Plugins.SimpleFactions.War.commitment.WarCommitmentService;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignDeclareValidator;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignNavyGate;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarUntangleService;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.resolution.WarOutcomeService;
import me.Plugins.SimpleFactions.installation.WartimeInstallationService;
import me.Plugins.SimpleFactions.War.declare.WarDeclareRequest;
import me.Plugins.SimpleFactions.War.declare.WarGoalValidator;
import me.Plugins.SimpleFactions.War.declare.WarValidationResult;

public class WarManager {
	private static List<War> wars = new ArrayList<>();
	private static String lastDeclareError;
	
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
		WarCommitmentService.commitAllParticipants(war);
		addWar(war);
		logWarDeclared(war, attacker, defender);
		return war;
	}

	private static void logWarDeclared(War war, Faction attacker, Faction defender) {
		if (war == null) {
			return;
		}
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

	public static War addWar(War w) {
		wars.add(w);
		for(String m : w.getAttackers().getLeader().getMembers()){
			Player p = Bukkit.getPlayerExact(m);
			if(p != null && p.isOnline()){
				p.sendTitle("§cWar Declared!", "§e/war list §7to view", 10, 120, 10);
				p.playSound(p, Sound.ITEM_GOAT_HORN_SOUND_2, SoundCategory.MASTER, 10f, 0.6f);
			}
		}
		for(String m : w.getDefenders().getLeader().getMembers()){
			Player p = Bukkit.getPlayerExact(m);
			if(p != null && p.isOnline()){
				p.sendTitle("§cWar Declared!", "§e/war list §7to view", 10, 120, 10);
				p.playSound(p, Sound.ITEM_GOAT_HORN_SOUND_2, SoundCategory.MASTER, 10f, 0.6f);
			}
		}
		persist(w);
		return w;
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
	}

	public static void endWar(War w) {
		endWar(w, WarEndReason.ADMIN_END);
	}

	public static void endWar(War w, WarEndReason reason) {
		if (w == null || reason == null) {
			return;
		}
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
		notifyWarEnded(w, reason);
	}

	private static void notifyWarEnded(War w, WarEndReason reason) {
		String message = switch (reason) {
			case WHITE_PEACE -> "§7The war has ended in white peace.";
			case ATTACKER_VICTORY -> "§7The war has ended. The attacker coalition wins.";
			case DEFENDER_VICTORY -> "§7The war has ended. The defender coalition wins.";
			default -> "§7The war has ended.";
		};
		for (String member : w.getAttackers().getLeader().getMembers()) {
			Player p = Bukkit.getPlayerExact(member);
			if (p != null && p.isOnline()) {
				p.sendMessage(message);
			}
		}
		for (String member : w.getDefenders().getLeader().getMembers()) {
			Player p = Bukkit.getPlayerExact(member);
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
		while(getById(i) != null) i++;
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
		Player p = Bukkit.getPlayerExact(target.getLeader());
		if(p == null || !p.isOnline()) {
			sender.sendMessage("§cCannot send request, target faction leader is not online!");
			return;
		}
		sender.sendMessage("§aSent a call to arms to "+target.getName());
		p.sendMessage(FactionManager.getByLeader(sender.getName()).getName()+" §7is requesting that you aid them in their war against "+w.getEnemy(origin).getName());
		p.sendMessage("§7Type §a/faction accept §7to accept");
		p.sendMessage("§7Request will time out in 60 seconds");
		RequestManager.addRequest(sender, p, new WarRequest(FactionManager.getByLeader(sender.getName()).getOrCreateMainGuild(), w));
	}
	
	public static void acceptRequest(Player p) {
		WarRequest req = (WarRequest) RequestManager.getRequest(p);
		Faction reciever = FactionManager.getByLeader(p.getName());
		if(reciever == null) {
			p.sendMessage("§cYou do not have a faction");
			return;
		}
		Faction origin = req.getFaction();
		War war = req.getWar();
		if (!war.call(origin, reciever)) {
			p.sendMessage("§cCould not join the war.");
			return;
		}
		WarCommitmentService.commitFaction(war, reciever);
		WarCommitmentService.snapshotLevyForFighter(war, reciever);
		//A secondary only counts as a participant once it has joined, so contracts are re-checked here
		me.Plugins.SimpleFactions.mercenary.contract.MercenaryLoyaltyWatcher.onWarJoined(reciever);
		Player sp = Bukkit.getPlayerExact(origin.getLeader());
		if(sp != null && sp.isOnline()) sp.sendMessage(reciever.getName()+" §aaccepted your call to arms");
		p.sendMessage("§aYour faction has joined the "+war.getName());
		persist(war);
	}
}
