package net.tfminecraft.simplefactions.guild.income;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.utils.BankAmount;

/** Creates a staff grant directly in a guild bank and records it in the deposit history. */
public final class GuildBankGrant {

    private GuildBankGrant() {
    }

    public static boolean grant(Guild guild, double amount) {
        if (guild == null || BankAmount.parse(Double.toString(amount)) == null) return false;
        Bank bank = guild.getBank();
        if (bank == null || guild.getLedger() == null) return false;

        bank.deposit(amount);
        guild.getLedger().getHistory().addDeposit("Compensation", amount);
        return true;
    }
}
