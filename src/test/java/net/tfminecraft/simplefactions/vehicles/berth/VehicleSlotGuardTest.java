package net.tfminecraft.simplefactions.vehicles.berth;



import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.TestCharacters;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;

class VehicleSlotGuardTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-slot-guard-");
        registry = new PlayerVehicleRegistry();
    }

    /** A personal row cached against the account, as every claim writes it. */
    private void own(UUID account, String vehicle, String type) {
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), vehicle, type, OwnershipMode.PERSONAL, null, null,
                account));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        }
    }

    private void loadConfig(String yaml) throws IOException {
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, yaml);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }

    @Test
    void checkCanBuild_okWhenUnderLimits() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));
    }

    @Test
    void checkCanBuild_unknownType() throws IOException {
        loadConfig("""
            personal-slot-limit: 1

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        assertEquals(
            CanBuildResult.UNKNOWN_TYPE,
            VehicleSlotGuard.checkCanBuild(player, "unknown", registry));
    }

    @Test
    void checkCanBuild_perTypeLimit() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              aircraft:
                cloudskimmer:
                  size: 1
              ships: {}
              static_emplacements: {}
            """);

        UUID player = UUID.randomUUID();
        own(player, "vehicle-1", "cloudskimmer");

        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(player, "cloudskimmer", registry));
    }

    @Test
    void checkCanBuild_landPerPersonThree() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              land_vehicles:
                horse_cart:
                  upkeep: 5
                  size: 1
                  per-person: 3
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        own(player, "vehicle-1", "horse_cart");
        own(player, "vehicle-2", "horse_cart");

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(player, "horse_cart", registry));

        own(player, "vehicle-3", "horse_cart");
        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(player, "horse_cart", registry));
    }

    @Test
    void checkCanBuild_totalLimit() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                  per-person: 3
                gunboat:
                  upkeep: 10
                  size: 1
                  per-person: 3
                cruiser:
                  upkeep: 40
                  size: 2
                  per-person: 3
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        own(player, "vehicle-1", "ironclad");
        own(player, "vehicle-2", "gunboat");
        own(player, "vehicle-3", "cruiser");

        assertEquals(
            CanBuildResult.TOTAL_LIMIT,
            VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));
    }

    @Test
    void checkCanBuild_ignoreLimitSkipsTotal() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                gunboat:
                  upkeep: 10
                  size: 1
                cruiser:
                  upkeep: 40
                  size: 2
              train:
                coal_car:
                  upkeep: 1
                  size: 1
                  ignore-limit: true
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        own(player, "vehicle-1", "ironclad");
        own(player, "vehicle-2", "gunboat");
        own(player, "vehicle-3", "cruiser");

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(player, "coal_car", registry));
    }

    @Test
    void checkCanBuild_ignoreLimitStillPerType() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              train:
                coal_car:
                  upkeep: 1
                  size: 1
                  ignore-limit: true
                  per-person: 3
              ships: {}
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        own(player, "vehicle-1", "coal_car");
        own(player, "vehicle-2", "coal_car");
        own(player, "vehicle-3", "coal_car");

        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(player, "coal_car", registry));
    }

    @Test
    void checkCanBuild_unlimitedTotalWhenLimitZero() throws IOException {
        loadConfig("""
            personal-slot-limit: 0
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                  per-person: 3
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        own(player, "vehicle-1", "ironclad");
        own(player, "vehicle-2", "ironclad");

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));
    }

    @Test
    void checkCanBuild_perTypeCheckedBeforeTotal() throws IOException {
        loadConfig("""
            personal-slot-limit: 1
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        own(player, "vehicle-1", "ironclad");

        assertEquals(
            CanBuildResult.PER_TYPE_LIMIT,
            VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));
    }

    @Test
    void checkCanBuild_excludesBerthedFromPersonalCount() throws IOException {
        loadConfig("""
            personal-slot-limit: 1
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
                  per-person: 3
              static_emplacements: {}
              aircraft: {}
            """);

        UUID player = UUID.randomUUID();
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "vehicle-1", "ironclad", OwnershipMode.INSTALLATION, "port-1", null, player));

        assertEquals(
            CanBuildResult.OK,
            VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));
    }

    private void oneShipEach() throws IOException {
        loadConfig("""
            personal-slot-limit: 3
            default-upkeep: 4

            categories:
              ships:
                ironclad:
                  upkeep: 20
                  size: 1
              static_emplacements: {}
              aircraft: {}
            """);
    }

    @Test
    void anotherAccountsRowsDoNotCount() throws IOException {
        oneShipEach();
        UUID player = UUID.randomUUID();
        own(UUID.randomUUID(), "vehicle-1", "ironclad");

        assertEquals(CanBuildResult.OK, VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));
    }

    @Test
    void anOlderRowCountsForTheAccountThatPlaysItsCharacter() throws IOException {
        oneShipEach();
        UUID player = UUID.randomUUID();
        var characters = TestCharacters.install();
        UUID mine = Members.characterId(characters.character(player, "Kira"));
        UUID theirs = Members.characterId(characters.character(UUID.randomUUID(), "Bran"));
        registry.register(new PlayerVehicleRecord(theirs, "vehicle-2", "ironclad", OwnershipMode.PERSONAL, null));
        assertEquals(CanBuildResult.OK, VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));

        registry.register(new PlayerVehicleRecord(mine, "vehicle-1", "ironclad", OwnershipMode.PERSONAL, null));
        assertEquals(CanBuildResult.PER_TYPE_LIMIT, VehicleSlotGuard.checkCanBuild(player, "ironclad", registry));
        assertEquals(player, VehicleSlotGuard.accountOf(registry.getByVehicleUuid("vehicle-1").orElseThrow()));
    }

    @Test
    void aRowWhoseAccountIsUnknownCountsAgainstEveryClaimer() throws IOException {
        oneShipEach();
        TestCharacters.install();
        registry.register(new PlayerVehicleRecord(null, "vehicle-1", "ironclad", OwnershipMode.PERSONAL, null));
        assertEquals(CanBuildResult.PER_TYPE_LIMIT,
                VehicleSlotGuard.checkCanBuild(UUID.randomUUID(), "ironclad", registry));

        registry.unregister("vehicle-1");
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "vehicle-1", "ironclad", OwnershipMode.PERSONAL,
                null));
        assertEquals(CanBuildResult.PER_TYPE_LIMIT,
                VehicleSlotGuard.checkCanBuild(UUID.randomUUID(), "ironclad", registry));
    }

    @Test
    void aLookupErrorRefusesInsteadOfCountingNothing() throws IOException {
        oneShipEach();
        Members.Resolver failing = org.mockito.Mockito.mock(Members.Resolver.class);
        org.mockito.Mockito.when(failing.key(org.mockito.ArgumentMatchers.any(UUID.class))).thenCallRealMethod();
        org.mockito.Mockito.when(failing.info(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("off the main thread"));
        Members.setResolver(failing);
        registry.register(new PlayerVehicleRecord(UUID.randomUUID(), "vehicle-1", "ironclad", OwnershipMode.PERSONAL,
                null));

        assertEquals(CanBuildResult.UNAVAILABLE,
                VehicleSlotGuard.checkCanBuild(UUID.randomUUID(), "ironclad", registry));
        assertEquals(VehicleConstructionMessages.UNAVAILABLE,
                VehicleConstructionMessages.forResult(CanBuildResult.UNAVAILABLE, "ironclad"));
    }

    @Test
    void aPlayerIsCheckedByTheirAccount() throws IOException {
        oneShipEach();
        UUID account = UUID.randomUUID();
        org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(account);
        PlayerVehicleRegistry shared = new PlayerVehicleRegistry();
        shared.register(new PlayerVehicleRecord(UUID.randomUUID(), "vehicle-1", "ironclad", OwnershipMode.PERSONAL,
                null, null, account));
        try (var plugin = org.mockito.Mockito.mockStatic(net.tfminecraft.simplefactions.SimpleFactions.class)) {
            plugin.when(net.tfminecraft.simplefactions.SimpleFactions::getVehicleRegistry).thenReturn(shared);
            assertEquals(CanBuildResult.PER_TYPE_LIMIT, VehicleSlotGuard.checkCanBuild(player, "ironclad"));
        }
        assertEquals(CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild(null, "ironclad"));
        assertEquals(CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild(account, "ironclad", null));
        assertEquals(CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild((UUID) null, "ironclad", shared));
        assertEquals(CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild(account, "", shared));
        assertEquals(CanBuildResult.UNKNOWN_TYPE, VehicleSlotGuard.checkCanBuild(account, null, shared));
    }

    @Test
    void anUnreadableCountIsExplainedToTheOtherPlayer() {
        assertEquals(VehicleConstructionMessages.UNAVAILABLE, FactionVehicleReleaseMessages.forGive(
                new FactionVehicleReleaseService.Outcome(FactionVehicleReleaseService.Status.NO_PERSONAL_ROOM,
                        CanBuildResult.UNAVAILABLE, "ironclad"), "Bob"));
    }
}
