package net.tfminecraft.simplefactions.vehicles.berth;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.vehicleframework.data.OwnerData;

class InstallationVehicleOwnerSyncTest {
    private List<Faction> previousFactions;
    private PlayerVehicleRegistry registry;
    private InstallationVehicleOwnerSync ownerSync;

    @BeforeEach
    void setUp() {
        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();
        registry = new PlayerVehicleRegistry();
        ownerSync = new InstallationVehicleOwnerSync(registry);
    }

    @AfterEach
    void tearDown() {
        FactionManager.factions = previousFactions;
    }

    @Test
    void expectedOwner_usesFactionLeader() {
        Faction faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("Alice");

        assertEquals("player_Alice", InstallationVehicleOwnerSync.expectedOwner(faction));
    }

    @Test
    void applyLeaderOwner_setsPlayerPrefix() {
        Faction faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("Bob");
        OwnerData ownerData = new OwnerData();

        ownerSync.applyLeaderOwner(ownerData, faction);

        assertEquals("player_Bob", ownerData.getOwner());
    }

    @Test
    void syncIfBerthed_updatesStaleOwnerForInstallationRecord() {
        Installation installation = new Installation(
                "port-1",
                "Harbour",
                InstallationKind.PORT,
                1,
                0,
                0,
                0L);
        InstallationHandler handler = mock(InstallationHandler.class);
        when(handler.getById("port-1")).thenReturn(installation);

        Faction faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("Carol");
        when(faction.getInstallationHandler()).thenReturn(handler);
        FactionManager.factions.add(faction);

        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(),
                "vehicle-1",
                "ironclad",
                OwnershipMode.INSTALLATION,
                "port-1"));

        OwnerData ownerData = new OwnerData();
        ownerData.setOwner("player_Alice");

        ownerSync.syncIfBerthed("vehicle-1", ownerData);

        assertEquals("player_Carol", ownerData.getOwner());
    }

    @Test
    void syncIfBerthed_migratesLegacyFactionOwner() {
        Installation installation = new Installation(
                "port-1",
                "Harbour",
                InstallationKind.PORT,
                1,
                0,
                0,
                0L);
        InstallationHandler handler = mock(InstallationHandler.class);
        when(handler.getById("port-1")).thenReturn(installation);

        Faction faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("Carol");
        when(faction.getInstallationHandler()).thenReturn(handler);
        FactionManager.factions.add(faction);

        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(),
                "vehicle-1",
                "ironclad",
                OwnershipMode.INSTALLATION,
                "port-1"));

        OwnerData ownerData = new OwnerData();
        ownerData.setOwner("faction_Brume");

        ownerSync.syncIfBerthed("vehicle-1", ownerData);

        assertEquals("player_Carol", ownerData.getOwner());
    }

    @Test
    void syncIfBerthed_noOpForPersonalRecord() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(),
                "vehicle-1",
                "ironclad",
                OwnershipMode.PERSONAL,
                null));

        OwnerData ownerData = new OwnerData();
        ownerData.setOwner("player_Alice");

        ownerSync.syncIfBerthed("vehicle-1", ownerData);

        assertEquals("player_Alice", ownerData.getOwner());
    }
}
