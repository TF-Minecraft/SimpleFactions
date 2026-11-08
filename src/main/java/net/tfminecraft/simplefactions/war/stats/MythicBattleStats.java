package net.tfminecraft.simplefactions.war.stats;

import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.bukkit.events.MythicAuraStartEvent;
import io.lumine.mythic.bukkit.events.MythicHealMechanicEvent;
import io.lumine.mythic.core.skills.mechanics.OnJumpMechanic;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;

/**
 * Reads skill heals and crowd control from MythicMobs, the only place that names the
 * caster. Registered only when MythicMobs is installed.
 */
public final class MythicBattleStats implements Listener {
	/** TFMC status auras that control a target, by kind; a jump-cancelling aura is a root. */
	static final Map<String, String> CONTROL_AURAS = Map.of(
			"slowed", "slow",
			"frozen", "root",
			"weakened", "weaken",
			"silenced", "silence",
			"wounded", "wound",
			"stun_aura", "stun",
			"vine_wave_stun", "stun");

	/** A jump-cancelling aura: a root on an enemy, a harmless lock when a support spell holds an ally. */
	static final String JUMP_LOCK = "jumplock";

	private final BattleStatsListener stats;

	public MythicBattleStats(BattleStatsListener stats) {
		this.stats = stats;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHeal(MythicHealMechanicEvent event) {
		if (!BattleManager.get().isEmpty() && event.getEntity() instanceof Player healer
				&& event.getTarget() instanceof Player target) {
			stats.expectHeal(healer, target);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onAura(MythicAuraStartEvent event) {
		if (BattleManager.get().isEmpty()) {
			return;
		}
		String kind = event.getAura() instanceof OnJumpMechanic ? JUMP_LOCK
				: CONTROL_AURAS.get(String.valueOf(event.getAuraName()).toLowerCase(java.util.Locale.ROOT));
		if (kind == null || event.getCaster() == null) {
			return;
		}
		Entity caster = bukkit(event.getCaster().getEntity());
		Entity target = event.getTargetEntity().map(MythicBattleStats::bukkit).orElse(null);
		if (caster instanceof Player source && target instanceof Player victim) {
			stats.control(source, victim, kind, event.getDuration() * 50L);
		}
	}

	private static Entity bukkit(AbstractEntity entity) {
		return entity == null ? null : entity.getBukkitEntity();
	}
}
