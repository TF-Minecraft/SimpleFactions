package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.EspionageModes;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

/** Owned vehicles become report fields: totals, categories, berths and, at Detailed, their types. */
class VehicleIntelligenceTest {
    private final SimpleFactions previous = SimpleFactions.plugin;
    private MockedStatic<SimpleFactions> plugin;
    private MockedStatic<VehiclesConfigLoader> vehicles;
    private PlayerVehicleRegistry registry;
    private Faction faction;

    @BeforeEach
    void setup() {
        EspionageModes.reset();
        registry = new PlayerVehicleRegistry();
        SimpleFactions.plugin = mock(SimpleFactions.class);
        plugin = mockStatic(SimpleFactions.class);
        plugin.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
        vehicles = mockStatic(VehiclesConfigLoader.class);
        vehicles.when(VehiclesConfigLoader::getCategoryIds).thenReturn(Set.of("land_vehicles", "ships", "train"));
        vehicles.when(() -> VehiclesConfigLoader.getCategoryId(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "horse_cart", "small_car" -> Optional.of("land_vehicles");
            case "galleon" -> Optional.of("ships");
            default -> Optional.empty();
        });
        vehicles.when(() -> VehiclesConfigLoader.getCategoryDisplayName("ships")).thenReturn("Ships");
        faction = mock(Faction.class, RETURNS_DEEP_STUBS);
        when(faction.getId()).thenReturn("realm");
        Installation fort = mock(Installation.class);
        when(fort.getId()).thenReturn("fort");
        when(faction.getInstallationHandler().getAll()).thenReturn(List.of(fort));
        add("horse_cart", OwnershipMode.POOL, null);
        add("horse_cart", OwnershipMode.POOL, null);
        add("mystery_raft", OwnershipMode.POOL, null);
        add("galleon", OwnershipMode.INSTALLATION, "fort");
        add("small_car", OwnershipMode.PERSONAL, null);
    }

    @AfterEach
    void close() {
        vehicles.close();
        plugin.close();
        SimpleFactions.plugin = previous;
        EspionageModes.reset();
    }

    private void add(String type, OwnershipMode mode, String installation) {
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), UUID.randomUUID().toString(), type, mode, installation,
                mode == OwnershipMode.PERSONAL ? null : "realm"));
    }

    @Test
    void captureCountsPoolAndBerthsAndEveryConfiguredCategory() {
        Map<String, Double> values = new HashMap<>();
        VehicleIntelligence.capture(values, faction);
        assertEquals(4.0, values.get(VehicleIntelligence.TOTAL), "Pool and berths, never personal vehicles");
        assertEquals(1.0, values.get(VehicleIntelligence.berthedKey("fort")));
        assertEquals(2.0, values.get(VehicleIntelligence.categoryKey("land_vehicles")));
        assertEquals(1.0, values.get(VehicleIntelligence.categoryKey("ships")));
        assertEquals(0.0, values.get(VehicleIntelligence.categoryKey("train")), "Empty categories are captured too");
    }

    @Test
    void detailedReportsNameTheVehicleTypesAndLoreShowsTheRanges() {
        Map<String, Double> values = new HashMap<>();
        VehicleIntelligence.capture(values, faction);
        var report = EspionageService.createReport(values, 100, new Random(1));
        VehicleIntelligence.captureTypes(report, faction);
        assertEquals(List.of("horse_cart", "mystery_raft"), report.details("vehicle-types", VehicleIntelligence.typesKey(null)));
        assertEquals(List.of("galleon"), report.details("vehicle-types", VehicleIntelligence.typesKey("fort")));
        var pool = String.join("\n", VehicleIntelligence.lore(report, null));
        assertFalse(pool.contains("Vehicles: §7Unknown"));
        assertTrue(pool.contains("Ships"));
        assertTrue(pool.contains("land vehicles"), "Categories without a display name use their id");
        assertTrue(pool.contains("Types in pool: §fhorse_cart, mystery_raft"));
        var berth = String.join("\n", VehicleIntelligence.lore(report, "fort"));
        assertTrue(berth.contains("Types: §fgalleon"));

        var broad = EspionageService.createReport(values, 40, new Random(1));
        VehicleIntelligence.captureTypes(broad, faction);
        assertTrue(broad.details("vehicle-types", VehicleIntelligence.typesKey(null)).isEmpty(), "Types need Detailed");
        assertTrue(String.join("\n", VehicleIntelligence.lore(broad, "fort")).contains("Vehicles: §7Unknown"),
                "Berths need Reliable");
        assertEquals(List.of("§7Vehicles: §7Unknown"), VehicleIntelligence.lore(null, null));
    }

    @Test
    void newFieldsUseTheirOwnGatesAndCannotBeNegative() {
        assertEquals("vehicle-categories", EspionageConfig.metricKey(VehicleIntelligence.categoryKey("ships")));
        assertEquals("berthed-vehicles", EspionageConfig.metricKey(VehicleIntelligence.berthedKey("fort")));
        assertEquals("installation-details", EspionageConfig.metricKey("Installation:fort:Level"));
        assertEquals("vehicles", EspionageConfig.metricKey(VehicleIntelligence.TOTAL));
        assertEquals("army", EspionageConfig.metricKey("Army"));
        assertTrue(EspionageConfig.allows(IntelligenceTier.RUMOURS, "Army"));
        assertTrue(EspionageConfig.allows(IntelligenceTier.RUMOURS, VehicleIntelligence.TOTAL));
        assertFalse(EspionageConfig.allows(IntelligenceTier.RUMOURS, "Professional army"));
        assertTrue(EspionageConfig.allows(IntelligenceTier.BROAD, "Professional army"));
        assertTrue(IntelligenceRanges.nonnegative("Army"));
        assertTrue(IntelligenceRanges.nonnegative(VehicleIntelligence.categoryKey("ships")));
    }

    @Test
    void withoutARegistryNothingIsCaptured() {
        SimpleFactions.plugin = null;
        Map<String, Double> values = new HashMap<>();
        VehicleIntelligence.capture(values, faction);
        assertTrue(values.isEmpty());
        var report = EspionageService.createReport(values, 100, new Random(1));
        VehicleIntelligence.captureTypes(report, faction);
        assertTrue(report.details.isEmpty());
    }
}
