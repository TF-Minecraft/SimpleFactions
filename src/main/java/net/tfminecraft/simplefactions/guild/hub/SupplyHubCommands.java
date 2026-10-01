package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.BuildFailure;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubStanding;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.PlacedCandidate;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.RemoveMatch;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.RemoveOutcome;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationBounds;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;

public final class SupplyHubCommands {
    private SupplyHubCommands() {
    }

    public static boolean guild(Player player, String[] args) {
        if (!Cache.requireProvinces(player)) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/guild hub <build|list|remove>");
            return true;
        }
        String action = args[1];
        if (action.equalsIgnoreCase("build")) {
            return build(player);
        }
        if (action.equalsIgnoreCase("list")) {
            return list(player);
        }
        if (action.equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: §e/guild hub remove <installation id>");
                player.sendMessage("§7If that id is used by more than one faction, use §e<faction id>:<installation id>");
                return true;
            }
            return remove(player, args[2]);
        }
        player.sendMessage("§cUsage: §e/guild hub <build|list|remove>");
        return true;
    }

    public static boolean permit(Player player, String[] args) {
        if (!Cache.requireProvinces(player)) {
            return true;
        }
        Faction faction = FactionManager.getByLeader(player.getName());
        if (faction == null) {
            player.sendMessage("§cYou need to be a faction leader to grant hub permits");
            return true;
        }
        if (args.length < 2) {
            listPermits(player, faction);
            return true;
        }
        Guild target = FactionManager.getGuildByString(args[1]);
        if (target == null) {
            player.sendMessage("§cNo guild by the id " + args[1]);
            return true;
        }
        if (target.getFaction() != null
                && target.getFaction().getId() != null
                && target.getFaction().getId().equalsIgnoreCase(faction.getId())) {
            player.sendMessage("§cYour faction's own guilds do not need a hub permit");
            return true;
        }
        boolean granted = faction.toggleHubPermit(target.getId());
        if (granted) {
            player.sendMessage("§aGranted §f" + target.getName() + " §aa hub permit");
        } else {
            player.sendMessage("§cRemoved the hub permit for §f" + target.getName());
        }
        recalculateTrade();
        return true;
    }

    public static void notifyRemoved(Guild guild, List<SupplyHub> removed) {
        if (guild == null || removed == null || removed.isEmpty()) {
            return;
        }
        Player leader = online(guild.getLeader());
        if (leader == null) {
            return;
        }
        for (SupplyHub hub : removed) {
            leader.sendMessage(SupplyHubService.removalMessage(label(hub)));
        }
    }

    public static List<String> completeGuild(Player player, String[] args) {
        List<String> completions = new ArrayList<>();
        if (player == null || !Cache.provincesEnabled) {
            return completions;
        }
        Guild guild = FactionManager.getGuildByMember(player.getName());
        if (guild == null) {
            return completions;
        }
        if (args.length == 2) {
            completions.add("list");
            if (isLeader(guild, player)) {
                completions.add("build");
                completions.add("remove");
            }
            return completions;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove") && isLeader(guild, player)) {
            return completeRemove(guild.getSupplyHubs());
        }
        return completions;
    }

    public static List<String> completePermits(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player player) || !Cache.provincesEnabled) {
            return completions;
        }
        if (FactionManager.getByLeader(player.getName()) == null) {
            return completions;
        }
        if (args.length != 2) {
            return completions;
        }
        for (Guild guild : FactionManager.getAllGuilds()) {
            if (guild == null || guild.getId() == null) {
                continue;
            }
            if (!completions.contains(guild.getId())) {
                completions.add(guild.getId());
            }
        }
        return completions;
    }

    public static List<String> completeRemove(List<SupplyHub> hubs) {
        List<String> completions = new ArrayList<>();
        if (hubs == null) {
            return completions;
        }
        for (SupplyHub hub : hubs) {
            if (hub == null || hub.installationId() == null) {
                continue;
            }
            int sameId = 0;
            for (SupplyHub other : hubs) {
                if (other != null
                        && other.installationId() != null
                        && other.installationId().equalsIgnoreCase(hub.installationId())) {
                    sameId++;
                }
            }
            String token = sameId > 1
                    ? hub.ownerFactionId() + ":" + hub.installationId()
                    : hub.installationId();
            if (!completions.contains(token)) {
                completions.add(token);
            }
        }
        return completions;
    }

    private static boolean build(Player player) {
        Guild guild = FactionManager.getGuildByMember(player.getName());
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild");
            return true;
        }
        if (!isLeader(guild, player)) {
            player.sendMessage("§cOnly the guild leader can build a supply hub");
            return true;
        }
        if (!SupplyHubService.allowsSupplyHubs(guild)) {
            player.sendMessage(SupplyHubService.buildFailureMessage(BuildFailure.ECONOMY_DISALLOWS, null, 0));
            return true;
        }
        List<PlacedCandidate> covering = covering(player.getLocation());
        if (covering.isEmpty()) {
            player.sendMessage("§cYou are not standing inside an installation");
            return true;
        }
        SupplyHubService.HubCandidate site = SupplyHubService.chooseAllowed(covering);
        PlacedCandidate chosen = site == null ? SupplyHubService.nearest(covering) : placed(covering, site);
        if (chosen == null) {
            player.sendMessage("§cYou are not standing inside an installation");
            return true;
        }
        Installation installation = SupplyHubService.findInstallation(
                chosen.ownerFactionId(), chosen.installationId());
        if (installation == null) {
            player.sendMessage("§cYou are not standing inside an installation");
            return true;
        }
        String guildFactionId = guild.getFaction() == null ? null : guild.getFaction().getId();
        Faction owner = FactionManager.getByString(chosen.ownerFactionId());
        boolean permit = owner != null && owner.hasHubPermit(guild.getId());
        BuildFailure failure = SupplyHubService.checkBuild(
                chosen.hubSlots(),
                SupplyHubService.hasHub(guild.getSupplyHubs(), chosen.ownerFactionId(), installation.getId()),
                guild.getSupplyHubs().size(),
                SupplyHubService.limit(guild),
                SupplyHubService.countLoaded(chosen.ownerFactionId(), installation.getId()),
                Math.max(
                        tradeInProvince(installation.getProvince(), guild.getId()),
                        reachableTrade(guild, installation)),
                SupplyHubService.ownerAllows(guildFactionId, chosen.ownerFactionId(), permit));
        if (failure != null) {
            player.sendMessage(SupplyHubService.buildFailureMessage(
                    failure, installation.getKind().getDisplayName(), SupplyHubService.limit(guild)));
            return true;
        }
        guild.getSupplyHubs().add(new SupplyHub(
                chosen.ownerFactionId(), installation.getId(), System.currentTimeMillis()));
        player.sendMessage("§aBuilt a supply hub at §f" + installation.getName());
        recalculateTrade();
        return true;
    }

    private static boolean list(Player player) {
        Guild guild = FactionManager.getGuildByMember(player.getName());
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild");
            return true;
        }
        List<SupplyHub> hubs = SupplyHubService.oldestFirst(guild.getSupplyHubs());
        player.sendMessage("§eSupply hubs §7(" + hubs.size() + "/" + SupplyHubService.limit(guild) + ")");
        if (hubs.isEmpty()) {
            player.sendMessage("§7Your guild has no supply hubs");
            return true;
        }
        for (SupplyHub hub : hubs) {
            Installation installation = SupplyHubService.findInstallation(
                    hub.ownerFactionId(), hub.installationId());
            String name = installation == null ? hub.installationId() : installation.getName();
            String kind = installation == null ? "unknown" : installation.getKind().getDisplayName();
            Faction owner = FactionManager.getByString(hub.ownerFactionId());
            String ownerName = owner == null ? hub.ownerFactionId() : owner.getName();
            double cost = SupplyHubService.upkeepOf(
                    hub, guild.getSupplyHubs(), SupplyHubService.baseUpkeep(), SupplyHubService.upkeepGrowth());
            player.sendMessage("§f" + name + " §7(" + kind + ") §7owned by §f" + ownerName);
            player.sendMessage("§7Upkeep: §e" + Formatter.formatMoney(cost) + "d/day §7" + statusOf(guild, hub, installation));
            HubTaxBreakdown.Assessment assessment = guild.getHubTaxBreakdown().forHub(hub);
            player.sendMessage("§7Taxable income: §e" + Formatter.formatMoney(assessment.taxableIncome())
                    + "d/day §7Hub Tax: §e" + Formatter.formatMoney(assessment.tax()) + "d/day");
            for (String line : connectionLines(guild, installation)) {
                player.sendMessage(line);
            }
        }
        return true;
    }

    private static boolean remove(Player player, String argument) {
        Guild guild = FactionManager.getGuildByMember(player.getName());
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild");
            return true;
        }
        if (!isLeader(guild, player)) {
            player.sendMessage("§cOnly the guild leader can remove a supply hub");
            return true;
        }
        RemoveMatch match = SupplyHubService.matchRemove(guild.getSupplyHubs(), argument);
        if (match.outcome() == RemoveOutcome.AMBIGUOUS) {
            player.sendMessage("§cThat installation id is used by more than one faction. Use §e/guild hub remove <faction id>:<installation id>");
            return true;
        }
        if (match.outcome() != RemoveOutcome.FOUND || match.hub() == null) {
            player.sendMessage("§cYour guild has no supply hub at that installation");
            return true;
        }
        String label = label(match.hub());
        guild.getSupplyHubs().remove(match.hub());
        player.sendMessage("§aRemoved the supply hub at §f" + label);
        recalculateTrade();
        return true;
    }

    private static void listPermits(Player player, Faction faction) {
        List<String> permits = faction.getHubPermits();
        if (permits.isEmpty()) {
            player.sendMessage("§7No hub permits");
            return;
        }
        player.sendMessage("§eHub permits");
        for (String guildId : permits) {
            Guild guild = FactionManager.getGuildByString(guildId);
            if (guild == null) {
                player.sendMessage("§7- §f" + guildId);
            } else {
                player.sendMessage("§7- §f" + guild.getName() + " §7(" + guild.getId() + ")");
            }
        }
    }

    private static String statusOf(Guild guild, SupplyHub hub, Installation installation) {
        String guildFactionId = guild.getFaction() == null ? null : guild.getFaction().getId();
        Faction owner = FactionManager.getByString(hub.ownerFactionId());
        boolean permit = owner != null && owner.hasHubPermit(guild.getId());
        int slots = 0;
        if (installation != null) {
            slots = InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
        }
        List<SupplyHub> atInstallation = SupplyHubService.atInstallation(
                hub.ownerFactionId(), hub.installationId(), SupplyHubService.allGuilds());
        HubStanding standing = SupplyHubService.standing(
                guild, hub,
                installation != null,
                SupplyHubService.ownerAllows(guildFactionId, hub.ownerFactionId(), permit),
                slots,
                atInstallation);
        return SupplyHubService.statusText(standing);
    }

    private static String label(SupplyHub hub) {
        Installation installation = SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
        if (installation == null) {
            return hub.installationId();
        }
        return installation.getName();
    }

    private static List<PlacedCandidate> covering(Location location) {
        List<PlacedCandidate> covering = new ArrayList<>();
        if (location == null || FactionManager.factions == null) {
            return covering;
        }
        for (Faction faction : FactionManager.factions) {
            if (faction == null || faction.getInstallationHandler() == null || faction.getId() == null) {
                continue;
            }
            for (Installation installation : faction.getInstallationHandler().getAll()) {
                if (installation == null || !InstallationBounds.isWithinRadius(installation, location)) {
                    continue;
                }
                double distance = InstallationBounds.horizontalDistanceBlocks(
                        installation.getCenterX(), installation.getCenterZ(), location);
                int slots = InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel());
                covering.add(new PlacedCandidate(
                        new SupplyHubService.HubCandidate(faction.getId(), installation.getId(), distance),
                        slots,
                        installation.getId(),
                        faction.getId()));
            }
        }
        return covering;
    }

    private static PlacedCandidate placed(List<PlacedCandidate> covering, SupplyHubService.HubCandidate site) {
        for (PlacedCandidate candidate : covering) {
            if (candidate != null && candidate.site() == site) {
                return candidate;
            }
        }
        return SupplyHubService.nearest(covering);
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

    private static List<String> connectionLines(Guild guild, Installation installation) {
        List<String> lines = new ArrayList<>();
        if (installation == null) {
            return lines;
        }
        double tradeBonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_TRADE);
        double productionBonus = GuildModifierOverride.resolve(guild, GuildModifier.HUB_PRODUCTION);
        for (HubTransport.Link link : HubNetwork.linksFor(guild)) {
            if (link.fromProvince() != installation.getProvince()
                    || link.mode().getKind() != installation.getKind()) {
                continue;
            }
            lines.add("§7Sends to §f" + hubNameIn(guild, link) + " §7by " + link.mode().getKey()
                    + " (" + Math.round(link.distance()) + " blocks): §e"
                    + Math.round(link.boostedTradeFactor(tradeBonus) * 100)
                    + "% §7trade, §e"
                    + Math.round(link.boostedProductionFactor(productionBonus) * 100)
                    + "% §7production");
        }
        return lines;
    }

    private static String hubNameIn(Guild guild, HubTransport.Link link) {
        for (SupplyHub hub : guild.getSupplyHubs()) {
            Installation other = SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
            if (other != null
                    && other.getProvince() == link.toProvince()
                    && other.getKind() == link.mode().getKind()) {
                return other.getName();
            }
        }
        return "province " + link.toProvince();
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
