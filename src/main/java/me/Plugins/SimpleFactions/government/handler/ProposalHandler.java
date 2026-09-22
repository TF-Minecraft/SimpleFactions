package me.Plugins.SimpleFactions.government.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Loaders.LawLoader;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.proposal.TaxLawChange;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public class ProposalHandler {
    private Government gov;
    private boolean movement;

    public ProposalHandler(Government gov) {
        this.gov = gov;
        this.movement = false;
    }

    public ProposalHandler(Government gov, boolean movement) {
        this.gov = gov;
        this.movement = movement;
    }

    private List<Proposal> proposals = new ArrayList<>();
    
    public boolean canPropose(String member) {
        if(movement) return true;
        return proposals.stream().filter(p -> p.getProposer().equals(member)).count() < 2;
    }

    public boolean hasProposals() {
        return !proposals.isEmpty();
    }

    public Proposal pop() {
        if(proposals.isEmpty()) return null;
        return proposals.remove(0);
    }

    public boolean canBeProposed(Proposal proposal) {
        if(movement) return true;
        if(proposal.isLawProposal()) {
            LawGroup group = gov.getFaction().getLawHandler().getGroup(proposal.getLaw().getGroup());
            for(Proposal p : proposals) {
                if(!p.isLawProposal()) continue;
                LawGroup g = gov.getFaction().getLawHandler().getGroup(p.getLaw().getGroup());
                if(g.getId().equalsIgnoreCase(group.getId())) return false;
            }
        } else if(proposal.isTaxProposal()) {
            TaxLawChange change = proposal.getTaxChange();
            for(Proposal p : proposals) {
                if(!p.isTaxProposal()) continue;
                TaxLawChange c = p.getTaxChange();
                if(c.getTarget().equals(change.getTarget()) && c.getId().equalsIgnoreCase(change.getId())) return false;
            }
        }
        return true;
    }

    public void propose(Proposal proposal) {
        proposals.add(proposal);
    }

    public List<Proposal> getProposals() {
        return proposals;
    }

    public void clearProposals() {
        proposals.clear();
    }

    public List<Proposal> getProposalsByProposer(String proposer) {
        List<Proposal> result = new ArrayList<>();
        for(Proposal p : proposals) {
            if(p.getProposer().equals(proposer)) result.add(p);
        }
        return result;
    }

    public List<String> serializeProposals() {
        List<String> result = new ArrayList<>();
        for (Proposal p : proposals) {
            if (p.isLawProposal() && p.getLaw() != null) {
                Law law = p.getLaw();
                result.add(p.getProposer() + ":law:" + law.getGroup() + ":" + law.getId());
            } else if (p.isTaxProposal() && p.getTaxChange() != null) {
                TaxLawChange tax = p.getTaxChange();
                result.add(p.getProposer() + ":tax:" + tax.getTarget().name() + ":" + tax.getId() + ":" + tax.getNewTax());
            }
        }
        return result;
    }

    public void restoreProposals(me.Plugins.SimpleFactions.Objects.Faction faction, List<String> serialized) {
        proposals.clear();
        for (String s : serialized) {
            String proposer = s.split(":", 2)[0];
            s = s.substring(proposer.length() + 1);
            if (s.startsWith("law:")) {
                String[] parts = s.substring(4).split(":");
                if (parts.length >= 2) {
                    String groupId = parts[0];
                    String newLawId = parts[1];
                    
                    LawGroup group = faction.getLawHandler().getGroup(groupId);
                    if (group != null) {
                        Law newLaw = group.getLaw(newLawId);
                        if (newLaw != null) {
                            Proposal p = new Proposal(proposer, gov);
                            p.setLawProposal(newLaw);
                            proposals.add(p);
                        }
                    }
                }
            } else if (s.startsWith("tax:")) {
                String[] parts = s.substring(4).split(":");
                if (parts.length >= 3) {
                    try {
                        me.Plugins.SimpleFactions.government.proposal.TaxTarget target = me.Plugins.SimpleFactions.government.proposal.TaxTarget.valueOf(parts[0]);
                        String taxId = parts[1];
                        double newRate = Double.parseDouble(parts[2]);
                        
                        TaxLawChange tax = new TaxLawChange(target, taxId, newRate);
                        Proposal p = new Proposal(proposer, gov);
                        p.setTaxProposal(tax);
                        proposals.add(p);
                    } catch (Exception e) {
                        // Skip malformed proposals
                    }
                }
            }
        }
    }
}
