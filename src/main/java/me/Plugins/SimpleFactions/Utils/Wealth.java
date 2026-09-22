package me.Plugins.SimpleFactions.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import net.tfminecraft.DenarEconomy.DenarEconomy;
import net.tfminecraft.DenarEconomy.Enum.Accounts;
import me.Plugins.SimpleFactions.Managers.RelationManager;

public class Wealth {
    public static double wealth(String player) {
        double wealth = 0;
        OfflinePlayer op = Bukkit.getOfflinePlayer(player);
        if(op.hasPlayedBefore()) {
            UUID uuid = op.getUniqueId();
            wealth += DenarEconomy.getMoneyManager().getBalance(Accounts.POUCH, uuid);
            wealth += DenarEconomy.getMoneyManager().getBalance(Accounts.BANK, uuid);
        }
        Guild guild = FactionManager.getGuildByMember(player);
        if(guild != null) {
            if(guild.getLeader().equalsIgnoreCase(player) && guild.isBase()) {
                wealth += guild.getWealth();
            } else if(!guild.isBase()) {
                wealth += guild.getWealth()/(double)guild.getMembers().size();
            }
        }
        return wealth;
    }

    public static List<String> topWealth(Faction faction, boolean includeVassals) {
        List<String> top = new ArrayList<>();
        Map<String, Double> wealthMap = new HashMap<>();
        for(String player : faction.getMembers()) {
            double wealth = wealth(player);
            wealthMap.put(player, wealth);
        }
        for(Faction vassal : RelationManager.getSubjects(faction)) {
            if(!includeVassals) break;
            for(String player : vassal.getMembers()) {
                double wealth = wealth(player);
                wealthMap.put(player, wealth);
            }
        }
        wealthMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEachOrdered(entry -> top.add(entry.getKey()));
        return top;
    }
}
