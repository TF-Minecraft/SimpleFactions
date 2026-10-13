package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.events.FactionLeaderChangedEvent;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

class LeaderStampsTest {
    private final PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
    private final UUID kira = UUID.randomUUID();
    private FakeOwnerApi api;
    private List<Faction> previousFactions;
    private Faction red;

    @BeforeEach
    void setUp() {
        api = FakeOwnerApi.install();
        previousFactions = FactionManager.factions;
        FactionManager.factions = new ArrayList<>();
        red = faction("red", kira.toString());
        LeaderStamps.enable(true);
    }

    @AfterEach
    void tearDown() {
        FactionManager.factions = previousFactions;
    }

    private Faction faction(String id, String leader) {
        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn(id);
        when(faction.getLeader()).thenReturn(leader);
        FactionManager.factions.add(faction);
        return faction;
    }

    private PlayerVehicleRecord pool(String vehicle, String faction) {
        PlayerVehicleRecord record = new PlayerVehicleRecord(UUID.randomUUID(), vehicle, "gun", OwnershipMode.POOL,
                null, faction);
        registry.register(record);
        return record;
    }

    @Test
    void theStampIsTheLeadersCharacterOrNobody() {
        assertEquals("char_" + kira, LeaderStamps.expectedStamp(red));
        assertEquals("char_none", LeaderStamps.expectedStamp(faction("dummy", "dummy_4")));
        assertEquals("char_none", LeaderStamps.expectedStamp(faction("vacant", "")));
        assertEquals("char_none", LeaderStamps.expectedStamp(faction("unset", null)));
        assertEquals("char_none", LeaderStamps.expectedStamp(null));
        assertEquals("char_" + kira, LeaderStamps.expected(pool("gun", "red")));
        assertEquals("char_none", LeaderStamps.expected(pool("orphan", "gone")));
    }

    @Test
    void onlyInstallationAndPoolRowsAreFactionRows() {
        assertTrue(LeaderStamps.isFactionRow(pool("gun", "red")));
        assertTrue(LeaderStamps.isFactionRow(new PlayerVehicleRecord(null, "ship", "ship", OwnershipMode.INSTALLATION,
                "port", "red")));
        assertFalse(LeaderStamps.isFactionRow(new PlayerVehicleRecord(null, "cart", "cart", OwnershipMode.PERSONAL, null)));
        assertFalse(LeaderStamps.isFactionRow(null));
        assertFalse(LeaderStamps.isStale(null, "anything"));
        assertTrue(LeaderStamps.isStale(pool("gun", "red"), "char_" + UUID.randomUUID()));
        assertFalse(LeaderStamps.isStale(pool("gun", "red"), "char_" + kira));
    }

    @Test
    void aRestampWritesOnlyWhenTheVehicleCarriesSomethingElse() {
        api.stored("gun", "gun", "char_" + kira).stored("stale", "gun", "player_Old", "char_x;y;Crew");

        assertEquals(Result.OK, LeaderStamps.restamp(pool("gun", "red")));
        assertEquals(Result.OK, LeaderStamps.restamp(pool("stale", "red")));
        assertEquals(Result.NOT_FOUND, LeaderStamps.restamp(pool("missing", "red")));

        assertEquals(List.of("stale char_" + kira + " RESTAMP"), api.writes);
        assertEquals(List.of("char_x;y;Crew"), api.vehicles.get("stale").current().orElseThrow().whitelist(),
                "A restamp keeps the crew");
    }

    @Test
    void aLeaderChangeRestampsThatFactionsRowsOnlyOnceTheStageHasFinished() {
        UUID next = UUID.randomUUID();
        faction("blue", UUID.randomUUID().toString());
        pool("gun", "red");
        pool("other", "blue");
        InstallationHandler handler = mock(InstallationHandler.class);
        when(handler.getById("port")).thenReturn(new Installation("port", "Port", InstallationKind.PORT, 1, 0, 0, 0L));
        when(red.getInstallationHandler()).thenReturn(handler);
        registry.register(new PlayerVehicleRecord(null, "legacy", "ship", OwnershipMode.INSTALLATION, "port", null));
        registry.register(new PlayerVehicleRecord(null, "cart", "cart", OwnershipMode.PERSONAL, null));
        api.stored("gun", "gun", "char_" + kira).stored("other", "gun", "char_" + kira)
                .stored("legacy", "ship", "char_" + kira).stored("cart", "cart", "char_" + kira);
        when(red.getLeader()).thenReturn(next.toString());

        LeaderStamps.enable(false);
        LeaderStamps.onLeaderChanged(red, registry);
        assertEquals(List.of(), api.writes, "Changes before the stage finishes are left to the boot reconcile");

        LeaderStamps.enable(true);
        LeaderStamps.onLeaderChanged(red, registry);
        LeaderStamps.onLeaderChanged(null, registry);
        LeaderStamps.onLeaderChanged(red, null);
        assertEquals(List.of("gun char_" + next + " RESTAMP", "legacy char_" + next + " RESTAMP"),
                api.writes.stream().sorted().toList());
        assertEquals("char_" + kira, api.owner("other"));
        assertTrue(LeaderStamps.enabled());
    }

