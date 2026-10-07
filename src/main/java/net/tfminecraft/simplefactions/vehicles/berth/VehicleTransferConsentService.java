package net.tfminecraft.simplefactions.vehicles.berth;



import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleService.CanRegisterResult;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService.CanAddResult;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.VehicleTransferConsentRequest;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class VehicleTransferConsentService {
    private final InstallationVehicleService installationVehicleService;
    private final VehicleTransferSessionManager sessionManager;
    private final FactionVehiclePoolService poolService;

    public VehicleTransferConsentService(
            InstallationVehicleService installationVehicleService,
            PlayerVehicleRegistry registry,
            VehicleTransferSessionManager sessionManager) {
        this(
                installationVehicleService,
                sessionManager,
                new FactionVehiclePoolService(registry, new InstallationVehicleOwnerSync(registry)));
    }

    public VehicleTransferConsentService(
            InstallationVehicleService installationVehicleService,
            VehicleTransferSessionManager sessionManager,
            FactionVehiclePoolService poolService) {
        this.installationVehicleService = installationVehicleService;
        this.sessionManager = sessionManager;
        this.poolService = poolService;
    }

    public void sendConsentRequest(Player leader, Player owner, Faction faction,
            Installation installation, ActiveVehicle vehicle) {
        trySendConsentRequest(leader, owner, faction, installation, vehicle);
    }

    public boolean trySendConsentRequest(Player leader, Player owner, Faction faction,
            Installation installation, ActiveVehicle vehicle) {
        if (leader == null || owner == null || faction == null
                || installation == null || vehicle == null) {
            return false;
        }
        VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
                faction.getOrCreateMainGuild(), installation.getId(), installation.getName(),
                vehicle.getUUID(), vehicle.getId(), owner.getUniqueId(), leader.getUniqueId());
        RequestManager.addRequest(leader, owner, request);
        if (RequestManager.getRequest(owner) != request) return false;
        owner.sendMessage(VehicleTransferMessages.consentPrompt(
                leader.getName(), vehicle.getId(), installation.getName()));
        leader.sendMessage(VehicleTransferMessages.consentSent(owner.getName()));
        return true;
    }

    public void sendPoolConsentRequest(Player leader, Player owner, Faction faction, ActiveVehicle vehicle) {
        trySendPoolConsentRequest(leader, owner, faction, vehicle);
    }

    public boolean trySendPoolConsentRequest(Player leader, Player owner, Faction faction, ActiveVehicle vehicle) {
        if (leader == null || owner == null || faction == null || vehicle == null) {
            return false;
        }
        VehicleTransferConsentRequest request = new VehicleTransferConsentRequest(
                faction.getOrCreateMainGuild(), null, "faction vehicle pool", vehicle.getUUID(),
                vehicle.getId(), owner.getUniqueId(), leader.getUniqueId(), true);
        RequestManager.addRequest(leader, owner, request);
        if (RequestManager.getRequest(owner) != request) return false;
        owner.sendMessage(VehicleTransferMessages.poolConsentPrompt(leader.getName(), vehicle.getId()));
        leader.sendMessage(VehicleTransferMessages.consentSent(owner.getName()));
        return true;
    }

    public void acceptRequest(Player owner) {
        if (!(RequestManager.getRequest(owner) instanceof VehicleTransferConsentRequest req)) {
            return;
        }

        if (!owner.getUniqueId().equals(req.getOwnerUuid())) {
            owner.sendMessage("§cYou cannot accept this request.");
            return;
        }

        if (req.timedOut()) {
            notifyExpired(req, owner);
            return;
        }

        Faction faction = resolveProposerFaction(req);
        if (faction == null) {
            owner.sendMessage(VehicleTransferMessages.consentExpired());
            return;
        }

        if (req.isPool()) {
            acceptPoolRequest(owner, req, faction);
            return;
        }

        Installation installation = faction.getInstallationHandler().getById(req.getInstallationId());
        if (installation == null) {
            owner.sendMessage(VehicleTransferMessages.unknownInstallation());
            return;
        }

        InstallationVehicleService.VehicleBerthTarget vehicle = resolveBerthTarget(req.getVehicleUuid());
        ActiveVehicle activeVehicle = resolveVehicle(req.getVehicleUuid());
        if (changedHands(owner, req)) {
            return;
        }

        CanRegisterResult result = installationVehicleService.canRegister(installation, vehicle);
        if (result != CanRegisterResult.OK) {
            String typeId = activeVehicle != null ? activeVehicle.getId() : req.getVehicleTypeId();
            String message = VehicleTransferMessages.forResult(
                    result,
                    installation,
                    activeVehicle,
                    typeId);
            if (message != null) {
                owner.sendMessage(message);
            }
            notifyProposer(req, message);
            return;
        }

        if (!installationVehicleService.tryRegister(installation, vehicle, faction, req.getOwnerUuid())) {
            String message = VehicleTransferMessages.saveFailed();
            owner.sendMessage(message);
            notifyProposer(req, message);
            return;
        }
        sessionManager.clear(req.getProposerLeaderUuid());

        String success = VehicleTransferMessages.berthSuccess(installation);
        owner.sendMessage(success);
        notifyProposer(req, success);
    }

    private void acceptPoolRequest(Player owner, VehicleTransferConsentRequest req, Faction faction) {
        FactionVehiclePoolService.PoolTarget vehicle = resolvePoolTarget(req.getVehicleUuid());
        ActiveVehicle activeVehicle = resolveVehicle(req.getVehicleUuid());
        if (changedHands(owner, req)) {
            return;
        }
        CanAddResult result = poolService.canAdd(faction, vehicle);
        if (result != CanAddResult.OK) {
            String typeId = activeVehicle != null ? activeVehicle.getId() : req.getVehicleTypeId();
            String message = VehicleTransferMessages.forPoolResult(result, typeId, faction);
            if (message != null) {
                owner.sendMessage(message);
            }
            notifyProposer(req, message);
            return;
        }

        if (!poolService.tryRegister(faction, vehicle, req.getOwnerUuid())) {
            String message = VehicleTransferMessages.saveFailed();
            owner.sendMessage(message);
            notifyProposer(req, message);
            return;
        }
        sessionManager.clear(req.getProposerLeaderUuid());
        String success = VehicleTransferMessages.poolSuccess();
        owner.sendMessage(success);
        notifyProposer(req, success);
    }

    /** Refuses the request if the vehicle was sold or re-claimed after the request was sent. */
    private boolean changedHands(Player owner, VehicleTransferConsentRequest req) {
        String current = VehicleOwnershipQueries.playerNameFromOwner(currentOwnerEntry(req.getVehicleUuid()));
        if (current == null || current.equalsIgnoreCase(owner.getName())) {
            // An unloaded or unowned vehicle is reported by the validation that follows.
            return false;
        }
        String message = VehicleTransferMessages.ownerChanged();
        owner.sendMessage(message);
        notifyProposer(req, message);
        return true;
    }

    public void notifyExpired(VehicleTransferConsentRequest req, Player owner) {
        if (req == null) {
            return;
        }
        String message = VehicleTransferMessages.consentExpired();
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(message);
        }
        notifyProposer(req, message);
    }

    InstallationVehicleService.VehicleBerthTarget resolveBerthTarget(String vehicleUuid) {
        ActiveVehicle vehicle = resolveVehicle(vehicleUuid);
        if (vehicle == null) {
            return null;
        }
        return new InstallationVehicleService.VehicleBerthTarget() {
            @Override
            public String getVehicleUuid() {
                return vehicle.getUUID();
            }

            @Override
            public String getVehicleTypeId() {
                return vehicle.getId();
            }

            @Override
            public org.bukkit.Location getLocation() {
                return vehicle.getLocation();
            }

            @Override
            public net.tfminecraft.vehicleframework.data.OwnerData getOwnerData() {
                return vehicle.getOwnerData();
            }
        };
    }

    FactionVehiclePoolService.PoolTarget resolvePoolTarget(String vehicleUuid) {
        ActiveVehicle vehicle = resolveVehicle(vehicleUuid);
        if (vehicle == null) {
            return null;
        }
        return new FactionVehiclePoolService.PoolTarget() {
            @Override
            public String getVehicleUuid() {
                return vehicle.getUUID();
            }

            @Override
            public String getVehicleTypeId() {
                return vehicle.getId();
            }

            @Override
            public net.tfminecraft.vehicleframework.data.OwnerData getOwnerData() {
                return vehicle.getOwnerData();
            }
        };
    }

    String currentOwnerEntry(String vehicleUuid) {
        ActiveVehicle vehicle = resolveVehicle(vehicleUuid);
        return vehicle == null || vehicle.getOwnerData() == null ? null : vehicle.getOwnerData().getOwner();
    }

    ActiveVehicle resolveVehicle(String vehicleUuid) {
        if (vehicleUuid == null) {
            return null;
        }
        return VehicleFramework.getVehicleManager().get(vehicleUuid);
    }

    private static Faction resolveProposerFaction(VehicleTransferConsentRequest req) {
        Player proposer = Bukkit.getPlayer(req.getProposerLeaderUuid());
        Faction faction = FactionManager.getByString(req.getDestinationFactionId());
        if (proposer != null && faction != null && proposer.getName().equals(faction.getLeader())) {
            return faction;
        }
        return null;
    }

    private static void notifyProposer(VehicleTransferConsentRequest req, String message) {
        Player proposer = Bukkit.getPlayer(req.getProposerLeaderUuid());
        if (proposer != null && proposer.isOnline()) {
            proposer.sendMessage(message);
        }
    }
}
