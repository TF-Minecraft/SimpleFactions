package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Set;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.inventory.CharacterKeyFixture;
import net.tfminecraft.simplefactions.managers.inventory.WarView;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Menus belong to the character session that opened them. */
@SuppressWarnings("deprecation")
class InventoryManagerStaleMenuTest {
  private static final String STALE = "§cThat menu belonged to your previous character.";

  private GuiTestFixture gui;
  private MockedStatic<FactionManager> factions;
  private CharacterKeyFixture characters;
  private InventoryManager manager;
  private Player player;

  @BeforeEach
  void setUp() {
    gui = new GuiTestFixture();
    factions = mockStatic(FactionManager.class);
    characters = CharacterKeyFixture.install();
    manager = new InventoryManager();
    manager.warView = mock(WarView.class);
    player = gui.player("Ryan");
  }

  @AfterEach
  void close() {
    factions.close();
    gui.close();
  }

  @Test
  void aMenuFromThePreviousCharacterClosesAndRunsNoAction() {
    characters.play(player, "Aldric");
    open(warList());
    characters.play(player, "Bryn");

    InventoryClickEvent click = gui.click(player, 0);
    manager.clickButton(click);

    assertTrue(click.isCancelled());
    verify(player).closeInventory();
    verify(player).sendMessage(STALE);
    verify(manager.warView, never()).click(any(), any(), any());
  }

  @Test
  void theSameSessionKeepsUsingTheMenu() {
    characters.play(player, "Aldric");
    open(warList());

    manager.clickButton(gui.click(player, 0));
    manager.clickButton(gui.click(player, 0));

    verify(manager.warView, times(2)).click(any(), any(), eq(player));
    verify(player, never()).sendMessage(STALE);
  }

  @Test
  void anEndedSessionWithNoNewCharacterIsStale() {
    characters.play(player, "Aldric");
    open(warList());
    characters.end(player);

    manager.clickButton(gui.click(player, 0));

    verify(player).sendMessage(STALE);
    verify(manager.warView, never()).click(any(), any(), any());
  }

  @Test
  void aMenuOpenedWithoutACharacterNeverActs() {
    open(warList());

    manager.clickButton(gui.click(player, 0));

    verify(player).sendMessage(STALE);
    verify(manager.warView, never()).click(any(), any(), any());
  }

  @Test
  void aMenuOpenedBeforeCaptureTakesTheSessionOfItsFirstClick() {
    characters.play(player, "Aldric");
    player.openInventory(warList());

    manager.clickButton(gui.click(player, 0));
    verify(manager.warView).click(any(), any(), eq(player));

    characters.play(player, "Bryn");
    manager.clickButton(gui.click(player, 0));
    verify(player).sendMessage(STALE);
    verify(manager.warView, times(1)).click(any(), any(), any());
  }

  @Test
  void closingOrQuittingForgetsTheCapturedSession() {
    characters.play(player, "Aldric");
    open(warList());
    manager.releaseMenuSession(new InventoryCloseEvent(player.getOpenInventory()));
    characters.play(player, "Bryn");
    manager.clickButton(gui.click(player, 0));

    open(warList());
    manager.clearPendingOnQuit(new PlayerQuitEvent(player, "quit"));
    characters.play(player, "Cora");
    manager.clickButton(gui.click(player, 0));

    verify(manager.warView, times(2)).click(any(), any(), eq(player));
    verify(player, never()).sendMessage(STALE);
  }

  @Test
  void confirmationsAreCheckedAndOtherInventoriesAreNot() {
    characters.play(player, "Aldric");
    manager.confirmView(player, mock(Faction.class), "war_declare", "1");
    manager.captureMenuSession(new InventoryOpenEvent(player.getOpenInventory()));
    characters.play(player, "Bryn");
    InventoryClickEvent confirm = gui.click(player, 11);
    manager.clickButton(confirm);
    assertTrue(confirm.isCancelled());
    verify(player).sendMessage(STALE);

    Inventory chest = gui.inventory(null, 27, "Chest");
    player.openInventory(chest);
    manager.captureMenuSession(new InventoryOpenEvent(player.getOpenInventory()));
    characters.play(player, "Cora");
    InventoryClickEvent ordinary = gui.click(player, 0);
    manager.clickButton(ordinary);
    assertFalse(ordinary.isCancelled());
    verify(player, times(1)).sendMessage(STALE);
  }

  @Test
  void draggingInAStaleMenuIsCancelled() {
    characters.play(player, "Aldric");
    open(warList());
    characters.play(player, "Bryn");

    InventoryView view = player.getOpenInventory();
    InventoryDragEvent drag = mock(InventoryDragEvent.class);
    when(drag.getWhoClicked()).thenReturn(player);
    when(drag.getView()).thenReturn(view);
    when(drag.getRawSlots()).thenReturn(Set.of(60));
    manager.dragInWarGui(drag);

    verify(drag).setCancelled(true);
    verify(player).sendMessage(STALE);
  }

  private Inventory warList() {
    Inventory inventory = gui.inventory(new SFInventoryHolder("realm", SFGUI.WAR_LIST), 54, "Wars");
    inventory.setItem(0, new ItemStack(Material.PAPER));
    return inventory;
  }

  private void open(Inventory inventory) {
    player.openInventory(inventory);
    manager.captureMenuSession(new InventoryOpenEvent(player.getOpenInventory()));
  }
}
