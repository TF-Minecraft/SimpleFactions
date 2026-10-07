package net.tfminecraft.simplefactions.managers.inventory;

import java.util.List;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.government.election.Election;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.keys.Keys;

public class ElectionView {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    public InventoryManager inv;

    ElectionCreator creator = new ElectionCreator();

    public ElectionView(InventoryManager inv) {
		this.inv = inv;
	}

    public void electionView(Player p, Faction f) {
        electionView(p, f, null);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
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

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void votingView(Player p, Faction f, Candidate candidateType, Inventory i) {
        boolean open = i == null;
        if(open) i = SimpleFactions.plugin.getServer().createInventory(new SFInventoryHolder(f.getId(), SFGUI.ELECTION_VOTING_VIEW, candidateType.name()), 54, "§7Vote for " + candidateType.getName());
        i.clear();
        Election election = f.getGovernment().getElection();
        List<String> candidates = election.getCandidates(candidateType);
        SFInventoryHolder holder = (SFInventoryHolder) i.getHolder();
        int lastPage = Math.max(0, (candidates.size() - 1) / PAGE_SIZE);
        holder.setPage(Math.min(holder.getPage(), lastPage));
        int start = holder.getPage() * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, candidates.size());
        for(int index = start; index < end; index++) {
            i.setItem(index - start, creator.createCandidateItem(f, candidateType, candidates.get(index)));
        }
        if(holder.getPage() > 0) i.setItem(PREVIOUS_SLOT, DefaultCreator.createPreviousPageButton());
        if(holder.getPage() < lastPage) i.setItem(NEXT_SLOT, DefaultCreator.createNextPageButton());
        if(open) p.openInventory(i);
    }

    public void click(InventoryClickEvent e, Inventory inventory, Player p) {
		e.setCancelled(true);
		SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
        Faction f = FactionManager.getByString(holder.getId());
        if(f == null) return;
        if(holder.getType() == SFGUI.ELECTION_VOTING_VIEW
                && (e.getSlot() == PREVIOUS_SLOT || e.getSlot() == NEXT_SLOT)) {
            holder.setPage(holder.getPage() + (e.getSlot() == NEXT_SLOT ? 1 : -1));
            votingView(p, f, Candidate.valueOf(holder.getSecondaryId()), inventory);
            return;
        }
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
