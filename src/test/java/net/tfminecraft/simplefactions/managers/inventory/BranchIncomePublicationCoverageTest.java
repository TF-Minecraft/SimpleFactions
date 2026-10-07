package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.*;
import net.tfminecraft.simplefactions.guild.income.BranchIncomePreview;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class BranchIncomePublicationCoverageTest {
  private FactionDomainFixture fixture;
  private MockedStatic<net.tfminecraft.tlibs.TLibs> items;
  private Guild guild;
  private Branch branch;
  private ProvinceManager provinces;
  private Province capital;
  private Player player;
  private Inventory inventory;
  private BranchIncomePreview.Prepared prepared;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    fixture.provincesEnabled(true);
    guild = fixture.guild(fixture.saved("home", "Alice"), "fields", "Alice");
    capital = new Province(1, "PLAINS", 40, 0, 0);
    provinces = new ProvinceManager();
    provinces.start(Map.of(1, capital));
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(provinces);
    when(fixture.ui.plugin.isEnabled()).thenReturn(true);
    for (int n = 0; n < 3; n++) guild.getFaction().getOrCreateMainGuild().getBranch(0).levelUp();
    guild.setCapital(1, false);
    branch = guild.getBranch(0);
    branch.levelUp();
    branch.getModifiers().put(GuildModifier.PRODUCTION, new BranchModifier(8, 2));
    branch.getModifiers().put(GuildModifier.TRADE_CARRY, new BranchModifier(1, 0));
    player = fixture.player("Alice");
    items = mockStatic(net.tfminecraft.tlibs.TLibs.class);
    var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class);
    var creator = mock(net.tfminecraft.tlibs.objects.api.subapi.ItemCreator.class);
    when(api.getCreator()).thenReturn(creator);
    items.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
    when(creator.getItemsAdderItem(anyString())).thenAnswer(c -> new ItemStack(Material.PAPER));
    inventory =
        fixture.ui.inventory(new SFInventoryHolder("fields", SFGUI.GUILD_VIEW), 54, "Guild");
    player.openInventory(inventory);
    capital.clearData();
    guild.getTradeBreakdown().clear();
    prepared = BranchIncomePreview.prepare(provinces);
    fixture.ui.tasks.clear();
    fixture.ui.asyncTasks.clear();
  }

  @AfterEach
  void close() {
    if (items != null) items.close();
    fixture.close();
  }

  private void display(boolean up) {
    var creator = new GuildCreator();
    inventory.setItem(
        20,
        up
            ? creator.createBranchUpgradeItem(player, guild, branch)
            : creator.createBranchDowngradeItem(player, guild, branch));
  }

  private String lore() {
    return String.join(" ", inventory.getItem(20).getItemMeta().getLore());
  }

  private void schedule(boolean up) {
    BranchIncomePreviewService.schedule(
        player, inventory, 20, prepared, guild, branch, up ? 1 : -1);
  }

  private void compute() {
    assertEquals(1, fixture.ui.asyncTasks.size());
    fixture.ui.asyncTasks.remove(0).run();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void paintsComputedTradeChangeWithoutChangingLiveEconomy(boolean up) {
    display(up);
    double expected = BranchIncomePreview.estimate(prepared, guild, branch, up ? 1 : -1);
    assertTrue(up ? expected > 0 : expected < 0, "Preview needs a meaningful nonzero economy");
    double balance = guild.getBank().getWealth();
    schedule(up);
    assertTrue(lore().contains("Calculating"));
    assertNotNull(
        inventory
            .getItem(20)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.BRANCH_PREVIEW, PersistentDataType.LONG));
    compute();
    assertTrue(lore().contains("Calculating"), "Async worker must not write to a menu");
    fixture.ui.runTasks();
    assertTrue(lore().contains(String.format("%.2f", expected) + "d/day"));
    assertFalse(lore().contains("Calculating"));
    assertEquals(1, branch.getLevel());
    assertEquals(balance, guild.getBank().getWealth());
    assertTrue(capital.getAllData().isEmpty());
    assertEquals(0, guild.getTradeBreakdown().getIncome());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "offline",
        "closed",
        "level",
        "item_removed",
        "item_replaced",
        "direction",
        "branch",
        "token"
      })
  void staleCalculationCannotRewriteChangedMenu(String changed) {
    display(true);
    schedule(true);
    compute();
    switch (changed) {
      case "offline" -> when(player.isOnline()).thenReturn(false);
      case "closed" -> player.openInventory(fixture.ui.inventory(null, 9, "Another"));
      case "level" -> branch.levelUp();
      case "item_removed" -> inventory.setItem(20, null);
      case "item_replaced" -> display(true);
      default -> {
        ItemStack item = inventory.getItem(20);
        var meta = item.getItemMeta();
        if (changed.equals("direction"))
          meta.getPersistentDataContainer()
              .set(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN, false);
        if (changed.equals("branch"))
          meta.getPersistentDataContainer()
              .set(Keys.BRANCH_ID, PersistentDataType.STRING, "different");
        if (changed.equals("token")) meta.getPersistentDataContainer().remove(Keys.BRANCH_PREVIEW);
        item.setItemMeta(meta);
      }
    }
    fixture.ui.runTasks();
    if (changed.equals("item_removed")) assertNull(inventory.getItem(20));
    else assertTrue(lore().contains("Calculating"));
  }

  @Test
  void replacingPendingCalculationKeepsNewTokenAndResult() {
    display(true);
    schedule(true);
    long first =
        inventory
            .getItem(20)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.BRANCH_PREVIEW, PersistentDataType.LONG);
    Runnable old = fixture.ui.asyncTasks.remove(0);
    display(false);
    schedule(false);
    long second =
        inventory
            .getItem(20)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(Keys.BRANCH_PREVIEW, PersistentDataType.LONG);
    assertNotEquals(first, second);
    old.run();
    fixture.ui.runTasks();
    assertTrue(lore().contains("Calculating"));
    compute();
    fixture.ui.runTasks();
    assertFalse(lore().contains("Calculating"));
    assertTrue(lore().contains("-"));
  }

  @Test
  void failedEstimateLogsFailureAndPublishesUnavailableWithoutChangingTheEconomy() {
    display(true);
    double balance = guild.getBank().getWealth();
    schedule(true);
    var logger = mock(java.util.logging.Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);
    RuntimeException failure = new IllegalStateException("Preview calculation failed");
    // Inject a failed calculation at the service boundary; the ordinary paths use real snapshots.
    try (var calculations = mockStatic(BranchIncomePreview.class, CALLS_REAL_METHODS)) {
      calculations
          .when(
              () ->
                  BranchIncomePreview.estimate(
                      eq(prepared), eq(guild), anyMap(), anyMap(), anyDouble()))
          .thenThrow(failure);
      compute();
    }
    assertTrue(lore().contains("Calculating"));
    fixture.ui.runTasks();
    assertTrue(lore().contains("Income estimate unavailable"));
    verify(logger).log(eq(java.util.logging.Level.WARNING), contains("fields"), same(failure));
    assertEquals(1, branch.getLevel());
    assertEquals(balance, guild.getBank().getWealth());
    assertTrue(capital.getAllData().isEmpty());
    assertEquals(0, guild.getTradeBreakdown().getIncome());
    schedule(true);
    compute();
    fixture.ui.runTasks();
    assertFalse(lore().contains("unavailable"));
  }

  @Test
  void disabledPluginAndMissingInputsDoNotScheduleOrPublish() {
    display(true);
    BranchIncomePreviewService.schedule(null, inventory, 20, prepared, guild, branch, 1);
    BranchIncomePreviewService.schedule(player, inventory, 0, prepared, guild, branch, 1);
    when(fixture.ui.plugin.isEnabled()).thenReturn(false);
    schedule(true);
    assertTrue(fixture.ui.asyncTasks.isEmpty());
    when(fixture.ui.plugin.isEnabled()).thenReturn(true);
    schedule(true);
    when(fixture.ui.plugin.isEnabled()).thenReturn(false);
    compute();
    assertTrue(fixture.ui.tasks.isEmpty());
    assertTrue(lore().contains("Calculating"));
    SimpleFactions old = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      schedule(true);
    } finally {
      SimpleFactions.plugin = old;
    }
    assertTrue(fixture.ui.asyncTasks.isEmpty());
  }
}
