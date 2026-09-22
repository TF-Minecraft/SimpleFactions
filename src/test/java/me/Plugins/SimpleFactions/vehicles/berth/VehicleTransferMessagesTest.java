package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleInstallationLockService;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleTransferMessages;
import me.Plugins.SimpleFactions.vehicles.berth.InstallationVehicleService.CanRegisterResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationBounds;
import me.Plugins.SimpleFactions.installation.InstallationKind;

class VehicleTransferMessagesTest {
    private Path tempDir;
    private Installation port;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-transfer-messages-");
        writeVehiclesFixture();
        InstallationConfigLoader.load(writeInstallationsFixture().toFile());
        port = new Installation("port-1", "Harbour", InstallationKind.PORT, 42, 0, 0, 0L);
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
    void notInRegistry_usesLockedCopy() {
        String message = VehicleTransferMessages.forResult(
                CanRegisterResult.NOT_IN_REGISTRY,
                port,
                null,
                null);
        assertEquals("§cThis vehicle must be owned by a player before it can be berthed.", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void commandArmed_includesInstallationName() {
        String message = VehicleTransferMessages.commandArmed(port);
        assertEquals("§aRight-click the vehicle to transfer it to Harbour.", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void berthSuccess_includesInstallationName() {
        assertEquals(
                "§aVehicle berthed at Harbour.",
                VehicleTransferMessages.berthSuccess(port));
    }

    @Test
    void unsupportedCategory_includesCategoryId() {
        String message = VehicleTransferMessages.forResult(
                CanRegisterResult.UNSUPPORTED_CATEGORY,
                port,
                null,
                "ironclad");
        assertEquals("§cThis installation does not support ships vehicles.", message);
    }

    @Test
    void alreadyBerthed_usesLockedCopy() {
        String message = VehicleTransferMessages.forResult(
                CanRegisterResult.ALREADY_BERTHED,
                port,
                null,
                null);
        assertEquals("§cThis vehicle is already berthed at an installation.", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void repairLocked_usesLockedCopy() {
        String message = VehicleTransferMessages.forResult(
                CanRegisterResult.REPAIR_LOCKED,
                port,
                null,
                null);
        assertEquals(VehicleInstallationLockService.BERTH_BLOCKED, message);
        assertFalse(message.contains("—"));
    }

    @Test
    void unknownType_usesLockedCopy() {
        String message = VehicleTransferMessages.forResult(
                CanRegisterResult.UNKNOWN_TYPE,
                port,
                null,
                null);
        assertEquals("§cThis vehicle is not registered for faction upkeep.", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void outOfRadius_usesLockedCopy() {
        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class, CALLS_REAL_METHODS)) {
            bounds.when(() -> InstallationBounds.horizontalDistanceBlocks(0, 0, null))
                    .thenReturn(100.0);

            String message = VehicleTransferMessages.forResult(
                    CanRegisterResult.OUT_OF_RADIUS,
                    port,
                    null,
                    null);
            assertEquals(
                    "§cVehicle must be within 80 blocks of Harbour (currently 100.0).",
                    message);
            assertFalse(message.contains("—"));
        }
    }

    @Test
    void wrongProvince_usesLockedCopy() {
        try (MockedStatic<InstallationBounds> bounds = mockStatic(InstallationBounds.class)) {
            bounds.when(() -> InstallationBounds.provinceAt(null)).thenReturn(99);

            String message = VehicleTransferMessages.forResult(
                    CanRegisterResult.WRONG_PROVINCE,
                    port,
                    null,
                    null);
            assertEquals("§cVehicle must be in province 42 (currently 99).", message);
            assertFalse(message.contains("—"));
        }
    }

    @Test
    void noCapacity_usesLockedCopy() {
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        for (int i = 0; i < 8; i++) {
            registry.register(new PlayerVehicleRecord(
                    UUID.randomUUID(),
                    "berthed-" + i,
                    "ironclad",
                    OwnershipMode.INSTALLATION,
                    port.getId()));
        }

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);

            String message = VehicleTransferMessages.forResult(
                    CanRegisterResult.NO_CAPACITY,
                    port,
                    null,
                    "ironclad");
            assertEquals("§cHarbour has no space for ships (8/8 used).", message);
            assertFalse(message.contains("—"));
        }
    }

    @Test
    void notLeader_usesLockedCopy() {
        String message = VehicleTransferMessages.notLeader();
        assertEquals("§cYou need to be a faction leader to transfer vehicles.", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void unknownInstallation_usesLockedCopy() {
        String message = VehicleTransferMessages.unknownInstallation();
        assertEquals("§cUnknown installation id.", message);
        assertFalse(message.contains("—"));
    }

    @Test
    void noPendingSession_usesLockedCopy() {
        String message = VehicleTransferMessages.noPendingSession();
        assertEquals(
                "§cYou are not transferring a vehicle. Use /faction vehicle transfer <id>.",
                message);
        assertFalse(message.contains("—"));
    }

    @Test
    void consentPrompt_usesLockedCopy() {
        String message = VehicleTransferMessages.consentPrompt("Alice", "ironclad", "Harbour");
        assertEquals(
                "§eAlice wants to berth your ironclad at Harbour. It will become a faction vehicle. §7/faction accept",
                message);
        assertFalse(message.contains("—"));
    }

    @Test
    void ownerOffline_usesLockedCopy() {
        assertEquals(
                "§cThe vehicle owner must be online to transfer this vehicle.",
                VehicleTransferMessages.ownerOffline());
    }

    @Test
    void ownerTooFar_usesLockedCopy() {
        assertEquals(
                "§cThe vehicle owner must be within 20 blocks of the vehicle.",
                VehicleTransferMessages.ownerTooFar(20));
    }

    @Test
    void consentExpired_usesLockedCopy() {
        assertEquals(
                "§cVehicle transfer request expired or was cancelled.",
                VehicleTransferMessages.consentExpired());
    }

    @Test
    void consentSent_usesLockedCopy() {
        assertEquals(
                "§aSent vehicle transfer request to Bob.",
                VehicleTransferMessages.consentSent("Bob"));
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
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
    }
}
