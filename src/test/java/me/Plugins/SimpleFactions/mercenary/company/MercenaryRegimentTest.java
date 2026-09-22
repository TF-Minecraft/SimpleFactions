package me.Plugins.SimpleFactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Loaders.RegimentLoader;
import me.Plugins.SimpleFactions.Objects.Faction;

class MercenaryRegimentTest {

    @BeforeEach
    void setUp() {
        RegimentLoader.oList.clear();
        RegimentLoader.oList.add(CompanyFixture.regularPrototype("militia", 6, 2.0));
        RegimentLoader.oList.add(CompanyFixture.prototype());
    }

    @AfterEach
    void tearDown() {
        CompanyFixture.clearRegiments();
    }

    @Test
    void factionMilitaryNeverHoldsTheMercenaryType() {
        Military military = new Military(mock(Faction.class));

        assertEquals(1, military.getRegiments().size());
        assertNull(military.getRegiment("mercenary"));
        assertFalse(military.getRegiments().stream().anyMatch(Regiment::isMercenary));
    }

    @Test
    void mercenaryYamlDoesNotChangeManpowerSlotsOrUpkeep() {
        Military military = new Military(mock(Faction.class));

        assertEquals(6, military.getTotalSlots());
        assertEquals(6, military.getManpower(true));
        assertEquals(6, military.getManpowerNoLevy(false));
        assertEquals(12.0, military.getRawTotalUpkeep());
    }

    @Test
    void companyClonesThePrototypeInsteadOfSharingIt() {
        Regiment prototype = RegimentLoader.getMercenaryRegiment();
        assertNotNull(prototype);
        assertTrue(prototype.isMercenary());

        Regiment first = MercenaryCompany.cloneMercenaryRegiment();
        Regiment second = MercenaryCompany.cloneMercenaryRegiment();

        assertNotSame(prototype, first);
        assertNotSame(first, second);
        assertTrue(first.isMercenary());

        first.setCurrentSlots(4);

        assertEquals(4, first.getCurrentSlots());
        assertEquals(0, second.getCurrentSlots());
        assertEquals(0, prototype.getCurrentSlots());
    }

    @Test
    void missingMercenaryYamlLeavesNothingToClone() {
        CompanyFixture.clearRegiments();

        assertNull(RegimentLoader.getMercenaryRegiment());
        assertNull(MercenaryCompany.cloneMercenaryRegiment());
    }
}
