package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.FactionRanker;

class UnguardedFactionTest {
    private final Player viewer = mock(Player.class);

    private Faction faction(String id) {
        var faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        when(faction.getFoundedAt()).thenReturn(1L);
        when(faction.getEspionage()).thenReturn(new EspionageState());
        when(viewer.getName()).thenReturn("ForeignViewer");
        return faction;
    }

    private SpecialPositionAssignment appoint(Faction faction, String name, int aptitude) {
        var holder = new SpecialPositionAssignment();
        holder.playerName = name;
        holder.playerId = UUID.randomUUID();
        holder.characterId = "character";
        when(faction.isMemberIgnoreCase(name)).thenReturn(true);
        faction.getEspionage().appoint(holder, aptitude);
        return holder;
    }

    @Test void vacancyExposesLiveFactionAndGuildWealthInsteadOfOldDailyEstimates() {
        var target = faction("target");
        var observer = faction("observer");
        when(target.getWealth()).thenReturn(1234.0);
        var guild = mock(Guild.class);
        when(guild.getFaction()).thenReturn(target);
        when(guild.getWealth()).thenReturn(321.0);
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        observer.getEspionage().report("target", 1, day, () -> {
            var stale = new IntelligenceReport();
            stale.quality = "Rumours";
            return stale;
        });
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getByMember("ForeignViewer")).thenReturn(observer);
            assertTrue(EspionageService.canViewExact(viewer, target));
            assertFalse(EspionageService.bypasses(viewer));
            assertFalse(EspionageService.isOwn(viewer, target));
            assertNull(EspionageService.report(viewer, target));
            var ranker = new FactionRanker();
            assertEquals(1234.0, ranker.visibleValue(viewer, target, RankType.WEALTH));
            assertEquals(321.0, ranker.visibleGuildValue(viewer, guild, RankType.WEALTH));
        }
    }

    @Test void zeroAptitudeAndSabotageStillProtectWhileRemovalAndAppointmentSwitchVisibility() {
        var target = faction("target");
        assertTrue(EspionageService.canViewExact(viewer, target));
        var holder = appoint(target, "Spy", 0);
        holder.defenseReduction = 100;
        assertTrue(EspionageService.hasSpymaster(target));
        assertFalse(EspionageService.canViewExact(viewer, target));
        target.getEspionage().removeSpymaster();
        assertTrue(EspionageService.canViewExact(viewer, target));
        appoint(target, "Spy", 100);
        assertFalse(EspionageService.canViewExact(viewer, target));
        assertFalse(EspionageService.canViewExact(null, target));
        assertFalse(EspionageService.canViewExact(viewer, null));
    }

    @Test void departedOrDeadHoldersLeaveInformationPublicWithoutReadTimeSaves() {
        var target = faction("target");
        var holder = appoint(target, "Spy", 50);
        try (var characters = mockStatic(OfficeCharacters.class); var databases = mockConstruction(Database.class)) {
            when(target.isMemberIgnoreCase("Spy")).thenReturn(false);
            assertTrue(EspionageService.canViewExact(viewer, target));
            when(target.isMemberIgnoreCase("Spy")).thenReturn(true);
            characters.when(() -> OfficeCharacters.isDead(holder.playerId, holder.characterId)).thenReturn(true);
            assertTrue(EspionageService.canViewExact(viewer, target));
            assertSame(holder, target.getEspionage().getSpymaster());
            assertTrue(databases.constructed().isEmpty());
        }
    }

    @Test void leaderKeepsProtectingAfterOthersJoinAndPendingFounderIsUnguarded() {
        var target = faction("target");
        target.getEspionage().pendingFounder(SpecialPosition.SPYMASTER);
        assertTrue(EspionageService.canViewExact(viewer, target));
        appoint(target, "Leader", 50);
        when(target.isLeader("Leader")).thenReturn(true);
        when(target.getMembers()).thenReturn(List.of("Leader"));
        assertFalse(EspionageService.canViewExact(viewer, target));
        when(target.getMembers()).thenReturn(List.of("Leader", "Member"));
        assertFalse(EspionageService.canViewExact(viewer, target));
    }

    @Test void publicMenusNeverGrantMembershipOfficeManagementOrSabotage() {
        var target = faction("target");
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getByString("target")).thenReturn(target);
            for (var type : new SFGUI[]{SFGUI.GOVERNMENT_VIEW, SFGUI.LEDGER_VIEW, SFGUI.MILITARY_VIEW})
                assertFalse(EspionageAccess.denied(viewer, new SFInventoryHolder("target", type)));
            for (var type : new SFGUI[]{SFGUI.SPECIAL_POSITIONS, SFGUI.SPYMASTER_SELECT, SFGUI.SPYMASTER_SETTINGS})
                assertTrue(EspionageAccess.denied(viewer, new SFInventoryHolder("target", type)));
            assertFalse(EspionageService.appoint(viewer, target, mock(Player.class)));
            assertFalse(EspionageService.remove(viewer, target));
            assertFalse(EspionageService.setSabotage(viewer, target, false, 100));
        }
    }

    @Test void ordinaryAndStaffRefreshSkipUnguardedTargetsWithoutCreatingReportsOrRolls() {
        var first = faction("first");
        var second = faction("second");
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        try (var factions = mockStatic(FactionManager.class); var databases = mockConstruction(Database.class)) {
            factions.when(() -> FactionManager.getByMember("ForeignViewer")).thenReturn(first);
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new ArrayList<>(List.of(first, second)));
            EspionageService.refreshReports(viewer);
            assertNull(first.getEspionage().cachedReport("second", 1, day));
            assertTrue(databases.constructed().isEmpty());
            verify(viewer, never()).sendMessage(anyString());
            verify(second, never()).getWealth();
            assertEquals(0, EspionageService.regenerateReports());
            assertNull(first.getEspionage().cachedReport("second", 1, day));
            assertNull(second.getEspionage().cachedReport("first", 1, day));
            assertEquals(2, databases.constructed().size(), "Staff refresh still persists cleared stale reports");
        }
    }

    @Test void missingObserverSpymasterBlocksOldReportsAndNewGatheringUntilAppointed() {
        var observer = faction("observer");
        var target = faction("target");
        appoint(target, "TargetSpy", 50);
        var holder = appoint(observer, "ObserverSpy", 0);
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        var cached = observer.getEspionage().report("target", 1, day, () -> {
            var report = new IntelligenceReport();
            report.quality = "Detailed estimates";
            report.estimates.put("Wealth", new EspionageMath.Estimate(100, 120));
            return report;
        });
        try (var factions = mockStatic(FactionManager.class); var databases = mockConstruction(Database.class)) {
            factions.when(() -> FactionManager.getByMember("ForeignViewer")).thenReturn(observer);
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new ArrayList<>(List.of(observer, target)));
            assertSame(cached, EspionageService.report(viewer, target));
            when(observer.isMemberIgnoreCase("ObserverSpy")).thenReturn(false);
            assertNull(EspionageService.report(viewer, target), "Ineligible observers cannot read cached findings");
            when(observer.isMemberIgnoreCase("ObserverSpy")).thenReturn(true);
            try (var characters = mockStatic(OfficeCharacters.class)) {
                characters.when(() -> OfficeCharacters.isDead(holder.playerId, holder.characterId)).thenReturn(true);
                assertNull(EspionageService.report(viewer, target), "Dead observers cannot read cached findings");
            }
            observer.getEspionage().removeSpymaster();
            assertNull(EspionageService.report(viewer, target));
            assertNull(EspionageService.visibleValue(viewer, target, "Wealth", () -> fail("No exact wealth for a protected target")));
            EspionageService.refreshReports(viewer);
            assertTrue(databases.constructed().isEmpty());
            verify(viewer, never()).sendMessage(anyString());
            assertSame(cached, observer.getEspionage().cachedReport("target", 1, day), "Office changes cannot reroll the day");
            appoint(observer, "ObserverSpy", 0);
            assertSame(cached, EspionageService.report(viewer, target));
            observer.getEspionage().removeSpymaster();
            assertEquals(0, EspionageService.regenerateReports());
            assertNull(observer.getEspionage().cachedReport("target", 1, day));
            assertNull(target.getEspionage().cachedReport("observer", 1, day));
        }
    }
}
