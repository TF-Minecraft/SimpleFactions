package net.tfminecraft.simplefactions.war.battle.engine.raid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

public final class RaidAttackerEliminationService {
	private static final Map<String, Set<UUID>> OUT_ATTACKERS = new HashMap<>();

	private RaidAttackerEliminationService() {
	}

	public static void resetForTests() {
		OUT_ATTACKERS.clear();
	}

	public static void markOut(Battle battle, UUID memberId) {
		if (battle == null || memberId == null) {
			return;
		}
		OUT_ATTACKERS.computeIfAbsent(battle.getId(), ignored -> new HashSet<>()).add(memberId);
	}

	public static boolean isMarkedOut(Battle battle, UUID memberId) {
		if (battle == null || memberId == null) {
			return false;
		}
		Set<UUID> out = OUT_ATTACKERS.get(battle.getId());
		return out != null && out.contains(memberId);
	}

	public static boolean isAttackerSideEliminated(Battle battle) {
		if (battle == null || battle.getBattleType() != BattleType.RAID) {
			return false;
		}
		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		if (attacker == null) {
			return false;
		}
		List<UUID> members = collectMemberIds(attacker);
		if (members.isEmpty()) {
			return false;
		}
		for (UUID memberId : members) {
			Player player = Bukkit.getPlayer(memberId);
			if (player != null && player.isOnline()) {
				if (!isParticipantOut(battle, memberId, player, attacker)) {
					return false;
				}
			}
		}
		return true;
	}

	public static int countActiveAttackers(Battle battle) {
		if (battle == null || battle.getBattleType() != BattleType.RAID) {
			return 0;
		}
		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		if (attacker == null) {
			return 0;
		}
		int active = 0;
		for (UUID memberId : collectMemberIds(attacker)) {
			Player player = Bukkit.getPlayer(memberId);
			if (player != null && player.isOnline()
					&& !isParticipantOut(battle, memberId, player, attacker)) {
				active++;
			}
		}
		return active;
	}

	public static int countAttackerRoster(Battle battle) {
		if (battle == null || battle.getBattleType() != BattleType.RAID) {
			return 0;
		}
		BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
		if (attacker == null) {
			return 0;
		}
		return collectMemberIds(attacker).size();
	}

	public static void clearBattleState(Battle battle) {
		if (battle != null) {
			OUT_ATTACKERS.remove(battle.getId());
		}
	}

	private static boolean isParticipantOut(Battle battle, UUID memberId, Player player, BattleSide attacker) {
		if (isMarkedOut(battle, memberId)) {
			return true;
		}
		return FieldWinService.isAtJail(player, attacker);
	}

	private static List<UUID> collectMemberIds(BattleSide side) {
		List<UUID> ids = new ArrayList<>();
		for (Warband warband : side.getBands()) {
			ids.addAll(warband.getMemberIds());
		}
		return ids;
	}
}
