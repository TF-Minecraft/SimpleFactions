package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Destination;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Group;
import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Terms;
import net.tfminecraft.simplefactions.utils.Formatter;

/** Lore for the hub proposal list and the negotiation chest. Tax and fee are not recalculated here. */
public final class HubProposalCopy {
    private HubProposalCopy() {
    }

    public static int startingRate(int minPercent, int maxPercent) {
        if (maxPercent < minPercent) {
            return minPercent;
        }
        return (minPercent + maxPercent) / 2;
    }

    public static int clampRate(int rate, int minPercent, int maxPercent) {
        return Math.max(minPercent, Math.min(maxPercent, rate));
    }

    public static long clampFeeCents(long cents, double maxFeeDenars) {
        long maxCents = Math.round(Math.max(0, maxFeeDenars) * 100.0);
        return Math.max(0, Math.min(maxCents, cents));
    }

    public static List<String> destinationLore(Destination destination) {
        List<String> lore = new ArrayList<>();
        if (destination == null) {
            lore.add("§7Estimates are not ready yet");
            return lore;
        }
        lore.add(destination.group() == Group.READY ? "§aReady now" : "§eWorth building");
        lore.add("§7You: about " + signed(destination.operatorGain()) + "§7 a day");
        lore.add("§7Host: about " + signed(destination.hostGain()) + "§7 a day");
        if (destination.assumedRailway()) {
            lore.add("§7No railway yet; assumes about "
                    + Math.round(destination.assumedTrackBlocks()) + " blocks of track");
        }
        if (destination.group() == Group.WORTH_BUILDING || destination.installationId() == null) {
            lore.add("§7A station has to be built here first");
        } else if (destination.ownRealm()) {
            lore.add("§eClick to build this hub");
        } else {
            lore.add("§eClick to negotiate");
        }
        return lore;
    }

    public static List<String> operatorLines(Destination destination, Terms terms) {
        List<String> lore = new ArrayList<>();
        if (destination == null || terms == null) {
            lore.add("§7Estimates are not ready yet");
            return lore;
        }
        if (destination.ownRealm()) {
            lore.add("§7Your guild: about " + signed(destination.operatorGain()) + "§7 a day");
            return lore;
        }
        lore.add("§7Your guild: about " + signed(destination.operatorGain()) + "§7 a day before tax");
        lore.add("§7Tax §c-" + Formatter.formatMoney(terms.tax())
                + "§7, fee §c-" + Formatter.formatMoney(terms.fee()));
        lore.add("§7Net " + signed(terms.operatorNet()) + "§7 a day");
        return lore;
    }

    public static List<String> hostLines(Destination destination, Terms terms, Map<String, String> guildNames) {
        List<String> lore = new ArrayList<>();
        if (destination == null || terms == null) {
            lore.add("§7Estimates are not ready yet");
            return lore;
        }
        if (destination.ownRealm()) {
            lore.add("§7Your realm: about " + signed(destination.hostGain()) + "§7 a day");
            return lore;
        }
        lore.add("§7Your guilds: about " + signed(destination.hostGain()) + "§7 a day");
        lore.add("§7Hub tax §a+" + Formatter.formatMoney(terms.tax())
                + "§7, fee §a+" + Formatter.formatMoney(terms.fee()));
        lore.add("§7Net " + signed(terms.hostNet()) + "§7 a day");
        if (destination.hostGuildGains() != null) {
            for (Map.Entry<String, Double> entry : destination.hostGuildGains().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String name = guildNames == null ? null : guildNames.get(entry.getKey());
                lore.add("§7" + (name == null || name.isBlank() ? entry.getKey() : name)
                        + ": about " + signed(entry.getValue()));
            }
        }
        return lore;
    }

    /**
     * The rate at which the host's net is zero, given the fee already on the screen.
     * The host gains before that rate when their other guilds already gain from the hub.
     */
    public static String breakEvenLine(Destination destination, long feeCents, int minPercent, int maxPercent) {
        if (destination == null || destination.ownRealm()) {
            return "§7A hub in your own realm has no tax or fee";
        }
        double fee = Math.max(0, feeCents) / 100.0;
        double taxable = destination.taxableIncome();
        if (taxable <= 0.0001) {
            if (destination.hostGain() + fee >= 0) {
                return "§7The host gains even at " + minPercent + "%";
            }
            return "§7The host does not break even inside " + minPercent + "-" + maxPercent + "%";
        }
        int shown = (int) Math.round((-destination.hostGain() - fee) * 100.0 / taxable);
        if (shown < minPercent) {
            return "§7The host gains even at " + minPercent + "%";
        }
        if (shown > maxPercent) {
            return "§7The host does not break even inside " + minPercent + "-" + maxPercent + "%";
        }
        if (fee > 0) {
            return "§7Break-even is about §e" + shown + "%§7 at this fee";
        }
        return "§7Break-even is about §e" + shown + "%";
    }

    static String signed(double amount) {
        String money = Formatter.formatMoney(Math.abs(amount));
        if (amount > 0.0001) {
            return "§a+" + money;
        }
        if (amount < -0.0001) {
            return "§c-" + money;
        }
        return "§e" + money;
    }
}
