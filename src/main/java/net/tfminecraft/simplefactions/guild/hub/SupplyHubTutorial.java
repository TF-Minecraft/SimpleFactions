package net.tfminecraft.simplefactions.guild.hub;

import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public final class SupplyHubTutorial {
    public enum Decision { SHOW, NOTE, NONE }

    private static final String GOT_IT_LABEL = "[Got It]";
    private static final int SEPARATOR_WIDTH = 40;

    private SupplyHubTutorial() {
    }

    public static Decision decision(
            boolean hasCapital, boolean hasCapitalHub, boolean targetIsCapital, boolean dismissed) {
        if (!hasCapital || hasCapitalHub || targetIsCapital) {
            return Decision.NONE;
        }
        return dismissed ? Decision.NOTE : Decision.SHOW;
    }

    public static void show(Player player, String capitalLine) {
        player.sendMessage(StringFormatter.formatHex("#777777" + "-".repeat(SEPARATOR_WIDTH)));
        player.sendMessage(StringFormatter.formatHex("#b5835aSupply Hubs"));
        List<String> lines = List.of(
                "Supply hubs work in pairs. A hub sends a share of your guild's trade power"
                        + " and production to your other hubs it is linked to.",
                "Trade power starts at your guild capital, so build your first hub there and"
                        + " your second where you want trade to arrive.",
                "Hubs link by train track between any two of them. Ports also link across the"
                        + " sea, and airports through the air.",
                capitalLine);
        for (String line : lines) {
            player.sendMessage("§7" + line);
        }
        String dashes = "-".repeat((SEPARATOR_WIDTH - GOT_IT_LABEL.length()) / 2);
        Component hover = Component.text("Click to dismiss", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("\nYou won't see this tutorial again.", NamedTextColor.GRAY)
                        .decorate(TextDecoration.BOLD, TextDecoration.ITALIC));
        Component gotIt = Component.text(GOT_IT_LABEL, NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand("/guild hub gotit"))
                .hoverEvent(HoverEvent.showText(hover));
        player.sendMessage(Component.empty()
                .append(Component.text(dashes, NamedTextColor.GRAY))
                .append(gotIt)
                .append(Component.text(dashes, NamedTextColor.GRAY)));
    }

    public static String placementLine(Installation installation, String ownerName) {
        if (installation != null) {
            return "Your capital has a free hub slot at §f" + installation.getName() + "§7.";
        }
        String faction = ownerName == null || ownerName.isBlank() ? "the faction that owns it" : ownerName;
        return "Your capital province has no port, airport or train station with a free hub slot. Ask "
                + faction + " to build or upgrade one.";
    }
}
