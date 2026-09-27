package net.tfminecraft.simplefactions.vehicles.fees;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.vehicles.VehicleIntegrationListener;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore.PaidBuild;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vfbuilders.events.BeginVehicleConstructionEvent;
import net.tfminecraft.vfbuilders.events.VehicleConstructEvent;
import net.tfminecraft.vfbuilders.events.VehicleConstructionCancelEvent;

/**
 * Charges the registration fee when a build starts, which is where the builder becomes the
 * owner. The first placement click shows the fee; a second click pays it and starts the build.
 * Needs VFBuilders 2.1.0 or later for the confirm click and refunds.
 */
public final class VehicleRegistrationFeeListener implements Listener {
    private final VehicleFeeStore store;
    private final VehicleFeeConfirmations confirmations;
    private final Runnable saver;

    public VehicleRegistrationFeeListener(VehicleFeeStore store, VehicleFeeConfirmations confirmations, Runnable saver) {
        this.store = store;
        this.confirmations = confirmations;
        this.saver = saver;
    }

    // After the slot-limit check (HIGH), so nobody pays for a build that is then refused.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBeginVehicleConstruction(BeginVehicleConstructionEvent event) {
        Player player = event.getConstructor();
        Blueprint blueprint = event.getBlueprint();
        if (player == null || blueprint == null) {
            return;
        }
        String vehicleTypeId = VehicleIntegrationListener.resolveVehicleTypeId(blueprint);
        Quote quote = VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, player.getName(), vehicleTypeId);
        if (quote == null) {
            return;
        }
        String stationKey = stationKey(event.getStation());
        long now = System.currentTimeMillis();
        String target = "build:" + stationKey + ":" + vehicleTypeId;
        if (!confirmations.confirm(player.getUniqueId(), target, quote.amount(), now)) {
            event.setCancelled(true);
            event.setKeepPlacement(true);
            confirmations.ask(player.getUniqueId(), target, quote.amount(), now);
            player.sendMessage(VehicleFeeMessages.registrationConfirm(quote, vehicleTypeId));
            return;
        }
        UUID payer = payerUuid(player);
        if (!VehicleFeeService.collect(payer, quote)) {
            event.setCancelled(true);
            player.sendMessage(VehicleFeeMessages.registrationUnaffordable(quote));
            return;
        }
        store.putPaidBuild(stationKey, new PaidBuild(payer, quote.faction().getId(), quote.amount()));
        saver.run();
        player.sendMessage(VehicleFeeMessages.registrationPaid(quote));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConstructionCancel(VehicleConstructionCancelEvent event) {
        PaidBuild build = store.takePaidBuild(stationKey(event.getStation()));
        if (build == null) {
            return;
        }
        saver.run();
        double refunded = VehicleFeeService.refund(
                build.payerUuid(), build.factionId(), FeeKind.REGISTRATION_FEE, build.amount());
        Player payer = Bukkit.getPlayer(build.payerUuid());
        if (payer != null && payer.isOnline() && refunded > 0.0) {
            payer.sendMessage(VehicleFeeMessages.registrationRefunded(refunded));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleConstruct(VehicleConstructEvent event) {
        boolean changed = store.takePaidBuild(stationKey(event.getStation())) != null;
        if (event.getVehicle() != null && event.getConstructorUuid() != null) {
            String owner = event.getConstructor() != null
                    ? event.getConstructor().getName()
                    : VehicleOwnershipQueries.resolvePlayerName(event.getConstructorUuid());
            if (owner != null) {
                store.setLastOwner(event.getVehicle().getUUID(), owner);
                changed = true;
            }
        }
        if (changed) {
            saver.run();
        }
    }

    static UUID payerUuid(Player player) {
        UUID resolved = VehicleFeeService.resolve(player.getName());
        return resolved != null ? resolved : player.getUniqueId();
    }

    static String stationKey(ActiveStation station) {
        Location loc = station == null ? null : station.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return "unknown";
        }
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
