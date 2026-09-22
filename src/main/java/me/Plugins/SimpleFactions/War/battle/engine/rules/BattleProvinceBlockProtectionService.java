package me.Plugins.SimpleFactions.War.battle.engine.rules;



import me.Plugins.SimpleFactions.War.battle.engine.core.BattleBoundsService;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.War.battle.ui.BattlePermissions;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.SimpleFactions;

public final class BattleProvinceBlockProtectionService {
	public static final String BLOCKED =
			"§cBlock changes are disabled in the battle province while this battle is active.";

	private BattleProvinceBlockProtectionService() {}

	public static boolean isPlayerBlockChangeBlocked(Location location) {
		if (!Cache.battleProvinceBlockProtectionEnabled || location == null) {
			return false;
		}
		int provinceId = resolveProvinceId(location);
		if (provinceId <= 0) {
			return false;
		}
		for (Battle battle : BattleManager.get()) {
			if (!BattleBoundsService.applies(battle)) {
				continue;
			}
			Integer battleProvinceId = battle.getProvinceId();
			if (battleProvinceId != null && battleProvinceId == provinceId) {
				return true;
			}
		}
		return false;
	}

	static int resolveProvinceId(Location location) {
		if (!Cache.mapEnabled || location == null) {
			return -1;
		}
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin == null) {
			return -1;
		}
		ProvinceGrid grid = plugin.getProvinceGrid();
		if (grid == null) {
			return -1;
		}
		return grid.getAt(location.getBlockX(), location.getBlockZ());
	}

	public static final class Listener implements org.bukkit.event.Listener {

		@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
		public void onBlockBreak(BlockBreakEvent event) {
			handleBlockChange(event.getPlayer(), event.getBlock().getLocation(), event);
		}

		@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
		public void onBlockPlace(BlockPlaceEvent event) {
			handleBlockChange(event.getPlayer(), event.getBlock().getLocation(), event);
		}

		private static void handleBlockChange(
				Player player,
				org.bukkit.Location location,
				org.bukkit.event.Cancellable event) {
			if (isStaffBypass(player)) {
				return;
			}
			if (BattleProvinceBlockProtectionService.isPlayerBlockChangeBlocked(location)) {
				event.setCancelled(true);
				if (player != null) {
					player.sendMessage(BattleProvinceBlockProtectionService.BLOCKED);
				}
			}
		}

		private static boolean isStaffBypass(Player player) {
			return player != null && (Permissions.isAdmin(player) || BattlePermissions.isAdmin(player));
		}
	}
}
