package net.tfminecraft.simplefactions.estate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.estate.EstateFallback;
import net.tfminecraft.tlibs.character.estate.EstateParticipant;
import net.tfminecraft.tlibs.character.estate.EstateState;
import net.tfminecraft.tlibs.character.estate.Estates;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimpleServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

/** Every estate call goes through the probed bridge, which never throws (E5, R13). */
class EstateBridgeTest {
    final Plugin plugin = mock(Plugin.class);
    final Server server = mock(Server.class);
    final SimpleServicesManager services = new SimpleServicesManager();
    final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    final Logger logger = Logger.getLogger("EstateBridgeTest");
    final java.util.List<LogRecord> logged = new java.util.ArrayList<>();
    final UUID character = UUID.randomUUID();
    MockedStatic<Estates> estates;
    MockedStatic<org.bukkit.Bukkit> bukkit;

    @BeforeEach void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getServicesManager()).thenReturn(services);
        when(server.getScheduler()).thenReturn(scheduler);
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        estates = mockStatic(Estates.class);
        // Registering a service announces it through the server's plugin manager.
        bukkit = mockStatic(org.bukkit.Bukkit.class);
        bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        EstateBridge.useProbe(null);
    }

    @AfterEach void close() {
        bukkit.close();
        estates.close();
        EstateBridge.useProbe(null);
        for (Handler handler : logger.getHandlers()) logger.removeHandler(handler);
    }

    @Test void probeFindsTheTLibsEstateApiOnceAndMissesAnOlderOne() {
        assertTrue(EstateBridge.available());
        assertTrue(EstateBridge.available());
        assertFalse(EstateBridge.probe("net.tfminecraft.tlibs.character.estate.Missing"));
    }

    @Test void vehiclesWaitWhileTheLatestDeathsEstateIsOpenHeldOrDecided() {
        for (EstateState state : EstateState.values()) {
            estates.when(() -> Estates.stateOf(character)).thenReturn(Optional.of(state));
            boolean waits = state == EstateState.OPEN || state == EstateState.HELD || state == EstateState.DECIDED;
            assertEquals(waits, EstateBridge.vehiclesAwaitEstate(character), state.name());
        }
        estates.when(() -> Estates.stateOf(character)).thenReturn(Optional.empty());
        assertFalse(EstateBridge.vehiclesAwaitEstate(character), "no estate service or no estate");
        assertFalse(EstateBridge.vehiclesAwaitEstate(null));
    }

    @Test void heirsMayInheritWhileTheLatestDeathsEstateIsOpenOrDecided() {
        for (EstateState state : EstateState.values()) {
            estates.when(() -> Estates.stateOf(character)).thenReturn(Optional.of(state));
            boolean may = state == EstateState.OPEN || state == EstateState.DECIDED;
            assertEquals(may, EstateBridge.heirMayInherit(character), state.name());
        }
        estates.when(() -> Estates.stateOf(character)).thenReturn(Optional.empty());
        assertFalse(EstateBridge.heirMayInherit(character), "no estate service or no estate");
        assertFalse(EstateBridge.heirMayInherit(null));
    }

    @Test void estateFailuresNeverEscapeTheBridge() {
        estates.when(() -> Estates.stateOf(character)).thenThrow(new IllegalStateException("off the main thread"));
        assertFalse(EstateBridge.vehiclesAwaitEstate(character));
        assertFalse(EstateBridge.heirMayInherit(character));
        estates.when(() -> Estates.stateOf(character)).thenThrow(new NoSuchMethodError("older TLibs"));
        assertFalse(EstateBridge.vehiclesAwaitEstate(character));
        assertFalse(EstateBridge.heirMayInherit(character));
        when(server.getServicesManager()).thenThrow(new IllegalStateException("disabled"));
        EstateBridge.registerFallback(plugin);
        EstateBridge.registerVehicles(plugin, Path.of("data"), new PlayerVehicleRegistry(), new VehicleFeeStore());
        assertEquals(2, logged.stream().filter(record -> record.getLevel() == Level.WARNING).count());
    }

    @Test void olderTLibsTakesNoPartInEstates() {
        EstateBridge.useProbe(false);
        estates.when(() -> Estates.stateOf(character)).thenReturn(Optional.of(EstateState.OPEN));
        assertFalse(EstateBridge.vehiclesAwaitEstate(character));
        assertFalse(EstateBridge.heirMayInherit(character));
        EstateBridge.registerFallback(plugin);
        EstateBridge.registerVehicles(plugin, Path.of("data"), new PlayerVehicleRegistry(), new VehicleFeeStore());
        assertNull(services.load(EstateFallback.class));
        assertNull(services.load(EstateParticipant.class));
        assertTrue(logged.getFirst().getMessage().contains("no estate API"));
    }

    @Test void registersTheFallbackAndTheVehiclesParticipantWithItsHourlyReconcile() {
        EstateBridge.registerFallback(plugin);
        assertInstanceOf(GuildEstateFallback.class, services.load(EstateFallback.class));
        EstateBridge.registerVehicles(plugin, Path.of("data"), new PlayerVehicleRegistry(), new VehicleFeeStore());
        EstateParticipant participant = services.load(EstateParticipant.class);
        assertEquals("simplefactions-vehicles", participant.id());
        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(200L), eq(72000L));
    }
}
