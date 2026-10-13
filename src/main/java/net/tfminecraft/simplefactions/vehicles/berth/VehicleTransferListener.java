package net.tfminecraft.simplefactions.vehicles.berth;

import net.tfminecraft.simplefactions.identity.Members;



import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService.CanRegisterResult;
import net.tfminecraft.simplefactions.vehicles.berth.VehicleTransferSessionManager.VehicleTransferSession;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService.CanAddResult;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.UUID;

import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationBounds;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class VehicleTransferListener implements Listener {
    private final VehicleTransferSessionManager sessionManager;
    private final InstallationVehicleService installationVehicleService;
    private final VehicleTransferConsentService consentService;
    private final FactionVehiclePoolService poolService;

    public VehicleTransferListener(
            VehicleTransferSessionManager sessionManager,
            PlayerVehicleRegistry registry,
            InstallationVehicleService installationVehicleService,
            VehicleTransferConsentService consentService,
            FactionVehiclePoolService poolService) {
        this.sessionManager = sessionManager;
        this.installationVehicleService = installationVehicleService;
        this.consentService = consentService;
        this.poolService = poolService;
    }

    @EventHandler
    public void onVehiclePreInteract(VehiclePreInteractEvent event) {
        Player leader = event.getPlayer();
        if (leader == null) {
            return;
        }

        VehicleTransferSession session = sessionManager.get(leader.getUniqueId());
        if (session == null) {
            return;
        }

        Faction faction = FactionManager.getByLeader(Members.key(leader));
        if (faction == null) {
            sessionManager.clear(leader.getUniqueId());
            leader.sendMessage(VehicleTransferMessages.notLeader());
            event.setCancelled(true);
            return;
        }

        if (session.isPool()) {
            handlePoolTransfer(leader, faction, event.getVehicle(), event);
            return;
        }

        Installation installation = faction.getInstallationHandler().getById(session.getInstallationId());
        if (installation == null) {
            sessionManager.clear(leader.getUniqueId());
            leader.sendMessage(VehicleTransferMessages.unknownInstallation());
            event.setCancelled(true);
            return;
        }

        ActiveVehicle vehicle = event.getVehicle();
        if (vehicle == null || vehicle.getUUID() == null) {
            return;
        }

        UUID owner = otherOwner(leader, vehicle);
        if (owner != null) {
            handleOtherOwnerTransfer(leader, faction, installation, vehicle, owner, event);
            return;
        }

        CanRegisterResult result = installationVehicleService.canRegister(installation, vehicle);
        if (result != CanRegisterResult.OK) {
            String message = VehicleTransferMessages.forResult(
                    result, installation, vehicle, vehicle.getId());
            if (message != null) {
                leader.sendMessage(message);
            }
            event.setCancelled(true);
            return;
        }

        if (!installationVehicleService.tryRegister(
                installation, vehicle, faction, VehicleOwnershipQueries.characterId(leader))) {
            leader.sendMessage(VehicleTransferMessages.saveFailed());
            event.setCancelled(true);
            return;
        }
        sessionManager.clear(leader.getUniqueId());
        leader.sendMessage(VehicleTransferMessages.berthSuccess(installation));
        event.setCancelled(true);
    }

    private void handleOtherOwnerTransfer(
            Player leader,
            Faction faction,
            Installation installation,
            ActiveVehicle vehicle,
            UUID ownerCharacter,
            VehiclePreInteractEvent event) {
        Player owner = Members.online(Members.key(ownerCharacter)).orElse(null);
        if (owner == null || !owner.isOnline()) {
            leader.sendMessage(VehicleTransferMessages.ownerOffline());
            event.setCancelled(true);
            return;
        }

        int proximityBlocks = InstallationConfigLoader.getConsentProximityBlocks();
        double distance = InstallationBounds.horizontalDistanceBlocks(
                owner.getLocation().getBlockX(),
                owner.getLocation().getBlockZ(),
                vehicle.getLocation());
        if (vehicle.getLocation() == null
                || !java.util.Objects.equals(owner.getLocation().getWorld(), vehicle.getLocation().getWorld())
                || distance > proximityBlocks) {
            leader.sendMessage(VehicleTransferMessages.ownerTooFar(proximityBlocks));
            event.setCancelled(true);
            return;
        }

        CanRegisterResult result = installationVehicleService.canRegister(installation, vehicle);
        if (result != CanRegisterResult.OK) {
            String message = VehicleTransferMessages.forResult(
                    result, installation, vehicle, vehicle.getId());
            if (message != null) {
                leader.sendMessage(message);
            }
            event.setCancelled(true);
            return;
        }

        if (consentService.trySendConsentRequest(leader, owner, faction, installation, vehicle)) {
            sessionManager.clear(leader.getUniqueId());
        }
        event.setCancelled(true);
    }

    private void handlePoolTransfer(
            Player leader,
            Faction faction,
            ActiveVehicle vehicle,
            VehiclePreInteractEvent event) {
        if (vehicle == null || vehicle.getUUID() == null) {
            return;
        }

        UUID owner = otherOwner(leader, vehicle);
        if (owner != null) {
            handleOtherOwnerPoolTransfer(leader, faction, vehicle, owner, event);
            return;
        }

        CanAddResult result = poolService.canAdd(faction, vehicle);
        if (result != CanAddResult.OK) {
            String message = VehicleTransferMessages.forPoolResult(result, vehicle.getId(), faction);
            if (message != null) {
                leader.sendMessage(message);
            }
            event.setCancelled(true);
            return;
        }

        if (!poolService.tryRegister(faction, vehicle, VehicleOwnershipQueries.characterId(leader))) {
            leader.sendMessage(VehicleTransferMessages.saveFailed());
            event.setCancelled(true);
            return;
        }
        sessionManager.clear(leader.getUniqueId());
        leader.sendMessage(VehicleTransferMessages.poolSuccess());
        event.setCancelled(true);
    }

    private void handleOtherOwnerPoolTransfer(
            Player leader,
            Faction faction,
            ActiveVehicle vehicle,
            UUID ownerCharacter,
            VehiclePreInteractEvent event) {
        Player owner = Members.online(Members.key(ownerCharacter)).orElse(null);
        if (owner == null || !owner.isOnline()) {
            leader.sendMessage(VehicleTransferMessages.ownerOffline());
            event.setCancelled(true);
            return;
        }

        int proximityBlocks = InstallationConfigLoader.getConsentProximityBlocks();
        double distance = InstallationBounds.horizontalDistanceBlocks(
                owner.getLocation().getBlockX(),
                owner.getLocation().getBlockZ(),
                vehicle.getLocation());
        if (vehicle.getLocation() == null
                || !java.util.Objects.equals(owner.getLocation().getWorld(), vehicle.getLocation().getWorld())
                || distance > proximityBlocks) {
            leader.sendMessage(VehicleTransferMessages.ownerTooFar(proximityBlocks));
            event.setCancelled(true);
            return;
        }

        CanAddResult result = poolService.canAdd(faction, vehicle);
        if (result != CanAddResult.OK) {
            String message = VehicleTransferMessages.forPoolResult(result, vehicle.getId(), faction);
            if (message != null) {
                leader.sendMessage(message);
            }
            event.setCancelled(true);
            return;
        }

        if (consentService.trySendPoolConsentRequest(leader, owner, faction, vehicle)) {
            sessionManager.clear(leader.getUniqueId());
        }
        event.setCancelled(true);
    }

    /** The character owning the vehicle when it is not the leader's current one, else null. */
    private static UUID otherOwner(Player leader, ActiveVehicle vehicle) {
        String tag = vehicle.getOwnerData() == null ? null : vehicle.getOwnerData().getOwner();
        UUID owner = OwnerTags.characterId(tag).orElse(null);
        return owner == null || owner.equals(VehicleOwnershipQueries.characterId(leader)) ? null : owner;
    }
}
