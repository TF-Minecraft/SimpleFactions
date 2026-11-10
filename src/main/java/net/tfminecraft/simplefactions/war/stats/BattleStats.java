package net.tfminecraft.simplefactions.war.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;

/**
 * Counters for a battle in progress. Saved with the battle file, so a restart
 * mid-battle keeps them; the short-lived hit and crowd-control memory is not saved.
 */
public final class BattleStats {
	/** How long an enemy hit still earns the kill, an assist, or credit for damage with no attacker. */
	static final long LAST_HIT_WINDOW_MS = 10_000L;

	public final Map<String, FighterStats> fighters = new LinkedHashMap<>();
	/** Victim account → enemy fighter keys that hit them, with the time of their last hit. */
	private transient Map<UUID, Map<String, Long>> recentHitters;
	/** Victim account → crowd-control kind → when the control already credited ends. */
	private transient Map<UUID, Map<String, Long>> controlledUntil;

	/** The fighter for this player on this side, created on first sight; null without a character. */
	public FighterStats fighter(Player player, BattleSide side) {
		return fighter(Members.key(player), player, side);
	}

	/** As above for a known character key, e.g. a session that is ending. */
	public FighterStats fighter(String key, Player player, BattleSide side) {
		if (key == null || side == null) return null;
		FighterStats fighter = fighters.get(key);
		if (fighter == null) {
			fighter = new FighterStats();
			fighter.key = key;
			fighter.name = Members.name(player);
			fighter.className = FighterClass.of(player);
			Faction faction = FactionManager.getByMember(player);
			fighter.faction = faction == null ? null : faction.getName();
			fighter.factionId = faction == null ? null : faction.getId();
			fighter.factionFoundedAt = faction == null || faction.getFoundedAt() <= 0 ? null : faction.getFoundedAt();
			fighters.put(key, fighter);
		}
		fighter.side = side.getId();
		return fighter;
	}

	void recordHit(UUID victimId, String attackerKey, long now) {
		recentHitters().computeIfAbsent(victimId, id -> new LinkedHashMap<>()).put(attackerKey, now);
	}

	/** The enemy fighter who hit last within the window, without forgetting the hits. */
	FighterStats lastHitter(UUID victimId, long now) {
		Map<String, Long> hits = recentHitters().get(victimId);
		String latest = null;
		long at = Long.MIN_VALUE;
		if (hits != null) {
			for (Map.Entry<String, Long> hit : hits.entrySet()) {
				if (now - hit.getValue() <= LAST_HIT_WINDOW_MS && hit.getValue() >= at) {
					latest = hit.getKey();
					at = hit.getValue();
				}
			}
		}
		return latest == null ? null : fighters.get(latest);
	}

	/** Every enemy fighter who hit the victim within the window, latest last; forgets them all. */
	List<FighterStats> takeHitters(UUID victimId, long now) {
		Map<String, Long> hits = recentHitters().remove(victimId);
		List<FighterStats> result = new ArrayList<>();
		if (hits != null) {
			hits.entrySet().stream()
					.filter(hit -> now - hit.getValue() <= LAST_HIT_WINDOW_MS)
					.sorted(Map.Entry.comparingByValue())
					.forEach(hit -> result.add(fighters.get(hit.getKey())));
		}
		return result;
	}

	/**
	 * Seconds of new control for this kind on this victim. Refreshing a control that is
	 * still running only counts the time it adds, so re-applied auras are not counted twice.
	 */
	double creditControl(UUID victimId, String kind, long now, long durationMs) {
		Map<String, Long> kinds = controlledUntil().computeIfAbsent(victimId, id -> new HashMap<>());
		long until = now + Math.max(0L, durationMs);
		long credited = Math.max(now, kinds.getOrDefault(kind, 0L));
		if (until <= credited) {
			return 0.0;
		}
		kinds.put(kind, until);
		return (until - credited) / 1000.0;
	}

	/** The account switched character: its hit and control history belongs to the old one. */
	void forgetVictim(UUID account) {
		recentHitters().remove(account);
		controlledUntil().remove(account);
	}

	private Map<UUID, Map<String, Long>> recentHitters() {
		if (recentHitters == null) {
			recentHitters = new HashMap<>();
		}
		return recentHitters;
	}

	private Map<UUID, Map<String, Long>> controlledUntil() {
		if (controlledUntil == null) {
			controlledUntil = new HashMap<>();
		}
		return controlledUntil;
	}
}
