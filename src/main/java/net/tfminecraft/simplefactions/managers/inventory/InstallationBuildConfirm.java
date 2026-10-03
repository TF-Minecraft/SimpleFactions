package net.tfminecraft.simplefactions.managers.inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates;
import net.tfminecraft.simplefactions.guild.hub.InfrastructureMenuCopy;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.ConstructResult;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Faction;

/** Confirmation chest for /faction construct, with the installation preview painted in. */
public final class InstallationBuildConfirm {
    private static final AtomicLong TOKENS = new AtomicLong();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private InstallationBuildConfirm() {}

    private record Pending(
            Faction faction,
            InstallationKind kind,
            String name,
            int province,
            int blockX,
            int blockZ,
            long token) {}

    public static boolean open(
            Player player,
            Faction faction,
            InstallationKind kind,
            String name,
            int province,
            int blockX,
            int blockZ) {
        SimpleFactions plugin = SimpleFactions.plugin;
        InventoryManager menus = FactionManager.getInv();
        if (player == null || faction == null || kind == null || plugin == null || !plugin.isEnabled() || menus == null) {
            if (player != null) {
                player.sendMessage("§cCould not open the construction confirmation.");
            }
            return false;
        }
        long token = TOKENS.incrementAndGet();
        PENDING.put(player.getUniqueId(), new Pending(faction, kind, name, province, blockX, blockZ, token));
        menus.confirming.put(player, faction);

        Inventory inventory = plugin.getServer().createInventory(null, 27, "§7Confirm Action");
        inventory.setItem(13, paper(plugin, name, token, List.of(InfrastructureMenuCopy.calculating())));
        inventory.setItem(11, menus.createButton("confirm", "installation_build", "build"));
        inventory.setItem(15, menus.createButton("cancel", "installation_build", "build"));
        player.openInventory(inventory);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> lore;
            boolean wrote = false;
            try {
                HubEstimates.InstallationPreview preview = HubEstimates.previewInstallation(
                        plugin.getProvinceManager(), faction, province, kind);
                wrote = preview != HubEstimates.InstallationPreview.NONE;
                lore = InfrastructureMenuCopy.installationPreview(preview);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Installation preview failed for " + kind + " in province " + province,
                        ex);
                wrote = true;
                lore = List.of("§cIncome estimate unavailable");
            }
            if (!plugin.isEnabled()) {
                return;
            }
            List<String> lines = lore;
            boolean restore = wrote;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (restore) {
                    restoreTrade(plugin);
                }
                publish(player, token, lines);
            });
        });
        return true;
    }

    public static void accept(Player player) {
        if (player == null) {
            return;
        }
        Pending pending = PENDING.remove(player.getUniqueId());
        if (pending == null || pending.faction == null) {
            player.sendMessage("§cThat construction is no longer waiting.");
            player.closeInventory();
            return;
        }
        if (pending.faction.getLeader() == null
                || !pending.faction.getLeader().equalsIgnoreCase(player.getName())) {
            player.sendMessage("§cYou need to be a faction leader to construct installations");
            player.closeInventory();
            return;
        }
        if (pending.faction.getInstallationHandler() == null) {
            player.sendMessage("§cCould not construct that installation.");
            player.closeInventory();
            return;
        }
        ConstructResult result = pending.faction.getInstallationHandler().construct(
                pending.kind, pending.name, pending.province, pending.blockX, pending.blockZ);
        player.sendMessage(result.getMessage());
        player.playSound(
                player,
                result.isSuccess() ? Sound.BLOCK_ANVIL_USE : Sound.BLOCK_NOTE_BLOCK_BIT,
                1f,
                1f);
        player.closeInventory();
    }

    public static void cancel(Player player) {
        if (player == null) {
            return;
        }
        PENDING.remove(player.getUniqueId());
        player.closeInventory();
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }

    public static void forget(Player player) {
        if (player != null) {
            PENDING.remove(player.getUniqueId());
        }
    }

    private static void restoreTrade(SimpleFactions plugin) {
        ProvinceManager live = plugin.getProvinceManager();
        if (live == null) {
            return;
        }
        try {
            live.recalculate();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not restore trade after an installation preview", ex);
        }
    }

    @SuppressWarnings("deprecation")
    private static void publish(Player player, long token, List<String> lore) {
        if (!player.isOnline() || player.getOpenInventory() == null) {
            return;
        }
        if (!"§7Confirm Action".equals(player.getOpenInventory().getTitle())) {
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        ItemStack item = inventory.getItem(13);
        if (!matches(item, token)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(13, item);
    }

    @SuppressWarnings("deprecation")
    private static ItemStack paper(SimpleFactions plugin, String name, long token, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name == null || name.isBlank() ? "§6Installation" : "§6" + name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tokenKey(plugin), PersistentDataType.LONG, token);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean matches(ItemStack item, long token) {
        SimpleFactions plugin = SimpleFactions.plugin;
        if (plugin == null || item == null || !item.hasItemMeta()) {
            return false;
        }
        Long stamped = item.getItemMeta().getPersistentDataContainer().get(tokenKey(plugin), PersistentDataType.LONG);
        return stamped != null && stamped == token;
    }

    private static NamespacedKey tokenKey(SimpleFactions plugin) {
        return new NamespacedKey(plugin, "installation_preview");
    }
}
