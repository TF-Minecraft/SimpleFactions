package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import net.tfminecraft.vehicleframework.api.OwnershipReason;
import net.tfminecraft.vehicleframework.events.VehicleOwnerClaimedEvent;
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
    void legacyBridgeKeepsTheOldStaffAndMissingPlayerPolicy() {
        LegacyVehicleClaimsOff guard = new LegacyVehicleClaimsOff();
        Player player = mock(Player.class);
        Player staff = mock(Player.class);
        when(staff.hasPermission("simplefactions.admin")).thenReturn(true);
        VehicleOwnerClaimedEvent claim = new VehicleOwnerClaimedEvent(player, null, "none", "player_A");
        VehicleOwnerClaimedEvent allowed = new VehicleOwnerClaimedEvent(staff, null, "none", "player_Staff");
        VehicleOwnerClaimedEvent absent = new VehicleOwnerClaimedEvent(null, null, "none", "player_X");
        guard.onClaim(claim);
        guard.onClaim(allowed);
        guard.onClaim(absent);
        assertTrue(claim.isCancelled());
        verify(player).sendMessage(VehicleClaimsOff.MESSAGE);
        assertFalse(allowed.isCancelled());
        assertFalse(absent.isCancelled());
    }

    @Test
    void selectsModernBeforeLegacyAndRefusesAnUnknownProvider() {
        String legacy = VehicleOwnerClaimedEvent.class.getName();
        assertInstanceOf(VehicleClaimsOff.class, VehicleClaimGuards.create());
        assertInstanceOf(LegacyVehicleClaimsOff.class, VehicleClaimGuards.create("missing.Modern", legacy));
        assertInstanceOf(LegacyVehicleClaimsOff.class, VehicleClaimGuards.create(BrokenEvent.class.getName(), legacy));
        assertThrows(IllegalStateException.class, () -> VehicleClaimGuards.create("missing.Modern", "missing.Legacy"));
    }

    static final class BrokenEvent {
        static { if (true) throw new NoClassDefFoundError("missing dependency"); }
    }

    private static VehicleOwnershipChangeEvent claim(Player player, OwnershipReason reason) {
        return new VehicleOwnershipChangeEvent(player, mock(ActiveVehicle.class), reason, UUID.randomUUID(),
                null, false, null, null, "none", "player_A");
    }
}
