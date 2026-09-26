package net.tfminecraft.simplefactions.vehicles.berth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Outcome;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Status;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard.CanBuildResult;
import net.tfminecraft.simplefactions.vehicles.registry.FakeOwnedInventory;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

class FactionVehicleReleaseServiceTest {
    private Path tempDir;
    private PlayerVehicleRegistry registry;
    private List<String> assigned;
    private FactionVehicleReleaseService service;
    private Faction faction;
    private Faction enemy;
    private InstallationHandler handler;
    private List<War> savedWars;
    private List<Battle> savedBattles;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sf-vehicle-release-");
        Path vehiclesYaml = tempDir.resolve("vehicles.yml");
        Files.writeString(vehiclesYaml, """
            personal-slot-limit: 1
            default-upkeep: 1
            default-per-person: 2

            categories:
              land_vehicles:
                coal_car:
                  size: 1
                  upkeep: 1
                  per-person: 2
              ships:
                ironclad:
                  size: 1
                  upkeep: 1
                  per-person: 1
              static_emplacements: {}
              aircraft: {}
            """);
        VehiclesConfigLoader.load(vehiclesYaml.toFile());
        VehicleOwnershipQueries.setSourceForTests(new FakeOwnedInventory());

        registry = new PlayerVehicleRegistry();
        assigned = new ArrayList<>();
        service = new FactionVehicleReleaseService(registry, (uuid, name) -> {
            assigned.add(uuid + ":" + name);
            return true;
        });

        faction = mock(Faction.class);
        enemy = mock(Faction.class);
        when(faction.getId()).thenReturn("red");
        when(faction.getLeader()).thenReturn("Leader");
        when(enemy.getId()).thenReturn("blue");
        when(enemy.getLeader()).thenReturn("Other");
        handler = mock(InstallationHandler.class);
        when(faction.getInstallationHandler()).thenReturn(handler);

