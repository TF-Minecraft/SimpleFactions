package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.checkerframework.checker.units.qual.C;

import me.Plugins.SimpleFactions.War.civilwar.CivilWarStartService;
import me.Plugins.SimpleFactions.War.resolution.CouncilPeaceQueries;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.enums.Member;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.MovementCrackdownQueries;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeService;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeSource;
import me.Plugins.SimpleFactions.government.movement.Phase;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class MovementView {
    public InventoryManager inv;
    
    public MovementCreator creator = new MovementCreator();
    
    private static final List<Integer> SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25
    );
    
    public MovementView(InventoryManager inv) {
        this.inv = inv;
    }
    
    public void movementView(Player player, Faction f, Movement movement, Inventory i) {
        boolean open = i == null;
        if (i == null) {
            i = Bukkit.createInventory(new SFInventoryHolder(movement.getId(), SFGUI.MOVEMENT_VIEW), 54, "Movement: " + movement.getLeader());
        }
        i.clear();
        
        // Movement leader icon
        i.setItem(10, creator.createMovementLeaderItem(player, movement));
        
        // Organization icon
        i.setItem(11, creator.createOrganizationItem(movement));
        
        // Causes button (red banner)
        i.setItem(13, creator.createCausesButton(movement));
        
        // Join as supporter button
        i.setItem(15, creator.createJoinAsSupporterButton(player, movement));
        
        // Join as foreign backer button
        i.setItem(16, creator.createJoinAsForeignBackerButton(player, movement));

        if(movement.isLeader(player.getName())) {
            i.setItem(19, creator.createSendDemandsItem(movement));
            i.setItem(34, creator.createEndMovmentItem(movement));
        }
        if(player.getName().equalsIgnoreCase(f.getLeader())) {
            i.setItem(25, creator.createDemandDisbandItem(f, movement));
        }

        int x = 28;
        for(Phase phase : Phase.values()) {
            i.setItem(x, creator.createPhaseItem(player, phase, movement));
            x++;
        }
        
        // Back button
        i.setItem(53, inv.createBackButton(SFGUI.MOVEMENT_VIEW));
        
        if (open) {
            player.openInventory(i);
        }
    }
    
    public void causesView(Player player, Faction f, Movement movement, Inventory i) {
        boolean open = i == null;
        if (i == null) {
            i = Bukkit.createInventory(new SFInventoryHolder(movement.getId(), SFGUI.CAUSES_VIEW), 54, "Causes");
        }
        i.clear();
        
        List<Cause> causes = movement.getCauses();
        final int MAX_CAUSES = 3;
        
        // Display up to 3 cause slots
        for (int index = 0; index < MAX_CAUSES; index++) {
            int slot = SLOTS.get(index);
            
            if (index < causes.size()) {
                // Active cause
                i.setItem(slot, creator.createCauseItem(causes.get(index), player, f));
            } else if (index == causes.size() && canPlayerCreateCause(player, movement)) {
                // Available slot for new cause
                i.setItem(slot, creator.createAvailableCauseSlot(movement, index));
            } else {
                // Empty slot (locked)
                i.setItem(slot, creator.createEmptyCauseSlot(index));
            }
        }
        
        // Back button
        i.setItem(53, inv.createBackButton(SFGUI.CAUSES_VIEW));
        
        if (open) {
            player.openInventory(i);
        }
    }
    
    public void causeView(Player player, Faction f, Movement movement, Cause cause, Inventory i) {
        boolean open = i == null;
        if (i == null) {
            i = Bukkit.createInventory(new SFInventoryHolder(movement.getId(), SFGUI.CAUSE_VIEW, cause.getIndex()), 54, "Cause Details");
        }
        i.clear();
        
        // Cause leader icon
        i.setItem(10, creator.createCauseLeaderItem(player, cause));
        
        // Proposal icon
        i.setItem(12, creator.createCauseProposalItem(cause, player, f));
        
        // Join cause button
        i.setItem(14, creator.createJoinCauseButton(player, cause));
        
        // Target selection button (2 rows below leader icon at slot 10)
        // Only show if this is a CHANGE_LEADER action
        if (cause.getProposal().needsTarget()) {
            i.setItem(28, creator.createTargetButton(player, cause));
        }
        
        // Back button
        i.setItem(53, inv.createBackButton(SFGUI.CAUSE_VIEW));
        
        if (open) {
            player.openInventory(i);
        }
    }

    public void targetSelectionView(Player player, Faction f, Movement movement, Cause cause, Inventory i) {
        boolean open = i == null;
        if (i == null) {
            i = Bukkit.createInventory(new SFInventoryHolder(movement.getId(), SFGUI.TARGET_SELECT, cause.getIndex()), 54, "§7Select New Leader");
        }
        i.clear();
        
        int x = 0;
        List<String> members = f.getMembers();
        members.addAll(f.getVassalMembers());
        
        for (String member : members) {
            if (!f.canBecomeLeader(member)) continue;
            if (x >= SLOTS.size()) break;
            i.setItem(SLOTS.get(x), creator.createPotentialTargetItem(player, f, member, cause.getIndex()));
            x++;
        }
        
        i.setItem(53, inv.createBackButton(SFGUI.TARGET_SELECT));
        if (open) {
            player.openInventory(i);
        }
    }
    
    public void movementListView(Player player, Faction f, Inventory i) {
        boolean open = i == null;
        if (i == null) {
            i = Bukkit.createInventory(new SFInventoryHolder(f.getId(), SFGUI.MOVEMENT_LIST), 54, "Active Movements");
        }
        i.clear();
        
        int x = 0;
        List<Movement> movements = f.getGovernment().getMovements();
        for (int slot : SLOTS) {
            if (x >= movements.size()) break;
            i.setItem(slot, creator.createMovementListItem(movements.get(x)));
            x++;
        }
        
        // Back button
        i.setItem(53, inv.createBackButton(SFGUI.MOVEMENT_LIST));
        
        if (open) {
            player.openInventory(i);
        }
    }

    public void demandsView(Player player, Faction f, Movement movement, Inventory i) {
        boolean open = i == null;
        if (i == null) {
            i = Bukkit.createInventory(new SFInventoryHolder(movement.getId(), SFGUI.MOVEMENT_DEMANDS), 54, "Movement Demands");
        }
        i.clear();
        
        // Display each cause/demand
        int slot = 10;
        for (Cause cause : movement.getCauses()) {
            i.setItem(slot, creator.createDemandItem(cause, movement));
            slot++;
        }
        
        // Power display
        i.setItem(13, creator.createMovementPowerItem(movement));
        
        // Warning item
        i.setItem(22, creator.createDecliningWarningItem());
        
        // Accept button (green concrete)
        i.setItem(29, creator.createAcceptDemandsButton(movement));
        
        // Decline button (red concrete)
        i.setItem(33, creator.createDeclineDemandsButton(movement));
        
        if (open) {
            player.openInventory(i);
        }
    }

    public void crackdownView(Player player, Faction f, Movement movement, Inventory i) {
        boolean open = i == null;
        if (i == null) {
            i = Bukkit.createInventory(new SFInventoryHolder(movement.getId(), SFGUI.MOVEMENT_CRACKDOWN), 54, "Demand Disband");
        }
        i.clear();

        int slot = 10;
        for (Cause cause : movement.getCauses()) {
            i.setItem(slot, creator.createDemandItem(cause, movement));
            slot++;
        }

        i.setItem(13, creator.createMovementPowerItem(movement));
        i.setItem(22, creator.createRefuseDisbandWarningItem());
        i.setItem(29, creator.createAcceptDisbandButton(movement));
        i.setItem(33, creator.createRefuseDisbandButton(movement));

        if (open) {
            player.openInventory(i);
        }
    }
    
    public void click(InventoryClickEvent e, Inventory inventory, Player p) {
        ItemStack item = e.getCurrentItem();
        if (item == null) return;
        
        SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
        if (holder == null) return;
        
        e.setCancelled(true);
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        SFGUI gui = holder.getType();
        Faction f = null;
        Movement movement = null;
        
        // MOVEMENT_LIST stores faction ID in holder, movement ID in item metadata
        if (gui == SFGUI.MOVEMENT_LIST) {
            f = FactionManager.getByString(holder.getId());
            if (f == null) return;
            
            if (meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
                String movementId = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
                movement = f.getGovernment().getMovementById(movementId);
            }
        } else {
            // Other movement GUIs store movement ID in holder
            movement = FactionManager.getMovementById(holder.getId());
            if (movement == null) return;
            f = movement.getFaction();
            if (f == null) return;
        }

        if (movement == null) {
            return;
        }
        
        switch (gui) {
            case MOVEMENT_LIST:
                handleMovementListClick(e, movement, p, f, inventory, meta);
                break;
            case MOVEMENT_VIEW:
                handleMovementViewClick(e, movement, p, f, inventory, meta);
                break;
            case CAUSES_VIEW:
                handleCausesViewClick(e, movement,  p, f, inventory, meta);
                break;
            case CAUSE_VIEW:
                handleCauseViewClick(e, movement, p, f, inventory, meta);
                break;
            case MOVEMENT_DEMANDS:
                handleDemandsViewClick(e, movement, p, f, inventory, meta);
                break;
            case MOVEMENT_CRACKDOWN:
                handleCrackdownViewClick(e, movement, p, f, inventory, meta);
                break;
            case TARGET_SELECT:
                handleTargetSelectClick(e, movement, p, f, inventory, meta);
                break;
            default:
                break;
        }
    }
    
    private void handleMovementListClick(InventoryClickEvent e, Movement movement, Player p, Faction f, Inventory inventory, ItemMeta meta) {
        // Check if a movement was clicked
        movementView(p, f, movement, null);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
    }
    
    private void handleMovementViewClick(InventoryClickEvent e, Movement movement, Player p, Faction f, Inventory inventory, ItemMeta meta) {
        int slot = e.getSlot();
        //leader
        if (slot == 10) {
            if(!movement.hasLeader() && movement.canBeLeader(p.getName())) {
                movement.setLeader(p.getName());
                movementView(p, f, movement, inventory);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
            }
        }
        
        // Causes button
        if (slot == 13) {
            causesView(p, f, movement, null);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
        }
        // Join as supporter button
        else if (slot == 15) {
            Object supporter = getSupporterObject(p, movement);
            if (supporter != null) {
                movement.leave(supporter, null);
                if (movement.isLeader(p.getName())) {
                    movement.setLeader(null);
                }
                movementView(p, f, movement, inventory);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
                return;
            }
            if(!movement.quickJoinCheck(p)) return;
            Object joiningAs = movement.getJoiningAs(p);
            if(joiningAs == null) return;
            String block = movement.joinBlockReason(joiningAs, null, false);
            if(block != null) {
                p.sendMessage(block);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            FactionManager.requestMovementJoin(p, movement, "supporter", null);
            movementView(p, f, movement, inventory);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
        }
        // Join as foreign backer button
        else if (slot == 16) {
            Faction playerFaction = FactionManager.getByMember(p.getName());
            if (playerFaction != null && !playerFaction.getId().equals(f.getId())) {
                if (movement.getForeignBackers().contains(playerFaction)) {
                    if (getSupporterObject(p, movement) != null) {
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                        p.sendMessage(StringFormatter.formatHex("§cYou cannot leave as a foreign backer while you are a supporter."));
                        return;
                    }
                    movement.leaveAsForeignBacker(playerFaction);
                } else {
                    String backerBlock = movement.foreignBackerBlockReason(playerFaction, false);
                    if(backerBlock != null) {
                        p.sendMessage(backerBlock);
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                        return;
                    }
                    FactionManager.requestMovementJoin(p, movement, "foreign_backer", null);
                }
                movementView(p, f, movement, inventory);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
            }
        }
        // Send demands button
        else if (slot == 19) {
            if (movement.isLeader(p.getName()) && movement.getOrganization() >= 100) {
                for(Cause cause : movement.getCauses()) {
                    if(cause.getProposal().isPoliticalActionProposal() && cause.getProposal().needsTarget()) {
                        if(!cause.getProposal().hasTarget()) {
                            p.sendMessage(StringFormatter.formatHex("§cOne or more causes lack a target!"));
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                            return;
                        }
                    }
                }
                Player factionLeader = Bukkit.getPlayer(f.getLeader());
                if (factionLeader != null && factionLeader.isOnline()) {
                    LogManager.movement(
                            "SEND_DEMANDS movementId=%s faction=%s power=%.1f",
                            movement.getId(),
                            f.getId(),
                            movement.getPower());
                    demandsView(factionLeader, f, movement, null);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.5f);
                    p.sendMessage(StringFormatter.formatHex("§7Demands sent to " + f.getLeader()));
                } else {
                    p.sendMessage(StringFormatter.formatHex("§cThe faction leader must be online to send demands!"));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                }
            }
        }
        else if (slot == 25) {
            if (!p.getName().equalsIgnoreCase(f.getLeader())) {
                return;
            }
            if (!MovementCrackdownQueries.canCrush(f, movement)) {
                p.sendMessage(StringFormatter.formatHex("§c" + MovementCrackdownQueries.denyReason(f, movement)));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            if (!movement.hasLeader()) {
                p.sendMessage(StringFormatter.formatHex("§cThe movement leader must be online to demand a disband."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            Player movementLeader = Bukkit.getPlayer(movement.getLeader());
            if (movementLeader == null || !movementLeader.isOnline()) {
                p.sendMessage(StringFormatter.formatHex("§cThe movement leader must be online to demand a disband."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            LogManager.movement(
                    "CRUSH_SEND movementId=%s faction=%s power=%.1f",
                    movement.getId(),
                    f.getId(),
                    movement.getPower());
            crackdownView(movementLeader, f, movement, null);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.5f);
            p.sendMessage(StringFormatter.formatHex("§7Disband demand sent to " + movement.getLeader()));
        }
        // Phase buttons (slots 28-31)
        else if (slot >= 28 && slot <= 31) {
            ItemStack item = e.getCurrentItem();
            if (item != null && item.getType() == Material.YELLOW_CONCRETE && movement.getLeader().equalsIgnoreCase(p.getName())) {
                if (meta.getPersistentDataContainer().has(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING)) {
                    String phaseName = meta.getPersistentDataContainer().get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING);
                    try {
                        Phase targetPhase = Phase.valueOf(phaseName);
                        if (movement.canChangeToPhase(targetPhase)) {
                            movement.setPhase(targetPhase);
                            movementView(p, f, movement, inventory);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.5f);
                            p.sendMessage(StringFormatter.formatHex("§7Phase changed to " + targetPhase.getDisplayName()));
                        }
                    } catch (IllegalArgumentException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        else if (slot == 34) {
            if(!movement.isLeader(p.getName())) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            inv.confirming.put(p, f);
            inv.confirmEndMovementView(p, movement);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
        }
    }
    
    private void handleCausesViewClick(InventoryClickEvent e, Movement movement, Player p, Faction f, Inventory inventory, ItemMeta meta) {
        ItemStack item = e.getCurrentItem();
        if (item == null) return;
        
        // Check if clicking on an available cause slot
        if (item.getType() == Material.YELLOW_CONCRETE && 
            meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) {
            String action = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
            if ("CREATE_CAUSE".equals(action)) {
                inv.proposalView(p, f, null);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                return;
            }
        }
        
        // Check if a cause was clicked
        if (meta.getPersistentDataContainer().has(Keys.INT, PersistentDataType.INTEGER)) {
            int index = meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER);
            if (index < movement.getCauses().size()) {
                Cause cause = movement.getCauses().get(index);
                if (cause != null) {
                    causeView(p, f, movement, cause, null);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
                }
            }
        }
    }
    
    private void handleCauseViewClick(InventoryClickEvent e, Movement movement, Player p, Faction f, Inventory inventory, ItemMeta meta) {
        int slot = e.getSlot();
        Cause cause = null;
        if (meta.getPersistentDataContainer().has(Keys.INT, PersistentDataType.INTEGER)) {
            int index = meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER);
            cause = movement.getCauses().get(index);
        }
        if (cause == null) {
            return;
        }
        if(slot == 10) {
            if(!cause.hasLeader() && cause.canBeLeader(p.getName())) {
                cause.setLeader(p.getName());
                causeView(p, f, movement, cause, inventory);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
            }
        }
        // Join cause button
        else if (slot == 14) {
            Object joiningAs = movement.getJoiningAs(p);
            if(joiningAs == null) return;
            if (cause.getFullMemberList().contains(p.getName())) {
                movement.leave(joiningAs, cause);
                if (cause.hasLeader() && cause.getLeader().equals(p.getName())) {
                    cause.setLeader(null);
                }
                if (movement.isLeader(p.getName())) {
                    movement.setLeader(null);
                }
                causeView(p, f, movement, cause, inventory);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
                return;
            }
            if(!movement.quickJoinCheck(p)) return;
            String causeBlock = movement.joinBlockReason(joiningAs, cause, false);
            if(causeBlock != null) {
                p.sendMessage(causeBlock);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            FactionManager.requestMovementJoin(p, movement, "member", null);
            causeView(p, f, movement, cause, inventory);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
        }
        // Target selection button
        else if (slot == 28) {
            if (cause.getProposal().needsTarget() && cause.hasLeader() && cause.getLeader().equals(p.getName())) {
                if (CouncilPeaceQueries.isWarEndAction(cause.getProposal().getPoliticalAction().getAction())) {
                    inv.governmentView.warPeaceSelectView(
                            p, f, cause.getProposal().getPoliticalAction().getAction(), true, cause.getIndex(), null);
                } else {
                    targetSelectionView(p, f, movement, cause, null);
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
            }
        }
    }

    private void handleTargetSelectClick(InventoryClickEvent e, Movement movement, Player p, Faction f, Inventory inventory, ItemMeta meta) {
        ItemStack item = e.getCurrentItem();
        if (item == null) return;
        
        if (!meta.getPersistentDataContainer().has(Keys.STRING_KEY, PersistentDataType.STRING)) return;
        if (!meta.getPersistentDataContainer().has(Keys.INT, PersistentDataType.INTEGER)) return;
        
        String targetName = meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING);
        int causeIndex = meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER);
        
        if (causeIndex >= movement.getCauses().size()) return;
        Cause cause = movement.getCauses().get(causeIndex);
        
        if (cause == null) return;
        if (!cause.hasLeader() || !cause.getLeader().equals(p.getName())) return;
        
        // Validate target can become leader
        if (!f.canBecomeLeader(targetName)) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            p.sendMessage(StringFormatter.formatHex("§c" + targetName + " cannot become the faction leader!"));
            return;
        }

        FactionManager.requestMovementLeaderTarget(p, movement, cause, targetName);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.5f);
        causeView(p, f, movement, cause, null);
    }

    private void handleDemandsViewClick(InventoryClickEvent e, Movement movement, Player p, Faction f, Inventory inventory, ItemMeta meta) {
        int slot = e.getSlot();
        
        // Accept button (green concrete)
        if (slot == 29) {
            if (p.getName().equalsIgnoreCase(f.getLeader())) {
                p.sendMessage(StringFormatter.formatHex("§aYou have accepted the movement's demands."));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2f);
                LogManager.movement(
                        "DEMANDS_ACCEPT movementId=%s faction=%s power=%.1f",
                        movement.getId(),
                        f.getId(),
                        movement.getPower());
                
                // Notify movement leader if online
                if (movement.hasLeader()) {
                    Player movementLeader = Bukkit.getPlayer(movement.getLeader());
                    if (movementLeader != null && movementLeader.isOnline()) {
                        movementLeader.sendMessage(StringFormatter.formatHex("§a" + f.getLeader() + " has accepted your demands!"));
                    }
                }

                MovementOutcomeService.apply(movement, MovementOutcomeSource.ACCEPTED);
                p.closeInventory();
            }
        }
        // Decline button (red concrete)
        else if (slot == 33) {
            if (p.getName().equalsIgnoreCase(f.getLeader())) {
                LogManager.movement(
                        "DEMANDS_REJECT movementId=%s faction=%s power=%.1f",
                        movement.getId(),
                        f.getId(),
                        movement.getPower());
                String error = CivilWarStartService.start(movement);
                if (error != null) {
                    p.sendMessage(error);
                    p.playSound(p.getLocation(), "entity.villager.no", 1, 1f);
                    return;
                }
                p.sendMessage(StringFormatter.formatHex("§cYou have declined the movement's demands."));
                p.sendMessage(StringFormatter.formatHex("§7A civil war has begun!"));
                p.playSound(p.getLocation(), "entity.ender_dragon.growl", 1, 0.8f);
                
                // Notify movement leader if online
                if (movement.hasLeader()) {
                    Player movementLeader = Bukkit.getPlayer(movement.getLeader());
                    if (movementLeader != null && movementLeader.isOnline()) {
                        movementLeader.sendMessage(StringFormatter.formatHex("§c" + f.getLeader() + " has declined your demands!"));
                        movementLeader.sendMessage(StringFormatter.formatHex("§7A civil war has begun!"));
                    }
                }

                p.closeInventory();
            }
        }
    }

    private void handleCrackdownViewClick(InventoryClickEvent e, Movement movement, Player p, Faction f, Inventory inventory, ItemMeta meta) {
        int slot = e.getSlot();
        if (!movement.isLeader(p.getName())) {
            return;
        }
        if (slot == 29) {
            p.sendMessage(StringFormatter.formatHex("§aYou have disbanded the movement."));
            p.playSound(p.getLocation(), "block.note_block.pling", 1, 2f);
            LogManager.movement(
                    "CRUSH_ACCEPT movementId=%s faction=%s power=%.1f",
                    movement.getId(),
                    f.getId(),
                    movement.getPower());
            Player factionLeader = Bukkit.getPlayer(f.getLeader());
            if (factionLeader != null && factionLeader.isOnline()) {
                factionLeader.sendMessage(StringFormatter.formatHex("§a" + p.getName() + " disbanded their movement."));
            }
            f.getGovernment().endMovement(movement);
            p.closeInventory();
        } else if (slot == 33) {
            LogManager.movement(
                    "CRUSH_REFUSE movementId=%s faction=%s power=%.1f",
                    movement.getId(),
                    f.getId(),
                    movement.getPower());
            String error = CivilWarStartService.start(movement);
            if (error != null) {
                p.sendMessage(error);
                p.playSound(p.getLocation(), "entity.villager.no", 1, 1f);
                return;
            }
            p.sendMessage(StringFormatter.formatHex("§cYou have refused to disband."));
            p.sendMessage(StringFormatter.formatHex("§7A civil war has begun!"));
            p.playSound(p.getLocation(), "entity.ender_dragon.growl", 1, 0.8f);
            Player factionLeader = Bukkit.getPlayer(f.getLeader());
            if (factionLeader != null && factionLeader.isOnline()) {
                factionLeader.sendMessage(StringFormatter.formatHex("§c" + p.getName() + " refused to disband!"));
                factionLeader.sendMessage(StringFormatter.formatHex("§7A civil war has begun!"));
            }
            p.closeInventory();
        }
    }

    private boolean canPlayerCreateCause(Player p, Movement movement) {
        if (movement.isFrozen()) return false;
        // Check if player is a supporter
        Object supporter = getSupporterObject(p, movement);
        if (supporter == null) return false;
        
        // Check if movement has room for more causes
        if (movement.getCauses().size() >= 3) return false;
        
        return true;
    }

    private Object getSupporterObject(Player p, Movement movement) {
        String playerName = p.getName();
        
        // Check if player is in supporters as a citizen
        if (movement.getSupporters().getCitizens().contains(playerName)) {
            return playerName;
        }
        
        // Check if player's guild is a supporter
        Member relation = movement.getFaction().getRelationToFaction(playerName);
        if (relation == Member.GUILD_LEADER || relation == Member.GUILD_MEMBER) {
            Guild guild = FactionManager.getGuildByMember(playerName);
            if (guild != null && movement.getSupporters().getGuilds().contains(guild)) {
                return guild;
            }
        }
        
        // Check if player's faction is a supporter
        if (relation == Member.VASSAL_LEADER || relation == Member.VASSAL_MEMBER) {
            Faction faction = FactionManager.getByMember(playerName);
            if (faction != null && movement.getSupporters().getFactions().contains(faction)) {
                return faction;
            }
        }
        
        return null;
    }

    public void handleEndConfirm(Player player, String movementId, boolean confirmed) {
        Faction f = inv.confirming.remove(player);
        Movement movement = FactionManager.getMovementById(movementId);
        if (movement == null) {
            if (f != null) {
                movementListView(player, f, null);
            } else {
                player.closeInventory();
            }
            return;
        }
        f = movement.getFaction();
        if (!confirmed) {
            movementView(player, f, movement, null);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1, 1);
            return;
        }
        if (!movement.isLeader(player.getName())) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            movementView(player, f, movement, null);
            return;
        }
        f.getGovernment().endMovement(movement);
        movementListView(player, f, null);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        player.sendMessage(StringFormatter.formatHex("§cThe movement has been disbanded."));
    }
}
