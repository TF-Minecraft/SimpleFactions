package net.tfminecraft.simplefactions.war.stats;

import java.time.Instant;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

/**
 * Counts hits, kills, deaths and logouts in running battles. Every handler
 * returns at once when no battle is running, and a hit only bumps counters.
 */
public final class BattleStatsListener implements Listener {

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}
		Player attacker = attacker(event.getDamager());
		if (attacker == null || attacker == victim) {
			return;
		}
		Battle battle = runningBattle(victim);
		BattleSide attackerSide = battle == null ? null : battle.getSideByPlayer(attacker);
		if (attackerSide == null) {
			return;
		}
		BattleSide victimSide = battle.getSideByPlayer(victim);
		BattleStats stats = battle.stats();
		boolean friendly = attackerSide == victimSide;
		if (event.isCancelled()) {
			FighterStats target = friendly || inHitCooldown(victim) ? null : stats.fighter(victim, victimSide);
			if (target != null) target.blockedHits++;
			return;
		}
		FighterStats hitter = stats.fighter(attacker, attackerSide);
		if (hitter == null) return;
		double dealt = Math.max(0.0, Math.min(event.getFinalDamage(),
				victim.getHealth() + victim.getAbsorptionAmount()));
		if (friendly) {
			hitter.friendlyDamage += dealt;
			return;
		}
		hitter.damage += dealt;
		FighterStats target = stats.fighter(victim, victimSide);
		if (target == null) return;
		target.damageTaken += dealt;
		stats.recordHit(victim.getUniqueId(), hitter.key, System.currentTimeMillis());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		Player victim = event.getEntity();
		Battle battle = runningBattle(victim);
		if (battle == null) {
			return;
		}
		BattleStats stats = battle.stats();
		FighterStats dead = stats.fighter(victim, battle.getSideByPlayer(victim));
		if (dead == null) return;
		dead.deaths++;
		FighterStats lastHitter = stats.takeLastHitter(victim.getUniqueId(), System.currentTimeMillis());
		Player killer = victim.getKiller();
		BattleSide killerSide = killer == null || killer == victim ? null : battle.getSideByPlayer(killer);
		FighterStats credited = killerSide == null ? lastHitter : stats.fighter(killer, killerSide);
		if (credited == null) {
			return;
		}
		if (credited.side.equalsIgnoreCase(dead.side)) {
			credited.teamKills++;
		} else {
			credited.kills++;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		Battle battle = runningBattle(player);
		if (battle == null) {
			return;
		}
		FighterStats fighter = battle.stats().fighter(player, battle.getSideByPlayer(player));
		if (fighter != null) fighter.logouts++;
	}

	/** Runs before the campaign outcome removes the battle. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onEnded(BattleEndedEvent event) {
		Battle battle = BattleManager.getByString(event.getBattleId());
		if (battle != null) {
			finish(battle, event.getWinningSideId(), String.valueOf(event.getEndReason()));
		}
	}

	/**
	 * Archives the battle's stats and tells its fighters. The battle's counters are
	 * taken, so a second end event for the same battle does nothing.
	 */
	public static BattleRecord finish(Battle battle, String winningSideId, String endReason) {
		BattleStats stats = battle.takeStats();
		if (stats == null) {
			return null;
		}
		for (BattleSide side : battle.getSides()) {
			for (Warband band : side.getBands()) {
				for (Player player : band.getPlayers()) {
					stats.fighter(player, side);
				}
			}
		}
		BattleRecord record = BattleRecord.of(battle, stats, winningSideId, endReason, Instant.now());
		BattleStatsArchive.save(record);
		BattleStatsView.announce(record);
		return record;
	}

	private static Battle runningBattle(Player player) {
		for (Battle battle : BattleManager.get()) {
			if (battle.hasStarted() && battle.getSideByPlayer(player) != null) {
				return battle;
			}
		}
		return null;
	}

	private static Player attacker(Entity damager) {
		if (damager instanceof Player player) {
			return player;
		}
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
			return shooter;
		}
		return null;
	}

	/** Vanilla ignores weaker hits for the first half of the hurt cooldown; those are not "blocked". */
	private static boolean inHitCooldown(Player victim) {
		return victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2;
	}
}
