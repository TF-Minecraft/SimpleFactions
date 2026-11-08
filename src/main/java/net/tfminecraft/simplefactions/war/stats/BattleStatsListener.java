package net.tfminecraft.simplefactions.war.stats;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;

/**
 * Counts hits, kills, deaths and logouts in running battles. Finding a player's
 * side is a set lookup per warband, and a hit only bumps counters.
 */
public final class BattleStatsListener implements Listener {

	private record Place(Battle battle, BattleSide side) {}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}
		Player attacker = attacker(event.getDamager());
		if (attacker == null || attacker == victim) {
			return;
		}
		Place place = place(victim);
		BattleSide attackerSide = place == null ? null : sideOf(place.battle(), attacker);
		if (attackerSide == null) {
			return;
		}
		BattleStats stats = place.battle().stats();
		boolean friendly = attackerSide == place.side();
		if (event.isCancelled()) {
			FighterStats target = friendly || inHitCooldown(victim) ? null : stats.fighter(victim, place.side());
			if (target != null) target.blockedHits++;
			return;
		}
		FighterStats hitter = stats.fighter(attacker, attackerSide);
		if (hitter == null) return;
		double dealt = Math.min(event.getFinalDamage(), victim.getHealth() + victim.getAbsorptionAmount());
		// A NaN or negative amount from another plugin must never reach the saved battle file.
		if (!(dealt > 0) || Double.isInfinite(dealt)) return;
		if (friendly) {
			hitter.friendlyDamage += dealt;
			return;
		}
		hitter.damage += dealt;
		FighterStats target = stats.fighter(victim, place.side());
		if (target == null) return;
		target.damageTaken += dealt;
		stats.recordHit(victim.getUniqueId(), hitter.key, System.currentTimeMillis());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		Player victim = event.getEntity();
		Place place = place(victim);
		if (place == null) {
			return;
		}
		BattleStats stats = place.battle().stats();
		FighterStats dead = stats.fighter(victim, place.side());
		if (dead == null) return;
		dead.deaths++;
		FighterStats lastHitter = stats.takeLastHitter(victim.getUniqueId(), System.currentTimeMillis());
		Player killer = victim.getKiller();
		BattleSide killerSide = killer == null || killer == victim ? null : sideOf(place.battle(), killer);
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

	/** Before the warband lets the leaving character go. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		Members.session(player).ifPresent(session -> logout(player, session.ref()));
	}

	/** A character switch mid-battle leaves the warband like a logout; quits are counted above. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onSessionEnd(CharacterSessionEndEvent event) {
		if (event.getReason() == CharacterSessionEndEvent.Reason.SWITCH) {
			logout(event.getPlayer(), event.getSession().ref());
		}
	}

	private static void logout(Player player, CharacterRef character) {
		UUID owner = player.getUniqueId();
		for (Battle battle : BattleManager.get()) {
			if (!battle.hasStarted()) {
				continue;
			}
			for (BattleSide side : battle.getSides()) {
				for (Warband band : side.getBands()) {
					if (band.getMemberCharacter(owner).filter(character::equals).isPresent()) {
						FighterStats fighter = battle.stats().fighter(Members.key(character), player, side);
						if (fighter != null) fighter.logouts++;
						return;
					}
				}
			}
		}
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

	private static Place place(Player player) {
		for (Battle battle : BattleManager.get()) {
			BattleSide side = battle.hasStarted() ? sideOf(battle, player) : null;
			if (side != null) {
				return new Place(battle, side);
			}
		}
		return null;
	}

	/** Member-set lookup per warband; only the warband that has the player checks its character. */
	private static BattleSide sideOf(Battle battle, Player player) {
		for (BattleSide side : battle.getSides()) {
			for (Warband band : side.getBands()) {
				if (band.hasMember(player)) {
					return side;
				}
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
