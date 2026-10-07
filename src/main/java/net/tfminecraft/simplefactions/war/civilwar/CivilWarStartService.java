package net.tfminecraft.simplefactions.war.civilwar;


import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarLandSplitService;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarRegimentSplitService;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarTitleMove;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarMemberMove;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarCapitalAssignService;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarUntangleService;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarSeaPortGate;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarWartimeVassalEnd;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildType;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarLandSplitService.LandSplitPlan;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.enums.Member;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

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
		String changeLeaderTarget = eligibleChangeLeaderTarget(movement, host);

		List<Faction> supporting = supportingVassals(movement, host);
		List<Faction> direct = directSupportingVassals(supporting, host);
		for (Faction vassal : direct) {
			String typeId = CivilWarUntangleService.snapshotVassalageTypeId(vassal, host);
			if (typeId == null) {
				applied.error = CivilWarCopy.COULD_NOT_START;
				return applied;
			}
			applied.vassalEnds.add(new CivilWarWartimeVassalEnd(vassal.getId(), host.getId(), typeId));
			applied.originalVassalRelations.put(vassal, vassal.getDiplomacyHandler().getRelations().get(host.getId()));
			applied.originalHostRelations.put(vassal, host.getDiplomacyHandler().getRelations().get(vassal.getId()));
		}
		if (needsTempRebels) {
			for (Guild guild : hostRebelGuilds) {
				applied.originalGuilds.put(guild, new GuildBeforeStart(guild));
			}
			Guild main = pickRebelMainGuild(movement, hostRebelGuilds);
			Map<String, Integer> rebelGuildOldCapitals = snapshotGuildCapitals(hostRebelGuilds);
			Faction rebels;
			if (main != null) {
				Guild.RebelNation nation = CivilWarTempRebelFactory.createFromMainGuild(host, main, movement.getLeader());
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
			moveCitizenSupporters(movement, host, rebels, applied);
			applyChangeLeaderTarget(changeLeaderTarget, host, rebels, applied);
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

		endWartimeVassalage(host, direct);

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

	private static void endWartimeVassalage(Faction host, List<Faction> vassals) {
		for (Faction vassal : vassals) {
			RelationManager.endVassalage(vassal, host, false);
		}
	}

	private static void foldDirectUnderRebels(AppliedStart applied) {
		for (CivilWarWartimeVassalEnd end : applied.vassalEnds) {
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

	private static String eligibleChangeLeaderTarget(Movement movement, Faction host) {
		Cause first = movement.getCauses().isEmpty() ? null : movement.getCauses().get(0);
		if (first == null || first.getAction() != Action.CHANGE_LEADER) {
			return null;
		}
		Proposal proposal = first.getProposal();
		if (proposal == null || !proposal.hasTarget()) {
			return null;
		}
		String target = proposal.getTarget();
		return host.canBecomeLeader(target) ? target : null;
	}

	private static void applyChangeLeaderTarget(String target, Faction host, Faction rebels, AppliedStart applied) {
		if (target == null) {
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
		if (origin.getFaction() == host) {
			applied.originalGuilds.computeIfAbsent(origin, GuildBeforeStart::new);
		}
		boolean wasLeader = !origin.isBase() && origin.isLeader(player);
		recordMemberMove(applied, new CivilWarMemberMove(player, origin.getId(), wasLeader));
		Guild rebelMain = rebels.getOrCreateMainGuild();
		if (origin == rebelMain) {
			return;
		}
		origin.kick(player);
		if (rebelMain != null) {
			rebelMain.addMember(player);
		}
	}

	private static void recordMemberMove(AppliedStart applied, CivilWarMemberMove move) {
		for (CivilWarMemberMove existing : applied.memberMoves) {
			if (existing != null && move.player().equalsIgnoreCase(existing.player())) {
				return;
			}
		}
		applied.memberMoves.add(move);
	}

	private static Guild findGuild(Faction faction, String player) {
		return faction.getGuildHandler().getGuildByMember(player);
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
		}
		applied.originalVassalRelations.forEach((vassal, relation) -> {
			if (relation == null) vassal.getDiplomacyHandler().getRelations().remove(applied.host.getId());
			else vassal.setRelation(applied.host, relation);
		});
		applied.originalHostRelations.forEach((vassal, relation) -> {
			if (relation == null) applied.host.getDiplomacyHandler().getRelations().remove(vassal.getId());
			else applied.host.setRelation(vassal, relation);
		});
		// Promotion and relocation reuse the guild objects. Put those objects and their
		// pre-start state back before deleting the temporary faction that now owns them.
		for (GuildBeforeStart original : applied.originalGuilds.values()) {
			original.restore();
		}
		for (GuildBeforeStart original : applied.originalGuilds.values()) {
			original.guild.setCapital(original.capital, false);
			original.guild.updateWealth();
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
		Map<Guild, GuildBeforeStart> originalGuilds = new LinkedHashMap<>();
		Map<Faction, net.tfminecraft.simplefactions.diplomacy.Relation> originalVassalRelations = new LinkedHashMap<>();
		Map<Faction, net.tfminecraft.simplefactions.diplomacy.Relation> originalHostRelations = new LinkedHashMap<>();
	}

	/** The state changed by promotion, relocation, or moving a citizen into the rebel main guild. */
	private static final class GuildBeforeStart {
		private final Guild guild;
		private final Faction host;
		private final GuildType type;
		private final int capital;
		private final String name;
		private final String leader;
		private final String leaderCharacter;
		private final String leaderCharacterOf;
		private final String rgb;
		private final List<String> banner;
		private final List<String> members;
		private final List<String> invites;
		private final boolean favoured;
		private final boolean repressed;
		private final Stance stance;
		private final Map<Integer, Branch> branches;
		private final Map<Upgrade, Integer> upgradeLevels = new LinkedHashMap<>();

		private GuildBeforeStart(Guild guild) {
			this.guild = guild;
			host = guild.getFaction();
			type = guild.getType();
			capital = guild.getCapital();
			name = guild.getOwnName();
			leader = guild.getLeader();
			leaderCharacter = guild.getLeaderCharacter();
			leaderCharacterOf = guild.getLeaderCharacterOf();
			rgb = guild.getRGB();
			banner = new ArrayList<>(guild.getBannerPatterns());
			members = new ArrayList<>(guild.getMembers());
			invites = new ArrayList<>(guild.getInvites());
			favoured = guild.isFavoured();
			repressed = guild.isRepressed();
			stance = guild.getStance(host);
			branches = new LinkedHashMap<>(guild.getBranches());
			for (Upgrade upgrade : guild.getUpgrades()) {
				upgradeLevels.put(upgrade, upgrade.getLevel());
			}
		}

		private void restore() {
			Faction currentHost = guild.getFaction();
			if (currentHost != host) {
				currentHost.getGuildHandler().removeGuild(guild.getId(), false, false);
			}
			if (guild.getType() != type) guild.convert(type);
			guild.setHost(host);
			host.getGuildHandler().addGuild(guild);
			guild.setName(name);
			guild.setLeader(leader);
			guild.rememberLeaderCharacter(leaderCharacter, leaderCharacterOf);
			guild.setRGB(rgb);
			guild.setBannerPatterns(new ArrayList<>(banner));
			guild.getMembers().clear();
			guild.getMembers().addAll(members);
			guild.getInvites().clear();
			guild.getInvites().addAll(invites);
			guild.setFavoured(favoured);
			guild.setRepressed(repressed);
			guild.setStance(stance);
			guild.getBranches().clear();
			guild.getBranches().putAll(branches);
			upgradeLevels.forEach(Upgrade::setLevel);
		}
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
		LawGroup group = handler.getGroup(Cache.civilWarVassalageGroup);
		Law law = group == null ? null : group.getLaw(Cache.civilWarVassalageLaw);
		if (group == null || law == null) {
			return CivilWarCopy.VASSALAGE_LAW_MISSING;
		}
		if (law.getScopedEffects() != null && law.getScopedEffects().get(Scope.FACTION) != null) {
			rebels.applyLaw(law, group);
		} else {
			group.switchTo(law, System.currentTimeMillis());
		}
		return null;
	}
}
