package me.Plugins.SimpleFactions.War.campaign.ui;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.Holder.CampaignInventoryHolder;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.Managers.Inventory.CampaignView;
import me.Plugins.SimpleFactions.enums.SFGUI;

public final class CampaignViewRefreshService implements Listener {
	private static final CampaignViewRefreshService INSTANCE = new CampaignViewRefreshService();

	private final Map<UUID, Integer> viewers = new ConcurrentHashMap<>();
	private BukkitRunnable task;

	private CampaignViewRefreshService() {}

	public static void start() {
		INSTANCE.startTask();
		SimpleFactions.plugin.getServer().getPluginManager().registerEvents(INSTANCE, SimpleFactions.plugin);
	}

	public static void stop() {
		if (INSTANCE.task != null) {
			INSTANCE.task.cancel();
			INSTANCE.task = null;
		}
		INSTANCE.viewers.clear();
	}

	public static void register(Player player, int warId) {
		if (player != null) {
			INSTANCE.viewers.put(player.getUniqueId(), warId);
		}
	}

	public static void unregister(Player player) {
		if (player != null) {
			INSTANCE.viewers.remove(player.getUniqueId());
		}
	}

	private void startTask() {
		if (task != null) {
			task.cancel();
		}
		task = new BukkitRunnable() {
			@Override
			public void run() {
				tick();
			}
		};
		task.runTaskTimer(SimpleFactions.plugin, 20L, 20L);
	}

	private void tick() {
		if (FactionManager.inv == null) {
			return;
		}
		CampaignView campaignView = FactionManager.inv.campaignView;
		Iterator<Map.Entry<UUID, Integer>> iterator = viewers.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			Player player = SimpleFactions.plugin.getServer().getPlayer(entry.getKey());
			int warId = entry.getValue();
			if (player == null || !player.isOnline() || !CampaignView.isViewingCampaign(player, warId)) {
				iterator.remove();
				continue;
			}
			War war = WarManager.getById(warId);
			if (war == null || !war.isActive()) {
				iterator.remove();
				continue;
			}
			campaignView.campaignView(player, war, false);
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		if (event.getInventory().getHolder() instanceof CampaignInventoryHolder holder
				&& holder.getType() == SFGUI.CAMPAIGN_VIEW) {
			unregister(player);
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		unregister(event.getPlayer());
	}
}
