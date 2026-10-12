package net.tfminecraft.simplefactions.vehicles.fees;

import java.util.UUID;
import java.util.function.Function;

import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
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

    /** The charge this member owes for this vehicle type, or null when they owe nothing. */
    public static Quote quote(FeeKind kind, String payer, String vehicleTypeId) {
        if (kind == null || payer == null || payer.isBlank() || vehicleTypeId == null) {
            return null;
        }
        Faction faction = factionLookup.apply(payer);
        if (faction == null || isLeader(faction, payer)) {
            return null;
        }
        double rate = faction.getVehicleFeeHandler().getChargedRate(kind, vehicleTypeId);
        double amount = kind.amount(rate, VehiclesConfigLoader.getUpkeep(vehicleTypeId));
        if (amount <= 0.0) {
            return null;
        }
        return new Quote(kind, vehicleTypeId, faction, rate, amount);
    }

    private static boolean isLeader(Faction faction, String payer) {
        return faction.getLeader() != null && faction.getLeader().equals(payer);
    }

    public static UUID resolve(String member) { return playerBank.resolve(member); }
    public static double bankBalance(UUID character) { return character == null ? 0 : playerBank.getBankBalance(character); }
}
