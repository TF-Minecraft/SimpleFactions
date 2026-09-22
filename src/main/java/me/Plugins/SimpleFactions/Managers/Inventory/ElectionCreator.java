package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Represents;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.election.Candidate;
import me.Plugins.SimpleFactions.government.election.Election;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class ElectionCreator {
    public ItemStack createCandidateTypeItem(Player p, Faction f, Candidate c) {
        Election e = f.getGovernment().getElection();
        ItemStack item = TLibs.getItemAPI().getCreator().getItemFromPath("ia.iasurvival:letter");
        ItemMeta m = item.getItemMeta();
        Government gov = f.getGovernment();
        m.setDisplayName(StringFormatter.formatHex("#84c468"+c.getName()));
        List<String> lore = new ArrayList<String>();
        if(!gov.hasElection()) {
            lore.add(StringFormatter.formatHex("#ab483fCannot cast votes yet"));
            lore.add(StringFormatter.formatHex("#ad9072Next Election: #e3d5a1"+gov.getTimeUntilNextElection()));
            lore.add("");
        }
        List<String> candidates = e.getCandidates(c);
        if(candidates.isEmpty()) {
            lore.add(StringFormatter.formatHex("#c4ba89No candidates yet"));
        } else {
            lore.add(StringFormatter.formatHex("#67cc64Candidates:"));
            for(String candidate : candidates) {
                lore.add(StringFormatter.formatHex("§f- #e3d5a1"+candidate + " §7(" + Represents.represents(f, candidate) +"§7)"));
            }
        }
        lore.add("");
        if(e.isCandiate(c, p.getName())) {
            // Already a candidate
            if(!e.isActive()) {
                lore.add(StringFormatter.formatHex("#ba7872Click to withdraw"));
            }
        } else if(!e.canBeCandidate(c, p.getName())) {
            e.applyReasons(lore, c, p);
        } else {
            lore.add(StringFormatter.formatHex("#28ed70Click to sign up"));
        }
        if(gov.hasElection() && e.canVote(p.getName())){
            if(!e.hasVoted(c, p.getName())) {
                lore.add(StringFormatter.formatHex("#28ed70Click to view candidates"));
            } else {
                lore.add(StringFormatter.formatHex("#ab483fYou have already voted for a candidate"));
            }
        }
        m.setLore(lore);
        m.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, c.name());
        item.setItemMeta(m);
        return item;
    }

    public ItemStack createCandidateItem(Faction f, Candidate candidateType, String candidateName) {
        ItemStack item = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) item.getItemMeta();
        skull.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        skull.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(candidateName));
        skull.setDisplayName(StringFormatter.formatHex("#84c468" + candidateName));
        List<String> lore = new ArrayList<>();
        lore.add(StringFormatter.formatHex("#67cc64" + Represents.represents(f, candidateName)));
        skull.setLore(lore);
        skull.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, candidateName);
        skull.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, candidateType.name());
        item.setItemMeta(skull);
        return item;
    }
}