    @Test
    void aDeletedFactionsRemainingRowsLoseTheirLeader() {
        SimpleFactions plugin = mock(SimpleFactions.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
            pool("gun", "red");
            pool("lost", "red");
            api.stored("gun", "gun", "char_" + kira);

            LeaderStamps.onFactionDeleted(red);

            assertEquals("char_none", api.owner("gun"));
            verify(logger).warning(contains("lost (NOT_FOUND)"));
            LeaderStamps.enable(false);
            LeaderStamps.onFactionDeleted(red);
            LeaderStamps.enable(true);
            LeaderStamps.onFactionDeleted(null);
            verify(logger, times(1)).warning(any(String.class));
        }
        LeaderStamps.onFactionDeleted(red);
    }

    @Test
    void theBootReconcileStampsEveryFactionRowAndAbortsOnAFailedWrite() throws IOException {
        pool("gun", "red");
        pool("gone", "red");
        registry.register(new PlayerVehicleRecord(null, "cart", "cart", OwnershipMode.PERSONAL, null));
        api.stored("gun", "gun", "player_Old").stored("cart", "cart", "player_Old");

        LeaderStamps.reconcile(registry);
        assertEquals(List.of("gun char_" + kira + " RESTAMP"), api.writes);

        api.stored("gun", "gun", "player_Old");
        api.forced = Result.SAVE_FAILED;
        IOException failure = assertThrows(IOException.class, () -> LeaderStamps.reconcile(registry));
        assertTrue(failure.getMessage().contains("SAVE_FAILED"));
    }

    @Test
    void aStaleClickSchedulesOneRestampPerVehicleForTheNextTick() {
        SimpleFactions plugin = mock(SimpleFactions.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PlayerVehicleRecord gun = pool("gun", "red");
        api.stored("gun", "gun", "player_Old");
        try (MockedStatic<SimpleFactions> sf = mockStatic(SimpleFactions.class);
                MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            sf.when(SimpleFactions::getInstance).thenReturn(plugin);
            sf.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            LeaderStamps.scheduleRestamp(gun);
            LeaderStamps.scheduleRestamp(gun);
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(1)).runTask(any(org.bukkit.plugin.Plugin.class), task.capture());
            task.getValue().run();
            assertEquals("char_" + kira, api.owner("gun"));

            LeaderStamps.scheduleRestamp(gun);
            verify(scheduler, times(2)).runTask(any(org.bukkit.plugin.Plugin.class), task.capture());
            registry.unregister("gun");
            task.getValue().run();
            LeaderStamps.scheduleRestamp(gun);
            verify(scheduler, times(3)).runTask(any(org.bukkit.plugin.Plugin.class), task.capture());
            LeaderStamps.enable(false);
            task.getValue().run();
            LeaderStamps.scheduleRestamp(gun);
            verify(scheduler, times(3)).runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
            assertEquals(1, api.writes.size());
        }
        LeaderStamps.enable(true);
        LeaderStamps.scheduleRestamp(gun);
    }

    @Test
    void factionLeaderChangesAreAnnouncedOnlyWhenTheLeaderReallyChanges() {
        PluginManager plugins = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class));
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            FactionLeaderChangedEvent.fire(mock(Faction.class), "a", "b");
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            Faction faction = mock(Faction.class);
            FactionLeaderChangedEvent.fire(faction, "a", "a");
            FactionLeaderChangedEvent.fire(faction, null, null);
            verify(plugins, never()).callEvent(any(Event.class));

            FactionLeaderChangedEvent.fire(faction, "a", "b");
            ArgumentCaptor<FactionLeaderChangedEvent> fired = ArgumentCaptor.forClass(FactionLeaderChangedEvent.class);
            verify(plugins).callEvent(fired.capture());
            assertEquals(faction, fired.getValue().getFaction());
            assertEquals("a", fired.getValue().getPreviousLeader());
            assertEquals("b", fired.getValue().getNewLeader());
            assertEquals(FactionLeaderChangedEvent.getHandlerList(), fired.getValue().getHandlers());

            bukkit.when(Bukkit::getPluginManager).thenReturn(null);
            FactionLeaderChangedEvent.fire(faction, "b", "c");
            bukkit.when(Bukkit::getServer).thenReturn(null);
            FactionLeaderChangedEvent.fire(faction, "c", "d");
            verify(plugins, times(1)).callEvent(any(Event.class));
        }
    }
}
