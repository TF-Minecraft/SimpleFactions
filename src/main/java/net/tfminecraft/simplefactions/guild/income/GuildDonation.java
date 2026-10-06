package net.tfminecraft.simplefactions.guild.income;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.utils.Formatter;

/**
 * A guild's daily gift to its realm treasury. The amount is what the realm receives.
 * The sender also pays a fee on top, so a 100d gift costs 110d and the extra 10d is destroyed.
 * That fee stops a guild from moving its whole balance into the realm for free.
 */
public final class GuildDonation {

    public static final double FEE_RATE = 0.10;

    private GuildDonation() {}

    /** Denars destroyed on top of {@code sent}. Zero when nothing is sent. */
    public static double fee(double sent) {
        if (sent <= 0 || Double.isNaN(sent) || Double.isInfinite(sent)) {
            return 0.0;
        }
        return Formatter.formatDouble(sent * FEE_RATE);
    }

    /** Gift plus fee. This is what must be on hand. */
    public static double cost(double sent) {
        if (sent <= 0 || Double.isNaN(sent) || Double.isInfinite(sent)) {
            return 0.0;
        }
        return Formatter.formatDouble(sent + fee(sent));
    }

    public static void notifyCancelled(Guild guild, double sent) {
        if (guild == null || sent <= 0) {
            return;
        }
        String leaderName = guild.getLeader();
        if (leaderName == null || leaderName.isBlank() || Bukkit.getServer() == null) {
            return;
        }
        Player leader = Bukkit.getPlayerExact(leaderName);
        if (leader == null) {
            return;
        }
        leader.sendMessage("§cDaily donation of §e" + Formatter.formatMoney(sent)
                + "d §cwas cancelled. The guild could not cover the day.");
    }
}
