package net.tfminecraft.simplefactions.government.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.loaders.PoliticalActionLoader;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.FeeChange;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;

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

    // Stands in for the null vehicle type of a general fee in the saved form.
    private static final String ALL_VEHICLES = "*";
    
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
        } else if(proposal.isFeeProposal()) {
            for(Proposal p : proposals) {
                if(p.isFeeProposal() && p.getFeeChange().sameTarget(proposal.getFeeChange())) return false;
            }
        } else if(proposal.isTaxProposal()) {
            TaxLawChange change = proposal.getTaxChange();
            for(Proposal p : proposals) {
                if(!p.isTaxProposal()) continue;
                TaxLawChange c = p.getTaxChange();
                if(c.getTarget().equals(change.getTarget()) && (c.getId() == null
                        ? change.getId() == null : c.getId().equalsIgnoreCase(change.getId()))) return false;
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
            } else if (p.isFeeProposal()) {
                FeeChange fee = p.getFeeChange();
                result.add(p.getProposer() + ":fee:" + fee.getKind().name() + ":"
                        + (fee.isGeneral() ? ALL_VEHICLES : fee.getVehicleTypeId()) + ":" + fee.getNewRate());
            } else if (p.isPoliticalActionProposal()) {
                result.add(p.getProposer() + ":action:" + p.getPoliticalAction().getAction().name()
                        + ":" + (p.getTarget() == null ? "" : p.getTarget()));
            }
        }
        return result;
    }

    public void restoreProposals(net.tfminecraft.simplefactions.objects.Faction faction, List<String> serialized) {
        proposals.clear();
        for (String s : serialized) {
            if (s == null) continue;
            int separator = s.indexOf(':');
            if (separator <= 0) continue;
            String proposer = s.substring(0, separator);
            s = s.substring(separator + 1);
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
            } else if (s.startsWith("fee:")) {
                // kind:type:rate, where the type may itself contain colons.
                String body = s.substring(4);
                int first = body.indexOf(':');
                int last = body.lastIndexOf(':');
                if (first > 0 && last > first) {
                    try {
                        FeeKind kind = FeeKind.valueOf(body.substring(0, first));
                        String typeField = body.substring(first + 1, last);
                        String type = ALL_VEHICLES.equals(typeField) ? null : typeField;
                        double newRate = Double.parseDouble(body.substring(last + 1));
                        if (!Double.isFinite(newRate)) continue;
                        Proposal p = new Proposal(proposer, gov);
                        p.setFeeProposal(new FeeChange(kind, type, newRate));
                        proposals.add(p);
                    } catch (Exception e) {
                        // Skip malformed proposals
                    }
                }
            } else if (s.startsWith("action:")) {
                String[] parts = s.substring(7).split(":", 2);
                try {
                    Action kind = Action.valueOf(parts[0]);
                    PoliticalAction action = PoliticalActionLoader.getByAction(kind);
                    Proposal proposal = new Proposal(proposer, gov);
                    proposal.setPoliticalActionProposal(action != null ? action : new PoliticalAction(kind));
                    if (parts.length == 2 && !parts[1].isEmpty()) proposal.setTarget(parts[1]);
                    proposals.add(proposal);
                } catch (IllegalArgumentException e) {
                    // Skip actions that do not exist in this version.
                }
            } else if (s.startsWith("tax:")) {
                String[] parts = s.substring(4).split(":");
                if (parts.length >= 3) {
                    try {
                        net.tfminecraft.simplefactions.government.proposal.TaxTarget target = net.tfminecraft.simplefactions.government.proposal.TaxTarget.valueOf(parts[0]);
                        String taxId = "null".equals(parts[1]) ? null : parts[1];
                        double newRate = Double.parseDouble(parts[2]);
                        if (!Double.isFinite(newRate)) continue;
                        
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
