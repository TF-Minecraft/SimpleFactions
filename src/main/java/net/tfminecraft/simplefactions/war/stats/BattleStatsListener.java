package net.tfminecraft.simplefactions.war.stats;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
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
 * Counts hits, kills, assists, deaths, healing, crowd control and logouts in running
 * battles. Every handler returns at once when no battle runs; finding a player's side
 * is a set lookup per warband, and an event only bumps counters.
 */
public final class BattleStatsListener implements Listener {

	private record Place(Battle battle, BattleSide side) {}

	/** Who is behind the next damage to a victim that arrives without an attacker, this tick only. */
	private record Pending(Player attacker, int tick) {}

	/** Damage causes with no attacker that still come from the fight: burns, poison, falls, blasts. */
	static final java.util.Set<EntityDamageEvent.DamageCause> FIGHT_CAUSES = java.util.EnumSet.of(
			EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.LAVA,
			EntityDamageEvent.DamageCause.POISON, EntityDamageEvent.DamageCause.WITHER, EntityDamageEvent.DamageCause.MAGIC,
			EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
			EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, EntityDamageEvent.DamageCause.CONTACT);
	/** Most crowd control one aura application can earn, so a long or endless aura cannot inflate it. */
	static final long MAX_CONTROL_MS = 30_000L;

	private final Map<UUID, Pending> pendingAttackers = new HashMap<>();
	/** Victims whose hit VehicleFramework dealt again this tick; the cancelled original is not a blocked hit. */
	private final Map<UUID, Integer> redirectedAt = new HashMap<>();
	private Pending pendingHealer;
	private UUID pendingHealTarget;

	/**
	 * VehicleFramework cancels hits on seated players and vehicle roots and deals them again
	 * without an attacker; remember the attacker so the repeated hit is credited.
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSeatedHit(EntityDamageByEntityEvent event) {
		if (BattleManager.get().isEmpty() || !(event.getEntity() instanceof Player victim) || !victim.isInsideVehicle()) {
			return;
		}
		Player attacker = attacker(event);
		if (attacker != null && attacker != victim) {
			pendingAttackers.put(victim.getUniqueId(), new Pending(attacker, Bukkit.getCurrentTick()));
		}
	}

	/**
	 * Called by the VehicleFramework bridge just before VehicleFramework deals damage without a
	 * source: vehicle weapons name the gunner, a redirected hit names the original attacker.
	 */
	void vehicleHit(Entity victimEntity, Entity damager) {
		if (BattleManager.get().isEmpty() || !(victimEntity instanceof Player victim)) {
			return;
		}
		Player attacker = player(damager);
		if (attacker != null && attacker != victim) {
			pendingAttackers.put(victim.getUniqueId(), new Pending(attacker, Bukkit.getCurrentTick()));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}
		Player attacker = attacker(event);
		if (attacker == null || attacker == victim) {
			return;
		}
		Place place = place(victim);
		BattleSide attackerSide = place == null ? null : sideOf(place.battle(), attacker);
		if (attackerSide == null) {
			return;
		}
		if (event.isCancelled()) {
			// A seated player's hit was cancelled only to be dealt again; that is not a blocked hit.
			Integer redirectedTick = redirectedAt.get(victim.getUniqueId());
			boolean redirected = redirectedTick != null && redirectedTick == Bukkit.getCurrentTick();
			// Stopped before VehicleFramework could deal it again: nothing will use the remembered attacker.
			if (!redirected) pendingAttackers.remove(victim.getUniqueId());
			boolean blocked = attackerSide != place.side() && !inHitCooldown(victim) && !redirected;
			FighterStats target = blocked ? place.battle().stats().fighter(victim, place.side()) : null;
			if (target != null) target.blockedHits++;
			return;
		}
		credit(place, victim, attacker, attackerSide, event.getFinalDamage());
	}

