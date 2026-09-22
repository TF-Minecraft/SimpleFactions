package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;

class AttendanceServiceTest {
    private ContractFixture fixture;
    private Faction enemy;
    private UUID sigrun;
    private UUID bjorn;
    private MercenaryContract contract;

    @BeforeEach
    void setUp() {
        AttendanceService.reset();
        fixture = ContractFixture.formed(2);
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        enemy = ContractFixture.faction("enemy_realm");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(enemy.getGuildHandler()).thenReturn(empty);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(enemy);
        fixture.company.kick("Soldier0");
        fixture.company.enlist("Sigrun");
        sigrun = UUID.randomUUID();
        bjorn = UUID.randomUUID();
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrun
                : "Bjorn".equalsIgnoreCase(name) ? bjorn : null);
        contract = fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        assertTrue(contract.activate());
    }

    @AfterEach
    void tearDown() {
        AttendanceService.reset();
        MercenaryEngagements.setUuidLookup(null);
        ContractFixture.tearDown();
    }

    private War war() {
        return new War(4, fixture.hirer, enemy);
    }

    private void end(War war, Set<UUID> start, Set<UUID> end) {
        AttendanceService.onStarted("b1", start);
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(4)).thenReturn(war);
            AttendanceService.onEnded("b1", 4, end);
        }
    }

    @Test
    void presentInBothSnapshotsPasses() {
        fixture.company.enlist("Bjorn");
        end(war(), Set.of(sigrun, bjorn), Set.of(sigrun, bjorn));
        AttendanceService.Result result = AttendanceService.result(contract, "b1");
        assertEquals(2, result.attended());
        assertEquals(0, result.absent());
        assertTrue(contract.hasCleanAttendance());
    }

    @Test
    void missingFromTheStartSnapshotFails() {
        end(war(), Set.of(), Set.of(sigrun));
        AttendanceService.Result result = AttendanceService.result(contract, "b1");
        assertEquals(0, result.attended());
        assertEquals(2, result.absent());
        assertFalse(contract.hasCleanAttendance());
    }

    @Test
    void missingFromTheEndSnapshotFails() {
        end(war(), Set.of(sigrun), Set.of());
        assertEquals(2, AttendanceService.result(contract, "b1").absent());
        assertFalse(contract.hasCleanAttendance());
    }

    @Test
    void eliminatedButStillRosteredPassesForThatSlot() {
        end(war(), Set.of(sigrun), Set.of(sigrun));
        AttendanceService.Result result = AttendanceService.result(contract, "b1");
        assertEquals(1, result.attended());
        assertEquals(1, result.absent());
    }

    @Test
    void aRejoinBetweenSnapshotsPasses() {
        end(war(), Set.of(sigrun), Set.of(sigrun));
        assertEquals(1, AttendanceService.result(contract, "b1").attended());
    }

    @Test
    void anUnfilledSlotIsAFailureNotMissingData() {
        end(war(), Set.of(sigrun), Set.of(sigrun));
        AttendanceService.Result result = AttendanceService.result(contract, "b1");
        assertEquals(1, result.attended());
        assertEquals(1, result.absent());
        assertFalse(result.snapshotMissing());
        assertFalse(contract.hasCleanAttendance());
    }

    @Test
    void aLostStartSnapshotRecordsNoAbsence() {
        War war = war();
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(4)).thenReturn(war);
            AttendanceService.onEnded("b1", 4, Set.of());
        }
        AttendanceService.Result result = AttendanceService.result(contract, "b1");
        assertTrue(result.snapshotMissing());
        assertEquals(0, result.absent());
        assertTrue(contract.hasCleanAttendance());
    }
}
