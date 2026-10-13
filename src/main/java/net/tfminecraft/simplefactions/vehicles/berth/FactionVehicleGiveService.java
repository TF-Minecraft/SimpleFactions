package net.tfminecraft.simplefactions.vehicles.berth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.VehicleGiveConsentRequest;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Outcome;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseService.Status;
import net.tfminecraft.simplefactions.identity.Members;

public final class FactionVehicleGiveService {
    private final FactionVehicleReleaseService releaseService;

    public FactionVehicleGiveService(FactionVehicleReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    public void offer(Player leader, Player recipient, Faction faction, String vehicleUuid, String vehicleTypeId) {
        if (leader == null || recipient == null || faction == null || vehicleUuid == null) {
            return;
        }
        var leaderCharacter = Members.session(leader).map(session -> session.ref()).orElse(null);
        var recipientCharacter = Members.session(recipient).map(session -> session.ref()).orElse(null);
        if (leaderCharacter == null || recipientCharacter == null) {
            leader.sendMessage(FactionVehicleReleaseMessages.ownershipUnavailable());
            return;
        }
        VehicleGiveConsentRequest request = new VehicleGiveConsentRequest(
                faction.getOrCreateMainGuild(),
                faction.getId(),
                vehicleUuid,
                vehicleTypeId,
                recipient.getUniqueId(),
                leader.getUniqueId(),
                leaderCharacter,
                recipientCharacter);
        RequestManager.addRequest(leader, recipient, request);
        // addRequest refuses when the recipient is already considering a request, even one for this vehicle.
        if (RequestManager.getRequest(recipient) != request) {
            return;
        }
        recipient.sendMessage(FactionVehicleReleaseMessages.givePrompt(Members.name(leader), vehicleTypeId));
        leader.sendMessage(FactionVehicleReleaseMessages.giveSent(Members.name(recipient)));
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
        if (!Members.session(recipient).map(session -> session.ref()).filter(req.getRecipientCharacter()::equals)
                .isPresent()) {
            recipient.sendMessage("§cThis request belongs to another character.");
            return;
        }
        Faction faction = FactionManager.getByString(req.getFactionId());
        if (faction == null || faction.getLeader() == null
                || !faction.getLeader().equals(req.getLeaderName())) {
            String expired = FactionVehicleReleaseMessages.giveExpired();
            recipient.sendMessage(expired);
            notifyLeader(req, expired);
            return;
        }

        Outcome outcome = releaseService.give(
                faction,
                req.getLeaderName(),
                req.getRecipientCharacter(),
                req.getVehicleUuid());
        if (outcome.status() != Status.OK) {
            String message = FactionVehicleReleaseMessages.forGive(outcome, Members.name(recipient));
            if (message != null) {
                recipient.sendMessage(message);
                notifyLeader(req, message);
            }
            return;
        }
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.recordVehicleOwner(req.getVehicleUuid(), Members.key(req.getRecipientCharacter()));
        }
        recipient.sendMessage(FactionVehicleReleaseMessages.giveSuccessRecipient(Members.display(req.getLeaderName())));
        notifyLeader(req, FactionVehicleReleaseMessages.giveSuccessLeader(Members.name(recipient)));
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
