package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.mercenary.MercenaryResult;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

/**
 * The market: what it advertises and who is allowed to sign. Signing is local, so
 * these tests are mostly about province equality and government membership.
 */
class MercenaryMarketTest {
    private static final int HALL_PROVINCE = 705;

    private ContractFixture fixture;
    private Faction host;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(4);
        host = MercenaryLoyalty.hostFaction(fixture.company);
        when(host.getRelations()).thenReturn(new HashMap<>());
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(fixture.host.guild.hasCapital()).thenReturn(true);
        when(fixture.host.guild.getCapital()).thenReturn(HALL_PROVINCE);
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    /* =====================================================
     * Listing
     * ===================================================== */

    @Test
    void theListingOnlyHoldsFormedCompanies() {
        assertEquals(List.of(fixture.company), MercenaryMarket.listing());
    }

    /** A track record has to be worth something, so reputation orders the hall. */
    @Test
    void companiesAreOrderedByReputationDescending() {
        MercenaryCompany poor = mock(MercenaryCompany.class);
        when(poor.getReputation()).thenReturn(10);
        when(poor.getName()).thenReturn("Ragged Spears");
        MercenaryCompany rich = mock(MercenaryCompany.class);
        when(rich.getReputation()).thenReturn(90);
        when(rich.getName()).thenReturn("Golden Company");

        assertEquals(List.of(rich, fixture.company, poor),
                MercenaryMarket.sorted(List.of(poor, fixture.company, rich)));
    }

    @Test
    void anEqualReputationFallsBackToTheNameSoTheOrderIsStable() {
        MercenaryCompany a = mock(MercenaryCompany.class);
        when(a.getReputation()).thenReturn(50);
        when(a.getName()).thenReturn("Anvil Guard");
        MercenaryCompany z = mock(MercenaryCompany.class);
        when(z.getReputation()).thenReturn(50);
        when(z.getName()).thenReturn("Zealots");

        assertEquals(List.of(a, z), MercenaryMarket.sorted(List.of(z, a)));
    }

    /** Availability, not raw slot count, because promised slots are not for sale. */
    @Test
    void availabilityCountsOnlyUnpromisedSlots() {
        assertEquals(4, MercenaryMarket.availableToday(fixture.company));
        fixture.offer(ContractFixture.validTerms(3), System.currentTimeMillis());
        assertEquals(1, MercenaryMarket.availableToday(fixture.company));
    }

    @Test
    void aCompanyWithNoCapitalShowsTheNoneFallback() {
        when(fixture.host.guild.hasCapital()).thenReturn(false);
        assertEquals("None", MercenaryMarket.homeSettlement(fixture.company));
    }

