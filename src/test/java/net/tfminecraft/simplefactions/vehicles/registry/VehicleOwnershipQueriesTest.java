package net.tfminecraft.simplefactions.vehicles.registry;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.vehicleframework.data.OwnedVehicleSummary;

class VehicleOwnershipQueriesTest {
    @AfterEach
    void tearDown() {
        VehicleOwnershipQueries.setSourceForTests(null);
    }

    @Test
    void isPlayerOwner_rejectsNoneAndBlank() {
        assertFalse(VehicleOwnershipQueries.isPlayerOwner(null));
        assertFalse(VehicleOwnershipQueries.isPlayerOwner("none"));
        assertFalse(VehicleOwnershipQueries.isPlayerOwner("player_none"));
        assertTrue(VehicleOwnershipQueries.isPlayerOwner("player_Alice"));
        assertEquals("Alice", VehicleOwnershipQueries.playerNameFromOwner("player_Alice"));
    }

    @Test
    void personalVehicles_excludesBerthedUuids() {
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(),
                "v-berthed",
                "ironclad",
                OwnershipMode.INSTALLATION,
                "port-1"));
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory()
                .add("v-personal", "ironclad", "player_Alice")
                .add("v-berthed", "ironclad", "player_Alice"));

        List<OwnedVehicleSummary> personal =
                VehicleOwnershipQueries.personalVehicles("Alice", registry);
        assertEquals(1, personal.size());
        assertEquals("v-personal", personal.get(0).getUuid());
    }
}
