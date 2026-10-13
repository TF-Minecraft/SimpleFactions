package net.tfminecraft.simplefactions.vehicles.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferMessages;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService.CanAddResult;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.data.OwnerData;

class FactionVehiclePoolServiceTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final String ALICE_TAG = "char_" + ALICE;
    private net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi api;

    @Test
    void aFailedLeaderStampPutsThePersonalRowBack() {
        PlayerVehicleRecord personal = new PlayerVehicleRecord(UUID.randomUUID(), "v1", "coal_car",
                OwnershipMode.PERSONAL, null);
        registry.register(personal);
        api.forced = net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result.SAVE_FAILED;
        net.tfminecraft.simplefactions.SimpleFactions plugin = mock(net.tfminecraft.simplefactions.SimpleFactions.class);
        java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.saveVehicleRegistry()).thenReturn(true, false);
        try (var sf = org.mockito.Mockito.mockStatic(net.tfminecraft.simplefactions.SimpleFactions.class)) {
            sf.when(net.tfminecraft.simplefactions.SimpleFactions::getInstance).thenReturn(plugin);
            assertEquals(false, service.tryRegister(faction, target("v1", "coal_car", owned("char_x")), UUID.randomUUID()));
        }
        assertEquals(personal, registry.getByVehicleUuid("v1").orElseThrow());
        org.mockito.Mockito.verify(logger).severe(org.mockito.ArgumentMatchers.contains("pool stamp failed"));

        registry.unregister("v1");
        assertEquals(false, service.tryRegister(faction, target("v1", "coal_car", owned("char_x")), UUID.randomUUID()));
        assertEquals(java.util.Optional.empty(), registry.getByVehicleUuid("v1"));
    }
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private FactionVehiclePoolService service;
    private Faction faction;
    private Regiment artillery;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-faction-pool-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
                personal-slot-limit: 3
                default-upkeep: 4
                categories:
                  land_vehicles:
                    horse_cart:
                      upkeep: 5
                      size: 1
                  train:
                    coal_car:
                      upkeep: 1
                      size: 1
                      ignore-limit: true
                  artillery:
                    field_artillery:
                      upkeep: 4
                      size: 1
                  ships:
                    ironclad:
                      upkeep: 20
                      size: 1
                  static_emplacements: {}
                  aircraft: {}
                """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
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
        InstallationConfigLoader.load(installationsYaml.toFile());

        registry = new PlayerVehicleRegistry();
        service = new FactionVehiclePoolService(registry, new InstallationVehicleOwnerSync(registry));
        artillery = mock(Regiment.class);
        when(artillery.getCurrentSlots()).thenReturn(1);
        Military military = mock(Military.class);
        when(military.getRegiment("artillery")).thenReturn(artillery);
        faction = mock(Faction.class);
        when(faction.getId()).thenReturn("red");
        when(faction.getLeader()).thenReturn(ALICE.toString());
        api = net.tfminecraft.simplefactions.vehicles.owners.FakeOwnerApi.install()
                .live("gun-1", "field_artillery", ALICE_TAG).live("v1", "coal_car", "char_" + UUID.randomUUID());
        when(faction.getMilitary()).thenReturn(military);
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
    void poolTargetWordIsPool() {
        assertTrue(FactionVehiclePoolService.isPoolTarget("pool"));
        assertTrue(FactionVehiclePoolService.isPoolTarget("POOL"));
    }

    @Test
    void nonBerthableTrainCanJoinThePool() {
        assertEquals(CanAddResult.OK, service.canAdd(faction, target("v1", "coal_car", ALICE_TAG)));
    }

    @Test
    void berthableTypeIsRefused() {
        assertEquals(CanAddResult.MUST_BERTH, service.canAdd(faction, target("v1", "horse_cart", ALICE_TAG)));
        assertEquals(CanAddResult.MUST_BERTH, service.canAdd(faction, target("v2", "ironclad", ALICE_TAG)));
        assertEquals(
                "§cThis vehicle belongs at an installation, not in the faction pool.",
                VehicleTransferMessages.forPoolResult(CanAddResult.MUST_BERTH, "ironclad", faction));
    }

    @Test
    void artilleryIsCappedByRegimentSlots() {
        when(artillery.getCurrentSlots()).thenReturn(0);
        assertEquals(
                CanAddResult.NO_ARTILLERY_CAPACITY,
                service.canAdd(faction, target("gun-1", "field_artillery", ALICE_TAG)));

        when(artillery.getCurrentSlots()).thenReturn(1);
        assertEquals(CanAddResult.OK, service.canAdd(faction, target("gun-1", "field_artillery", ALICE_TAG)));

        OwnerData owner = owned(ALICE_TAG);
        service.register(faction, target("gun-1", "field_artillery", owner), UUID.randomUUID());
        assertEquals(
                CanAddResult.NO_ARTILLERY_CAPACITY,
                service.canAdd(faction, target("gun-2", "field_artillery", ALICE_TAG)));
        assertEquals(1, registry.countPoolCategory("red", "artillery"));
    }

    @Test
    void registerAssignsPoolOwnershipToTheFactionLeader() {
        OwnerData owner = owned("char_" + UUID.randomUUID());
        UUID original = UUID.randomUUID();
        service.register(faction, target("v1", "coal_car", owner), original);

        PlayerVehicleRecord record = registry.getByVehicleUuid("v1").orElseThrow();
        assertEquals(OwnershipMode.POOL, record.getMode());
        assertEquals("red", record.getFactionId());
        assertEquals(original, record.getPlayerUuid());
        assertEquals(ALICE_TAG, api.owner("v1"));
        assertEquals(java.util.List.of("v1 " + ALICE_TAG + " TRANSFER"), api.writes);
        assertEquals(1.0, FactionVehiclePoolService.dailyUpkeep(registry, "red"));
    }

    @Test
    void refusesVehiclesAlreadyInThePoolOrAtAnInstallation() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "pooled", "coal_car", OwnershipMode.POOL, null, "red"));
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "berthed", "ironclad", OwnershipMode.INSTALLATION, "port-1"));

        assertEquals(CanAddResult.ALREADY_IN_POOL, service.canAdd(faction, target("pooled", "coal_car", ALICE_TAG)));
        assertEquals(CanAddResult.ALREADY_BERTHED, service.canAdd(faction, target("berthed", "ironclad", ALICE_TAG)));
        assertEquals(CanAddResult.NOT_OWNED, service.canAdd(faction, target("free", "coal_car", "none")));
        assertEquals(CanAddResult.UNKNOWN_TYPE, service.canAdd(faction, target("odd", "not_a_vehicle", ALICE_TAG)));
    }

    @Test
    void loreListsTypeUpkeepArtilleryAndUnpaidMaintenance() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "gun-1", "field_artillery", OwnershipMode.POOL, null, "red"));
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "train-1", "coal_car", OwnershipMode.POOL, null, "red"));

        var lines = FactionVehiclePoolLore.lines(registry.getPoolVehicles("red"), 2, Set.of("gun-1"));
        assertTrue(lines.get(0).contains("Artillery") && lines.get(0).contains("1/2"));
        assertTrue(lines.stream().anyMatch(line -> line.contains("field_artillery") && line.contains("unpaid")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("coal_car") && line.contains("1.00d/day")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Unpaid: 1 of 2")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("coal_car") && line.contains("#train-")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("/faction vehicle take")));
    }

    private static FactionVehiclePoolService.PoolTarget target(String uuid, String typeId, String owner) {
        return target(uuid, typeId, owned(owner));
    }

    private static FactionVehiclePoolService.PoolTarget target(String uuid, String typeId, OwnerData ownerData) {
        return new FactionVehiclePoolService.PoolTarget() {
            @Override
            public String getVehicleUuid() {
                return uuid;
            }

            @Override
            public String getVehicleTypeId() {
                return typeId;
            }

            @Override
            public OwnerData getOwnerData() {
                return ownerData;
            }
        };
    }

    private static OwnerData owned(String owner) {
        OwnerData data = new OwnerData();
        data.setOwner(owner);
        return data;
    }
}
