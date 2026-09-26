package net.tfminecraft.simplefactions.war.battle.engine.win;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;

public final class FieldWinService {
	private static final double JAIL_RADIUS_BLOCKS = 5.0;
	private static final double JAIL_RADIUS_SQ = JAIL_RADIUS_BLOCKS * JAIL_RADIUS_BLOCKS;
	private static final ConcurrentHashMap<String, Instant> emptySince = new ConcurrentHashMap<>();
	private static final Set<String> seenOnline = ConcurrentHashMap.newKeySet();
	// Battles restored after a restart get a fresh grace, so players have time to reconnect.
	private static volatile Instant trackingSince = Instant.now();

	private FieldWinService() {
	}

	public static void clearEmptySideTracking(Battle battle) {
		if (battle == null || battle.getId() == null) {
			return;
		}
		String prefix = battle.getId().toLowerCase(Locale.ROOT) + "|";
		emptySince.keySet().removeIf(key -> key.startsWith(prefix));
		seenOnline.removeIf(key -> key.startsWith(prefix));
	}

	static void clearEmptySideTrackingForTests() {
		emptySince.clear();
		seenOnline.clear();
		trackingSince = Instant.EPOCH;
	}

	static void setTrackingSinceForTests(Instant since) {
		trackingSince = since;
	}

	public static void checkFieldWin(Battle battle) {
		if (battle == null || !battle.hasStarted() || battle.getBattleType() != BattleType.FIELD) {
			return;
		}
		List<BattleSide> eliminated = new ArrayList<>();
		for (BattleSide side : battle.getSides()) {
			if (isSideEliminated(battle, side)) {
				eliminated.add(side);
			}
		}
		if (eliminated.isEmpty()) {
			return;
		}
		String winnerId = null;
		if (eliminated.size() == 1 && battle.getSides().size() == 2) {
			BattleSide loser = eliminated.get(0);
			for (BattleSide side : battle.getSides()) {
				if (!side.getId().equalsIgnoreCase(loser.getId())) {
					winnerId = side.getId();
					break;
				}
			}
		}
		endBattle(battle, winnerId);
	}

	static void endBattle(Battle battle, String winningSideId) {
		BattleEndSupport.endBattle(battle, winningSideId);
	}

	public static boolean isSideEliminated(BattleSide side) {
		return isSideEliminated(null, side, Instant.now());
	}

	public static boolean isSideEliminated(Battle battle, BattleSide side) {
		return isSideEliminated(battle, side, Instant.now());
	}

	static boolean isSideEliminated(Battle battle, BattleSide side, Instant now) {
		if (side == null) {
			return false;
		}
		Instant clock = now != null ? now : Instant.now();
		List<Player> online = getOnlineParticipants(side);
		boolean grace = usesEmptySideGrace(battle);
		String key = grace ? trackingKey(battle, side) : null;
		if (grace) {
			if (online.isEmpty()) {
				emptySince.computeIfAbsent(key, ignored -> anchor(battle, key, clock));
			} else {
				seenOnline.add(key);
				emptySince.remove(key);
			}
		}
		if (online.isEmpty()) {
			// With the grace, a side with nobody online loses once it runs out, lives left
			// or not, so a battle cannot stall when one side logs off.
			if (!grace) {
				return side.getLives() <= 0;
			}
			return emptyLongEnough(key, clock);
		}
		if (side.getLives() > 0) {
			return false;
		}
		for (Player player : online) {
			if (!isAtJail(player, side)) {
				return false;
			}
		}
		return true;
	}

	private static boolean usesEmptySideGrace(Battle battle) {
		if (battle == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}

	private static Instant anchor(Battle battle, String key, Instant now) {
		if (!seenOnline.contains(key) && battle != null && battle.getStartedAt() != null
				&& !battle.getStartedAt().isBefore(trackingSince)) {
			return battle.getStartedAt();
		}
		return now;
	}

	private static boolean emptyLongEnough(String key, Instant now) {
		Instant since = emptySince.get(key);
		if (since == null) {
			return false;
		}
		int grace = Math.max(0, Cache.battleEmptySideGraceSeconds);
		return !now.isBefore(since.plusSeconds(grace));
	}

	private static String trackingKey(Battle battle, BattleSide side) {
		String battleId = battle.getId() != null ? battle.getId().toLowerCase(Locale.ROOT) : "-";
		String sideId = side.getId() != null ? side.getId().toLowerCase(Locale.ROOT) : "-";
		return battleId + "|" + sideId;
	}

	public static boolean isAtJail(Player player, BattleSide side) {
		if (player == null || side == null) {
			return false;
		}
		return isNearJail(player.getLocation(), player.getWorld(), side);
	}

	static boolean isNearJail(Location playerLocation, org.bukkit.World world, BattleSide side) {
		if (playerLocation == null || side == null) {
			return false;
		}
		Location jail = side.getJail();
		if (jail == null || jail.getWorld() == null) {
			return false;
		}
		if (world == null || !world.equals(jail.getWorld())) {
			return false;
		}
		return playerLocation.distanceSquared(jail) <= JAIL_RADIUS_SQ;
	}

	private static List<Player> getOnlineParticipants(BattleSide side) {
		List<Player> online = new ArrayList<>();
		for (net.tfminecraft.simplefactions.war.battle.warband.Warband warband : side.getBands()) {
			for (Player player : warband.getPlayers()) {
				if (player != null && player.isOnline()) {
					online.add(player);
				}
			}
		}
		return online;
	}
}
