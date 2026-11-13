package net.tfminecraft.simplefactions.vehicles.upkeep;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.VehicleGiveConsentRequest;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleGiveService;
import net.tfminecraft.simplefactions.vehicles.berth.FactionVehicleReleaseMessages;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.GiveOutcome;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.GiveStatus;
import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * The leader gives an upkeep-only vehicle to a member. As with a pool gift, the member must
 * accept ({@code /faction accept}), since they take on its upkeep; the leader giving one to
 * themselves needs nobody's consent.
 */
public final class UpkeepGifts {
    private UpkeepGifts() {}

    /** Offers the vehicle; true when the click mode is done (offered, given, or no longer the leader). */
    static boolean offer(UpkeepVehicleService service, Player leader, UpkeepHolder holder, String recipientKey,
            String vehicle) {
        String leaderKey = Members.key(leader);
        String name = Members.display(recipientKey);
        CharacterRef recipient = Members.ref(recipientKey).orElse(null);
        boolean self = recipientKey.equals(leaderKey);
        GiveOutcome check = self ? service.give(holder, leaderKey, recipient, vehicle)
                : service.evaluateGive(holder, leaderKey, recipient, vehicle);
        if (check.status() != GiveStatus.OK) {
            leader.sendMessage(UpkeepVehicleMessages.forGive(check, holder, name));
            return check.status() == GiveStatus.NOT_LEADER;
        }
        if (self) {
            SimpleFactions plugin = SimpleFactions.getInstance();
            if (plugin != null) plugin.recordVehicleOwner(vehicle, recipientKey);
            leader.sendMessage(UpkeepVehicleMessages.forGive(check, holder, name));
            return true;
        }
        Player online = Members.online(recipientKey).orElse(null);
        CharacterRef leaderRef = Members.session(leader).map(session -> session.ref()).orElse(null);
        CharacterRef playing = online == null ? null : Members.session(online).map(session -> session.ref()).orElse(null);
        if (leaderRef == null || playing == null || !playing.equals(recipient)) {
            leader.sendMessage(FactionVehicleReleaseMessages.recipientOffline(name));
            return false;
        }
        Faction faction = holder.faction();
        VehicleGiveConsentRequest request = new VehicleGiveConsentRequest(holder.bank(),
                faction == null ? null : faction.getId(), vehicle, check.vehicleTypeId(), online.getUniqueId(),
                leader.getUniqueId(), leaderRef, playing, holder);
        RequestManager.addRequest(leader, online, request);
        // addRequest refuses (and tells the leader) while the member is considering another request.
        if (RequestManager.getRequest(online) != request) return false;
        online.sendMessage(UpkeepVehicleMessages.givePrompt(holder, Members.name(leader), check.vehicleTypeId()));
        leader.sendMessage(FactionVehicleReleaseMessages.giveSent(name));
        return true;
    }

    /** {@code /faction accept} on an upkeep-only gift; the recipient's character was checked already. */
    public static void accept(Player recipient, VehicleGiveConsentRequest request) {
        UpkeepHolder holder = request.getUpkeepHolder();
        if (!holder.isLeader(request.getLeaderName())) {
            String expired = FactionVehicleReleaseMessages.giveExpired();
            recipient.sendMessage(expired);
            FactionVehicleGiveService.notifyLeader(request, expired);
            return;
        }
        GiveOutcome outcome = SimpleFactions.getInstance().getUpkeepVehicleService()
                .give(holder, request.getLeaderName(), request.getRecipientCharacter(), request.getVehicleUuid());
        String result = UpkeepVehicleMessages.forGive(outcome, holder, Members.name(recipient));
        if (outcome.status() != GiveStatus.OK) {
            recipient.sendMessage(result);
            FactionVehicleGiveService.notifyLeader(request, result);
            return;
        }
        SimpleFactions.getInstance().recordVehicleOwner(request.getVehicleUuid(), Members.key(request.getRecipientCharacter()));
        recipient.sendMessage(UpkeepVehicleMessages.received(holder, Members.display(request.getLeaderName()),
                outcome.vehicleTypeId()));
        FactionVehicleGiveService.notifyLeader(request, result);
    }
}
