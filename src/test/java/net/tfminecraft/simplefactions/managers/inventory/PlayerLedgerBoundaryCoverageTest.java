package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.*;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.*;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class PlayerLedgerBoundaryCoverageTest {
  private GuiTestFixture gui;
  private MockedStatic<SimpleFactions> pluginApi;
  private PlayerEconomyManager economy;
  private Player player;

  @BeforeEach
  void setup() {
    gui = new GuiTestFixture();
    player = gui.player("Bookkeeper");
    when(org.bukkit.Bukkit.getOfflinePlayer(player.getUniqueId())).thenReturn(player);
    economy = new PlayerEconomyManager();
    pluginApi = mockStatic(SimpleFactions.class);
    pluginApi.when(SimpleFactions::getPlayerEconomyManager).thenReturn(economy);
  }

  @AfterEach
  void close() {
    pluginApi.close();
    gui.close();
  }

  @Test
  void ledgerCommandOpensRealLedgerAndRepaintingDoesNotOpenAnotherWindow() {
    PlayerLedger ledger = economy.getLedger(player.getUniqueId());
    ledger.add(PlayerCashflow.WAGES, 17);
    ledger.add(PlayerCashflow.CITIZEN_TAX, -2);
    InventoryManager manager = new InventoryManager();
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("LeDgEr");
    assertTrue(
        new LedgerCommandManager(manager).onCommand(player, command, "ledger", new String[0]));
    ArgumentCaptor<Inventory> opened = ArgumentCaptor.forClass(Inventory.class);
    verify(player).openInventory(opened.capture());
    Inventory inventory = opened.getValue();
    assertEquals(27, inventory.getSize());
    assertInstanceOf(SFInventoryHolder.class, inventory.getHolder());
    ItemStack book = inventory.getItem(13);
    assertEquals(Material.WRITABLE_BOOK, book.getType());
    assertTrue(book.getItemMeta().getLore().stream().anyMatch(x -> x.contains("+17.00")));
    for (int slot = 0; slot < 27; slot++)
      if (slot != 13)
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(slot).getType());
    ledger.clearDaily();
    manager.playerLedgerView.open(player, inventory);
    verify(player, times(1)).openInventory(inventory);
    assertTrue(
        inventory.getItem(13).getItemMeta().getLore().stream()
            .anyMatch(x -> x.contains("No income sources.")));
  }

  @Test
  void unrelatedCommandsAndConsoleNeverCreateAPlayerInventory() {
    LedgerCommandManager command = new LedgerCommandManager(new InventoryManager());
    Command other = mock(Command.class);
    when(other.getName()).thenReturn("other");
    assertFalse(command.onCommand(player, other, "other", new String[0]));
    Command ledger = mock(Command.class);
    when(ledger.getName()).thenReturn("ledger");
    CommandSender console = mock(CommandSender.class);
    assertTrue(command.onCommand(console, ledger, "ledger", new String[0]));
    verify(console).sendMessage("§cThis command can only be used by players.");
    verify(player, never()).openInventory(any(Inventory.class));
  }

  @Test
  void explicitLedgerBookMatchesAmountsAndCashflowClassification() {
    PlayerLedger ledger = new PlayerLedger();
    ledger.add(null, 100);
    ledger.add(PlayerCashflow.WAGES, 0);
    assertEquals(0, ledger.getAmount(null));
    ledger.add(PlayerCashflow.WAGES, 5);
    ledger.add(PlayerCashflow.WAGES, 2);
    ledger.add(PlayerCashflow.CITIZEN_TAX, -9);
    ItemStack book = new PlayerLedgerCreator().createLedgerBook(ledger, player.getUniqueId());
    assertEquals(Material.WRITABLE_BOOK, book.getType());
    assertEquals(-2, ledger.getNetDaily());
    assertEquals(java.util.List.of(PlayerCashflow.CITIZEN_TAX), ledger.getExpenseFlows());
    assertTrue(book.getItemMeta().getLore().stream().anyMatch(x -> x.contains("-2.00")));
    assertNotNull(PlayerCashflow.WAGES.getDisplay());
    assertTrue(PlayerCashflow.WAGES.isIncome());
  }
}
