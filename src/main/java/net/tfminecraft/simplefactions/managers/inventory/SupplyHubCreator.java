package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubStanding;
import net.tfminecraft.simplefactions.utils.Formatter;

public class SupplyHubCreator {
    public static List<String> guildHubLore(
            String installationName,
            String kind,
            String factionName,
            double upkeep,
            HubStanding standing,
            List<String> connections,
            double tradePower,
            double production) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Installation: §e" + installationName + " §8(" + kind + ")");
        lore.add("§7Owned by: §e" + factionName);
        lore.add("§7Daily upkeep: §e" + Formatter.formatDouble(upkeep) + "d");
        lore.add(net.tfminecraft.simplefactions.guild.hub.SupplyHubService.statusText(standing));
        if (connections != null) {
            lore.addAll(connections);
        }
        lore.add("§7Trade power here: §e" + Formatter.formatDouble(tradePower));
        lore.add("§7Production here: §e" + Formatter.formatDouble(production));
        return lore;
    }

    public static List<String> hostHubLore(String guildName, String factionName, HubStanding standing) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Guild: §e" + guildName);
        lore.add("§7Faction: §e" + factionName);
        lore.add(net.tfminecraft.simplefactions.guild.hub.SupplyHubService.statusText(standing));
        return lore;
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
