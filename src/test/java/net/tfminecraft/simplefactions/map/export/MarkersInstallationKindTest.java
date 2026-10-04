package net.tfminecraft.simplefactions.map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary;
import net.tfminecraft.simplefactions.guild.network.NetworkSummary.GuildLabel;
import net.tfminecraft.simplefactions.guild.network.TradeGraph;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.ProvinceData;
import net.tfminecraft.simplefactions.guild.network.TradeGraphBuilder.Site;
import net.tfminecraft.simplefactions.guild.hub.HubTransport;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

class MarkersInstallationKindTest {
    @Test
    void trainStationUsesCommandNameAsMapKind() {
        Installation station = new Installation(
                "central_station", "Central Station", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L);

        assertEquals("train_station", Markers.installationKind(station));
    }

    @Test
    void installationMapRowIncludesLevel() {
        Installation station = new Installation(
                "central_station", "Central Station", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L, 2);

        var row = Markers.installationRow(station, "faction-a");

        assertTrue(row.get("level").isJsonPrimitive());
        assertEquals(2, row.get("level").getAsInt());
    }

    @Test
    void installationMapRowIncludesHubCapacityAndUsage() {
        Installation station = new Installation(
                "central_station", "Central Station", InstallationKind.TRAIN_STATION, 1, 0, 0, 1L, 2);

        var row = Markers.installationRow(station, "faction-a", 2, 1);

        assertEquals(2, row.get("hub_slots").getAsInt());
        assertEquals(1, row.get("hubs").getAsInt());
    }

    @Test
    void hubLinkRowUsesMapShapeAndRoundsShares() {
        HubTransport.Link link = new HubTransport.Link(12, 28, HubTransport.Mode.RAIL, 1234.5, 0.629876, 0.224444);

        var row = Markers.hubLinkRow(
                "The_Chisels",
                "§bThe Chisels",
                "Thalendor",
                link,
                new Markers.HubEndpoint("Abrar_Station", "Khazabrar", "§aAbrar Station", 12, 740, 1344),
                new Markers.HubEndpoint("Dunir_Station", "Khazabrar", "Dunir Station", 28, 572, 1340));

        assertEquals("The_Chisels", row.get("guild_id").getAsString());
        assertEquals("The Chisels", row.get("guild_name").getAsString());
        assertEquals("Thalendor", row.get("faction_id").getAsString());
        assertEquals("rail", row.get("mode").getAsString());
        assertEquals(1234.5, row.get("distance").getAsDouble());
        assertEquals(0.6299, row.get("trade_share").getAsDouble());
        assertEquals(0.2244, row.get("production_share").getAsDouble());
        assertEquals("Abrar_Station", row.getAsJsonObject("from").get("installation_id").getAsString());
        assertEquals("Khazabrar", row.getAsJsonObject("from").get("faction_id").getAsString());
        assertEquals("Abrar Station", row.getAsJsonObject("from").get("name").getAsString());
        assertEquals(28, row.getAsJsonObject("to").get("province_id").getAsInt());
    }

    @Test
    void tradeNetworksAndEdgesKeepTheGraphNextToHubLinks() {
        Map<Integer, ProvinceData> provinces = new HashMap<>();
        provinces.put(1, new ProvinceData(Terrain.PLAINS, Set.of(2)));
        provinces.put(2, new ProvinceData(Terrain.SEA, Set.of(1, 3)));
        provinces.put(3, new ProvinceData(Terrain.SEA, Set.of(2, 4)));
        provinces.put(4, new ProvinceData(Terrain.PLAINS, Set.of(3)));
        TradeGraph graph = TradeGraphBuilder.build(List.of(
                new Site("alpha", new Installation(
                        "port-a", "Port A", InstallationKind.PORT, 1, 0, 0, 1L), 2, true),
                new Site("beta", new Installation(
                        "port-b", "Port B", InstallationKind.PORT, 4, 10, 0, 1L), 1, true)),
                provinces, (from, to) -> Optional.empty(), point -> 0);

        JsonArray networks = Markers.tradeNetworkRows(NetworkSummary.summarize(
                graph, Map.of(), List.of(new GuildLabel("chisels", "The Chisels")),
                Map.of("alpha", "Alpha", "beta", "Beta"), "Vardera",
                (guild, province) -> 0));
        JsonObject network = networks.get(0).getAsJsonObject();
        assertEquals("The Vardera Network", network.get("name").getAsString());
        assertTrue(network.get("global").getAsBoolean());
        JsonArray nodes = network.getAsJsonArray("nodes");
        assertEquals("port-a", nodes.get(0).getAsJsonObject().get("installation_id").getAsString());
        assertEquals("alpha", nodes.get(0).getAsJsonObject().get("owner").getAsString());
        assertEquals(1, nodes.get(0).getAsJsonObject().get("province_id").getAsInt());
        assertEquals("port-b", nodes.get(1).getAsJsonObject().get("installation_id").getAsString());
        assertEquals(4, nodes.get(1).getAsJsonObject().get("province_id").getAsInt());

        JsonObject edge = Markers.tradeEdgeRows(graph.edges()).get(0).getAsJsonObject();
        assertEquals("sea", edge.get("mode").getAsString());
        assertEquals("port-a", edge.getAsJsonObject("from").get("installation_id").getAsString());
        assertEquals("alpha", edge.getAsJsonObject("from").get("owner").getAsString());
        assertEquals("port-b", edge.getAsJsonObject("to").get("installation_id").getAsString());
        assertEquals("beta", edge.getAsJsonObject("to").get("owner").getAsString());
        assertEquals(2, edge.getAsJsonArray("provinces").get(0).getAsInt());
        assertEquals(3, edge.getAsJsonArray("provinces").get(1).getAsInt());
    }

    @Test
    void exportWritesTradeNetworksNextToHubLinks() throws Exception {
        List<Faction> previous = FactionManager.factions;
        File out = File.createTempFile("markers", ".json");
        try {
            FactionManager.factions = new ArrayList<>();
            Markers.export(out);
            String text = Files.readString(out.toPath());
            int hub = text.indexOf("\"hub_links\"");
            int networks = text.indexOf("\"trade_networks\"");
            int edges = text.indexOf("\"trade_edges\"");
            int forts = text.indexOf("\"forts\"");
            assertTrue(hub >= 0 && networks > hub && edges > networks && forts > edges);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            assertTrue(root.get("hub_links").isJsonArray());
            assertTrue(root.get("trade_networks").isJsonArray());
            assertTrue(root.get("trade_edges").isJsonArray());
        } finally {
            FactionManager.factions = previous;
            out.delete();
        }
    }
}
