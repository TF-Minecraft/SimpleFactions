package net.tfminecraft.simplefactions.vehicles.fees;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.utils.Formatter;
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
        var session = Members.session(player).orElse(null);
        if (session == null) { event.setCancelled(true); player.sendMessage("§cSelect a living character first."); return; }
        String vehicleTypeId = VehicleIntegrationListener.resolveVehicleTypeId(blueprint);
        String stationKey = stationKey(event.getStation());
        String scope = "build:" + stationKey;
        var payments = VehiclePayments.current();
        if (payments == null || !payments.ready() || payments.receipt(scope) != null || store.buildOwner(stationKey) != null || stationKey.equals("unknown")) {
            event.setCancelled(true); player.sendMessage("§cVehicle payment needs staff review or recovery."); return;
        }
        Quote quote = VehicleFeeService.quote(FeeKind.REGISTRATION_FEE, Members.key(player), vehicleTypeId);
        if (quote == null) {
            store.beginBuild(stationKey, session.ref().characterId().toString());
            try { store.saveDurably(); }
            catch (java.io.IOException error) { payments.fail(error); event.setCancelled(true); }
            return;
        }
        long now = System.currentTimeMillis();
        String target = scope + ":" + vehicleTypeId + ":" + session.ref().characterId()
                + ":" + quote.faction().getOrCreateMainGuild().getIncarnationId();
        if (!confirmations.confirm(player.getUniqueId(), target, quote.amount(), now)) {
            event.setCancelled(true);
            event.setKeepPlacement(true);
            confirmations.ask(player.getUniqueId(), target, quote.amount(), now);
            player.sendMessage(VehicleFeeMessages.registrationConfirm(quote, vehicleTypeId));
            return;
        }
        if (payments.charge(scope, "sf:vehicle:build:" + UUID.randomUUID(), session.ref(), quote) != Result.APPLIED) {
            event.setCancelled(true);
            player.sendMessage(VehicleFeeMessages.registrationUnaffordable(quote));
            return;
        }
        player.sendMessage(VehicleFeeMessages.registrationPaid(quote));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConstructionCancel(VehicleConstructionCancelEvent event) {
        String scope = "build:" + stationKey(event.getStation());
        var payments = VehiclePayments.current();
        var build = payments == null ? null : payments.receipt(scope);
        if (build == null) {
            if (store.buildOwner(stationKey(event.getStation())) != null && payments != null) payments.complete(scope, null);
            return;
        }
        var refund = payments.refund(scope);
        if (refund.result() != Result.APPLIED) return;
        double refunded = refund.amount();
        double shortfall = Formatter.formatDouble(net.tfminecraft.simplefactions.economy.Money.denars(build.cents()) - refunded);
        if (shortfall > 0.0 && SimpleFactions.getInstance() != null) {
            // The faction bank may be short or the deposit may have failed; either way staff are told.
            SimpleFactions.getInstance().getLogger().warning("Registration fee refund short by " + shortfall
                    + " for " + build.payer().characterId() + " from faction " + build.factionId());
        }
        Player payer = Members.online(build.payer().characterId().toString()).orElse(null);
        if (payer != null && payer.isOnline()) {
            if (refunded > 0.0) {
                payer.sendMessage(VehicleFeeMessages.registrationRefunded(refunded));
            }
            if (shortfall > 0.0) {
                payer.sendMessage(VehicleFeeMessages.registrationRefundShort(shortfall));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleConstruct(VehicleConstructEvent event) {
        String scope = "build:" + stationKey(event.getStation());
        var payments = VehiclePayments.current();
        var paid = payments == null ? null : payments.receipt(scope);
        if (event.getVehicle() == null || event.getVehicle().getUUID() == null || payments == null) return;
        String owner = paid != null ? paid.payer().characterId().toString() : store.buildOwner(stationKey(event.getStation()));
        if (owner == null || !payments.personal(event.getVehicle().getUUID(), event.getVehicle().getId(), UUID.fromString(owner))) return;
        store.setLastOwner(event.getVehicle().getUUID(), Members.key(UUID.fromString(owner)));
        payments.complete(scope, null);
    }

    /** The paying character, without an account fallback. */
    static UUID payerUuid(Player player) {
        return VehicleOwnershipQueries.characterId(player);
    }

    static String stationKey(ActiveStation station) {
        Location loc = station == null ? null : station.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return "unknown";
        }
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
