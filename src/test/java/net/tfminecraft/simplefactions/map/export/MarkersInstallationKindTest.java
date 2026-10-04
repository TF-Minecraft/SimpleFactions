package net.tfminecraft.simplefactions.map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

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
        assertNull(row.get("hub_slots"));
        assertNull(row.get("hubs"));
    }

    @Test
    void exportOmitsHubLinksAndTradeNetworks() throws Exception {
        List<Faction> previous = FactionManager.factions;
        File out = File.createTempFile("markers", ".json");
        try {
            FactionManager.factions = new ArrayList<>();
            Markers.export(out);
            String text = Files.readString(out.toPath());
            assertFalse(text.contains("\"hub_links\""));
            assertFalse(text.contains("\"trade_networks\""));
            assertFalse(text.contains("\"trade_edges\""));
            assertFalse(text.contains("\"hub_slots\""));
            assertFalse(text.contains("\"hubs\""));
            assertTrue(JsonParser.parseString(text).getAsJsonObject().has("forts"));
        } finally {
            FactionManager.factions = previous;
            out.delete();
        }
    }
}
