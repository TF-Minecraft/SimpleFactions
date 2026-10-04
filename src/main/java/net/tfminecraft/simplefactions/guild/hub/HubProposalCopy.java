package net.tfminecraft.simplefactions.guild.hub;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.guild.hub.HubEstimates.Site;
import net.tfminecraft.simplefactions.utils.Formatter;

/** Lore for the hub proposal list and the negotiation chest. */
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

    /** {@code Joins: The Vardera Network}, with the same colours as the other proposal lines. */
    public static String joinsLine(String networkName) {
        String name = networkName == null || networkName.isBlank() ? "a network" : networkName;
        return "§7Joins: §f" + name;
    }

    public static List<String> destinationLore(Site site, boolean showArrival) {
        List<String> lore = new ArrayList<>();
        if (site == null) {
            lore.add("§7This installation is not available");
            return lore;
        }
        lore.add("§aReady now");
        lore.add("§7Trade power here: §e" + Formatter.formatDouble(site.tradeHere()));
        if (showArrival) {
            lore.add("§7Arrives here: §e" + Formatter.formatDouble(site.arrivesHere()));
        }
        if (site.ownRealm()) {
            lore.add("§eClick to build this hub");
        } else {
            lore.add("§eClick to negotiate");
        }
        return lore;
    }

    public static List<String> powerLines(Site site) {
        List<String> lore = new ArrayList<>();
        if (site == null) {
            lore.add("§7This installation is not available");
            return lore;
        }
        lore.add("§7Trade power here: §e" + Formatter.formatDouble(site.tradeHere()));
        lore.add("§7Arrives here: §e" + Formatter.formatDouble(site.arrivesHere()));
        return lore;
    }

    public static List<String> hostLines(Site site) {
        if (site != null && site.ownRealm()) {
            return List.of("§7A hub in your own realm has no tax or fee");
        }
        return List.of("§7The rate and the daily fee are what this realm charges.");
    }
}
