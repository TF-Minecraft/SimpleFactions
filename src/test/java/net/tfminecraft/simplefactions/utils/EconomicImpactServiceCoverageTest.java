package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class EconomicImpactServiceCoverageTest {
  enum Place {
    TOP,
    BOTTOM,
    CURSOR
  }

  GuiTestFixture gui;
  Player player;
  Guild viewer;
  Inventory top;
  AtomicReference<ItemStack> cursor;
  MockedStatic<EconomicPreview> previews;
  EconomicPreview.Prepared prepared;
  AtomicInteger calculations;

  @BeforeEach
  void setUp() {
    gui = new GuiTestFixture();
    when(gui.plugin.isEnabled()).thenReturn(true);
    player = gui.player("Reader");
    top = gui.inventory(null, 9, "Proposal");
    player.openInventory(top);
    cursor = new AtomicReference<>();
    when(player.getItemOnCursor()).thenAnswer(call -> cursor.get());
    doAnswer(
            call -> {
              cursor.set(call.getArgument(0));
              return null;
            })
        .when(player)
        .setItemOnCursor(nullable(ItemStack.class));
    viewer = mock(Guild.class);
    when(viewer.getName()).thenReturn("Our Guild");
    previews = mockStatic(EconomicPreview.class);
    prepared = mock(EconomicPreview.Prepared.class);
    previews.when(EconomicPreview::current).thenReturn(prepared);
    calculations = new AtomicInteger();
  }

  @AfterEach
  void close() {
    when(player.isOnline()).thenReturn(false);
    gui.runTasks();
    previews.close();
    gui.close();
  }

  @ParameterizedTest
  @EnumSource(Place.class)
  void updatesTheStampedItemInEachSupportedLocation(Place place) {
    ItemStack item =
        enqueue(
            Material.PAPER,
            false,
            List.of("Before", EconomicImpact.calculatingLine(), "After"),
            7.25);
    put(place, item);
    gui.runTasks();
    assertEquals(0, calculations.get());
    assertEquals(1, gui.asyncTasks.size());
    runAsync();
    assertEquals(1, calculations.get());
    assertTrue(text(item).contains("Calculating"), "The worker cannot write menu state");
    gui.runTasks();
    assertTrue(text(item).startsWith("Before\n"));
    assertTrue(text(item).endsWith("After"));
    assertTrue(text(item).contains("Our Guild: +7.25d/day"));
    assertFalse(text(item).contains("Calculating"));
    if (place == Place.CURSOR) verify(player).setItemOnCursor(item);
  }

  @Test
  void followsTheItemWhenItMovesWhileTheWorkerRuns() {
    ItemStack item = enqueue(Material.PAPER, false, List.of(EconomicImpact.calculatingLine()), 9);
    top.setItem(1, item);
    gui.runTasks();
    runAsync();
    top.setItem(1, new ItemStack(Material.STONE));
    player.getInventory().setItem(7, item);
    gui.runTasks();
    assertEquals(Material.STONE, top.getItem(1).getType());
    assertTrue(text(player.getInventory().getItem(7)).contains("+9.00d/day"));
  }

  @Test
  void anOlderResultCannotOverwriteANewerPreviewOnTheSameItem() {
    ItemStack item = enqueue(Material.PAPER, false, List.of(EconomicImpact.calculatingLine()), 9);
    top.setItem(1, item);
    gui.runTasks();
    Runnable olderWorker = gui.asyncTasks.removeFirst();
    ItemMeta newer = item.getItemMeta();
    EconomicImpactService.enqueue(
        player, newer, viewer, false, false, state -> Map.of(viewer, 22.0));
    item.setItemMeta(newer);
    gui.runTasks();
    runAsync();
    gui.runTasks();
    assertTrue(text(item).contains("+22.00d/day"));
    olderWorker.run();
    gui.runTasks();
    assertTrue(text(item).contains("+22.00d/day"));
    assertFalse(text(item).contains("+9.00d/day"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aRemovedItemDoesNotReceiveAResult(boolean afterWorkerStarts) {
    ItemStack item = enqueue(Material.PAPER, false, List.of(EconomicImpact.calculatingLine()), 4);
    top.setItem(1, item);
    if (afterWorkerStarts) {
      gui.runTasks();
      runAsync();
    }
    top.setItem(1, new ItemStack(Material.STONE));
    gui.runTasks();
    assertEquals(afterWorkerStarts ? 1 : 0, calculations.get());
    assertEquals(Material.STONE, top.getItem(1).getType());
    assertTrue(text(item).contains("Calculating"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void disconnectedReadersAreNotCalculatedOrUpdated(boolean afterWorkerStarts) {
    ItemStack item = enqueue(Material.PAPER, false, List.of(EconomicImpact.calculatingLine()), 4);
    top.setItem(1, item);
    if (afterWorkerStarts) {
      gui.runTasks();
      runAsync();
    }
    when(player.isOnline()).thenReturn(false);
    gui.runTasks();
    assertEquals(afterWorkerStarts ? 1 : 0, calculations.get());
    assertTrue(text(item).contains("Calculating"));
    verify(player, never()).openBook(any(ItemStack.class));
  }

  @Test
  void disabledPluginDoesNotQueueAPublishAfterCalculation() {
    ItemStack item = enqueue(Material.PAPER, false, List.of(EconomicImpact.calculatingLine()), 4);
    top.setItem(1, item);
    gui.runTasks();
    when(gui.plugin.isEnabled()).thenReturn(false);
    runAsync();
    assertEquals(1, calculations.get());
    assertTrue(gui.tasks.isEmpty());
    assertTrue(text(item).contains("Calculating"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unavailableCalculationsReplaceThePlaceholderWithoutDiscardingDescriptions(
      boolean throwsFailure) {
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    meta.setLore(List.of("Before", EconomicImpact.calculatingLine(), "After"));
    EconomicImpactService.enqueue(
        player,
        meta,
        viewer,
        false,
        false,
        state -> {
          if (throwsFailure) throw new IllegalStateException("Preview unavailable");
          return null;
        });
    item.setItemMeta(meta);
    top.setItem(1, item);
    finish();
    assertEquals("Before\nIncome estimate unavailable\nAfter", text(item));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void existingItemsWithoutAPlaceholderAppendTheEstimate(boolean hasLore) {
    ItemStack item = enqueue(Material.PAPER, false, hasLore ? List.of("Description") : null, -3);
    top.setItem(1, item);
    finish();
    assertTrue(text(item).contains("Our Guild: -3.00d/day"));
    if (hasLore) assertTrue(text(item).startsWith("Description\n"));
  }

  static Stream<Arguments> bookViews() {
    return Stream.of(InventoryType.CRAFTING, InventoryType.CREATIVE, InventoryType.CHEST)
        .flatMap(type -> Stream.of(0, 1, 2, 3).map(pages -> Arguments.of(type, pages)));
  }

  @ParameterizedTest
  @MethodSource("bookViews")
  void aBoundCouncilBookUpdatesOnlyItsEstimateAndRespectsOpenMenus(
      InventoryType openType, int pageCount) {
    ItemStack book = enqueue(Material.WRITTEN_BOOK, true, null, 13);
    BookMeta meta = (BookMeta) book.getItemMeta();
    List<String> pages =
        new ArrayList<>(List.of("Proposal", "Calculating", "Other details").subList(0, pageCount));
    meta.setPages(pages);
    meta.setTitle("Council proposal");
    meta.setAuthor("Council");
    book.setItemMeta(meta);
    EconomicImpactService.bindBook(meta, book);
    when(player.getOpenInventory().getType()).thenReturn(openType);
    finish();
    BookMeta updated = (BookMeta) book.getItemMeta();
    assertEquals(pageCount < 2 ? pageCount + 1 : pageCount, updated.getPageCount());
    assertTrue(
        ChatColor.stripColor(updated.getPage(pageCount == 0 ? 1 : 2)).contains("+13.00d/day"));
    if (pageCount > 0) assertEquals("Proposal", updated.getPage(1));
    if (pageCount == 3) assertEquals("Other details", updated.getPage(3));
    assertEquals("Council proposal", updated.getTitle());
    assertEquals("Council", updated.getAuthor());
    if (openType == InventoryType.CHEST) verify(player, never()).openBook(any(ItemStack.class));
    else verify(player).openBook(book);
  }

  @Test
  void anUnboundBookCanBeFoundInInventoryAndThenPublished() {
    ItemStack book = enqueue(Material.WRITTEN_BOOK, true, null, 5);
    top.setItem(2, book);
    finish();
    assertTrue(
        ChatColor.stripColor(((BookMeta) book.getItemMeta()).getPage(1)).contains("+5.00d/day"));
    EconomicImpactService.bindBook(book.getItemMeta(), book); // Already consumed pending request.
    assertTrue(gui.tasks.isEmpty());
  }

  @Test
  void anUnboundBookMissingFromInventoryDoesNotCalculate() {
    enqueue(Material.WRITTEN_BOOK, true, null, 5);
    gui.runTasks();
    assertTrue(gui.asyncTasks.isEmpty());
    assertEquals(0, calculations.get());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aBoundBookWhoseMetadataWasReplacedIsNotReopened(boolean noMetadata) {
    ItemStack book = enqueue(Material.WRITTEN_BOOK, true, null, 5);
    EconomicImpactService.bindBook(book.getItemMeta(), book);
    gui.runTasks();
    runAsync();
    if (noMetadata) when(book.getItemMeta()).thenReturn(null);
    else {
      ItemMeta meta = book.getItemMeta();
      meta.getPersistentDataContainer().remove(Keys.ECONOMY_PREVIEW);
      book.setItemMeta(meta);
    }
    gui.runTasks();
    verify(player, never()).openBook(any(ItemStack.class));
  }

  @Test
  void nonBookMetadataStillReceivesLoreWhenTheCallerRequestsABook() {
    ItemStack item = enqueue(Material.PAPER, true, List.of("Description"), 6);
    EconomicImpactService.bindBook(item.getItemMeta(), item);
    finish();
    assertTrue(text(item).contains("+6.00d/day"));
  }

  @Test
  void invalidEnqueueAndBookBindingInputsDoNotScheduleWork() {
    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    ItemMeta meta = book.getItemMeta();
    EconomicImpactService.Calculator calculator = state -> Map.of();
    EconomicImpactService.enqueue(null, meta, viewer, false, false, calculator);
    EconomicImpactService.enqueue(player, null, viewer, false, false, calculator);
    EconomicImpactService.enqueue(player, meta, viewer, false, false, null);
    when(gui.plugin.isEnabled()).thenReturn(false);
    EconomicImpactService.enqueue(player, meta, viewer, false, false, calculator);
    SimpleFactions.plugin = null;
    EconomicImpactService.enqueue(player, meta, viewer, false, false, calculator);
    SimpleFactions.plugin = gui.plugin;
    EconomicImpactService.bindBook(null, book);
    EconomicImpactService.bindBook(meta, null);
    EconomicImpactService.bindBook(meta, book);
    assertTrue(gui.tasks.isEmpty());
    assertFalse(meta.getPersistentDataContainer().has(Keys.ECONOMY_PREVIEW));
  }

  private ItemStack enqueue(Material type, boolean book, List<String> lore, double delta) {
    ItemStack item = new ItemStack(type);
    ItemMeta meta = item.getItemMeta();
    if (lore != null) meta.setLore(lore);
    EconomicImpactService.enqueue(
        player,
        meta,
        viewer,
        false,
        book,
        state -> {
          assertSame(
              prepared, state, "The worker receives the snapshot captured when the menu was built");
          calculations.incrementAndGet();
          return Map.of(viewer, delta);
        });
    item.setItemMeta(meta);
    return item;
  }

  private void put(Place place, ItemStack item) {
    switch (place) {
      case TOP -> top.setItem(1, item);
      case BOTTOM -> player.getInventory().setItem(2, item);
      case CURSOR -> cursor.set(item);
    }
  }

  private void runAsync() {
    gui.asyncTasks.removeFirst().run();
  }

  private void finish() {
    gui.runTasks();
    runAsync();
    gui.runTasks();
  }

  private String text(ItemStack item) {
    return ChatColor.stripColor(String.join("\n", item.getItemMeta().getLore()));
  }
}
