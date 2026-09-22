package me.Plugins.SimpleFactions.government;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Utils.Wealth;
import me.Plugins.SimpleFactions.enums.Rules;
import me.Plugins.SimpleFactions.government.election.Candidate;
import me.Plugins.SimpleFactions.government.handler.ProposalHandler;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.session.Session;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryLoyaltyWatcher;

public class Council {
    private Faction f;
    private List<String> members = new ArrayList<>();
    private Rules type;
    private int size;

    private List<String> refuses = new ArrayList<>();

    private ProposalHandler proposalHandler;

    public Council(Government gov, Faction f) {
        this.f = f;
        this.proposalHandler = new ProposalHandler(gov);
        this.size = f.getCouncilSize();
        this.type = f.getCouncilType();
    }

    public boolean canPropose(String player) {
        if(!members.contains(player)) return false;
        return proposalHandler.canPropose(player);
    }

    public void reorganize() {
        int newSize = f.getCouncilSize();
        Rules newType = f.getCouncilType();

        boolean typeChanged = newType != type;
        boolean sizeDecreased = newSize < size;

        // Trim if size decreased
        if (sizeDecreased && members.size() > newSize) {
            members = new ArrayList<>(members.subList(0, newSize));
        }

        // Handle type change
        if (typeChanged) {
            switch (newType) {
                case WEALTH_BASED_COUNCIL:
                    members.clear(); // must reselect
                    break;

                case NO_COUNCIL:
                    members.clear();
                    break;

                case APPOINTED_COUNCIL:
                    // keep existing members, seats may go empty later
                    break;

                case ELECTED_COUNCIL:
                    // keep until next election
                    break;
                default:
                    break;
            }

            proposalHandler.clearProposals();
        }
        
        // Apply new settings
        setCouncilSize(newSize);
        setCouncilType(newType);

        if(FactionManager.isLoaded()) {
            // 🔑 Let the unified logic handle membership validity & refill
            replace();
        }
    }

    public boolean hasSession() {
        return SimpleFactions.getInstance().getSessionManager().hasSession(this);
    }

    public Session getSession() {
        return SimpleFactions.getInstance().getSessionManager().getSession(this);
    }

    public boolean canHostSession() {
        return proposalHandler.hasProposals() && !hasSession();
    }

    public boolean hasProposals() {
        return proposalHandler.hasProposals();
    }

    public Rules getType() {
        return type;
    }

    public void setCouncilType(Rules type) {
        this.type = type;
    }

    public void setCouncilSize(int size) {
        this.size = size;
    }

    public boolean refuses(String name) {
        return refuses.contains(name);
    }

    public boolean toggleRefuse(String name) {
        if(refuses.contains(name)) {
            refuses.remove(name);
            return true;
        } else {
            refuses.add(name);
            return true;
        }
    }

    public boolean canBeMember(String name, boolean ignoreSize, boolean ignoreRefuses) {
        if(members.contains(name)) return false;
        if(f.getLeader().equalsIgnoreCase(name)) return false;
        if(!ignoreSize && getCurrentSize() >= getMaxSize()) return false;
        if(!ignoreRefuses && refuses.contains(name)) return false;
        if(f.getOrCreateMainGuild().isMember(name)) return true;
        for(Faction vassal : RelationManager.getSubjects(f)) {
            if(vassal.isLeader(name)) return true;
        }
        for(Guild guild : f.getGuildHandler().getGuilds()) {
            if(guild.isLeader(name)) return true;
        }
        return true;
    }

    public boolean canRemainMember(String name) {
        if (f.getLeader().equalsIgnoreCase(name)) {
            return false;
        }
        if (f.getOrCreateMainGuild().isMember(name)) {
            return true;
        }

        for (Faction vassal : RelationManager.getSubjects(f)) {
            if (vassal.isLeader(name)) {
                return true;
            }
        }

        for (Guild guild : f.getGuildHandler().getGuilds()) {
            if (guild.isLeader(name)) {
                return true;
            }
        }

        return false;
    }


    public void clearMembers() {
        members.clear();
        MercenaryLoyaltyWatcher.onGovernmentChanged(f);
    }

