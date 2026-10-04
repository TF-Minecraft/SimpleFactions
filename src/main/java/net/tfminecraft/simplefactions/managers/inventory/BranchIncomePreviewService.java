package net.tfminecraft.simplefactions.managers.inventory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.guild.income.BranchIncomePreview;
import net.tfminecraft.simplefactions.keys.Keys;

/** Paints branch up/down income onto an open guild menu after the preview finishes. */
public final class BranchIncomePreviewService {
    private static final AtomicLong TOKENS = new AtomicLong();
    private static final GuildCreator CREATOR = new GuildCreator();

    private BranchIncomePreviewService() {}

    public static void schedule(
            Player player,
            Inventory inventory,
            int slot,
            BranchIncomePreview.Prepared prepared,
            Guild guild,
            Branch branch,
            int levelDelta) {
        if (player == null || inventory == null || prepared == null || guild == null || branch == null) {
            return;
        }
        ItemStack item = inventory.getItem(slot);
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        SimpleFactions plugin = SimpleFactions.plugin;
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        int level = branch.getLevel();
        boolean upgrade = levelDelta > 0;
        Map<GuildModifier, Double> current = BranchIncomePreview.modifiers(guild);
        Map<GuildModifier, Double> hypothetical = BranchIncomePreview.adjust(current, branch, level, levelDelta);
        double taxFraction = BranchIncomePreview.taxFraction(guild);
        String guildId = guild.getId();
        String branchId = branch.getId();
        long token = TOKENS.incrementAndGet();
        stamp(item, token);
        inventory.setItem(slot, item);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double delta;
            try {
                delta = BranchIncomePreview.estimate(prepared, guild, current, hypothetical, taxFraction);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Branch income preview failed for guild " + guildId + " branch " + branchId,
                        ex);
                delta = Double.NaN;
            }
            if (!plugin.isEnabled()) {
                return;
            }
            double result = delta;
            Bukkit.getScheduler().runTask(plugin, () -> publish(
                    player, inventory, slot, token, guild, branch, upgrade, level, result));
        });
    }

    private static void publish(
            Player player,
            Inventory inventory,
            int slot,
            long token,
            Guild guild,
            Branch branch,
            boolean upgrade,
            int level,
            double delta) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
            return;
        }
        if (branch.getLevel() != level) {
            return;
        }
        ItemStack item = inventory.getItem(slot);
        if (!matches(item, token, branch.getId(), upgrade)) {
            return;
        }
        if (upgrade) {
            CREATOR.writeUpgradeEstimate(item, guild, branch, delta);
        } else {
            CREATOR.writeDowngradeEstimate(item, guild, branch, delta);
        }
        inventory.setItem(slot, item);
    }

    private static void stamp(ItemStack item, long token) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.BRANCH_PREVIEW, PersistentDataType.LONG, token);
        item.setItemMeta(meta);
    }

    private static boolean matches(ItemStack item, long token, String branchId, boolean upgrade) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Long stamped = meta.getPersistentDataContainer().get(Keys.BRANCH_PREVIEW, PersistentDataType.LONG);
        String id = meta.getPersistentDataContainer().get(Keys.BRANCH_ID, PersistentDataType.STRING);
        Boolean flag = meta.getPersistentDataContainer().get(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN);
        return stamped != null
                && stamped == token
                && branchId.equals(id)
                && flag != null
                && flag == upgrade;
    }
}
