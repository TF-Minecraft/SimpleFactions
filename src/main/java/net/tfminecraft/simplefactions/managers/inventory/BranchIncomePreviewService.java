package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    /** One branch button to paint: its slot, its branch, and up (+1) or down (-1). */
    public record Request(int slot, Branch branch, int levelDelta) {}

    private record Job(Request request, int level, Map<GuildModifier, Double> hypothetical, long token) {}

    /** Latest preview run per player. A newer menu paint cancels the older run between estimates. */
    private static final Map<UUID, Long> RUNS = new ConcurrentHashMap<>();

    /** Stops the player's preview run, if any, and drops its entry. */
    public static void forget(UUID player) {
        RUNS.remove(player);
    }

    public static void schedule(
            Player player,
            Inventory inventory,
            int slot,
            BranchIncomePreview.Prepared prepared,
            Guild guild,
            Branch branch,
            int levelDelta) {
        schedule(player, inventory, prepared, guild, List.of(new Request(slot, branch, levelDelta)));
    }

    /**
     * Paints every requested button from one background run. The guild's current trade is
     * measured once and reused for each change; estimates already made on this copy paint at once.
     */
    public static void schedule(
            Player player,
            Inventory inventory,
            BranchIncomePreview.Prepared prepared,
            Guild guild,
            List<Request> requests) {
        if (player == null || inventory == null || prepared == null || guild == null || requests == null) {
            return;
        }
        SimpleFactions plugin = SimpleFactions.plugin;
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        Map<GuildModifier, Double> current = BranchIncomePreview.modifiers(guild);
        List<Job> jobs = new ArrayList<>();
        for (Request request : requests) {
            Branch branch = request.branch();
            if (branch == null) {
                continue;
            }
            ItemStack item = inventory.getItem(request.slot());
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            int level = branch.getLevel();
            Map<GuildModifier, Double> hypothetical =
                    BranchIncomePreview.adjust(current, branch, level, request.levelDelta());
            BranchIncomePreview.Estimate known = prepared.cached(guild, current, hypothetical);
            if (known != null) {
                if (!describes(item, branch.getId(), request.levelDelta() > 0)) {
                    continue;
                }
                write(item, guild, branch, request.levelDelta() > 0, known);
                inventory.setItem(request.slot(), item);
                continue;
            }
            long token = TOKENS.incrementAndGet();
            stamp(item, token);
            inventory.setItem(request.slot(), item);
            jobs.add(new Job(request, level, hypothetical, token));
        }
        UUID viewer = player.getUniqueId();
        long run = TOKENS.incrementAndGet();
        RUNS.put(viewer, run);
        if (jobs.isEmpty()) {
            return;
        }
        String guildId = guild.getId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            BranchIncomePreview.Baseline baseline = null;
            RuntimeException failure = null;
            for (Job job : jobs) {
                if (!plugin.isEnabled() || !Long.valueOf(run).equals(RUNS.get(viewer))) {
                    return;
                }
                Branch branch = job.request().branch();
                BranchIncomePreview.Estimate estimate;
                try {
                    if (failure != null) {
                        throw failure;
                    }
                    if (baseline == null) {
                        baseline = prepared.reads().read(
                                () -> BranchIncomePreview.baseline(prepared, guild, current));
                    }
                    BranchIncomePreview.Baseline measured = baseline;
                    estimate = prepared.reads().read(
                            () -> BranchIncomePreview.estimate(measured, job.hypothetical()));
                    prepared.remember(guild, current, job.hypothetical(), estimate);
                } catch (RuntimeException ex) {
                    if (failure == null) {
                        plugin.getLogger().log(
                                Level.WARNING,
                                "Branch income preview failed for guild " + guildId + " branch " + branch.getId(),
                                ex);
                    }
                    failure = ex;
                    estimate = BranchIncomePreview.Estimate.UNAVAILABLE;
                }
                if (!plugin.isEnabled()) {
                    return;
                }
                BranchIncomePreview.Estimate result = estimate;
                Bukkit.getScheduler().runTask(plugin, () -> publish(
                        player, inventory, job.request().slot(), job.token(), guild, branch,
                        job.request().levelDelta() > 0, job.level(), result));
            }
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
            BranchIncomePreview.Estimate estimate) {
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
        write(item, guild, branch, upgrade, estimate);
        inventory.setItem(slot, item);
    }

    private static void write(
            ItemStack item, Guild guild, Branch branch, boolean upgrade, BranchIncomePreview.Estimate estimate) {
        if (upgrade) {
            CREATOR.writeUpgradeEstimate(item, guild, branch, estimate);
        } else {
            CREATOR.writeDowngradeEstimate(item, guild, branch, estimate);
        }
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
        Long stamped = item.getItemMeta().getPersistentDataContainer().get(Keys.BRANCH_PREVIEW, PersistentDataType.LONG);
        return stamped != null && stamped == token && describes(item, branchId, upgrade);
    }

    /** Whether the item is this branch's up (or down) button. Slot numbers alone overlap other rows. */
    private static boolean describes(ItemStack item, String branchId, boolean upgrade) {
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(Keys.BRANCH_ID, PersistentDataType.STRING);
        Boolean flag = meta.getPersistentDataContainer().get(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN);
        return branchId.equals(id) && flag != null && flag == upgrade;
    }
}
