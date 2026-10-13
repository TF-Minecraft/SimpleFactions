package net.tfminecraft.simplefactions.vehicles.fees;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.economy.EconomyRuntime;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleRegistryPersistence;
import net.tfminecraft.simplefactions.economy.OperationJournal;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.tlibs.character.CharacterRef;

/** Durable vehicle payment intents. An unfinished physical handover/build stays quarantined by scope. */
public final class VehiclePayments {
    public record Receipt(String id, CharacterRef payer, String factionId, UUID guildIncarnation, FeeKind kind, long cents) {}
    public record Refund(Result result, double amount) {}
    private static VehiclePayments active;
    private final Path folder;
    private final VehicleFeeStore store;
    private final VehicleMaintenanceStore maintenance;
    private final OperationJournal journal;
    private boolean ready;

    public VehiclePayments(Path folder, VehicleFeeStore store, VehicleMaintenanceStore maintenance) {
        this.folder = folder;
        this.store = store;
        this.maintenance = maintenance;
        journal = new OperationJournal(folder.resolve("vehicle-payments"));
    }

    public static void startup(Path folder, VehicleFeeStore store, VehicleMaintenanceStore maintenance) throws IOException {
        active = new VehiclePayments(folder, store, maintenance);
        active.recover();
    }
    public static VehiclePayments current() { return active; }
    public static void stop() { active = null; }

    public void recover() throws IOException {
        ready = false;
        journal.recover(this::apply);
        // Legacy paid builds already belong to imported treasuries. Capture their incarnation once.
        for (var entry : store.pendingBuilds().entrySet()) {
            String scope = "build:" + entry.getKey();
            if (store.receipt(scope) != null) continue;
            var old = entry.getValue();
            var payer = Members.ref(old.payerUuid().toString()).orElseThrow(() -> new IOException("Unresolved legacy vehicle refund payer"));
            var faction = FactionManager.getByString(old.factionId());
            if (faction == null) throw new IOException("Unresolved legacy vehicle refund treasury");
            Guild guild = faction.getGuildHandler().getGuild(faction.getId());
            if (guild == null) throw new IOException("Missing legacy vehicle refund treasury");
            store.putReceipt(scope, new Receipt("sf:vehicle:legacy:" + entry.getKey(), payer, old.factionId(),
                    guild.getIncarnationId(), FeeKind.REGISTRATION_FEE, Money.cents(old.amount())));
        }
        store.saveDurably();
        ready = true;
    }

    public Receipt receipt(String scope) { return store.receipt(scope); }
    public boolean ready() { return ready; }
    public boolean blockedVehicle(String vehicle) { return receipt("claim:" + vehicle) != null || receipt("handover:" + vehicle) != null; }
    public void fail(Throwable error) { ready = false; EconomyRuntime.failClosed(error); }
    public boolean personal(String vehicle, String type, UUID character, UUID account) {
        if (!ready) return false;
        try {
            VehicleRegistryPersistence.personal(folder, SimpleFactions.getVehicleRegistry(), vehicle, type, character, account);
            return true;
        } catch (IOException | RuntimeException error) { fail(error); return false; }
    }

    /** Durably sets a vehicle's row: the one it had before a failed owner change, or none (null). */
    public boolean replaceRow(String vehicle, net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord previous) {
        try {
            VehicleRegistryPersistence.replace(folder, SimpleFactions.getVehicleRegistry(), vehicle, previous);
            return true;
        } catch (IOException | RuntimeException error) { fail(error); return false; }
    }

    public Result charge(String scope, String operationId, CharacterRef payer, VehicleFeeService.Quote quote) {
        return charge(scope, operationId, payer, quote, System.currentTimeMillis());
    }

    /** {@code at} is part of the replayable intent, so a retried operation must pass the same time. */
    public Result charge(String scope, String operationId, CharacterRef payer, VehicleFeeService.Quote quote, long at) {
        if (!ready) return Result.UNAVAILABLE;
        if (store.receipt(scope) != null) return Result.CONFLICT;
        if (payer == null || quote == null || quote.faction().getBank() == null) return Result.REFUSED;
        Guild guild = quote.faction().getGuildHandler().getGuild(quote.faction().getId());
        if (guild == null) return Result.REFUSED;
        long cents = Money.cents(quote.amount());
        if (cents <= 0) return Result.REFUSED;
        Receipt receipt = new Receipt(operationId, payer, quote.faction().getId(), guild.getIncarnationId(), quote.kind(), cents);
        JsonObject changes = new JsonObject();
        changes.addProperty("action", "charge");
        changes.addProperty("scope", scope);
        // When it was paid, so a rollback can list fees paid after the vehicle owner migration.
        changes.addProperty("at", at);
        changes.add("receipt", JsonUtil.GSON.toJsonTree(receipt));
        bookkeeping(changes, receipt, guild, cents);
        return execute(new OperationJournal.Intent(operationId, "vehicle:" + quote.kind().name(),
                List.of(new Leg(Denars.bank(payer), -cents), new Leg(Denars.guild(receipt.guildIncarnation()), cents)), changes));
    }

