package net.tfminecraft.simplefactions.vehicles.berth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.VehicleGiveConsentRequest;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Outcome;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Status;

public final class FactionVehicleGiveService {
    private final FactionVehicleReleaseService releaseService;

    public FactionVehicleGiveService(FactionVehicleReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    public void offer(Player leader, Player recipient, Faction faction, String vehicleUuid, String vehicleTypeId) {
        if (leader == null || recipient == null || faction == null || vehicleUuid == null) {
            return;
        }
        RequestManager.addRequest(
                leader,
                recipient,
                new VehicleGiveConsentRequest(
                        faction.getOrCreateMainGuild(),
                        faction.getId(),
                        vehicleUuid,
                        vehicleTypeId,
                        recipient.getUniqueId(),
                        leader.getUniqueId(),
                        leader.getName()));
        if (!(RequestManager.getRequest(recipient) instanceof VehicleGiveConsentRequest stored)
                || !vehicleUuid.equals(stored.getVehicleUuid())) {
            return;
        }
        recipient.sendMessage(FactionVehicleReleaseMessages.givePrompt(leader.getName(), vehicleTypeId));
        leader.sendMessage(FactionVehicleReleaseMessages.giveSent(recipient.getName()));
    }

    public void acceptRequest(Player recipient) {
        if (!(RequestManager.getRequest(recipient) instanceof VehicleGiveConsentRequest req)) {
            return;
        }
        if (recipient.getUniqueId() == null || !recipient.getUniqueId().equals(req.getRecipientUuid())) {
            recipient.sendMessage("§cYou cannot accept this request.");
            return;
        }
        if (req.timedOut()) {
            notifyExpired(req, recipient);
            return;
        }
        Faction faction = FactionManager.getByString(req.getFactionId());
        if (faction == null || faction.getLeader() == null
                || !faction.getLeader().equalsIgnoreCase(req.getLeaderName())) {
            String expired = FactionVehicleReleaseMessages.giveExpired();
            recipient.sendMessage(expired);
            notifyLeader(req, expired);
            return;
        }

        Outcome outcome = releaseService.give(
                faction,
                req.getLeaderName(),
                recipient.getName(),
                req.getVehicleUuid());
        if (outcome.status() != Status.OK) {
            String message = FactionVehicleReleaseMessages.forGive(outcome, recipient.getName());
            if (message != null) {
                recipient.sendMessage(message);
                notifyLeader(req, message);
            }
            return;
        }
        recipient.sendMessage(FactionVehicleReleaseMessages.giveSuccessRecipient(req.getLeaderName()));
        notifyLeader(req, FactionVehicleReleaseMessages.giveSuccessLeader(recipient.getName()));
    }

    public void notifyExpired(VehicleGiveConsentRequest req, Player recipient) {
        if (req == null) {
            return;
        }
        String message = FactionVehicleReleaseMessages.giveExpired();
        if (recipient != null && recipient.isOnline()) {
            recipient.sendMessage(message);
        }
        notifyLeader(req, message);
    }

    private static void notifyLeader(VehicleGiveConsentRequest req, String message) {
        if (message == null || req.getLeaderUuid() == null) {
            return;
        }
        Player leader = Bukkit.getPlayer(req.getLeaderUuid());
        if (leader != null && leader.isOnline()) {
            leader.sendMessage(message);
        }
    }
}
