package net.tfminecraft.simplefactions.vehicles.handover;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeConfirmations;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeMessages;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService.Outcome;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverService.Status;
import net.tfminecraft.simplefactions.vehicles.handover.VehicleHandoverSessionManager.Session;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

/** The vehicle click after /faction vehicle handover. A transfer fee is shown before the offer goes out. */
public final class VehicleHandoverListener implements Listener {
    private final VehicleHandoverSessionManager sessions;
    private final VehicleHandoverService service;
    private final VehicleFeeConfirmations confirmations;

    public VehicleHandoverListener(
            VehicleHandoverSessionManager sessions,
            VehicleHandoverService service,
            VehicleFeeConfirmations confirmations) {
        this.sessions = sessions;
        this.service = service;
        this.confirmations = confirmations;
    }

    @EventHandler
    public void onVehiclePreInteract(VehiclePreInteractEvent event) {
        Player owner = event.getPlayer();
        Session session = owner == null ? null : sessions.get(owner.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        ActiveVehicle vehicle = event.getVehicle();
        if (vehicle == null || vehicle.getUUID() == null) {
            return;
        }
        Player recipient = Bukkit.getPlayerExact(session.recipientName());
        if (recipient == null || !recipient.isOnline() || !recipient.getUniqueId().equals(session.recipientUuid())) {
            owner.sendMessage(VehicleHandoverMessages.recipientOffline(session.recipientName()));
            sessions.clear(owner.getUniqueId());
            return;
        }
        Outcome outcome = service.evaluate(owner.getName(), recipient.getName(), vehicle.getUUID());
        if (outcome.status() != Status.OK) {
            owner.sendMessage(VehicleHandoverMessages.forOutcome(outcome, recipient.getName()));
            if (outcome.status() != Status.NOT_OWNER) {
                sessions.clear(owner.getUniqueId());
            }
            return;
        }
        Quote quote = VehicleFeeService.quote(FeeKind.TRANSFER_FEE, owner.getName(), outcome.vehicleTypeId());
        if (quote != null) {
            long now = System.currentTimeMillis();
            String target = "handover:" + vehicle.getUUID() + ":" + recipient.getUniqueId();
            if (!confirmations.confirm(owner.getUniqueId(), target, quote.amount(), now)) {
                confirmations.ask(owner.getUniqueId(), target, quote.amount(), now);
                owner.sendMessage(VehicleFeeMessages.handoverConfirm(quote, recipient.getName()));
                return;
            }
        }
        sessions.clear(owner.getUniqueId());
        service.offer(owner, recipient, vehicle.getUUID(), outcome.vehicleTypeId());
    }
}
