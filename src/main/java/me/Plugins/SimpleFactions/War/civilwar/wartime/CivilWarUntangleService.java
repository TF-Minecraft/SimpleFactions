package me.Plugins.SimpleFactions.War.civilwar.wartime;


import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarRegimentSplitService;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarTitleMove;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarMemberMove;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.GuildLoader;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService.LandSplitPlan;

public final class CivilWarUntangleService {
	private CivilWarUntangleService() {}

	public static void restore(War war) {
		restore(war, WarEndReason.WHITE_PEACE);
	}

	public static void restore(War war, WarEndReason reason) {
		if (war == null) {
			return;
		}
		CivilWarSnapshot snapshot = war.getCivilWarSnapshot();
		if (snapshot == null) {
			return;
		}
		Faction host = FactionManager.getByString(snapshot.getHostFactionId());
		Faction rebels = snapshot.getTempRebelFactionId() == null
				? null
				: FactionManager.getByString(snapshot.getTempRebelFactionId());
		if (host != null && rebels != null) {
			mergeTempRebels(host, rebels, snapshot);
		}
		restoreVassals(snapshot);
		if (host != null) {
			restoreMembers(host, snapshot, reason);
		}
	}

	private static void mergeTempRebels(Faction host, Faction rebels, CivilWarSnapshot snapshot) {
		CivilWarRegimentSplitService.mergeRemaining(rebels, host);
		relocateNonBaseGuilds(rebels, host);
		restoreLand(host, rebels, snapshot);
		restoreMovedTitle(host, rebels, snapshot);
		absorbRebelMainGuild(rebels, host, snapshot);
		Integer oldCapital = snapshot.getHostOldCapitalId();
		if (oldCapital != null && oldCapital > 0) {
			host.setCapital(oldCapital, true, false);
		}
		try {
			FactionManager.deleteFaction(rebels);
		} catch (Exception ignored) {
			FactionManager.factions.remove(rebels);
		}
	}

	private static void relocateNonBaseGuilds(Faction rebels, Faction host) {
		if (rebels.getGuildHandler() == null) {
			return;
		}
		for (Guild guild : new ArrayList<>(rebels.getGuildHandler().getGuilds())) {
			if (guild == null || guild.isBase()) {
				continue;
			}
			int capital = guild.hasCapital() ? guild.getCapital() : -1;
			guild.relocate(host, capital);
		}
	}

	private static void restoreLand(Faction host, Faction rebels, CivilWarSnapshot snapshot) {
		Set<Integer> tiles = new LinkedHashSet<>();
		if (snapshot.getTransferredProvinces() != null) {
			tiles.addAll(snapshot.getTransferredProvinces().keySet());
		}
		if (rebels.getProvinces() != null) {
			tiles.addAll(rebels.getProvinces());
		}
		if (tiles.isEmpty()) {
			return;
		}
		CivilWarLandSplitService.rollback(
				host,
				rebels,
				new LandSplitPlan(new ArrayList<>(tiles), List.of()));
	}

	private static void restoreMovedTitle(Faction host, Faction rebels, CivilWarSnapshot snapshot) {
		if (snapshot.getMovedTitleId() == null || snapshot.getMovedTitleId().isBlank()) {
			return;
		}
		Title title = TitleLoader.getById(snapshot.getMovedTitleId());
		if (title == null) {
			return;
		}
		if (rebels.hasTitle(title)) {
			CivilWarTitleMove.transfer(rebels, host, title);
		}
	}

	private static void absorbRebelMainGuild(Faction rebels, Faction host, CivilWarSnapshot snapshot) {
		if (rebels.getGuildHandler() == null || host.getGuildHandler() == null) {
			return;
		}
		Guild base = rebels.getOrCreateMainGuild();
		if (base == null) {
			return;
		}
		if (GuildLoader.getDefaultType() != null) {
			base.convert(GuildLoader.getDefaultType());
		}
		String ownName = snapshot == null ? null : snapshot.getRebelMainGuildOwnName();
		if (ownName != null && !ownName.isBlank()) {
			base.setName(ownName);
		}
		if (base.isBase()) {
			host.getGuildHandler().addGuild(base);
			return;
		}
		int capital = base.hasCapital() ? base.getCapital() : -1;
		base.relocate(host, capital);
	}

	private static void restoreVassals(CivilWarSnapshot snapshot) {
		if (snapshot.getWartimeVassalEnds() == null) {
			return;
		}
		for (CivilWarWartimeVassalEnd end : snapshot.getWartimeVassalEnds()) {
			if (end == null) {
				continue;
			}
			restoreVassalRelation(end.factionId(), end.formerOverlordId(), end.relationTypeId());
		}
	}

	private static void restoreMembers(Faction host, CivilWarSnapshot snapshot, WarEndReason reason) {
		if (snapshot.getMemberMoves() == null || snapshot.getMemberMoves().isEmpty()) {
			return;
		}
		boolean attackerWin = reason == WarEndReason.ATTACKER_VICTORY;
		String wanted = snapshot.getWantedLeaderName();
		for (CivilWarMemberMove move : snapshot.getMemberMoves()) {
			if (move == null || move.player() == null || move.player().isBlank()) {
				continue;
			}
			boolean wantedLeader = attackerWin
					&& wanted != null
					&& wanted.equalsIgnoreCase(move.player());
			if (host.getGuildHandler() != null) {
				host.getGuildHandler().forceKick(move.player());
			}
			if (wantedLeader) {
				host.getOrCreateMainGuild().addMember(move.player());
				continue;
			}
			Guild origin = host.getGuildHandler() == null
					? null
					: host.getGuildHandler().getGuild(move.originGuildId());
			if (origin == null) {
				origin = host.getOrCreateMainGuild();
			}
			origin.addMember(move.player());
			if (move.originWasGuildLeader() && !origin.isBase()) {
				origin.setLeader(move.player());
			}
		}
	}

	public static String snapshotVassalageTypeId(Faction vassal, Faction overlord) {
		if (vassal == null || overlord == null) {
			return null;
		}
		RelationType fromOverlord = typeOf(overlord.getRelation(vassal.getId()));
		if (fromOverlord != null && fromOverlord.isVassalage()) {
			return fromOverlord.getId();
		}
		RelationType fromVassal = typeOf(vassal.getRelation(overlord.getId()));
		RelationType vassalage = vassalageType(fromOverlord);
		if (vassalage == null) {
			vassalage = vassalageType(fromVassal);
		}
		return vassalage == null ? null : vassalage.getId();
	}

	public static void restoreVassalRelation(String vassalId, String overlordId, String typeId) {
		Faction vassal = FactionManager.getByString(vassalId);
		Faction overlord = FactionManager.getByString(overlordId);
		RelationType type = vassalageType(typeId == null ? null : RelationLoader.getType(typeId));
		if (vassal != null && overlord != null && type != null) {
			RelationManager.setRelationForced(type, vassal, overlord);
		}
	}

	private static RelationType typeOf(Relation relation) {
		return relation == null ? null : relation.getType();
	}

	private static RelationType vassalageType(RelationType type) {
		if (type == null) {
			return null;
		}
		if (type.isVassalage()) {
			return type;
		}
		if (type.isOverlord() && type.getLink() != null && type.getLink().isVassalage()) {
			return type.getLink();
		}
		return null;
	}
}