    public Refund refund(String scope) {
        if (!ready) return new Refund(Result.UNAVAILABLE, 0);
        Receipt receipt = store.receipt(scope);
        if (receipt == null) return new Refund(Result.REFUSED, 0);
        long cents;
        try { cents = Math.min(receipt.cents(), Math.max(0L, Denars.balance(Denars.guild(receipt.guildIncarnation())))); }
        catch (RuntimeException error) { fail(error); return new Refund(Result.UNAVAILABLE, 0); }
        if (cents == 0) return new Refund(complete(scope, null) ? Result.APPLIED : Result.UNAVAILABLE, 0);
        JsonObject changes = new JsonObject();
        changes.addProperty("action", "refund");
        changes.addProperty("scope", scope);
        changes.add("receipt", JsonUtil.GSON.toJsonTree(receipt));
        bookkeeping(changes, receipt, guild(receipt), -cents);
        Result result = execute(new OperationJournal.Intent(receipt.id() + ":refund", "vehicle:refund",
                List.of(new Leg(Denars.guild(receipt.guildIncarnation()), -cents), new Leg(Denars.bank(receipt.payer()), cents)), changes));
        return new Refund(result, result == Result.APPLIED ? Money.denars(cents) : 0);
    }

    public Result maintain(String id, CharacterRef payer, String vehicle, boolean bank, long cents) {
        if (!ready) return Result.UNAVAILABLE;
        if (payer == null || !maintenance.isUnpaid(vehicle)) return Result.REFUSED;
        JsonObject changes = new JsonObject();
        changes.addProperty("action", "maintenance");
        var after = new LinkedHashMap<>(maintenance.snapshot());
        after.remove(vehicle);
        changes.add("maintenance", JsonUtil.GSON.toJsonTree(after));
        if (cents <= 0) {
            try { applyMaintenance(changes); return Result.APPLIED; }
            catch (IOException error) { fail(error); return Result.UNAVAILABLE; }
        }
        return execute(new OperationJournal.Intent(id, "vehicle:maintenance",
                List.of(new Leg(bank ? Denars.bank(payer) : Denars.pouch(payer), -cents), new Leg(Denars.MINT, cents)), changes));
    }

    /** Called only after a successful physical action; a failed save leaves the scope blocked on restart. */
    public boolean complete(String scope, String lastOwner) {
        if (!ready) return false;
        try {
            if (lastOwner != null && scope.startsWith("claim:")) store.setLastOwner(scope.substring(6), lastOwner);
            if (lastOwner != null && scope.startsWith("handover:")) store.setLastOwner(scope.substring(9), lastOwner);
            clear(scope);
            store.saveDurably();
            return true;
        } catch (IOException error) { fail(error); return false; }
    }

    private Result execute(OperationJournal.Intent intent) {
        try {
            Result result = journal.execute(intent, this::apply);
            if (result == Result.UNAVAILABLE || result == Result.CONFLICT) fail(new IOException("Vehicle payment " + intent.id() + " remains " + result));
            return result;
        } catch (IOException | RuntimeException error) {
            fail(error);
            return Result.UNAVAILABLE;
        }
    }

    private void apply(OperationJournal.Intent intent) throws IOException {
        JsonObject changes = intent.changes();
        String action = changes.get("action").getAsString();
        if (action.equals("maintenance")) { applyMaintenance(changes); return; }
        Receipt receipt = JsonUtil.GSON.fromJson(changes.get("receipt"), Receipt.class);
        String scope = changes.get("scope").getAsString();
        Guild guild = guild(receipt);
        if (guild != null && changes.has("feeIncome")) {
            guild.getLedger().setVehicleFeeIncome(changes.get("feeIncome").getAsDouble());
            if (!new Database().saveFactionDurably(guild.getFaction())) throw new IOException("Could not save vehicle fee income");
        }
        if (action.equals("charge")) store.putReceipt(scope, receipt);
        else if (action.equals("refund")) clear(scope);
        else throw new IOException("Unknown vehicle payment action: " + action);
        store.saveDurably();
        if (SimpleFactions.getInstance() != null && changes.has("playerAmount")) {
            var ledger = SimpleFactions.getPlayerEconomyManager().getLedger(receipt.payer().characterId());
            PlayerCashflow kind = receipt.kind() == FeeKind.VEHICLE_TAX ? PlayerCashflow.VEHICLE_TAX : PlayerCashflow.VEHICLE_FEES;
            ledger.add(kind, changes.get("playerAmount").getAsDouble() - ledger.getAmount(kind));
        }
    }

    private void applyMaintenance(JsonObject changes) throws IOException {
        java.util.Map<String, Long> after = new LinkedHashMap<>();
        changes.getAsJsonObject("maintenance").entrySet().forEach(entry -> after.put(entry.getKey(), entry.getValue().getAsLong()));
        DurableJson.write(folder.resolve("Cache/vehicle_maintenance.json"), after);
        maintenance.replaceAll(after);
    }

    private void clear(String scope) {
        store.removeReceipt(scope);
        if (scope.startsWith("build:")) { store.takePaidBuild(scope.substring(6)); store.endBuild(scope.substring(6)); }
    }

    private static Guild guild(Receipt receipt) {
        var faction = FactionManager.getByString(receipt.factionId());
        if (faction == null) return null;
        Guild guild = faction.getGuildHandler().getGuild(faction.getId());
        return guild != null && guild.getIncarnationId().equals(receipt.guildIncarnation()) ? guild : null;
    }

    private static void bookkeeping(JsonObject changes, Receipt receipt, Guild guild, long cents) {
        if (guild != null) changes.addProperty("feeIncome", Money.denars(Math.addExact(Money.cents(guild.getLedger().getVehicleFeeIncome()), cents)));
        if (SimpleFactions.getInstance() != null) {
            var ledger = SimpleFactions.getPlayerEconomyManager().getLedger(receipt.payer().characterId());
            var kind = receipt.kind() == FeeKind.VEHICLE_TAX ? PlayerCashflow.VEHICLE_TAX : PlayerCashflow.VEHICLE_FEES;
            changes.addProperty("playerAmount", ledger.getAmount(kind) - Money.denars(cents));
        }
    }
}
