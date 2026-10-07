package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.LevyEntry;
import net.tfminecraft.simplefactions.army.MilitaryExpansion;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.government.stability.StabilityReport;
import net.tfminecraft.simplefactions.government.stability.StabilityStatus;
import net.tfminecraft.simplefactions.government.stability.StateStability;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.laws.LawEffect;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.vehicles.maintenance.VehicleMaintenanceStore;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.freeze.PreparationFreeze;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class ArmyMenusCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Player player;
  private InventoryManager navigation;
  private TierTitleView titles;
  private MilitaryView military;
  private java.util.Map<Player, Tier> oldForming;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> s = mockStatic(type);
    scopes.add(s);
    return s;
  }

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    fixture.lawGroup("military", Map.of());
    faction = fixture.saved("realm", "Leader");
    faction.getOrCreateMainGuild();
    player = fixture.player("Leader");
    faction.addProvince(10);
    navigation = mock(InventoryManager.class);
    navigation.confirming = new HashMap<>();
    when(navigation.createBackButton(any())).thenAnswer(c -> new ItemStack(Material.BARRIER));
    titles = new TierTitleView(navigation);
    military = new MilitaryView(navigation);
    oldForming = TitleManager.isFormingTitle;
    TitleManager.isFormingTitle = new HashMap<>();
    scoped(IconGetter.class);
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator items = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(items);
    when(items.getItemsAdderItem(anyString())).thenAnswer(c -> new ItemStack(Material.PAPER));
    scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(api);
  }

  @AfterEach
  void close() {
    try {
      for (int n = scopes.size() - 1; n >= 0; n--) scopes.get(n).close();
    } finally {
      TitleManager.isFormingTitle = oldForming;
      fixture.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"leadership", "removed"})
  void anOpenTierAliasButtonCannotMutateAFormerlyLedOrRemovedFaction(String change) {
    faction.getTier().getAliases().add("March");
    titles.tierView(null, player, faction, true);
    Inventory inventory = top();
    int slot = -1;
    for (int n = 0; n < inventory.getSize(); n++)
      if (inventory.getItem(n) != null
          && inventory.getItem(n).getType() == Material.YELLOW_CONCRETE) {
        slot = n;
        break;
      }
    assertTrue(slot >= 0);
    assertEquals(-1, faction.getTier().getIndex());
    if (change.equals("leadership")) {
      faction.addMember("Successor");
      faction.setLeader("Successor");
    } else FactionManager.factions.clear();
    InventoryClickEvent event = fixture.ui.click(player, slot);
    assertDoesNotThrow(() -> titles.click(event, inventory, player));
    assertEquals(-1, faction.getTier().getIndex());
    assertTrue(event.isCancelled());
  }

  @ParameterizedTest
  @ValueSource(strings = {"leadership", "requirements", "owner"})
  void titleClaimClicksRecheckTheCurrentLeaderEligibilityAndOwner(String change) {
    Title title = fixture.title("North County", "county", 10);
    titles.titleTypeView(null, player, faction, title.getTier(), true, 0);
    Inventory inventory = top();
    assertEquals(Material.YELLOW_CONCRETE, inventory.getItem(0).getType());
    if (change.equals("leadership")) {
      faction.addMember("Successor");
      faction.setLeader("Successor");
    }
    if (change.equals("requirements")) faction.getProvinceHandler().removeProvince(10, false);
    Faction liege = null;
    if (change.equals("owner")) {
      liege = fixture.saved("liege", "Liege");
      liege.getOrCreateMainGuild();
      fixture.subject(liege, faction);
      liege.addTitle(title);
    }
    InventoryClickEvent event = fixture.ui.click(player, 0);
    titles.click(event, inventory, player);
    assertFalse(faction.hasTitle(title), "A stale yellow claim button must not grant a title");
    if (liege != null) assertTrue(liege.hasTitle(title));
    assertTrue(event.isCancelled());
  }

  @ParameterizedTest
  @ValueSource(strings = {"independent", "ownership", "last_title"})
  void aStaleGrantButtonCannotTransferANoLongerGrantableTitle(String change) {
    Faction subject = fixture.saved("subject", "Subject");
    subject.getOrCreateMainGuild();
    subject.addProvince(20);
    fixture.subject(faction, subject);
    Title title = fixture.title("Subject County", "county", 20);
    Title retained = fixture.title("Home County", "county", 10);
    faction.addTitle(title);
    faction.addTitle(retained);
    assertTrue(TitleManager.getGrantableTitles(faction, subject, title.getTier()).contains(title));
    titles.titleTypeView(null, player, subject, title.getTier(), true, 0);
    Inventory inventory = top();
    assertEquals(Material.GREEN_CONCRETE, inventory.getItem(0).getType());
    if (change.equals("independent")) {
      faction.setRelation(
          subject,
          new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
      subject.setRelation(
          faction,
          new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
    }
    if (change.equals("ownership")) faction.stripTitle(title);
    if (change.equals("last_title")) faction.stripTitle(retained);
    boolean previouslyOwned = faction.hasTitle(title);
    InventoryClickEvent event = fixture.ui.click(player, 0);
    titles.click(event, inventory, player);
    assertFalse(subject.hasTitle(title), "A stale grant must not confer the title");
    assertEquals(previouslyOwned, faction.hasTitle(title));
    assertTrue(event.isCancelled());
  }

  @Test
  void tierAliasesKeepTheConfiguredNameAndOnlyTheCurrentTierCanBeChanged() {
    faction.getTier().getAliases().add("March");
    titles.tierView(null, player, faction, true);
    Inventory inventory = top();
    ItemStack base = inventory.getItem(9), alias = inventory.getItem(10);
    assertEquals(Material.GREEN_CONCRETE, base.getType());
    assertEquals("province", name(base));
    assertEquals(Material.YELLOW_CONCRETE, alias.getType());
    assertEquals("March (province)", name(alias));
    assertEquals(Material.RED_CONCRETE, inventory.getItem(18).getType());
    titles.click(fixture.ui.click(player, 10), inventory, player);
    assertEquals(0, faction.getTier().getIndex());
    assertEquals(Material.GREEN_CONCRETE, inventory.getItem(10).getType());
    titles.click(fixture.ui.click(player, 9), inventory, player);
    assertEquals(-1, faction.getTier().getIndex());
    titles.click(fixture.ui.click(player, 18), inventory, player);
    assertEquals(-1, faction.getTier().getIndex());
    Player visitor = fixture.player("Visitor");
    titles.tierView(null, visitor, faction, true);
    Inventory publicMenu = visitor.getOpenInventory().getTopInventory();
    assertNull(publicMenu.getItem(9));
    assertEquals(Material.BARRIER, publicMenu.getItem(53).getType());
  }

  @Test
  void titleItemsDistinguishOwnedAvailableUnreachableAndAnotherOwnersLand() {
    Title own = fixture.title("Home", "county", 10);
    Title available = fixture.title("East", "county", 11);
    Title distant = fixture.title("West", "county", 30);
    Title theirs = fixture.title("Foreign", "county", 40);
    faction.addMember("Citizen");
    faction.addProvince(11);
    faction.addTitle(own);
    Faction other = fixture.saved("other", "Other");
    other.addProvince(40);
    other.addTitle(theirs);
    List<ItemStack> rendered =
        titles.creator.getSortedTitleItems(
            player, faction, List.of(theirs, distant, own, available));
    assertEquals(
        List.of(own.getId(), available.getId(), distant.getId(), theirs.getId()),
        rendered.stream().map(this::id).toList());
    assertEquals(
        List.of(
            Material.GREEN_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.RED_CONCRETE,
            Material.GRAY_CONCRETE),
        rendered.stream().map(ItemStack::getType).toList());
    assertTrue(lore(rendered.get(0)).contains("Current"));
    assertTrue(lore(rendered.get(0)).contains("Prestige: +50"));
    assertTrue(lore(rendered.get(1)).contains("Click to claim"));
    assertTrue(lore(rendered.get(3)).contains("Owned by: other"));
    assertEquals("claim", string(rendered.get(2), "type"));
  }

  @Test
  void claimingAnAvailableTitleChangesOwnershipAndRefreshesItsButton() {
    Title title = fixture.title("North", "county", 10);
    titles.titleView(null, player, faction, true);
    assertEquals("county", id(top().getItem(10)));
    titles.click(fixture.ui.click(player, 10), top(), player);
    Inventory inventory = top();
    assertEquals(SFGUI.TITLE_TYPE_VIEW, ((SFInventoryHolder) inventory.getHolder()).getType());
    titles.click(fixture.ui.click(player, 0), inventory, player);
    assertTrue(faction.hasTitle(title));
    assertSame(faction, FactionManager.getTitleOwner(title));
    assertEquals(Material.GREEN_CONCRETE, inventory.getItem(0).getType());
    verify(player).sendMessage("§aClaimed the title North");
    titles.click(fixture.ui.click(player, 0), inventory, player);
    assertEquals(1, faction.getTitles().size());
  }

  @Test
  void anAuthorizedLiegeCanGrantASecondCountyAndKeepsItsOwnTitle() {
    Faction subject = fixture.saved("subject", "Subject");
    subject.addProvince(20);
    fixture.subject(faction, subject);
    Title theirs = fixture.title("Subject County", "county", 20),
        home = fixture.title("Home County", "county", 10);
    faction.addTitle(theirs);
    faction.addTitle(home);
    titles.titleView(null, player, subject, true);
    assertEquals("county", id(top().getItem(10)));
    titles.titleTypeView(null, player, subject, theirs.getTier(), true, 0);
    assertTrue(lore(top().getItem(0)).contains("Click to grant this title to subject"));
    titles.click(fixture.ui.click(player, 0), top(), player);
    assertTrue(subject.hasTitle(theirs));
    assertFalse(faction.hasTitle(theirs));
    assertTrue(faction.hasTitle(home));
    verify(player).sendMessage("§aGranted subject the title Subject County");
    assertEquals("No titles to give away", name(top().getItem(10)));
  }

  @Test
  void titlePaginationClearsPreviousContentsAndCanNavigateBothDirections() {
    for (int n = 0; n < 47; n++) fixture.title("Title " + n, "county", 100 + n);
    Tier county = TierLoader.getByString("county");
    titles.titleTypeView(null, player, faction, county, true, -4);
    Inventory inventory = top();
    assertEquals(0, ((SFInventoryHolder) inventory.getHolder()).getPage());
    assertNotNull(inventory.getItem(44));
    assertNotNull(inventory.getItem(51));
    titles.click(fixture.ui.click(player, 51), inventory, player);
    assertEquals(1, ((SFInventoryHolder) inventory.getHolder()).getPage());
    assertNotNull(inventory.getItem(1));
    assertNull(inventory.getItem(2));
    assertNull(inventory.getItem(51));
    titles.click(fixture.ui.click(player, 50), inventory, player);
    assertEquals(0, ((SFInventoryHolder) inventory.getHolder()).getPage());
    assertNull(inventory.getItem(50));
    assertNotNull(inventory.getItem(44));
  }

  @Test
  void anEmptyCurrentPageCanStillNavigateBackAfterTitlesAreRemoved() {
    for (int n = 0; n < 46; n++) fixture.title("Title " + n, "county", 100 + n);
    Tier county = TierLoader.getByString("county");
    titles.titleTypeView(null, player, faction, county, true, 1);
    Inventory inventory = top();
    TitleLoader.getTitles().clear();
    titles.titleTypeView(inventory, player, faction, county, false, 1);
    assertNull(inventory.getItem(0));
    assertNotNull(inventory.getItem(50));
    assertDoesNotThrow(() -> titles.click(fixture.ui.click(player, 50), inventory, player));
    assertEquals(0, ((SFInventoryHolder) inventory.getHolder()).getPage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"former_leader", "disabled"})
  void newTitlePromptRejectsFormerLeadersAndTiersThatCannotBeFormed(String reason) {
    Tier county = configureTier("county", 2, reason.equals("disabled") ? 0 : 1);
    titles.titleTypeView(null, player, faction, county, true, 0);
    Inventory inventory = top();
    if (reason.equals("former_leader")) {
      faction.addMember("Successor");
      faction.setLeader("Successor");
    }
    titles.click(fixture.ui.click(player, 52), inventory, player);
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
    verify(player, never()).sendMessage("§eType the name of the new county §ein chat.");
  }

  @Test
  void enabledCountyCreationPromptsForANameOnlyWithEnoughUnclaimedLand() {
    Tier county = configureTier("county", 2, 2);
    titles.titleTypeView(null, player, faction, county, true, 0);
    assertTrue(lore(top().getItem(52)).contains("Currently: 1 [50%]"));
    titles.click(fixture.ui.click(player, 52), top(), player);
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
    faction.addProvince(11);
    titles.titleTypeView(null, player, faction, county, true, 0);
    assertTrue(lore(top().getItem(52)).contains("Click to create"));
    titles.click(fixture.ui.click(player, 52), top(), player);
    assertSame(county, TitleManager.isFormingTitle.get(player));
    verify(player).sendMessage("§eType the name of the new county §ein chat.");
    verify(player).closeInventory();
  }

  @Test
  void newCountiesAreLimitedByMembersAndNewHigherTitlesByTheLiege() {
    Tier county = configureTier("county", 2, 1);
    Title home = fixture.title("Home", "county", 10);
    faction.addTitle(home);
    faction.addProvince(11);
    titles.titleTypeView(null, player, faction, county, true, 0);
    titles.click(fixture.ui.click(player, 52), top(), player);
    verify(player).sendMessage("§cYou need more members to form more counties!");
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
    faction.addMember("Citizen");
    Faction liege = fixture.saved("liege", "Liege");
    liege.addProvince(20);
    fixture.subject(liege, faction);
    titles.titleTypeView(null, player, faction, county, true, 0);
    titles.click(fixture.ui.click(player, 52), top(), player);
    verify(player)
        .sendMessage("§cForming a new county title would make you a higher tier than your liege!");
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
  }

  @Test
  void militaryRendersLiveCountsAndOnlyTheCurrentLeaderGetsAdjustmentButtons() {
    Regiment infantry = regiment("infantry", false, false, 3, 2),
        levy = regiment("levy", true, false, 0, 0);
    infantry.setFreeSlots(1);
    infantry.getDescription().add("Citizen militia");
    infantry.setSentToOverlord(1);
    military.militaryView(null, player, faction, true);
    Inventory inventory = top();
    assertEquals("realm Military", name(inventory.getItem(10)));
    assertTrue(lore(inventory.getItem(10)).contains("Total Soldiers: 0/3"));
    assertTrue(lore(inventory.getItem(10)).contains("Total Upkeep: 4"));
    assertTrue(lore(inventory.getItem(12)).contains("3 (1 free)"));
    assertTrue(lore(inventory.getItem(12)).contains("sent as levies"));
    assertTrue(lore(inventory.getItem(12)).contains("Citizen militia"));
    assertEquals("increase", string(inventory.getItem(3), "type"));
    assertEquals("decrease", string(inventory.getItem(21), "type"));
    assertEquals(Material.AIR, inventory.getItem(4).getType());
    assertEquals(Material.AIR, inventory.getItem(22).getType());
    assertEquals(Material.MINECART, inventory.getItem(49).getType());
    Player citizen = fixture.player("Citizen");
    faction.addMember("Citizen");
    military.militaryView(inventory, citizen, faction, false);
    assertEquals(Material.AIR, inventory.getItem(3).getType());
    assertEquals(Material.AIR, inventory.getItem(21).getType());
    assertTrue(lore(inventory.getItem(13)).contains("Total Levies: 0"));
  }

  @Test
  void militaryQueueClicksEnqueueConfirmCancellationAndConfirmDecreases() {
    Regiment infantry = regiment("infantry", false, false, 2, 1);
    military.militaryView(null, player, faction, true);
    Inventory inventory = top();
    military.click(fixture.ui.click(player, 3), inventory, player);
    assertEquals(1, faction.getMilitary().getQueue().size());
    assertSame(infantry, faction.getMilitary().getQueue().get(0).getRegiment());
    assertEquals("Expanding infantry", name(inventory.getItem(39)));
    verify(player).sendMessage("§eQueued infantry");
    military.click(fixture.ui.click(player, 39), inventory, player);
    verify(navigation)
        .openQueueCancelConfirm(
            player,
            faction,
            QueueCancelPayload.military("realm", 0),
            "§eCancel queued regiment expansion?");
    assertEquals(1, faction.getMilitary().getQueue().size());
    military.click(fixture.ui.click(player, 21), inventory, player);
    verify(navigation).confirmView(player, faction, "regiment", "infantry");
    assertSame(faction, navigation.confirming.get(player));
    assertEquals(2, infantry.getCurrentSlots());
  }

  @Test
  void professionalRecruitmentAndFullQueuesAreRejectedWithoutChangingTheQueue() {
    var group = faction.getLawHandler().getGroup("military");
    var law = group.getLaw("current");
    group.setCurrent(law);
    YamlConfiguration rules = new YamlConfiguration();
    rules.set("rules", List.of("CAN_RECRUIT_PROFESSIONAL_ARMY false"));
    law.getScopedEffects().put(Scope.FACTION, new LawEffect(Scope.FACTION, rules));
    Regiment professional = regiment("professional", false, true, 0, 3);
    military.militaryView(null, player, faction, true);
    Inventory inventory = top();
    assertTrue(lore(inventory.getItem(12)).contains("Blocked"));
    assertTrue(lore(inventory.getItem(3)).contains("Your laws do not allow"));
    military.click(fixture.ui.click(player, 3), inventory, player);
    assertTrue(faction.getMilitary().getQueue().isEmpty());
    verify(player).sendMessage("§cYour laws do not allow recruiting a professional army.");
    Regiment militia = regiment("militia", false, false, 0, 1);
    for (int n = 0; n < 3; n++) assertTrue(faction.getMilitary().enqueue(militia));
    military.militaryView(inventory, player, faction, false);
    military.click(fixture.ui.click(player, 4), inventory, player);
    assertEquals(3, faction.getMilitary().getQueue().size());
    verify(player).sendMessage("§cQueue is full");
    assertTrue(lore(inventory.getItem(40)).contains("Queued..."));
    assertNotNull(inventory.getItem(41));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing_item",
        "no_meta",
        "no_id",
        "missing_faction",
        "no_regiment",
        "missing_regiment",
        "no_type",
        "former_leader",
        "other_type"
      })
  void militaryIgnoresStaleOrNonActionButtons(String state) {
    Regiment r = regiment("militia", false, false, 1, 0);
    military.militaryView(null, player, faction, true);
    Inventory inventory = top();
    ItemStack button = military.creator.createRegimentIncreaseButton(faction, r);
    if (state.equals("missing_item")) button = null;
    if (state.equals("no_meta")) button = new ItemStack(Material.STONE);
    if (state.equals("no_id")) remove(button, "id");
    if (state.equals("missing_faction")) set(button, "id", "missing");
    if (state.equals("no_regiment")) remove(button, "regiment");
    if (state.equals("missing_regiment")) set(button, "regiment", "missing");
    if (state.equals("no_type")) remove(button, "type");
    if (state.equals("former_leader")) {
      faction.addMember("Successor");
      faction.setLeader("Successor");
    }
    if (state.equals("other_type")) set(button, "type", "unknown");
    inventory.setItem(3, button);
    InventoryClickEvent event = fixture.ui.click(player, 3);
    military.click(event, inventory, player);
    assertTrue(event.isCancelled());
    assertTrue(faction.getMilitary().getQueue().isEmpty());
    assertTrue(navigation.confirming.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong_holder", "removed", "former_leader"})
  void cancellationButtonsCannotActForAMissingOrForeignFaction(String state) {
    Regiment r = regiment("militia", false, false, 1, 0);
    faction.getMilitary().enqueue(r);
    military.militaryView(null, player, faction, true);
    Inventory inventory = top();
    if (state.equals("wrong_holder")) {
      Inventory other = fixture.ui.inventory(null, 54, "Military");
      other.setItem(39, inventory.getItem(39));
      inventory = other;
      player.openInventory(inventory);
    }
    if (state.equals("removed")) FactionManager.factions.clear();
    if (state.equals("former_leader")) {
      faction.addMember("Successor");
      faction.setLeader("Successor");
    }
    military.click(fixture.ui.click(player, 39), inventory, player);
    verify(navigation, never()).openQueueCancelConfirm(any(), any(), anyString(), anyString());
    assertEquals(1, faction.getMilitary().getQueue().size());
  }

  @Test
  void regimentSummaryIncludesReducedUpkeepFrozenTimersAndBoundedLevyContributors() {
    Regiment r = regiment("infantry", false, false, 4, 2);
    r.setFreeSlots(0);
    faction
        .getRank()
        .getModifiers()
        .add(new FactionModifier(FactionModifiers.MILITARY_UPKEEP, -25));
    assertTrue(lore(military.creator.createMilitarySummary(faction)).contains("6.00d (From 8.00)"));
    assertTrue(
        lore(military.creator.createRegimentIcon(faction, r)).contains("6.00d (1.50d per slot)"));
    Regiment levy = regiment("levy", true, false, 0, 0);
    for (int n = 0; n < 12; n++) levy.addLevyEntry(new LevyEntry(faction, n + 1));
    String levyLore = lore(military.creator.createRegimentIcon(faction, levy));
    assertTrue(levyLore.contains("Total Levies: 78"));
    assertTrue(levyLore.contains("And 2 more..."));
    assertEquals(10, levyLore.lines().filter(line -> line.startsWith("- realm:")).count());
    try (MockedStatic<PreparationFreeze> freeze =
        mockStatic(PreparationFreeze.class, CALLS_REAL_METHODS)) {
      freeze
          .when(() -> PreparationFreeze.frozenUntil(eq(faction), any(Instant.class)))
          .thenReturn(Instant.now().plusSeconds(3600));
      ItemStack queue = military.creator.createQueueItem(new MilitaryExpansion(r, 60), 0, faction);
      assertTrue(lore(queue).contains("Frozen: battle postponed"));
      assertEquals(
          QueueCancelPayload.military("realm", 0),
          queue
              .getItemMeta()
              .getPersistentDataContainer()
              .get(Keys.QUEUE_CANCEL, PersistentDataType.STRING));
    }
  }

  @Test
  void formedHomeCompanyAppearsAfterFactionRegimentsAndDisappearsWhenRemoved() {
    Regiment r = regiment("militia", false, false, 1, 0);
    MercenaryCompany company =
        new MercenaryCompany(faction.getOrCreateMainGuild(), "The Guard", new Regiment(r), 0);
    faction.getOrCreateMainGuild().setCompany(company);
    military.militaryView(null, player, faction, true);
    Inventory inventory = top();
    assertEquals("The Guard", name(inventory.getItem(13)));
    assertTrue(lore(inventory.getItem(13)).contains("1 slots"));
    assertTrue(lore(inventory.getItem(13)).contains("Only filled slots count toward offense"));
    faction.getOrCreateMainGuild().setCompany(null);
    military.militaryView(inventory, player, faction, false);
    assertEquals(Material.AIR, inventory.getItem(13).getType());
  }

  @Test
  void vehiclePoolIconShowsTheRegistryAndOutstandingMaintenance() {
    PlayerVehicleRegistry registry = mock(PlayerVehicleRegistry.class);
    VehicleMaintenanceStore store = mock(VehicleMaintenanceStore.class);
    PlayerVehicleRecord record =
        new PlayerVehicleRecord(
            player.getUniqueId(), "vehicle-123", "wagon", OwnershipMode.POOL, null, "realm");
    when(registry.getPoolVehicles("realm")).thenReturn(List.of(record));
    when(store.unpaidUuids()).thenReturn(Set.of("vehicle-123"));
    when(fixture.ui.plugin.getVehicleMaintenanceStore()).thenReturn(store);
    try (MockedStatic<SimpleFactions> plugin =
        mockStatic(SimpleFactions.class, CALLS_REAL_METHODS)) {
      plugin.when(SimpleFactions::getVehicleRegistry).thenReturn(registry);
      ItemStack item = military.creator.createVehiclePoolIcon(faction);
      assertEquals("Vehicle Pool", name(item));
      assertTrue(lore(item).contains("Unpaid: 1 of 1"));
      assertTrue(lore(item).contains("wagon"));
    }
  }

  @Test
  void anOldTierAliasCannotBeAppliedAfterAClaimPromotesTheFaction() {
    faction.getTier().getAliases().add("March");
    titles.tierView(null, player, faction, true);
    Inventory inventory = top();
    assertEquals(Material.YELLOW_CONCRETE, inventory.getItem(10).getType());
    faction.addTitle(fixture.title("New County", "county", 10));
    assertEquals("county", faction.getTier().getId());
    titles.click(fixture.ui.click(player, 10), inventory, player);
    assertEquals(-1, faction.getTier().getIndex());
    assertEquals("county", faction.getTier().getFormattedName());
  }

  @Test
  void foreignMilitaryUsesTheReportedRendererForOpeningAndRefresh() {
    Player visitor = fixture.player("Visitor");
    Inventory reported =
        fixture.ui.inventory(
            new SFInventoryHolder("realm", SFGUI.MILITARY_VIEW), 54, "Military View");
    try (MockedStatic<EspionageService> intelligence = mockStatic(EspionageService.class);
        MockedStatic<ReportedMenus> menus = mockStatic(ReportedMenus.class)) {
      menus
          .when(
              () -> ReportedMenus.open(visitor, "realm", SFGUI.MILITARY_VIEW, 54, "Military View"))
          .thenReturn(reported);
      military.militaryView(null, visitor, faction, true);
      assertSame(reported, visitor.getOpenInventory().getTopInventory());
      military.militaryView(reported, visitor, faction, false);
      menus.verify(() -> ReportedMenus.military(reported, visitor, faction, navigation), times(2));
      menus.verify(
          () -> ReportedMenus.open(visitor, "realm", SFGUI.MILITARY_VIEW, 54, "Military View"),
          times(1));
      assertNull(reported.getItem(3));
    }
  }

  @Test
  void missingPluginOrFactionStillProducesAnEmptyVehiclePoolDescription() {
    SimpleFactions previous = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      ItemStack item = military.creator.createVehiclePoolIcon(null);
      assertTrue(lore(item).contains("No vehicles in the pool."));
    } finally {
      SimpleFactions.plugin = previous;
    }
  }

  @Test
  void aCompositeTitleListsItsRequiredCountiesAndAnEnabledDuchyUsesFreeTitles() {
    faction.addMember("Citizen");
    faction.addProvince(11);
    Title first = fixture.title("First County", "county", 10),
        second = fixture.title("Second County", "county", 11);
    faction.addTitle(first);
    faction.addTitle(second);
    Tier duchy = configureTier("duchy", 3, 2);
    JsonObject json = new JsonObject();
    json.addProperty("name", "Combined Duchy");
    JsonArray required = new JsonArray();
    required.add(first.getId());
    required.add(second.getId());
    json.add("titles", required);
    Title composite = new Title(duchy, "combined", json);
    TitleLoader.getTitles().add(composite);
    ItemStack existing =
        titles.creator.createTitleItem(
            player,
            faction,
            composite,
            TitleManager.getProvinces(faction),
            TitleManager.getTitles(faction));
    assertEquals(Material.YELLOW_CONCRETE, existing.getType());
    assertTrue(lore(existing).contains("Required Titles:"));
    assertTrue(lore(existing).contains("First County"));
    assertTrue(lore(existing).contains("Second County"));
    TitleLoader.getTitles().remove(composite);
    titles.titleTypeView(null, player, faction, duchy, true, 0);
    ItemStack create = top().getItem(52);
    assertTrue(lore(create).contains("2 free titles of the type county"));
    assertTrue(lore(create).contains("Currently: 2 [100%]"));
    titles.click(fixture.ui.click(player, 52), top(), player);
    assertSame(duchy, TitleManager.isFormingTitle.get(player));
    verify(player).sendMessage("§eType the name of the new duchy §ein chat.");
    assertEquals(0, titles.creator.getNewTitleCost(faction, null));
    when(IconGetter.hasIcon("duchy")).thenReturn(true);
    when(IconGetter.getIcon("duchy")).thenAnswer(call -> new ItemStack(Material.NETHER_STAR));
    ItemStack category = titles.creator.createTierViewItem(faction, duchy, 1);
    assertEquals(Material.NETHER_STAR, category.getType());
    assertEquals("duchy", id(category));
  }

  @Test
  void aClaimAboveTheLiegesTierIsRefusedWithoutChangingEitherTitleSet() {
    Title claim = fixture.title("North County", "county", 10);
    Faction liege = fixture.saved("liege", "Liege");
    liege.addProvince(20);
    fixture.subject(liege, faction);
    titles.titleTypeView(null, player, faction, claim.getTier(), true, 0);
    assertEquals(Material.YELLOW_CONCRETE, top().getItem(0).getType());
    titles.click(fixture.ui.click(player, 0), top(), player);
    assertFalse(faction.hasTitle(claim));
    assertTrue(liege.getTitles().isEmpty());
    verify(player)
        .sendMessage(
            "§cCannot form this title as it would make your tier higher than that of your"
                + " overlord!");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aCollapsingGovernmentCannotClaimOrStartCreatingTitles(boolean existing) {
    Tier county = configureTier("county", 2, 1);
    Title claim = fixture.title("North County", "county", existing ? 10 : 20);
    StabilityReport report = new StabilityReport();
    report.status = StabilityStatus.COLLAPSING;
    report.stability = 5;
    try (MockedStatic<StateStability> state = mockStatic(StateStability.class)) {
      state.when(() -> StateStability.of(faction)).thenReturn(report);
      titles.titleTypeView(null, player, faction, county, true, 0);
      titles.click(fixture.ui.click(player, existing ? 0 : 52), top(), player);
      assertFalse(faction.hasTitle(claim));
      assertFalse(TitleManager.isFormingTitle.containsKey(player));
      verify(player).sendMessage("§cA Collapsing State cannot form titles.");
    }
  }

  @Test
  void grantingOneOfSeveralSpareTitlesKeepsTheRemainingGrantChoicesOpen() {
    faction.addMember("Citizen");
    faction.addMember("Other");
    Faction subject = fixture.saved("subject", "Subject");
    subject.addMember("Member");
    subject.addProvince(20);
    subject.addProvince(21);
    fixture.subject(faction, subject);
    Title first = fixture.title("First", "county", 20),
        second = fixture.title("Second", "county", 21),
        home = fixture.title("Home", "county", 10);
    faction.addTitle(first);
    faction.addTitle(second);
    faction.addTitle(home);
    titles.titleTypeView(null, player, subject, first.getTier(), true, 0);
    Inventory inventory = top();
    titles.click(fixture.ui.click(player, 0), inventory, player);
    assertTrue(subject.hasTitle(first));
    assertSame(inventory, top());
    assertEquals(second.getId(), id(inventory.getItem(0)));
    assertEquals(Material.GREEN_CONCRETE, inventory.getItem(0).getType());
    assertNull(inventory.getItem(1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"county", "landless"})
  void tiersThatCannotBeFormedNeverAdvertiseFreeCreationOrFailToRender(String id) {
    Tier tier = TierLoader.getByString(id);
    assertFalse(tier.canForm());
    ItemStack item = assertDoesNotThrow(() -> titles.creator.createNewTitleItem(faction, tier));
    assertTrue(lore(item).contains("Unavailable"));
    assertFalse(lore(item).contains("Click to create"));
    assertFalse(lore(item).contains("-1%"));
  }

  @Test
  void titleHandlerLeavesAnUnrelatedInventoryUntouched() {
    Inventory inventory = fixture.ui.inventory(null, 27, "Unrelated");
    inventory.setItem(0, new ItemStack(Material.STONE));
    player.openInventory(inventory);
    InventoryClickEvent event = fixture.ui.click(player, 0);
    titles.click(event, inventory, player);
    assertFalse(event.isCancelled());
    assertSame(inventory, top());
    assertTrue(TitleManager.isFormingTitle.isEmpty());
  }

  @Test
  void creatingAnotherCountyDoesNotRaiseTheSubjectsTierAboveAnEqualCountyLiege() {
    Tier county = configureTier("county", 2, 1);
    faction.addMember("Citizen");
    faction.addProvince(11);
    faction.addTitle(fixture.title("Home County", "county", 10));
    Faction liege = fixture.saved("liege", "Liege");
    liege.addProvince(20);
    liege.addTitle(fixture.title("Liege County", "county", 20));
    fixture.subject(liege, faction);
    assertEquals(2, faction.getTier().getTier());
    assertEquals(2, liege.getTier().getTier());
    assertEquals(List.of(11), faction.getUntitledProvinces());
    titles.titleTypeView(null, player, faction, county, true, 0);
    titles.click(fixture.ui.click(player, 52), top(), player);
    assertSame(county, TitleManager.isFormingTitle.get(player));
    verify(player).sendMessage("§eType the name of the new county §ein chat.");
  }

  private Tier configureTier(String id, int level, int formCost) {
    TierLoader.oList.removeIf(t -> t.getId().equals(id));
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(id + ".tier", level);
    yaml.set(id + ".form-cost", formCost);
    yaml.set(id + ".prestige", 50);
    Tier tier = new Tier(id, yaml.getConfigurationSection(id));
    TierLoader.oList.add(tier);
    return tier;
  }

  private Regiment regiment(
      String id, boolean levy, boolean professional, int slots, double upkeep) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("name", id);
    yaml.set("item.material", "PAPER");
    yaml.set("default-slots", slots);
    yaml.set("upkeep", upkeep);
    yaml.set("expansion-time", 60);
    yaml.set("levy", levy);
    yaml.set("professional", professional);
    Regiment r = new Regiment(id, yaml);
    faction.getMilitary().getRegiments().add(r);
    return r;
  }

  private String id(ItemStack item) {
    return string(item, "id");
  }

  private String string(ItemStack item, String key) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.STRING);
  }

  private void set(ItemStack item, String key, String value) {
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.STRING, value);
    item.setItemMeta(meta);
  }

  private void remove(ItemStack item, String key) {
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer().remove(new NamespacedKey(fixture.ui.plugin, key));
    item.setItemMeta(meta);
  }

  private static String name(ItemStack item) {
    return ChatColor.stripColor(item.getItemMeta().getDisplayName());
  }

  private static String lore(ItemStack item) {
    return item.getItemMeta().getLore() == null
        ? ""
        : String.join(
            "\n", item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList());
  }

  private Inventory top() {
    return player.getOpenInventory().getTopInventory();
  }
}
