package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.hub.HubAgreementService;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubCommands;
import net.tfminecraft.simplefactions.guild.hub.HubNetwork;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.hub.SupplyHub;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubStanding;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

public class SupplyHubView {
    private final InventoryManager inv;

    public SupplyHubView(InventoryManager inv) {
        this.inv = inv;
    }

    public void guildView(Player player, Guild guild) {
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder(
                        guild.getId(), SFGUI.SUPPLY_HUB_VIEW),
                54,
                "§7Supply Hubs");
        populateGuildView(player, guild, inventory);
        player.openInventory(inventory);
    }

    private void populateGuildView(Player player, Guild guild, Inventory inventory) {
        if (!net.tfminecraft.simplefactions.espionage.EspionageService.canViewExact(player, guild.getFaction())) {
            ReportedMenus.supplyHubs(inventory, player, guild, inv);
            return;
        }
        inventory.clear();
        ProvinceManager provinces = SimpleFactions.getInstance().getProvinceManager();
        List<SupplyHub> hubs = SupplyHubService.oldestFirst(guild.getSupplyHubs());
        for (int index = 0; index < hubs.size() && index < 45; index++) {
            SupplyHub hub = hubs.get(index);
            Installation installation = SupplyHubService.findInstallation(
                    hub.ownerFactionId(), hub.installationId());
            Faction owner = FactionManager.getByString(hub.ownerFactionId());
            HubStanding standing = standing(guild, hub);
            List<String> connections = connectionLore(guild, hub, installation);
            Province province = installation == null ? null : provinces.get(installation.getProvince());
            List<String> lore = SupplyHubCreator.guildHubLore(
                    installation == null ? hub.installationId() : installation.getName(),
                    installation == null ? "Unknown" : installation.getKind().getDisplayName(),
                    owner == null ? hub.ownerFactionId() : owner.getName(),
                    SupplyHubService.upkeepOf(hub, hubs, SupplyHubService.upkeepPerHub(guild)),
                    standing,
                    connections,
                    province == null ? 0 : SupplyHubService.exportedTrade(province, guild.getId()),
                    province == null ? 0 : province.getGuildProduction(guild));
            ItemStack item = SupplyHubCreator.item(
                    Material.CHEST,
                    "§e" + (installation == null ? hub.installationId() : installation.getName()),
                    lore);
            if (isLeader(guild, player)) {
                ItemMeta meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING,
                        hub.ownerFactionId() + ":" + hub.installationId());
                lore.add("§cClick to remove this hub");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(index, item);
        }
        int limit = SupplyHubService.limit(guild);
        List<String> information = new ArrayList<>(List.of(
                "§7Trade walks from your capital. Ports, airports and stations carry it further.",
                "§7Your hub at a stop carries it at full strength.",
                "§7Production only moves between your own hubs.",
                "§7Hubs: §e" + hubs.size() + "/" + limit,
                "§7Upkeep per hub: §e" + Formatter.formatDouble(SupplyHubService.upkeepPerHub(guild)) + "d/day",
                "§7Daily upkeep: §e" + Formatter.formatDouble(SupplyHubService.dailyCost(guild)) + "d/day"));
        boolean noHubs = hubs.isEmpty();
        boolean noInstallation = false;
        boolean noTrade = false;
        if (noHubs) {
            var territory = HubEstimates.territory(HubEstimates.sites(guild));
            noInstallation = territory.isEmpty();
            noTrade = !noInstallation && HubEstimates.withTrade(territory).isEmpty();
        }
        if (noHubs && noInstallation) {
            information.add("§7You have no installation for a hub.");
        } else if (noHubs && noTrade) {
            information.add("§7None of your installations have trade power.");
        } else if (isLeader(guild, player)) {
            if (noHubs) {
                information.add("§7Choose §eCreate main hub §7to place the first one in your territory.");
            } else {
                information.add("§7Choose §ePropose a hub §7to see where your hubs can reach.");
            }
        }
        inventory.setItem(49, SupplyHubCreator.item(Material.PAPER, "§eSupply Hub Information", information));
        ItemStack networks = SupplyHubCreator.item(Material.COMPASS, "§eTrade networks", List.of(
                "§7Ports, airports and stations, and who uses them.",
                "§eClick to view"));
        ItemMeta networkMeta = networks.getItemMeta();
        networkMeta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, "networks");
        networks.setItemMeta(networkMeta);
        inventory.setItem(47, networks);
        if (noHubs && noInstallation) {
            inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7You have no installation", List.of(
                    "§7Build a port, airport, or train station in your territory.")));
        } else if (noHubs && noTrade) {
            inventory.setItem(22, SupplyHubCreator.item(Material.PAPER, "§7No trade power in your territory", List.of(
                    "§7A main hub is placed where your guild already has trade power.")));
        } else if (isLeader(guild, player)) {
            if (noHubs) {
                ItemStack main = SupplyHubCreator.item(Material.EMERALD, "§aCreate main hub", List.of(
                        "§7The first hub has to be in your territory.",
                        "§7It starts where your trade power is highest.",
                        "§eClick to choose the site"));
                ItemMeta mainMeta = main.getItemMeta();
                mainMeta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, "main");
                main.setItemMeta(mainMeta);
                inventory.setItem(22, main);
            } else {
                ItemStack propose = SupplyHubCreator.item(Material.EMERALD, "§aPropose a hub", List.of(
                        "§7Places your existing hubs can already reach.",
                        "§eClick to choose a destination"));
                ItemMeta proposeMeta = propose.getItemMeta();
                proposeMeta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, "propose");
                propose.setItemMeta(proposeMeta);
                inventory.setItem(45, propose);
            }
        }
        inventory.setItem(53, inv.createBackButton(SFGUI.SUPPLY_HUB_VIEW));
    }

    public void hostedView(Player player, Faction faction, Installation installation) {
        Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
                new net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder(
                        faction.getId(), SFGUI.HOSTED_SUPPLY_HUB_VIEW, installation.getId()),
                54,
                "§7Hosted Supply Hubs");
        populateHostedView(player, faction, installation, inventory);
        player.openInventory(inventory);
    }

    private void populateHostedView(Player player, Faction faction, Installation installation, Inventory inventory) {
        inventory.clear();
        List<Guild> guilds = new ArrayList<>();
        for (Guild guild : SupplyHubService.allGuilds()) {
            if (SupplyHubService.hasHub(guild.getSupplyHubs(), faction.getId(), installation.getId())) {
                guilds.add(guild);
            }
        }
        guilds.sort(Comparator.comparingLong(guild -> SupplyHubService.findHub(
                guild.getSupplyHubs(), faction.getId(), installation.getId()).createdAt()));
        int max = InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
        for (int index = 0; index < guilds.size() && index < 45; index++) {
            Guild guild = guilds.get(index);
            SupplyHub hub = SupplyHubService.findHub(guild.getSupplyHubs(), faction.getId(), installation.getId());
            HubStanding standing = standing(guild, hub);
            Faction host = guild.getFaction();
            ItemStack item = SupplyHubCreator.item(Material.CHEST,
                    "§e" + guild.getName(),
                    SupplyHubCreator.hostHubLore(guild.getName(), host == null ? "Unknown" : host.getName(), standing));
            boolean agreed = HubAgreementService.hasAgreement(guild, faction.getId(), installation.getId());
            if (!agreed && faction.getLeader() != null && faction.getLeader().equalsIgnoreCase(player.getName())) {
                ItemMeta meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, guild.getId());
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.add("§cClick to evict this hub");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(index, item);
        }
        inventory.setItem(47, SupplyHubCreator.item(Material.PAPER, "§eHub Slots",
                List.of("§7Used: §e" + guilds.size() + "/" + max)));
        if (faction.getGovernment() != null && faction.getGovernment().isCouncilMember(player)
                && hasOfferAt(faction.getId(), installation.getId())) {
            ItemStack offers = SupplyHubCreator.item(Material.WRITABLE_BOOK, "§eHub offers", List.of(
                    "§7An offer for a hub here is waiting.",
                    "§eClick to answer"));
            ItemMeta offerMeta = offers.getItemMeta();
            offerMeta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, "offers");
            offers.setItemMeta(offerMeta);
            inventory.setItem(45, offers);
        }
        inventory.setItem(53, inv.createBackButton(SFGUI.HOSTED_SUPPLY_HUB_VIEW));
    }

    public static boolean removeGuildHub(
            List<SupplyHub> hubs, String ownerFactionId, String installationId) {
        SupplyHub hub = SupplyHubService.findHub(hubs, ownerFactionId, installationId);
        return hub != null && hubs.remove(hub);
    }

    public void click(InventoryClickEvent event, Inventory inventory, Player player) {
        event.setCancelled(true);
        if (!(inventory.getHolder() instanceof net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder holder)) {
            return;
        }
        if (holder.getType() == SFGUI.SUPPLY_HUB_VIEW) {
            Guild guild = FactionManager.getGuildByString(holder.getId());
            if (guild == null) {
                return;
            }
            if (event.getSlot() == 53) {
                // InventoryManager's back button handling opens the guild menu.
                return;
            }
            ItemStack item = event.getCurrentItem();
            String data = item == null || !item.hasItemMeta() ? null
                    : item.getItemMeta().getPersistentDataContainer()
                            .get(Keys.STRING_KEY, PersistentDataType.STRING);
            if ("networks".equals(data)) {
                NetworkViewer.open(player, guild, 0);
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            if (!isLeader(guild, player)) {
                return;
            }
            if (item == null || !item.hasItemMeta() || data == null) {
                return;
            }
            if (data.equals("propose") || data.equals("main")) {
                HubProposalMenu.openProposals(player, guild, 0);
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            int separator = data.indexOf(':');
            if (separator < 0) {
                return;
            }
            inv.confirmSupplyHub(player, guild.getFaction(), "guild|" + guild.getId() + "|"
                    + data.substring(0, separator) + "|" + data.substring(separator + 1));
        } else if (holder.getType() == SFGUI.HOSTED_SUPPLY_HUB_VIEW) {
            Faction faction = FactionManager.getByString(holder.getId());
            if (faction == null) {
                return;
            }
            Installation installation = faction.getInstallationHandler().getById(holder.getSecondaryId());
            if (event.getSlot() == 53) {
                // InventoryManager's back button handling opens the installation menu.
                return;
            }
            if (installation == null) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            String marker = clicked == null || !clicked.hasItemMeta() ? null
                    : clicked.getItemMeta().getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
            if ("offers".equals(marker)) {
                HubProposalMenu.openOffersAt(player, faction.getId(), installation.getId());
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            if (!faction.getLeader().equalsIgnoreCase(player.getName())) {
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) {
                return;
            }
            String guildId = item.getItemMeta().getPersistentDataContainer()
                    .get(Keys.STRING_KEY, PersistentDataType.STRING);
            if (guildId == null) {
                return;
            }
            inv.confirmSupplyHub(player, faction,
                    "host|" + faction.getId() + "|" + installation.getId() + "|" + guildId);
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }

    public boolean confirm(Player player, String data, boolean accepted) {
        String[] parts = data.split("\\|", -1);
        if (parts.length == 4 && parts[0].equals("build")) {
            Guild guild = FactionManager.getGuildByString(parts[1]);
            if (guild == null || !isLeader(guild, player)) {
                return false;
            }
            if (accepted) {
                SupplyHubCommands.place(player, guild, parts[2], parts[3]);
            }
            guildView(player, guild);
            return true;
        }
        if (parts.length == 4 && parts[0].equals("guild")) {
            Guild guild = FactionManager.getGuildByString(parts[1]);
            if (guild == null || !isLeader(guild, player)) {
                return false;
            }
            if (accepted) {
                if (removeGuildHub(guild.getSupplyHubs(), parts[2], parts[3])) {
                    HubAgreementService.onHubRemoved(guild, parts[2], parts[3]);
                    SupplyHubCommands.recalculateTrade();
                }
            }
            guildView(player, guild);
            return true;
        }
        if (parts.length == 4 && parts[0].equals("host")) {
            Faction faction = FactionManager.getByString(parts[1]);
            if (faction == null || !faction.getLeader().equalsIgnoreCase(player.getName())) {
                return false;
            }
            if (accepted) {
                Guild guild = FactionManager.getGuildByString(parts[3]);
                if (guild != null && HubAgreementService.hasAgreement(guild, parts[1], parts[2])) {
                    return false;
                }
                if (guild != null && removeGuildHub(guild.getSupplyHubs(), parts[1], parts[2])) {
                    SupplyHubCommands.recalculateTrade();
                    org.bukkit.entity.Player online = SimpleFactions.plugin.getServer()
                            .getPlayerExact(guild.getLeader());
                    if (online != null) {
                        Installation site = faction.getInstallationHandler().getById(parts[2]);
                        online.sendMessage("§cYour supply hub at §f"
                                + (site == null ? parts[2] : site.getName()) + " §cwas evicted");
                    }
                }
            }
            Installation installation = faction.getInstallationHandler().getById(parts[2]);
            if (installation != null) {
                hostedView(player, faction, installation);
            }
            return true;
        }
        return false;
    }

    /** A realm guild is led by its faction's leader, which {@link Guild#getLeader()} resolves. */
    private static boolean isLeader(Guild guild, Player player) {
        return player.getName().equalsIgnoreCase(guild.getLeader());
    }

    private static boolean hasOfferAt(String hostFactionId, String installationId) {
        for (Guild guild : SupplyHubService.allGuilds()) {
            if (HubAgreementService.offer(guild, hostFactionId, installationId) != null) {
                return true;
            }
        }
        return false;
    }

    private static HubStanding standing(Guild guild, SupplyHub hub) {
        Installation installation = SupplyHubService.findInstallation(
                hub.ownerFactionId(), hub.installationId());
        boolean allowed = SupplyHubService.hubPermitted(guild, hub.ownerFactionId(), hub.installationId());
        int slots = installation == null
                ? 0
                : InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
        return SupplyHubService.standing(guild, hub, installation != null, allowed, slots,
                SupplyHubService.atInstallation(
                        hub.ownerFactionId(), hub.installationId(), SupplyHubService.allGuilds()));
    }

    private static List<String> connectionLore(Guild guild, SupplyHub hub, Installation installation) {
        List<String> result = new ArrayList<>();
        if (installation == null) {
            return result;
        }
        double tradeBonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_TRADE);
        double productionBonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_PRODUCTION);
        for (Link link : HubNetwork.linksFor(guild)) {
            if (link.fromProvince() != installation.getProvince()
                    || !hub.ownerFactionId().equalsIgnoreCase(link.fromFactionId())
                    || !hub.installationId().equalsIgnoreCase(link.fromInstallationId())) {
                continue;
            }
            Installation destination = null;
            for (SupplyHub other : guild.getSupplyHubs()) {
                Installation candidate = SupplyHubService.findInstallation(
                        other.ownerFactionId(), other.installationId());
                if (candidate != null && candidate.getProvince() == link.toProvince()
                        && other.ownerFactionId().equalsIgnoreCase(link.toFactionId())
                        && other.installationId().equalsIgnoreCase(link.toInstallationId())) {
                    destination = candidate;
                    break;
                }
            }
            if (destination != null) {
                result.add("§7Sends to §f" + destination.getName() + " §7by "
                        + link.mode().getKey() + " (" + Math.round(link.distance()) + " blocks): §e"
                        + Math.round(link.boostedTradeFactor(tradeBonus) * 100) + "% §7trade, §e"
                        + Math.round(link.boostedProductionFactor(productionBonus) * 100) + "% §7production");
            }
        }
        if (result.isEmpty() && standing(guild, hub).active()) {
            result.add("§7Not linked to another hub");
        }
        return result;
    }
}
