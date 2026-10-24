package net.tfminecraft.simplefactions.war.battle.warband;

import net.tfminecraft.simplefactions.identity.Members;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandBattleService;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSideLock;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;

public class WarbandMembershipService {
	private static WarbandMembershipService instance;

	private final Map<UUID, WarbandRejoinState> pendingRejoin = new HashMap<>();

	public static WarbandMembershipService getInstance() {
		if (instance == null) {
			instance = new WarbandMembershipService();
		}
		return instance;
	}

	public static void resetForTests() {
		instance = new WarbandMembershipService();
	}

	public void handleQuit(UUID playerId) {
		Player player = getOnlinePlayer(playerId);
		if (player == null) handleQuit(playerId, null);
		else handleQuit(player);
	}

	public void handleQuit(Player player) {
		if (player != null) {
			Warband band = WarbandManager.getByMemberId(player.getUniqueId());
			if (band != null && Members.session(player)
					.filter(session -> !band.getMemberCharacter(player.getUniqueId()).equals(java.util.Optional.of(session.ref())))
					.isPresent()) return;
			handleQuit(player.getUniqueId(), player);
		}
	}

	private void handleQuit(UUID playerId, Player player) {
		Warband warband = WarbandManager.getByMemberId(playerId);
		if (warband == null) {
			return;
		}
		// Only the character that left may take the place back.
		CharacterRef character = warband.getMemberCharacter(playerId).orElse(null);
		if (character == null) {
			return;
		}
		Faction faction = warband.isFaction() && character != null
				? FactionManager.getByMember(character.characterId()) : null;
		detachFromBattle(player, warband);
		if (character != null) {
			pendingRejoin.put(playerId, new WarbandRejoinState(warband.getId(), faction, character));
		}
		warband.removeMember(playerId);
	}

	/** The outgoing session is already noncurrent when its end event fires. */
	public void handleSessionEnd(Player player, CharacterSession session) {
		Warband warband = WarbandManager.getByMemberId(player.getUniqueId());
		if (warband != null && warband.getMemberCharacter(player.getUniqueId()).equals(java.util.Optional.of(session.ref()))) {
			handleQuit(player.getUniqueId(), player);
		}
	}

	/** Restores the place only when the character that quit starts playing again. */
	public boolean handleSessionStart(Player player, CharacterSession session) {
		if (player == null || session == null) {
			return false;
		}
		WarbandRejoinState state = pendingRejoin.get(player.getUniqueId());
		if (state == null) {
			return false;
		}
		if (!Members.isCurrent(session) || !Members.session(player).equals(java.util.Optional.of(session))) {
			return false;
		}
		if (!state.isFor(session.ref())) {
			pendingRejoin.remove(player.getUniqueId());
			return false;
		}
		return attemptRejoin(player.getUniqueId(), Members.key(player), player);
	}

	boolean attemptRejoin(UUID playerId, String memberKey, Player onlinePlayer) {
		WarbandRejoinState state = pendingRejoin.remove(playerId);
		if (state == null) {
			return false;
		}
		Warband warband = WarbandManager.getByString(state.getWarbandId());
		if (warband == null || !state.belongsTo(warband)) {
			return false;
		}
		Warband current = WarbandManager.getByMemberId(playerId);
		if (current != null && current != warband) return false;
		Faction playerFaction = warband.isFaction() ? FactionManager.getByMember(memberKey) : null;
		if (!evaluateRejoin(warband, playerId, memberKey, playerFaction, state)) {
			return false;
		}
		CampaignWarbandSideLock.Tie tie = CampaignWarbandSideLock.tieOf(warband);
		if (tie != null && CampaignWarbandSideLock.refusal(tie.war(), tie.coalition(), playerId) != null) {
			return false;
		}
		if (onlinePlayer != null) warband.addPlayer(onlinePlayer);
		else {
			warband.addMember(playerId, state.getCharacter());
		}
		if (!warband.getMemberCharacter(playerId).equals(java.util.Optional.of(state.getCharacter()))) return false;
		if (tie != null) CampaignWarbandSideLock.bind(tie.war(), tie.coalition(), playerId);
		if (onlinePlayer != null) {
			restoreBattleState(onlinePlayer);
		}
		return true;
	}

	/**
	 * A character who left their faction leaves its war host too, as with {@code /warband leave}:
	 * the leadership passes on, the battle is told, and no place is kept to rejoin.
	 */
	public boolean removeFromFactionWarband(CharacterRef character) {
		UUID owner = character.ownerId();
		WarbandRejoinState pending = pendingRejoin.get(owner);
		if (pending != null && pending.isFor(character)) pendingRejoin.remove(owner);
		Warband warband = WarbandManager.getByMemberId(owner);
		if (warband == null || !warband.isFaction()
				|| !warband.getMemberCharacter(owner).equals(java.util.Optional.of(character))) {
			return false;
		}
		CampaignWarbandBattleService.processLeave(owner, getOnlinePlayer(owner), warband, true);
		return true;
	}

	public WarbandRejoinState getPendingRejoin(UUID playerId) {
		return pendingRejoin.get(playerId);
	}

	public void clearPendingRejoin(UUID playerId) {
		pendingRejoin.remove(playerId);
	}

	public boolean evaluateRejoin(
			Warband warband, UUID playerId, String memberKey, Faction playerFaction, WarbandRejoinState state) {
		if (warband.isFaction()) {
			if (!state.hasFaction()) {
				return false;
			}
			Faction faction = FactionManager.getByString(state.getFactionId());
			if (faction == null) {
				return false;
			}
			if (playerFaction == null || !playerFaction.equals(faction)) {
				return false;
			}
			CampaignBattleContext ctx = CampaignBattleJoinService.findCampaignBattleForWarband(warband);
			if (ctx != null && ctx.battle().hasStarted()) {
				return CampaignBattleJoinService.validateWarbandMemberJoin(
						ctx.war(), ctx.battle(), ctx.sideId(), warband, memberKey, playerId) == null;
			}
			if (ctx != null) {
				return CampaignBattleJoinService.validateRosterHasRoom(
						ctx.war(), ctx.battle(), ctx.sideId(), warband, 1) == null;
			}
			return true;
		}
		if (warband.isLocked()) {
			Player player = getOnlinePlayer(playerId);
			return player != null && warband.isInvited(player);
		}
		return true;
	}

	private Player getOnlinePlayer(UUID playerId) {
		if (Bukkit.getServer() == null) {
			return null;
		}
		return Bukkit.getPlayer(playerId);
	}

	private void detachFromBattle(Player player, Warband warband) {
		if (player == null) {
			return;
		}
		BattleManager.currentBattle.remove(player);
		BattleSide side = findSideForWarband(warband);
		if (side != null) {
			side.removeBossBarPlayer(player);
		}
	}

	private BattleSide findSideForWarband(Warband warband) {
		for (Battle battle : BattleManager.get()) {
			for (BattleSide side : battle.getSides()) {
				if (side.getBands().contains(warband)) {
					return side;
				}
			}
		}
		return null;
	}

	private void restoreBattleState(Player player) {
		Battle battle = BattleManager.getBattleByMemberId(player.getUniqueId());
		if (battle == null || !battle.hasStarted()) {
			return;
		}
		if (battle.isCampaignRaid()) {
			return;
		}
		BattleSide side = battle.getSideByMemberId(player.getUniqueId());
		side.addBossBarPlayer(player);
	}
}
