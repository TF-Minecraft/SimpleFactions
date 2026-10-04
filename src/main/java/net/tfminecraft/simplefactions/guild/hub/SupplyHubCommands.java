package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.BuildFailure;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.inventory.NetworkViewer;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

public final class SupplyHubCommands {
    private SupplyHubCommands() {
    }

    public static final String MENU_HINT = "§7Supply hubs are managed from your guild's Supply Hubs menu.";

    public static boolean guild(Player player, String[] args) {
        if (!Cache.requireProvinces(player)) {
            return true;
        }
        player.sendMessage(MENU_HINT);
        return true;
    }

    /** Opens the trade network list for the player's guild. */
    public static boolean networks(Player player) {
        if (player == null) return true;
        if (!Cache.requireProvinces(player)) return true;
        Guild guild = FactionManager.getGuildByMember(player.getName());
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild");
            return true;
        }
        NetworkViewer.open(player, guild, 0);
        return true;
    }

    public static void notifyRemoved(Guild guild, List<SupplyHub> removed) {
        if (guild == null || removed == null || removed.isEmpty()) {
            return;
        }
        Player leader = online(guild.getLeader());
        for (SupplyHub hub : removed) {
            if (leader != null) {
                leader.sendMessage(SupplyHubService.removalMessage(label(hub)));
            }
            Faction host = FactionManager.getByString(hub.ownerFactionId());
            if (host == null || host.getGovernment() == null) {
                continue;
            }
            String message = "§c" + (guild.getName() == null ? "A guild" : guild.getName())
                    + "'s supply hub at §f" + label(hub) + " §cwas removed §7(they could not pay)";
            java.util.LinkedHashSet<String> council = new java.util.LinkedHashSet<>();
            if (host.getLeader() != null) {
                council.add(host.getLeader());
            }
            if (host.getGovernment().getCouncilMembers() != null) {
                council.addAll(host.getGovernment().getCouncilMembers());
            }
            for (String name : council) {
                Player member = online(name);
                if (member != null) {
                    member.sendMessage(message);
                }
            }
        }
    }

    public static List<String> completeGuild(Player player, String[] args) {
        return new ArrayList<>();
    }

    /** Builds a hub at an installation the leader already chose in the menu. */
    public static boolean place(Player player, Guild guild, String ownerFactionId, String installationId) {
        if (player == null || guild == null) {
            return false;
        }
        if (!isLeader(guild, player)) {
            player.sendMessage("§cOnly the guild leader can build a supply hub");
            return false;
        }
        if (!SupplyHubService.allowsSupplyHubs(guild)) {
            player.sendMessage(SupplyHubService.buildFailureMessage(BuildFailure.ECONOMY_DISALLOWS, null, 0));
            return false;
        }
        Installation installation = SupplyHubService.findInstallation(ownerFactionId, installationId);
        if (installation == null) {
            player.sendMessage("§cThat installation is not there");
            return false;
        }
        TradeGraph graph = HighwaySnapshot.current().graph();
        Node node = graph == null ? null : graph.node(ownerFactionId, installation.getId());
        if (!HubPlacement.mayPlace(
                graph == null ? null : graph.networkOf(node),
                HubPlacement.joinedNetworks(graph, guild.getSupplyHubs()),
                HubPlacement.ownLand(guild, ownerFactionId))) {
            player.sendMessage(HubPlacement.refusal());
            return false;
        }
        int slots;
        try {
            slots = InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
        } catch (IllegalStateException ex) {
            slots = SupplyHubService.defaultHubSlots(installation.getKind(), installation.getLevel());
        }
        BuildFailure failure = SupplyHubService.checkBuild(
                slots,
                SupplyHubService.hasHub(guild.getSupplyHubs(), ownerFactionId, installation.getId()),
                guild.getSupplyHubs().size(),
                SupplyHubService.limit(guild),
                SupplyHubService.countLoaded(ownerFactionId, installation.getId()),
                Math.max(
                        tradeInProvince(installation.getProvince(), guild.getId()),
                        reachableTrade(guild, installation)),
                SupplyHubService.hubPermitted(guild, ownerFactionId, installation.getId()));
        if (failure != null) {
            player.sendMessage(SupplyHubService.buildFailureMessage(
                    failure, installation.getKind().getDisplayName(), SupplyHubService.limit(guild)));
            return false;
        }
        guild.getSupplyHubs().add(new SupplyHub(
                ownerFactionId, installation.getId(), System.currentTimeMillis()));
        player.sendMessage("§aBuilt a supply hub at §f" + installation.getName());
        recalculateTrade();
        SupplyHub newHub = guild.getSupplyHubs().get(guild.getSupplyHubs().size() - 1);
        List<String> connections = connectionLines(guild, newHub, installation);
        for (String line : connections) {
            player.sendMessage(line);
        }
        if (connections.isEmpty() && guild.getSupplyHubs().size() == 1) {
            player.sendMessage("§7A hub does nothing alone. Build a second one where you want trade to arrive.");
        } else if (connections.isEmpty()) {
            player.sendMessage("§7Trade now leaves and arrives here at full strength. Production needs a second hub of yours on the same network.");
        }
        return true;
    }

    private static String label(SupplyHub hub) {
        Installation installation = SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
        if (installation == null) {
            return hub.installationId();
        }
        return installation.getName();
    }

    static double tradeAvailable(Guild guild, Installation installation) {
        if (guild == null || installation == null) {
            return 0;
        }
        return Math.max(
                tradeInProvince(installation.getProvince(), guild.getId()),
                reachableTrade(guild, installation));
    }

    /** Trade power the guild's existing hubs would deliver to a hub at this installation. */
    private static double reachableTrade(Guild guild, Installation installation) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || plugin.getProvinceManager() == null) {
            return 0;
        }
        double delivered = HubNetwork.potentialTrade(guild, installation, plugin.getProvinceManager());
        return delivered >= 0.5 ? delivered : 0;
    }

    /** Hub changes move trade power, so the map and incomes are brought up to date at once. */
    public static void recalculateTrade() {
        if (FactionManager.getMap() != null && FactionManager.factions != null) {
            for (Faction faction : FactionManager.factions) {
                if (faction != null && faction.getRGB() != null) {
                    FactionManager.getMap().enqueue("nation", faction.getRGB());
                }
            }
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || plugin.getProvinceManager() == null) {
            return;
        }
        plugin.getProvinceManager().recalculate();
    }

    static List<String> connectionLines(Guild guild, SupplyHub hub, Installation installation) {
        List<String> lines = new ArrayList<>();
        if (installation == null) {
            return lines;
        }
        double tradeBonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_TRADE);
        double productionBonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_PRODUCTION);
        for (HubTransport.Link link : HubNetwork.linksFor(guild)) {
            if (link.fromProvince() != installation.getProvince()
                    || !sameEndpoint(link.fromFactionId(), link.fromInstallationId(), hub)) {
                continue;
            }
            lines.add("§7Sends to §f" + hubNameIn(link) + " §7by " + link.mode().getKey()
                    + " (" + Math.round(link.distance()) + " blocks): §e"
                    + Math.round(link.boostedTradeFactor(tradeBonus) * 100)
                    + "% §7trade, §e"
                    + Math.round(link.boostedProductionFactor(productionBonus) * 100)
                    + "% §7production");
        }
        return lines;
    }

    private static String hubNameIn(HubTransport.Link link) {
        Installation other = SupplyHubService.findInstallation(link.toFactionId(), link.toInstallationId());
        return other == null ? "province " + link.toProvince() : other.getName();
    }

    private static boolean sameEndpoint(String factionId, String installationId, SupplyHub hub) {
        return factionId != null && installationId != null && hub != null
                && factionId.equalsIgnoreCase(hub.ownerFactionId())
                && installationId.equalsIgnoreCase(hub.installationId());
    }

    private static double tradeInProvince(int provinceId, String guildId) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || plugin.getProvinceManager() == null) {
            return 0;
        }
        Province province = plugin.getProvinceManager().get(provinceId);
        return SupplyHubService.exportedTrade(province, guildId);
    }

    /** A realm guild is led by its faction's leader, which {@link Guild#getLeader()} resolves. */
    private static boolean isLeader(Guild guild, Player player) {
        return player.getName().equalsIgnoreCase(guild.getLeader());
    }

    private static Player online(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            if (Bukkit.getServer() == null) {
                return null;
            }
            return Bukkit.getPlayerExact(name);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
