package net.tfminecraft.simplefactions.war.stats;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;

/**
 * Counters for a battle in progress. Saved with the battle file, so a restart
 * mid-battle keeps them; the last-hit memory is not saved.
 */
public final class BattleStats {
	/** How long an enemy hit still earns the kill when the death has no killer. */
	static final long LAST_HIT_WINDOW_MS = 10_000L;

	public final Map<String, FighterStats> fighters = new LinkedHashMap<>();
	private transient Map<UUID, LastHit> lastHits;

	private record LastHit(String attackerKey, long at) {}

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
			fighters.put(key, fighter);
		}
		fighter.side = side.getId();
		return fighter;
	}

	void recordHit(UUID victimId, String attackerKey, long now) {
		hits().put(victimId, new LastHit(attackerKey, now));
	}

	/** The fighter whose enemy hit landed last within the window, and forgets it. */
	FighterStats takeLastHitter(UUID victimId, long now) {
		LastHit hit = hits().remove(victimId);
		if (hit == null || now - hit.at() > LAST_HIT_WINDOW_MS) {
			return null;
		}
		return fighters.get(hit.attackerKey());
	}

	private Map<UUID, LastHit> hits() {
		if (lastHits == null) {
			lastHits = new HashMap<>();
		}
		return lastHits;
	}
}
