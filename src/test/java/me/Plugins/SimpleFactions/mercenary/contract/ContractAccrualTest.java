package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import me.Plugins.SimpleFactions.Database.MercenaryCompanyData;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

class ContractAccrualTest {
    private ContractFixture fixture;
    private Faction enemy;
    private MercenaryContract contract;
    private UUID sigrun;

    @BeforeEach
    void setUp() {
        AttendanceService.reset();
        fixture = ContractFixture.formed(2);
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        enemy = ContractFixture.faction("accrual_enemy");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(enemy.getGuildHandler()).thenReturn(empty);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(enemy);
        fixture.company.kick("Soldier0");
        fixture.company.enlist("Sigrun");
        sigrun = UUID.randomUUID();
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrun : null);
        contract = fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());
        assertTrue(contract.activate());
    }

    @AfterEach
    void tearDown() {
        AttendanceService.reset();
        MercenaryEngagements.setUuidLookup(null);
        ContractFixture.tearDown();
    }

    private War war() {
        return new War(6, fixture.hirer, enemy);
    }

    @Test
    void aStartedBattleAccruesOnce() {
        War war = war();
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(6)).thenReturn(war);
            ContractAccrualService.onBattleStarted("b1", 6);
            ContractAccrualService.onBattleStarted("b1", 6);
        }
        assertEquals(50.0, contract.getAccruedToCompany());
        assertEquals(0.0, contract.getAccruedToHirer());
        assertEquals(Set.of("b1"), contract.getBattleIdsCharged());
    }

    @Test
    void aCancelledBattleAccruesNothing() {
        assertEquals(0.0, contract.getAccruedToCompany());
        ContractAccrualService.onBattleStarted("skipped", null);
        assertEquals(0.0, contract.getAccruedToCompany());
    }

    @Test
    void aBattleDayAccruesBothPrices() {
        contract.addDayServed();
        War war = war();
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(6)).thenReturn(war);
            ContractAccrualService.onBattleStarted("b1", 6);
        }
        assertEquals(50.0, contract.getAccruedToCompany());
        assertEquals(10.0, contract.getServedDaysOwed());
    }

    @Test
    void absenceAccruesTheRefundAtTheContractRate() {
        War war = war();
        AttendanceService.onStarted("b1", Set.of());
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(6)).thenReturn(war);
            AttendanceService.onEnded("b1", 6, Set.of());
            ContractAccrualService.onBattleEnded("b1", 6);
            ContractAccrualService.onBattleEnded("b1", 6);
        }
        assertEquals(50.0, contract.getAccruedToHirer());
        assertEquals(0.0, contract.getAccruedToCompany());
        assertEquals(Set.of("b1"), contract.getBattleIdsRefunded());
    }

    @Test
    void bucketsStaySeparateThroughARoundTrip() {
        contract.accrueBattleCharge("b1", 50.0);
        contract.accrueAbsenceRefund("b1", 50.0);
        MercenaryCompanyData data = fixture.company.serialize();
        MercenaryCompany restored = new MercenaryCompany(
                fixture.host.guild, data, fixture.company.getRegiment());
        MercenaryContract copy = restored.getContractHandler().getById(contract.getId());
        assertEquals(50.0, copy.getAccruedToCompany());
        assertEquals(50.0, copy.getAccruedToHirer());
        assertEquals(Set.of("b1"), copy.getBattleIdsCharged());
        assertEquals(Set.of("b1"), copy.getBattleIdsRefunded());
    }
}
