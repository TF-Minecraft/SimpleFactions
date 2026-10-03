package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.inventory.GovernmentView;
import net.tfminecraft.simplefactions.managers.inventory.GuildView;
import net.tfminecraft.simplefactions.managers.inventory.InstallationView;
import net.tfminecraft.simplefactions.managers.inventory.LawView;
import net.tfminecraft.simplefactions.managers.inventory.MilitaryView;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class EspionagePermissionsTest {
    @Test
    void outsidersOpenMaskedSubmenusWithoutBuildingExactInformation() {
        Player outsider = mock(Player.class);
        Faction faction = mock(Faction.class);
        EspionageTestFixtures.protect(faction);
        Guild guild = mock(Guild.class);
        when(guild.getFaction()).thenReturn(faction);
        InventoryManager manager = mock(InventoryManager.class);
        var inventory = mock(org.bukkit.inventory.Inventory.class);
        try (var reported = mockStatic(net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.class);
             var views = mockStatic(net.tfminecraft.simplefactions.managers.inventory.EspionageView.class)) {
            reported.when(() -> net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.open(any(), any(), any(), anyInt(), anyString())).thenReturn(inventory);
            new MilitaryView(manager).militaryView(null, outsider, faction, true);
            new GovernmentView(manager).governmentView(outsider, faction, null);
            new LawView(manager).lawView(outsider, faction, null);
            new InstallationView(manager).installationsView(null, outsider, faction, true);
            new GuildView(manager).ledgerView(outsider, guild, null);
            new GuildView(manager).upgradeView(outsider, guild, inventory);
            reported.verify(() -> net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.military(inventory, outsider, faction, manager));
            reported.verify(() -> net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.government(inventory, outsider, faction, manager));
            reported.verify(() -> net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.laws(inventory, outsider, faction, manager));
            reported.verify(() -> net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.installations(inventory, outsider, faction, manager));
            reported.verify(() -> net.tfminecraft.simplefactions.managers.inventory.ReportedMenus.upgrades(inventory, outsider, guild, manager));
            views.verify(() -> net.tfminecraft.simplefactions.managers.inventory.EspionageView.foreignLedger(outsider, guild, manager));
        }
        verify(manager, never()).factionView(outsider, faction);
        verify(faction, never()).getMilitary();
        verify(faction, never()).getGovernment();
        verify(guild, never()).getLedger();
    }

    @Test
    void sabotageRequiresTheOfficeHoldersUuidEvenForLeadersAndMembers() {
        Player leader = mock(Player.class);
        Player holder = mock(Player.class);
        Faction faction = mock(Faction.class);
        var state = new EspionageState();
        var assignment = new SpecialPositionAssignment();
        assignment.playerId = UUID.randomUUID();
        assignment.playerName = "Spy";
        assignment.characterId = "character";
        state.appoint(assignment, () -> 60);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.isMemberIgnoreCase(anyString())).thenReturn(true);
        when(faction.isLeader("Leader")).thenReturn(true);
        when(leader.getName()).thenReturn("Leader");
        when(leader.getUniqueId()).thenReturn(UUID.randomUUID());
        when(holder.getName()).thenReturn("Spy");
        when(holder.getUniqueId()).thenReturn(assignment.playerId);
        try (var databases = mockConstruction(Database.class, (database, context) ->
                when(database.saveFactionChecked(faction)).thenReturn(true))) {
            assertFalse(EspionageService.setSabotage(leader, faction, true, 100));
            assertEquals(0, assignment.offenseReduction);
            assertFalse(EspionageService.setSabotage(holder, faction, true, 24));
            assertTrue(EspionageService.setSabotage(holder, faction, true, 75));
            assertEquals(75, assignment.offenseReduction);
            assertEquals(0, assignment.defenseReduction);
            assertTrue(EspionageService.setSabotage(holder, faction, false, 100));
            assertTrue(EspionageService.setSabotage(holder, faction, true, 0));
            assertEquals(0, assignment.offenseReduction);
            verify(leader, times(1)).sendMessage(anyString());
        }
    }

    @Test
    void onlyFactionLeaderCanAppointOrRemove() {
        Player member = mock(Player.class);
        Player candidate = mock(Player.class);
        Faction faction = mock(Faction.class);
        when(member.getName()).thenReturn("Member");
        when(faction.isMemberIgnoreCase("Member")).thenReturn(true);
        try (var databases = mockConstruction(Database.class)) {
            assertFalse(EspionageService.appoint(member, faction, candidate));
            assertFalse(EspionageService.remove(member, faction));
            assertTrue(databases.constructed().isEmpty());
            verifyNoInteractions(candidate);
        }
    }

    @Test
    void unaffiliatedAndOwnMembersDoNotConsumeForeignRolls() {
        Player viewer = mock(Player.class);
        Faction target = mock(Faction.class);
        EspionageTestFixtures.protect(target);
        when(viewer.getName()).thenReturn("Visitor");
        try (var factions = mockStatic(FactionManager.class)) {
            assertNull(EspionageService.report(viewer, target));
            assertNull(target.getEspionage().cachedReport("unused", 0, 0));
            when(target.isMemberIgnoreCase("Visitor")).thenReturn(true);
            assertNull(EspionageService.report(viewer, target));
            factions.verify(() -> FactionManager.getByMember("Visitor"), times(1));
        }
    }

    @Test
    void expiredMembershipAndReplacedOfficeCloseExistingPrivateScreens() {
        Player member = mock(Player.class);
        Faction faction = mock(Faction.class);
        when(member.getName()).thenReturn("FormerSpy");
        when(member.getUniqueId()).thenReturn(UUID.randomUUID());
        EspionageTestFixtures.protect(faction);
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getByString("faction")).thenReturn(faction);
            assertTrue(EspionageAccess.denied(member, new SFInventoryHolder("faction", SFGUI.MILITARY_VIEW)));
            when(faction.isMemberIgnoreCase("FormerSpy")).thenReturn(true);
            assertFalse(EspionageAccess.denied(member, new SFInventoryHolder("faction", SFGUI.MILITARY_VIEW)));
            assertTrue(EspionageAccess.denied(member, new SFInventoryHolder("faction", SFGUI.SPYMASTER_SETTINGS)));
            assertFalse(EspionageAccess.denied(member, new SFInventoryHolder("faction", SFGUI.SPECIAL_POSITIONS)));
        }
    }

    @Test
    void departedSpymasterLosesOfficeAndCannotSabotage() {
        Player former = mock(Player.class);
        Faction faction = mock(Faction.class);
        var state = new EspionageState();
        var assignment = new SpecialPositionAssignment();
        assignment.playerName = "FormerSpy";
        assignment.playerId = UUID.randomUUID();
        assignment.characterId = "character";
        state.appoint(assignment, () -> 90);
        when(faction.getEspionage()).thenReturn(state);
        when(former.getName()).thenReturn("FormerSpy");
        when(former.getUniqueId()).thenReturn(assignment.playerId);
        try (var databases = mockConstruction(Database.class)) {
            assertNull(EspionageService.spymaster(faction));
            assertNull(state.getSpymaster());
            assertFalse(EspionageService.setSabotage(former, faction, true, 100));
            verify(databases.constructed().getFirst()).saveFaction(faction);
        }
    }

    @Test
    void leaderIsIneligibleUnlessAloneAndSoloAptitudeIsReducedBySeventyFivePercent() {
        Faction faction = mock(Faction.class);
        when(faction.isLeader("Leader")).thenReturn(true);
        when(faction.isMemberIgnoreCase(anyString())).thenReturn(true);
        when(faction.getMembers()).thenReturn(java.util.List.of("Leader", "Spy"));
        assertFalse(EspionageService.eligible(faction, "Leader"));
        assertTrue(EspionageService.eligible(faction, "Spy"));
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Leader";
        holder.aptitude = 100;
        assertEquals(0, EspionageService.effectiveAptitude(faction, holder));
        when(faction.getMembers()).thenReturn(java.util.List.of("Leader"));
        assertTrue(EspionageService.eligible(faction, "Leader"));
        assertEquals(25, EspionageService.effectiveAptitude(faction, holder));
        assertEquals(100, holder.aptitude, "Permanent base aptitude must remain unchanged");
        holder.aptitude = 79;
        assertEquals(19, EspionageService.effectiveAptitude(faction, holder));
    }

    @Test
    void soloLeaderLosesOfficeWhenAnotherMemberJoins() {
        Faction faction = mock(Faction.class);
        when(faction.isLeader("Leader")).thenReturn(true);
        when(faction.getMembers()).thenReturn(java.util.List.of("Leader"));
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Leader";
        state.appoint(holder, 100);
        when(faction.getEspionage()).thenReturn(state);
        assertSame(holder, EspionageService.spymaster(faction));
        when(faction.getMembers()).thenReturn(java.util.List.of("Leader", "GuildMember"));
        try (var databases = mockConstruction(Database.class)) {
            assertNull(EspionageService.spymaster(faction));
            assertNull(state.getSpymaster());
            verify(databases.constructed().getFirst()).saveFaction(faction);
        }
    }
}
