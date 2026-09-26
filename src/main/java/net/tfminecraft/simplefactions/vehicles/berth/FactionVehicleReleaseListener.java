package net.tfminecraft.simplefactions.vehicles.berth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Outcome;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Status;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleReleaseSessionManager.Kind;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleReleaseSessionManager.VehicleReleaseSession;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class FactionVehicleReleaseListener implements Listener {
    private final VehicleReleaseSessionManager sessionManager;
    private final FactionVehicleReleaseService releaseService;
    private final FactionVehicleGiveService giveService;

    public FactionVehicleReleaseListener(
            VehicleReleaseSessionManager sessionManager,
            FactionVehicleReleaseService releaseService,
            FactionVehicleGiveService giveService) {
        this.sessionManager = sessionManager;
        this.releaseService = releaseService;
        this.giveService = giveService;
    }

    @EventHandler
    public void onVehiclePreInteract(VehiclePreInteractEvent event) {
        Player leader = event.getPlayer();
        if (leader == null) {
            return;
        }
        VehicleReleaseSession session = sessionManager.get(leader.getUniqueId());
        if (session == null) {
            return;
        }

        Faction faction = FactionManager.getByLeader(leader.getName());
        if (faction == null) {
            sessionManager.clear(leader.getUniqueId());
            leader.sendMessage(FactionVehicleReleaseMessages.notLeader());
            event.setCancelled(true);
            return;
        }

        ActiveVehicle vehicle = event.getVehicle();
        if (vehicle == null || vehicle.getUUID() == null) {
            event.setCancelled(true);
            return;
        }

        if (session.getKind() == Kind.GIVE) {
            handleGive(leader, faction, session, vehicle.getUUID(), event);
            return;
        }

        Outcome outcome = releaseService.take(faction, leader.getName(), vehicle.getUUID());
        String message = FactionVehicleReleaseMessages.forTake(outcome);
        if (message != null) {
            leader.sendMessage(message);
        }
        if (outcome.status() == Status.OK || outcome.status() == Status.IN_BATTLE
                || outcome.status() == Status.NOT_LEADER) {
            sessionManager.clear(leader.getUniqueId());
        }
        event.setCancelled(true);
    }

    private void handleGive(
            Player leader,
            Faction faction,
            VehicleReleaseSession session,
            String vehicleUuid,
            VehiclePreInteractEvent event) {
        Player recipient = Bukkit.getPlayerExact(session.getTargetName());
        if (recipient == null || !recipient.isOnline()
                || (session.getTargetUuid() != null && !session.getTargetUuid().equals(recipient.getUniqueId()))) {
            leader.sendMessage(FactionVehicleReleaseMessages.recipientOffline(session.getTargetName()));
            event.setCancelled(true);
            return;
        }

        Outcome outcome = releaseService.evaluateGive(
                faction, leader.getName(), recipient.getName(), vehicleUuid);
        if (outcome.status() != Status.OK) {
            String message = FactionVehicleReleaseMessages.forGive(outcome, recipient.getName());
            if (message != null) {
                leader.sendMessage(message);
            }
            if (outcome.status() == Status.IN_BATTLE || outcome.status() == Status.NOT_LEADER) {
                sessionManager.clear(leader.getUniqueId());
            }
            event.setCancelled(true);
            return;
        }

        giveService.offer(leader, recipient, faction, vehicleUuid, outcome.vehicleTypeId());
        sessionManager.clear(leader.getUniqueId());
        event.setCancelled(true);
    }
}