    public void addMemberForce(String name) {
        if (!members.contains(name)) {
            members.add(name);
            MercenaryLoyaltyWatcher.onGovernmentChanged(f);
        }
    }

    public void addMember(String member) {
        if(members.size() < size) {
            members.add(member);
            MercenaryLoyaltyWatcher.onGovernmentChanged(f);
        }
    }

    public void replaceMember(int slot, String newMember) {
        if(slot >= 0 && slot < members.size()) {
            members.set(slot, newMember);
            MercenaryLoyaltyWatcher.onGovernmentChanged(f);
        }
    }

    public boolean isMember(String name) {
        return members.contains(name);
    }

    public int getCurrentProposals(String member) {
        return proposalHandler.getProposalsByProposer(member).size();
    }

    public boolean canBeProposed(Proposal proposal) {
        return proposalHandler.canBeProposed(proposal);
    }

    public ProposalHandler getProposalHandler() {
        return proposalHandler;
    }

    public List<String> getMembers() {
        return members;
    }

    public int getMaxSize() {
        return size;
    }

    public int getCurrentSize() {
        return members.size();
    }

    public boolean couldBeBigger() {
        return members.size() < size && type.equals(Rules.APPOINTED_COUNCIL) && f.getMembers().size()-1 > members.size();
    }

    public double fillPercentage() {
        if(size == 0) return 0.0;
        return (double)members.size()/(double)size;
    }

    public boolean isDummyAccount(String name) {
        return name.toLowerCase().startsWith("dummy_");
    }

    public Set<String> getEligibleVoters() {
        Set<String> eligibleVoters = new HashSet<>();
        eligibleVoters.addAll(members);
        eligibleVoters.add(f.getLeader());
        return eligibleVoters;
    }
    
    public boolean hasEnoughValidVoters() {
        if(getEligibleVoters().isEmpty()) return false;
        int total = getEligibleVoters().size();
        int validCount = 0;
        
        for (String voterName : getEligibleVoters()) {
            Player voterPlayer = Bukkit.getPlayer(voterName);
            if ((voterPlayer != null && voterPlayer.isOnline()) || isDummyAccount(voterName)) {
                validCount++;
            }
        }
        
        // At least 75% of eligible voters must be valid
        return (validCount * 100) / total >= 75;
    }

    public void replace() {
        cleanupCouncil();
        refillCouncil();
    }

    private void cleanupCouncil() {
        for (String member : new ArrayList<>(getMembers())) {
            if (!canRemainMember(member)) {
                getMembers().remove(member);
            }
        }
        for(String refuser : refuses) {
            if(!canBeMember(refuser, true, true)) {
                refuses.remove(refuser);
            }
        }
    }

    private void refillCouncil() {
        if (getCurrentSize() >= getMaxSize()) return;

        switch (getType()) {
            case ELECTED_COUNCIL:
                refillElectedCouncil();
                break;

            case WEALTH_BASED_COUNCIL:
                refillWealthCouncil();
                break;

            case APPOINTED_COUNCIL:
            case NO_COUNCIL:
            default:
                // Do nothing — seats stay empty
                break;
        }
    }

    private void refillElectedCouncil() {
        int maxSize = getMaxSize();

        // 1. Election winners
        for (String name : f.getGovernment().getElection().getWinners(Candidate.COUNCIL)) {
            if (getCurrentSize() >= maxSize) break;
            if (!isMember(name) && canRemainMember(name)) {
                addMemberForce(name);
            }
        }

        // 2. Fallback faction members
        for (String name : f.getMembers()) {
            if (getCurrentSize() >= maxSize) break;
            if (!isMember(name) && canRemainMember(name)) {
                addMemberForce(name);
            }
        }
    }

    private void refillWealthCouncil() {
        List<String> richest = Wealth.topWealth(f, true);

        for (String name : richest) {
            if (getCurrentSize() >= getMaxSize()) break;
            if (!isMember(name) && canBeMember(name, false, false)) {
                addMemberForce(name);
            }
        }
    }
}
