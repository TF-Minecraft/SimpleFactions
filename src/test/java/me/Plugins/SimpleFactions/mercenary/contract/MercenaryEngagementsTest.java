package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

class MercenaryEngagementsTest {
    private ContractFixture fixture;
    private Faction host;
    private Faction enemy;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(2);
        host = MercenaryLoyalty.hostFaction(fixture.company);
        when(host.getRelations()).thenReturn(new HashMap<>());
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        enemy = ContractFixture.faction("enemy_realm");
        when(enemy.getRelations()).thenReturn(new HashMap<>());
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(enemy.getGuildHandler()).thenReturn(empty);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(enemy);
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    private MercenaryContract activeHire() {
        MercenaryContract contract =
                fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        assertTrue(contract.activate());
        return contract;
    }

    @Test
    void anEngagementAppearsOnTheHirersSideOnly() {
        activeHire();
        War war = new War(1, fixture.hirer, enemy);

        List<MercenaryEngagements.Engagement> attackers =
                MercenaryEngagements.on(war, war.getAttackers());
        List<MercenaryEngagements.Engagement> defenders =
                MercenaryEngagements.on(war, war.getDefenders());

        assertEquals(1, attackers.size());
        assertEquals(fixture.company, attackers.get(0).company());
        assertEquals(2, attackers.get(0).promisedSlots());
        assertTrue(defenders.isEmpty());
    }

    @Test
    void aTerminatedContractStopsAppearingWithNoRemovalCall() {
        MercenaryContract contract = activeHire();
        War war = new War(1, fixture.hirer, enemy);
        assertEquals(1, MercenaryEngagements.on(war, war.getAttackers()).size());

        contract.finish(ContractStatus.TERMINATED);

        assertTrue(MercenaryEngagements.on(war, war.getAttackers()).isEmpty());
        assertEquals(contract, fixture.company.getContractHandler().getById(contract.getId()));
    }

    @Test
    void aContractCoversEveryWarTheHirerIsIn() {
        activeHire();
        Faction otherEnemy = ContractFixture.faction("other_enemy");
        when(otherEnemy.getRelations()).thenReturn(new HashMap<>());
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(otherEnemy.getGuildHandler()).thenReturn(empty);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(otherEnemy);

        War first = new War(1, fixture.hirer, enemy);
        War second = new War(2, otherEnemy, fixture.hirer);

        assertEquals(1, MercenaryEngagements.on(first, first.getAttackers()).size());
        assertEquals(1, MercenaryEngagements.on(second, second.getDefenders()).size());
        assertTrue(MercenaryEngagements.on(second, second.getAttackers()).isEmpty());
    }

    @Test
    void aCompanysPresenceDoesNotMakeItsHostABelligerent() {
        activeHire();
        War war = new War(1, fixture.hirer, enemy);

        assertFalse(war.isParticipating(host));
        assertFalse(war.getAttackers().isParticipating(host));
        assertTrue(war.isParticipating(fixture.hirer));
    }

    @Test
    void sideForFollowsTheContractedHirer() {
        activeHire();
        fixture.company.enlist("Sigrun");
        War war = new War(1, fixture.hirer, enemy);

        assertEquals(war.getAttackers(), MercenaryEngagements.sideFor(war, "Sigrun"));
        assertNull(MercenaryEngagements.sideFor(war, "Stranger"));
    }

    @Test
    void coveringMembersCapsAtPromisedSlots() {
        activeHire();
        fixture.company.enlist("Sigrun");
        UUID soldier = UUID.randomUUID();
        UUID sigrun = UUID.randomUUID();
        UUID extra = UUID.randomUUID();
        Warband band = Warband.createWithMemberIds("host", extra, true, soldier, sigrun, extra);
        BattleSide side = mock(BattleSide.class);
        when(side.getBands()).thenReturn(List.of(band));

        MercenaryEngagements.Engagement engagement = new MercenaryEngagements.Engagement(
                fixture.company, fixture.company.getContractHandler().getActive().get(0));

        int covering = MercenaryEngagements.coveringMembers(
                engagement, side, name -> "Soldier0".equalsIgnoreCase(name) ? soldier
                        : "Sigrun".equalsIgnoreCase(name) ? sigrun : null);
        assertEquals(2, covering);
    }

    @Test
    void aSecondOfferFromTheEnemyIsRefused() {
        MercenaryContract first = activeHire();
        War war = new War(1, fixture.hirer, enemy);

        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(WarManager::getActive).thenReturn(List.of(war));
            MercenaryResult result = MercenaryLoyalty.canServeAlongside(
                    fixture.company, enemy, List.of(war));
            assertFalse(result.ok());
            assertTrue(result.message().contains("already serving the other side"));

            ContractHandler.Offer offer = fixture.company.getContractHandler()
                    .offer(enemy, ContractFixture.validTerms(1));
            assertFalse(offer.ok());
            assertEquals(ContractStatus.ACTIVE, first.getStatus());
        }
    }

    @Test
    void aLaterWarTerminatesTheJuniorContractAndKeepsTheElder() {
        long now = System.currentTimeMillis();
        MercenaryContract elder = fixture.offer(ContractFixture.validTerms(1), now);
        assertTrue(elder.activate());

        Faction otherHirer = ContractFixture.faction("other_client");
        when(otherHirer.getRelations()).thenReturn(new HashMap<>());
        me.Plugins.SimpleFactions.Objects.Handler.GuildHandler empty =
                mock(me.Plugins.SimpleFactions.Objects.Handler.GuildHandler.class);
        when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
        when(otherHirer.getGuildHandler()).thenReturn(empty);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(otherHirer);
        MercenaryContract junior = new MercenaryContract(
                fixture.company, otherHirer, ContractKind.MERCENARY,
                ContractFixture.validTerms(1), now + 1000);
        fixture.company.getContractHandler().add(junior);
        assertTrue(junior.activate());

        War later = new War(9, fixture.hirer, otherHirer);
        List<MercenaryContract> ended =
                ContractTerminationService.resolveDoubleHire(fixture.company, List.of(later));

        assertEquals(List.of(junior), ended);
        assertEquals(ContractStatus.ACTIVE, elder.getStatus());
        assertEquals(ContractStatus.TERMINATED, junior.getStatus());
    }
}
