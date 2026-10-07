package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.SpecialPositionAssignment;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.loaders.CompanyUpgradeLoader;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryEligibility;
import net.tfminecraft.simplefactions.mercenary.contract.ContractBook;
import net.tfminecraft.simplefactions.mercenary.contract.ContractStatus;
import net.tfminecraft.simplefactions.mercenary.contract.ContractTerms;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryMarket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class MercenaryMenusCoverageTest {
  private FactionDomainFixture fixture;
  private Guild guild;
  private Faction host;
  private Faction hirer;
  private Player captain;
  private Player customer;
  private MercenaryCompany company;
  private Regiment prototype;
  private InventoryManager navigation;
  private CompanyView companies;
  private ContractView contracts;
  private MercenaryMarketView market;
  private MockedStatic<RestServer> rest;
  private MockedStatic<TLibs> tlibs;
  private Map<String, Upgrade> previousUpgrades;
  private Map<Player, Integer> previousPages;
  private final Map<Field, Object> globals = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    save(Cache.class, "baseYear", "322 AE");
    save(Cache.class, "baseIrlYear", 2026);
    save(Cache.class, "mercenaryFormationCost", 100.0);
    save(Cache.class, "mercenaryFormationSeconds", 2);
    save(Cache.class, "mercenarySlotUpkeep", 8.0);
    save(Cache.class, "mercenaryMinPricePerBattle", 50.0);
    save(Cache.class, "mercenaryMinPricePerDay", 10.0);
    save(Cache.class, "mercenaryMaxContractDays", 14);
    save(Cache.class, "mercenaryDefaultBreachRefund", 500.0);
    save(MercenaryEligibility.class, "probe", MercenaryEligibility.Probe.OPEN);
    previousUpgrades = new LinkedHashMap<>(CompanyUpgradeLoader.get());
    CompanyUpgradeLoader.get().clear();
    upgrade("company_health", 2);
    upgrade("company_mana", 3);
    upgrade("company_unlimited", 0);
    previousPages = new LinkedHashMap<>(MercenaryMarketView.currentPage);
    MercenaryMarketView.currentPage.clear();
    host = fixture.saved("host", "Host");
    host.addProvince(10);
    guild = fixture.guild(host, "blades", "Captain");
    guild.setCapital(10, false);
    guild.getBank().setWealth(1000.0);
    hirer = fixture.saved("hirer", "Hirer");
    hirer.getOrCreateMainGuild();
    captain = fixture.player("Captain");
    when(captain.getWorld()).thenReturn(fixture.ui.world);
    customer = fixture.player("Hirer");
    YamlConfiguration config = new YamlConfiguration();
    config.set("mercenary.name", "Mercenary");
    config.set("mercenary.mercenary", true);
    config.set("mercenary.item.material", "IRON_SWORD");
    config.set("mercenary.upkeep", 8.0);
    config.set("mercenary.expansion-time", 2);
    prototype = new Regiment("mercenary", config.getConfigurationSection("mercenary"));
    RegimentLoader.oList.add(prototype);
    company = form(guild, "Hired Blades");
    navigation = mock(InventoryManager.class);
    when(navigation.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.BARRIER));
    companies = new CompanyView(navigation);
    contracts = new ContractView(navigation);
    navigation.contractView = contracts;
    market = new MercenaryMarketView(navigation);
    rest = mockStatic(RestServer.class);
    rest.when(() -> RestServer.getProvince(any(Player.class))).thenReturn(10);
    tlibs = mockStatic(TLibs.class);
    ItemAPI items = mock(ItemAPI.class);
    ItemCreator creator = mock(ItemCreator.class);
    when(items.getCreator()).thenReturn(creator);
    when(creator.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    tlibs.when(TLibs::getItemAPI).thenReturn(items);
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (tlibs != null) tlibs.close();
      if (rest != null) rest.close();
      if (previousPages != null) {
        MercenaryMarketView.currentPage.clear();
        MercenaryMarketView.currentPage.putAll(previousPages);
      }
      if (previousUpgrades != null) {
        CompanyUpgradeLoader.get().clear();
        CompanyUpgradeLoader.get().putAll(previousUpgrades);
      }
      for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    } finally {
      fixture.close();
    }
  }

  @Test
  void aHiringGovernmentMemberCannotAcceptAfterLeavingTheCompanyHall() {
    MercenaryContract contract = offer();
    contracts.detailView(customer, guild, contract.getId());
    Inventory menu = top(customer);
    rest.when(() -> RestServer.getProvince(customer)).thenReturn(99);
    contracts.click(fixture.ui.click(customer, 11), menu, customer);
    assertTrue(contract.isOffered(), "Signing must still happen at the company's hall");
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void refreshingTheMarketAfterCompaniesDisappearReturnsToAValidPage() {
    List<Guild> others = new ArrayList<>();
    for (int n = 0; n < 48; n++) {
      Guild other = fixture.guild(host, "guild_" + n, "Captain_" + n);
      form(other, "Blades " + n);
      others.add(other);
    }
    market.marketList(customer);
    Inventory menu = top(customer);
    assertNotNull(menu.getItem(53));
    market.click(fixture.ui.click(customer, 53), menu, customer);
    assertEquals(1, MercenaryMarketView.currentPage.get(customer));
    others.forEach(g -> g.setCompany(null));
    market.marketList(customer, menu);
    assertEquals(0, MercenaryMarketView.currentPage.get(customer));
    assertNotNull(menu.getItem(0));
    assertNull(menu.getItem(53));
  }

  @Test
  void aDraftFromTheContractMenuIsNotLostWhenTheInventoryIsFull() {
    for (int slot = 0; slot < 41; slot++)
      captain.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
    contracts.listView(captain, guild);
    contracts.click(fixture.ui.click(captain, 4), top(captain), captain);
    ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
    verify(fixture.ui.world).dropItemNaturally(eq(captain.getLocation()), dropped.capture());
    assertEquals(
        ContractBook.STAGE_DRAFT, ContractBook.stage((BookMeta) dropped.getValue().getItemMeta()));
    assertEquals(
        guild.getId(), ContractBook.companyGuildId((BookMeta) dropped.getValue().getItemMeta()));
    assertTrue(company.getContractHandler().getAll().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"unfounded", "forming", "formed"})
  void companyHomeShowsItsActualFormationStageAndKeepsGuildBannerUntouched(String state) {
    if (state.equals("unfounded")) guild.setCompany(null);
    if (state.equals("forming")) {
      company = new MercenaryCompany(guild, "Hired Blades", new Regiment(prototype), 2);
      guild.setCompany(company);
    }
    List<String> banner = new ArrayList<>(guild.getBannerPatterns());
    companies.companyView(captain, guild);
    Inventory menu = top(captain);
    assertEquals(SFGUI.COMPANY_VIEW, ((SFInventoryHolder) menu.getHolder()).getType());
    assertEquals(Material.BARRIER, menu.getItem(26).getType());
    if (state.equals("unfounded")) {
      assertTrue(name(menu.getItem(4)).contains("Found a Mercenary Company"));
      assertTrue(lore(menu.getItem(4)).contains("Cost: 100.00d"));
    } else {
      assertEquals("Hired Blades", name(menu.getItem(4)));
      ItemStack entry = companies.creator.createCompanyEntryItem(guild);
      assertEquals("Hired Blades", name(entry));
      if (state.equals("forming")) {
        assertTrue(lore(menu.getItem(4)).contains("Being founded"));
        assertTrue(lore(entry).contains("Being founded"));
        companies.click(fixture.ui.click(captain, 11), menu, captain);
        assertSame(menu, top(captain));
        company.tick();
        company.tick();
        companies.companyView(captain, guild, menu);
        assertNotNull(menu.getItem(11));
      } else {
        assertTrue(lore(menu.getItem(4)).contains("Slots: 1/1"));
        assertEquals("Slots", name(menu.getItem(11)));
        assertEquals("Roster", name(menu.getItem(13)));
        assertEquals("Company Upgrades", name(menu.getItem(15)));
        assertEquals("Contracts", name(menu.getItem(22)));
        assertTrue(lore(entry).contains("Leader: Captain"));
      }
    }
    assertEquals(banner, guild.getBannerPatterns());
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 13, 15, 22})
  void companyButtonsOpenTheirCorrespondingPublicMenus(int slot) {
    companies.companyView(captain, guild);
    companies.click(fixture.ui.click(captain, slot), top(captain), captain);
    SFGUI expected =
        switch (slot) {
          case 11 -> SFGUI.COMPANY_SLOTS_VIEW;
          case 13 -> SFGUI.COMPANY_ROSTER_VIEW;
          case 15 -> SFGUI.COMPANY_UPGRADE_VIEW;
          default -> SFGUI.CONTRACT_LIST_VIEW;
        };
    assertEquals(expected, ((SFInventoryHolder) top(captain).getHolder()).getType());
    assertEquals(guild.getId(), ((SFInventoryHolder) top(captain).getHolder()).getId());
    assertEquals(Material.BARRIER, top(captain).getItem(53).getType());
  }

  @Test
  void companyFoundingConfirmationQuotesBankAndCostWithoutSpending() {
    ItemStack confirmation = companies.creator.createFoundConfirmItem(guild, "New Company");
    assertEquals("Found New Company?", name(confirmation));
    assertTrue(lore(confirmation).contains("Cost: 100.00d"));
    assertTrue(lore(confirmation).contains("Guild bank: 1000.00d"));
    assertTrue(lore(confirmation).contains("not refunded"));
    assertEquals(1000, guild.getBank().getWealth());
  }

  @Test
  void companyOverviewWarnsWhenRealRetainerCostsExceedTheGuildsIncome() {
    company.getWageSettings().setPeacetimePerDay(1000000.0);
    assertTrue(guild.getLedger().getNetIncome() < 0);
    companies.companyView(captain, guild);
    String summary = lore(top(captain).getItem(4));
    assertTrue(summary.contains("Burn exceeds what this guild earns"));
    assertTrue(summary.contains("voids every contract"));
    assertEquals(1000, guild.getBank().getWealth());
  }

  @Test
  void largeCompanySlotsShowTheLastThirtySixAndCanRemoveAnUnusedSlot() {
    assertTrue(company.adminAdjustSlots(39).ok());
    companies.slotsView(captain, guild);
    Inventory menu = top(captain);
    assertEquals("Slot 5", name(menu.getItem(9)));
    assertEquals("Slot 40", name(menu.getItem(44)));
    assertTrue(lore(menu.getItem(9)).contains("Empty"));
    companies.click(fixture.ui.click(captain, 44), menu, captain);
    assertEquals(39, company.getSlots());
    assertEquals("Slot 39", name(menu.getItem(44)));
    assertEquals(List.of("Captain"), company.getEnlisted());
    assertEquals(1000, guild.getBank().getWealth());
  }

  @Test
  void slotsExpandThroughTheMenuAndFilledSlotsCannotBeRemoved() {
    companies.slotsView(captain, guild);
    Inventory menu = top(captain);
    assertTrue(lore(menu.getItem(9)).contains("Mercenary: Captain"));
    assertEquals(Material.LIME_DYE, menu.getItem(4).getType());
    companies.click(fixture.ui.click(captain, 9), menu, captain);
    assertEquals(1, company.getSlots());
    companies.click(fixture.ui.click(captain, 4), menu, captain);
    assertEquals(1, company.getSlotQueue().size());
    assertEquals(
        QueueCancelPayload.companySlot(guild.getId(), 0),
        data(menu.getItem(45), Keys.QUEUE_CANCEL));
    assertEquals(Material.GRAY_DYE, menu.getItem(4).getType());
    assertTrue(lore(menu.getItem(4)).contains("Fill every slot"));
    company.tick();
    company.tick();
    companies.slotsView(captain, guild, menu);
    assertEquals(2, company.getSlots());
    assertNull(menu.getItem(45));
    assertTrue(lore(menu.getItem(10)).contains("Empty"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void onlyTheCurrentLeaderMayExpandRemoveSlotsOrCancelQueuedSlots(boolean leaderStill) {
    assertTrue(company.adminAdjustSlots(1).ok());
    company.addQueuedExpansion(60);
    companies.slotsView(captain, guild);
    Inventory menu = top(captain);
    if (!leaderStill) {
      guild.addMember("Successor");
      guild.setLeader("Successor");
    }
    companies.click(fixture.ui.click(captain, 45), menu, captain);
    if (leaderStill)
      verify(navigation)
          .openQueueCancelConfirm(
              captain,
              host,
              QueueCancelPayload.companySlot(guild.getId(), 0),
              "§eCancel queued slot expansion?");
    else verify(navigation, never()).openQueueCancelConfirm(any(), any(), anyString(), anyString());
    assertEquals(1, company.getSlotQueue().size());
    companies.click(fixture.ui.click(captain, 10), menu, captain);
    assertEquals(leaderStill ? 1 : 2, company.getSlots());
    if (!leaderStill) {
      companies.click(fixture.ui.click(captain, 4), menu, captain);
      assertEquals(1, company.getSlotQueue().size());
      companies.slotsView(captain, guild, menu);
      assertNull(menu.getItem(4));
    }
  }

  @Test
  void bottomInventorySlotsCannotRemoveCompanyCapacity() {
    assertTrue(company.adminAdjustSlots(1).ok());
    companies.slotsView(captain, guild);
    Inventory menu = top(captain);
    InventoryClickEvent playerSlot = fixture.ui.click(captain, 54 + 10);
    companies.click(playerSlot, menu, captain);
    assertTrue(playerSlot.isCancelled());
    assertEquals(2, company.getSlots());
  }

  @Test
  void restoredQueuesShowFrozenFirstTimersAndQueuedLaterEntries() {
    for (int n = 0; n < 3; n++) {
      company.addQueuedExpansion(60 + n);
      company.addQueuedUpgrade(company.getUpgrade("company_health"), 60 + n);
    }
    War war = new War(1, host, hirer);
    war.setPreparationFrozenUntil(Instant.now().plusSeconds(3600));
    WarManager.get().add(war);
    companies.slotsView(captain, guild);
    assertTrue(lore(top(captain).getItem(45)).contains("Frozen: battle postponed"));
    assertTrue(lore(top(captain).getItem(46)).contains("Queued..."));
    assertNotNull(top(captain).getItem(47));
    companies.companyUpgradeView(captain, guild);
    assertTrue(lore(top(captain).getItem(39)).contains("Frozen: battle postponed"));
    assertTrue(lore(top(captain).getItem(40)).contains("Queued..."));
    assertEquals(
        QueueCancelPayload.companyUpgrade(guild.getId(), 2),
        data(top(captain).getItem(41), Keys.QUEUE_CANCEL));
    assertNull(top(captain).getItem(42));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rosterClickDismissesOnlyForTheCurrentLeaderAndRefreshesTheSlot(boolean currentLeader) {
    assertTrue(company.adminAdjustSlots(1).ok());
    assertTrue(company.enlist("Recruit"));
    companies.rosterView(captain, guild);
    Inventory menu = top(captain);
    assertEquals("Recruit", data(menu.getItem(10), Keys.STRING_KEY));
    assertTrue(lore(menu.getItem(10)).contains("Click to dismiss"));
    if (!currentLeader) {
      guild.addMember("Successor");
      guild.setLeader("Successor");
    }
    companies.click(fixture.ui.click(captain, 10), menu, captain);
    assertEquals(!currentLeader, company.isEnlisted("Recruit"));
    assertEquals(2, company.getSlots());
    if (currentLeader) assertNull(menu.getItem(10));
    else assertFalse(lore(menu.getItem(10)).contains("Click to dismiss"));
  }

  @Test
  void rosterAndUpgradeMenusRespectTheirDisplayCapacity() {
    assertTrue(company.adminAdjustSlots(40).ok());
    for (int n = 0; n < 40; n++) assertTrue(company.enlist("Recruit" + n));
    companies.rosterView(captain, guild);
    assertEquals("Recruit34", data(top(captain).getItem(44), Keys.STRING_KEY));
    assertNull(top(captain).getItem(45));
    for (int n = 0; n < 8; n++) upgrade("additional_" + n, 2);
    company = form(guild, "Hired Blades");
    companies.companyUpgradeView(captain, guild);
    for (int slot = 9; slot <= 17; slot++)
      assertNotNull(data(top(captain).getItem(slot), Keys.STRING_KEY));
    assertNull(top(captain).getItem(18));
    assertEquals(Material.BARRIER, top(captain).getItem(53).getType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"order", "cancel", "former_leader", "maxed"})
  void companyUpgradeActionsUseCurrentAuthorityAndTheRealQueue(String action) {
    Upgrade health = company.getUpgrade("company_health");
    if (action.equals("maxed")) health.setLevel(health.getMaxLevel());
    if (action.equals("cancel")) company.addQueuedUpgrade(health, 10);
    companies.companyUpgradeView(captain, guild);
    Inventory menu = top(captain);
    int slot = find(menu, Keys.STRING_KEY, health.getId());
    assertTrue(slot >= 9 && slot <= 17);
    assertTrue(lore(menu.getItem(slot)).contains("Only while fighting as a hired mercenary"));
    assertTrue(lore(menu.getItem(slot)).contains("per level"));
    if (action.equals("former_leader")) {
      guild.addMember("Successor");
      guild.setLeader("Successor");
    }
    companies.click(fixture.ui.click(captain, action.equals("cancel") ? 39 : slot), menu, captain);
    if (action.equals("cancel")) {
      verify(navigation)
          .openQueueCancelConfirm(
              captain,
              host,
              QueueCancelPayload.companyUpgrade(guild.getId(), 0),
              "§eCancel queued company upgrade?");
      assertEquals(1, company.getUpgradeQueue().size());
    } else if (action.equals("order")) {
      assertEquals(1, company.getUpgradeQueue().size());
      assertEquals(0, health.getLevel());
      company.tick();
      company.tick();
      assertEquals(1, health.getLevel());
    } else {
      assertTrue(company.getUpgradeQueue().isEmpty());
      if (action.equals("maxed"))
        assertTrue(lore(menu.getItem(slot)).contains("Maximum level reached"));
    }
    assertEquals(1000, guild.getBank().getWealth());
  }

  @Test
  void spymasterProtectedCompanyOverviewContainsOnlyReportedInformation() {
    SpecialPositionAssignment assignment = new SpecialPositionAssignment();
    assignment.playerName = "Host";
    assignment.playerId = fixture.player("Host").getUniqueId();
    assignment.characterId = "host_character";
    host.getEspionage().appoint(assignment, 50);
    companies.companyView(customer, guild);
    Inventory menu = top(customer);
    assertTrue(((SFInventoryHolder) menu.getHolder()).isReported());
    assertTrue(lore(menu.getItem(4)).contains("Unknown"));
    for (ItemStack item : menu.getContents()) {
      if (item != null && item.hasItemMeta()) {
        assertNull(data(item, Keys.QUEUE_CANCEL));
        assertNull(data(item, Keys.CONTRACT_ID));
      }
    }
    companies.companyView(customer, guild, menu);
    assertSame(menu, top(customer));
  }

  @ParameterizedTest
  @ValueSource(strings = {"slots", "roster", "upgrades", "contracts", "detail"})
  void removedCompaniesClearPreviouslyRenderedMenusWithoutChangingDetachedState(String target) {
    MercenaryContract offered = offer();
    Inventory menu;
    switch (target) {
      case "slots" -> companies.slotsView(captain, guild);
      case "roster" -> companies.rosterView(captain, guild);
      case "upgrades" -> companies.companyUpgradeView(captain, guild);
      case "contracts" -> contracts.listView(captain, guild);
      default -> contracts.detailView(captain, guild, offered.getId());
    }
    menu = top(captain);
    guild.setCompany(null);
    switch (target) {
      case "slots" -> companies.slotsView(captain, guild, menu);
      case "roster" -> companies.rosterView(captain, guild, menu);
      case "upgrades" -> companies.companyUpgradeView(captain, guild, menu);
      case "contracts" -> contracts.listView(captain, guild, menu);
      default -> contracts.detailView(captain, guild, menu, offered.getId());
    }
    for (ItemStack item : menu.getContents()) assertNull(item);
    companies.click(fixture.ui.click(captain, 9), menu, captain);
    contracts.click(fixture.ui.click(captain, 11), menu, captain);
    assertTrue(offered.isOffered());
    assertEquals(List.of("Captain"), company.getEnlisted());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void localHiringGovernmentCanAcceptOrDeclineWithoutAnUpfrontPayment(boolean accepts) {
    MercenaryContract contract = offer();
    contracts.detailView(customer, guild, contract.getId());
    Inventory menu = top(customer);
    assertTrue(name(menu.getItem(11)).contains("Accept the contract"));
    assertTrue(name(menu.getItem(15)).contains("Decline the offer"));
    double wealth = guild.getBank().getWealth() + hirer.getBank().getWealth();
    contracts.click(fixture.ui.click(customer, accepts ? 11 : 15), menu, customer);
    assertEquals(accepts ? ContractStatus.ACTIVE : ContractStatus.TERMINATED, contract.getStatus());
    assertNull(menu.getItem(11));
    assertNull(menu.getItem(15));
    assertEquals(wealth, guild.getBank().getWealth() + hirer.getBank().getWealth());
    assertEquals(accepts ? 0 : 1, MercenaryMarket.availableToday(company));
  }

  @Test
  void nullSignerCannotActivateAContractOrConsumeItsReservation() {
    MercenaryContract contract = offer();
    assertFalse(company.getContractHandler().acceptAtHall(contract.getId(), hirer, null).ok());
    assertTrue(contract.isOffered());
    assertEquals(0, MercenaryMarket.availableToday(company));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void clickingContractInformationDoesNotAcceptAnOfferOrItsPendingAmendment(boolean active) {
    assertTrue(company.adminAdjustSlots(1).ok());
    MercenaryContract contract = offer();
    if (active) {
      assertTrue(company.getContractHandler().acceptAtHall(contract.getId(), hirer, customer).ok());
      assertTrue(
          company
              .getContractHandler()
              .proposeSlots(contract.getId(), "Captain", 2, System.currentTimeMillis())
              .ok());
    }
    contracts.detailView(customer, guild, contract.getId());
    Inventory menu = top(customer);
    contracts.click(fixture.ui.click(customer, 13), menu, customer);
    assertEquals(active ? ContractStatus.ACTIVE : ContractStatus.OFFERED, contract.getStatus());
    assertEquals(1, contract.getSlots());
    assertEquals(active, contract.hasPendingSlots(System.currentTimeMillis()));
    assertSame(menu, top(customer));
  }

  @Test
  void closedCompaniesAndUnaffiliatedViewersCannotSignAtTheMarket() {
    Player outsider = fixture.player("Outsider");
    assertFalse(MercenaryMarket.canSign(company, outsider).ok());
    assertTrue(MercenaryMarket.canSign(company, outsider).message().contains("not in a faction"));
    MercenaryCompany forming =
        new MercenaryCompany(guild, "New Company", new Regiment(prototype), 2);
    assertFalse(MercenaryMarket.canSign(forming, customer).ok());
    assertFalse(MercenaryMarket.canSign(null, customer).ok());
    assertTrue(company.getContractHandler().getAll().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(ContractStatus.class)
  void contractCardsUseStatusMaterialsAndExplainBothPrices(ContractStatus status) {
    MercenaryContract contract = offer();
    if (status != ContractStatus.OFFERED) assertTrue(contract.activate());
    if (status.isFinished()) assertTrue(contract.finish(status));
    contracts.listView(captain, guild);
    ItemStack card = top(captain).getItem(9);
    Material expected =
        switch (status) {
          case OFFERED -> Material.WRITABLE_BOOK;
          case ACTIVE -> Material.WRITTEN_BOOK;
          case COMPLETED -> Material.BOOK;
          case BREACHED -> Material.SHIELD;
          case TERMINATED -> Material.PAPER;
        };
    assertEquals(expected, card.getType());
    assertEquals(contract.getId(), data(card, Keys.CONTRACT_ID));
    assertTrue(lore(card).contains("Status: " + ContractCreator.label(status)));
    assertTrue(lore(card).contains("day price AND the battle price"));
    contracts.click(fixture.ui.click(captain, 9), top(captain), captain);
    Inventory detail = top(captain);
    assertTrue(lore(detail.getItem(13)).contains("Company: Hired Blades"));
    assertTrue(lore(detail.getItem(13)).contains("Per slot per day: 10.00d"));
    assertTrue(lore(detail.getItem(13)).contains("Breach refund: 500.00d"));
    if (status.isFinished()) {
      contracts.click(fixture.ui.click(captain, 11), detail, captain);
      assertEquals(status, contract.getStatus());
    }
  }

  @Test
  void contractListReservesControlRowsAndRefreshesDeletedRecords() {
    assertTrue(company.adminAdjustSlots(39).ok());
    for (int n = 0; n < 37; n++) offer();
    contracts.listView(captain, guild);
    Inventory list = top(captain);
    assertNotNull(data(list.getItem(44), Keys.CONTRACT_ID));
    assertNull(list.getItem(45));
    assertEquals("Draft a contract", name(list.getItem(4)));
    company
        .getContractHandler()
        .getAll()
        .forEach(c -> company.getContractHandler().remove(c.getId()));
    contracts.listView(captain, guild, list);
    assertNull(list.getItem(9));
    assertNull(list.getItem(44));
    assertEquals(Material.BARRIER, list.getItem(53).getType());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"propose", "withdraw", "accept", "decline", "former_captain", "former_hirer"})
  void activeContractSlotChangesRespectBothPartiesAndPreserveSignedPrices(String action) {
    assertTrue(company.adminAdjustSlots(2).ok());
    MercenaryContract contract = offer();
    assertTrue(company.getContractHandler().acceptAtHall(contract.getId(), hirer, customer).ok());
    boolean pending = !action.equals("propose");
    if (pending)
      assertTrue(
          company
              .getContractHandler()
              .proposeSlots(contract.getId(), "Captain", 2, System.currentTimeMillis())
              .ok());
    Player actor =
        List.of("accept", "decline", "former_hirer").contains(action) ? customer : captain;
    contracts.detailView(actor, guild, contract.getId());
    Inventory menu = top(actor);
    long due = contract.getDueDate();
    if (action.equals("former_captain")) {
      guild.addMember("Successor");
      guild.setLeader("Successor");
    }
    if (action.equals("former_hirer")) {
      hirer.addMember("Successor");
      hirer.setLeader("Successor");
    }
    if (pending) {
      assertTrue(lore(menu.getItem(13)).contains("Slot change waiting: 1 → 2"));
      assertTrue(
          lore(contracts.creator.createContractItem(contract)).contains("Slot change waiting: 2"));
    }
    int slot = actor == captain ? 11 : action.equals("decline") ? 16 : 15;
    contracts.click(fixture.ui.click(actor, slot), menu, actor);
    if (action.equals("propose")) verify(navigation).beginSlotChange(captain, guild, contract);
    else if (action.startsWith("former"))
      assertEquals(2, contract.getPendingSlots(System.currentTimeMillis()));
    else assertFalse(contract.hasPendingSlots(System.currentTimeMillis()));
    assertEquals(action.equals("accept") ? 2 : 1, contract.getSlots());
    assertEquals(10, contract.getPricePerSlotPerDay());
    assertEquals(50, contract.getPricePerSlotPerBattle());
    assertEquals(due, contract.getDueDate());
    assertEquals(1000, guild.getBank().getWealth());
  }

  @Test
  void contractMenusIgnoreOutsidersStaleLeadersRemovedContractsAndDecorationClicks() {
    MercenaryContract contract = offer();
    Player outsider = fixture.player("Outsider");
    contracts.detailView(outsider, guild, contract.getId());
    assertNull(top(outsider).getItem(11));
    contracts.click(fixture.ui.click(outsider, 11), top(outsider), outsider);
    assertTrue(contract.isOffered());
    contracts.listView(captain, guild);
    Inventory menu = top(captain);
    guild.addMember("Successor");
    guild.setLeader("Successor");
    contracts.click(fixture.ui.click(captain, 4), menu, captain);
    assertNull(captain.getInventory().getItem(0));
    contracts.click(fixture.ui.click(captain, 0), menu, captain);
    menu.setItem(0, new ItemStack(Material.STONE));
    contracts.click(fixture.ui.click(captain, 0), menu, captain);
    assertSame(menu, top(captain));
    company.getContractHandler().remove(contract.getId());
    contracts.detailView(customer, guild, contract.getId());
    contracts.click(fixture.ui.click(customer, 11), top(customer), customer);
    assertTrue(contract.isOffered());
    assertEquals(1000, guild.getBank().getWealth());
  }

  @Test
  void marketCardsShowRealAvailabilityAndBothNavigationDirectionsWork() {
    List<Guild> others = new ArrayList<>();
    for (int n = 0; n < 48; n++) {
      Guild other = fixture.guild(host, "guild_" + n, "Captain_" + n);
      MercenaryCompany created = form(other, "Blades " + n);
      created.setReputation(n);
      others.add(other);
    }
    company.setReputation(100);
    market.marketList(customer);
    Inventory menu = top(customer);
    assertEquals(guild.getId(), data(menu.getItem(0), Keys.STRING_KEY));
    assertTrue(lore(menu.getItem(0)).contains("Free today: 1"));
    assertTrue(lore(menu.getItem(0)).contains("Home: Province 10"));
    market.click(fixture.ui.click(customer, 0), menu, customer);
    verify(navigation).contractListView(customer, guild);
    market.click(fixture.ui.click(customer, 53), menu, customer);
    assertEquals(1, MercenaryMarketView.currentPage.get(customer));
    assertNotNull(menu.getItem(45));
    assertNull(menu.getItem(53));
    market.click(fixture.ui.click(customer, 45), menu, customer);
    assertEquals(0, MercenaryMarketView.currentPage.get(customer));
    assertEquals(guild.getId(), data(menu.getItem(0), Keys.STRING_KEY));
    assertTrue(
        market.buildListingLore(null, company).stream()
            .noneMatch(line -> line.contains("You may hire")));
  }

  @Test
  void menuDispatchGuardsIgnoreOtherInventoriesMissingGuildsAndUnkeyedItems() {
    Inventory ordinary = fixture.ui.inventory(null, 9, "Other plugin");
    captain.openInventory(ordinary);
    InventoryClickEvent other = fixture.ui.click(captain, 0);
    companies.click(other, ordinary, captain);
    contracts.click(other, ordinary, captain);
    market.click(other, ordinary, captain);
    assertFalse(other.isCancelled());
    companies.companyView(captain, guild);
    market.click(fixture.ui.click(captain, 0), top(captain), captain);
    companies.click(fixture.ui.click(captain, 0), top(captain), captain);
    contracts.click(fixture.ui.click(captain, 0), top(captain), captain);
    companies.rosterView(captain, guild);
    companies.click(fixture.ui.click(captain, 0), top(captain), captain);
    top(captain).setItem(0, new ItemStack(Material.STONE));
    companies.click(fixture.ui.click(captain, 0), top(captain), captain);
    companies.companyUpgradeView(captain, guild);
    companies.click(fixture.ui.click(captain, 0), top(captain), captain);
    market.marketList(captain);
    Inventory listing = top(captain);
    market.click(fixture.ui.click(captain, 8), listing, captain);
    listing.setItem(8, new ItemStack(Material.STONE));
    market.click(fixture.ui.click(captain, 8), listing, captain);
    listing.setItem(8, companies.creator.createSlotsButton(guild));
    market.click(fixture.ui.click(captain, 8), listing, captain);
    FactionManager.factions.remove(host);
    market.click(fixture.ui.click(captain, 0), listing, captain);
    Inventory missing =
        fixture.ui.inventory(new SFInventoryHolder(guild.getId(), SFGUI.COMPANY_VIEW), 27, "Stale");
    captain.openInventory(missing);
    companies.click(fixture.ui.click(captain, 11), missing, captain);
    contracts.click(fixture.ui.click(captain, 11), missing, captain);
    verify(navigation, never()).contractListView(any(), any());
    assertEquals(List.of("Captain"), company.getEnlisted());
  }

  private Upgrade upgrade(String id, int maximum) {
    YamlConfiguration config = new YamlConfiguration();
    config.set(id + ".name", id);
    config.set(id + ".icon", "writable_book.0");
    config.set(id + ".upkeep", 10.0);
    config.set(id + ".expansion-time", 2);
    config.set(id + ".description", List.of("A trained company discipline"));
    config.set(id + ".modifiers", List.of("MAX_HEALTH 0 0.5"));
    if (maximum > 0) config.set(id + ".max-level", maximum);
    Upgrade result = new Upgrade(id, config.getConfigurationSection(id));
    CompanyUpgradeLoader.get().put(id, result);
    return result;
  }

  private int find(Inventory inventory, org.bukkit.NamespacedKey key, String id) {
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack item = inventory.getItem(slot);
      if (item != null && item.hasItemMeta() && id.equals(data(item, key))) return slot;
    }
    return -1;
  }

  private String name(ItemStack item) {
    return ChatColor.stripColor(item.getItemMeta().getDisplayName());
  }

  private String lore(ItemStack item) {
    return ChatColor.stripColor(String.join("\n", item.getItemMeta().getLore()));
  }

  private String data(ItemStack item, org.bukkit.NamespacedKey key) {
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  private MercenaryContract offer() {
    var offered = company.getContractHandler().offer(hirer, ContractTerms.defaults());
    assertTrue(offered.ok(), offered.message());
    return offered.contract();
  }

  private MercenaryCompany form(Guild owner, String name) {
    MercenaryCompany created = new MercenaryCompany(owner, name, new Regiment(prototype), 0);
    owner.setCompany(created);
    created.enlistLeader();
    return created;
  }

  private Inventory top(Player player) {
    return player.getOpenInventory().getTopInventory();
  }

  private void save(Class<?> type, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    globals.put(field, field.get(null));
    field.set(null, value);
  }
}
