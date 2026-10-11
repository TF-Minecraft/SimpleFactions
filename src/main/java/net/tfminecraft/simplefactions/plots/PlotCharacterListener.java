package net.tfminecraft.simplefactions.plots;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import net.tfminecraft.rpcharacters.enums.Status;
import net.tfminecraft.rpcharacters.holder.RPCHolder;
import net.tfminecraft.rpcharacters.lifecycle.CharacterActivatedEvent;
import net.tfminecraft.rpcharacters.permadeath.CharacterPermakillEvent;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.espionage.EspionageService;

/** RPCharacters events, and Intrigue plus a Plots button in its Character Info menu. */
public final class PlotCharacterListener implements Listener {
    static final String CHARACTER_INFO = "§7Character Info";
    static final int INTRIGUE_SLOT = 20, PLOTS_SLOT = 24;
    static final NamespacedKey CHARACTER_ID = NamespacedKey.fromString("rpcharacters:character_id");
    static final NamespacedKey BUTTON = NamespacedKey.fromString("simplefactions:plots_button");

    @SuppressWarnings("deprecation")
    private static boolean characterInfo(InventoryView view, Player player) {
        return view.getTopInventory().getHolder() instanceof RPCHolder holder && player.equals(holder.getOwner())
                && CHARACTER_INFO.equals(view.getTitle());
    }

    /** RPCharacters fills the menu before opening it; free slots get Intrigue and, for the played character, Plots. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !characterInfo(event.getView(), player)) return;
        var inventory = event.getView().getTopInventory();
        ItemStack shown = inventory.getItem(10);
        String id = shown == null || !shown.hasItemMeta() ? null
                : shown.getItemMeta().getPersistentDataContainer().get(CHARACTER_ID, PersistentDataType.STRING);
        if (id == null) return;
        PlotCharacter active = PlotCharacters.active(player);
        boolean played = active != null && active.is(id);
        PlotCharacter character = played ? active : new PlotCharacter(id, player.getUniqueId(), player.getName(), "");
        inventory.setItem(INTRIGUE_SLOT, PlotMenu.intrigueItem(character,
                played ? Integer.valueOf(PlotService.intrigue(active)) : EspionageService.knownIntrigue(id)));
        if (!played) return;
        ItemStack button = PlotMenu.item(Material.IRON_SWORD, "§5Plots",
                "§7Your approach, your defender,", "§7threats against you and your own plots.", "§7Click to open.");
        var meta = button.getItemMeta();
        meta.getPersistentDataContainer().set(BUTTON, PersistentDataType.BYTE, (byte) 1);
        button.setItemMeta(meta);
        inventory.setItem(PLOTS_SLOT, button);
    }

    /** RPCharacters cancels every click in its menu and ignores this slot. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !characterInfo(event.getView(), player)
                || event.getClickedInventory() != event.getView().getTopInventory() || event.getSlot() != PLOTS_SLOT) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()
                || !clicked.getItemMeta().getPersistentDataContainer().has(BUTTON, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        SimpleFactions.plugin.getServer().getScheduler().runTask(SimpleFactions.plugin, () -> PlotMenu.open(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPermakill(CharacterPermakillEvent event) {
        var character = event.getCharacter();
        // RPCharacters fires before committing the death. Confirm it next tick.
        SimpleFactions.plugin.getServer().getScheduler().runTask(SimpleFactions.plugin, () -> {
            if (character.getStatus() == Status.DEAD) PlotService.characterDied(character.getId());
        });
    }

    @EventHandler
    public void onActivated(CharacterActivatedEvent event) {
        Player player = event.getOwner();
        if (player != null) SimpleFactions.plugin.getServer().getScheduler().runTask(SimpleFactions.plugin, () -> PlotService.deliver(player));
    }
}
