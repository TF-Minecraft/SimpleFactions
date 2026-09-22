package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;

/**
 * The loyalty rules. A contract names no war, so every check is asked against all
 * of the hirer's wars at once.
 */
class MercenaryLoyaltyTest {
    private ContractFixture fixture;
    private Faction host;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(2);
        host = MercenaryLoyalty.hostFaction(fixture.company);
        relations(host);
        relations(fixture.hirer);
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    private static void relations(Faction f) {
        when(f.getRelations()).thenReturn(new HashMap<>());
    }

    private static Relation relation(String id, boolean overlord, boolean vassalage) {
        RelationType type = mock(RelationType.class);
        when(type.getId()).thenReturn(id);
        when(type.isOverlord()).thenReturn(overlord);
        when(type.isVassalage()).thenReturn(vassalage);
        Relation r = mock(Relation.class);
        when(r.getType()).thenReturn(type);
        return r;
    }

    /** A war the hirer is in, with the given side facing them. */
    private static War warAgainst(Faction hirer, Side opposing) {
        War war = mock(War.class);
        when(war.isParticipating(hirer)).thenReturn(true);
        when(war.getOppositeSide(hirer)).thenReturn(opposing);
        return war;
    }

    private static Side sideOpposing(Faction host) {
        Side side = mock(Side.class);
        when(side.isParticipating(host)).thenReturn(true);
        when(side.getLeader()).thenReturn(host);
        when(side.getMainParticipants()).thenReturn(List.of());
        return side;
    }

    private static Side sideLedBy(Faction enemyLeader) {
        Side side = mock(Side.class);
        when(side.getLeader()).thenReturn(enemyLeader);
        when(side.getMainParticipants()).thenReturn(List.of());
        return side;
    }

    /* =====================================================
     * Company level: canServe
     * ===================================================== */

    @Test
    void aPeacefulHirerRaisesNoObjection() {
        assertTrue(MercenaryLoyalty.canServe(fixture.company, fixture.hirer, List.of()).ok());
    }

    @Test
    void aWarTheHirerIsNotInIsIgnored() {
        War other = mock(War.class);
        when(other.isParticipating(fixture.hirer)).thenReturn(false);
        assertTrue(MercenaryLoyalty.canServe(
                fixture.company, fixture.hirer, List.of(other)).ok());
    }

    @Test
    void aCompanyWillNotFightItsOwnRealmDirectly() {
        War war = warAgainst(fixture.hirer, sideOpposing(host));
        MercenaryResult result =
                MercenaryLoyalty.canServe(fixture.company, fixture.hirer, List.of(war));
        assertFalse(result.ok());
        assertTrue(result.message().contains("will not take arms against"));
        assertTrue(result.message().contains(host.getName()));
    }

    /**
     * The nested case, through a real {@link Side}: the enemy leader has the host
     * realm as a vassal, so {@code Side.isParticipating} finds it without the
     * loyalty service walking the tree itself.
     */
    @Test
    void aCompanyWillNotFightAnOverlordOfItsOwnRealm() {
        Faction enemy = ContractFixture.faction("enemy");
        HashMap<String, Relation> enemyRelations = new HashMap<>();
        enemyRelations.put(host.getId(), relation("vassal", false, true));
        when(enemy.getRelations()).thenReturn(enemyRelations);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(enemy);

        Side side = new Side(enemy);
        assertTrue(side.isParticipating(host), "the real Side already walks subjects");

        War war = warAgainst(fixture.hirer, side);
        assertFalse(MercenaryLoyalty.canServe(fixture.company, fixture.hirer, List.of(war)).ok());
    }

    /**
     * An ally who has not answered a call to arms is not a participant yet, but
     * hiring against them is still treachery, so alliances are checked separately.
     */
    @Test
    void aCompanyWillNotFightAnAllyOfItsOwnRealm() {
        Faction ally = ContractFixture.faction("ally_realm");
        relations(ally);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(ally);

        HashMap<String, Relation> hostRelations = new HashMap<>();
        hostRelations.put("ally_realm", relation("ally", false, false));
        when(host.getRelations()).thenReturn(hostRelations);

        War war = warAgainst(fixture.hirer, sideLedBy(ally));
        MercenaryResult result =
                MercenaryLoyalty.canServe(fixture.company, fixture.hirer, List.of(war));
        assertFalse(result.ok());
        assertTrue(result.message().contains("bound to"));
    }

    @Test
    void aCompanyWillNotFightItsOwnOverlord() {
        Faction overlord = ContractFixture.faction("overlord_realm");
        relations(overlord);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(overlord);

        HashMap<String, Relation> hostRelations = new HashMap<>();
        hostRelations.put("overlord_realm", relation("overlord", true, false));
        when(host.getRelations()).thenReturn(hostRelations);

        War war = warAgainst(fixture.hirer, sideLedBy(overlord));
        assertFalse(MercenaryLoyalty.canServe(fixture.company, fixture.hirer, List.of(war)).ok());
    }

