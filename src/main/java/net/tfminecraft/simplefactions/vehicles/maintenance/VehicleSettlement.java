package net.tfminecraft.simplefactions.vehicles.maintenance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.economy.settlement.RealmSettlementChanges;
import net.tfminecraft.simplefactions.economy.settlement.SettlementGuilds;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.tlibs.character.CharacterInfo;

/** Adds vehicle costs last to the realm posting; no live money or maintenance state changes here. */
public final class VehicleSettlement {
    private VehicleSettlement() {}

    public static void calculate(Map<String, Long> balances, Map<String, Long> legs, RealmSettlementChanges changes) {
        calculate(balances, legs, changes, System.currentTimeMillis());
    }

    public static void calculate(Map<String, Long> balances, Map<String, Long> legs, RealmSettlementChanges changes, long now) {
        calculate(balances, legs, changes, SimpleFactions.getVehicleRegistry(), SimpleFactions.getInstance().getVehicleMaintenanceStore(), now);
    }

    static void calculate(Map<String, Long> balances, Map<String, Long> legs, RealmSettlementChanges changes,
            PlayerVehicleRegistry registry, VehicleMaintenanceStore maintenance, long now) {
        Map<String, Long> unpaid = new LinkedHashMap<>(maintenance.snapshot());
        JsonArray notices = new JsonArray();
        for (var vehicle : VehicleOwnershipQueries.allPersonalVehicles(registry)) {
            long upkeep = Money.cents(VehiclesConfigLoader.getUpkeep(vehicle.typeId()));
            if (upkeep <= 0) {
                unpaid.remove(vehicle.vehicle());
                continue;
            }
            var record = registry.getByVehicleUuid(vehicle.vehicle()).orElse(null);
            String payer = record == null || record.getPlayerUuid() == null ? null : record.getPlayerUuid().toString();
            // Waiting for the dead payer's estate: no new unpaid day, and an earlier one stays.
            if (UpkeepPause.paused(payer)) continue;
            var payments = VehiclePayments.current();
            if (payments != null && payments.blockedVehicle(vehicle.vehicle())) {
                markUnpaid(unpaid, notices, vehicle.vehicle(), vehicle.typeId(), payer, null, now);
                continue;
            }
            var character = Members.info(payer).filter(CharacterInfo::alive);
            if (character.isEmpty()) {
                markUnpaid(unpaid, notices, vehicle.vehicle(), vehicle.typeId(), payer, null, now);
                continue;
            }
            var ref = character.get().ref();
            var tax = VehicleFeeService.quote(FeeKind.VEHICLE_TAX, payer, vehicle.typeId());
            long fee = tax == null ? 0 : Money.cents(tax.amount());
            long total = Math.addExact(upkeep, fee);
            String account = Denars.bank(ref);
            if ((tax != null && SettlementGuilds.capital(tax.faction()).getBank() == null) || balance(balances, legs, account) < total) {
                markUnpaid(unpaid, notices, vehicle.vehicle(), vehicle.typeId(), payer, null, now);
                continue;
            }
            transfer(balances, legs, account, Denars.MINT, upkeep);
            income(changes, ref.characterId(), PlayerCashflow.VEHICLE_UPKEEP, -upkeep);
            if (tax != null) {
                var guild = SettlementGuilds.capital(tax.faction());
                var saved = changes.guilds.stream().filter(value -> guild.getIncarnationId().equals(value.incarnation))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Vehicle tax guild absent from settlement"));
                transfer(balances, legs, account, Denars.guild(guild.getIncarnationId()), fee);
                saved.vehicleFeeIncome = Money.denars(Math.addExact(Money.cents(saved.vehicleFeeIncome), fee));
                income(changes, ref.characterId(), PlayerCashflow.VEHICLE_TAX, -fee);
            }
            unpaid.remove(vehicle.vehicle());
        }
        for (var record : registry.getAll()) {
            if (record.getMode() == OwnershipMode.PERSONAL) continue;
            long upkeep = Money.cents(VehiclesConfigLoader.getUpkeep(record.getVehicleTypeId()));
            var faction = FactionVehiclePoolService.payingFaction(record);
            if (upkeep <= 0 || (faction == null && record.getMode() == OwnershipMode.INSTALLATION)) {
                unpaid.remove(record.getVehicleUuid());
                continue;
            }
            String account = faction == null ? null : Denars.guild(SettlementGuilds.capital(faction).getIncarnationId());
            if (faction == null || SettlementGuilds.capital(faction).getBank() == null || balance(balances, legs, account) < upkeep) {
                markUnpaid(unpaid, notices, record.getVehicleUuid(), record.getVehicleTypeId(), null,
                        faction == null ? null : faction.getId(), now);
                continue;
            }
            transfer(balances, legs, account, Denars.MINT, upkeep);
            unpaid.remove(record.getVehicleUuid());
        }
        changes.vehicleChanges.add("maintenance", JsonUtil.GSON.toJsonTree(unpaid));
        changes.vehicleChanges.add("notices", notices);
    }

