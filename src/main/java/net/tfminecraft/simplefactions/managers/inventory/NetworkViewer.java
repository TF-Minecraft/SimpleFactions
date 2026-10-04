package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.Highway.HubSite;
import net.tfminecraft.simplefactions.guild.hub.HighwaySnapshot;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService;
import net.tfminecraft.simplefactions.guild.network.CurrentNetworks;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary.Summary;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;

/** The trade networks, read from the current snapshot when the screen opens. */
public final class NetworkViewer {
    private static final String SEP = "\u001f";
    private static final int PAGE_SIZE = 45;

    private NetworkViewer() { }

    public static void open(Player player, Guild guild, int page) {
        if (player == null || guild == null || guild.getId() == null) return;
        HighwaySnapshot snapshot = HighwaySnapshot.current();
        List<Summary> summaries = CurrentNetworks.from(snapshot);
        int pages = Math.max(1, (summaries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.TRADE_NETWORK_LIST, shown),
                54, "§7Trade Networks");
        if (summaries.isEmpty()) {
            inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7No trade networks", List.of(
                    "§7Ports, airports and stations form one when they are built.")));
        }
        int start = shown * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < summaries.size(); index++) {
            Summary summary = summaries.get(start + index);
            Node anchor = anchor(summary.network());
            if (anchor == null) continue;
            inventory.setItem(index, networkItem(summary, anchor));
        }
        if (shown > 0) {
            inventory.setItem(48, SupplyHubCreator.item(Material.ARROW, "§ePrevious page", List.of()));
        }
        if (shown + 1 < pages) {
            inventory.setItem(50, SupplyHubCreator.item(Material.ARROW, "§eNext page", List.of()));
        }
        inventory.setItem(53, backButton(SFGUI.TRADE_NETWORK_LIST));
        player.openInventory(inventory);
    }

    public static void click(InventoryClickEvent event, Inventory inventory, Player player, InventoryManager menus) {
        event.setCancelled(true);
        if (!(inventory.getHolder() instanceof SFInventoryHolder holder) || player == null) return;
        Guild guild = FactionManager.getGuildByString(holder.getId());
        if (guild == null) return;
        if (holder.getType() == SFGUI.TRADE_NETWORK_LIST) {
            clickList(event, holder, player, menus, guild);
        } else if (holder.getType() == SFGUI.TRADE_NETWORK_NODES) {
            clickNodes(event, holder, player, guild);
        }
    }

    private static void clickList(
            InventoryClickEvent event, SFInventoryHolder holder, Player player, InventoryManager menus, Guild guild) {
        if (event.getSlot() == 53) {
            menus.supplyHubView.guildView(player, guild);
            return;
        }
        if (event.getSlot() == 48 && holder.getPage() > 0) {
            open(player, guild, holder.getPage() - 1);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            return;
        }
        if (event.getSlot() == 50 && arrow(event)) {
            open(player, guild, holder.getPage() + 1);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            return;
        }
        String data = key(event.getCurrentItem());
        NodeKey anchor = NodeKey.parse(data);
        if (anchor == null) return;
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        openNodes(player, guild, anchor.owner, anchor.installation, 0, holder.getPage());
    }

    private static void clickNodes(
            InventoryClickEvent event, SFInventoryHolder holder, Player player, Guild guild) {
        NodeKey key = NodeKey.parse(holder.getSecondaryId());
        int listPage = key == null ? 0 : key.listPage;
        if (event.getSlot() == 53) {
            open(player, guild, listPage);
            return;
        }
        if (key == null) return;
        if (event.getSlot() == 48 && holder.getPage() > 0) {
            openNodes(player, guild, key.owner, key.installation, holder.getPage() - 1, listPage);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            return;
        }
        if (event.getSlot() == 50 && arrow(event)) {
            openNodes(player, guild, key.owner, key.installation, holder.getPage() + 1, listPage);
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        }
    }

    private static boolean arrow(InventoryClickEvent event) {
        return event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW;
    }

    private static void openNodes(
            Player player, Guild guild, String owner, String installation, int page, int listPage) {
        HighwaySnapshot snapshot = HighwaySnapshot.current();
        TradeGraph graph = snapshot.graph();
        Node node = graph == null ? null : graph.node(owner, installation);
        Summary summary = node == null ? null : NetworkSummary.containing(CurrentNetworks.from(snapshot), node);
        if (summary == null) {
            player.sendMessage("§cThat network is no longer there.");
            open(player, guild, listPage);
            return;
        }
        List<Node> nodes = new ArrayList<>(summary.network().nodes());
        nodes.sort(Comparator.comparing((Node stop) -> stopName(stop), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Node::installationId, String.CASE_INSENSITIVE_ORDER));
        int pages = Math.max(1, (nodes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new SFInventoryHolder(guild.getId(), SFGUI.TRADE_NETWORK_NODES, shown, false,
                        new NodeKey(owner, installation, listPage).stored()),
                54, windowTitle("§7" + summary.name()));
        if (nodes.isEmpty()) {
            inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7No stops", List.of(
                    "§7This network has no ports, airports or stations.")));
        }
        int start = shown * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < nodes.size(); index++) {
            inventory.setItem(index, nodeItem(snapshot, guild, summary, nodes.get(start + index)));
        }
        if (shown > 0) {
            inventory.setItem(48, SupplyHubCreator.item(Material.ARROW, "§ePrevious page", List.of()));
        }
        if (shown + 1 < pages) {
            inventory.setItem(50, SupplyHubCreator.item(Material.ARROW, "§eNext page", List.of()));
        }
        inventory.setItem(53, backButton(SFGUI.TRADE_NETWORK_NODES));
        player.openInventory(inventory);
    }

    private static ItemStack networkItem(Summary summary, Node anchor) {
        List<String> lore = new ArrayList<>();
        if (summary.global()) lore.add("§7The global network.");
        lore.add("§7Nodes: §e" + summary.nodes());
        lore.add("§7Hubs: §e" + summary.hubs() + "/" + summary.slots());
        lore.add("§7Members:");
        if (summary.members().isEmpty()) {
            lore.add(summary.tradeTotal() > 0
                    ? "§7Every share is under one percent."
                    : "§7No guild has trade here yet.");
        } else {
            for (String line : summary.memberLines()) lore.add("§7" + line);
        }
        lore.add("§eClick to see the stops");
        ItemStack item = SupplyHubCreator.item(Material.COMPASS, "§e" + summary.name(), lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING,
                new NodeKey(anchor.ownerFactionId(), anchor.installationId(), 0).stored());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack nodeItem(HighwaySnapshot snapshot, Guild guild, Summary summary, Node node) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Owner: §f" + ownerName(node));
        lore.add("§7" + kindLabel(node.kind()));
        lore.add("§7Hubs: §e" + summary.hubsAt(node) + "/" + Math.max(0, node.hubSlots()));
        boolean own = snapshot.hubbed(guild.getId()).contains(
                new HubSite(node.ownerFactionId(), node.installationId()));
        lore.add(own ? "§aYour guild has a hub here" : "§7Your guild has no hub here");
        return SupplyHubCreator.item(Material.CHEST, "§e" + stopName(node), lore);
    }

    private static String stopName(Node node) {
        Installation installation = SupplyHubService.findInstallation(node.ownerFactionId(), node.installationId());
        String name = installation == null ? node.installationId() : installation.getName();
        String plain = NetworkSummary.plain(name);
        return plain.isEmpty() ? node.installationId() : plain;
    }

    private static String ownerName(Node node) {
        Faction owner = FactionManager.getByString(node.ownerFactionId());
        String name = owner == null ? node.ownerFactionId() : owner.getName();
        String plain = NetworkSummary.plain(name);
        return plain.isEmpty() ? node.ownerFactionId() : plain;
    }

    private static String kindLabel(InstallationKind kind) {
        if (kind == null) return "Stop";
        String display = kind.getDisplayName();
        if (display == null || display.isEmpty()) return "Stop";
        return Character.toUpperCase(display.charAt(0)) + display.substring(1);
    }

    private static Node anchor(TradeGraph.Network network) {
        Node best = null;
        for (Node node : network.nodes()) {
            if (best == null || node.provinceId() < best.provinceId()
                    || (node.provinceId() == best.provinceId()
                            && compareId(node.installationId(), best.installationId()) < 0)) {
                best = node;
            }
        }
        return best;
    }

    private static int compareId(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return a.compareToIgnoreCase(b);
    }

    private static String windowTitle(String text) {
        String title = text == null || text.isEmpty() ? "§7Network" : text;
        return title.length() <= 128 ? title : title.substring(0, 128);
    }

    private static ItemStack backButton(SFGUI gui) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cBack");
        if (SimpleFactions.plugin != null) {
            NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "gui");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, gui.name());
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String key(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
    }

    private record NodeKey(String owner, String installation, int listPage) {
        private static NodeKey parse(String raw) {
            if (raw == null) return null;
            String[] parts = raw.split(SEP, -1);
            if (parts.length < 2 || parts[1].isEmpty()) return null;
            int page = 0;
            if (parts.length >= 3) {
                try {
                    page = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ex) {
                    page = 0;
                }
            }
            return new NodeKey(parts[0], parts[1], Math.max(0, page));
        }

        private String stored() {
            return owner + SEP + installation + SEP + listPage;
        }
    }
}
