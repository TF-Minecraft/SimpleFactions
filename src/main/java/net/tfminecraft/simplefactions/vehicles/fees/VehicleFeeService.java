package net.tfminecraft.simplefactions.vehicles.fees;

import java.util.UUID;
import java.util.function.Function;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;

/**
 * Prices and collects the vehicle tax law's charges. The payer's own faction sets the rate
 * and receives the money; players with no faction and faction leaders pay nothing.
 */
public final class VehicleFeeService {
    /** A priced charge. Only built when there is something to pay. */
    public record Quote(FeeKind kind, String vehicleTypeId, Faction faction, double rate, double amount) {}

    private static final Function<String, Faction> MEMBER_FACTION =
            name -> FactionManager.factions == null ? null : FactionManager.getByMember(name);
    private static Function<String, Faction> factionLookup = MEMBER_FACTION;
    private static PlayerBank playerBank = DenarEconomyPlayerBank.INSTANCE;

    private VehicleFeeService() {}

    public static void setForTests(Function<String, Faction> lookup, PlayerBank bank) {
        factionLookup = lookup == null ? MEMBER_FACTION : lookup;
        playerBank = bank == null ? DenarEconomyPlayerBank.INSTANCE : bank;
    }

    /** The charge this player owes for this vehicle type, or null when they owe nothing. */
    public static Quote quote(FeeKind kind, String payerName, String vehicleTypeId) {
        if (kind == null || payerName == null || payerName.isBlank() || vehicleTypeId == null) {
            return null;
        }
        Faction faction = factionLookup.apply(payerName);
        if (faction == null || isLeader(faction, payerName)) {
            return null;
        }
        double rate = faction.getVehicleFeeHandler().getChargedRate(kind, vehicleTypeId);
        double amount = kind.amount(rate, VehiclesConfigLoader.getUpkeep(vehicleTypeId));
        if (amount <= 0.0) {
            return null;
        }
        return new Quote(kind, vehicleTypeId, faction, rate, amount);
    }

    private static boolean isLeader(Faction faction, String playerName) {
        return faction.getLeader() != null && faction.getLeader().equalsIgnoreCase(playerName);
    }

    /**
     * Takes the quote from the payer's bank into the faction bank. Returns false, moving
     * nothing, when the payer cannot cover it.
     */
    public static boolean collect(UUID payerUuid, Quote quote) {
        if (payerUuid == null || quote == null) {
            return false;
        }
        if (quote.faction().getBank() == null || !playerBank.withdrawFromBank(payerUuid, quote.amount())) {
            return false;
        }
        credit(payerUuid, quote);
        return true;
    }

    /** Pays an already withdrawn quote into the faction bank and records it on both ledgers. */
    public static void credit(UUID payerUuid, Quote quote) {
        Bank bank = quote.faction().getBank();
        if (bank != null) {
            bank.deposit(quote.amount());
        }
        quote.faction().getOrCreateMainGuild().getLedger().addVehicleFeeEntry(quote.amount());
        recordPlayer(payerUuid, quote.kind(), -quote.amount());
    }

    /**
     * Pays a collected charge back from the faction bank, as far as the bank covers it.
     * Returns the denars refunded.
     */
    public static double refund(UUID payerUuid, String factionId, FeeKind kind, double amount) {
        if (payerUuid == null || amount <= 0.0) {
            return 0.0;
        }
        Faction faction = factionId == null ? null : FactionManager.getByString(factionId);
        Bank bank = faction == null ? null : faction.getBank();
        Double wealth = bank == null ? null : bank.getWealth();
        if (wealth == null || wealth <= 0.0) {
            return 0.0;
        }
        double refunded = Math.min(amount, wealth);
        if (!playerBank.depositToBank(payerUuid, refunded)) {
            return 0.0;
        }
        bank.withdraw(refunded);
        faction.getOrCreateMainGuild().getLedger().addVehicleFeeEntry(-refunded);
        recordPlayer(payerUuid, kind, refunded);
        return refunded;
    }

    public static UUID resolve(String playerName) {
        return playerBank.resolve(playerName);
    }

    public static double bankBalance(UUID playerUuid) {
        return playerUuid == null ? 0.0 : playerBank.getBankBalance(playerUuid);
    }

    private static void recordPlayer(UUID payerUuid, FeeKind kind, double amount) {
        if (SimpleFactions.getInstance() == null) {
            return;
        }
        PlayerLedger ledger = SimpleFactions.getPlayerEconomyManager().getLedger(payerUuid);
        if (ledger != null) {
            ledger.add(kind == FeeKind.VEHICLE_TAX ? PlayerCashflow.VEHICLE_TAX : PlayerCashflow.VEHICLE_FEES, amount);
        }
    }
}