    public static void apply(JsonObject changes) throws IOException {
        var plugin = SimpleFactions.getInstance();
        apply(changes, plugin.getDataFolder().toPath(), plugin.getVehicleMaintenanceStore());
    }

    static void apply(JsonObject changes, Path folder, VehicleMaintenanceStore maintenance) throws IOException {
        Map<String, Long> unpaid = new LinkedHashMap<>();
        if (!changes.has("maintenance") || !changes.get("maintenance").isJsonObject())
            throw new IOException("Missing vehicle maintenance after-values");
        try {
            changes.getAsJsonObject("maintenance").entrySet().forEach(entry -> unpaid.put(entry.getKey(), entry.getValue().getAsLong()));
        } catch (RuntimeException error) {
            throw new IOException("Invalid vehicle maintenance after-values", error);
        }
        DurableJson.write(folder.resolve("Cache/vehicle_maintenance.json"), unpaid);
        maintenance.replaceAll(unpaid);
        if (changes.has("notices")) {
            for (var value : changes.getAsJsonArray("notices")) {
                JsonObject notice = value.getAsJsonObject();
                String character = notice.has("character") ? notice.get("character").getAsString() : null;
                if (notice.has("faction")) {
                    var faction = net.tfminecraft.simplefactions.managers.FactionManager.getByString(notice.get("faction").getAsString());
                    if (faction != null) character = faction.getLeader();
                }
                Members.online(character).ifPresent(player -> player.sendMessage(
                        "§cCould not pay vehicle upkeep (" + notice.get("type").getAsString() + "): insufficient bank balance."));
            }
        }
    }

    private static long balance(Map<String, Long> balances, Map<String, Long> legs, String account) {
        return balances.computeIfAbsent(account, key -> Math.addExact(Denars.balance(key), legs.getOrDefault(key, 0L)));
    }

    private static void transfer(Map<String, Long> balances, Map<String, Long> legs, String from, String to, long amount) {
        balances.computeIfPresent(from, (key, value) -> Math.subtractExact(value, amount));
        balances.computeIfPresent(to, (key, value) -> Math.addExact(value, amount));
        legs.merge(from, -amount, Math::addExact);
        legs.merge(to, amount, Math::addExact);
    }

    private static void income(RealmSettlementChanges changes, UUID character, PlayerCashflow kind, long cents) {
        changes.playerIncome.computeIfAbsent(character, ignored -> new LinkedHashMap<>())
                .merge(kind.name(), Money.denars(cents), Double::sum);
    }

    private static void markUnpaid(Map<String, Long> unpaid, JsonArray notices, String vehicle, String type,
            String character, String faction, long now) {
        unpaid.putIfAbsent(vehicle, now);
        JsonObject notice = new JsonObject();
        notice.addProperty("type", type);
        if (character != null) notice.addProperty("character", character);
        if (faction != null) notice.addProperty("faction", faction);
        notices.add(notice);
    }
}
