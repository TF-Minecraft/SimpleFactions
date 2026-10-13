package net.tfminecraft.simplefactions.vehicles.owners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.tfminecraft.vehicleframework.events.VehicleOwnerClaimedEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

class VehicleClaimsOffTest {
    @Test
    void withoutCharacterOwnershipOnlyStaffMayClaim() {
        VehicleClaimsOff guard = new VehicleClaimsOff();
        Player player = mock(Player.class);
        Player staff = mock(Player.class);
        when(staff.hasPermission("simplefactions.admin")).thenReturn(true);
        ActiveVehicle vehicle = mock(ActiveVehicle.class);

        VehicleOwnerClaimedEvent claim = new VehicleOwnerClaimedEvent(player, vehicle, "none", "player_Steve");
        guard.onClaim(claim);
        VehicleOwnerClaimedEvent staffClaim = new VehicleOwnerClaimedEvent(staff, vehicle, "none", "player_Admin");
        guard.onClaim(staffClaim);
        VehicleOwnerClaimedEvent nobody = new VehicleOwnerClaimedEvent(null, vehicle, "none", "player_x");
        guard.onClaim(nobody);

        assertTrue(claim.isCancelled());
        verify(player).sendMessage(VehicleClaimsOff.MESSAGE);
        assertFalse(staffClaim.isCancelled());
        assertFalse(nobody.isCancelled());
    }
}
