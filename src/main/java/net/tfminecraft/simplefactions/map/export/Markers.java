package net.tfminecraft.simplefactions.map.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.md_5.bungee.api.ChatColor;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.hub.HighwaySnapshot;
import net.tfminecraft.simplefactions.guild.hub.HubNetwork;
import net.tfminecraft.simplefactions.guild.hub.HubTransport.Link;
import net.tfminecraft.simplefactions.guild.network.CurrentNetworks;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Edge;
import net.tfminecraft.simplefactions.guild.network.TradeGraph.Node;
import net.tfminecraft.simplefactions.guild.hub.SupplyHub;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.settlement.Settlement;

public final class Markers {
    private Markers() {
    }

    static String markerSizeForPopulation(int population) {
        return population > Cache.settlementLargePopulationThreshold ? "large" : "small";
    }

    public static void export(File out) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("map_id", Cache.mapRef);
        ChapterIdentity.putOnEnvelope(root);
        root.addProperty("exported_at", Instant.now().toString());
        root.addProperty("settlement_large_population_threshold", Cache.settlementLargePopulationThreshold);

        JsonArray settlements = new JsonArray();
        for (Faction faction : FactionManager.factions) {
            int factionCapital = faction.getCapital();
            for (Settlement settlement : faction.getSettlementHandler().getAll()) {
                JsonObject row = new JsonObject();
                row.addProperty("id", settlement.getId());
                row.addProperty("name", settlement.getName());
                row.addProperty("faction_id", faction.getId());
                row.addProperty("province_id", settlement.getCenterProvince());
                row.addProperty("center_x", settlement.getCenterX());
                row.addProperty("center_z", settlement.getCenterZ());

                int population = faction.getSettlementHandler().populationSize(settlement);
                String markerSize = markerSizeForPopulation(population);
                row.addProperty("population", population);
                row.addProperty("marker_size", markerSize);

                String kind = factionCapital == settlement.getCenterProvince()
                        ? "faction_capital"
                        : "settlement";
                row.addProperty("kind", kind);

                JsonArray provinces = new JsonArray();
                provinces.add(settlement.getCenterProvince());
                row.add("provinces", provinces);

                settlements.add(row);
            }
        }
        root.add("settlements", settlements);

