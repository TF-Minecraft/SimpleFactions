package net.tfminecraft.simplefactions.war.battle.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.military.BattleLivesService;
import net.tfminecraft.simplefactions.war.battle.military.BattleLivesService.SideLivesPreview;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import java.time.Instant;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import net.tfminecraft.simplefactions.loaders.BattleTemplateLoader;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.war.battle.engine.capture.CapturePoint;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.enums.DefenderRespawnMode;
import net.tfminecraft.simplefactions.war.battle.template.BattleLocation;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.template.CapturePointDefinition;
import net.tfminecraft.simplefactions.war.battle.template.ContestArea;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class BattleInventoryCoverageTest {
  private GuiTestFixture ui;
  private Player alice;
  private BattleInventoryManager menus;
  private List<Battle> previousBattles;
  private List<Warband> previousBands;
  private Map<Player, Battle> previousEditors;
  private Map<Player, String> previousSideEditors;
  private Map<String, BattleTemplate> previousTemplates;
  private final Map<String, Player> online = new LinkedHashMap<>();
  private MockedStatic<TLibs> tlibs;
  private ItemAPI items;

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    previousBattles = new ArrayList<>(BattleManager.get());
    previousBands = new ArrayList<>(WarbandManager.get());
    previousTemplates = BattleTemplateLoader.getAll();
    previousEditors = new LinkedHashMap<>(BattleManager.currentBattle);
    previousSideEditors = new LinkedHashMap<>(BattleManager.currentSideEdit);
    BattleManager.currentBattle.clear();
    BattleManager.currentSideEdit.clear();
    BattleManager.get().clear();
    WarbandManager.get().clear();
    BattleTemplateLoader.resetForTests();
    when(ui.world.getName()).thenReturn("world");
    when(Bukkit.getWorld("world")).thenReturn(ui.world);
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> online.values());
    when(Bukkit.getPlayerExact(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
    when(Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.values().stream()
        .filter(player -> player.getUniqueId().equals(call.getArgument(0))).findFirst().orElse(null));
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
        .thenAnswer(call -> mock(BossBar.class));
    tlibs = mockStatic(TLibs.class);
    items = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    tlibs.when(TLibs::getItemAPI).thenReturn(items);
    when(items.getCreator().getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    alice = player("Alice");
    when(alice.hasPermission("warbands.admin")).thenReturn(true);
    menus = new BattleInventoryManager();
  }

  @AfterEach
  void cleanup() {
    tlibs.close();
    BattleManager.get().clear();
    BattleManager.get().addAll(previousBattles);
    WarbandManager.get().clear();
    WarbandManager.get().addAll(previousBands);
    BattleTemplateLoader.resetForTests();
    previousTemplates.values().forEach(BattleTemplateLoader::putForTests);
    BattleManager.currentBattle.clear();
    BattleManager.currentBattle.putAll(previousEditors);
    BattleManager.currentSideEdit.clear();
    BattleManager.currentSideEdit.putAll(previousSideEditors);
    ui.close();
  }

  private Player player(String name) {
    Player player = ui.player(name);
    online.put(name, player);
    return player;
  }

  private Battle battle(String id, BattleType type) {
    Battle battle = BattleFactory.createBlank(type, id);
    BattleManager.addBattle(battle);
    return battle;
  }

  private Inventory top() {
    return alice.getOpenInventory().getTopInventory();
  }

  private String name(ItemStack item) {
    assertNotNull(item);
    return item.getItemMeta().getDisplayName();
  }

  private void lore(ItemStack item, String expected) {
    assertNotNull(item);
    assertTrue(item.getItemMeta().getLore().stream().anyMatch(line -> line.contains(expected)), () -> item.getItemMeta().getLore().toString());
  }

  private CapturePoint point(Battle battle, String id, int sequence, int progress) {
    CapturePoint point = new CapturePoint(id, new Location(ui.world, sequence + 0.6, 64.2, 7.8), battle.getSides().getFirst(), progress);
    point.setSequenceIndex(sequence);
    battle.addPoint(point);
    return point;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingLockIconStillRendersAnOperableBattleMenu(boolean locked) {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setLocked(locked);
    when(items.getCreator().getItemsAdderItem(anyString())).thenReturn(null);
    assertDoesNotThrow(() -> menus.battleView(alice, battle));
    ItemStack button = top().getItem(1);
    assertNotNull(button);
    assertFalse(button.isEmpty());
    assertEquals(locked ? "§cLocked" : "§aUnlocked", name(button));
    lore(button, locked ? "cannot join" : "can join");
  }

  @Test
  void anEmptyCustomLockIconUsesAVisibleFallback() {
    Battle battle = battle("front", BattleType.FIELD);
    ItemStack empty = new ItemStack(Material.AIR);
    when(items.getCreator().getItemsAdderItem(anyString())).thenReturn(empty);
    assertDoesNotThrow(() -> menus.battleView(alice, battle));
    assertFalse(top().getItem(1).isEmpty());
    assertEquals("§cLocked", name(top().getItem(1)));
  }

  @Test
  void battleListStaysWithinItsInventoryWhenManyCampaignBattlesExist() {
    for (int index = 0; index < 80; index++) {
      Battle battle = battle("battle-" + index, BattleType.FIELD);
      battle.setWarId(index + 1);
    }
    assertDoesNotThrow(() -> menus.battleList(alice));
    assertEquals(27, top().getSize());
    assertEquals("§ebattle-0", name(top().getItem(0)));
    assertEquals("§aNext page", name(top().getItem(25)));
    Set<String> names = collectPages();
    assertEquals(80, names.size());
    assertTrue(names.contains("§ebattle-79"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"side-selection", "side-view", "points", "spawns"})
  void largeBattleCollectionsCannotOverwriteNavigationOrExceedMenuCapacity(String screen) {
    Battle battle = battle("front", BattleType.FIELD);
    Warband band = new Warband("scouts", alice);
    battle.getSides().getFirst().addBand(band);
    for (int index = 0; index < 35; index++) {
      battle.addSide(new BattleSide("side-" + index, battle.getLifeType(), 10));
      point(battle, "point-" + index, index, 100);
    }
    battle.getPointManager().setPoints(battle.getPoints());
    assertDoesNotThrow(() -> {
      switch (screen) {
        case "side-selection" -> menus.sideSelection(alice, battle);
        case "side-view" -> menus.sideView(alice, battle);
        case "points" -> menus.pointView(alice, battle);
        case "spawns" -> menus.spawnList(alice, battle);
        default -> fail();
      }
    });
    assertEquals(27, top().getSize());
    assertNotNull(top().getItem(0));
    if (!screen.equals("spawns")) assertEquals("§cBACK", name(top().getItem(26)));
    Set<String> names = collectPages();
    assertEquals(screen.startsWith("side") ? 37 : 35, names.size());
    assertTrue(names.stream().anyMatch(label -> label.contains(screen.startsWith("side") ? "side-34" : "point-34")));
  }

  @Test
  void fieldBattleMenuReflectsMutableRulesAndClearsObsoleteControlsOnRefresh() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setDisplayName("Northern Front");
    battle.setTemplateName("plains");
    battle.setLocked(false);
    battle.setSequentialCapture(true);
    battle.setFriendlyFire(false);
    battle.setKeepInventory(true);
    battle.setLootEnabled(false);
    battle.setTeleport(true);
    menus.battleView(alice, battle);
    Inventory inventory = top();
    assertEquals("§7Battle View", alice.getOpenInventory().getTitle());
    assertEquals("§fSides: §e2", name(inventory.getItem(0)));
    assertEquals("§aUnlocked", name(inventory.getItem(1)));
    assertEquals("§eLife Mode: §aCollective", name(inventory.getItem(2)));
    assertEquals("§ePer-side lives", name(inventory.getItem(3)));
    assertEquals("§fFriendly Fire: §cFALSE", name(inventory.getItem(4)));
    assertEquals("§fKeep Inventory: §bTRUE", name(inventory.getItem(5)));
    assertEquals("§fTP on start: §bTRUE", name(inventory.getItem(6)));
    assertEquals("§fTemplate: §eplains", name(inventory.getItem(7)));
    assertEquals("§fSequential capture: §bTRUE", name(inventory.getItem(8)));
    assertEquals("§fBattle: §eNorthern Front", name(inventory.getItem(13)));
    lore(inventory.getItem(13), "Id: front");
    assertEquals("§fBattle Loot: §cFALSE", name(inventory.getItem(14)));
    assertEquals("§aSTART BATTLE", name(inventory.getItem(18)));
    assertEquals("§cDelete battle", name(inventory.getItem(22)));
    assertEquals("§fCapture Points: §e0", name(inventory.getItem(23)));
    battle.setStarted(true);
    battle.setLocked(true);
    battle.setCapturePointsEnabled(false);
    menus.updateView(alice, battle, inventory);
    assertSame(inventory, top());
    assertEquals("§cLocked", name(inventory.getItem(1)));
    assertEquals("§cEnd Battle", name(inventory.getItem(18)));
    assertNull(inventory.getItem(8));
    assertNull(inventory.getItem(22));
    assertNull(inventory.getItem(23));
  }
  @ParameterizedTest
  @ValueSource(strings = {"warbands", "templates"})
  void largeWarbandAndTemplateListsOfferNavigationInsteadOfHidingEntries(String screen) {
    for (int index = 0; index < 35; index++) {
      WarbandManager.addWarband(new Warband("band-" + index, alice));
      YamlConfiguration config = new YamlConfiguration();
      config.set("type", "field");
      BattleTemplateLoader.putForTests(new BattleTemplate("template-" + index, config));
    }
    if (screen.equals("warbands")) menus.warbandList(alice);
    else menus.templateView(alice, battle("front", BattleType.FIELD));
    assertEquals("§aNext page", name(top().getItem(25)));
    if (screen.equals("templates")) assertEquals("§cBACK", name(top().getItem(26)));
    assertEquals(screen.equals("templates") ? 36 : 35, collectPages().size());
  }

  private Set<String> collectPages() {
    Set<String> result = new LinkedHashSet<>();
    for (int page = 0; page < 10; page++) {
      for (int slot = 0; slot < 24; slot++) {
        ItemStack item = top().getItem(slot);
        if (item != null) assertTrue(result.add(name(item)), "duplicate page entry");
      }
      if (top().getItem(25) == null) return result;
      assertEquals("§aNext page", name(top().getItem(25)));
      assertTrue(BattleInventoryManager.handlePageClick(ui.click(alice, 25)));
    }
    throw new AssertionError("pagination did not terminate");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void navigationRunsOnceForEitherListenerOrderAndBackStillRoutesToItsParent(boolean warbandFirst) {
    for (int index = 0; index < 55; index++) battle("front-" + index, BattleType.FIELD);
    menus.battleList(alice);
    Inventory inventory = top();
    assertSame(inventory, inventory.getHolder().getInventory());
    BattleManager battleListener = new BattleManager();
    WarbandManager warbandListener = new WarbandManager();
    InventoryClickEvent next = ui.click(alice, 25);
    if (warbandFirst) { warbandListener.invenClick(next); battleListener.invenClick(next); }
    else { battleListener.invenClick(next); warbandListener.invenClick(next); }
    assertTrue(next.isCancelled());
    assertEquals("§efront-24", name(top().getItem(0)));
    InventoryClickEvent previous = ui.click(alice, 24);
    if (warbandFirst) { warbandListener.invenClick(previous); battleListener.invenClick(previous); }
    else { battleListener.invenClick(previous); warbandListener.invenClick(previous); }
    assertEquals("§efront-0", name(top().getItem(0)));
    Battle selected = BattleManager.get().getFirst();
    InventoryClickEvent entry = ui.click(alice, 0);
    if (warbandFirst) { warbandListener.invenClick(entry); battleListener.invenClick(entry); }
    else { battleListener.invenClick(entry); warbandListener.invenClick(entry); }
    assertEquals("§7Side Selection", alice.getOpenInventory().getTitle());
    assertSame(selected, BattleManager.currentBattle.get(alice));
    InventoryClickEvent back = ui.click(alice, 26);
    if (warbandFirst) { warbandListener.invenClick(back); battleListener.invenClick(back); }
    else { battleListener.invenClick(back); warbandListener.invenClick(back); }
    assertEquals("§7Battle List", alice.getOpenInventory().getTitle());
  }

  @Test
  void refreshingAnOpenListPreservesItsPageAndClampsAfterEntriesDisappear() {
    for (int index = 0; index < 55; index++) battle("front-" + index, BattleType.FIELD);
    menus.battleList(alice);
    assertTrue(BattleInventoryManager.handlePageClick(ui.click(alice, 25)));
    menus.populateBattleList(top());
    assertEquals("§efront-24", name(top().getItem(0)));
    BattleManager.get().subList(1, 55).clear();
    menus.populateBattleList(top());
    assertEquals("§efront-0", name(top().getItem(0)));
    assertNull(top().getItem(24));
    assertNull(top().getItem(25));
    BattleManager.get().clear();
    menus.populateBattleList(top());
    assertNull(top().getItem(0));
  }

  @Test
  void shrinkingToOnePageDuringNavigationCannotTurnTheSecondListenerIntoAnEntryClick() {
    for (int index = 0; index < 35; index++) WarbandManager.addWarband(new Warband("band-" + index, player("Leader" + index)));
    menus.warbandList(alice);
    WarbandManager.get().subList(26, 35).clear();
    InventoryClickEvent event = ui.click(alice, 25);
    new BattleManager().invenClick(event);
    new WarbandManager().invenClick(event);
    assertTrue(event.isCancelled());
    assertEquals("§eband-25", name(top().getItem(25)));
    verify(alice, never()).sendMessage(anyString());
    assertNull(WarbandManager.getByPlayer(alice));
  }

  @Test
  void pagesBlockInventoryTransfersAndForeignViewersWithoutTreatingOrdinaryEntriesAsNavigation() {
    for (int index = 0; index < 35; index++) battle("front-" + index, BattleType.FIELD);
    menus.battleList(alice);
    InventoryClickEvent bottom = ui.click(alice, 27);
    assertTrue(BattleInventoryManager.handlePageClick(bottom));
    assertTrue(bottom.isCancelled());
    InventoryClickEvent outside = ui.click(alice, -999);
    assertTrue(BattleInventoryManager.handlePageClick(outside));
    assertTrue(outside.isCancelled());
    InventoryClickEvent shift = new InventoryClickEvent(alice.getOpenInventory(), InventoryType.SlotType.CONTAINER,
        25, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
    assertTrue(BattleInventoryManager.handlePageClick(shift));
    assertTrue(shift.isCancelled());
    assertEquals("§efront-0", name(top().getItem(0)));
    Player bob = player("Bob");
    bob.openInventory(top());
    assertTrue(BattleInventoryManager.handlePageClick(ui.click(bob, 25)));
    assertEquals("§efront-0", name(top().getItem(0)));
    InventoryClickEvent normal = ui.click(alice, 0);
    assertFalse(BattleInventoryManager.handlePageClick(normal));
    assertFalse(normal.isCancelled());
    normal.setCancelled(true);
    assertFalse(BattleInventoryManager.handlePageClick(normal));
    menus.battleView(alice, BattleManager.get().getFirst());
    assertFalse(BattleInventoryManager.handlePageClick(ui.click(alice, 0)));
  }

  @Test
  void smallListsKeepAllOriginalSlotsAndLegacyRefreshClearsRemovedEntries() {
    for (int index = 0; index < 27; index++) battle("front-" + index, BattleType.FIELD);
    menus.battleList(alice);
    assertEquals("§efront-26", name(top().getItem(26)));
    assertFalse(BattleInventoryManager.handlePageClick(ui.click(alice, 25)));
    BattleManager.get().subList(2, 27).clear();
    Inventory legacy = ui.inventory(null, 9, "external list");
    legacy.setItem(8, new ItemStack(Material.DIRT));
    menus.populateBattleList(legacy);
    assertEquals("§efront-1", name(legacy.getItem(1)));
    assertNull(legacy.getItem(8));
    Warband band = new Warband("scouts", alice);
    WarbandManager.addWarband(band);
    menus.populateWarbandList(legacy);
    assertEquals("§escouts", name(legacy.getItem(0)));
    assertNull(legacy.getItem(1));
    menus.warbandList(alice);
    WarbandManager.get().clear();
    menus.populateWarbandList(top(), alice);
    assertNull(top().getItem(0));
  }

  @Test
  void customLockTemplatesAreClonedAndOnlyTheSelectedVariantIsRequested() {
    ItemStack template = new ItemStack(Material.PAPER);
    var original = template.getItemMeta();
    original.setDisplayName("Original");
    template.setItemMeta(original);
    when(items.getCreator().getItemsAdderItem("mcicons:icon_lock")).thenReturn(template);
    Battle battle = battle("front", BattleType.FIELD);
    ItemStack button = menus.createLockButton(battle);
    assertNotSame(template, button);
    assertEquals("§cLocked", name(button));
    assertEquals("Original", name(template));
    verify(items.getCreator(), never()).getItemsAdderItem("mcicons:icon_unlock");
  }

  @Test
  void sideMenusDisplayStableIdentitiesRosterLocationsAndEditableManualLives() {
    Battle battle = battle("front", BattleType.FIELD);
    BattleSide side = battle.getSides().getFirst();
    Warband band = new Warband("scouts", alice);
    band.addPlayer(player("Bob"));
    side.addBand(band);
    ItemStack basic = menus.createSideItem(side);
    assertEquals("§eattacker: §f2", name(basic));
    assertEquals("attacker", BattleInventoryManager.getSideIdFromItem(basic));
    lore(basic, "scouts: 2");
    menus.sideEditView(alice, battle, side);
    Inventory inventory = top();
    assertEquals(BattleInventoryManager.SIDE_EDIT_TITLE, alice.getOpenInventory().getTitle());
    lore(inventory.getItem(10), "Current: not set");
    lore(inventory.getItem(12), "Current: not set");
    assertEquals("§aAdd capture point", name(inventory.getItem(14)));
    lore(inventory.getItem(14), "Auto-names");
    assertEquals("§fSide lives: §e25", name(inventory.getItem(16)));
    assertEquals("§cBACK", name(inventory.getItem(26)));
    side.setSpawn(new Location(ui.world, 2.8, 64.1, 7.8));
    side.setJail(new Location(ui.world, -3.8, 60.1, 1.4));
    side.setLives(13);
    menus.updateSideEditView(alice, battle, side, inventory);
    lore(inventory.getItem(4), "Spawn: x3, y64, z8");
    lore(inventory.getItem(4), "Jail: x-4, y60, z1");
    lore(inventory.getItem(10), "Current: x3, y64, z8");
    lore(inventory.getItem(12), "Current: x-4, y60, z1");
    lore(inventory.getItem(16), "Current: §e13§7/§e13");
    battle.setStarted(true);
    battle.setCapturePointsEnabled(false);
    menus.updateSideEditView(alice, battle, side, inventory);
    assertNull(inventory.getItem(14));
    assertNull(inventory.getItem(16));
    battle.setStarted(false);
    battle.setWarId(12);
    menus.updateSideEditView(alice, battle, side, inventory);
    assertNull(inventory.getItem(16));
    assertEquals("§fBattle: §efront", name(menus.createBattleInfoItem(battle)));
    lore(menus.createBattleItem(battle), "Participants: 2");
  }

  @ParameterizedTest
  @CsvSource({"attacker,100,GREEN_CONCRETE,§a,Friendly Control", "attacker,49,YELLOW_CONCRETE,§e,Contested", "defender,100,RED_CONCRETE,§c,Enemy Control"})
  void spawnItemsCommunicateControlAndTheRespawnThreshold(String controller, int progress, Material material, String color, String status) {
    Battle battle = battle("front", BattleType.FIELD);
    CapturePoint point = point(battle, "A", 0, progress);
    point.setController(battle.getSideById(controller));
    ItemStack item = menus.createSpawnPointItem(point, battle.getSideById("attacker"));
    assertEquals(material, item.getType());
    assertEquals(color + "A", name(item));
    lore(item, status);
    lore(item, "x1, y64, z8");
  }

  @Test
  void pointViewSortsByChainAndUsesPersistentIdsIndependentOfDisplayText() {
    Battle battle = battle("front", BattleType.FIELD);
    CapturePoint second = point(battle, "B", 1, 72);
    point(battle, "A", 0, 100);
    menus.pointView(alice, battle);
    assertEquals("A", BattleInventoryManager.getPointIdFromItem(top().getItem(0)));
    assertEquals("B", BattleInventoryManager.getPointIdFromItem(top().getItem(1)));
    assertEquals("§cBACK", name(top().getItem(26)));
    ItemStack item = menus.createPointItem(second);
    assertEquals("§eB: §fattacker §7(72%)", name(item));
    lore(item, "Chain #2 (B)");
    lore(item, "x2, y64, z8");
    lore(item, "Click to delete");
    battle.setSequentialCapture(true);
    lore(menus.createPointItem(second, battle), "Order synced defender to attacker spawns");
    for (ItemStack missing : java.util.Arrays.asList(null, mock(ItemStack.class), new ItemStack(Material.STONE))) {
      assertNull(BattleInventoryManager.getPointIdFromItem(missing));
      assertNull(BattleInventoryManager.getSideIdFromItem(missing));
      assertNull(BattleInventoryManager.getTemplateIdFromItem(missing));
    }
  }

  @Test
  void templateSelectionIncludesOnlyMatchingTypesAndKeepsBaseResetIdentity() {
    YamlConfiguration field = new YamlConfiguration();
    field.set("type", "field");
    YamlConfiguration siege = new YamlConfiguration();
    siege.set("type", "siege");
    BattleTemplateLoader.putForTests(new BattleTemplate("plain", field));
    BattleTemplateLoader.putForTests(new BattleTemplate("castle", siege));
    Battle battle = battle("front", BattleType.FIELD);
    menus.templateView(alice, battle);
    assertEquals(BattleInventoryManager.TEMPLATE_NONE_ID, BattleInventoryManager.getTemplateIdFromItem(top().getItem(0)));
    lore(top().getItem(0), "Reset battle layout");
    assertEquals("plain", BattleInventoryManager.getTemplateIdFromItem(top().getItem(1)));
    lore(top().getItem(1), "Wipes current battle layout");
    assertNull(top().getItem(2));
    battle.setBattleType(null);
    menus.templateView(alice, battle);
    assertEquals(BattleInventoryManager.TEMPLATE_NONE_ID, BattleInventoryManager.getTemplateIdFromItem(top().getItem(0)));
    assertNull(top().getItem(1));
    assertEquals("§fTemplate: §eNone", name(menus.createTemplateButton(battle)));
  }

  @Test
  void siegeMenusExposeUnsetAndConfiguredCornersAndLockDurationAfterStart() {
    Battle battle = battle("siege", BattleType.SIEGE);
    battle.setContestDurationSeconds(123);
    menus.battleView(alice, battle);
    assertEquals("§eContest duration: §f123s", name(top().getItem(3)));
    assertNull(top().getItem(8));
    lore(top().getItem(23), "Not configured");
    menus.contestView(alice, battle);
    lore(top().getItem(0), "Not set");
    lore(top().getItem(1), "Not set");
    lore(top().getItem(2), "Click to cycle");
    assertNull(top().getItem(3));
    assertEquals("§cBACK", name(top().getItem(26)));
    ContestArea area = new ContestArea();
    area.setMin(new BattleLocation("world", 1.6, 60, -1.6, 0, 0));
    area.setMax(new BattleLocation("world", 10, 70, 20, 0, 0));
    battle.setContestArea(area);
    lore(menus.createContestButton(battle), "Configured");
    battle.setStarted(true);
    battle.setContestHoldRemainingSeconds(47);
    menus.contestView(alice, battle);
    lore(top().getItem(0), "x2, y60, z-2");
    lore(top().getItem(1), "x10, y70, z20");
    lore(top().getItem(2), "Duration locked after start");
    assertEquals("§eHold remaining: §f47s", name(top().getItem(3)));
    battle.setContestArea(new ContestArea());
    lore(menus.createContestButton(battle), "Not configured");
  }

  @Test
  void raidMenusToggleLimitedRespawnsAndShowTheLiveTargetOnlyAfterStart() {
    Battle battle = battle("raid", BattleType.RAID);
    battle.setDefenderRespawnMode(DefenderRespawnMode.INFINITE);
    menus.battleView(alice, battle);
    assertEquals("§fDefender respawn: §einfinite", name(top().getItem(3)));
    assertNull(top().getItem(8));
    assertEquals("§fRaid Target: §eunset", name(top().getItem(23)));
    menus.raidTargetView(alice, battle);
    lore(top().getItem(0), "Not set");
    assertNull(top().getItem(1));
    battle.setDefenderRespawnMode(DefenderRespawnMode.LIVES);
    battle.setDefenderLives(19);
    battle.setRaidTarget(new CapturePointDefinition("gate", new BattleLocation("world", 4.2, 50.5, -2.2, 0, 0)));
    menus.battleView(alice, battle);
    assertEquals("§fDefender respawn: §elives", name(top().getItem(3)));
    assertEquals("§fDefender lives: §e19", name(top().getItem(8)));
    assertEquals("§fRaid Target: §egate", name(top().getItem(23)));
    battle.setStarted(true);
    menus.raidTargetView(alice, battle);
    assertNull(top().getItem(1));
    CapturePoint point = point(battle, "gate", 0, 80);
    battle.getPointManager().setPoints(List.of(point));
    menus.raidTargetView(alice, battle);
    lore(top().getItem(0), "x4, y51, z-2");
    assertEquals("gate", BattleInventoryManager.getPointIdFromItem(top().getItem(1)));
    assertEquals("§cBACK", name(top().getItem(26)));
  }

  @Test
  void campaignSummaryShowsCommitmentsAndProvidesSafeResetInstructions() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setWarId(12);
    War war = mock(War.class);
    SideLivesPreview attacker = new SideLivesPreview(3, 30, 7, 23, 0);
    SideLivesPreview defender = new SideLivesPreview(0, 0, 2, 0, 0);
    try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
         MockedStatic<BattleLivesService> lives = mockStatic(BattleLivesService.class)) {
      menus.battleView(alice, battle);
      lore(top().getItem(3), "War not found");
      assertEquals("§cCampaign battle", name(top().getItem(22)));
      lore(top().getItem(22), "/war admin schedule 12 battledelete");
      wars.when(() -> WarManager.getById(12)).thenReturn(war);
      lives.when(() -> BattleLivesService.previewCampaignSideLives(war, battle, "attacker")).thenReturn(attacker);
      lives.when(() -> BattleLivesService.previewCampaignSideLives(war, battle, "defender")).thenReturn(defender);
      menus.updateView(alice, battle, top());
      lore(top().getItem(3), "Attacker: §e23 §8(30 - 7)");
      lore(top().getItem(3), "Defender: §e0");
      menus.sideView(alice, battle);
      lore(top().getItem(0), "Lives: §e23");
      lore(top().getItem(0), "Regiments §e3");
      lore(top().getItem(1), "no committed regiments");
      battle.setCampaignRaid(true);
      menus.battleView(alice, battle);
      assertEquals("§cCampaign raid", name(top().getItem(22)));
      lore(top().getItem(22), "timer expires");
      lore(top().getItem(22), "admin teardown");
    }
    battle.setWarId(null);
    assertEquals(1, menus.createCampaignBattleResetHintItem(battle).getItemMeta().getLore().size());
  }

  @Test
  void manualWarbandItemsShowOnlineRosterOverflowAndCurrentJoiningPolicy() {
    Warband band = new Warband("scouts", alice);
    for (int index = 0; index < 6; index++) band.addPlayer(player("Member" + index));
    ItemStack item = menus.createWarbandItem(band);
    assertEquals("§escouts", name(item));
    assertEquals(1, item.getItemMeta().getCustomModelData());
    lore(item, "Leader: Alice");
    lore(item, "Soldiers: §e7");
    lore(item, "Online: §e7");
    lore(item, "and 2 more");
    lore(item, "LOCKED");
    assertEquals("scouts", item.getItemMeta().getPersistentDataContainer().get(
        new org.bukkit.NamespacedKey(net.tfminecraft.simplefactions.SimpleFactions.plugin, "id"),
        org.bukkit.persistence.PersistentDataType.STRING));
    band.setLocked(false);
    lore(menus.createWarbandItem(band), "OPEN");
  }

  @Test
  void campaignWarbandItemsShowPendingLeaderSignupPreviewAndRunningLives() {
    Side side = mock(Side.class);
    Faction faction = mock(Faction.class);
    when(side.getLeader()).thenReturn(faction);
    when(faction.getName()).thenReturn("North");
    Warband band = Warband.createRaidShell("north", side, "attacker");
    Battle battle = battle("front", BattleType.FIELD);
    War war = mock(War.class);
    try (MockedStatic<CampaignBattleJoinService> campaigns = mockStatic(CampaignBattleJoinService.class);
         MockedStatic<CampaignWarbandSignupService> signup = mockStatic(CampaignWarbandSignupService.class);
         MockedStatic<BattleLivesService> lives = mockStatic(BattleLivesService.class)) {
      ItemStack pending = menus.createWarbandItem(band);
      assertEquals("§eThe North Host", name(pending));
      assertEquals(2, pending.getItemMeta().getCustomModelData());
      lore(pending, "Soldiers: §e0");
      assertTrue(pending.getItemMeta().getLore().stream().noneMatch(line -> line.contains("Online:")));
      campaigns.when(() -> CampaignBattleJoinService.findCampaignBattleForWarband(band))
          .thenReturn(new CampaignBattleContext(battle, "attacker", war));
      campaigns.when(() -> CampaignBattleJoinService.countSideRoster(battle, "attacker")).thenReturn(7);
      campaigns.when(() -> CampaignBattleJoinService.previewSidePoolLives(war, battle, "attacker")).thenReturn(30);
      lives.when(() -> BattleLivesService.previewCampaignSideLives(war, battle, "attacker"))
          .thenReturn(new SideLivesPreview(3, 30, 7, 23, 0));
      ItemStack preview = menus.createWarbandItem(band);
      lore(preview, "Side lives: §e23");
      lore(preview, "Pool §e30");
      lore(preview, "Soldiers: §e7§7/§e30");
      lore(preview, "Signup closed until 20:00");
      signup.when(() -> CampaignWarbandSignupService.isSignupOpen(eq(war), any(Instant.class))).thenReturn(true);
      lives.when(() -> BattleLivesService.previewCampaignSideLives(war, battle, "attacker"))
          .thenReturn(new SideLivesPreview(0, 0, 0, 0, 0));
      assertTrue(menus.createWarbandItem(band).getItemMeta().getLore().stream().noneMatch(line -> line.contains("Signup closed")));
      battle.setStarted(true);
      battle.getSideById("attacker").setLives(11);
      lore(menus.createWarbandItem(band), "Lives: §e11");
      battle.clearSides();
      assertTrue(menus.createWarbandItem(band).getItemMeta().getLore().stream().noneMatch(line -> line.contains("Lives:")));
    }
  }

  @Test
  void hiddenRaidWarbandsAreFilteredForTheViewerAndRefreshUsesThatSameViewer() {
    Warband visible = new Warband("visible", alice);
    Warband hidden = new Warband("hidden", player("Bob"));
    WarbandManager.addWarband(hidden);
    WarbandManager.addWarband(visible);
    try (MockedStatic<CampaignRaidWarbandService> visibility = mockStatic(CampaignRaidWarbandService.class)) {
      visibility.when(() -> CampaignRaidWarbandService.isRaidWarbandHiddenFromPlayer(hidden, alice)).thenReturn(true);
      menus.warbandList(alice);
      assertEquals("§evisible", name(top().getItem(0)));
      assertNull(top().getItem(1));
      menus.populateWarbandList(top());
      assertEquals("§evisible", name(top().getItem(0)));
      assertNull(top().getItem(1));
    }
  }

  @Test
  void explicitHelperButtonsPreserveLegacyIndicatorsAndNullBattleHints() {
    Battle battle = battle("front", BattleType.FIELD);
    battle.setLives(17);
    ItemStack life = menus.createLifeCount(battle);
    assertEquals("§eLives: §c17", name(life));
    lore(life, "Each side has 17 respawns");
    battle.setStarted(true);
    ItemStack started = menus.createStartButton(battle);
    assertTrue(started.getItemMeta().hasEnchant(Enchantment.UNBREAKING));
    assertTrue(started.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    assertEquals("§ePer-side lives", name(menus.createManualSideLivesHintItem(null)));
    battle.setBattleType(BattleType.RAID);
    assertEquals(1, menus.createManualSideLivesHintItem(battle).getItemMeta().getLore().size());
  }

  @ParameterizedTest
  @CsvSource({"60,120", "120,180", "180,240", "240,300", "300,60", "77,180"})
  void durationCycleHasAStableWrapAndFallback(int current, int expected) {
    assertEquals(expected, BattleInventoryManager.cycleContestDuration(current));
  }

  @ParameterizedTest
  @CsvSource({"5,10", "10,15", "15,20", "20,25", "25,50", "50,5", "77,25"})
  void defenderLivesCycleHasAStableWrapAndFallback(int current, int expected) {
    assertEquals(expected, BattleInventoryManager.cycleDefenderLives(current));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ordinaryWarbandEntriesStillRunExactlyOnceInEitherListenerOrder(boolean warbandFirst) {
    WarbandManager.addWarband(new Warband("mine", alice));
    for (int index = 0; index < 35; index++) WarbandManager.addWarband(new Warband("other-" + index, player("Leader" + index)));
    menus.warbandList(alice);
    InventoryClickEvent event = ui.click(alice, 0);
    BattleManager battleListener = new BattleManager();
    WarbandManager warbandListener = new WarbandManager();
    if (warbandFirst) { warbandListener.invenClick(event); battleListener.invenClick(event); }
    else { battleListener.invenClick(event); warbandListener.invenClick(event); }
    assertTrue(event.isCancelled());
    verify(alice).sendMessage("§cAlready in this warband, use §e/warband leave §cto leave it");
    assertEquals("§7Warband List", alice.getOpenInventory().getTitle());
  }

  @Test
  void paginatedSideEditingStillChecksTheCurrentBattleStartedStateAndRoutesBack() {
    Battle battle = battle("front", BattleType.FIELD);
    for (int index = 0; index < 35; index++) battle.addSide(new BattleSide("extra-" + index, battle.getLifeType(), 5));
    BattleManager.currentBattle.put(alice, battle);
    menus.sideView(alice, battle);
    BattleManager listener = new BattleManager();
    listener.invenClick(ui.click(alice, 25));
    assertEquals("extra-22", BattleInventoryManager.getSideIdFromItem(top().getItem(0)));
    battle.setStarted(true);
    listener.invenClick(ui.click(alice, 0));
    verify(alice).sendMessage("§cCannot edit a battle while it has started");
    assertFalse(BattleManager.currentSideEdit.containsKey(alice));
    listener.invenClick(ui.click(alice, 26));
    assertEquals("§7Battle View", alice.getOpenInventory().getTitle());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void removedMembersCannotUseARespawnMenuOpenedOrPagedAfterTheyLeave(boolean alreadyOpen) {
    Battle battle = battle("front", BattleType.FIELD);
    Warband band = new Warband("scouts", player("Bob"));
    band.addPlayer(alice);
    battle.getSides().getFirst().addBand(band);
    WarbandManager.addWarband(band);
    for (int index = 0; index < 35; index++) point(battle, "point-" + index, index, 100);
    battle.getPointManager().setPoints(battle.getPoints());
    if (alreadyOpen) menus.spawnList(alice, battle);
    band.removePlayer(alice);
    assertNull(battle.getSideByPlayer(alice));
    if (alreadyOpen) assertDoesNotThrow(() -> new BattleManager().invenClick(ui.click(alice, 25)));
    else assertDoesNotThrow(() -> menus.spawnList(alice, battle));
    assertTrue(java.util.Arrays.stream(top().getContents()).filter(java.util.Objects::nonNull)
        .noneMatch(item -> item.getType() == Material.GREEN_CONCRETE));
    verify(alice, never()).teleport(any(Location.class));
  }

}
