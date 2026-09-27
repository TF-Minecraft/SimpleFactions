package net.tfminecraft.simplefactions.vehicles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.data.VehicleRemovePayload;
import net.tfminecraft.vehicleframework.enums.VehicleDeath;
import net.tfminecraft.vehicleframework.enums.VehicleRemoveReason;

class VehicleRemoveKeepsFactionVehiclesTest {
    private PlayerVehicleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PlayerVehicleRegistry();
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "v1", "sloop", OwnershipMode.POOL, null, "rome"));
    }

    @Test
    void chunkUnloadKeepsThePoolRecord() {
        assertFalse(VehicleIntegrationListener.dropRecord(
                registry, "v1", VehicleRemovePayload.remove(VehicleRemoveReason.UNLOAD)));

        assertTrue(registry.isFactionOwned("v1"));
    }

    @Test
    void destroyedVehicleLosesItsRecord() {
        assertTrue(VehicleIntegrationListener.dropRecord(
                registry, "v1", VehicleRemovePayload.death(VehicleDeath.DIE)));

        assertFalse(registry.isFactionOwned("v1"));
    }
}