	/**
	 * Damage with no attacker: a hit VehicleFramework dealt again, vehicle weapons, fire,
	 * poison, falls and explosions. It goes to the attacker remembered for this tick, or
	 * else to the enemy who hit the victim last within the window.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSourcelessDamage(EntityDamageEvent event) {
		if (event instanceof EntityDamageByEntityEvent || BattleManager.get().isEmpty()
				|| !(event.getEntity() instanceof Player victim)) {
			return;
		}
		Pending pending = pendingAttackers.remove(victim.getUniqueId());
		Place place = place(victim);
		if (place == null) {
			return;
		}
		if (pending != null && pending.tick() == Bukkit.getCurrentTick()) {
			// VehicleFramework dealt the hit again, so its cancelled original is not a blocked hit.
			redirectedAt.put(victim.getUniqueId(), pending.tick());
			BattleSide attackerSide = sideOf(place.battle(), pending.attacker());
			if (attackerSide != null) credit(place, victim, pending.attacker(), attackerSide, event.getFinalDamage());
			return;
		}
		if (!FIGHT_CAUSES.contains(event.getCause())) {
			return;
		}
		BattleStats stats = place.battle().stats();
		FighterStats lastHitter = stats.lastHitter(victim.getUniqueId(), System.currentTimeMillis());
		FighterStats target = lastHitter == null ? null : stats.fighter(victim, place.side());
		if (target == null) return;
		double dealt = dealt(victim, event.getFinalDamage());
		lastHitter.damage += dealt;
		target.damageTaken += dealt;
	}

	private static void credit(Place place, Player victim, Player attacker, BattleSide attackerSide, double amount) {
		BattleStats stats = place.battle().stats();
		FighterStats hitter = stats.fighter(attacker, attackerSide);
		if (hitter == null) return;
		double dealt = dealt(victim, amount);
		if (attackerSide == place.side()) {
			hitter.friendlyDamage += dealt;
			return;
		}
		hitter.damage += dealt;
		FighterStats target = stats.fighter(victim, place.side());
		if (target == null) return;
		target.damageTaken += dealt;
		if (dealt > 0) stats.recordHit(victim.getUniqueId(), hitter.key, System.currentTimeMillis());
	}

	/** Damage that actually landed; NaN, negative or endless amounts from other plugins count as 0. */
	private static double dealt(Player victim, double amount) {
		double dealt = Math.min(amount, victim.getHealth() + victim.getAbsorptionAmount());
		return dealt > 0 && !Double.isInfinite(dealt) ? dealt : 0.0;
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
		List<FighterStats> hitters = stats.takeHitters(victim.getUniqueId(), System.currentTimeMillis());
		Player killer = victim.getKiller();
		BattleSide killerSide = killer == null || killer == victim ? null : sideOf(place.battle(), killer);
		FighterStats credited = killerSide != null ? stats.fighter(killer, killerSide)
				: hitters.isEmpty() ? null : hitters.get(hitters.size() - 1);
		boolean teamKill = credited != null && credited.side.equalsIgnoreCase(dead.side);
		for (FighterStats hitter : hitters) {
			if (hitter != credited && !teamKill) hitter.assists++;
		}
		if (credited == null) {
			return;
		}
		if (teamKill) {
			credited.teamKills++;
		} else {
			credited.kills++;
		}
	}

	/**
	 * Skill heals arrive as a MythicMobs heal (naming the healer) followed by this event in
	 * the same call; other magic heals (lifesteal, healing potions) count as self-healing.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onRegain(EntityRegainHealthEvent event) {
		EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
		if ((reason != EntityRegainHealthEvent.RegainReason.CUSTOM && reason != EntityRegainHealthEvent.RegainReason.MAGIC)
				|| BattleManager.get().isEmpty() || !(event.getEntity() instanceof Player target)) {
			return;
		}
		Pending healer = target.getUniqueId().equals(pendingHealTarget) && pendingHealer.tick() == Bukkit.getCurrentTick()
				? pendingHealer : null;
		pendingHealer = null;
		pendingHealTarget = null;
		Place place = place(target);
		if (place == null) {
			return;
		}
		double healed = Math.min(event.getAmount(), maxHealth(target) - target.getHealth());
		if (!(healed > 0)) return;
		BattleStats stats = place.battle().stats();
		if (healer == null || healer.attacker() == target) {
			FighterStats self = stats.fighter(target, place.side());
			if (self != null) self.selfHealing += healed;
			return;
		}
		BattleSide healerSide = sideOf(place.battle(), healer.attacker());
		FighterStats medic = healerSide == place.side() ? stats.fighter(healer.attacker(), healerSide) : null;
		if (medic != null) medic.healing += healed;
	}

	/** Called by the MythicMobs bridge when a skill heal is about to land. */
	void expectHeal(Player healer, Player target) {
		pendingHealer = new Pending(healer, Bukkit.getCurrentTick());
		pendingHealTarget = target.getUniqueId();
	}

