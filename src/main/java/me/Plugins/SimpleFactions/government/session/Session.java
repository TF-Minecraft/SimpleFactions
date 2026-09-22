package me.Plugins.SimpleFactions.government.session;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.government.Council;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Managers.SessionManager;

public class Session {
    private Player leader;
    private Block lantern;
    private Faction f;
    private Council c;
    private boolean started = false;
    private int time = 0;

    private Map<Proposal, VoteResult> proposals = new LinkedHashMap<>();
    private SessionHologram hologram;
    private Map<String, Vote> currentVotes = new HashMap<>(); // Player name -> Vote
    private Set<String> eligibleVoters = new HashSet<>(); // Council members + leader
    
    public Session(Player leader, Faction f) {
        this.leader = leader;
        this.f = f;
        c = f.getGovernment().getCouncil();
        leader.sendTitle("§aSelect Location", "§7Click on a §eLantern §7to select Location", 20, 80, 20);
    }

    public void tick() {
        time++;
        if (time >= 60 && !started) {
            cancel();
        }
    }
    
    public void onLanternClick(Block lanternBlock) {
        if (started) return;
        
        this.lantern = lanternBlock;
        start();
    }

    public boolean isStarted() {
        return started;
    }

    private void nextProposal() {
        Proposal proposal = c.getProposalHandler().pop();
        if (proposal == null) {
            end();
            return;
        }
        proposals.put(proposal, VoteResult.IN_PROGRESS);
        initializeVoting();
        updateHologram();
    }
    
    private void initializeVoting() {
        currentVotes.clear();
        eligibleVoters.clear();
        
        // Add leader
        eligibleVoters.add(leader.getName());
        
        // Add council members
        for (String member : c.getMembers()) {
            eligibleVoters.add(member);
        }
        
        // Auto-abstain offline members
        for (String member : eligibleVoters) {
            Player memberPlayer = Bukkit.getPlayer(member);
            if (memberPlayer == null || !memberPlayer.isOnline()) {
                currentVotes.put(member, Vote.ABSTAIN);
            }
        }
    }
    
    public boolean recordVote(String playerName, Vote vote) {
        if (!eligibleVoters.contains(playerName)) {
            return false;
        }
        currentVotes.put(playerName, vote);
        updateHologram();
        
        // Check if all votes are cast
        if (areAllVotesCast()) {
            // Delay counting votes slightly for visual feedback
            new BukkitRunnable() {
                @Override
                public void run() {
                    countVotes();
                }
            }.runTaskLater(SimpleFactions.getInstance(), 10L); // 0.5 seconds delay
        }
        
        return true;
    }
    
    private boolean areAllVotesCast() {
        return currentVotes.size() >= eligibleVoters.size();
    }
    
    private int[] getVoteCounts() {
        int yay = 0, nay = 0, abstain = 0;
        for (Vote v : currentVotes.values()) {
            if (v == Vote.YAY) yay++;
            else if (v == Vote.NAY) nay++;
            else if (v == Vote.ABSTAIN) abstain++;
        }
        return new int[]{yay, nay, abstain};
    }

    public void countVotes() {
        int[] counts = getVoteCounts();
        int yay = counts[0];
        int nay = counts[1];
        
        Proposal currentProposal = getCurrentProposal();
        if (currentProposal == null) return;
        
        VoteResult result;
        if (yay > nay) {
            result = VoteResult.PASSED;
            proposals.put(currentProposal, result);
            leader.sendMessage("§aProposal Passed!");
            displayParticles(Particle.HAPPY_VILLAGER);
        } else if (nay > yay) {
            result = VoteResult.FAILED;
            proposals.put(currentProposal, result);
            leader.sendMessage("§cProposal Failed!");
            displayParticles(Particle.FLAME);
        } else {
            result = VoteResult.TIE;
            proposals.put(currentProposal, result);
            leader.sendMessage("§eProposal Tied!");
            displayParticles(Particle.NOTE);
        }
        
        // Proceed to next proposal after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                nextProposal();
            }
        }.runTaskLater(SimpleFactions.getInstance(), 60L); // 3 seconds delay
    }
    
    private void displayParticles(Particle particle) {
        if (lantern == null) return;
        org.bukkit.Location particleLoc = lantern.getLocation().add(0.5, 1.0, 0.5);
        for (int i = 0; i < 20; i++) {
            lantern.getWorld().spawnParticle(particle, particleLoc, 1, 0.5, 0.5, 0.5);
        }
    }

    public Proposal getCurrentProposal() {
        for (Proposal p : proposals.keySet()) {
            if (proposals.get(p).equals(VoteResult.IN_PROGRESS)) {
                return p;
            }
        }
        return null;
    }
    
    public void start() {
        if (!c.hasEnoughValidVoters()) {
            leader.sendMessage("§cNot enough council members online! At least 75% must be present.");
            cancel();
            return;
        }
        
        leader.sendTitle("§aSession Started!", "§7The §eLantern §7will show proposals and count votes", 20, 80, 20);
        leader.playSound(leader, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        hologram = new SessionHologram(lantern);
        hologram.create();
        nextProposal();
        started = true;
    }

    public void kill() {
        if (hologram != null) {
            hologram.destroy();
        }
    }

    public void end() {
        // Create session report
        SessionReport report = new SessionReport(leader.getName(), f);
        
        // Gather all results
        for(Proposal p : proposals.keySet()) {
            VoteResult result = proposals.get(p);
            if (!result.equals(VoteResult.IN_PROGRESS)) {
                // Find votes for this proposal (reconstruct from currentVotes if needed)
                // For now we'll get them from the last counts when the proposal was decided
                int[] counts = getVoteCounts();
                report.addResult(p, result, counts[0], counts[1], counts[2]);
            }
        }

        ItemStack book;
        if(leader != null && leader.isOnline()) {
            book = report.generateReportBook();
        } else {
            book = null;
        }

        for(Proposal p : proposals.keySet()) {
            VoteResult result = proposals.get(p);
            if (result.equals(VoteResult.PASSED)) {
                p.apply(null);
            }
        }
        
        // Send title to all online eligible voters
        for (String voterName : eligibleVoters) {
            org.bukkit.entity.Player voter = Bukkit.getPlayer(voterName);
            if (voter != null && voter.isOnline()) {
                voter.sendTitle("§aSession Completed!", "§7View the report in your inventory", 20, 60, 20);
            }
        }
        
        // Give leader the report book
        if (leader != null && leader.isOnline()) {
            leader.getInventory().addItem(book);
            leader.sendMessage("§aSession report added to your inventory!");
        }
        
        endSession();
    }
    
    private void updateHologram() {
        if (hologram == null) return;
        int current = proposals.size();
        int total = c.getProposalHandler().getProposals().size() + current;
        hologram.updateProposalInfo(current, total);
        
        // Get current vote counts
        int[] counts = getVoteCounts();
        hologram.updateVotes(counts[0], counts[1], counts[2]);
    }
    
    private void endSession() {
        if (hologram != null) {
            hologram.destroy();
        }
        SessionManager sessionManager = SimpleFactions.getInstance().getSessionManager();
        sessionManager.endSession(f.getGovernment().getCouncil());
    }
    
    private void cancel() {
        if (leader != null) {
            leader.sendMessage("§cSession cancelled: No lantern selected within 60 seconds.");
        }
        endSession();
    }
    
    public Player getLeader() {
        return leader;
    }
    
    public Block getLantern() {
        return lantern;
    }
    
    public Faction getFaction() {
        return f;
    }
}
