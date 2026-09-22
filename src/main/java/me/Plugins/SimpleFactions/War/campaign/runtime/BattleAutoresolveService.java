package me.Plugins.SimpleFactions.War.campaign.runtime;

import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RequestManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Request.AutoresolveRequest;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleLaunchService;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;

public final class BattleAutoresolveService {
	public enum SendResult {
		SENT,
		NOT_ALLOWED,
		OPPOSING_LEADER_OFFLINE
	}

	private BattleAutoresolveService() {}

	public static boolean canProposeAutoresolveNow(War war, Instant now) {
		if (war == null || !war.isActive() || now == null) {
			return false;
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return false;
		}
		return BattleScheduleService.isBeforeVoteClose(war, now);
	}

	public static SendResult sendProposeRequest(Player proposer, War war, BelligerentRole side) {
		Instant now = CampaignClock.now();
		if (!canProposeAutoresolveNow(war, now) || side == null) {
			return SendResult.NOT_ALLOWED;
		}

		Faction proposerFaction = FactionManager.getByLeader(proposer.getName());
		if (proposerFaction == null || !isWarLeader(proposerFaction, war, side)) {
			return SendResult.NOT_ALLOWED;
		}

		Faction opposingLeaderFaction = opposingLeaderFaction(war, side);
		if (opposingLeaderFaction == null) {
			return SendResult.NOT_ALLOWED;
		}

		Player target = Bukkit.getPlayerExact(opposingLeaderFaction.getLeader());
		if (target == null || !target.isOnline()) {
			return SendResult.OPPOSING_LEADER_OFFLINE;
		}

		proposer.sendMessage("§aSent autoresolve request to " + opposingLeaderFaction.getName());
		target.sendMessage(proposerFaction.getName() + " §7requests autoresolving today's battle vote");
		target.sendMessage("§7Type §a/faction accept §7to accept");
		target.sendMessage("§7Request will time out in 60 seconds");
		RequestManager.addRequest(proposer, target, new AutoresolveRequest(
				proposerFaction.getOrCreateMainGuild(),
				war,
				side));
		return SendResult.SENT;
	}

	public static void acceptRequest(Player acceptor) {
		if (!(RequestManager.getRequest(acceptor) instanceof AutoresolveRequest req)) {
			return;
		}

		War war = WarManager.getById(req.getWar().getId());
		if (war == null || !war.isActive()) {
			acceptor.sendMessage("§cThat war is no longer active.");
			return;
		}

		BelligerentRole opposingSide = req.getProposerSide() == BelligerentRole.ATTACKER
				? BelligerentRole.DEFENDER
				: BelligerentRole.ATTACKER;
		Faction acceptorFaction = FactionManager.getByLeader(acceptor.getName());
		if (acceptorFaction == null || !isWarLeader(acceptorFaction, war, opposingSide)) {
			acceptor.sendMessage("§cYou cannot accept this request.");
			return;
		}

		if (!canProposeAutoresolveNow(war, CampaignClock.now())) {
			acceptor.sendMessage("§cAutoresolve is only available before vote close.");
			return;
		}

		BattleScheduleService.enterAutoresolvePending(war);
		me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleLaunchService.launchAutoresolveBattle(war);
		WarManager.persist(war);

		Faction proposerFaction = req.getFaction();
		if (proposerFaction != null) {
			Player proposer = Bukkit.getPlayerExact(proposerFaction.getLeader());
			if (proposer != null && proposer.isOnline()) {
				proposer.sendMessage(acceptorFaction.getName() + " §aaccepted autoresolve");
			}
		}
		acceptor.sendMessage("§aBattle vote autoresolve accepted.");
	}

	private static Faction opposingLeaderFaction(War war, BelligerentRole proposerSide) {
		if (war == null || proposerSide == null) {
			return null;
		}
		return proposerSide == BelligerentRole.ATTACKER
				? war.getDefenders().getLeader()
				: war.getAttackers().getLeader();
	}

	private static boolean isWarLeader(Faction faction, War war, BelligerentRole side) {
		if (faction == null || war == null || side == null) {
			return false;
		}
		String leaderId = side == BelligerentRole.ATTACKER
				? war.getAttackerLeaderId()
				: war.getDefenderLeaderId();
		return faction.getId().equalsIgnoreCase(leaderId);
	}
}
