package net.tfminecraft.simplefactions.managers;

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
import net.tfminecraft.simplefactions.database.MercenaryCompanyData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.loaders.CompanyUpgradeLoader;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompanyService;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryEligibility;
import net.tfminecraft.simplefactions.mercenary.contract.ContractBook;
import net.tfminecraft.simplefactions.mercenary.contract.ContractHandler;
import net.tfminecraft.simplefactions.mercenary.contract.ContractKind;
import net.tfminecraft.simplefactions.mercenary.contract.ContractStatus;
import net.tfminecraft.simplefactions.mercenary.contract.ContractTerms;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryMarket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.MercenaryInviteRequest;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.utils.DisplayNameGate;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class MercenaryCommandsCoverageTest {
  private FactionDomainFixture fixture;
  private Guild guild;
  private Faction host;
  private Faction hirer;
  private Player captain;
  private Player customer;
  private MercenaryCompany company;
  private Regiment prototype;
  private MercenaryCommandManager commands;
  private Command command;
  private MockedStatic<RestServer> rest;
  private Map<String, Upgrade> previousUpgrades;
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
    save(RequestManager.class, "requests", new java.util.HashMap<>());
    previousUpgrades = new LinkedHashMap<>(CompanyUpgradeLoader.get());
    CompanyUpgradeLoader.get().clear();
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
    PlayerInventory inventory = captain.getInventory();
    when(inventory.getItemInMainHand()).thenAnswer(call -> inventory.getItem(0));
    doAnswer(
            call -> {
              inventory.setItem(0, call.getArgument(0));
              return null;
            })
        .when(inventory)
        .setItemInMainHand(any());
    rest = mockStatic(RestServer.class);
    rest.when(() -> RestServer.getProvince(any(Player.class))).thenReturn(10);
    commands = new MercenaryCommandManager();
    command = mock(Command.class);
    when(command.getName()).thenReturn("company");
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (captain != null) new DisplayNameGate().onPlayerQuit(new PlayerQuitEvent(captain, ""));
      if (rest != null) rest.close();
      if (previousUpgrades != null) {
        CompanyUpgradeLoader.get().clear();
        CompanyUpgradeLoader.get().putAll(previousUpgrades);
      }
      for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    } finally {
      fixture.close();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "draft",
        "expired",
        "missing_expiry",
        "changed_terms",
        "other_company",
        "agreement"
      })
  void offeringRequiresAnUnexpiredUnmodifiedReviewFromTheCurrentCompany(String state) {
    ItemStack book = ContractBook.reviewBook(company, ContractTerms.defaults());
    if (state.equals("draft")) book = ContractBook.draftBook(company);
    if (state.equals("other_company")) {
      Guild other = fixture.guild(host, "other", "Other");
      book = ContractBook.reviewBook(form(other, "Other Blades"), ContractTerms.defaults());
    }
    if (state.equals("agreement")) {
      var offer = company.getContractHandler().offer(hirer, ContractTerms.defaults());
      assertTrue(offer.ok(), offer.message());
      book = ContractBook.agreementBook(offer.contract());
      company.getContractHandler().remove(offer.contract().getId());
    }
    BookMeta meta = (BookMeta) book.getItemMeta();
    if (state.equals("missing_expiry")) meta.getPersistentDataContainer().remove(Keys.LONG);
    if (state.equals("expired"))
      meta.getPersistentDataContainer()
          .set(Keys.LONG, PersistentDataType.LONG, System.currentTimeMillis() - 1000);
    if (state.equals("changed_terms"))
      meta.setPage(2, ContractBook.termsPage(company, new ContractTerms(1, 60, 20, 7, 60, 500)));
    book.setItemMeta(meta);
    captain.getInventory().setItem(0, book);
    assertTrue(commands.onCommand(captain, command, "company", new String[] {"offer", "hirer"}));
    assertTrue(
        company.getContractHandler().getAll().isEmpty(),
        "Rejected books must not reserve slots or publish an offer");
    assertSame(book, captain.getInventory().getItem(0));
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void fullInventoryDoesNotLoseTheRequestedDraft() {
    for (int slot = 0; slot < 41; slot++)
      captain.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
    assertTrue(commands.onCommand(captain, command, "company", new String[] {"draft"}));
    ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
    verify(fixture.ui.world).dropItemNaturally(eq(captain.getLocation()), dropped.capture());
    assertEquals(
        ContractBook.STAGE_DRAFT, ContractBook.stage((BookMeta) dropped.getValue().getItemMeta()));
    assertEquals(
        guild.getId(), ContractBook.companyGuildId((BookMeta) dropped.getValue().getItemMeta()));
    for (int slot = 0; slot < 41; slot++)
      assertEquals(64, captain.getInventory().getItem(slot).getAmount());
  }

  @Test
  void commandSelectionAndPlayerGateDoNotAffectCompanies() {
    CommandSender console = mock(CommandSender.class);
    when(command.getName()).thenReturn("unrelated");
    assertFalse(commands.onCommand(console, command, "other", new String[0]));
    verifyNoInteractions(console);
    when(command.getName()).thenReturn("company");
    assertTrue(commands.onCommand(console, command, "company", new String[0]));
    verify(console).sendMessage(contains("only be used by players"));
    assertEquals(1, company.getSlots());
    assertTrue(company.getContractHandler().getAll().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "unknown"})
  void unknownOrEmptySubcommandListsTheSupportedActions(String subcommand) {
    run(captain, subcommand.isEmpty() ? new String[0] : new String[] {subcommand});
    verify(captain).sendMessage(contains("/company found <name>"));
    verify(captain).sendMessage(contains("/company draft"));
    verify(captain).sendMessage(contains("/mercenaries hire <company>"));
    assertEquals(1000, guild.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(strings = {"found", "invite", "kick", "offer"})
  void missingRequiredArgumentsDoNotMutateCompanyState(String subcommand) {
    run(captain, subcommand);
    verify(captain).sendMessage(contains("Usage: /company " + subcommand));
    assertEquals(List.of("Captain"), company.getEnlisted());
    assertTrue(company.getContractHandler().getAll().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"valid", "name_warning", "poor", "outsider", "nonleader", "already_formed"})
  void foundingChecksTheNameAndCurrentGuildBeforeRequestingFinancialConfirmation(String state) {
    if (!state.equals("already_formed")) guild.setCompany(null);
    if (state.equals("poor")) guild.getBank().setWealth(50.0);
    Player actor = state.equals("outsider") ? fixture.player("Outsider") : captain;
    if (state.equals("nonleader")) {
      guild.addMember("Successor");
      guild.setLeader("Successor");
    }
    String name = state.equals("name_warning") ? "hired blades" : "Hired Blades";
    double balance = guild.getBank().getWealth();
    run(actor, "found", name);
    if (state.equals("valid")) {
      verify(fixture.inventory).confirmCompanyFoundView(captain, guild, name);
    } else {
      verify(fixture.inventory, never()).confirmCompanyFoundView(any(), any(), anyString());
      verify(actor, atLeastOnce()).sendMessage(anyString());
    }
    assertEquals(
        balance, guild.getBank().getWealth(), "Only the later confirmation may spend money");
    if (!state.equals("already_formed")) assertNull(guild.getCompany());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void invitedPlayersCanAcceptOrDeclineTheirOwnRequest(boolean accepts) {
    assertTrue(company.adminAdjustSlots(1).ok());
    Player recruit = fixture.player("Recruit");
    run(captain, "invite", "Recruit");
    assertTrue(RequestManager.hasRequest(recruit));
    assertFalse(company.isEnlisted("Recruit"));
    run(recruit, accepts ? "accept" : "decline");
    assertFalse(RequestManager.hasRequest(recruit));
    assertEquals(accepts, company.isEnlisted("Recruit"));
    assertEquals(accepts ? 2 : 1, company.getEnlisted().size());
    assertEquals(1000, guild.getBank().getWealth());
  }

  @Test
  void noInviteAndOfflineInviteRefusalsKeepTheRosterUnchanged() {
    Player recruit = fixture.player("Recruit");
    run(recruit, "accept");
    run(recruit, "decline");
    verify(recruit).sendMessage(contains("no company invite to accept"));
    verify(recruit).sendMessage(contains("no company invite to decline"));
    assertTrue(company.adminAdjustSlots(1).ok());
    run(captain, "invite", "Offline");
    verify(captain).sendMessage(contains("Could not find the player Offline"));
    assertEquals(List.of("Captain"), company.getEnlisted());
  }

  @Test
  void expandingAndDismissingUseTheRealCurrentCompanyRules() {
    run(captain, "expand");
    assertEquals(1, company.getSlotQueue().size());
    company.tick();
    company.tick();
    assertEquals(2, company.getSlots());
    assertTrue(company.enlist("Recruit"));
    run(captain, "kick", "Recruit");
    assertFalse(company.isEnlisted("Recruit"));
    assertEquals(2, company.getSlots());
    assertTrue(company.getSlotQueue().isEmpty());
    assertEquals(1000, guild.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(strings = {"draft", "offer", "contracts", "expand", "kick"})
  void outsiderCommandsCannotOperateAnotherGuildsCompany(String subcommand) {
    Player outsider = fixture.player("Outsider");
    run(outsider, subcommand, "hirer");
    verify(outsider, atLeastOnce()).sendMessage(anyString());
    assertEquals(List.of("Captain"), company.getEnlisted());
    assertTrue(company.getSlotQueue().isEmpty());
    assertTrue(company.getContractHandler().getAll().isEmpty());
    verify(fixture.inventory, never()).contractListView(any(), any());
  }

  @Test
  void aCompanyMemberCanReadContractsButCannotDraftOrOfferOnTheLeadersBehalf() {
    guild.addMember("Member");
    Player member = fixture.player("Member");
    run(member, "contracts");
    verify(fixture.inventory).contractListView(member, guild);
    run(member, "draft");
    run(member, "offer", "hirer");
    verify(member).sendMessage(contains("Only a company leader may draft"));
    verify(member).sendMessage(contains("Only a company leader may offer"));
    assertTrue(company.getContractHandler().getAll().isEmpty());
  }

  @Test
  void draftCommandCreatesExactlyOneCompanyBoundBookInAvailableInventorySpace() {
    run(captain, "draft");
    BookMeta draft = (BookMeta) captain.getInventory().getItem(0).getItemMeta();
    assertEquals(ContractBook.STAGE_DRAFT, ContractBook.stage(draft));
    assertEquals(guild.getId(), ContractBook.companyGuildId(draft));
    assertNotNull(ContractBook.parseTerms(draft));
    assertNull(captain.getInventory().getItem(1));
    verify(fixture.ui.world, never()).dropItemNaturally(any(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "ordinary",
        "unmarked_book",
        "unreadable",
        "missing_faction",
        "invalid_terms",
        "valid_id",
        "valid_display_name"
      })
  void offeringValidatesTheHeldBookAndOnlyReplacesItWhenAnOfferIsPublished(String state) {
    ItemStack held = null;
    if (state.equals("ordinary")) held = new ItemStack(Material.STONE);
    else if (state.equals("unmarked_book")) held = new ItemStack(Material.WRITABLE_BOOK);
    else if (!state.equals("null")) {
      ContractTerms terms =
          state.equals("invalid_terms")
              ? new ContractTerms(1, 1, 1, 7, 1, 500)
              : ContractTerms.defaults();
      held = ContractBook.reviewBook(company, terms);
      if (state.equals("unreadable")) {
        BookMeta meta = (BookMeta) held.getItemMeta();
        meta.setPages(List.of("Instructions only"));
        held.setItemMeta(meta);
      }
    }
    captain.getInventory().setItem(0, held);
    hirer.setName("Eastern Realm");
    String target =
        state.equals("missing_faction")
            ? "missing"
            : state.equals("valid_display_name") ? "Eastern Realm" : "hirer";
    run(captain, "offer", target);
    if (state.startsWith("valid_")) {
      var all = company.getContractHandler().getAll();
      assertEquals(1, all.size());
      assertSame(hirer, all.getFirst().getHirer());
      assertEquals(ContractTerms.defaults(), all.getFirst().getTerms());
      BookMeta agreement = (BookMeta) captain.getInventory().getItem(0).getItemMeta();
      assertEquals(ContractBook.STAGE_AGREEMENT, ContractBook.stage(agreement));
      assertEquals(all.getFirst().getId(), ContractBook.contractId(agreement));
      assertTrue(ContractBook.matchesSnapshot(agreement));
    } else {
      assertTrue(company.getContractHandler().getAll().isEmpty());
      assertSame(held, captain.getInventory().getItem(0));
      verify(captain, atLeastOnce()).sendMessage(anyString());
    }
    assertEquals(1000, guild.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "denied",
        "missing",
        "verb",
        "unknown_company",
        "not_number",
        "zero",
        "negative",
        "default",
        "give",
        "take",
        "by_id"
      })
  void administrativeSlotChangesValidatePermissionTargetAndPositiveAmount(String state) {
    if (!state.equals("denied"))
      when(captain.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
    assertTrue(company.adminAdjustSlots(4).ok());
    String[] args =
        switch (state) {
          case "missing" -> new String[] {"admin"};
          case "verb" -> new String[] {"admin", "other", "Hired Blades"};
          case "unknown_company" -> new String[] {"admin", "give", "missing"};
          case "not_number" -> new String[] {"admin", "give", "Hired Blades", "oops"};
          case "zero" -> new String[] {"admin", "give", "Hired Blades", "0"};
          case "negative" -> new String[] {"admin", "give", "Hired Blades", "-1"};
          case "take" -> new String[] {"admin", "take", "Hired Blades", "2"};
          case "give" -> new String[] {"admin", "give", "Hired Blades", "2"};
          case "by_id" -> new String[] {"admin", "give", guild.getId()};
          default -> new String[] {"admin", "give", "Hired Blades"};
        };
    run(captain, args);
    int expected =
        switch (state) {
          case "take" -> 3;
          case "give" -> 7;
          case "default", "by_id" -> 6;
          default -> 5;
        };
    assertEquals(expected, company.getSlots());
    assertEquals(List.of("Captain"), company.getEnlisted());
    assertEquals(1000, guild.getBank().getWealth());
    verify(captain, atLeastOnce()).sendMessage(anyString());
  }

  @Test
  void marketCommandOpensTheHallListsAvailabilityAndChecksCompanyLookup() {
    when(command.getName()).thenReturn("mercenaries");
    run(customer);
    verify(fixture.inventory).mercenaryMarketList(customer);
    run(customer, "list");
    verify(customer).sendMessage(contains("Companies for hire"));
    verify(customer).sendMessage(contains("Hired Blades"));
    run(customer, "hire", "Hired", "Blades");
    verify(customer).sendMessage(contains("You may hire Hired Blades"));
    run(customer, "hire", "Missing");
    verify(customer).sendMessage(contains("No company by that name is for hire"));
    guild.setCompany(null);
    run(customer, "list");
    verify(customer).sendMessage(contains("No mercenary companies are for hire"));
  }

  @ParameterizedTest
  @ValueSource(ints = {-2, 0, 99, 10})
  void hiringCheckUsesTheCurrentProvinceAndNeverCreatesAContract(int province) {
    when(command.getName()).thenReturn("mercenaries");
    rest.when(() -> RestServer.getProvince(customer)).thenReturn(province);
    run(customer, "hire", "Hired Blades");
    verify(customer).sendMessage(startsWith(province == 10 ? "§a" : "§c"));
    assertTrue(company.getContractHandler().getAll().isEmpty());
    assertEquals(1000, guild.getBank().getWealth());
    assertSame(company, MercenaryMarket.byName("hired_blades"));
    assertNull(MercenaryMarket.byName(null));
  }

  @Test
  void minimumIntegerAdministrativeRemovalIsRejectedWithoutChangingCapacity() {
    var result = company.adminAdjustSlots(Integer.MIN_VALUE);
    assertFalse(result.ok(), "An overflowing removal is not a successful administrative action");
    assertEquals(1, company.getSlots());
    assertEquals(List.of("Captain"), company.getEnlisted());
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pendingInvitesCannotJoinACompanyThatWasDisbandedOrReplaced(boolean disbanded) {
    assertTrue(company.adminAdjustSlots(1).ok());
    Player recruit = fixture.player("Recruit");
    run(captain, "invite", "Recruit");
    assertTrue(RequestManager.hasRequest(recruit));
    if (disbanded) company.disband();
    else form(guild, "Replacement Blades");
    run(recruit, "accept");
    assertFalse(RequestManager.hasRequest(recruit));
    assertFalse(
        company.isEnlisted("Recruit"), "An obsolete invite must not enroll into an orphan company");
    if (!disbanded) assertFalse(guild.getCompany().isEnlisted("Recruit"));
    verify(recruit, atLeastOnce()).sendMessage(startsWith("§c"));
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void anExistingCompanyInviteMustNotBeReportedAsANewSuccessfulInvitation() {
    assertTrue(company.adminAdjustSlots(1).ok());
    Player recruit = fixture.player("Recruit");
    Guild rivalGuild = fixture.guild(host, "rival", "Rival");
    MercenaryCompany rival = form(rivalGuild, "Rival Blades");
    MercenaryInviteRequest original = new MercenaryInviteRequest(rival);
    RequestManager.addRequest(captain, recruit, original);
    run(captain, "invite", "Recruit");
    assertSame(original, RequestManager.getRequest(recruit));
    verify(captain).sendMessage(contains("already considering another request"));
    verify(captain, never()).sendMessage(contains("Invited Recruit"));
    verify(recruit, never()).sendMessage(contains("offers you a slot"));
    assertEquals(List.of("Captain"), company.getEnlisted());
  }

  @Test
  void anUnrelatedPendingRequestBlocksAnInviteWithoutReplacingTheRequest() {
    assertTrue(company.adminAdjustSlots(1).ok());
    Player recruit = fixture.player("Recruit");
    Request original = new Request(guild);
    RequestManager.addRequest(captain, recruit, original);
    run(captain, "invite", "Recruit");
    assertSame(original, RequestManager.getRequest(recruit));
    verify(captain).sendMessage(contains("already considering another request"));
    verify(recruit, never()).sendMessage(contains("offers you a slot"));
  }

  @Test
  void anInviteIsRefusedWhenTheLastSlotWasTakenBeforeAcceptance() {
    assertTrue(company.adminAdjustSlots(1).ok());
    Player recruit = fixture.player("Recruit");
    run(captain, "invite", "Recruit");
    assertTrue(company.enlist("Earlier Recruit"));
    run(recruit, "accept");
    assertFalse(RequestManager.hasRequest(recruit));
    assertEquals(List.of("Captain", "Earlier Recruit"), company.getEnlisted());
    verify(recruit).sendMessage(contains("no free slot"));
  }

  @Test
  void fullAndStillFormingCompaniesCannotInvite() {
    Player recruit = fixture.player("Recruit");
    run(captain, "invite", "Recruit");
    verify(captain).sendMessage(contains("Every slot is already filled"));
    company = new MercenaryCompany(guild, "New Blades", new Regiment(prototype), 2);
    guild.setCompany(company);
    run(captain, "invite", "Recruit");
    verify(captain).sendMessage(contains("still being founded"));
    assertFalse(RequestManager.hasRequest(recruit));
    assertTrue(company.getEnlisted().isEmpty());
    assertFalse(MercenaryCompanyService.removeSlot(guild, "Captain").ok());
    assertEquals(0, company.getSlots());
  }

  @Test
  void missingBankAndMissingCompanyServiceInputsCannotSpendOrMutate() {
    guild.setCompany(null);
    var bank = guild.getBank();
    guild.setBank(null);
    assertFalse(MercenaryCompanyService.canFound(guild, "Captain", "New Blades").ok());
    guild.setBank(bank);
    assertFalse(MercenaryCompanyService.canInvite(guild, "Captain", "Recruit").ok());
    assertFalse(MercenaryCompanyService.canJoin(null, "Recruit").ok());
    assertFalse(MercenaryCompanyService.removeSlot(guild, "Captain").ok());
    assertFalse(MercenaryCompanyService.upgrade(guild, "Captain", "missing").ok());
    assertEquals(1000.0, bank.getWealth());
    assertEquals(List.of("Captain"), company.getEnlisted());
  }

  @Test
  void ineligibleRecruitsDoNotConsumeTheFreeSlot() {
    assertTrue(company.adminAdjustSlots(1).ok());
    MercenaryEligibility.setProbe(player -> MercenaryEligibility.Status.INELIGIBLE);
    assertFalse(MercenaryCompanyService.join(company, "Recruit").ok());
    assertEquals(List.of("Captain"), company.getEnlisted());
    assertTrue(company.hasFreeSlot());
  }

  @Test
  void upgradeServiceRejectsFormerLeadersUnknownMaxedAndFullQueues() {
    Upgrade upgrade = companyUpgrade("company_health", 2);
    CompanyUpgradeLoader.get().put(upgrade.getId(), upgrade);
    company = form(guild, "Hired Blades");
    assertFalse(MercenaryCompanyService.upgrade(guild, "Former Captain", upgrade.getId()).ok());
    assertFalse(MercenaryCompanyService.upgrade(guild, "Captain", "missing").ok());
    Upgrade owned = company.getUpgrade(upgrade.getId());
    owned.setLevel(2);
    assertFalse(MercenaryCompanyService.upgrade(guild, "Captain", upgrade.getId()).ok());
    owned.setLevel(0);
    for (int i = 0; i < 3; i++) assertTrue(company.enqueueUpgrade(owned));
    assertFalse(MercenaryCompanyService.upgrade(guild, "Captain", upgrade.getId()).ok());
    assertEquals(3, company.getUpgradeQueue().size());
    assertEquals(0, owned.getLevel());
    assertFalse(company.cancelUpgradeQueue(-1));
    assertFalse(company.cancelUpgradeQueue(3));
    assertTrue(company.cancelUpgradeQueue(1));
    assertEquals(2, company.getUpgradeQueue().size());
    assertFalse(company.cancelSlotQueue(0));
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void companiesWithoutAConfiguredRegimentCannotGainOrLoseSlots() {
    MercenaryCompany unconfigured = new MercenaryCompany(guild, "Unconfigured", null, 0);
    assertFalse(unconfigured.canExpand().ok());
    assertFalse(unconfigured.adminAdjustSlots(1).ok());
    assertFalse(unconfigured.dropSlot());
    assertEquals(0, unconfigured.getSlots());
    assertNull(unconfigured.serialize().slots);
    assertTrue(unconfigured.getEnlisted().isEmpty());
  }

  @Test
  void corruptPersistedSlotNumbersDoNotDiscardValidCompanyIdentityAndQueues() {
    company.setName("Renamed Blades");
    List<String> banner = new ArrayList<>(List.of("red.base"));
    company.setBannerPatterns(banner);
    banner.clear();
    assertEquals(List.of("red.base"), company.getBannerPatterns());
    MercenaryCompanyData data = company.serialize();
    data.slots = "mercenary.invalid";
    data.slotQueue = new ArrayList<>(List.of("mercenary.invalid", "mercenary.2"));
    MercenaryCompany loaded = new MercenaryCompany(guild, data, new Regiment(prototype));
    assertEquals("Renamed Blades", loaded.getName());
    assertEquals(0, loaded.getSlots());
    assertEquals(List.of("red.base"), loaded.getBannerPatterns());
    assertEquals(1, loaded.getSlotQueue().size());
    assertEquals(2, loaded.getSlotQueue().getFirst().getTimeLeft());
    loaded.setBannerPatterns(null);
    assertTrue(loaded.getBannerPatterns().isEmpty());
    assertFalse(company.kick("Missing Recruit"));
    assertEquals(List.of("Captain"), company.getEnlisted());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void characterChangesDismissMembersOrDisbandAndNotifyOnlinePlayers(boolean leaderChanged) {
    assertTrue(company.adminAdjustSlots(1).ok());
    assertTrue(company.enlist("Recruit"));
    Player recruit = fixture.player("Recruit");
    MercenaryEligibility.setProbe(
        player ->
            player.equals(leaderChanged ? "Captain" : "Recruit")
                ? MercenaryEligibility.Status.INELIGIBLE
                : MercenaryEligibility.Status.ELIGIBLE);
    company.tick();
    assertFalse(company.isEnlisted("Recruit"));
    if (leaderChanged) {
      assertNull(guild.getCompany());
      assertTrue(company.getEnlisted().isEmpty());
      verify(captain).sendMessage(contains("disbanded because"));
      verify(recruit).sendMessage(contains("has disbanded"));
    } else {
      assertSame(company, guild.getCompany());
      assertEquals(List.of("Captain"), company.getEnlisted());
      verify(recruit).sendMessage(contains("dismissed from"));
      verify(captain).sendMessage(contains("Recruit was dismissed"));
    }
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void activeContractIncomeAndPayrollAreProjectedWithoutMovingMoney() {
    var contract = activeContract();
    company.getWageSettings().setPeacetimePerDay(4.0);
    company.getWageSettings().setActivePercent(20.0);
    assertEquals(10.0, company.getContractIncome());
    assertEquals(6.0, company.getWageUpkeep());
    assertEquals(14.0, company.getDailyBurn());
    assertEquals(-4.0, company.getNetPosition());
    assertEquals(60.0, contract.getTerms().maxDailyCostPerSlot());
    assertEquals(1000.0, guild.getBank().getWealth());
    assertEquals(0.0, contract.getAccruedToCompany());
    assertTrue(company.getPendingWages().isEmpty());
  }

  @Test
  void companyTickExpiresOldOffersAndRefreshesTheContractScreen() {
    long now = System.currentTimeMillis();
    MercenaryContract old =
        new MercenaryContract(
            company,
            hirer,
            ContractKind.MERCENARY,
            ContractTerms.defaults(),
            now - MercenaryContract.OFFER_WINDOW_MS - 1000);
    company.getContractHandler().add(old);
    company.tick();
    assertEquals(ContractStatus.TERMINATED, old.getStatus());
    assertTrue(company.getContractHandler().getReserving().isEmpty());
    verify(fixture.inventory.getUpdater())
        .inventorySound(
            "minecraft:block.note_block.chime",
            net.tfminecraft.simplefactions.enums.SFGUI.CONTRACT_LIST_VIEW);
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void missingContractAndUnreadableOfferOperationsPreserveTheLedger() {
    ContractHandler handler = company.getContractHandler();
    assertSame(company, handler.getCompany());
    handler.add(null);
    assertNull(handler.getById("missing"));
    assertFalse(handler.offer(null, ContractTerms.defaults()).ok());
    assertFalse(handler.offer(hirer, null).ok());
    long now = System.currentTimeMillis();
    assertFalse(handler.decline("missing").ok());
    assertFalse(handler.proposeSlots("missing", "Captain", 2, now).ok());
    assertFalse(handler.withdrawSlots("missing", "Captain", now).ok());
    assertFalse(handler.acceptSlots("missing", hirer, "Hirer", now).ok());
    assertFalse(handler.declineSlots("missing", hirer, "Hirer", now).ok());
    assertTrue(handler.getAll().isEmpty());
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ended",
        "no_capacity",
        "withdraw_foreign",
        "withdraw_absent",
        "withdraw_expired",
        "accept_inactive",
        "accept_foreign",
        "accept_absent",
        "accept_expired",
        "decline_foreign",
        "decline_absent",
        "decline_expired"
      })
  void slotAmendmentDenialsPreserveTheSignedTermsAndClearOnlyExpiredProposals(String state) {
    if (!state.equals("no_capacity")) assertTrue(company.adminAdjustSlots(1).ok());
    MercenaryContract contract = activeContract();
    ContractTerms original = contract.getTerms();
    ContractHandler handler = company.getContractHandler();
    long now = System.currentTimeMillis();
    boolean pending = state.endsWith("foreign") || state.endsWith("expired");
    if (pending) assertTrue(handler.proposeSlots(contract.getId(), "Captain", 2, now).ok());
    if (state.endsWith("expired")) now = contract.getPendingExpiry();
    if (state.equals("accept_inactive")) contract.finish(ContractStatus.COMPLETED);
    MercenaryResult result =
        switch (state) {
          case "ended" ->
              handler.proposeSlots(contract.getId(), "Captain", 2, contract.getDueDate());
          case "no_capacity" -> handler.proposeSlots(contract.getId(), "Captain", 2, now);
          case "withdraw_foreign" -> handler.withdrawSlots(contract.getId(), "Outsider", now);
          case "withdraw_absent", "withdraw_expired" ->
              handler.withdrawSlots(contract.getId(), "Captain", now);
          case "accept_foreign" -> handler.acceptSlots(contract.getId(), host, "Host", now);
          case "accept_inactive", "accept_absent", "accept_expired" ->
              handler.acceptSlots(contract.getId(), hirer, "Hirer", now);
          case "decline_foreign" -> handler.declineSlots(contract.getId(), host, "Host", now);
          default -> handler.declineSlots(contract.getId(), hirer, "Hirer", now);
        };
    assertFalse(result.ok(), state);
    assertEquals(original, contract.getTerms());
    assertEquals(pending && !state.endsWith("expired"), contract.hasPendingSlots(now));
    if (state.endsWith("expired")) assertEquals(0, contract.getPendingExpiry());
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void amendmentNotificationsReachOnlyTheHiringGovernmentAndFrozenIncreasesStayPending() {
    assertTrue(company.adminAdjustSlots(1).ok());
    MercenaryContract contract = activeContract();
    Player outsider = fixture.player("Outsider");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> List.of(captain, customer, outsider));
    long now = System.currentTimeMillis();
    assertTrue(company.getContractHandler().proposeSlots(contract.getId(), "Captain", 2, now).ok());
    verify(customer).sendMessage(contains("offered to change your contract"));
    verify(captain, never()).sendMessage(contains("offered to change your contract"));
    verify(outsider, never()).sendMessage(anyString());
    Faction enemy = fixture.saved("enemy", "Enemy");
    War frozen = new War(hirer, enemy);
    frozen.setPreparationFrozenUntil(Instant.ofEpochMilli(now + 3600000));
    WarManager.get().add(frozen);
    assertFalse(
        company.getContractHandler().acceptSlots(contract.getId(), hirer, "Hirer", now).ok());
    assertEquals(1, contract.getSlots());
    assertEquals(2, contract.getPendingSlots(now));
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @Test
  void restoredOvercommittedAmendmentIsDroppedWithoutChangingSignedCapacity() {
    assertTrue(company.adminAdjustSlots(1).ok());
    MercenaryContract contract = activeContract();
    long now = System.currentTimeMillis();
    assertTrue(company.getContractHandler().proposeSlots(contract.getId(), "Captain", 2, now).ok());
    MercenaryCompanyData data = company.serialize();
    data.slots = "mercenary.1";
    MercenaryCompany restored = new MercenaryCompany(guild, data, new Regiment(prototype));
    guild.setCompany(restored);
    MercenaryContract saved = restored.getContractHandler().getById(contract.getId());
    assertFalse(restored.getContractHandler().acceptSlots(saved.getId(), hirer, "Hirer", now).ok());
    assertEquals(1, saved.getSlots());
    assertFalse(saved.hasPendingSlots(now));
    assertEquals(ContractStatus.ACTIVE, saved.getStatus());
    verify(captain).sendMessage(contains("company no longer has room"));
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aWarStartingAfterTheOfferRechecksHostAndExistingContractLoyalty(boolean hostOpposes) {
    assertTrue(company.adminAdjustSlots(1).ok());
    Faction opponent = hostOpposes ? host : fixture.saved("opponent", "Opponent");
    if (!hostOpposes) {
      var other = company.getContractHandler().offer(opponent, ContractTerms.defaults());
      assertTrue(other.ok(), other.message());
      assertTrue(other.contract().activate());
    }
    var offer = company.getContractHandler().offer(hirer, ContractTerms.defaults());
    assertTrue(offer.ok(), offer.message());
    WarManager.get().add(new War(hirer, opponent));
    var result = company.getContractHandler().accept(offer.contract().getId(), hirer, "Hirer");
    assertFalse(result.ok());
    assertEquals(ContractStatus.TERMINATED, offer.contract().getStatus());
    assertEquals(hostOpposes ? 0 : 1, company.getContractHandler().getActive().size());
    assertEquals(1000.0, guild.getBank().getWealth());
  }

  private MercenaryContract activeContract() {
    var offer = company.getContractHandler().offer(hirer, ContractTerms.defaults());
    assertTrue(offer.ok(), offer.message());
    assertTrue(company.getContractHandler().accept(offer.contract().getId(), hirer, "Hirer").ok());
    return offer.contract();
  }

  private Upgrade companyUpgrade(String id, int maximum) {
    YamlConfiguration config = new YamlConfiguration();
    config.set(id + ".name", id);
    config.set(id + ".icon", "writable_book.0");
    config.set(id + ".max-level", maximum);
    config.set(id + ".expansion-time", 2);
    return new Upgrade(id, config.getConfigurationSection(id));
  }

  private void run(Player player, String... args) {
    assertTrue(commands.onCommand(player, command, command.getName(), args));
  }

  private MercenaryCompany form(Guild owner, String name) {
    MercenaryCompany created = new MercenaryCompany(owner, name, new Regiment(prototype), 0);
    owner.setCompany(created);
    created.enlistLeader();
    return created;
  }

  private void save(Class<?> type, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    globals.put(field, field.get(null));
    field.set(null, value);
  }
}