    @Test
    void anUnrelatedEnemyIsFairGame() {
        Faction stranger = ContractFixture.faction("stranger");
        relations(stranger);
        me.Plugins.SimpleFactions.Managers.FactionManager.factions.add(stranger);

        War war = warAgainst(fixture.hirer, sideLedBy(stranger));
        assertTrue(MercenaryLoyalty.canServe(fixture.company, fixture.hirer, List.of(war)).ok());
    }

    /* =====================================================
     * Player level: canDeploy
     * ===================================================== */

    @Test
    void aGovernmentMemberMayNotMarchOnTheirOwnRealm() {
        Government gov = mock(Government.class);
        when(gov.isCouncilMember("Chancellor")).thenReturn(true);
        when(gov.isCouncilMember("Farmhand")).thenReturn(false);
        when(host.getGovernment()).thenReturn(gov);
        when(fixture.host.guild.getMembers()).thenReturn(new ArrayList<>(List.of("Chancellor", "Farmhand")));

        assertFalse(MercenaryLoyalty.canDeploy("Chancellor", host));
        assertTrue(MercenaryLoyalty.canDeploy("Farmhand", host),
                "a plain citizen may sell their sword against their own realm");
    }

    @Test
    void deployingAgainstSomeoneElseIsAlwaysAllowed() {
        Government gov = mock(Government.class);
        when(gov.isCouncilMember("Chancellor")).thenReturn(true);
        when(host.getGovernment()).thenReturn(gov);
        when(fixture.host.guild.isMember("Chancellor")).thenReturn(true);

        assertTrue(MercenaryLoyalty.canDeploy("Chancellor", fixture.hirer));
    }

    @Test
    void blockedAgainstListsOnlyTheRulers() {
        fixture.company.enlist("Chancellor");
        fixture.company.enlist("Farmhand");

        Government gov = mock(Government.class);
        when(gov.isCouncilMember("Chancellor")).thenReturn(true);
        when(gov.isCouncilMember("Farmhand")).thenReturn(false);
        when(host.getGovernment()).thenReturn(gov);
        when(fixture.host.guild.getMembers()).thenReturn(new ArrayList<>(List.of("Chancellor", "Farmhand")));

        assertEquals(List.of("Chancellor"), MercenaryLoyalty.blockedAgainst(fixture.company, host));
    }

    /* =====================================================
     * Re-check hooks
     * ===================================================== */

    /**
     * The reason this exists: the contract was legal when signed, then the world
     * moved. Each entry point must end it, and end it without punishing anyone.
     */
    @Test
    void eachRecheckEntryPointEndsANowIllegalContractWithoutPenalty() {
        assertRecheckTerminates(joiner -> MercenaryLoyaltyWatcher.onWarJoined(joiner));
        assertRecheckTerminates(joiner ->
                MercenaryLoyaltyWatcher.onRelationChanged(joiner, joiner));
        assertRecheckTerminates(joiner -> MercenaryLoyaltyWatcher.onGovernmentChanged(joiner));
    }

    private void assertRecheckTerminates(java.util.function.Consumer<Faction> trigger) {
        MercenaryContract contract =
                fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());
        contract.activate();
        contract.addDayServed();

        RecordingSeam seam = new RecordingSeam();
        ContractTerminationService.setReputationSeam(seam);
        List<War> illegal = List.of(warAgainst(fixture.hirer, sideOpposing(host)));
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(WarManager::getActive).thenReturn(illegal);
            trigger.accept(fixture.hirer);
        } finally {
            ContractTerminationService.setReputationSeam(null);
        }

        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
        assertEquals(0, seam.calls, "a loyalty conflict moves no reputation");
        assertEquals(fixture.company.getContractHandler().getById(contract.getId())
                .getServedDaysOwed(), contract.getServedDaysOwed());
        assertTrue(contract.getServedDaysOwed() > 0, "the day already served is still owed");
        fixture.company.getContractHandler().remove(contract.getId());
    }

    @Test
    void aLegalContractSurvivesARecheck() {
        MercenaryContract contract =
                fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());
        contract.activate();

        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(WarManager::getActive).thenReturn(List.of());
            MercenaryLoyaltyWatcher.onWarJoined(fixture.hirer);
        }

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    /** An offer is not yet a promise, so a re-check leaves it for the offer flow. */
    @Test
    void aRecheckLeavesAnUnacceptedOfferAlone() {
        MercenaryContract offer =
                fixture.offer(ContractFixture.validTerms(1), System.currentTimeMillis());

        List<War> illegal = List.of(warAgainst(fixture.hirer, sideOpposing(host)));
        try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
            wars.when(WarManager::getActive).thenReturn(illegal);
            MercenaryLoyaltyWatcher.onWarJoined(fixture.hirer);
        }

        assertEquals(ContractStatus.OFFERED, offer.getStatus());
    }

    private static final class RecordingSeam implements ContractReputationSeam {
        private int calls;

        @Override
        public void onTermination(MercenaryContract contract, TerminationReason reason) {
            calls++;
        }
    }
}
