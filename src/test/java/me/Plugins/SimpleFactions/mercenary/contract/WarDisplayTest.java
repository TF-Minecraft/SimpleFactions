package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.Inventory.WarCreator;
import me.Plugins.SimpleFactions.Managers.Inventory.WarView;
import me.Plugins.SimpleFactions.Managers.Inventory.WarView.SideColumnLayout;
import me.Plugins.SimpleFactions.Utils.HomeSettlementNames;

class WarDisplayTest {
    private ContractFixture fixture;
    private WarCreator creator;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(2);
        when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
        when(MercenaryLoyalty.hostFaction(fixture.company).getRelations())
                .thenReturn(new HashMap<>());
        creator = new WarCreator();
        fixture.company.setReputation(72);
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    @Test
    void mercenaryLoreUsesPromisedSlotsReputationHomeAndDays() {
        MercenaryContract contract =
                fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
        assertTrue(contract.activate());
        while (fixture.company.getFilledSlots() < fixture.company.getSlots()) {
            fixture.company.enlist("Extra" + fixture.company.getFilledSlots());
        }

        MercenaryEngagements.Engagement engagement =
                new MercenaryEngagements.Engagement(fixture.company, contract);
        List<String> lore = creator.buildMercenaryLore(engagement, "mercenary_attacker");

        assertTrue(lore.stream().anyMatch(line -> line.contains("Mercenary") && line.contains("Attacker")));
        assertFalse(lore.stream().anyMatch(line -> line.contains("Main Attacker")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Promised slots") && line.contains("2")));
        assertFalse(lore.stream().anyMatch(line ->
                line.contains("Promised slots") && line.contains(String.valueOf(fixture.company.getFilledSlots()))
                        && fixture.company.getFilledSlots() != 2));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Reputation") && line.contains("72")));
        assertTrue(lore.stream().anyMatch(line ->
                line.contains("Home") && line.contains(HomeSettlementNames.of(fixture.host.guild))));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Days remaining")));
    }

    @Test
    void overflowOpenerAppearsOnlyPastTheColumnLimit() {
        assertFalse(SideColumnLayout.of(16, 0, WarView.SIDE_COLUMN_SIZE).overflowOpener());
        assertFalse(SideColumnLayout.of(15, 1, WarView.SIDE_COLUMN_SIZE).overflowOpener());
        SideColumnLayout overflow = SideColumnLayout.of(16, 1, WarView.SIDE_COLUMN_SIZE);
        assertTrue(overflow.overflowOpener());
        assertEquals(15, overflow.factionsShown());
        assertEquals(0, overflow.engagementsShown());
        assertTrue(creator.buildOverflowOpenerLore(1).stream().anyMatch(line -> line.contains("+1")));
    }
}
