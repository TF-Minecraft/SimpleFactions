package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.EspionageModes;
import net.tfminecraft.simplefactions.utils.FactionRanker;

/** By default a Spymaster guards only regiments and vehicles; everything else is public. */
class MilitaryOnlyIntelligenceTest {
    private Player viewer;
    private Faction target;

    @BeforeEach
    void setup() {
        EspionageModes.reset();
        viewer = mock(Player.class);
        when(viewer.getName()).thenReturn("Viewer");
        target = mock(Faction.class);
        EspionageTestFixtures.protect(target);
    }

    @AfterEach
    void reset() { EspionageModes.reset(); }

    @Test
    void protectedFactionsShowEverythingButTheirMilitary() {
        assertTrue(EspionageConfig.militaryOnly());
        assertTrue(EspionageService.canViewExact(viewer, target));
        assertFalse(EspionageService.canViewCovert(viewer, target));
        assertFalse(EspionageService.canViewExact(null, target));
        assertFalse(EspionageService.canViewExact(viewer, null));
        assertTrue(EspionageAccess.canView(viewer, target, SFGUI.GOVERNMENT_VIEW));
        assertTrue(EspionageAccess.canView(viewer, target, SFGUI.INSTALLATIONS_VIEW));
        assertTrue(EspionageAccess.canView(viewer, target, SFGUI.COMPANY_VIEW));
        assertFalse(EspionageAccess.canView(viewer, target, SFGUI.MILITARY_VIEW));
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getByString("target")).thenReturn(target);
            for (SFGUI type : new SFGUI[]{SFGUI.LEDGER_VIEW, SFGUI.GOVERNMENT_VIEW, SFGUI.STABILITY_VIEW,
                    SFGUI.LAW_VIEW, SFGUI.TAX_VIEW, SFGUI.UPGRADE_VIEW, SFGUI.INSTALLATIONS_VIEW,
                    SFGUI.INSTALLATION_DETAIL_VIEW}) {
                assertFalse(EspionageAccess.denied(viewer, new SFInventoryHolder("target", type)), type.name());
            }
            assertTrue(EspionageAccess.denied(viewer, new SFInventoryHolder("target", SFGUI.MILITARY_VIEW)));
            assertTrue(EspionageAccess.denied(viewer, new SFInventoryHolder("target", SFGUI.SPYMASTER_SETTINGS)),
                    "Office management stays with members");
        }
    }

    @Test
    void wealthRankingsUseExactValuesWithoutReports() {
        when(target.getWealth()).thenReturn(4321.0);
        assertEquals(4321.0, new FactionRanker().visibleValue(viewer, target, RankType.WEALTH));
        assertEquals(1.0, EspionageService.visibleValue(viewer, target, "Members", () -> 1.0));
    }

    @Test
    void guardingEverythingRestoresTheFullDisguise() {
        EspionageModes.guardEverything();
        assertFalse(EspionageConfig.militaryOnly());
        assertFalse(EspionageService.canViewExact(viewer, target));
        assertFalse(EspionageAccess.canView(viewer, target, SFGUI.GOVERNMENT_VIEW));
        when(viewer.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
        assertTrue(EspionageService.canViewExact(viewer, target));
        assertTrue(EspionageAccess.canView(viewer, target, SFGUI.MILITARY_VIEW));
    }
}
