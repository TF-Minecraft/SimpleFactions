package net.tfminecraft.simplefactions.plots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;
import net.tfminecraft.rpcharacters.enums.Status;
import net.tfminecraft.rpcharacters.holder.RPCHolder;
import net.tfminecraft.rpcharacters.lifecycle.CharacterActivatedEvent;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.permadeath.CharacterPermakillEvent;
import net.tfminecraft.rpcharacters.permadeath.PermakillCause;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings({"deprecation", "removal"})
class PlotListenerTest {
    @TempDir Path directory;
    private PlotTestWorld world;
    private Player master, assassin, target;
    private final PlotListener listener = new PlotListener();
    private final PlotCharacterListener characters = new PlotCharacterListener();

    @BeforeEach
    void setup() throws Exception {
        world = new PlotTestWorld(directory);
        master = world.player("Master", "c-master", 50);
        assassin = world.player("Assassin", "c-assassin", 60);
        target = world.player("Target", "c-target", 40);
    }

    @AfterEach
    void close() { world.close(); }

    private Plot executing() {
        Plot plot = PlotService.request(master);
        PlotService.approve(mock(CommandSender.class), plot.code, "Target");
        PlotService.invite(master, assassin);
        PlotService.accept(assassin, plot.code);
        PlotService.assign(master, "c-assassin", PlotRole.ASSASSIN, null);
        plot.phase = PlotPhase.EXECUTION;
        return plot;
    }

    @Test
    void plotMenuClicksRunTheirSlotActionOnly() {
        PlotMenu.open(master);
        InventoryClickEvent outside = world.gui.click(master, 60);
        listener.onClick(outside);
        assertTrue(outside.isCancelled());
        InventoryClickEvent empty = world.gui.click(master, 0);
        listener.onClick(empty);
        assertTrue(empty.isCancelled());
        listener.onClick(world.gui.click(master, 2));
        assertEquals(PlotApproach.DEFENSIVE, world.store().peek("c-master").approach());
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        InventoryView view = master.getOpenInventory();
        when(drag.getView()).thenReturn(view);
        listener.onDrag(drag);
        org.mockito.Mockito.verify(drag).setCancelled(true);
        master.closeInventory();
        InventoryClickEvent other = world.gui.click(master, 0);
        listener.onClick(other);
        assertFalse(other.isCancelled());
        InventoryDragEvent otherDrag = mock(InventoryDragEvent.class);
        InventoryView closed = master.getOpenInventory();
        when(otherDrag.getView()).thenReturn(closed);
        listener.onDrag(otherDrag);
        org.mockito.Mockito.verify(otherDrag, org.mockito.Mockito.never()).setCancelled(true);
    }