    @Test
    void theHomeSettlementIsNamedFromTheHostGuildsCapital() {
        Settlement settlement = mock(Settlement.class);
        when(settlement.getName()).thenReturn("Greenfort");
        SettlementHandler handler = mock(SettlementHandler.class);
        when(handler.getByProvince(HALL_PROVINCE)).thenReturn(settlement);
        when(host.getSettlementHandler()).thenReturn(handler);

        try (MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class)) {
            titles.when(() -> TitleLoader.getByProvince(HALL_PROVINCE)).thenReturn(null);
            assertEquals("Greenfort", MercenaryMarket.homeSettlement(fixture.company));
        }
    }

    /* =====================================================
     * The local gate
     * ===================================================== */

    /**
     * A settlement occupies exactly one province, so province equality is the whole
     * range check rather than a radius.
     */
    @Test
    void standingInTheHallProvinceIsLocal() {
        assertTrue(MercenaryMarket.isLocal(fixture.company, HALL_PROVINCE).ok());
    }

    @Test
    void signingFromAnotherProvinceIsRefusedAndNamesTheSettlement() {
        Settlement settlement = mock(Settlement.class);
        when(settlement.getName()).thenReturn("Greenfort");
        SettlementHandler handler = mock(SettlementHandler.class);
        when(handler.getByProvince(HALL_PROVINCE)).thenReturn(settlement);
        when(host.getSettlementHandler()).thenReturn(handler);

        MercenaryResult result = MercenaryMarket.isLocal(fixture.company, HALL_PROVINCE + 1);
        assertFalse(result.ok());
        assertTrue(result.message().contains("Greenfort"), result.message());
        assertTrue(result.message().contains("Hired Blades"));
    }

    @Test
    void anUnresolvedOrProvincelessLocationIsRefusedDistinctly() {
        MercenaryResult unresolved = MercenaryMarket.isLocal(fixture.company, -2);
        assertFalse(unresolved.ok());
        assertTrue(unresolved.message().contains("resolve"));

        MercenaryResult none = MercenaryMarket.isLocal(fixture.company, 0);
        assertFalse(none.ok());
        assertTrue(none.message().contains("no province"));
    }

    @Test
    void aCompanyWithNoHallCannotBeSignedWithAnywhere() {
        when(fixture.host.guild.hasCapital()).thenReturn(false);
        MercenaryResult result = MercenaryMarket.isLocal(fixture.company, HALL_PROVINCE);
        assertFalse(result.ok());
        assertTrue(result.message().contains("no hall"));
    }

    /* =====================================================
     * The authority gate
     * ===================================================== */

    /** Any government member, so a besieged ruler is not a single point of failure. */
    @Test
    void anyGovernmentMemberOfTheHiringFactionMaySign() {
        governedHirer("Chancellor", "Marshal");

        assertTrue(MercenaryMarket.hasAuthority(fixture.company, "Chancellor").ok());
        assertTrue(MercenaryMarket.hasAuthority(fixture.company, "Marshal").ok());
    }

    @Test
    void aPlainCitizenCannotHireACompany() {
        governedHirer("Chancellor");

        MercenaryResult result = MercenaryMarket.hasAuthority(fixture.company, "Farmhand");
        assertFalse(result.ok());
        assertTrue(result.message().contains("government"));
    }

    @Test
    void someoneWithNoFactionCannotHire() {
        MercenaryResult result = MercenaryMarket.hasAuthority(fixture.company, "Wanderer");
        assertFalse(result.ok());
        assertTrue(result.message().contains("not in a faction"));
    }

    /** The loyalty rule is part of the gate, not a separate afterthought. */
    @Test
    void aFactionAtWarWithTheHostRealmCannotHireItAtAll() {
        governedHirer("Chancellor");
        Side opposing = mock(Side.class);
        when(opposing.isParticipating(host)).thenReturn(true);
        when(opposing.getLeader()).thenReturn(host);
        when(opposing.getMainParticipants()).thenReturn(List.of());
        War war = mock(War.class);
        when(war.isParticipating(fixture.hirer)).thenReturn(true);
        when(war.getOppositeSide(fixture.hirer)).thenReturn(opposing);
        List<War> wars = List.of(war);

        try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
            warManager.when(WarManager::getActive).thenReturn(wars);
            MercenaryResult result = MercenaryMarket.hasAuthority(fixture.company, "Chancellor");
            assertFalse(result.ok());
            assertTrue(result.message().contains("will not take arms against"));
        }
    }

    /** Makes the hirer a real faction the signer belongs to, with a government. */
    private void governedHirer(String... council) {
        Government gov = mock(Government.class);
        for (String member : council) {
            when(gov.isCouncilMember(member)).thenReturn(true);
        }
        when(fixture.hirer.getGovernment()).thenReturn(gov);

        List<String> members = new java.util.ArrayList<>(List.of(council));
        members.add("Farmhand");
        when(fixture.hirer.getMembers()).thenReturn(members);
        when(fixture.hirer.isMemberIgnoreCase(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return members.stream().anyMatch(m -> m.equalsIgnoreCase(name));
        });
        assertTrue(FactionManager.factions.contains(fixture.hirer));
    }
}
