package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleService;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleOwnerSync;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleInstallationLockService;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleService.CanRegisterResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationBounds;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleService.VehicleBerthTarget;
import net.tfminecraft.VehicleFramework.Data.OwnerData;

class InstallationVehicleServiceTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private InstallationVehicleService service;
    private Installation port;
    private Installation fort;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-berth-service-");
        writeVehiclesFixture();
        InstallationConfigLoader.load(writeInstallationsFixture().toFile());

        registry = new PlayerVehicleRegistry();
        InstallationVehicleOwnerSync ownerSync = new InstallationVehicleOwnerSync(registry);
        service = new InstallationVehicleService(registry, ownerSync);
        port = new Installation("port-1", "Harbour", InstallationKind.PORT, 42, 0, 0, 0L);
        fort = new Installation("fort-1", "Redoubt", InstallationKind.FORT, 42, 0, 0, 0L);
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
    void canRegister_unowned_returnsNotInRegistry() {
        VehicleBerthTarget vehicle = berthTarget("vehicle-1", "ironclad", locationAt(0, 64, 0), unowned());

        assertEquals(
            CanRegisterResult.NOT_IN_REGISTRY,
            service.canRegister(port, vehicle));
    }

    @Test
    void canRegister_alreadyBerthed_returnsAlreadyBerthed() {
        registry.register(new PlayerVehicleRecord(
            UUID.randomUUID(),
            "vehicle-1",
            "ironclad",
            OwnershipMode.INSTALLATION,
            "port-1"));
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "ironclad");

        assertEquals(
            CanRegisterResult.ALREADY_BERTHED,
            service.canRegister(port, vehicle));
    }

    @Test
    void canRegister_unknownType_returnsUnknownType() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "unknown-type");

        assertEquals(
            CanRegisterResult.UNKNOWN_TYPE,
            service.canRegister(port, vehicle));
    }

    @Test
    void canRegister_unsupportedCategory_returnsUnsupportedCategory() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "ironclad");

        assertEquals(
            CanRegisterResult.UNSUPPORTED_CATEGORY,
            service.canRegister(fort, vehicle));
    }

    @Test
    void canRegister_noCapacity_returnsNoCapacity() {
        for (int i = 0; i < 8; i++) {
            registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(),
                "berthed-" + i,
                "ironclad",
                OwnershipMode.INSTALLATION,
                port.getId()));
        }

        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "ironclad");

        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class)) {
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(port), any())).thenReturn(true);
            bounds.when(() -> InstallationBounds.isCorrectProvince(eq(port), any())).thenReturn(true);

            assertEquals(
                CanRegisterResult.NO_CAPACITY,
                service.canRegister(port, vehicle));
        }
    }

    @Test
    void canRegister_outOfRadius_returnsOutOfRadius() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "ironclad");

        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class)) {
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(port), any())).thenReturn(false);

            assertEquals(
                CanRegisterResult.OUT_OF_RADIUS,
                service.canRegister(port, vehicle));
        }
    }

    @Test
    void canRegister_wrongProvince_returnsWrongProvince() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "ironclad");

        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class)) {
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(port), any())).thenReturn(true);
            bounds.when(() -> InstallationBounds.isCorrectProvince(eq(port), any())).thenReturn(false);

            assertEquals(
                CanRegisterResult.WRONG_PROVINCE,
                service.canRegister(port, vehicle));
        }
    }

    @Test
    void canRegister_allChecksPass_returnsOk() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "ironclad");

        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class)) {
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(port), any())).thenReturn(true);
            bounds.when(() -> InstallationBounds.isCorrectProvince(eq(port), any())).thenReturn(true);

            assertEquals(
                CanRegisterResult.OK,
                service.canRegister(port, vehicle));
        }
    }

    @Test
    void canRegister_landVehicleAtFort_returnsOk() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "horse_cart");

        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class)) {
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(fort), any())).thenReturn(true);
            bounds.when(() -> InstallationBounds.isCorrectProvince(eq(fort), any())).thenReturn(true);

            assertEquals(
                CanRegisterResult.OK,
                service.canRegister(fort, vehicle));
        }
    }

    @Test
    void canRegister_destinationLocked_returnsRepairLocked() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "ironclad");

        try (MockedStatic<VehicleInstallationLockService> lock = mockStatic(VehicleInstallationLockService.class)) {
            lock.when(() -> VehicleInstallationLockService.isVehicleLocked(eq("port-1"), any()))
                    .thenReturn(true);

            assertEquals(
                    CanRegisterResult.REPAIR_LOCKED,
                    service.canRegister(port, vehicle));
        }
    }

    @Test
    void canRegister_landVehicleAtFort_noCapacity() {
        registry.register(new PlayerVehicleRecord(
            UUID.randomUUID(),
            "berthed-1",
            "horse_cart",
            OwnershipMode.INSTALLATION,
            fort.getId()));
        registry.register(new PlayerVehicleRecord(
            UUID.randomUUID(),
            "berthed-2",
            "horse_cart",
            OwnershipMode.INSTALLATION,
            fort.getId()));

        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "horse_cart");

        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class)) {
            bounds.when(() -> InstallationBounds.isWithinRadius(eq(fort), any())).thenReturn(true);
            bounds.when(() -> InstallationBounds.isCorrectProvince(eq(fort), any())).thenReturn(true);

            assertEquals(
                CanRegisterResult.NO_CAPACITY,
                service.canRegister(fort, vehicle));
        }
    }

    @Test
    void canRegister_landVehicleAtPort_unsupportedCategory() {
        VehicleBerthTarget vehicle = ownedTarget("vehicle-1", "horse_cart");

        assertEquals(
            CanRegisterResult.UNSUPPORTED_CATEGORY,
            service.canRegister(port, vehicle));
    }

    @Test
    void register_updatesRegistryOwnerAndPersists() {
        UUID playerUuid = UUID.randomUUID();
        OwnerData ownerData = ownedData();
        VehicleBerthTarget vehicle = berthTarget("vehicle-1", "ironclad", locationAt(0, 64, 0), ownerData);

        Faction faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn("Alice");

        SimpleFactions plugin = mock(SimpleFactions.class);
        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);

            service.register(port, vehicle, faction, playerUuid);

            PlayerVehicleRecord updated = registry.getByVehicleUuid("vehicle-1").orElseThrow();
            assertEquals(OwnershipMode.INSTALLATION, updated.getMode());
            assertEquals("port-1", updated.getInstallationId());
            assertEquals(playerUuid, updated.getPlayerUuid());
            assertEquals("player_Alice", ownerData.getOwner());
            verify(plugin).saveVehicleRegistry();
        }
    }

    private static OwnerData unowned() {
        return new OwnerData();
    }

    private static OwnerData ownedData() {
        OwnerData ownerData = new OwnerData();
        ownerData.setOwner("player_Alice");
        return ownerData;
    }

    private static VehicleBerthTarget ownedTarget(String uuid, String typeId) {
        return berthTarget(uuid, typeId, locationAt(0, 64, 0), ownedData());
    }

    private static VehicleBerthTarget berthTarget(
            String uuid, String typeId, Location location, OwnerData ownerData) {
        return new VehicleBerthTarget() {
            @Override
            public String getVehicleUuid() {
                return uuid;
            }

            @Override
            public String getVehicleTypeId() {
                return typeId;
            }

            @Override
            public Location getLocation() {
                return location;
            }

            @Override
            public OwnerData getOwnerData() {
                return ownerData;
            }
        };
    }

    private static Location locationAt(int x, int y, int z) {
        World world = mock(World.class);
        return new Location(world, x, y, z);
    }

    private Path writeInstallationsFixture() throws IOException {
        Path installationsYaml = tempDir.resolve("installations.yml");
        Files.writeString(installationsYaml, """
            consent-proximity-blocks: 20
            transfer-request-timeout-seconds: 60

            fort:
              radius: 80
              daily-upkeep: 50
              construction-time: 10
              slots:
                static_emplacements: 8
                land_vehicles: 2
            port:
              radius: 80
              daily-upkeep: 20
              construction-time: 10
              slots:
                ships: 8
            airport:
              radius: 80
              daily-upkeep: 35
              construction-time: 10
              slots:
                aircraft: 10
            """);
        return installationsYaml;
    }

    private void writeVehiclesFixture() throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              land_vehicles:
                horse_cart:
                  upkeep: 5
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }
}
