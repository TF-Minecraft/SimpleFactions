package net.tfminecraft.simplefactions.vehicles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import net.tfminecraft.vehicleframework.data.OwnerData;
import net.tfminecraft.vehicleframework.events.*;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.berth.*;
import net.tfminecraft.simplefactions.vehicles.handover.*;
import net.tfminecraft.simplefactions.vehicles.maintenance.*;
import net.tfminecraft.simplefactions.vehicles.upkeep.*;
import net.tfminecraft.simplefactions.vehicles.fees.*;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.request.*;
import org.bukkit.entity.Player;
import java.util.UUID;

class ServerVehicleAuditTest {
    @Test void clickAndRepairListenersIgnoreServerVehicles() {
        assertTrue(OwnerTags.isServer("SeRvEr")); assertFalse(OwnerTags.isServer((String)null));
        assertFalse(OwnerTags.isCharacter("server")); assertFalse(OwnerTags.isUnowned("server"));
        ActiveVehicle vehicle = mock(ActiveVehicle.class);
        OwnerData owner = new OwnerData(); owner.setOwner("server");
        when(vehicle.getOwnerData()).thenReturn(owner);
        VehiclePreInteractEvent click = new VehiclePreInteractEvent(mock(Player.class), vehicle);
        new VehicleTransferListener(null, null, null, null, null).onVehiclePreInteract(click);
        new FactionVehicleReleaseListener(null, null, null).onVehiclePreInteract(click);
        new VehicleHandoverListener(null, null, null).onVehiclePreInteract(click);
        new UpkeepVehicleListener(null).onVehiclePreInteract(click);
        new VehicleMaintenancePayListener(null, null).onVehiclePreInteract(click);
        assertFalse(click.isCancelled());
        VehicleRepairStartEvent repair = mock(VehicleRepairStartEvent.class);
        when(repair.getVehicle()).thenReturn(vehicle);
        new VehicleMaintenanceRepairListener(null).onRepairStart(repair);
        verify(repair, never()).setCancelled(true);
    }

    @Test void removesOnlyVehicleOffersAndConfirmations() {
        Player sender = mock(Player.class);
        Player recipient = mock(Player.class);
        for (Request request : new Request[] {mock(VehicleTransferConsentRequest.class),
                mock(VehicleGiveConsentRequest.class), mock(VehicleHandoverRequest.class)}) {
            if (request instanceof VehicleTransferConsentRequest value) when(value.getVehicleUuid()).thenReturn("v");
            if (request instanceof VehicleGiveConsentRequest value) when(value.getVehicleUuid()).thenReturn("v");
            if (request instanceof VehicleHandoverRequest value) when(value.getVehicleUuid()).thenReturn("v");
            RequestManager.addRequest(sender, recipient, request);
            RequestManager.forgetVehicle("other"); assertTrue(RequestManager.hasRequest(recipient));
            RequestManager.forgetVehicle("v"); assertFalse(RequestManager.hasRequest(recipient));
        }
        RequestManager.addRequest(sender, recipient, mock(Request.class));
        RequestManager.forgetVehicle("v"); assertTrue(RequestManager.hasRequest(recipient));
        RequestManager.remove(recipient);
        VehicleFeeConfirmations confirmations = new VehicleFeeConfirmations();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        confirmations.ask(first, "claim:v:actor", 1, 0);
        confirmations.ask(second, "handover:v:actor", 1, 0);
        confirmations.ask(third, "other", 1, 0);
        confirmations.forgetVehicle("v");
        assertFalse(confirmations.confirm(first, "claim:v:actor", 1, 1));
        assertFalse(confirmations.confirm(second, "handover:v:actor", 1, 1));
        assertTrue(confirmations.confirm(third, "other", 1, 1));
    }
}
