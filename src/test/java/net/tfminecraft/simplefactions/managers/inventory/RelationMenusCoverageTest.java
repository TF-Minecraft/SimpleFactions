package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.diplomacy.Threshold;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.utils.EconomicImpact;
import net.tfminecraft.simplefactions.war.declare.DeclareCodePrompt;
import net.tfminecraft.simplefactions.war.declare.WarDeclareCodeService;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsService;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Menu outputs and actions are exercised together; capacity/war services are scoped boundaries. */
class RelationMenusCoverageTest {
  private GuiTestFixture ui;
  private Player player;
  private Faction origin;
  private Faction target;
  private Relation outgoing;
  private Relation incoming;
  private RelationType neutral;
  private Attitude indifferent;
  private InventoryManager manager;
  private RelationCreator creator;
  private RelationView view;
  private List<Faction> previousFactions;
  private List<RelationType> previousTypes;
  private List<Attitude> previousAttitudes;
  private HashMap<String, String> previousIcons;
  private boolean previousCodeRequired;
  private int previousWarOpinion;
  private final List<MockedStatic<?>> mocks = new ArrayList<>();
  private MockedStatic<FactionManager> lookup;
  private MockedStatic<RelationManager> diplomacy;
  private MockedStatic<WarManager> wars;
  private MockedStatic<WarDeclareCodeService> codes;
  private MockedStatic<DeclareCodePrompt> prompts;
  private MockedStatic<WarReparationsService> reparations;
  private MockedStatic<EconomicImpact> economics;

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> result = mockStatic(type);
    mocks.add(result);
    return result;
  }

  @BeforeEach
  void setup() {
    ui = new GuiTestFixture();
    previousFactions = FactionManager.factions;
    previousTypes = RelationLoader.types;
    previousAttitudes = RelationLoader.attitudes;
    previousIcons = Cache.icons;
    previousCodeRequired = Cache.warRequireDeclareCode;
    previousWarOpinion = Cache.warDeclareOpinionThreshold;
    FactionManager.factions = new ArrayList<>();
    RelationLoader.types = new ArrayList<>();
    RelationLoader.attitudes = new ArrayList<>();
    Cache.icons = new HashMap<>();
    Cache.icons.put("war", "IRON_SWORD.1");
    Cache.warDeclareOpinionThreshold = 0;
    Cache.warRequireDeclareCode = false;
    player = ui.player("Alice");
    neutral = type("neutral", "default", true);
    indifferent = attitude("indifferent", "target", 0);
    origin = faction("origin", "Origin", "Alice");
    target = faction("target", "Target", "Bob");
    outgoing = new Relation(neutral, indifferent, -20);
    incoming = new Relation(neutral, indifferent, 5);
    when(origin.getRelation("target")).thenReturn(outgoing);
    when(target.getRelation("origin")).thenReturn(incoming);
    lookup = scoped(FactionManager.class);
    lookup.when(() -> FactionManager.getByString(anyString())).thenAnswer(call ->
        FactionManager.factions.stream().filter(f -> f.getId().equals(call.getArgument(0))).findFirst().orElse(null));
    lookup.when(() -> FactionManager.getByMember(anyString())).thenAnswer(call ->
        FactionManager.factions.stream().filter(f -> f.getMembers().contains(call.getArgument(0))).findFirst().orElse(null));
    lookup.when(() -> FactionManager.getByLeader(anyString())).thenAnswer(call ->
        FactionManager.factions.stream().filter(f -> f.getLeader().equals(call.getArgument(0))).findFirst().orElse(null));
    diplomacy = scoped(RelationManager.class);
    wars = scoped(WarManager.class);
    codes = scoped(WarDeclareCodeService.class);
    prompts = scoped(DeclareCodePrompt.class);
    reparations = scoped(WarReparationsService.class);
    economics = scoped(EconomicImpact.class);
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator items = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(items);
    when(items.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(api);
    manager = mock(InventoryManager.class);
    when(manager.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.BARRIER));
    creator = new RelationCreator();
    view = new RelationView(manager);
  }

  @AfterEach
  void cleanup() {
    for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
    FactionManager.factions = previousFactions;
    RelationLoader.types = previousTypes;
    RelationLoader.attitudes = previousAttitudes;
    Cache.icons = previousIcons;
    Cache.warRequireDeclareCode = previousCodeRequired;
    Cache.warDeclareOpinionThreshold = previousWarOpinion;
    ui.close();
  }

  private static YamlConfiguration config(Object... values) {
    YamlConfiguration config = new YamlConfiguration();
    for (int i = 0; i < values.length; i += 2) config.set((String) values[i], values[i + 1]);
    return config;
  }

  private RelationType type(String id, Object... values) {
    YamlConfiguration config = config(values);
    config.set("name", id);
    RelationType result = new RelationType(id, config);
    RelationLoader.types.add(result);
    return result;
  }

  private Attitude attitude(String id, Object... values) {
    YamlConfiguration config = config(values);
    config.set("name", id);
    Attitude result = new Attitude(id, config);
    RelationLoader.attitudes.add(result);
    return result;
  }

  private Faction faction(String id, String name, String leader) {
    Faction faction = mock(Faction.class);
    when(faction.getId()).thenReturn(id);
    when(faction.getName()).thenReturn(name);
    when(faction.getLeader()).thenReturn(leader);
    when(faction.getMembers()).thenReturn(new ArrayList<>(List.of(leader)));
    when(faction.numOnline()).thenReturn(1);
    when(faction.getBanner()).thenAnswer(call -> new ItemStack(Material.WHITE_BANNER));
    DiplomacyHandler handler = mock(DiplomacyHandler.class);
    when(faction.getDiplomacyHandler()).thenReturn(handler);
    when(handler.getAvailableCapacity()).thenReturn(10.0);
    when(faction.getRelation(anyString())).thenAnswer(call -> new Relation(neutral, indifferent));
    Tier tier = mock(Tier.class);
    when(tier.getTier()).thenReturn(2);
    when(faction.getTier()).thenReturn(tier);
    when(faction.canHaveVassals()).thenReturn(true);
    FactionManager.factions.add(faction);
    return faction;
  }

  private static String text(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    return ChatColor.stripColor(meta.getDisplayName() + "\n" + String.join("\n", meta.getLore() == null ? List.of() : meta.getLore()));
  }

  private Inventory top() { return player.getOpenInventory().getTopInventory(); }
  private SFGUI menu() { return ((SFInventoryHolder) top().getHolder()).getType(); }
  private void click(int slot) {
    Inventory inventory = top();
    InventoryClickEvent event = ui.click(player, slot);
    view.click(event, inventory, player);
    assertTrue(event.isCancelled());
  }

  @Test
  void renderingDiplomacyDoesNotRewriteTheStoredFactionBanner() {
    ItemStack banner = new ItemStack(Material.BLUE_BANNER);
    ItemMeta meta = banner.getItemMeta();
    meta.setDisplayName("Original standard");
    meta.setLore(List.of("Guild heraldry"));
    banner.setItemMeta(meta);
    when(target.getBanner()).thenReturn(banner);
    view.diplomacyView(null, player, target, true);
    assertNotSame(banner, top().getItem(21));
    assertEquals("Original standard", banner.getItemMeta().getDisplayName());
    assertEquals(List.of("Guild heraldry"), banner.getItemMeta().getLore());
    assertTrue(text(top().getItem(21)).contains("Target"));
  }

  @Test
  void aRemovedAttitudeOptionIsIgnoredAfterReload() {
    attitude("friendly", "target", 10);
    view.attitudeView(null, player, target, true);
    RelationLoader.attitudes.clear();
    assertDoesNotThrow(() -> click(11));
    diplomacy.verify(() -> RelationManager.setAttitude(any(), any(), any(), any()), never());
  }

  @Test
  void reorderingAttitudesDoesNotChangeTheClickedOption() {
    Attitude friendly = attitude("friendly", "target", 10);
    view.attitudeView(null, player, target, true);
    Collections.reverse(RelationLoader.attitudes);
    click(11);
    diplomacy.verify(() -> RelationManager.setAttitude(player, friendly, target, origin));
    diplomacy.verify(() -> RelationManager.setAttitude(player, indifferent, target, origin), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"relation", "trade", "treaty"})
  void aRemovedDefinitionIsIgnoredAfterReload(String kind) {
    RelationType choice = type("choice", "trade-agreement", kind.equals("trade"), "treaty", kind.equals("treaty"));
    openPicker(kind);
    int slot = kind.equals("relation") ? 10 : 9;
    RelationLoader.types.remove(choice);
    assertDoesNotThrow(() -> click(slot));
    assertNoDiplomaticMutation();
  }

  @ParameterizedTest
  @ValueSource(strings = {"relation", "trade", "treaty"})
  void losingFactionMembershipWhileMenuIsOpenDoesNotMutateDiplomacy(String kind) {
    type("choice", "trade-agreement", kind.equals("trade"), "treaty", kind.equals("treaty"));
    openPicker(kind);
    when(origin.getMembers()).thenReturn(List.of());
    assertDoesNotThrow(() -> click(kind.equals("relation") ? 10 : 9));
    assertNoDiplomaticMutation();
  }

  private void assertNoDiplomaticMutation() {
    diplomacy.verify(() -> RelationManager.setRelation(any(), any(), any(), any(), anyBoolean()), never());
    diplomacy.verify(() -> RelationManager.setTradeRelation(any(), any(), any(), any(), anyBoolean()), never());
    diplomacy.verify(() -> RelationManager.setTreatyRelation(any(), any(), any(), any(), anyBoolean()), never());
  }

  private void openPicker(String kind) {
    switch (kind) {
      case "relation" -> view.relationView(null, player, target, true);
      case "trade" -> view.tradeAgreementView(null, player, target, true);
      case "treaty" -> view.treatyView(null, player, target, true);
      default -> throw new AssertionError(kind);
    }
  }

  @Test
  void directoryShowsOfficialPartnersSeparatorsOtherFactionsAndPaging() {
    RelationType allied = type("ally");
    outgoing.setType(allied);
    for (int i = 0; i < 35; i++) faction("f" + i, String.format("F%02d", i), "member" + i);
    view.diplomacyListView(null, player, origin, true, -1);
    assertEquals("target", ui.data(top().getItem(0), "id"));
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, top().getItem(1).getType());
    assertEquals("f0", ui.data(top().getItem(19), "id"));
    assertNotNull(top().getItem(52));
    click(52);
    assertEquals(1, ((SFInventoryHolder) top().getHolder()).getPage());
    assertNotNull(top().getItem(45));
    Inventory secondPage = top();
    view.diplomacyListView(secondPage, player, origin, false);
    assertSame(secondPage, top());
    assertNotNull(top().getItem(0));
    click(45);
    assertEquals(0, ((SFInventoryHolder) top().getHolder()).getPage());
    click(0);
    assertEquals(SFGUI.DIPLOMACY_VIEW, menu());
    assertEquals("target", ((SFInventoryHolder) top().getHolder()).getId());
  }

  @Test
  void foreignDirectoryIsReadOnlyAndHandlesNoOfficialRelations() {
    view.diplomacyListView(null, player, target, true);
    assertTrue(text(top().getItem(0)).contains("No official relations"));
    incoming.setType(type("ally"));
    view.diplomacyListView(top(), player, target, false);
    assertFalse(text(top().getItem(0)).contains("Click to open"));
    Inventory inventory = top();
    click(0);
    assertSame(inventory, top());
  }

  @Test
  void directoryIgnoresSeparatorMissingIdsRemovedFactionsAndObsoleteNextPage() {
    for (int i = 0; i < 30; i++) faction("f" + i, "Faction" + i, "Person" + i);
    view.diplomacyListView(null, player, origin, true);
    Inventory original = top();
    click(0);
    assertSame(original, top());
    top().setItem(40, new ItemStack(Material.PAPER));
    click(40);
    FactionManager.factions.removeIf(f -> f != origin);
    click(18);
    click(52);
    assertSame(original, top());
    FactionManager.factions.clear();
    click(53);
    assertSame(original, top());
  }

  @Test
  void summaryUsesBannerFallbacksAndShowsBothDirectionalRelationsAndOverlays() {
    RelationType trade = type("trade", "trade-agreement", true);
    RelationType treaty = type("treaty", "treaty", true);
    incoming.setType(type("rival", "visible", false));
    when(target.getDiplomacyHandler().hasTradeRelation("origin")).thenReturn(true);
    when(target.getDiplomacyHandler().getTradeRelation("origin")).thenReturn(trade);
    when(target.getDiplomacyHandler().hasTreatyRelation("origin")).thenReturn(true);
    when(target.getDiplomacyHandler().getTreatyRelation("origin")).thenReturn(treaty);
    String summary = text(creator.createRelationItem(target, origin));
    assertTrue(summary.contains("outgoing"));
    assertTrue(summary.contains("incoming"));
    assertTrue(summary.contains("Trade: trade"));
    assertTrue(summary.contains("Treaty: treaty"));
    when(origin.getDiplomacyHandler().hasTradeRelation("target")).thenReturn(true);
    when(origin.getDiplomacyHandler().getTradeRelation("target")).thenReturn(trade);
    when(origin.getDiplomacyHandler().hasTreatyRelation("target")).thenReturn(true);
    when(origin.getDiplomacyHandler().getTreatyRelation("target")).thenReturn(treaty);
    for (ItemStack banner : new ItemStack[] {null, new ItemStack(Material.AIR)}) {
      when(target.getBanner()).thenReturn(banner);
      ItemStack card = creator.createDiplomacyListFactionItem(origin, target, true);
      assertEquals(Material.WHITE_BANNER, card.getType());
      assertEquals("target", ui.data(card, "id"));
      assertTrue(text(card).contains("Trade: trade"));
      assertTrue(text(card).contains("Treaty: treaty"));
    }
  }

  @Test
  void detailAndSelectionMenusRenderOnlyRelevantDefinitionsAndSupportRedraw() {
    type("hidden", "settable", false);
    RelationType trade = type("trade", "trade-agreement", true);
    RelationType treaty = type("treaty", "treaty", true);
    when(origin.getDiplomacyHandler().getTradeRelation("target")).thenReturn(trade);
    when(origin.getDiplomacyHandler().getTreatyRelation("target")).thenReturn(treaty);
    view.diplomacyView(null, player, target, true);
    Inventory detail = top();
    assertTrue(text(detail.getItem(20)).contains("trade"));
    assertTrue(text(detail.getItem(22)).contains("treaty"));
    view.diplomacyView(detail, player, target, false);
    assertSame(detail, top());
    for (int slot : new int[] {30, 12, 20, 22}) {
      player.openInventory(detail);
      click(slot);
      Inventory picker = top();
      switch (slot) {
        case 30 -> { assertEquals(SFGUI.ATTITUDE_VIEW, menu()); view.attitudeView(picker, player, target, false); }
        case 12 -> { assertEquals(SFGUI.RELATION_VIEW, menu()); assertEquals("neutral", ui.data(picker.getItem(9), "id")); assertNull(picker.getItem(10)); view.relationView(picker, player, target, false); }
        case 20 -> { assertEquals(SFGUI.TRADE_AGREEMENT_VIEW, menu()); assertEquals("trade", ui.data(picker.getItem(9), "id")); view.tradeAgreementView(picker, player, target, false); }
        case 22 -> { assertEquals(SFGUI.TREATY_VIEW, menu()); assertEquals("treaty", ui.data(picker.getItem(9), "id")); view.treatyView(picker, player, target, false); }
      }
      assertSame(picker, top());
      assertEquals(Material.BARRIER, picker.getItem(26).getType());
    }
  }

  @Test
  void ownFactionAndFactionlessVisitorsSeeOnlyBackButton() {
    view.diplomacyView(null, player, origin, true);
    assertNull(top().getItem(21));
    assertNotNull(top().getItem(53));
    when(origin.getMembers()).thenReturn(List.of());
    view.diplomacyView(null, player, target, true);
    assertNull(top().getItem(21));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void attitudeSelectionUsesServiceResultAndOnlySuccessfulChangesReturnToDetails(boolean allowed) {
    Attitude friendly = attitude("friendly", "target", 10);
    diplomacy.when(() -> RelationManager.setAttitude(player, friendly, target, origin)).thenReturn(allowed);
    view.attitudeView(null, player, target, true);
    click(11);
    assertEquals(allowed ? SFGUI.DIPLOMACY_VIEW : SFGUI.ATTITUDE_VIEW, menu());
    diplomacy.verify(() -> RelationManager.setAttitude(player, friendly, target, origin));
  }

  @ParameterizedTest
  @CsvSource({"leader,leader", "already,already in a war", "opinion,opinion", "offline,online", "code,code", "goal,goal"})
  void warDeclarationRechecksLiveConditionsBeforePromptOrGoalSelection(String scenario, String expected) {
    type("war", "threshold.amount", -10);
    view.diplomacyView(null, player, target, true);
    if (scenario.equals("leader")) when(origin.getLeader()).thenReturn("NewLeader");
    if (scenario.equals("already")) wars.when(() -> WarManager.exists(origin, target)).thenReturn(true);
    if (scenario.equals("opinion")) when(origin.getRelation("target")).thenReturn(new Relation(neutral, indifferent, 20));
    if (scenario.equals("offline")) when(target.numOnline()).thenReturn(0);
    if (scenario.equals("code")) codes.when(() -> WarDeclareCodeService.isRequired(player)).thenReturn(true);
    click(24);
    if (scenario.equals("code")) prompts.verify(() -> DeclareCodePrompt.begin(player, origin, target));
    else if (scenario.equals("goal")) verify(manager).openDeclareWarGoalPicker(player, origin, target);
    else {
      prompts.verifyNoInteractions();
      verify(manager, never()).openDeclareWarGoalPicker(any(), any(), any());
      if (!scenario.equals("leader")) verify(player).sendMessage(contains(expected));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"locked", "vassal", "tier", "limit", "war", "capacity", "success"})
  void relationChangesRespectLocksVassalHierarchyLimitsWarAndBothCapacityChecks(String scenario) {
    RelationType choice = type("choice", "vassal", scenario.equals("vassal") || scenario.equals("tier"));
    if (scenario.equals("locked")) outgoing.setType(type("bound", "lock", true));
    if (scenario.equals("vassal")) when(origin.canHaveVassals()).thenReturn(false);
    if (scenario.equals("tier")) when(origin.getTier().getTier()).thenReturn(1);
    if (scenario.equals("limit")) diplomacy.when(() -> RelationManager.atLimit(origin, choice)).thenReturn(true);
    if (scenario.equals("war")) diplomacy.when(() -> RelationManager.wartimeBlock(choice, target, origin)).thenReturn("War blocks changes");
    if (scenario.equals("capacity")) {
      diplomacy.when(() -> RelationManager.actorLacksCapacity(origin, target, choice, neutral)).thenReturn(true);
      diplomacy.when(() -> RelationManager.partnerLacksRelationCapacity(origin, target, choice)).thenReturn(true);
    }
    view.relationView(null, player, target, true);
    click(10);
    if (scenario.equals("success")) {
      diplomacy.verify(() -> RelationManager.setRelation(player, choice, target, origin, true));
      assertEquals(SFGUI.DIPLOMACY_VIEW, menu());
    } else {
      assertNoDiplomaticMutation();
      verify(player, atLeastOnce()).sendMessage(anyString());
      assertEquals(SFGUI.RELATION_VIEW, menu());
    }
  }

  @ParameterizedTest
  @CsvSource({"trade,locked", "trade,capacity", "trade,success", "treaty,locked", "treaty,capacity", "treaty,success", "treaty,clear"})
  void overlayChangesRespectLocksCapacityAndTreatyClearing(String kind, String scenario) {
    RelationType choice = type("choice", "trade-agreement", kind.equals("trade"), "treaty", kind.equals("treaty"), "clear", scenario.equals("clear"));
    RelationType current = scenario.equals("locked") ? type("bound", "lock", true) : neutral;
    if (kind.equals("trade")) when(origin.getDiplomacyHandler().getTradeRelation("target")).thenReturn(current);
    else when(origin.getDiplomacyHandler().getTreatyRelation("target")).thenReturn(current);
    if (scenario.equals("capacity") || scenario.equals("clear")) {
      diplomacy.when(() -> RelationManager.actorLacksCapacity(origin, target, choice, current)).thenReturn(true);
      diplomacy.when(() -> RelationManager.partnerLacksOverlayCapacity(origin, target, choice, null)).thenReturn(true);
    }
    openPicker(kind);
    click(9);
    if (scenario.equals("success") || scenario.equals("clear")) {
      if (kind.equals("trade")) diplomacy.verify(() -> RelationManager.setTradeRelation(player, choice, target, origin, true));
      else diplomacy.verify(() -> RelationManager.setTreatyRelation(player, choice, target, origin, true));
      assertEquals(SFGUI.DIPLOMACY_VIEW, menu());
    } else {
      assertNoDiplomaticMutation();
      verify(player, atLeastOnce()).sendMessage(anyString());
    }
  }

  @Test
  void warThresholdAndReparationCardsExplainCostsDurationAndTicketRequirements() {
    assertEquals(Material.AIR, creator.createWarButton(target, origin).getType());
    type("war", "threshold.amount", -10, "threshold.mutual", true);
    Cache.warRequireDeclareCode = true;
    assertTrue(text(creator.createWarButton(target, origin)).contains("approved War Ticket"));
    Cache.warRequireDeclareCode = false;
    assertTrue(text(creator.createWarButton(target, origin)).contains("Select a war goal"));
    List<String> lines = new ArrayList<>();
    creator.addThreshold(lines, new Threshold(config("amount", 20, "mutual", false)));
    assertTrue(ChatColor.stripColor(String.join("\n", lines)).contains("+20"));
    assertTrue(text(creator.createWarReparationsItem(origin, target)).contains("No active"));
    reparations.when(() -> WarReparationsService.findObligation(origin, target)).thenReturn(new WarReparationsObligation("target", 12.5, 3));
    reparations.when(() -> WarReparationsService.findObligation(target, origin)).thenReturn(new WarReparationsObligation("origin", 4, 2));
    String copy = text(creator.createWarReparationsItem(origin, target));
    assertTrue(copy.contains("Paying Target"));
    assertTrue(copy.contains("Receiving from Target"));
    assertTrue(copy.contains("12.5%"));
    assertTrue(copy.contains("3 day(s)"));
    assertTrue(copy.contains("2 day(s)"));
  }

  @Test
  void attitudeCardsShowModifiersCostsCurrentSelectionAndCapacityDenial() {
    Attitude friendly = attitude("friendly", "target", 15, "cost", 2, "recieve-modifiers", List.of("PRESTIGE(2)"));
    Cache.icons.put("friendly", "DIAMOND.3");
    diplomacy.when(() -> RelationManager.getDiplomaticCost(origin, target, friendly)).thenReturn(2.0);
    ItemStack selection = creator.createAttitudeItem(friendly, origin, target, true);
    assertEquals(Material.DIAMOND, selection.getType());
    assertTrue(text(selection).contains("+15"));
    assertTrue(text(selection).contains("Diplomatic Cost"));
    assertTrue(text(selection).contains("recieve modifiers"));
    diplomacy.when(() -> RelationManager.lacksCapacityForChange(10, 2, 0)).thenReturn(true);
    assertTrue(text(creator.createAttitudeItem(friendly, origin, target, true)).contains("Unavailable"));
    ItemStack current = creator.createAttitudeItem(indifferent, origin, target, true);
    assertTrue(text(current).contains("Current"));
    assertEquals(1, current.getItemMeta().getEnchantLevel(Enchantment.UNBREAKING));
    assertTrue(text(creator.createAttitudeItem(indifferent)).contains("target by: 0"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"locked", "war", "capacity", "current", "mutual", "reset", "linked", "plain"})
  void relationCardsDescribeSelectionConsequences(String scenario) {
    RelationType initial = type("choice", "mutual", scenario.equals("mutual"), "limit", 3, "target", 10, "threshold.amount", 5,
        "recieve-modifiers", List.of("PRESTIGE(2)"), "give-modifiers", List.of("PRESTIGE(1)"));
    RelationType choice = scenario.equals("linked") ? replaceChoiceWithLinked(initial) : initial;
    Cache.icons.put("choice", "GOLD_INGOT.4");
    diplomacy.when(() -> RelationManager.getDiplomaticCost(origin, target, choice)).thenReturn(2.0);
    diplomacy.when(() -> RelationManager.getDiplomaticCost(target, origin, choice)).thenReturn(3.0);
    if (scenario.equals("locked")) outgoing.setType(type("bound", "lock", true));
    if (scenario.equals("current")) outgoing.setType(choice);
    if (scenario.equals("war")) diplomacy.when(() -> RelationManager.wartimeBlock(choice, target, origin)).thenReturn("Wartime rule");
    if (scenario.equals("capacity")) {
      diplomacy.when(() -> RelationManager.actorLacksCapacity(origin, target, choice, neutral)).thenReturn(true);
      diplomacy.when(() -> RelationManager.partnerLacksRelationCapacity(origin, target, choice)).thenReturn(true);
    }
    if (scenario.equals("reset") || scenario.equals("linked")) {
      diplomacy.when(() -> RelationManager.reverseChange(target, origin, choice)).thenReturn(true);
    }
    ItemStack item = creator.createRelationTypeItem(choice, target, origin, true);
    String copy = text(item);
    assertEquals("choice", ui.data(item, "id"));
    assertTrue(copy.contains("We recieve modifiers"));
    assertTrue(copy.contains("They recieve modifiers"));
    switch (scenario) {
      case "locked", "war", "capacity" -> assertTrue(copy.contains("Unavailable"));
      case "current" -> assertEquals(1, item.getItemMeta().getEnchantLevel(Enchantment.UNBREAKING));
      case "mutual" -> { assertTrue(copy.contains("Click to request")); assertTrue(copy.contains("(them)")); }
      case "reset" -> assertTrue(copy.contains("reset their relationship"));
      case "linked" -> assertTrue(copy.contains("set their relationship"));
      default -> assertTrue(copy.contains("Click to change"));
    }
  }

  private RelationType replaceChoiceWithLinked(RelationType old) {
    RelationLoader.types.remove(old);
    type("overlord");
    RelationType linked = type("choice", "link", "overlord", "vassal", true, "recieve-modifiers", List.of("PRESTIGE(2)"), "give-modifiers", List.of("PRESTIGE(1)"));
    diplomacy.when(() -> RelationManager.reverseChange(target, origin, linked)).thenReturn(true);
    return linked;
  }

  @ParameterizedTest
  @CsvSource({"trade,locked", "trade,capacity", "trade,current", "trade,mutual", "trade,plain", "trade,summary", "treaty,locked", "treaty,capacity", "treaty,current", "treaty,mutual", "treaty,plain", "treaty,summary", "treaty,clear", "treaty,clearEmpty"})
  void overlayCardsExplainEffectsRestrictionsAndAvailableActions(String kind, String scenario) {
    boolean trade = kind.equals("trade");
    boolean mutual = scenario.equals("mutual");
    boolean clear = scenario.startsWith("clear");
    RelationType choice = type("choice", "trade-agreement", trade, "treaty", !trade, "mutual", mutual,
        "clear", clear, "target", mutual ? 15 : -5, "blocks-war", !trade, "threshold.amount", 5,
        "trade-effects-us", List.of("TRADE_POWER(2)"), "trade-effects-them", List.of("TRADE_POWER(1)"), "installation-access", 1.0);
    Cache.icons.put("choice", "DIAMOND.7");
    RelationType current = scenario.equals("locked") ? type("bound", "lock", true) : scenario.equals("current") ? choice : scenario.equals("clear") ? neutral : null;
    if (trade) when(origin.getDiplomacyHandler().getTradeRelation("target")).thenReturn(current);
    else when(origin.getDiplomacyHandler().getTreatyRelation("target")).thenReturn(current);
    diplomacy.when(() -> RelationManager.getDiplomaticCost(origin, target, choice)).thenReturn(2.0);
    diplomacy.when(() -> RelationManager.getDiplomaticCost(target, origin, choice)).thenReturn(3.0);
    if (scenario.equals("capacity")) {
      diplomacy.when(() -> RelationManager.actorLacksCapacity(origin, target, choice, current)).thenReturn(true);
      diplomacy.when(() -> RelationManager.partnerLacksOverlayCapacity(origin, target, choice, null)).thenReturn(true);
    }
    boolean full = !scenario.equals("summary");
    ItemStack item = trade ? creator.createTradeAgreementTypeItem(player, choice, target, origin, full)
        : creator.createTreatyTypeItem(player, choice, target, origin, full);
    String copy = text(item);
    assertEquals(Material.DIAMOND, item.getType());
    assertTrue(copy.contains("Diplomatic Cost"));
    if (trade) { assertTrue(copy.contains("Our Guilds")); assertTrue(copy.contains("Their Guilds")); assertTrue(copy.contains("Installations")); }
    else assertTrue(copy.contains("Blocks declaring war"));
    switch (scenario) {
      case "locked", "capacity" -> assertTrue(copy.contains("Unavailable"));
      case "current" -> { assertTrue(copy.contains("Current")); assertEquals(1, item.getItemMeta().getEnchantLevel(Enchantment.UNBREAKING)); }
      case "mutual" -> assertTrue(copy.contains("Click to request"));
      case "summary" -> assertTrue(copy.contains("more information"));
      case "clear" -> assertTrue(copy.contains("Click to clear treaty"));
      case "clearEmpty" -> assertTrue(copy.contains("Current"));
      default -> assertTrue(copy.contains("Click to set"));
    }
    if (trade && List.of("current", "mutual", "plain").contains(scenario)) {
      RelationType proposed = scenario.equals("current") ? null : choice;
      economics.verify(() -> EconomicImpact.applyTradeAgreementChange(anyList(), eq(player), eq(origin), eq(target), eq(proposed), eq(false), any(), eq(false)));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"attitude", "relation", "trade", "treaty", "detail"})
  void removingTheTargetWhileAMenuIsOpenSafelyIgnoresTheOldCards(String kind) {
    type("choice", "trade-agreement", kind.equals("trade"), "treaty", kind.equals("treaty"));
    int slot;
    if (kind.equals("attitude")) { view.attitudeView(null, player, target, true); slot = 10; }
    else if (kind.equals("detail")) { view.diplomacyView(null, player, target, true); slot = 12; }
    else { openPicker(kind); slot = kind.equals("relation") ? 10 : 9; }
    FactionManager.factions.remove(target);
    Inventory original = top();
    assertDoesNotThrow(() -> click(slot));
    assertSame(original, top());
    assertNoDiplomaticMutation();
    diplomacy.verify(() -> RelationManager.setAttitude(any(), any(), any(), any()), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"attitude", "detail"})
  void staleActorMembershipIsRejectedBeforeOpeningOrApplyingAnAttitude(String kind) {
    if (kind.equals("attitude")) view.attitudeView(null, player, target, true);
    else view.diplomacyView(null, player, target, true);
    when(origin.getMembers()).thenReturn(List.of());
    Inventory original = top();
    assertDoesNotThrow(() -> click(kind.equals("attitude") ? 10 : 30));
    assertSame(original, top());
    diplomacy.verify(() -> RelationManager.setAttitude(any(), any(), any(), any()), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"attitude", "relation", "trade", "treaty"})
  void navigationButtonsNeverDispatchDiplomacyChanges(String kind) {
    if (kind.equals("attitude")) view.attitudeView(null, player, target, true);
    else openPicker(kind);
    click(26);
    assertNoDiplomaticMutation();
    diplomacy.verify(() -> RelationManager.setAttitude(any(), any(), any(), any()), never());
  }

  @Test
  void summaryCardsFallbackToWhiteBannerAndHideInvisibleRelationLabels() {
    outgoing.setType(type("hidden", "visible", false));
    for (ItemStack banner : new ItemStack[] {null, new ItemStack(Material.AIR)}) {
      when(target.getBanner()).thenReturn(banner);
      ItemStack item = creator.createRelationItem(target, origin);
      assertEquals(Material.WHITE_BANNER, item.getType());
      assertTrue(text(item).contains("Our opinion of them: -20"));
    }
    assertTrue(text(creator.createNoTradeAgreementItem()).contains("No Trade Agreement"));
    assertTrue(text(creator.createNoTreatyItem()).contains("No Treaty"));
  }

  @Test
  void emptyUnusableAndUnownedInventoryClicksDoNotReachDiplomacyServices() {
    view.attitudeView(null, player, target, true);
    Inventory inventory = top();
    InventoryClickEvent empty = ui.click(player, 0);
    view.click(empty, inventory, player);
    assertFalse(empty.isCancelled());
    ItemStack air = new ItemStack(Material.AIR);
    when(air.getItemMeta()).thenReturn(null);
    inventory.setItem(0, air);
    InventoryClickEvent invalid = ui.click(player, 0);
    view.click(invalid, inventory, player);
    assertTrue(invalid.isCancelled());
    Inventory unowned = ui.inventory(null, 9, "Unowned inventory");
    unowned.setItem(0, new ItemStack(Material.PAPER));
    player.openInventory(unowned);
    InventoryClickEvent unownedClick = ui.click(player, 0);
    view.click(unownedClick, unowned, player);
    assertFalse(unownedClick.isCancelled());
    assertNoDiplomaticMutation();
    diplomacy.verify(() -> RelationManager.setAttitude(any(), any(), any(), any()), never());
  }
}
