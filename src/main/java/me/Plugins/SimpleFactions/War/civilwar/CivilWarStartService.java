package me.Plugins.SimpleFactions.War.civilwar;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarRegimentSplitService;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarTitleMove;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarMemberMove;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarCapitalAssignService;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarUntangleService;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarSeaPortGate;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarWartimeVassalEnd;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.GuildLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService.LandSplitPlan;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.enums.Member;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public final class CivilWarStartService {
	private CivilWarStartService() {}

	public static String start(Movement movement) {
		if (movement == null || movement.isFrozen()) {
			return CivilWarCopy.COULD_NOT_START;
		}
		Faction host = movement.getFaction();
		if (host == null) {
			return CivilWarCopy.COULD_NOT_START;
		}
		WarGoalType goal = CivilWarGoalMapper.fromFirstCause(movement);
		if (goal == null) {
			return CivilWarCopy.UNMAPPABLE_CAUSE;
		}
		String lockError = CivilWarBorderLock.refuseStart(movement, host);
		if (lockError != null) {
			return lockError;
		}

		List<Guild> hostRebelGuilds = supportingHostGuilds(movement, host);
		boolean leaderIsVassal = isVassalMember(host, movement.getLeader());
		boolean needsTempRebels = !hostRebelGuilds.isEmpty() || !leaderIsVassal;
		if (needsTempRebels && vassalageConfigMissing()) {
			return CivilWarCopy.VASSALAGE_LAW_MISSING;
		}
		LandSplitPlan plan = null;
		if (needsTempRebels) {
			plan = CivilWarLandSplitService.plan(host, hostRebelGuilds);
			if (plan == null) {
				return CivilWarCopy.LAND_SPLIT_FAILED;
			}
			if (!CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(host, plan)) {
				return CivilWarCopy.NO_PORT_ON_SEA;
			}
		}

		LogManager.movement(
				"CIVIL_WAR_START movementId=%s faction=%s power=%.1f leader=%s",
				movement.getId(),
				host.getId(),
				movement.getPower(),
				movement.getLeader());
		AppliedStart applied = applyShape(movement, host, hostRebelGuilds, leaderIsVassal, needsTempRebels, plan);
		if (applied.error != null) {
			return applied.error;
		}
		applied.regimentMoves = CivilWarRegimentSplitService.split(
				host,
				applied.tempRebels,
				movement.getPower());

		War war = WarManager.startCivilWar(
				applied.warLeader,
				host,
				goal,
				movement.getId(),
				applied.extraAttackers,
				movement.getForeignBackers(),
				applied.snapshot);
		if (war == null) {
			rollback(applied);
			String last = WarManager.getLastDeclareError();
			return last != null && !last.isBlank() ? last : CivilWarCopy.COULD_NOT_START;
		}

		movement.setFrozen(true);
		LogManager.movement(
				"FROZEN movementId=%s faction=%s power=%.1f warStarted=true",
				movement.getId(),
				host.getId(),
				movement.getPower());
		return null;
	}

	static List<Guild> supportingHostGuilds(Movement movement, Faction host) {
		List<Guild> result = new ArrayList<>();
		if (movement == null || host == null || host.getId() == null) {
			return result;
		}
		for (Guild guild : movement.getAllSupportingGuilds()) {
			if (guild == null || guild.isBase()) {
				continue;
			}
			if (guild.getFaction() != null && host.getId().equalsIgnoreCase(guild.getFaction().getId())) {
				result.add(guild);
			}
		}
		return result;
	}

	public static List<Faction> supportingVassals(Movement movement, Faction host) {
		List<Faction> result = new ArrayList<>();
		if (movement == null || host == null || host.getId() == null) {
			return result;
		}
		for (Faction vassal : movement.getAllSupportingFactions()) {
			if (vassal == null || vassal.getId() == null) {
				continue;
			}
			if (vassal.getId().equalsIgnoreCase(host.getId())) {
				continue;
			}
			result.add(vassal);
		}
		return result;
	}

	static List<Faction> directSupportingVassals(List<Faction> supporting, Faction host) {
		List<Faction> direct = new ArrayList<>();
		if (supporting == null || host == null || host.getId() == null) {
			return direct;
		}
		String hostId = host.getId();
		for (Faction vassal : supporting) {
			if (vassal == null || vassal.getId() == null) {
				continue;
			}
			String overlord = RelationManager.getOverlord(vassal);
			if (overlord != null && overlord.equalsIgnoreCase(hostId)) {
				direct.add(vassal);
			}
		}
		return direct;
	}

	private static AppliedStart applyShape(
			Movement movement,
			Faction host,
			List<Guild> hostRebelGuilds,
			boolean leaderIsVassal,
			boolean needsTempRebels,
			LandSplitPlan plan) {
		AppliedStart applied = new AppliedStart();
		applied.host = host;
		applied.plan = plan;
		applied.hostOldCapital = host.getCapital();

		List<Faction> supporting = supportingVassals(movement, host);
		List<Faction> direct = directSupportingVassals(supporting, host);
		if (needsTempRebels) {
			Guild main = pickRebelMainGuild(movement, hostRebelGuilds);
			Map<String, Integer> rebelGuildOldCapitals = snapshotGuildCapitals(hostRebelGuilds);
			Faction rebels;
			if (main != null) {
				Guild.RebelNation nation = CivilWarTempRebelFactory.createFromMainGuild(host, main, movement.getLeader());
				if (nation == null || nation.faction() == null) {
					applied.error = CivilWarCopy.COULD_NOT_START;
					return applied;
				}
				rebels = nation.faction();
				applied.rebelMainGuildOwnName = nation.ownName();
			} else {
				rebels = CivilWarTempRebelFactory.create(host, movement.getLeader());
			}
			if (rebels == null) {
				applied.error = CivilWarCopy.COULD_NOT_START;
				return applied;
			}
			applied.tempRebels = rebels;
			for (Guild guild : hostRebelGuilds) {
				if (main != null && guild.getId() != null && guild.getId().equalsIgnoreCase(main.getId())) {
					continue;
				}
				int capital = guild.hasCapital() ? guild.getCapital() : -1;
				guild.relocateKeepingSettlements(rebels, capital);
			}
			if (main != null && GuildLoader.getBaseType() != null && !main.isBase()) {
				main.convert(GuildLoader.getBaseType());
			}
			moveCitizenSupporters(movement, host, rebels, applied);
			applyChangeLeaderTarget(movement, host, rebels, applied);
			CivilWarLandSplitService.apply(host, rebels, plan);
			applied.splitApplied = true;
			int rebelCapital = CivilWarCapitalAssignService.assign(
					host,
					rebels,
					plan,
					applied.hostOldCapital,
					rebelGuildOldCapitals);
			applied.rebelCapital = rebelCapital > 0 ? rebelCapital : null;
			applied.hostCapitalMoved = plan.rebelProvinceIds().contains(applied.hostOldCapital);
			if (host.getProvinceHandler() != null) {
				host.getProvinceHandler().revalidateClaims();
			}
			if (rebels.getProvinceHandler() != null) {
				rebels.getProvinceHandler().revalidateClaims();
			}
			Title moved = CivilWarTitleMove.pick(host, rebels, applied.rebelCapital == null ? -1 : applied.rebelCapital);
			if (moved != null) {
				CivilWarTitleMove.transfer(host, rebels, moved);
				applied.movedTitleId = moved.getId();
			}
			String lawError = applyConfiguredVassalage(rebels);
			if (lawError != null) {
				applied.error = lawError;
				rollback(applied);
				return applied;
			}
		}

		endWartimeVassalage(host, direct, applied);

		if (applied.tempRebels != null && !leaderIsVassal) {
			foldDirectUnderRebels(applied);
			applied.warLeader = applied.tempRebels;
			applied.extraAttackers.addAll(direct);
		} else {
			Faction leaderFaction = FactionManager.getByMember(movement.getLeader());
			applied.warLeader = leaderFaction != null ? leaderFaction : (direct.isEmpty() ? applied.tempRebels : direct.get(0));
			for (Faction vassal : direct) {
				if (applied.warLeader != null && vassal.getId().equalsIgnoreCase(applied.warLeader.getId())) {
					continue;
				}
				applied.extraAttackers.add(vassal);
			}
			if (applied.tempRebels != null
					&& (applied.warLeader == null
							|| !applied.tempRebels.getId().equalsIgnoreCase(applied.warLeader.getId()))) {
				applied.extraAttackers.add(applied.tempRebels);
			}
		}
		if (applied.warLeader == null) {
			applied.error = CivilWarCopy.COULD_NOT_START;
			rollback(applied);
			return applied;
		}

		applied.snapshot = buildSnapshot(applied);
		return applied;
	}

	public static Map<String, Integer> snapshotGuildCapitals(List<Guild> guilds) {
		Map<String, Integer> snapshot = new LinkedHashMap<>();
		if (guilds == null) {
			return snapshot;
		}
		for (Guild guild : guilds) {
			if (guild == null || guild.getId() == null) {
				continue;
			}
			snapshot.put(guild.getId(), guild.hasCapital() ? guild.getCapital() : -1);
		}
		return snapshot;
	}

	private static CivilWarSnapshot buildSnapshot(AppliedStart applied) {
		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId(applied.host.getId());
		if (applied.tempRebels != null) {
			snapshot.setTempRebelFactionId(applied.tempRebels.getId());
		}
		Map<Integer, String> transferred = new LinkedHashMap<>();
		if (applied.plan != null) {
			for (int provinceId : applied.plan.rebelProvinceIds()) {
				transferred.put(provinceId, applied.host.getId());
			}
		}
		snapshot.setTransferredProvinces(transferred);
		snapshot.setWartimeVassalEnds(applied.vassalEnds);
		if (applied.hostCapitalMoved) {
			snapshot.setHostOldCapitalId(applied.hostOldCapital);
		}
		snapshot.setRebelCapitalId(applied.rebelCapital);
		snapshot.setWantedLeaderName(applied.wantedLeaderName);
		snapshot.setMemberMoves(applied.memberMoves);
		snapshot.setRebelMainGuildOwnName(applied.rebelMainGuildOwnName);
		snapshot.setMovedTitleId(applied.movedTitleId);
		return snapshot;
	}

	private static void endWartimeVassalage(Faction host, List<Faction> vassals, AppliedStart applied) {
		for (Faction vassal : vassals) {
			String typeId = CivilWarUntangleService.snapshotVassalageTypeId(vassal, host);
			applied.vassalEnds.add(new CivilWarWartimeVassalEnd(vassal.getId(), host.getId(), typeId));
			RelationManager.endVassalage(vassal, host, false);
		}
	}

	private static void foldDirectUnderRebels(AppliedStart applied) {
		if (applied == null || applied.tempRebels == null) {
			return;
		}
		for (CivilWarWartimeVassalEnd end : applied.vassalEnds) {
			if (end == null || end.relationTypeId() == null) {
				continue;
			}
			CivilWarUntangleService.restoreVassalRelation(
					end.factionId(),
					applied.tempRebels.getId(),
					end.relationTypeId());
		}
	}

	private static void moveCitizenSupporters(Movement movement, Faction host, Faction rebels, AppliedStart applied) {
		List<String> citizens = new ArrayList<>(movement.getSupporters().getCitizens());
		for (Cause cause : movement.getCauses()) {
			citizens.addAll(cause.getPool().getCitizens());
		}
		for (String citizen : citizens) {
			if (citizen == null) {
				continue;
			}
			if (host.getRelationToFaction(citizen) != Member.MEMBER) {
				continue;
			}
			moveToRebelMain(host, rebels, citizen, applied);
		}
	}

	private static void applyChangeLeaderTarget(Movement movement, Faction host, Faction rebels, AppliedStart applied) {
		Cause first = movement.getCauses().isEmpty() ? null : movement.getCauses().get(0);
		if (first == null || first.getAction() != Action.CHANGE_LEADER) {
			return;
		}
		Proposal proposal = first.getProposal();
		if (proposal == null || !proposal.hasTarget()) {
			return;
		}
		String target = proposal.getTarget();
		if (host == null || !host.canBecomeLeader(target)) {
			return;
		}
		applied.wantedLeaderName = target;
		moveToRebelMain(host, rebels, target, applied);
		rebels.setLeader(target);
	}

	private static void moveToRebelMain(Faction host, Faction rebels, String player, AppliedStart applied) {
		if (host == null || rebels == null || player == null || player.isBlank()) {
			return;
		}
		Guild origin = findGuild(host, player);
		if (origin == null) {
			origin = findGuild(rebels, player);
		}
		if (origin == null) {
			return;
		}
		boolean wasLeader = !origin.isBase() && origin.isLeader(player);
		recordMemberMove(applied, new CivilWarMemberMove(player, origin.getId(), wasLeader));
		Guild rebelMain = rebels.getOrCreateMainGuild();
		if (rebelMain != null && rebelMain.isMember(player)) {
			return;
		}
		if (wasLeader) {
			String successor = successorExcept(origin, player);
			if (successor != null) {
				origin.setLeader(successor);
			}
		}
		origin.kick(player);
		if (rebelMain != null) {
			rebelMain.addMember(player);
		}
	}

	private static void recordMemberMove(AppliedStart applied, CivilWarMemberMove move) {
		if (applied == null || move == null || move.player() == null) {
			return;
		}
		for (CivilWarMemberMove existing : applied.memberMoves) {
			if (existing != null && move.player().equalsIgnoreCase(existing.player())) {
				return;
			}
		}
		applied.memberMoves.add(move);
	}

	private static Guild findGuild(Faction faction, String player) {
		if (faction == null || faction.getGuildHandler() == null || player == null) {
			return null;
		}
		return faction.getGuildHandler().getGuildByMember(player);
	}

	private static String successorExcept(Guild guild, String leaving) {
		if (guild == null || guild.getMembers() == null) {
			return null;
		}
		for (String member : guild.getMembers()) {
			if (member != null && !member.equalsIgnoreCase(leaving)) {
				return member;
			}
		}
		return null;
	}

	static Guild pickRebelMainGuild(Movement movement, List<Guild> hostRebelGuilds) {
		if (hostRebelGuilds == null || hostRebelGuilds.isEmpty()) {
			return null;
		}
		if (movement != null && movement.getLeader() != null) {
			for (Guild guild : hostRebelGuilds) {
				if (guild != null && guild.isMember(movement.getLeader())) {
					return guild;
				}
			}
		}
		Guild strongest = null;
		double best = Double.NEGATIVE_INFINITY;
		for (Guild guild : hostRebelGuilds) {
			double power = 0;
			if (guild != null && guild.getTradeBreakdown() != null) {
				power = guild.getTradeBreakdown().getTradePower();
			}
			if (strongest == null || power > best) {
				strongest = guild;
				best = power;
			}
		}
		return strongest;
	}

	static boolean isVassalMember(Faction host, String player) {
		if (host == null || player == null) {
			return false;
		}
		Member relation = host.getRelationToFaction(player);
		return relation == Member.VASSAL_LEADER || relation == Member.VASSAL_MEMBER;
	}

	private static void rollback(AppliedStart applied) {
		if (applied == null) {
			return;
		}
		if (applied.regimentMoves != null && !applied.regimentMoves.isEmpty()) {
			CivilWarRegimentSplitService.rollback(applied.host, applied.tempRebels, applied.regimentMoves);
		}
		if (applied.splitApplied && applied.tempRebels != null && applied.plan != null) {
			if (applied.hostCapitalMoved && applied.hostOldCapital > 0) {
				applied.host.setCapital(applied.hostOldCapital, true, false);
			}
			CivilWarLandSplitService.rollback(applied.host, applied.tempRebels, applied.plan);
		}
		if (applied.movedTitleId != null && applied.host != null && applied.tempRebels != null) {
			Title title = TitleLoader.getById(applied.movedTitleId);
			if (title != null && applied.tempRebels.hasTitle(title)) {
				CivilWarTitleMove.transfer(applied.tempRebels, applied.host, title);
			}
		}
		for (CivilWarWartimeVassalEnd end : applied.vassalEnds) {
			if (applied.tempRebels != null) {
				Faction vassal = FactionManager.getByString(end.factionId());
				if (vassal != null) {
					RelationManager.endVassalage(vassal, applied.tempRebels, false);
				}
			}
			CivilWarUntangleService.restoreVassalRelation(end.factionId(), end.formerOverlordId(), end.relationTypeId());
		}
		if (applied.tempRebels != null) {
			try {
				FactionManager.deleteFaction(applied.tempRebels);
			} catch (Exception ignored) {
				FactionManager.factions.remove(applied.tempRebels);
			}
		}
	}

	private static final class AppliedStart {
		String error;
		Faction host;
		Faction tempRebels;
		Faction warLeader;
		List<Faction> extraAttackers = new ArrayList<>();
		LandSplitPlan plan;
		boolean splitApplied;
		int hostOldCapital;
		boolean hostCapitalMoved;
		Integer rebelCapital;
		List<CivilWarWartimeVassalEnd> vassalEnds = new ArrayList<>();
		List<CivilWarMemberMove> memberMoves = new ArrayList<>();
		String wantedLeaderName;
		String rebelMainGuildOwnName;
		String movedTitleId;
		CivilWarSnapshot snapshot;
		Map<String, Integer> regimentMoves = new LinkedHashMap<>();
	}

	private static boolean vassalageConfigMissing() {
		return Cache.civilWarVassalageGroup == null
				|| Cache.civilWarVassalageGroup.isBlank()
				|| Cache.civilWarVassalageLaw == null
				|| Cache.civilWarVassalageLaw.isBlank();
	}

	static String applyConfiguredVassalage(Faction rebels) {
		if (vassalageConfigMissing()) {
			return CivilWarCopy.VASSALAGE_LAW_MISSING;
		}
		if (rebels == null) {
			return CivilWarCopy.VASSALAGE_LAW_MISSING;
		}
		LawHandler handler = rebels.getLawHandler();
		if (handler == null) {
			return CivilWarCopy.VASSALAGE_LAW_MISSING;
		}
		LawGroup group = handler.getGroup(Cache.civilWarVassalageGroup);
		Law law = group == null ? null : group.getLaw(Cache.civilWarVassalageLaw);
		if (group == null || law == null) {
			return CivilWarCopy.VASSALAGE_LAW_MISSING;
		}
		if (law.getScopedEffects() != null && law.getScopedEffects().get(Scope.FACTION) != null) {
			rebels.applyLaw(law, group);
		} else {
			group.setCurrent(law);
		}
		return null;
	}
}
