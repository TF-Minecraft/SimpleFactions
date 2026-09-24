package net.tfminecraft.simplefactions.vehicles.maintenance;


import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.VehicleMaintenancePayResult;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceRepairListener;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerPouch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.PaymentSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;

class VehicleMaintenancePayAndRepairTest {
    private Path tempDir;
    private VehicleMaintenanceStore store;
    private TestPlayerPouch pouch;
    private VehicleMaintenancePayService payService;
    private PlayerBank bank;

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
        bank = mock(PlayerBank.class);
        payService = new VehicleMaintenancePayService(store, pouch, bank);
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
        verifyNoInteractions(bank);
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
    void bankPaymentChargesOnlyPayersBankAndAllowsRepair() {
        UUID payer = UUID.randomUUID();
        pouch.setBalance(payer, 50.0);
        store.markUnpaid("vehicle-1", 1L);
        when(bank.withdrawFromBank(payer, 20.0)).thenReturn(true);

        assertEquals(VehicleMaintenancePayResult.SUCCESS,
                payService.tryPay(payer, "vehicle-1", "ironclad", PaymentSource.BANK));
        verify(bank).withdrawFromBank(payer, 20.0);
        verifyNoMoreInteractions(bank);
        assertEquals(50.0, pouch.getPouchBalance(payer));
        assertFalse(VehicleMaintenanceRepairListener.shouldCancelRepair(store, "vehicle-1"));

        assertEquals(VehicleMaintenancePayResult.NOT_UNPAID,
                payService.tryPay(payer, "vehicle-1", "ironclad", PaymentSource.BANK));
        verifyNoMoreInteractions(bank);
    }

    @Test
    void failedBankPaymentLeavesUnpaidWithoutFallingBackToPouch() {
        UUID payer = UUID.randomUUID();
        pouch.setBalance(payer, 50.0);
        store.markUnpaid("vehicle-1", 1L);

        assertEquals(VehicleMaintenancePayResult.INSUFFICIENT_BANK,
                payService.tryPay(payer, "vehicle-1", "ironclad", PaymentSource.BANK));
        assertEquals(50.0, pouch.getPouchBalance(payer));
        assertEquals(true, VehicleMaintenanceRepairListener.shouldCancelRepair(store, "vehicle-1"));
        verify(bank).withdrawFromBank(payer, 20.0);
    }

    @Test
    void unknownVehicleTypeDoesNotChargeBankOrClearUnpaid() {
        store.markUnpaid("vehicle-1", 1L);
        assertEquals(VehicleMaintenancePayResult.UNKNOWN_TYPE,
                payService.tryPay(UUID.randomUUID(), "vehicle-1", "unknown", PaymentSource.BANK));
        assertEquals(true, store.isUnpaid("vehicle-1"));
        verifyNoInteractions(bank);
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
