package net.tfminecraft.simplefactions.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.simplefactions.managers.RelationManager;

public class Wealth {
    /** Pouch, bank and a share of the character's guild. */
    public static double wealth(String player) {
        double wealth = balance(player, Accounts.POUCH);
        wealth += balance(player, Accounts.BANK);
        Guild guild = FactionManager.getGuildByMember(player);
        if(guild != null) {
            if(guild.getLeader().equals(player) && guild.isBase()) {
                wealth += guild.getWealth();
            } else if(!guild.isBase()) {
                wealth += guild.getWealth()/(double)guild.getMembers().size();
            }
        }
        return wealth;
    }

    private static double balance(String member, Accounts account) {
        UUID id = Members.characterId(member);
        if (id == null) return 0;
        return net.tfminecraft.simplefactions.economy.Money.denars(net.tfminecraft.denareconomy.api.Denars.balance(
                "char:" + id + ":" + (account == Accounts.POUCH ? "pouch" : "bank")));
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
