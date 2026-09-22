package me.Plugins.SimpleFactions.Map.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.settlement.Settlement;

public final class Markers {
    private Markers() {
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

                int population = faction.getSettlementHandler().getPopulation(settlement).size();
                String markerSize = population > Cache.settlementLargePopulationThreshold ? "large" : "small";
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
                JsonObject row = new JsonObject();
                row.addProperty("id", installation.getId());
                row.addProperty("name", installation.getName());
                row.addProperty("kind", installation.getKind().getCommandName());
                row.addProperty("faction_id", faction.getId());
                row.addProperty("province_id", installation.getProvince());
                row.addProperty("center_x", installation.getCenterX());
                row.addProperty("center_z", installation.getCenterZ());
                installations.add(row);
            }
        }
        root.add("installations", installations);

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
}