	/** Called by the MythicMobs bridge when a caster puts crowd control on a player. */
	void control(Player caster, Player target, String kind, long durationMs) {
		if (caster == target) {
			return;
		}
		Place place = place(target);
		BattleSide casterSide = place == null ? null : sideOf(place.battle(), caster);
		// Support spells lock an ally's jump while they help them; that is not crowd control.
		if (casterSide == null || (casterSide == place.side() && MythicBattleStats.JUMP_LOCK.equals(kind))) {
			return;
		}
		BattleStats stats = place.battle().stats();
		FighterStats source = stats.fighter(caster, casterSide);
		FighterStats victim = stats.fighter(target, place.side());
		if (source == null || victim == null) return;
		String counted = MythicBattleStats.JUMP_LOCK.equals(kind) ? "root" : kind;
		double seconds = stats.creditControl(target.getUniqueId(), counted, System.currentTimeMillis(),
				Math.min(durationMs, MAX_CONTROL_MS));
		if (casterSide == place.side()) {
			source.friendlyCc += seconds;
		} else {
			source.cc += seconds;
			victim.ccTaken += seconds;
		}
	}

	/** Before the warband lets the leaving character go. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		pendingAttackers.remove(player.getUniqueId());
		redirectedAt.remove(player.getUniqueId());
		Members.session(player).ifPresent(session -> logout(player, session.ref()));
	}

	/** A character switch mid-battle leaves the warband like a logout; quits are counted above. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onSessionEnd(CharacterSessionEndEvent event) {
		if (event.getReason() == CharacterSessionEndEvent.Reason.SWITCH) {
			logout(event.getPlayer(), event.getSession().ref());
			// Hits on the old character must not credit kills or assists against the next one.
			UUID account = event.getPlayer().getUniqueId();
			pendingAttackers.remove(account);
			for (Battle battle : BattleManager.get()) {
				if (battle.getStats() != null) battle.getStats().forgetVictim(account);
			}
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

	/** Everyone in the battle when it starts is a fighter, even if they leave before doing anything. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onStarted(net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent event) {
		Battle battle = BattleManager.getByString(event.getBattleId());
		BattleStats stats = battle == null ? null : battle.getStats();
		if (stats == null) {
			return;
		}
		for (BattleSide side : battle.getSides()) {
			for (Warband band : side.getBands()) {
				for (Player player : band.getPlayers()) {
					stats.fighter(player, side);
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

	/**
	 * For code that removes a running battle without an end event (war end, a raid
	 * called off): archives what was fought, and never lets a stats failure stop the removal.
	 */
	public static void archiveUnfinished(Battle battle, String reason) {
		if (battle == null || !battle.hasStarted()) {
			return;
		}
		try {
			finish(battle, null, reason);
		} catch (RuntimeException failed) {
			Bukkit.getLogger().log(java.util.logging.Level.WARNING,
					"[SimpleFactions] Could not archive battle stats for " + battle.getId(), failed);
		}
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

	/** The player behind a hit: the damage source's causing entity (TNT, clouds, projectiles) or the damager. */
	private static Player attacker(EntityDamageByEntityEvent event) {
		var source = event.getDamageSource();
		Player causing = source == null ? null : player(source.getCausingEntity());
		return causing != null ? causing : player(event.getDamager());
	}

	private static Player player(Entity entity) {
		if (entity instanceof Player player) {
			return player;
		}
		if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
			return shooter;
		}
		return null;
	}

	// The attribute registry is not needed for a plain max-health read.
	@SuppressWarnings("deprecation")
	private static double maxHealth(Player player) {
		return player.getMaxHealth();
	}

	/** Vanilla ignores weaker hits for the first half of the hurt cooldown; those are not "blocked". */
	private static boolean inHitCooldown(Player victim) {
		return victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2;
	}
}