        savedWars = new ArrayList<>(WarManager.get());
        savedBattles = new ArrayList<>(BattleManager.get());
        WarManager.get().clear();
        BattleManager.resetForTests();
    }

    @AfterEach
    void tearDown() throws IOException {
        VehicleOwnershipQueries.setSourceForTests(null);
        BattleManager.resetForTests();
        WarManager.get().clear();
        WarManager.get().addAll(savedWars);
        for (Battle battle : savedBattles) {
            BattleManager.addBattle(battle);
        }
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void take_poolVehicleBecomesLeadersPersonalVehicle() {
        registry.register(pool("gun-1", "coal_car"));

        SimpleFactions plugin = savingPlugin();
        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            Outcome outcome = service.take(faction, "Leader", "gun-1");
            assertEquals(Status.OK, outcome.status());
            assertFalse(registry.getByVehicleUuid("gun-1").isPresent());
            assertEquals(List.of("gun-1:Leader"), assigned);
            verify(plugin).saveVehicleRegistry();
        }
    }

    @Test
    void take_failedSaveKeepsTheFactionVehicleAndItsOwner() {
        registry.register(pool("gun-1", "coal_car"));
        FactionVehicleReleaseService failing = new FactionVehicleReleaseService(
                registry,
                (uuid, name) -> {
                    assigned.add(uuid + ":" + name);
                    return true;
                },
                () -> false);

        Outcome outcome = failing.take(faction, "Leader", "gun-1");

        assertEquals(Status.SAVE_FAILED, outcome.status());
        assertTrue(registry.getByVehicleUuid("gun-1").isPresent());
        assertTrue(assigned.isEmpty());
    }

    @Test
    void take_failedOwnerChangeRestoresTheFactionRecord() {
        registry.register(pool("gun-1", "coal_car"));
        FactionVehicleReleaseService unassignable =
                new FactionVehicleReleaseService(registry, (uuid, name) -> false, () -> true);

        Outcome outcome = unassignable.take(faction, "Leader", "gun-1");

        assertEquals(Status.OWNERSHIP_UNAVAILABLE, outcome.status());
        assertEquals(OwnershipMode.POOL, registry.getByVehicleUuid("gun-1").orElseThrow().getMode());
    }

    @Test
    void take_failedRollbackSaveReportsSaveFailed() {
        registry.register(pool("gun-1", "coal_car"));
        java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
        FactionVehicleReleaseService service = new FactionVehicleReleaseService(
                registry, (uuid, name) -> false, () -> saves.incrementAndGet() == 1);

        Outcome outcome = service.take(faction, "Leader", "gun-1");

        assertEquals(Status.SAVE_FAILED, outcome.status());
        assertEquals(2, saves.get());
        assertTrue(registry.getByVehicleUuid("gun-1").isPresent());
    }

    @Test
    void take_installationVehicleBecomesLeadersPersonalVehicle() {
        when(handler.getById("port-1")).thenReturn(
                new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L));
        registry.register(berthed("ship-1", "ironclad", "port-1"));

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(savingPlugin());
            Outcome outcome = service.take(faction, "Leader", "ship-1");
            assertEquals(Status.OK, outcome.status());
            assertEquals(List.of("ship-1:Leader"), assigned);
            assertFalse(registry.isBerthed("ship-1"));
        }
    }

    @Test
    void take_refusesWhenPersonalLimitIsFull() {
        registry.register(pool("car-1", "coal_car"));
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("owned", "ironclad", "player_Leader"));

        Outcome outcome = service.take(faction, "Leader", "car-1");

        assertEquals(Status.NO_PERSONAL_ROOM, outcome.status());
        assertEquals(CanBuildResult.TOTAL_LIMIT, outcome.slotFailure());
        assertTrue(registry.getByVehicleUuid("car-1").isPresent());
        assertTrue(assigned.isEmpty());
        assertTrue(FactionVehicleReleaseMessages.forTake(outcome).contains("personal vehicle limit"));
    }

    @Test
    void take_refusesPerTypeLimit() {
        when(handler.getById("port-1")).thenReturn(
                new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L));
        registry.register(berthed("ship-1", "ironclad", "port-1"));
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("owned", "ironclad", "player_Leader"));

        Outcome outcome = service.take(faction, "Leader", "ship-1");

        assertEquals(Status.NO_PERSONAL_ROOM, outcome.status());
        assertEquals(CanBuildResult.PER_TYPE_LIMIT, outcome.slotFailure());
    }

    @Test
    void take_refusesDuringStartedCampaignBattle() {
        registry.register(pool("car-1", "coal_car"));
        startBattle(true, false);

        Outcome outcome = service.take(faction, "Leader", "car-1");

        assertEquals(Status.IN_BATTLE, outcome.status());
        assertTrue(registry.getByVehicleUuid("car-1").isPresent());
        assertEquals(FactionCampaignBattleLock.BLOCKED, FactionVehicleReleaseMessages.forTake(outcome));
    }

    @Test
    void take_refusesDuringStartedCampaignRaid() {
        registry.register(pool("car-1", "coal_car"));
        startBattle(true, true);

        assertEquals(Status.IN_BATTLE, service.take(faction, "Leader", "car-1").status());
    }

    @Test
    void take_allowsScheduledBattleThatHasNotStarted() {
        registry.register(pool("car-1", "coal_car"));
        startBattle(false, false);

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(savingPlugin());
            assertEquals(Status.OK, service.take(faction, "Leader", "car-1").status());
        }
    }

    @Test
    void take_refusesInstallationLock() {
        when(handler.getById("port-1")).thenReturn(
                new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L));
        registry.register(berthed("ship-1", "ironclad", "port-1"));

        try (MockedStatic<VehicleInstallationLockService> lock = mockStatic(VehicleInstallationLockService.class)) {
            lock.when(() -> VehicleInstallationLockService.isVehicleLocked(org.mockito.ArgumentMatchers.eq("port-1"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(true);
            Outcome outcome = service.take(faction, "Leader", "ship-1");
            assertEquals(Status.INSTALLATION_LOCKED, outcome.status());
            assertEquals(
                    VehicleInstallationLockService.UNBERTH_BLOCKED,
                    FactionVehicleReleaseMessages.forTake(outcome));
            assertTrue(registry.isBerthed("ship-1"));
        }
    }

    @Test
    void take_refusesAnotherFactionsPoolVehicle() {
        registry.register(new PlayerVehicleRecord(
                UUID.randomUUID(), "car-1", "coal_car", OwnershipMode.POOL, null, "blue"));

        assertEquals(Status.NOT_FACTION_VEHICLE, service.take(faction, "Leader", "car-1").status());
    }

    @Test
    void give_assignsRecipientWhenTheirLimitsAllow() {
        registry.register(pool("car-1", "coal_car"));

        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(savingPlugin());
            Outcome outcome = service.give(faction, "Leader", "Bob", "car-1");
            assertEquals(Status.OK, outcome.status());
            assertEquals(List.of("car-1:Bob"), assigned);
            assertFalse(registry.getByVehicleUuid("car-1").isPresent());
        }
    }

    @Test
    void give_refusesWhenRecipientHasNoRoom() {
        registry.register(pool("car-1", "coal_car"));
        VehicleOwnershipQueries.setSourceForTests(
                new FakeOwnedInventory().add("owned", "ironclad", "player_Bob"));

        Outcome outcome = service.evaluateGive(faction, "Leader", "Bob", "car-1");

        assertEquals(Status.NO_PERSONAL_ROOM, outcome.status());
        assertTrue(registry.getByVehicleUuid("car-1").isPresent());
        assertTrue(FactionVehicleReleaseMessages.forGive(outcome, "Bob").contains("Bob"));
    }

    @Test
    void give_refusesInstallationLockWithoutReleasing() {
        when(handler.getById("port-1")).thenReturn(
                new Installation("port-1", "Harbour", InstallationKind.PORT, 1, 0, 0, 0L));
        registry.register(berthed("ship-1", "ironclad", "port-1"));

        try (MockedStatic<VehicleInstallationLockService> lock = mockStatic(VehicleInstallationLockService.class)) {
            lock.when(() -> VehicleInstallationLockService.isVehicleLocked(org.mockito.ArgumentMatchers.eq("port-1"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(true);
            Outcome outcome = service.evaluateGive(faction, "Leader", "Bob", "ship-1");
            assertEquals(Status.INSTALLATION_LOCKED, outcome.status());
            assertTrue(FactionVehicleReleaseMessages.forGive(outcome, "Bob").contains("give"));
        }
    }

    private void startBattle(boolean started, boolean raid) {
        War war = new War(41, faction, enemy);
        WarManager.get().add(war);
        Battle battle = new Battle(raid ? "cr_battle_41" : "campaign_w41");
        battle.setWarId(war.getId());
        battle.setStarted(started);
        battle.setCampaignRaid(raid);
        BattleManager.addBattle(battle);
    }

    private static PlayerVehicleRecord pool(String uuid, String typeId) {
        return new PlayerVehicleRecord(UUID.randomUUID(), uuid, typeId, OwnershipMode.POOL, null, "red");
    }

    private static PlayerVehicleRecord berthed(String uuid, String typeId, String installationId) {
        return new PlayerVehicleRecord(
                UUID.randomUUID(), uuid, typeId, OwnershipMode.INSTALLATION, installationId);
    }

    /** A plugin mock whose registry saves succeed. */
    private static SimpleFactions savingPlugin() {
        // A default answer rather than when(), so it is safe inside another stubbing call.
        return org.mockito.Mockito.mock(SimpleFactions.class, invocation ->
                invocation.getMethod().getReturnType() == boolean.class
                        ? Boolean.TRUE
                        : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
    }
}
