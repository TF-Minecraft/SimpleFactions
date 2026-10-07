package net.tfminecraft.simplefactions.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.government.Council;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.session.Session;
import net.tfminecraft.simplefactions.government.session.Vote;
import net.tfminecraft.simplefactions.managers.FactionManager;

public class SessionManager implements Listener{
    private Map<Council, Session> sessions = new HashMap<>();

    public void start() {
        tickCycle();
    }

    public void end() {
        for(Session session : new ArrayList<>(sessions.values())) {
            session.kill();
        }
    }

    public void tickCycle() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for(Session session : new ArrayList<>(sessions.values())) {
                    session.tick();
                }
            }
        }.runTaskTimer(SimpleFactions.getInstance(), 20, 20); //Run every second
    }
    
    public void newSession(Player player, Faction f) {
        Council council = f.getGovernment().getCouncil();
        Session previous = sessions.get(council);
        if (previous != null) previous.kill();
        sessions.put(council, new Session(player, f));
    }
    
    public void endSession(Council council) {
        sessions.remove(council);
    }
    
    public boolean hasSession(Council council) {
        return sessions.containsKey(council);
    }
    
    public Session getSession(Council council) {
        return sessions.get(council);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if(!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        Block clickedBlock = event.getClickedBlock();
        
        if (clickedBlock == null) return;
        if (!clickedBlock.getType().equals(Material.LANTERN)) return;
        
        Faction faction = FactionManager.getByMember(player.getName());
        if (faction == null) return;
        
        Council council = faction.getGovernment().getCouncil();
        Session session = getSession(council);
        if (session != null && (faction.getLeader().equals(player.getName()) || faction.getGovernment().isCouncilMember(player))) {
            if(session.isStarted() && clickedBlock.equals(session.getLantern())) {
                event.setCancelled(true);
                Proposal currentProposal = session.getCurrentProposal();
                if(currentProposal == null) {
                    return;
                }
                player.openBook(currentProposal.getAsBook(player));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                player.swingMainHand();
                return;
            } else if(session.isStarted() && !clickedBlock.equals(session.getLantern())) {
                return;
            }
            event.setCancelled(true);
            session.onLanternClick(clickedBlock);
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        if (!brokenBlock.getType().equals(Material.LANTERN)) return;
        
        // Check if this lantern is part of any active session
        for (Session session : sessions.values()) {
            if (session.getLantern() != null && session.getLantern().equals(brokenBlock)) {
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage("§cSession ended!");
                }
                session.kill();
                return;
            }
        }
    }
    
    // Retain Bukkit chat-event ordering and String message semantics for existing integrations.
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage().trim();
        Vote vote = Vote.fromString(message);
        
        if (vote == null) return; // Not a vote command
        
        Player player = event.getPlayer();
        // Chat may be asynchronous; faction state, world access and holograms belong on the server thread.
        new BukkitRunnable() {
            @Override
            public void run() {
                recordChatVote(player, vote);
            }
        }.runTask(SimpleFactions.getInstance());
    }

    private void recordChatVote(Player player, Vote vote) {
        if (!player.isOnline()) return;
        String playerName = player.getName();
        
        Faction faction = FactionManager.getByMember(playerName);
        if (faction == null) return;
        
        Council council = faction.getGovernment().getCouncil();
        Session session = getSession(council);
        
        if (session != null && session.isStarted()) {
            // Check if player is within 10 blocks of the lantern
            if (session.getLantern() != null) {
                org.bukkit.Location playerLocation = player.getLocation();
                org.bukkit.Location lanternLocation = session.getLantern().getLocation();
                if (!java.util.Objects.equals(playerLocation.getWorld(), lanternLocation.getWorld())
                        || playerLocation.distanceSquared(lanternLocation) > 100) {
                    player.sendMessage("§cYou must be within 10 blocks of the lantern to vote!");
                    return;
                }
            }
            
            if (session.recordVote(playerName, vote)) {
                player.sendMessage("§aYour vote (" + vote.getDisplay() + ") has been recorded!");
                Sound sound = switch (vote) {
                    case YAY -> Sound.BLOCK_NOTE_BLOCK_CHIME;
                    case NAY -> Sound.BLOCK_NOTE_BLOCK_BASS;
                    case ABSTAIN -> Sound.ITEM_BOOK_PAGE_TURN;
                };
                player.getWorld().playSound(player.getLocation(), sound, 1f, 1f);
                player.swingMainHand();
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        // Check if this player is a session leader
        for (Session session : sessions.values()) {
            if (session.getLeader().getName().equals(playerName)) {
                session.kill();
                return;
            }
        }
    }
}