        JsonArray installations = new JsonArray();
        for (Faction faction : FactionManager.factions) {
            for (Installation installation : faction.getInstallationHandler().getAll()) {
                installations.add(installationRow(
                        installation,
                        faction.getId(),
                        InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel()),
                        SupplyHubService.countAt(faction.getId(), installation.getId(), SupplyHubService.allGuilds())));
            }
        }
        root.add("installations", installations);

        JsonArray hubLinks = new JsonArray();
        for (Guild guild : SupplyHubService.allGuilds()) {
            hubLinks.addAll(hubLinkRows(guild, HubNetwork.linksFor(guild)));
        }
        root.add("hub_links", hubLinks);

        HighwaySnapshot snapshot = HighwaySnapshot.current();
        root.add("trade_networks", tradeNetworkRows(CurrentNetworks.from(snapshot)));
        root.add("trade_edges", tradeEdgeRows(snapshot.graph().edges()));

        JsonArray forts = new JsonArray();
        for (Faction faction : FactionManager.factions) {
            for (Installation installation : faction.getInstallationHandler().getAll()) {
                if (installation.getKind() != InstallationKind.FORT) {
                    continue;
                }

                JsonObject row = new JsonObject();
                row.addProperty("id", installation.getId());
                row.addProperty("name", installation.getName());
                row.addProperty("faction_id", faction.getId());
                row.addProperty("province_id", installation.getProvince());
                row.addProperty("center_x", installation.getCenterX());
                row.addProperty("center_z", installation.getCenterZ());

                JsonArray zocProvinces = new JsonArray();
                for (int provinceId :
                        ZocRealm.computeZocProvincesForExport(
                                installation, faction, WarManager.getActive())) {
                    zocProvinces.add(provinceId);
                }
                row.add("zoc_provinces", zocProvinces);
                forts.add(row);
            }
        }
        root.add("forts", forts);

        JsonArray wars = WarMapExporter.exportWars(WarManager.getActive());
        if (!wars.isEmpty()) {
            root.add("wars", wars);
        }

        File parent = out.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(out, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
    }

    static JsonObject installationRow(Installation installation, String factionId) {
        return installationRow(
                installation,
                factionId,
                SupplyHubService.defaultHubSlots(installation.getKind(), installation.getLevel()),
                0);
    }

    static JsonObject installationRow(Installation installation, String factionId, int hubSlots, int hubs) {
        JsonObject row = new JsonObject();
        row.addProperty("id", installation.getId());
        row.addProperty("name", installation.getName());
        row.addProperty("kind", installationKind(installation));
        row.addProperty("faction_id", factionId);
        row.addProperty("province_id", installation.getProvince());
        row.addProperty("center_x", installation.getCenterX());
        row.addProperty("center_z", installation.getCenterZ());
        row.addProperty("level", installation.getLevel());
        row.addProperty("hub_slots", hubSlots);
        row.addProperty("hubs", hubs);
        return row;
    }

    static JsonArray hubLinkRows(Guild guild, List<Link> links) {
        JsonArray rows = new JsonArray();
        if (guild == null || guild.getFaction() == null || guild.getFaction().getId() == null || links == null) {
            return rows;
        }
        for (Link link : links) {
            if (link == null || link.fromProvince() >= link.toProvince()) {
                continue;
            }
            Installation from = activeHubAt(guild, link.fromProvince(), link.fromFactionId(), link.fromInstallationId());
            Installation to = activeHubAt(guild, link.toProvince(), link.toFactionId(), link.toInstallationId());
            if (from == null || to == null) {
                continue;
            }
            Faction fromFaction = hubOwner(guild, from);
            Faction toFaction = hubOwner(guild, to);
            if (fromFaction == null || toFaction == null) {
                continue;
            }
            rows.add(hubLinkRow(
                    guild.getId(),
                    stripColor(guild.getName()),
                    guild.getFaction().getId(),
                    link,
                    hubEndpoint(from, fromFaction),
                    hubEndpoint(to, toFaction)));
        }
        return rows;
    }

    static JsonArray tradeNetworkRows(List<NetworkSummary.Summary> summaries) {
        JsonArray rows = new JsonArray();
        if (summaries == null) return rows;
        for (NetworkSummary.Summary summary : summaries) {
            if (summary == null || summary.network() == null) continue;
            JsonObject row = new JsonObject();
            row.addProperty("name", summary.name());
            row.addProperty("global", summary.global());
            JsonArray nodes = new JsonArray();
            for (Node node : summary.network().nodes()) {
                JsonObject stop = new JsonObject();
                stop.addProperty("installation_id", node.installationId());
                stop.addProperty("owner", node.ownerFactionId());
                stop.addProperty("province_id", node.provinceId());
                nodes.add(stop);
            }
            row.add("nodes", nodes);
            rows.add(row);
        }
        return rows;
    }

    static JsonArray tradeEdgeRows(List<Edge> edges) {
        JsonArray rows = new JsonArray();
        if (edges == null) return rows;
        for (Edge edge : edges) {
            if (edge == null || edge.first() == null || edge.second() == null || edge.mode() == null) continue;
            JsonObject row = new JsonObject();
            row.add("from", tradeStop(edge.first()));
            row.add("to", tradeStop(edge.second()));
            row.addProperty("mode", edge.mode().getKey());
            JsonArray provinces = new JsonArray();
            for (int provinceId : edge.provinces()) provinces.add(provinceId);
            row.add("provinces", provinces);
            rows.add(row);
        }
        return rows;
    }

    private static JsonObject tradeStop(Node node) {
        JsonObject row = new JsonObject();
        row.addProperty("installation_id", node.installationId());
        row.addProperty("owner", node.ownerFactionId());
        return row;
    }

    static JsonObject hubLinkRow(
            String guildId,
            String guildName,
            String factionId,
            Link link,
            HubEndpoint from,
            HubEndpoint to) {
        JsonObject row = new JsonObject();
        row.addProperty("guild_id", guildId);
        row.addProperty("guild_name", stripColor(guildName));
        row.addProperty("faction_id", factionId);
        row.addProperty("mode", link.mode().getKey());
        row.addProperty("distance", link.distance());
        row.addProperty("trade_share", roundShare(link.tradeFactor()));
        row.addProperty("production_share", roundShare(link.productionFactor()));
        row.add("from", hubEndpointRow(from));
        row.add("to", hubEndpointRow(to));
        return row;
    }

    static JsonObject hubEndpointRow(HubEndpoint endpoint) {
        JsonObject row = new JsonObject();
        row.addProperty("installation_id", endpoint.installationId());
        row.addProperty("faction_id", endpoint.factionId());
        row.addProperty("name", stripColor(endpoint.name()));
        row.addProperty("province_id", endpoint.provinceId());
        row.addProperty("center_x", endpoint.centerX());
        row.addProperty("center_z", endpoint.centerZ());
        return row;
    }

    private static Installation activeHubAt(Guild guild, int provinceId, String factionId, String installationId) {
        for (SupplyHub hub : guild.getSupplyHubs()) {
            Installation installation = SupplyHubService.findInstallation(hub.ownerFactionId(), hub.installationId());
            if (installation == null || installation.getProvince() != provinceId
                    || !hub.ownerFactionId().equalsIgnoreCase(factionId)
                    || !hub.installationId().equalsIgnoreCase(installationId)) {
                continue;
            }
            SupplyHubService.HubStanding standing = SupplyHubService.standing(
                    hub,
                    true,
                    SupplyHubService.hubPermitted(guild, hub.ownerFactionId(), hub.installationId()),
                    InstallationConfigLoader.getHubSlots(installation.getKind(), installation.getLevel()),
                    SupplyHubService.atInstallation(
                            hub.ownerFactionId(), hub.installationId(), SupplyHubService.allGuilds()));
            if (standing.active()) {
                return installation;
            }
        }
        return null;
    }

    private static Faction hubOwner(Guild guild, Installation installation) {
        for (SupplyHub hub : guild.getSupplyHubs()) {
            if (hub.installationId() == null || hub.ownerFactionId() == null) {
                continue;
            }
            Installation hubInstallation = SupplyHubService.findInstallation(
                    hub.ownerFactionId(), hub.installationId());
            if (hubInstallation == installation) {
                return FactionManager.getByString(hub.ownerFactionId());
            }
        }
        return null;
    }

    private static HubEndpoint hubEndpoint(Installation installation, Faction owner) {
        return new HubEndpoint(
                installation.getId(),
                owner.getId(),
                installation.getName(),
                installation.getProvince(),
                installation.getCenterX(),
                installation.getCenterZ());
    }

    private static String stripColor(String value) {
        return ChatColor.stripColor(value == null ? "" : value);
    }

    private static double roundShare(double share) {
        return Math.round(share * 10000.0) / 10000.0;
    }

    record HubEndpoint(
            String installationId,
            String factionId,
            String name,
            int provinceId,
            int centerX,
            int centerZ) {
    }

    static String installationKind(Installation installation) {
        return installation.getKind().getCommandName();
    }
}
