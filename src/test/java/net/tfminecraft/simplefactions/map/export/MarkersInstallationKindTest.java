package net.tfminecraft.simplefactions.map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.hub.HubTransport;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

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
}