    private static EntityDamageByEntityEvent blow(org.bukkit.entity.Entity victim, org.bukkit.entity.Entity damager) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(damager);
        return event;
    }

    @Test
    void blowsAndDeathsReachThePlots() {
        Plot plot = executing();
        Zombie zombie = mock(Zombie.class);
        listener.onDamage(blow(assassin, zombie));
        listener.onDamage(blow(zombie, assassin));
        listener.onDamage(blow(target, target));
        Arrow stray = mock(Arrow.class);
        listener.onDamage(blow(target, stray));
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(assassin);
        listener.onDamage(blow(target, arrow));
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(target);
        listener.onDeath(death);
        assertEquals(PlotPhase.SUCCEEDED, plot.phase);
        listener.onDamage(blow(target, assassin));
    }

    @Test
    void returningPlayersGetTheirMessages() {
        PlotService.tell(world.character(master), "waiting");
        world.logOff(master);
        PlotService.tell(new PlotCharacter("c-master", master.getUniqueId(), "Master", "M"), "later");
        world.active.put(master.getUniqueId(), new PlotCharacter("c-master", master.getUniqueId(), "Master", "M"));
        listener.onJoin(new PlayerJoinEvent(master, "joined"));
        when(master.isOnline()).thenReturn(false);
        listener.onJoin(new PlayerJoinEvent(master, "joined"));
        world.gui.runTasks();
        assertFalse(world.heard(master, "later"));
        when(master.isOnline()).thenReturn(true);
        listener.onJoin(new PlayerJoinEvent(master, "joined"));
        world.gui.runTasks();
        assertTrue(world.heard(master, "later"));
    }

    private Inventory characterInfo(Player viewer, Player owner, String title, ItemStack shown) {
        Inventory inventory = world.gui.inventory(new RPCHolder(owner), 27, title);
        inventory.setItem(10, shown);
        viewer.openInventory(inventory);
        return inventory;
    }

    private ItemStack shown(String characterId) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        var meta = item.getItemMeta();
        meta.setDisplayName("Character");
        if (characterId != null)
            meta.getPersistentDataContainer().set(PlotCharacterListener.CHARACTER_ID, PersistentDataType.STRING, characterId);
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void characterInfoShowsIntrigueAndAPlotsButtonForThePlayedCharacter() {
        Inventory info = characterInfo(master, master, PlotCharacterListener.CHARACTER_INFO, shown("c-master"));
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertEquals("§5Intrigue: §e50/100", info.getItem(20).getItemMeta().getDisplayName());
        assertEquals("§5Plots", info.getItem(24).getItemMeta().getDisplayName());
        InventoryClickEvent button = world.gui.click(master, 24);
        characters.onClick(button);
        assertTrue(button.isCancelled());
        world.gui.runTasks();
        assertTrue(master.getOpenInventory().getTopInventory().getHolder() instanceof PlotMenu.Holder);

        Inventory other = characterInfo(master, master, PlotCharacterListener.CHARACTER_INFO, shown("c-old"));
        world.intrigue.put("c-old", 33);
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertEquals("§5Intrigue: §e33/100", other.getItem(20).getItemMeta().getDisplayName());
        assertNull(other.getItem(24));
        InventoryClickEvent nothing = world.gui.click(master, 24);
        characters.onClick(nothing);
        assertFalse(nothing.isCancelled());
        other.setItem(24, shown(null));
        characters.onClick(world.gui.click(master, 24));
        other.setItem(24, new ItemStack(Material.STONE));
        characters.onClick(world.gui.click(master, 24));
        characters.onClick(world.gui.click(master, 23));
        characters.onClick(world.gui.click(master, 40));

        world.logOff(master);
        Inventory loggedOff = characterInfo(master, master, PlotCharacterListener.CHARACTER_INFO, shown("c-master"));
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertNull(loggedOff.getItem(24));
    }

    @Test
    void otherMenusAndUnmarkedCharactersAreLeftAlone() {
        Inventory foreign = characterInfo(master, target, PlotCharacterListener.CHARACTER_INFO, shown("c-target"));
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertNull(foreign.getItem(20));
        Inventory titled = characterInfo(master, master, "§7Trait List", shown("c-master"));
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertNull(titled.getItem(20));
        Inventory plain = world.gui.inventory(null, 27, PlotCharacterListener.CHARACTER_INFO);
        master.openInventory(plain);
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        characters.onClick(world.gui.click(master, 24));
        Inventory blank = characterInfo(master, master, PlotCharacterListener.CHARACTER_INFO, null);
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertNull(blank.getItem(20));
        Inventory bare = characterInfo(master, master, PlotCharacterListener.CHARACTER_INFO, new ItemStack(Material.ENDER_PEARL));
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertNull(bare.getItem(20));
        Inventory unmarked = characterInfo(master, master, PlotCharacterListener.CHARACTER_INFO, shown(null));
        characters.onOpen(new InventoryOpenEvent(master.getOpenInventory()));
        assertNull(unmarked.getItem(20));
        InventoryView view = mock(InventoryView.class);
        org.bukkit.entity.HumanEntity human = mock(org.bukkit.entity.HumanEntity.class);
        when(view.getPlayer()).thenReturn(human);
        characters.onOpen(new InventoryOpenEvent(view));
        InventoryClickEvent humanClick = mock(InventoryClickEvent.class);
        when(humanClick.getWhoClicked()).thenReturn(human);
        characters.onClick(humanClick);
    }

    @Test
    void charactersDyingOrSwitchingReachThePlots() {
        Plot plot = executing();
        RPCharacter dying = mock(RPCharacter.class);
        when(dying.getId()).thenReturn("c-master");
        when(dying.getStatus()).thenReturn(Status.ALIVE);
        characters.onPermakill(new CharacterPermakillEvent(master, dying, PermakillCause.OTHER));
        world.gui.runTasks();
        assertEquals(PlotPhase.EXECUTION, plot.phase);
        when(dying.getStatus()).thenReturn(Status.DEAD);
        characters.onPermakill(new CharacterPermakillEvent(master, dying, PermakillCause.OTHER));
        world.gui.runTasks();
        assertEquals(PlotPhase.DISSOLVED, plot.phase);

        PlotService.tell(new PlotCharacter("c-target", target.getUniqueId(), "Target", "T"), "queued");
        world.logOff(target);
        PlotService.tell(new PlotCharacter("c-target", target.getUniqueId(), "Target", "T"), "held");
        world.active.put(target.getUniqueId(), new PlotCharacter("c-target", target.getUniqueId(), "Target", "T"));
        characters.onActivated(new CharacterActivatedEvent(target, target.getUniqueId(), mock(RPCharacter.class), null));
        characters.onActivated(new CharacterActivatedEvent(null, UUID.randomUUID(), mock(RPCharacter.class), null));
        world.gui.runTasks();
        assertTrue(world.heard(target, "held"));
    }

    @Test
    void anInventoryClickOutsideTheTopDoesNothing() {
        PlotMenu.open(master);
        InventoryClickEvent bottom = new InventoryClickEvent(master.getOpenInventory(),
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 60, ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        listener.onClick(bottom);
        assertTrue(bottom.isCancelled());
    }
}
