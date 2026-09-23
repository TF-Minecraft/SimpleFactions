package net.tfminecraft.simplefactions.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.keys.Keys;

/** Fills economic-impact lore after the preview finishes off the server thread. */
@SuppressWarnings("deprecation")
public final class EconomicImpactService {
    private static final AtomicLong TOKENS = new AtomicLong();
    private static final ConcurrentHashMap<Long, Pending> PENDING = new ConcurrentHashMap<>();

    private EconomicImpactService() {}

    @FunctionalInterface
    public interface Calculator {
        Map<Guild, Double> calculate(EconomicPreview.Prepared prepared);
    }

    public static void enqueue(
            Player player,
            ItemMeta meta,
            Guild viewer,
            boolean shortForm,
            boolean book,
            Calculator calculator) {
        SimpleFactions plugin = SimpleFactions.plugin;
        if (plugin == null || !plugin.isEnabled() || player == null || meta == null || calculator == null) {
            return;
        }
        long token = TOKENS.incrementAndGet();
        meta.getPersistentDataContainer().set(Keys.ECONOMY_PREVIEW, PersistentDataType.LONG, token);
        EconomicPreview.Prepared prepared = EconomicPreview.current();
        PENDING.put(token, new Pending(viewer, shortForm, book, prepared, calculator));
        Bukkit.getScheduler().runTask(plugin, () -> start(plugin, player, token));
    }

    /**
     * Council proposals are shown with {@code openBook} and are not placed in an inventory.
     * Call this with the finished book in the same tick as the preview, before the book is opened.
     */
    public static void bindBook(ItemMeta meta, ItemStack book) {
        if (meta == null || book == null) {
            return;
        }
        Long token = meta.getPersistentDataContainer().get(Keys.ECONOMY_PREVIEW, PersistentDataType.LONG);
        if (token == null) {
            return;
        }
        Pending pending = PENDING.get(token);
        if (pending != null) {
            pending.bookItem = book;
        }
    }

    private static void start(SimpleFactions plugin, Player player, long token) {
        Pending pending = PENDING.remove(token);
        if (pending == null || !player.isOnline()) {
            return;
        }
        if (pending.book && pending.bookItem == null) {
            Located located = find(player, token);
            if (located != null) {
                pending.bookItem = located.item;
            }
        }
        if (!pending.book) {
            Located located = find(player, token);
            if (located == null) {
                return;
            }
        } else if (pending.bookItem == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Guild, Double> deltas;
            try {
                deltas = pending.calculator.calculate(pending.prepared);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Economic impact preview failed", ex);
                deltas = null;
            }
            if (!plugin.isEnabled()) {
                return;
            }
            Map<Guild, Double> result = deltas;
            Bukkit.getScheduler().runTask(plugin, () -> publish(player, token, pending, result));
        });
    }

    private static void publish(Player player, long token, Pending pending, Map<Guild, Double> deltas) {
        if (!player.isOnline()) {
            return;
        }
        if (pending.book && pending.bookItem != null) {
            ItemMeta meta = pending.bookItem.getItemMeta();
            if (meta == null || !matches(pending.bookItem, token)) {
                return;
            }
            applyImpact(meta, pending, deltas);
            pending.bookItem.setItemMeta(meta);
            player.openBook(pending.bookItem);
            return;
        }
        Located located = find(player, token);
        if (located == null) {
            return;
        }
        ItemMeta meta = located.item.getItemMeta();
        if (meta == null) {
            return;
        }
        applyImpact(meta, pending, deltas);
        located.item.setItemMeta(meta);
        if (located.inventory != null) {
            located.inventory.setItem(located.slot, located.item);
        } else if (located.cursor) {
            player.setItemOnCursor(located.item);
        }
    }

    private static void applyImpact(ItemMeta meta, Pending pending, Map<Guild, Double> deltas) {
        if (deltas == null) {
            replaceCalculating(meta, pending, List.of(EconomicImpact.unavailableLine()));
            return;
        }
        List<String> impact = new ArrayList<>();
        EconomicImpact.write(impact, deltas, pending.viewer, pending.shortForm);
        replaceCalculating(meta, pending, impact);
    }

    private static void replaceCalculating(ItemMeta meta, Pending pending, List<String> impact) {
        if (pending.book && meta instanceof BookMeta book) {
            List<String> pages = new ArrayList<>(book.getPages());
            String text = String.join("\n", impact);
            if (pages.size() >= 2) {
                pages.set(1, text);
            } else {
                pages.add(text);
            }
            book.setPages(pages);
            return;
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        int index = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i) != null && lore.get(i).contains(EconomicImpact.CALCULATING)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            lore.addAll(impact);
        } else {
            lore.remove(index);
            lore.addAll(index, impact);
        }
        meta.setLore(lore);
    }

    private static Located find(Player player, long token) {
        Inventory top = player.getOpenInventory().getTopInventory();
        Located located = find(top, token);
        if (located != null) {
            return located;
        }
        located = find(player.getInventory(), token);
        if (located != null) {
            return located;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (matches(cursor, token)) {
            return new Located(cursor, null, -1, true);
        }
        return null;
    }

    private static Located find(Inventory inventory, long token) {
        if (inventory == null) {
            return null;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (matches(item, token)) {
                return new Located(item, inventory, slot, false);
            }
        }
        return null;
    }

    private static boolean matches(ItemStack item, long token) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Long stamped = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ECONOMY_PREVIEW, PersistentDataType.LONG);
        return stamped != null && stamped == token;
    }

    private static final class Pending {
        private final Guild viewer;
        private final boolean shortForm;
        private final boolean book;
        private final EconomicPreview.Prepared prepared;
        private final Calculator calculator;
        private volatile ItemStack bookItem;

        private Pending(
                Guild viewer,
                boolean shortForm,
                boolean book,
                EconomicPreview.Prepared prepared,
                Calculator calculator) {
            this.viewer = viewer;
            this.shortForm = shortForm;
            this.book = book;
            this.prepared = prepared;
            this.calculator = calculator;
        }
    }

    private record Located(ItemStack item, Inventory inventory, int slot, boolean cursor) {}
}
