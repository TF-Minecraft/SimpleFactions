package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.List;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.election.Candidate;
import me.Plugins.SimpleFactions.government.election.Election;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.keys.Keys;

public class ElectionView {
    public InventoryManager inv;

    ElectionCreator creator = new ElectionCreator();

    public ElectionView(InventoryManager inv) {
		this.inv = inv;
	}

    public void electionView(Player p, Faction f) {
        electionView(p, f, null);
    }

    public void electionView(Player p, Faction f, Inventory i) {
        boolean open = i == null;
        if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.ELECTION_VIEW), 9, "§7Election View");
        i.clear();
		Government gov = f.getGovernment();
        int x = 0;
		for(Candidate c : Candidate.values()) {
            if(gov.hasElections(c)) {
                i.setItem(x, creator.createCandidateTypeItem(p, f, c));
                x++;
            }
        }
        if(open) p.openInventory(i);
    }

    public void votingView(Player p, Faction f, Candidate candidateType) {
        votingView(p, f, candidateType, null);
    }

    public void votingView(Player p, Faction f, Candidate candidateType, Inventory i) {
        boolean open = i == null;
        if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.ELECTION_VOTING_VIEW, candidateType.name()), 54, "§7Vote for " + candidateType.getName());
        i.clear();
        Election election = f.getGovernment().getElection();
        List<String> candidates = election.getCandidates(candidateType);
        int x = 0;
        for(String candidateName : candidates) {
            if(x >= 54) break;
            i.setItem(x, creator.createCandidateItem(f, candidateType, candidateName));
            x++;
        }
        if(open) p.openInventory(i);
    }

    public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		e.setCancelled(true);
		SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
        Faction f = FactionManager.getByString(holder.getId());
        if(f == null) return;
		ItemStack item = e.getCurrentItem();
		if(item == null || item.getItemMeta() == null) return;
		ItemMeta meta = item.getItemMeta();
		String id = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
		if(id == null) return;
		
		if(holder.getType() == SFGUI.ELECTION_VIEW) {
			try {
				Candidate c = Candidate.valueOf(id);
				Election election = f.getGovernment().getElection();
                if(election.isActive()) {
                    if(election.canVote(p.getName()) && !election.hasVoted(c, p.getName())) {
                        votingView(p, f, c);
                    }
                } else {
                    if(election.isCandiate(c, p.getName())) {
                        election.removeCandidate(c, p.getName());
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.sendMessage("§aWithdrawn from candidacy for "+c.getName());
                        electionView(p, f);
                    } else if(election.canBeCandidate(c, p.getName())) {
                        election.addCandidate(c, p.getName());
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.sendMessage("§aSigned up as a candidate for "+c.getName());
                        electionView(p, f);
                    }
                }
			} catch (Exception ex) {
				// Not a candidate type
			}
		} else if(holder.getType() == SFGUI.ELECTION_VOTING_VIEW) {
			String candidateName = id;
			Election election = f.getGovernment().getElection();
			String cid = meta.getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
		    if(cid == null) return;
            try {
                Candidate type = Candidate.valueOf(cid);
                if(election.canVote(p.getName()) && election.isActive()) {
                    if(!election.hasVoted(type, p.getName()) && 
                        election.getCandidates(type).contains(candidateName)) {
                        election.addVote(type, p.getName(), candidateName);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.sendMessage("§aVoted for " + candidateName + " as " + type.getName());
                        p.closeInventory();
                        return;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
		}
	}
}
