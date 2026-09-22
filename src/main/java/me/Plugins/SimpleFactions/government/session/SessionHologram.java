package me.Plugins.SimpleFactions.government.session;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.TextDisplay;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

import org.bukkit.entity.EntityType;

public class SessionHologram {
    private Block lantern;
    private TextDisplay line1; // "Proposal x/y"
    private TextDisplay line2; // "Click to view proposal"
    private TextDisplay line3; // " x Yay y Nay z Abstain"
    
    public SessionHologram(Block lanternBlock) {
        this.lantern = lanternBlock;
    }
    
    public void create() {
        if (lantern == null) return;
        
        Location baseLoc = lantern.getLocation().clone().add(0.5, 1.0, 0.5);
        
        // Line 1: "Proposal x/y"
        line1 = (TextDisplay) lantern.getWorld().spawnEntity(baseLoc.clone().add(0, 0.6, 0), EntityType.TEXT_DISPLAY);
        line1.setText("§bProposal 1/1");
        line1.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
        line1.setBillboard(org.bukkit.entity.TextDisplay.Billboard.CENTER);
        
        // Line 2: "Click to view proposal"
        line2 = (TextDisplay) lantern.getWorld().spawnEntity(baseLoc.clone().add(0, 0.3, 0), EntityType.TEXT_DISPLAY);
        line2.setText(StringFormatter.formatHex("#5ca3bdClick to view proposal"));
        line2.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
        line2.setBillboard(org.bukkit.entity.TextDisplay.Billboard.CENTER);
        
        // Line 3: " x Yay y Nay z Abstain"
        line3 = (TextDisplay) lantern.getWorld().spawnEntity(baseLoc.clone(), EntityType.TEXT_DISPLAY);
        line3.setText("§a0 §aYay  §c0 §cNay  §70 §7Abstain");
        line3.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
        line3.setBillboard(org.bukkit.entity.TextDisplay.Billboard.CENTER);
    }
    
    public void updateProposalInfo(int current, int total) {
        if (line1 != null) {
            line1.setText(StringFormatter.formatHex("#57c274Proposal #c7b89d" + current + "/" + total));
        }
    }
    
    public void updateVotes(int yay, int nay, int abstain) {
        if (line3 != null) {
            line3.setText("§a" + yay + " Yay  §c" + nay + " Nay  §e" + abstain + " Abstain");
        }
    }
    
    public void destroy() {
        if (line1 != null) line1.remove();
        if (line2 != null) line2.remove();
        if (line3 != null) line3.remove();
        
        line1 = null;
        line2 = null;
        line3 = null;
    }
}
