package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.tfminecraft.vehicleframework.api.OwnershipReason;
import net.tfminecraft.vehicleframework.events.VehicleOwnershipChangeEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

class VehicleClaimsOffTest {
    @Test
    void withoutCharacterOwnershipOnlyStaffMayClaim() {
        VehicleClaimsOff guard = new VehicleClaimsOff();
        Player player = mock(Player.class);
        Player staff = mock(Player.class);
        when(staff.hasPermission("simplefactions.admin")).thenReturn(true);
        VehicleOwnershipChangeEvent claim = claim(player, OwnershipReason.CLAIM);
        guard.onClaim(claim);
        VehicleOwnershipChangeEvent staffClaim = claim(staff, OwnershipReason.CLAIM);
        guard.onClaim(staffClaim);
        VehicleOwnershipChangeEvent other = claim(player, OwnershipReason.TAKEOVER);
        guard.onClaim(other);
        assertTrue(claim.isCancelled());
        verify(player).sendMessage(VehicleClaimsOff.MESSAGE);
        assertFalse(staffClaim.isCancelled());
        assertFalse(other.isCancelled());
    }

    @Test
    void registersTheOwnershipEventGuardFirstAndRefusesAnUnknownProvider() {
        PluginManager plugins = mock(PluginManager.class);
        Plugin plugin = mock(Plugin.class);
        VehicleClaimGuards.register(plugins, plugin);
        verify(plugins).registerEvents(any(VehicleClaimsOff.class), eq(plugin));
        assertThrows(IllegalStateException.class,
                () -> VehicleClaimGuards.register(plugins, plugin, "missing.Modern", "missing.Legacy"));
        assertThrows(IllegalStateException.class, () -> VehicleClaimGuards.register(plugins, plugin,
                BrokenEvent.class.getName(), NotAnEvent.class.getName()));
    }

    @Test
    void beforeVehicleFramework213TheLegacyClaimEventIsGuardedByName() throws Exception {
        PluginManager plugins = mock(PluginManager.class);
        Plugin plugin = mock(Plugin.class);
        VehicleClaimGuards.register(plugins, plugin, "missing.Modern", LegacyClaim.class.getName());
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(plugins).registerEvent(eq(LegacyClaim.class), any(Listener.class), eq(EventPriority.LOWEST),
                executor.capture(), eq(plugin), eq(true));
        Player player = mock(Player.class);
        Player staff = mock(Player.class);
        when(staff.hasPermission("simplefactions.admin")).thenReturn(true);
        LegacyClaim claim = new LegacyClaim(player);
        LegacyClaim staffClaim = new LegacyClaim(staff);
        LegacyClaim absent = new LegacyClaim(null);
        executor.getValue().execute(null, claim);
        executor.getValue().execute(null, staffClaim);
        executor.getValue().execute(null, absent);
        assertTrue(claim.isCancelled());
        verify(player).sendMessage(VehicleClaimGuards.MESSAGE);
        assertFalse(staffClaim.isCancelled());
        assertFalse(absent.isCancelled());
        VehicleClaimGuards.refuseLegacy(new PlainEvent());
        NoPlayerClaim unreadable = new NoPlayerClaim();
        VehicleClaimGuards.refuseLegacy(unreadable);
        assertFalse(unreadable.isCancelled());
    }

    static final class BrokenEvent {
        static { if (true) throw new NoClassDefFoundError("missing dependency"); }
    }

    static final class NotAnEvent {
    }

    public static class LegacyClaim extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private boolean cancelled;

        LegacyClaim(Player player) {
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    static final class NoPlayerClaim extends Event implements Cancellable {
        private boolean cancelled;
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }

    static final class PlainEvent extends Event {
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }

    private static VehicleOwnershipChangeEvent claim(Player player, OwnershipReason reason) {
        return new VehicleOwnershipChangeEvent(player, mock(ActiveVehicle.class), reason, UUID.randomUUID(),
                null, false, null, null, "none", "player_A");
    }
}
