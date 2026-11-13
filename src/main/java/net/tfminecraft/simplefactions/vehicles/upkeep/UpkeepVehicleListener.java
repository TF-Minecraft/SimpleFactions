package net.tfminecraft.simplefactions.vehicles.upkeep;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.installation.InstallationBounds;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferMessages;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepClickSessions.Session;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Awaiting;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepRequests.Pending;
import net.tfminecraft.simplefactions.vehicles.upkeep.UpkeepVehicleService.AddResult;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;

/**
 * The click after an upkeep-only command. A leader's own vehicle moves in at once; someone
 * else's waits for its owner, who must be online and near it, as for the faction pool. An
 * owner's request waits for the leader. A failed check keeps the click mode armed.
 */
public final class UpkeepVehicleListener implements Listener {
    private final UpkeepVehicleService service;

    public UpkeepVehicleListener(UpkeepVehicleService service) {
        this.service = service;
    }

    @EventHandler
    public void onVehiclePreInteract(VehiclePreInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Session session = UpkeepClickSessions.get(player.getUniqueId());
        if (session == null) return;
        UpkeepTarget target = UpkeepTarget.of(event.getVehicle());
        if (target == null) return;
        event.setCancelled(true);
        switch (session.mode()) {
            case ADD -> add(player, session.holder(), target, event);
            case REQUEST -> request(player, session.holder(), target);
            case GIVE -> give(player, session, target);
        }
    }

    private boolean refused(Player player, UpkeepHolder holder, UpkeepTarget target) {
        AddResult result = service.canAdd(holder, target);
        if (result == AddResult.OK) return false;
        player.sendMessage(UpkeepVehicleMessages.forAdd(result, holder, holder.vehicles(service.registry()).size()));
        return true;
    }

    private void add(Player leader, UpkeepHolder holder, UpkeepTarget target, VehiclePreInteractEvent event) {
        if (!holder.isLeader(Members.key(leader))) {
            UpkeepClickSessions.disarm(leader.getUniqueId());
            leader.sendMessage(UpkeepVehicleMessages.notLeader(holder.kind()));
            return;
        }
        if (refused(leader, holder, target)) return;
        UUID owner = OwnerTags.characterId(target.owner()).orElseThrow();
        if (!owner.equals(VehicleOwnershipQueries.characterId(leader))) {
            askOwner(leader, holder, target, owner, event);
            return;
        }
        if (!service.register(holder, target, owner)) {
            leader.sendMessage(UpkeepVehicleMessages.saveFailed());
            return;
        }
        UpkeepClickSessions.disarm(leader.getUniqueId());
        leader.sendMessage(UpkeepVehicleMessages.addedForLeader(holder, target.typeId()));
    }

    private void askOwner(Player leader, UpkeepHolder holder, UpkeepTarget target, UUID owner,
            VehiclePreInteractEvent event) {
        Player online = Members.online(Members.key(owner)).orElse(null);
        CharacterRef ref = Members.ref(Members.key(owner)).orElse(null);
        if (online == null || ref == null) {
            leader.sendMessage(VehicleTransferMessages.ownerOffline());
            return;
        }
        int proximity = InstallationConfigLoader.getConsentProximityBlocks();
        Location at = event.getVehicle().getLocation();
        if (at == null || !Objects.equals(online.getLocation().getWorld(), at.getWorld())
                || InstallationBounds.horizontalDistanceBlocks(online.getLocation().getBlockX(),
                        online.getLocation().getBlockZ(), at) > proximity) {
            leader.sendMessage(VehicleTransferMessages.ownerTooFar(proximity));
            return;
        }
        Pending pending = new Pending(holder, target.vehicle(), target.typeId(), ref, Members.key(leader),
                Awaiting.OWNER, UpkeepRequests.expiry());
        if (!UpkeepRequests.add(pending)) {
            leader.sendMessage(UpkeepVehicleMessages.alreadyPending());
            return;
        }
        UpkeepClickSessions.disarm(leader.getUniqueId());
        online.sendMessage(UpkeepVehicleMessages.ownerPrompt(pending, Members.name(leader)));
        leader.sendMessage(UpkeepVehicleMessages.ownerAsked(Members.name(online)));
    }

    private void request(Player owner, UpkeepHolder holder, UpkeepTarget target) {
        CharacterRef ref = Members.session(owner).map(session -> session.ref()).orElse(null);
        if (ref == null || !OwnerTags.names(target.owner(), ref.characterId())) {
            owner.sendMessage(UpkeepVehicleMessages.notYourVehicle());
            return;
        }
        if (refused(owner, holder, target)) return;
        Pending pending = new Pending(holder, target.vehicle(), target.typeId(), ref, null, Awaiting.LEADER,
                UpkeepRequests.expiry());
        if (!UpkeepRequests.add(pending)) {
            owner.sendMessage(UpkeepVehicleMessages.alreadyPending());
            return;
        }
        UpkeepClickSessions.disarm(owner.getUniqueId());
        Player leader = Members.online(holder.leader()).orElse(null);
        if (leader != null) leader.sendMessage(UpkeepVehicleMessages.leaderPrompt(pending, Members.name(owner)));
        owner.sendMessage(UpkeepVehicleMessages.requestSent(holder, target.typeId(), leader != null));
    }

    private void give(Player leader, Session session, UpkeepTarget target) {
        if (UpkeepGifts.offer(service, leader, session.holder(), session.recipient(), target.vehicle())) {
            UpkeepClickSessions.disarm(leader.getUniqueId());
        }
    }
}
