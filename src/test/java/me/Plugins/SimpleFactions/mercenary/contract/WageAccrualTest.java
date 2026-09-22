package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.income.Ledger;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.War.core.War;

/**
 * Two wages, one ledger line. The day share follows the contract, the battle
 * share follows attendance, and the peacetime wage follows neither.
 */
class WageAccrualTest {

    private ContractFixture fixture;
    private Faction enemy;
    private UUID sigrun;
    private UUID bjorn;

    @BeforeEach
    void setUp() {
        AttendanceService.reset();
        fixture = ContractFixture.formed(2);
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations()).thenReturn(new HashMap<>());

        // The hiring capital has to exist, because the pre-pass pushes its bill there.
        Guild capital = mock(Guild.class);
        when(capital.getId()).thenReturn("brume_capital");
        when(capital.isBankrupt()).thenReturn(false);
        when(capital.getLedger()).thenReturn(new Ledger(capital));
        when(fixture.hirer.getOrCreateMainGuild()).thenReturn(capital);

        enemy = ContractFixture.faction("enemy_realm");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        GuildHandler empty = mock(GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new ArrayList<>());
        when(enemy.getGuildHandler()).thenReturn(empty);
        FactionManager.factions.add(enemy);

        fixture.company.kick("Soldier0");
        fixture.company.enlist("Sigrun");
        fixture.company.enlist("Bjorn");
        sigrun = UUID.randomUUID();
        bjorn = UUID.randomUUID();
        MercenaryEngagements.setUuidLookup(name -> "Sigrun".equalsIgnoreCase(name) ? sigrun
                : "Bjorn".equalsIgnoreCase(name) ? bjorn : null);
        fixture.company.getWageSettings().setActivePercent(20);
    }

    @AfterEach
    void tearDown() {
        AttendanceService.reset();
        MercenaryEngagements.setUuidLookup(null);
        ContractFixture.tearDown();
    }

    @Test
    void aDayUnderContractPaysEachCoveredSlotItsDayShare() {
        activeContract();

        ContractAccrualService.accrueDailyAndPush();

        assertEquals(2.0, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
        assertEquals(2.0, fixture.company.getPendingWages().get("Bjorn"), 1e-9);
    }

    @Test
    void noContractPaysNoActiveWage() {
        ContractAccrualService.accrueDailyAndPush();
        assertTrue(fixture.company.getPendingWages().isEmpty(), "an idle company owes nothing");
    }

    @Test
    void anOfferThatWasNeverSignedPaysNothing() {
        fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        ContractAccrualService.accrueDailyAndPush();
        assertTrue(fixture.company.getPendingWages().isEmpty());
    }

    @Test
    void aBattlePaysTheBattleShareOnlyToWhoeverShowedUp() {
        MercenaryContract contract = activeContract();

        endBattle("b1", Set.of(sigrun, bjorn), Set.of(sigrun));

        // Sigrun was in both snapshots; Bjorn left before it resolved.
        assertEquals(10.0, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
        assertEquals(null, fixture.company.getPendingWages().get("Bjorn"));
        assertEquals(1, AttendanceService.result(contract, "b1").absent());
    }

    @Test
    void aFailedAttendancePaysTheDayShareButNotTheBattleShare() {
        activeContract();

        endBattle("b1", Set.of(), Set.of());
        assertTrue(fixture.company.getPendingWages().isEmpty(), "nobody fought, nobody is paid for it");

        ContractAccrualService.accrueDailyAndPush();
        assertEquals(2.0, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
    }

    @Test
    void aReplayedBattleEndAccruesNothingExtra() {
        activeContract();
        endBattle("b1", Set.of(sigrun), Set.of(sigrun));
        double once = fixture.company.getPendingWages().get("Sigrun");

        War war = new War(4, fixture.hirer, enemy);
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(4)).thenReturn(war);
            ContractAccrualService.onBattleEnded("b1", 4);
        }

        assertEquals(once, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
    }

    @Test
    void anOverrideIsPaidInsteadOfTheBase() {
        fixture.company.getWageSettings().setActiveOverride("Sigrun", 50.0);
        activeContract();

        ContractAccrualService.accrueDailyAndPush();

        assertEquals(5.0, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
        assertEquals(2.0, fixture.company.getPendingWages().get("Bjorn"), 1e-9);
    }

    @Test
    void thePeacetimeWageIsPaidWithNoContractAtAll() {
        fixture.company.getWageSettings().setPeacetimePerDay(4.0);

        ContractAccrualService.accrueDailyAndPush();

        assertEquals(4.0, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
        assertEquals(4.0, fixture.company.getPendingWages().get("Bjorn"), 1e-9);
    }

    @Test
    void aPeacetimeOverrideRetainsOneSoldierBetterThanTheRest() {
        fixture.company.getWageSettings().setPeacetimePerDay(4.0);
        fixture.company.getWageSettings().setPeacetimeOverride("Sigrun", 12.0);

        ContractAccrualService.accrueDailyAndPush();

        assertEquals(12.0, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
        assertEquals(4.0, fixture.company.getPendingWages().get("Bjorn"), 1e-9);
    }

    @Test
    void bothWagesLandOnTheSamePlayerAndTheSameLedgerLine() {
        fixture.company.getWageSettings().setPeacetimePerDay(4.0);
        activeContract();

        ContractAccrualService.accrueDailyAndPush();

        assertEquals(6.0, fixture.company.getPendingWages().get("Sigrun"), 1e-9);
    }

    @Test
    void aZeroPeacetimeWagePaysNothing() {
        ContractAccrualService.accrueDailyAndPush();
        assertTrue(fixture.company.getPendingWages().isEmpty());
    }

    @Test
    void thePeacetimeWageCountsTowardTheDailyBurn() {
        fixture.company.getWageSettings().setPeacetimePerDay(4.0);

        assertEquals(8.0, fixture.company.getWageUpkeep(), 1e-9);
        assertEquals(
                fixture.company.getSlotUpkeep()
                        + fixture.company.getUpgradeUpkeep()
                        + fixture.company.getWageUpkeep(),
                fixture.company.getDailyBurn(),
                1e-9);
    }

    @Test
    void projectedBurnMatchesWhatADayActuallyAccrues() {
        activeContract();
        double projected = fixture.company.getWageUpkeep();

        ContractAccrualService.accrueDailyAndPush();
        double accrued = 0;
        for (double amount : fixture.company.getPendingWages().values()) {
            accrued += amount;
        }

        assertEquals(projected, accrued, 1e-9);
    }

    private MercenaryContract activeContract() {
        MercenaryContract contract = fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        assertTrue(contract.activate());
        return contract;
    }

    private void endBattle(String battleId, Set<UUID> start, Set<UUID> end) {
        War war = new War(4, fixture.hirer, enemy);
        AttendanceService.onStarted(battleId, start);
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(() -> WarManager.getById(4)).thenReturn(war);
            AttendanceService.onEnded(battleId, 4, end);
            ContractAccrualService.onBattleEnded(battleId, 4);
        }
    }
}
