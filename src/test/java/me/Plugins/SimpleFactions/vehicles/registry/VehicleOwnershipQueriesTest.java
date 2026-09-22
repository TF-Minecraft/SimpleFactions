package me.Plugins.SimpleFactions.vehicles.registry;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.VehicleFramework.Data.OwnedVehicleSummary;

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
