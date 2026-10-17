package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
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
    void selectsTheOwnershipEventGuardAndRefusesAnUnknownProvider() {
        assertInstanceOf(VehicleClaimsOff.class, VehicleClaimGuards.create());
        assertThrows(IllegalStateException.class, () -> VehicleClaimGuards.create("missing.Modern"));
        assertThrows(IllegalStateException.class, () -> VehicleClaimGuards.create(BrokenEvent.class.getName()));
    }

    static final class BrokenEvent {
        static { if (true) throw new NoClassDefFoundError("missing dependency"); }
    }

    private static VehicleOwnershipChangeEvent claim(Player player, OwnershipReason reason) {
        return new VehicleOwnershipChangeEvent(player, mock(ActiveVehicle.class), reason, UUID.randomUUID(),
                null, false, null, null, "none", "player_A");
    }
}
