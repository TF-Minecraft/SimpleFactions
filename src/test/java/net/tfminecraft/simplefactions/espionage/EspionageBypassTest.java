package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.FactionRanker;

class EspionageBypassTest {
    @Test
    void configuredPermissionChangesOnReloadAndOwnViewsRemainExact() {
        Player viewer = mock(Player.class);
        when(viewer.getName()).thenReturn("Viewer");
        when(viewer.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
        Faction own = mock(Faction.class), foreign = mock(Faction.class);
        when(own.isMemberIgnoreCase("Viewer")).thenReturn(true);
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            assertTrue(EspionageService.canViewExact(viewer, foreign));
            config.set("espionage.bypass-permission", " custom.staff.intelligence ");
            EspionageConfig.load(config);
            assertEquals("custom.staff.intelligence", EspionageConfig.bypassPermission());
            assertFalse(EspionageService.canViewExact(viewer, foreign));
            assertTrue(EspionageService.canViewExact(viewer, own));
            when(viewer.hasPermission("custom.staff.intelligence")).thenReturn(true);
            assertTrue(EspionageService.canViewExact(viewer, foreign));
            config.set("espionage.bypass-permission", " ");
            EspionageConfig.load(config);
            assertEquals(EspionageService.BYPASS_PERMISSION, EspionageConfig.bypassPermission());
            assertTrue(EspionageService.bypasses(viewer));
        } finally { EspionageConfig.load(new org.bukkit.configuration.file.YamlConfiguration()); }
    }

    @Test
    void bypassRestoresExactFactionAndGuildRankingsWithoutGatheringReports() {
        Player staff = mock(Player.class);
        when(staff.getName()).thenReturn("Staff");
        when(staff.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
        Faction rich = mock(Faction.class), poor = mock(Faction.class);
        when(rich.getId()).thenReturn("rich");
        when(poor.getId()).thenReturn("poor");
        when(rich.getWealth()).thenReturn(250.0);
        when(poor.getWealth()).thenReturn(12.0);
        Guild guild = mock(Guild.class);
        when(guild.getFaction()).thenReturn(rich);
        when(guild.getWealth()).thenReturn(76.0);
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new ArrayList<>(List.of(poor, rich)));
            var ranker = new FactionRanker();
            assertEquals(250.0, ranker.visibleValue(staff, rich, RankType.WEALTH));
            assertEquals(76.0, ranker.visibleGuildValue(staff, guild, RankType.WEALTH));
            assertEquals(List.of(rich, poor), ranker.getVisibleRankedList(staff, RankType.WEALTH));
            assertEquals(1, ranker.getVisibleRank(staff, rich, RankType.WEALTH));
            assertNull(EspionageService.report(staff, rich));
            EspionageService.refreshReports(staff);
            verify(rich, never()).getEspionage();
            verify(poor, never()).getEspionage();
            factions.verify(() -> FactionManager.getByMember("Staff"), never());
            verify(staff, never()).sendMessage(anyString());
        }
    }

    @Test
    void revocationClosesPrivateInformationMenusAndBypassDoesNotGrantOfficeAuthority() {
        Player staff = mock(Player.class);
        when(staff.getName()).thenReturn("Staff");
        when(staff.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(true);
        Faction faction = mock(Faction.class);
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getByString("target")).thenReturn(faction);
            assertTrue(EspionageService.canViewExact(staff, faction));
            assertFalse(EspionageService.isOwn(staff, faction));
            for (SFGUI type : new SFGUI[]{SFGUI.MILITARY_VIEW, SFGUI.LEDGER_VIEW,
                    SFGUI.GOVERNMENT_VIEW, SFGUI.LAW_VIEW, SFGUI.INSTALLATIONS_VIEW}) {
                assertFalse(EspionageAccess.denied(staff, new SFInventoryHolder("target", type)));
            }
            for (SFGUI type : new SFGUI[]{SFGUI.SPECIAL_POSITIONS, SFGUI.SPYMASTER_SETTINGS, SFGUI.SPYMASTER_SELECT}) {
                assertTrue(EspionageAccess.denied(staff, new SFInventoryHolder("target", type)));
            }
            assertFalse(EspionageService.appoint(staff, faction, mock(Player.class)));
            assertFalse(EspionageService.remove(staff, faction));
            when(staff.hasPermission(EspionageService.BYPASS_PERMISSION)).thenReturn(false);
            assertFalse(EspionageService.canViewExact(staff, faction));
            assertTrue(EspionageAccess.denied(staff, new SFInventoryHolder("target", SFGUI.MILITARY_VIEW)));
            assertFalse(EspionageService.bypasses(null));
            assertFalse(EspionageService.canViewExact(staff, null));
        }
    }
}
