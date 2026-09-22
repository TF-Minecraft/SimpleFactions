package me.Plugins.SimpleFactions.War.battle.engine.rules;

import java.util.function.DoubleSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;

/**
 * Scales {@link org.bukkit.event.player.PlayerItemDamageEvent} amounts for started battles.
 * Sub-1 scaled damage uses a probability so a typical 1-damage hit is not always cancelled.
 */
public final class BattleItemDurability {

	private BattleItemDurability() {
	}

	public static int apply(int damage, double multiplier, DoubleSupplier random) {
		if (damage <= 0) {
			return 0;
		}
		double factor = clampUnit(multiplier);
		if (factor <= 0.0) {
			return 0;
		}
		if (factor >= 1.0) {
			return damage;
		}
		double scaled = damage * factor;
		if (scaled < 1.0) {
			double roll = random == null ? 1.0 : random.getAsDouble();
			return roll < scaled ? damage : 0;
		}
		return Math.max(1, (int) Math.round(scaled));
	}

	static double clampUnit(double value) {
		if (value < 0.0) {
			return 0.0;
		}
		if (value > 1.0) {
			return 1.0;
		}
		return value;
	}

	public static final class Listener implements org.bukkit.event.Listener {

		@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
		public void onItemDamage(PlayerItemDamageEvent event) {
			Player player = event.getPlayer();
			Battle battle = BattleManager.getBattleByPlayer(player);
			if (battle == null || !battle.hasStarted()) {
				return;
			}
			int next = BattleItemDurability.apply(
					event.getDamage(),
					Cache.battleItemDurabilityMultiplier,
					Math::random);
			if (next <= 0) {
				event.setCancelled(true);
				return;
			}
			event.setDamage(next);
		}
	}
}
