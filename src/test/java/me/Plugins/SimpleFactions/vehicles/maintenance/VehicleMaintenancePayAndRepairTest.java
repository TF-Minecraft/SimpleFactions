package me.Plugins.SimpleFactions.vehicles.maintenance;


import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenanceStore;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenancePayService;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenancePayService.VehicleMaintenancePayResult;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenanceRepairListener;
import me.Plugins.SimpleFactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerPouch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;

class VehicleMaintenancePayAndRepairTest {
    private Path tempDir;
    private VehicleMaintenanceStore store;
    private TestPlayerPouch pouch;
    private VehicleMaintenancePayService payService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-pay-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
        store = new VehicleMaintenanceStore();
        pouch = new TestPlayerPouch();
        payService = new VehicleMaintenancePayService(store, pouch);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void payAmountEqualsTypeUpkeep() {
        assertEquals(20.0, payService.payAmount("ironclad"));
    }

    @Test
    void tryPay_chargesPouchAndClearsUnpaid() {
        UUID playerUuid = UUID.randomUUID();
        pouch.setBalance(playerUuid, 50.0);
        store.markUnpaid("vehicle-1", 1L);

        assertEquals(
                VehicleMaintenancePayResult.SUCCESS,
                payService.tryPay(playerUuid, "vehicle-1", "ironclad"));
        assertEquals(30.0, pouch.getPouchBalance(playerUuid));
        assertFalse(store.isUnpaid("vehicle-1"));
    }

    @Test
    void tryPay_insufficientPouchLeavesUnpaid() {
        UUID playerUuid = UUID.randomUUID();
        pouch.setBalance(playerUuid, 5.0);
        store.markUnpaid("vehicle-1", 1L);

        assertEquals(
                VehicleMaintenancePayResult.INSUFFICIENT_POUCH,
                payService.tryPay(playerUuid, "vehicle-1", "ironclad"));
        assertEquals(5.0, pouch.getPouchBalance(playerUuid));
        assertEquals(true, store.isUnpaid("vehicle-1"));
    }

    @Test
    void repairStart_cancelsWhenUnpaid() {
        store.markUnpaid("vehicle-1", 1L);
        assertEquals(true, VehicleMaintenanceRepairListener.shouldCancelRepair(store, "vehicle-1"));
        assertEquals(false, VehicleMaintenanceRepairListener.shouldCancelRepair(store, "vehicle-2"));
    }

    private static final class TestPlayerPouch implements PlayerPouch {
        private final Map<UUID, Double> balances = new HashMap<>();

        void setBalance(UUID playerUuid, double balance) {
            balances.put(playerUuid, balance);
        }

        @Override
        public double getPouchBalance(UUID playerUuid) {
            return balances.getOrDefault(playerUuid, 0.0);
        }

        @Override
        public boolean withdrawFromPouch(UUID playerUuid, double amount) {
            double balance = getPouchBalance(playerUuid);
            if (balance < amount) {
                return false;
            }
            balances.put(playerUuid, balance - amount);
            return true;
        }
    }
}
