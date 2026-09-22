package me.Plugins.SimpleFactions.vehicles.maintenance;



import me.Plugins.SimpleFactions.vehicles.registry.FakeOwnedInventory;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleUpkeepProjection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.Managers.Inventory.PlayerLedgerCreator;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.player.income.PlayerCashflow;
import me.Plugins.SimpleFactions.player.income.PlayerLedger;

class VehicleUpkeepProjectionTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-upkeep-projection-");
        Files.writeString(
                tempDir.resolve("vehicles.yml"),
                """
                personal-slot-limit: 3

                categories:
                  aircraft:
                    monoplane:
                      upkeep: 8
                      size: 1
                """);
        VehiclesConfigLoader.load(tempDir.resolve("vehicles.yml").toFile());
        registry = new PlayerVehicleRegistry();
        playerUuid = UUID.randomUUID();
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("v1", "monoplane", "player_Alice"));
    }

    @AfterEach
    void tearDown() throws IOException {
        VehicleOwnershipQueries.setSourceForTests(null);
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void projectedDailyUpkeep_sumsPersonalVehiclesOnly() {
        registry.register(new PlayerVehicleRecord(
                playerUuid, "v2", "monoplane", OwnershipMode.INSTALLATION, "port-1"));
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("v1", "monoplane", "player_Alice")
                .add("v2", "monoplane", "player_Alice"));

        assertEquals(8.0, VehicleUpkeepProjection.projectedDailyUpkeep("Alice", registry));
    }

    @Test
    void displayVehicleExpense_usesProjectedBeforeSettlement() {
        PlayerLedger ledger = new PlayerLedger();

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
                MockedStatic<Bukkit> bukkit = mockBukkit()) {
            sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
            assertEquals(-8.0, VehicleUpkeepProjection.displayVehicleExpense(ledger, playerUuid));
            assertEquals(-8.0, VehicleUpkeepProjection.displayNetDaily(ledger, playerUuid));
        }
    }

    @Test
    void displayVehicleExpense_prefersSettledLedgerAmount() {
        PlayerLedger ledger = new PlayerLedger();
        ledger.add(PlayerCashflow.VEHICLE_UPKEEP, -8.0);

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
                MockedStatic<Bukkit> bukkit = mockBukkit()) {
            sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
            assertEquals(-8.0, VehicleUpkeepProjection.displayVehicleExpense(ledger, playerUuid));
        }
    }

    @Test
    void ledgerCreator_showsProjectedVehicleExpense() {
        PlayerLedger ledger = new PlayerLedger();

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
                MockedStatic<Bukkit> bukkit = mockBukkit()) {
            sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
            List<String> lore = new PlayerLedgerCreator().buildLore(ledger, playerUuid);
            String upkeepAmount = String.format("%.2f", -8.0) + "d";
            assertTrue(lore.stream().anyMatch(line ->
                    line.contains("Vehicles") && line.contains(upkeepAmount)));
        }
    }

    private MockedStatic<Bukkit> mockBukkit() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getName()).thenReturn("Alice");
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(null);
        bukkit.when(() -> Bukkit.getOfflinePlayer(playerUuid)).thenReturn(offline);
        return bukkit;
    }
}
