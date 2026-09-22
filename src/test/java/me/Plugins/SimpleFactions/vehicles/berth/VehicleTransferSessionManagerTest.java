package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.berth.VehicleTransferSessionManager;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleTransferSessionManager.VehicleTransferSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VehicleTransferSessionManagerTest {
    private VehicleTransferSessionManager manager;
    private UUID leaderUuid;

    @BeforeEach
    void setUp() {
        manager = new VehicleTransferSessionManager();
        leaderUuid = UUID.randomUUID();
    }

    @Test
    void putAndGet_returnsSession() {
        VehicleTransferSession session = new VehicleTransferSession(
                "port-1",
                System.currentTimeMillis() + 60_000L);
        manager.put(leaderUuid, session);

        assertNotNull(manager.get(leaderUuid));
        assertEquals("port-1", manager.get(leaderUuid).getInstallationId());
    }

    @Test
    void put_replacesExistingSession() {
        manager.put(leaderUuid, new VehicleTransferSession(
                "port-1",
                System.currentTimeMillis() + 60_000L));
        manager.put(leaderUuid, new VehicleTransferSession(
                "port-2",
                System.currentTimeMillis() + 60_000L));

        assertEquals("port-2", manager.get(leaderUuid).getInstallationId());
    }

    @Test
    void get_returnsNullWhenExpired() {
        manager.put(leaderUuid, new VehicleTransferSession(
                "port-1",
                System.currentTimeMillis() - 1L));

        assertNull(manager.get(leaderUuid));
    }

    @Test
    void clear_removesSession() {
        manager.put(leaderUuid, new VehicleTransferSession(
                "port-1",
                System.currentTimeMillis() + 60_000L));
        manager.clear(leaderUuid);

        assertNull(manager.get(leaderUuid));
    }
}
