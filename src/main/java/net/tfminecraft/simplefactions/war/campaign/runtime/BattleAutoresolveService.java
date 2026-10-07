package net.tfminecraft.simplefactions.war.campaign.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.AutoresolveRequest;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleOutcomeService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyService;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.military.BattlePoolService;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.progression.AttackerNavalContestService;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

public final class BattleAutoresolveService {
	public enum SendResult {
		SENT,
		NOT_ALLOWED,
		OPPOSING_LEADER_OFFLINE
	}

	/**
	 * Lanchester result. Deaths are lives lost; regiment losses use
	 * {@link BattleCasualtyService#regimentLossesForDeaths}.
	 * Offensive is the coalition on the attack for this battle, not always the war attacker.
	 */
	public record Prediction(
			boolean offensiveWins,
			int offensiveDeaths,
			int defensiveDeaths,
			int offensiveRegimentLosses,
			int defensiveRegimentLosses) {}

	private static Random randomOverride;

	private BattleAutoresolveService() {}

	static void setRandomForTests(Random random) {
		randomOverride = random;
	}

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

		War war = req.getWar();
		if (war == null || !war.isActive() || WarManager.getById(war.getId()) != war) {
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

		if (!resolve(war)) {
			acceptor.sendMessage("§cAutoresolve could not find a battle to resolve.");
			return;
		}
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

	public static boolean resolve(War war) {
		return resolve(war, randomOverride != null ? randomOverride : ThreadLocalRandom.current());
	}

	public static boolean resolve(War war, Random random) {
		if (war == null || !war.isActive()) {
			return false;
		}
		Battle prepared = BattleManager.getByWarId(war.getId());
		if (prepared != null && prepared.hasStarted()) {
			return false;
		}
		Integer provinceId = BattleScheduleService.resolveScheduledProvinceId(war);
		if (provinceId == null) {
			return false;
		}
		if (AttackerNavalContestService.applyIfAttackerHasNoBerthedNavy(war, provinceId)) {
			clearAutoresolveFlags(war);
			war.setPostponementsThisCycle(0);
			return true;
		}
		war.setFirstBattleStarted(true);
		WarManager.persist(war);

		CampaignCoalition offensive = CampaignCapabilityService.battleOffensiveCoalition(war);
		CampaignCoalition defensive = offensive.opposing();
		Side offensiveSide = CampaignCoalitionService.toSide(war, offensive);
		Side defensiveSide = CampaignCoalitionService.toSide(war, defensive);
		int livesPerRegiment = Math.max(0, Cache.warBattleLivesPerRegiment);
		// Mercenary slots only count members on a battle roster. There is no roster here, so they are omitted.
		double offensiveLives = (double) livesPerRegiment * committedRegiments(war, provinceId, offensiveSide);
		double defensiveLives = (double) livesPerRegiment * committedRegiments(war, provinceId, defensiveSide);
		Prediction prediction = predict(
				offensiveLives,
				defensiveLives,
				Cache.warAutoresolveLuck,
				Cache.warAutoresolveLoserLossFraction,
				random);

		CampaignCoalition winner = prediction.offensiveWins() ? offensive : defensive;
		clearAutoresolveFlags(war);
		war.setPostponementsThisCycle(0);
		// The offensive is pinned so fuel is charged to the side that attacked, even if the
		// casualties change which coalition would be on the offensive next.
		CampaignBattleOutcomeService.applyCampaignBattleOutcome(
				war,
				CampaignCoalitionService.coalitionToBelligerentRole(winner),
				provinceId,
				casualtyCarrier(war, provinceId, offensive),
				Map.of(
						BattleTemplate.ATTACKER_SIDE, prediction.offensiveDeaths(),
						BattleTemplate.DEFENDER_SIDE, prediction.defensiveDeaths()),
				offensive);
		if (prepared != null) {
			BattlePersistenceService.deleteCampaignBattle(prepared);
		}
		broadcastResult(war, provinceId, winner, prediction);
		return true;
	}

	public static Prediction predict(
			int offensiveLives,
			int defensiveLives,
			double luck,
			double loserLossFraction,
			Random random) {
		return predict((double) offensiveLives, defensiveLives, luck, loserLossFraction, random);
	}

	private static Prediction predict(
			double offensiveLives, double defensiveLives, double luck, double loserLossFraction, Random random) {
		double offensiveReal = Math.max(0, offensiveLives);
		double defensiveReal = Math.max(0, defensiveLives);
		double luckSpan = clampLuck(luck);
		double fraction = Math.max(0, Math.min(1, loserLossFraction));
		if (offensiveReal == 0 || defensiveReal == 0) {
			boolean offensiveWins = offensiveReal > 0;
			return new Prediction(offensiveWins, 0, 0, 0, 0);
		}

		double offensiveEffective = offensiveReal * luckFactor(random, luckSpan);
		double defensiveEffective = defensiveReal * luckFactor(random, luckSpan);
		boolean offensiveWins = offensiveEffective > defensiveEffective;
		double winnerEffective = offensiveWins ? offensiveEffective : defensiveEffective;
		double loserEffective = offensiveWins ? defensiveEffective : offensiveEffective;
		double winnerReal = offensiveWins ? offensiveReal : defensiveReal;
		double loserReal = offensiveWins ? defensiveReal : offensiveReal;
		int winnerDeaths = lanchesterWinnerDeaths(winnerEffective, loserEffective, winnerReal);
		int loserDeaths = (int) Math.min(Integer.MAX_VALUE, Math.round(loserReal * fraction));
		int offensiveDeaths = offensiveWins ? winnerDeaths : loserDeaths;
		int defensiveDeaths = offensiveWins ? loserDeaths : winnerDeaths;
		return new Prediction(
				offensiveWins,
				offensiveDeaths,
				defensiveDeaths,
				BattleCasualtyService.regimentLossesForDeaths(offensiveDeaths),
				BattleCasualtyService.regimentLossesForDeaths(defensiveDeaths));
	}

	private static int lanchesterWinnerDeaths(double winnerEffective, double loserEffective, double winnerRealLives) {
		double gap = winnerEffective * winnerEffective - loserEffective * loserEffective;
		long deaths = Math.round(winnerEffective - Math.sqrt(gap));
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.min(winnerRealLives, deaths)));
	}

	private static double clampLuck(double luck) {
		if (luck < 0) {
			return 0;
		}
		if (luck > 1) {
			return 1;
		}
		return luck;
	}

	/** Uniform factor in {@code [1 - luck, 1 + luck)}. A luck of 0 is exactly 1. */
	private static double luckFactor(Random random, double luck) {
		if (luck <= 0) {
			return 1.0;
		}
		double roll = random == null ? 0.5 : random.nextDouble();
		return (1.0 - luck) + roll * (2.0 * luck);
	}

	private static int committedRegiments(War war, int provinceId, Side side) {
		return Math.max(0, BattlePoolService.totalCommittedRegiments(war, provinceId, side));
	}

	/** Unregistered battle so casualties key to the offensive coalition. It is not started. */
	private static Battle casualtyCarrier(War war, int provinceId, CampaignCoalition offensive) {
		ScheduledCampaignBattle slot = CampaignScheduleService.slotAtActiveIndex(war).orElse(null);
		BattleType type = slot != null ? slot.battleType() : BattleType.FIELD;
		Battle carrier = new Battle("autoresolve-" + war.getId());
		carrier.setWarId(war.getId());
		carrier.setProvinceId(provinceId);
		carrier.setBattleType(type);
		carrier.setOffensiveCoalition(offensive);
		return carrier;
	}

	private static void clearAutoresolveFlags(War war) {
		war.setAutoresolveProposedByAttacker(false);
		war.setAutoresolveProposedByDefender(false);
	}

	private static void broadcastResult(
			War war,
			int provinceId,
			CampaignCoalition winner,
			Prediction prediction) {
		String message = "§7Autoresolved " + battleName(war, provinceId)
				+ ": §e" + winnerLabel(war, winner)
				+ " §7won. Losses: attacker " + prediction.offensiveRegimentLosses()
				+ ", defender " + prediction.defensiveRegimentLosses()
				+ " regiments.";
		sendToSide(war.getAttackers(), message);
		sendToSide(war.getDefenders(), message);
	}

	private static String battleName(War war, int provinceId) {
		ScheduledCampaignBattle slot = CampaignScheduleService.slotAtActiveIndex(war).orElse(null);
		if (slot == null) {
			return BattleNamingService.buildDisplayName(
					BattleType.FIELD,
					BattleNamingService.resolveLocationDisplayName(provinceId),
					1);
		}
		return BattleNamingService.resolveScheduledDisplayName(
				war,
				CampaignScheduleService.activeLeg(war),
				CampaignScheduleService.getActiveScheduleIndex(war),
				slot,
				provinceId);
	}

	private static String winnerLabel(War war, CampaignCoalition winner) {
		Side side = CampaignCoalitionService.toSide(war, winner);
		if (side != null && side.getLeader() != null) {
			String name = side.getLeader().getName();
			if (name != null && !name.isBlank()) {
				return name;
			}
		}
		return winner == CampaignCoalition.AGGRESSOR ? "Attacker" : "Defender";
	}

	private static void sendToSide(Side side, String message) {
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(side)) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}

	private static Faction opposingLeaderFaction(War war, BelligerentRole proposerSide) {
		return proposerSide == BelligerentRole.ATTACKER
				? war.getDefenders().getLeader()
				: war.getAttackers().getLeader();
	}

	private static boolean isWarLeader(Faction faction, War war, BelligerentRole side) {
		String leaderId = side == BelligerentRole.ATTACKER
				? war.getAttackerLeaderId()
				: war.getDefenderLeaderId();
		return faction.getId().equalsIgnoreCase(leaderId);
	}
}
