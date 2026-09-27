package net.tfminecraft.simplefactions.vehicles.fees;

import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeService.Quote;

public final class VehicleFeeMessages {
    private VehicleFeeMessages() {}

    private static String amount(double amount) {
        return "§e" + Formatter.formatMoney(amount) + "d";
    }

    private static String charge(Quote quote) {
        return amount(quote.amount()) + " §7(" + quote.kind().formatRate(quote.rate()) + ", "
                + quote.faction().getName() + "§7)";
    }

    public static String registrationConfirm(Quote quote, String vehicleTypeId) {
        return "§6Registering a §e" + vehicleTypeId + " §6costs " + charge(quote)
                + "§6. Left-click again to confirm and pay from your bank.";
    }

    public static String registrationPaid(Quote quote) {
        return "§aPaid a registration fee of " + amount(quote.amount()) + "§a.";
    }

    public static String registrationUnaffordable(Quote quote) {
        return "§cYou cannot afford the registration fee of " + amount(quote.amount())
                + " §cfrom your bank.";
    }

    public static String registrationRefunded(double amount) {
        return "§aYour construction was cancelled, so " + amount(amount)
                + " §aof registration fee was refunded to your bank.";
    }

    public static String claimConfirm(Quote quote, String lastOwner) {
        return "§6This vehicle was last owned by §e" + lastOwner + "§6, so claiming it is a transfer. "
                + "The transfer fee is " + charge(quote) + "§6. Right-click again to confirm and pay from your bank.";
    }

    public static String claimUnaffordable(Quote quote) {
        return "§cYou cannot afford the transfer fee of " + amount(quote.amount()) + " §cfrom your bank.";
    }

    public static String claimPaid(Quote quote) {
        return "§aPaid a transfer fee of " + amount(quote.amount()) + "§a.";
    }

    public static String handoverConfirm(Quote quote, String recipient) {
        return "§6Handing this vehicle to §e" + recipient + " §6costs you " + charge(quote)
                + "§6. Right-click it again to confirm.";
    }

    public static String handoverUnaffordable(Quote quote) {
        return "§cYou cannot afford the transfer fee of " + amount(quote.amount()) + " §cfrom your bank.";
    }

    public static String handoverPaid(Quote quote) {
        return "§aPaid a transfer fee of " + amount(quote.amount()) + "§a.";
    }
}
