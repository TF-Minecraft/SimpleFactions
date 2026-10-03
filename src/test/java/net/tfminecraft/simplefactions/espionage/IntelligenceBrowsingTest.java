package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.enums.RankType;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.FactionRanker;

class IntelligenceBrowsingTest {
    private Faction faction(String id) {
        Faction faction = mock(Faction.class, RETURNS_DEEP_STUBS);
        when(faction.getId()).thenReturn(id);
        when(faction.getFoundedAt()).thenReturn(1L);
        when(faction.getEspionage()).thenReturn(new EspionageState());
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Spy";
        faction.getEspionage().appoint(holder, 50);
        when(faction.isMemberIgnoreCase("Spy")).thenReturn(true);
        when(faction.getMembers()).thenReturn(List.of());
        when(faction.getGuildHandler().getGuilds()).thenReturn(List.of());
        when(faction.getMilitary().getRegiments()).thenReturn(List.of());
        when(faction.getInstallationHandler().getAll()).thenReturn(List.of());
        return faction;
    }

    @Test
    void browsingOnlyReadsCacheAndMenuRefreshIsSharedOncePerDay() {
        Player first = mock(Player.class), second = mock(Player.class);
        when(first.getName()).thenReturn("First");
        when(second.getName()).thenReturn("Second");
        Faction observer = faction("observer"), target = faction("target");
        when(observer.isMemberIgnoreCase(anyString())).thenReturn(true);
        try (var factions = mockStatic(FactionManager.class); var databases = mockConstruction(Database.class)) {
            factions.when(() -> FactionManager.getByMember("First")).thenReturn(observer);
            factions.when(() -> FactionManager.getByMember("Second")).thenReturn(observer);
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new ArrayList<>(List.of(observer, target)));
            assertNull(EspionageService.report(first, target));
            assertTrue(databases.constructed().isEmpty(), "Clicking must not save or roll anything");
            verify(target, never()).getWealth();
            EspionageService.refreshReports(first);
            var report = EspionageService.report(first, target);
            assertNotNull(report);
            assertSame(report, EspionageService.report(second, target));
            assertEquals(2, databases.constructed().size());
            EspionageService.refreshReports(second);
            assertSame(report, EspionageService.report(second, target));
            assertEquals(2, databases.constructed().size(), "A second member cannot reroll the faction's day");
            verify(first, times(1)).sendMessage(anyString());
            verify(second, never()).sendMessage(anyString());
        }
    }

    @Test
    void dailyRefreshBatchesObserverAndTargetsAndSkipsUnchangedReports() {
        Player viewer = mock(Player.class);
        when(viewer.getName()).thenReturn("Viewer");
        Faction observer = faction("observer"), first = faction("first"), second = faction("second");
        try (var factions = mockStatic(FactionManager.class); var databases = mockConstruction(Database.class)) {
            factions.when(() -> FactionManager.getByMember("Viewer")).thenReturn(observer);
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new ArrayList<>(List.of(observer, first, second)));
            EspionageService.refreshReports(viewer);
            assertEquals(3, databases.constructed().size());
            verify(databases.constructed().get(0)).saveFaction(first);
            verify(databases.constructed().get(1)).saveFaction(observer);
            verify(databases.constructed().get(2)).saveFaction(second);
            EspionageService.refreshReports(viewer);
            assertEquals(3, databases.constructed().size());
            for (Database database : databases.constructed()) verifyNoMoreInteractions(database);
        }
    }

    @Test
    void prestigeIsPublicAndWealthRankingUsesOnlyKnownMidpoints() {
        Player viewer = mock(Player.class);
        when(viewer.getName()).thenReturn("Viewer");
        Faction observer = faction("own"), estimated = faction("estimated"), hidden = faction("hidden");
        when(observer.isMemberIgnoreCase("Viewer")).thenReturn(true);
        when(observer.getWealth()).thenReturn(100.0);
        when(observer.getPrestige()).thenReturn(1.0);
        when(estimated.getPrestige()).thenReturn(5.0);
        when(hidden.getPrestige()).thenReturn(10.0);
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        observer.getEspionage().report("estimated", 1, day, () -> {
            var report = new IntelligenceReport();
            report.quality = "Detailed estimates";
            report.estimates.put("Wealth", new EspionageMath.Estimate(200, 800));
            return report;
        });
        var broad = observer.getEspionage().report("hidden", 1, day, () -> {
            var report = new IntelligenceReport();
            report.quality = "Detailed estimates";
            report.estimates.put("Wealth", new EspionageMath.Estimate(0, 1000));
            return report;
        });
        try (var factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getByMember("Viewer")).thenReturn(observer);
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new ArrayList<>(List.of(observer, estimated, hidden)));
            var ranker = new FactionRanker();
            assertEquals(500.0, ranker.visibleValue(viewer, estimated, RankType.WEALTH));
            assertNull(ranker.visibleValue(viewer, hidden, RankType.WEALTH));
            assertEquals(List.of(estimated, observer, hidden), ranker.getVisibleRankedList(viewer, RankType.WEALTH));
            assertEquals(1, ranker.getVisibleRank(viewer, estimated, RankType.WEALTH));
            assertNull(ranker.getVisibleRank(viewer, hidden, RankType.WEALTH));
            assertEquals(new EspionageMath.Estimate(0, 1000), broad.estimates.get("Wealth"));
            assertEquals(List.of(hidden, estimated, observer), ranker.getVisibleRankedList(viewer, RankType.PRESTIGE));
            verify(estimated, never()).getWealth();
            verify(hidden, never()).getWealth();
        }
    }

    @Test
    void staffRefreshRebuildsAllDirectionsAndPersistsEachFactionOnce() {
        Faction first = faction("first"), second = faction("second");
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        var oldFirst = first.getEspionage().report("second", 1, day, IntelligenceReport::new);
        var oldSecond = second.getEspionage().report("first", 1, day, IntelligenceReport::new);
        var oldRolls = first.getEspionage().rolls(day, new Random(1));
        try (var factions = mockStatic(FactionManager.class); var databases = mockConstruction(Database.class)) {
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new ArrayList<>(List.of(first, second)));
            assertEquals(2, EspionageService.regenerateReports());
            assertNotSame(oldFirst, first.getEspionage().cachedReport("second", 1, day));
            assertNotSame(oldSecond, second.getEspionage().cachedReport("first", 1, day));
            assertNotSame(oldRolls, first.getEspionage().rolls(day, new Random(1)));
            assertEquals(2, databases.constructed().size());
            verify(databases.constructed().get(0)).saveFaction(first);
            verify(databases.constructed().get(1)).saveFaction(second);
        }
    }

    @Test
    void sampledRostersImproveWithQualityAndPersistAsSnapshots() {
        List<String> members = java.util.stream.IntStream.range(0, 10).mapToObj(i -> "Character " + i + " — Guild").toList();
        assertEquals(2, EspionageService.sample(members, 0, new Random(1)).size());
        assertEquals(2, EspionageService.sample(members, 1, new Random(1)).size());
        assertEquals(4, EspionageService.sample(members, 30, new Random(1)).size());
        assertEquals(6, EspionageService.sample(members, 65, new Random(1)).size());
        var report = new IntelligenceReport();
        report.quality = "Detailed estimates";
        report.members = EspionageService.sample(members, 100, new Random(1));
        assertEquals(8, report.members.size());
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(report), IntelligenceReport.class);
        assertEquals(report.members, restored.members);
        assertNotEquals(report.members, EspionageService.sample(members, 100, new Random(10)));
    }
}
