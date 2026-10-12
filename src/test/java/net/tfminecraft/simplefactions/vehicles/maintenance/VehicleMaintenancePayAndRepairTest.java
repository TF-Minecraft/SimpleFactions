package net.tfminecraft.simplefactions.vehicles.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.economy.EconomyRuntime;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.testsupport.TestTreasuries;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenancePayService.*;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class VehicleMaintenancePayAndRepairTest {
    @TempDir Path folder;
    final CharacterRef payer = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    VehicleMaintenanceStore store;
    VehicleMaintenancePayService service;
    TestTreasuries treasury;
    MockedStatic<Bukkit> bukkit;
    @BeforeEach void setup() throws Exception {
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        treasury = new TestTreasuries();
        Files.writeString(folder.resolve("vehicles.yml"), "categories:\n  ships:\n    ship:\n      upkeep: 20\n      size: 1\n");
        VehiclesConfigLoader.load(folder.resolve("vehicles.yml").toFile());
        store = new VehicleMaintenanceStore();
        service = new VehicleMaintenancePayService(store);
        var fees = new VehicleFeeStore(); fees.bind(folder.resolve("Cache").toFile());
        VehiclePayments.startup(folder, fees, store);
        TestTreasuries.set(Denars.bank(payer), 5000);
        TestTreasuries.set(Denars.pouch(payer), 500);
    }
    @AfterEach void close() { VehiclePayments.stop(); treasury.close(); bukkit.close(); EconomyRuntime.recovered(); }
    @Test void selectedAccountPaysAndSecondClickCannotPayAgain() {
        store.markUnpaid("v", 1);
        assertEquals(20, service.payAmount("ship"));
        assertEquals(VehicleMaintenancePayResult.INSUFFICIENT_POUCH, service.tryPay("pouch", payer, "v", "ship", PaymentSource.POUCH));
        assertTrue(VehicleMaintenanceRepairListener.shouldCancelRepair(store, "v"));
        assertEquals(VehicleMaintenancePayResult.SUCCESS, service.tryPay("bank", payer, "v", "ship", PaymentSource.BANK));
        assertFalse(VehicleMaintenanceRepairListener.shouldCancelRepair(store, "v"));
        assertEquals(3000, Denars.balance(Denars.bank(payer)));
        assertEquals(500, Denars.balance(Denars.pouch(payer)));
        assertEquals(VehicleMaintenancePayResult.NOT_UNPAID, service.tryPay("repeat", payer, "v", "ship", PaymentSource.BANK));
    }
    @Test void missingTypeSessionOrServiceMovesNothingAndBankNeverFallsBackToPouch() {
        store.markUnpaid("v", 1);
        assertEquals(VehicleMaintenancePayResult.UNKNOWN_TYPE, service.tryPay("unknown", payer, "v", "unknown", PaymentSource.BANK));
        assertEquals(VehicleMaintenancePayResult.UNAVAILABLE, service.tryPay("missing", null, "v", "ship", PaymentSource.BANK));
        TestTreasuries.set(Denars.bank(payer), 0);
        TestTreasuries.set(Denars.pouch(payer), 5000);
        assertEquals(VehicleMaintenancePayResult.INSUFFICIENT_BANK, service.tryPay("bank", payer, "v", "ship", PaymentSource.BANK));
        assertEquals(5000, Denars.balance(Denars.pouch(payer)));
        assertTrue(store.isUnpaid("v"));
        VehiclePayments.stop();
        assertEquals(VehicleMaintenancePayResult.UNAVAILABLE, service.tryPay("off", payer, "v", "ship", PaymentSource.BANK));
        assertEquals(VehicleMaintenancePayResult.NOT_UNPAID, service.tryPay("nil", payer, null, "ship", PaymentSource.BANK));
    }
}
