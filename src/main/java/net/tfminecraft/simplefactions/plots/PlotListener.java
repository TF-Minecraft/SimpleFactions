package net.tfminecraft.simplefactions.plots;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import net.tfminecraft.simplefactions.SimpleFactions;

/** Plot menus, assassins' blows, deaths and messages waiting for a returning character. */
public final class PlotListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlotMenu.Holder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        var action = holder.actions.get(event.getSlot());
        if (action != null) action.accept(event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PlotMenu.Holder) event.setCancelled(true);
    }

    /** Monitors cancelled blows too: a nonlethal knockout cancels the blow that downs the target. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker != null && !attacker.equals(victim)) PlotService.damaged(victim, attacker);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        PlotService.died(event.getEntity(), event.getEntity().getKiller());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // RPCharacters loads the character after joining.
        SimpleFactions.plugin.getServer().getScheduler().runTaskLater(SimpleFactions.plugin, () -> {
            if (player.isOnline()) PlotService.deliver(player);
        }, 100L);
    }
}
