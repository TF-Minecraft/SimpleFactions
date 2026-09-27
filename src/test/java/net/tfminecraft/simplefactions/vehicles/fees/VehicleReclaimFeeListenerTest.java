package net.tfminecraft.simplefactions.vehicles.fees;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.vehicleframework.data.VehicleRemovePayload;
import net.tfminecraft.vehicleframework.enums.VehicleRemoveReason;

class VehicleReclaimFeeListenerTest {
    @Test
    void chunkUnloadKeepsTheLastOwner() {
        assertFalse(VehicleReclaimFeeListener.isDestroyed(VehicleRemovePayload.remove(VehicleRemoveReason.UNLOAD)));
        assertFalse(VehicleReclaimFeeListener.isDestroyed(VehicleRemovePayload.remove(VehicleRemoveReason.GENERIC)));
        assertFalse(VehicleReclaimFeeListener.isDestroyed(null));
    }

    @Test
    void destroyedVehiclesAreForgotten() {
        assertTrue(VehicleReclaimFeeListener.isDestroyed(VehicleRemovePayload.remove(VehicleRemoveReason.PLAYER_DESTROY)));
        assertTrue(VehicleReclaimFeeListener.isDestroyed(VehicleRemovePayload.remove(VehicleRemoveReason.ADMIN_KILL)));
    }
}
