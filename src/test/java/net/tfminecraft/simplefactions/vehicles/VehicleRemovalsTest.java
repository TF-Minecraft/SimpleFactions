package net.tfminecraft.simplefactions.vehicles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.vehicleframework.data.VehicleRemovePayload;
import net.tfminecraft.vehicleframework.enums.VehicleDeath;
import net.tfminecraft.vehicleframework.enums.VehicleRemoveReason;

class VehicleRemovalsTest {
    @Test
    void chunkUnloadIsNotGone() {
        assertFalse(VehicleRemovals.isGoneForGood(VehicleRemovePayload.remove(VehicleRemoveReason.UNLOAD)));
    }

    @Test
    void destructionIsGone() {
        assertTrue(VehicleRemovals.isGoneForGood(VehicleRemovePayload.death(VehicleDeath.DIE)));
        assertTrue(VehicleRemovals.isGoneForGood(VehicleRemovePayload.remove(VehicleRemoveReason.PLAYER_DESTROY)));
        assertTrue(VehicleRemovals.isGoneForGood(VehicleRemovePayload.remove(VehicleRemoveReason.ADMIN_KILL)));
        assertTrue(VehicleRemovals.isGoneForGood(VehicleRemovePayload.remove(VehicleRemoveReason.GENERIC)));
        assertTrue(VehicleRemovals.isGoneForGood(null));
    }
}
