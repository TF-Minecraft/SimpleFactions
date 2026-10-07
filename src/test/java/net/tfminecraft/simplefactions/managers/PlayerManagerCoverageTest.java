package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.denareconomy.event.PlayerBankPulseEvent;
import net.tfminecraft.denareconomy.event.PlayerDepositMaterialsEvent;
import net.tfminecraft.denareconomy.event.PlayerEarnMoneyEvent;
import net.tfminecraft.denareconomy.item.Coin;
import net.tfminecraft.denareconomy.managers.MoneyManager;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.loans.LoanBook;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.ContractBook;
import net.tfminecraft.simplefactions.mercenary.contract.ContractTerms;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.player.income.PlayerLedger;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Player event workflows with actual books, guilds and financial handlers. */
class PlayerManagerCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Guild lender;
  private Player leader;
  private String oldBaseYear;
  private int oldBaseIrlYear;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();
  private final PlayerEconomyManager economy = new PlayerEconomyManager();
  private final PlayerManager listener = new PlayerManager();

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    oldBaseYear = Cache.baseYear;
    oldBaseIrlYear = Cache.baseIrlYear;
    Cache.baseYear = "322 AE";
    Cache.baseIrlYear = 2026;
    Chunk origin = fixture.ui.world.getChunkAt(0, 0);
    when(fixture.ui.world.getChunkAt(any(Location.class))).thenReturn(origin);
    faction = fixture.saved("realm", "Leader");
    lender = faction.getOrCreateMainGuild();
    lender.setBank(null);
    leader = player("Leader");
    scoped(net.tfminecraft.simplefactions.rest.RestServer.class)
        .when(() -> net.tfminecraft.simplefactions.rest.RestServer.getProvince(any(Player.class)))
        .thenReturn(10);
    scoped(PlayerEconomyManager.class).when(PlayerEconomyManager::get).thenReturn(economy);
    scoped(OfflineModifier.class)
        .when(() -> OfflineModifier.playerId(anyString()))
        .thenAnswer(
            call ->
                UUID.nameUUIDFromBytes(
                    ((String) call.getArgument(0))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  @AfterEach
  void close() {
    try {
      for (int i = scopes.size() - 1; i >= 0; i--) scopes.get(i).close();
    } finally {
      Cache.baseYear = oldBaseYear;
      Cache.baseIrlYear = oldBaseIrlYear;
      fixture.close();
    }
  }

  @Test
  void delayedLoanReviewReplacesTheSignedBookWithoutOverwritingTheNewlyHeldItem() {
    ItemStack draft = LoanBook.getBaseBook(lender);
    leader.getInventory().setItem(0, draft);
    leader.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 7));
    PlayerEditBookEvent event = sign(leader, draft);
    assertTrue(event.isCancelled());
    leader.getInventory().setHeldItemSlot(1);
    fixture.ui.runTasks();
    assertEquals(Material.DIAMOND, leader.getInventory().getItem(1).getType());
    assertEquals(7, leader.getInventory().getItem(1).getAmount());
    BookMeta review = (BookMeta) leader.getInventory().getItem(0).getItemMeta();
    assertEquals(2, review.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER));
  }

  @Test
  void delayedBookReplacementDoesNotOverwriteAnItemMovedIntoTheSignedSlot() {
    ItemStack draft = LoanBook.getBaseBook(lender);
    leader.getInventory().setItem(0, draft);
    sign(leader, draft);
    leader.getInventory().setItem(0, new ItemStack(Material.EMERALD, 4));
    fixture.ui.runTasks();
    assertEquals(Material.EMERALD, leader.getInventory().getItem(0).getType());
    assertEquals(4, leader.getInventory().getItem(0).getAmount());
  }

  @Test
  void loginPassesThePlayerToTheRealInactivityWorkflow() {
    try (MockedStatic<FactionCleanup> cleanup = mockStatic(FactionCleanup.class)) {
      listener.joinEvent(new PlayerJoinEvent(leader, "joined"));
      cleanup.verify(() -> FactionCleanup.daysOffline("Leader"));
      cleanup.verify(() -> FactionCleanup.ping("Leader"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"not_signing", "ordinary", "missing_issuer", "no_stage", "unknown_stage"})
  void ordinaryOrObsoleteBooksDoNotStartFinancialWorkflows(String kind) {
    ItemStack book = LoanBook.getBaseBook(lender);
    if (kind.equals("ordinary")) book = new ItemStack(Material.WRITABLE_BOOK);
    if (kind.equals("missing_issuer"))
      edit(
          book,
          meta ->
              meta.getPersistentDataContainer()
                  .set(Keys.STRING_KEY, PersistentDataType.STRING, "deleted"));
    if (kind.equals("no_stage"))
      edit(book, meta -> meta.getPersistentDataContainer().remove(Keys.INT));
    if (kind.equals("unknown_stage"))
      edit(
          book,
          meta -> meta.getPersistentDataContainer().set(Keys.INT, PersistentDataType.INTEGER, 99));
    leader.getInventory().setItem(0, book);
    BookMeta meta = (BookMeta) book.getItemMeta();
    PlayerEditBookEvent event =
        new PlayerEditBookEvent(leader, 0, meta, meta, !kind.equals("not_signing"));
    listener.signBook(event);
    assertFalse(event.isCancelled());
    assertTrue(fixture.ui.tasks.isEmpty());
    assertTrue(lender.getLoanHandler().getLoansGiven().isEmpty());
    assertTrue(leader.getInventory().getItem(0).isSimilar(book));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void onlyTheCurrentLenderLeaderCanDraftOrReview(int stage) {
    Player other = player("Other");
    ItemStack book = loanBook(stage, null);
    other.getInventory().setItem(0, book);
    PlayerEditBookEvent event = sign(other, book);
    assertTrue(event.isCancelled());
    assertTrue(fixture.ui.tasks.isEmpty());
    verify(other).sendMessage(contains("Only the leader of"));
    assertTrue(lender.getLoanHandler().getLoansGiven().isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"1,false", "2,false", "1,true", "2,true"})
  void draftingAndReviewingValidateExpiryAndTermsBeforeProducingABook(int stage, boolean expired) {
    ItemStack book = loanBook(stage, null);
    if (expired)
      edit(
          book,
          meta -> meta.getPersistentDataContainer().set(Keys.LONG, PersistentDataType.LONG, 1L));
    else edit(book, meta -> meta.setPage(2, "invalid terms"));
    leader.getInventory().setItem(0, book);
    assertTrue(sign(leader, book).isCancelled());
    fixture.ui.runTasks();
    assertTrue(lender.getLoanHandler().getLoansGiven().isEmpty());
    BookMeta result = (BookMeta) leader.getInventory().getItem(0).getItemMeta();
    if (expired) {
      verify(leader).sendMessage(contains("expired"));
      assertNull(result.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER));
    } else {
      verify(leader).sendMessage(LoanBook.INVALID_TERMS_MESSAGE);
      assertEquals(
          stage, result.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER));
    }
  }

  @Test
  void aLeaderCanReviewAndPublishValidLoanTermsWithoutMovingMoney() {
    lender.setBank(new Bank(lender, 10000, null));
    ItemStack draft = LoanBook.getBaseBook(lender);
    leader.getInventory().setItem(0, draft);
    sign(leader, draft);
    fixture.ui.runTasks();
    ItemStack review = leader.getInventory().getItem(0);
    assertEquals(
        2,
        ((BookMeta) review.getItemMeta())
            .getPersistentDataContainer()
            .get(Keys.INT, PersistentDataType.INTEGER));
    sign(leader, review);
    fixture.ui.runTasks();
    BookMeta offer = (BookMeta) leader.getInventory().getItem(0).getItemMeta();
    assertEquals(3, offer.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER));
    assertNotNull(LoanBook.offerId(offer));
    assertNotNull(
        offer
            .getPersistentDataContainer()
            .get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING));
    assertEquals(10000, lender.getBank().getWealth());
    assertTrue(lender.getLoanHandler().getLoansGiven().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "expired",
        "old_offer",
        "missing_snapshot",
        "invalid_snapshot",
        "invalid_terms",
        "changed_amount",
        "no_borrower",
        "no_lender_bank",
        "no_borrower_bank",
        "insufficient",
        "used_offer"
      })
  void refusedLoanAgreementsNeverMoveMoneyOrCreateDebt(String reason) {
    Guild borrower = borrower();
    Player signer = reason.equals("no_borrower") ? player("Visitor") : player("Borrower");
    ItemStack book = loanBook(3, borrower);
    switch (reason) {
      case "expired" ->
          edit(
              book,
              meta ->
                  meta.getPersistentDataContainer().set(Keys.LONG, PersistentDataType.LONG, 1L));
      case "old_offer" ->
          edit(book, meta -> meta.getPersistentDataContainer().remove(Keys.LOAN_OFFER));
      case "missing_snapshot" ->
          edit(book, meta -> meta.getPersistentDataContainer().remove(Keys.SECONDARY_STRING_KEY));
      case "invalid_snapshot" ->
          edit(
              book,
              meta ->
                  meta.getPersistentDataContainer()
                      .set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, "bad snapshot"));
      case "invalid_terms" -> edit(book, meta -> meta.setPage(2, "bad page"));
      case "changed_amount" ->
          edit(book, meta -> meta.setPage(2, meta.getPage(2).replace("500.0", "900.0")));
      case "no_lender_bank" -> lender.setBank(null);
      case "no_borrower_bank" -> borrower.setBank(null);
      case "insufficient" -> lender.getBank().setWealth(20.0);
      case "used_offer" ->
          lender
              .getLoanHandler()
              .useOffer(
                  LoanBook.offerId((BookMeta) book.getItemMeta()),
                  System.currentTimeMillis() + 60000);
    }
    double beforeLender = lender.getBank() == null ? 0 : lender.getBank().getWealth();
    double beforeBorrower = borrower.getBank() == null ? 0 : borrower.getBank().getWealth();
    signer.getInventory().setItem(0, book);
    assertTrue(sign(signer, book).isCancelled());
    fixture.ui.runTasks();
    if (lender.getBank() != null) assertEquals(beforeLender, lender.getBank().getWealth());
    if (borrower.getBank() != null) assertEquals(beforeBorrower, borrower.getBank().getWealth());
    assertTrue(lender.getLoanHandler().getLoansGiven().isEmpty());
    assertTrue(messages(signer).stream().anyMatch(m -> m.startsWith("§c")), reason);
  }

  @ParameterizedTest
  @ValueSource(strings = {"online", "offline", "absent", "no_expiry"})
  void acceptingALoanConservesFundsRecordsOneDebtAndProducesASignedReceipt(String notification) {
    Guild borrower = borrower();
    Player signer = player("Borrower");
    if (!notification.equals("absent")) {
      when(Bukkit.getPlayer("Leader")).thenReturn(leader);
      when(leader.isOnline()).thenReturn(!notification.equals("offline"));
    }
    ItemStack book = loanBook(3, borrower);
    if (notification.equals("no_expiry"))
      edit(book, meta -> meta.getPersistentDataContainer().remove(Keys.LONG));
    signer.getInventory().setItem(0, book);
    String offer = LoanBook.offerId((BookMeta) book.getItemMeta());
    double total = lender.getBank().getWealth() + borrower.getBank().getWealth();
    assertTrue(sign(signer, book).isCancelled());
    assertEquals(9500, lender.getBank().getWealth());
    assertEquals(600, borrower.getBank().getWealth());
    assertEquals(total, lender.getBank().getWealth() + borrower.getBank().getWealth());
    assertEquals(1, lender.getLoanHandler().getLoansGiven().size());
    Loan accepted = lender.getLoanHandler().getLoanById(offer);
    assertNotNull(accepted);
    assertSame(borrower, accepted.getBorrower());
    assertEquals(500, accepted.getAmount());
    sign(signer, book);
    assertEquals(600, borrower.getBank().getWealth());
    assertEquals(1, lender.getLoanHandler().getLoansGiven().size());
    fixture.ui.runTasks();
    ItemStack signed = signer.getInventory().getItem(0);
    assertEquals(Material.WRITTEN_BOOK, signed.getType());
    BookMeta meta = (BookMeta) signed.getItemMeta();
    assertEquals("Leader and Borrower", meta.getAuthor());
    assertTrue(meta.getTitle().contains("Loan Agreement"));
    assertTrue(meta.getPageCount() >= 2);
    verify(signer).sendMessage("§aLoan taken!");
    if (notification.equals("online") || notification.equals("no_expiry"))
      verify(leader).sendMessage(contains("Your loan has been accepted"));
    else verify(leader, never()).sendMessage(contains("Your loan has been accepted"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"unknown", "no_bank", "wrong_chunk", "null_chunk", "faction_bank", "guild_bank"})
  void bankPulsesRequireAnOwnedBankAtThePlayersCurrentChunk(String state) {
    Player actor = state.equals("unknown") ? player("Stranger") : leader;
    Chunk here = actor.getLocation().getChunk();
    if (state.equals("wrong_chunk")) lender.setBank(new Bank(lender, mock(Chunk.class)));
    if (state.equals("null_chunk")) lender.setBank(new Bank(lender, (Chunk) null));
    if (state.equals("faction_bank")) lender.setBank(new Bank(lender, here));
    if (state.equals("guild_bank")) {
      Guild guild = fixture.guild(faction, "merchant", "Merchant");
      actor = player("Merchant");
      guild.setBank(new Bank(guild, here));
    }
    PlayerBankPulseEvent event = new PlayerBankPulseEvent(actor);
    listener.bankPulse(event);
    boolean allowed = state.endsWith("bank") && !state.equals("no_bank");
    assertEquals(!allowed, event.isCancelled());
    if (state.equals("unknown")) verify(actor).sendMessage(contains("must belong"));
    else if (!allowed) verify(actor).sendMessage(contains("must be at your bank"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"taxed", "zero_rate", "no_bank", "outsider", "zero_income"})
  void earningsRecordGrossAndTaxesWithoutLosingMoneyOrSettlingTheGuildEarly(String state) {
    lender.setBank(new Bank(lender, 100, null));
    faction.setTaxRate(state.equals("zero_rate") ? 0 : 10);
    if (state.equals("no_bank")) lender.setBank(null);
    String name = state.equals("outsider") ? "Stranger" : "Leader";
    double gross = state.equals("zero_income") ? 0 : 200;
    double expectedTax = state.equals("taxed") ? 20 : 0;
    PlayerEarnMoneyEvent event = new PlayerEarnMoneyEvent(name, gross);
    listener.earnMoney(event);
    PlayerLedger ledger = economy.getLedger(name);
    assertEquals(expectedTax, event.getAmount());
    assertEquals(gross, ledger.getAmount(PlayerCashflow.EARNINGS));
    assertEquals(-expectedTax, ledger.getAmount(PlayerCashflow.CITIZEN_TAX), 1e-9);
    assertEquals(gross, ledger.getNetDaily() + expectedTax);
    assertEquals(expectedTax, lender.getLedger().getCitizenTaxesCopy().getOrDefault(name, 0.0));
    if (lender.getBank() != null) assertEquals(100, lender.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown_item", "coin", "away", "deposit"})
  void materialDepositsConsumeOnlySupportedMaterialsAtAnOwnedBank(String state) {
    ItemStack material = new ItemStack(Material.GOLD_INGOT, 7);
    YamlConfiguration config = new YamlConfiguration();
    config.set("value", 12.5);
    config.set("withdraw", state.equals("coin"));
    config.set("item", "minecraft.GOLD_INGOT");
    Coin coin = new Coin("gold", config);
    MoneyManager money = mock(MoneyManager.class);
    scoped(DenarEconomy.class).when(DenarEconomy::getMoneyManager).thenReturn(money);
    when(money.getCoin(material)).thenReturn(state.equals("unknown_item") ? null : coin);
    if (!state.equals("away")) lender.setBank(new Bank(lender, leader.getLocation().getChunk()));
    double[] credited = {0};
    doAnswer(
            call -> {
              credited[0] += (Double) call.getArgument(1);
              return null;
            })
        .when(money)
        .addMoneyToAccount(anyString(), anyDouble(), anyBoolean(), anyBoolean(), any());
    listener.depositMaterials(new PlayerDepositMaterialsEvent(leader, material));
    assertEquals(state.equals("deposit") ? 0 : 7, material.getAmount());
    assertEquals(87.5, credited[0] + material.getAmount() * coin.getValue());
    if (state.equals("deposit"))
      verify(money)
          .addMoneyToAccount(leader.getUniqueId().toString(), 87.5, false, true, Accounts.BANK);
    else
      verify(money, never())
          .addMoneyToAccount(anyString(), anyDouble(), anyBoolean(), anyBoolean(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing_company",
        "forming",
        "expired",
        "bad_terms",
        "not_leader",
        "invalid_duration"
      })
  void contractDraftsRejectUnavailableCompaniesOrInvalidTerms(String reason) {
    MercenaryCompany company = company(reason.equals("forming") ? 60 : 0);
    Player captain = player("Captain");
    ItemStack draft = ContractBook.draftBook(company);
    if (reason.equals("missing_company")) company.getGuild().setCompany(null);
    if (reason.equals("expired"))
      edit(
          draft,
          meta -> meta.getPersistentDataContainer().set(Keys.LONG, PersistentDataType.LONG, 1L));
    if (reason.equals("bad_terms"))
      edit(draft, meta -> meta.setPages("Instructions without a terms page"));
    if (reason.equals("not_leader")) company.getGuild().setLeader("Successor");
    if (reason.equals("invalid_duration"))
      edit(
          draft,
          meta ->
              meta.setPage(
                  2, ContractBook.termsPage(company, new ContractTerms(1, 50, 10, 0, 50, 500))));
    captain.getInventory().setItem(0, draft);
    assertTrue(sign(captain, draft).isCancelled());
    fixture.ui.runTasks();
    assertTrue(company.getContractHandler().getAll().isEmpty());
    assertTrue(messages(captain).stream().anyMatch(m -> m.startsWith("§c")), reason);
    BookMeta after = (BookMeta) captain.getInventory().getItem(0).getItemMeta();
    if (reason.equals("expired")) assertNull(ContractBook.stage(after));
    else assertEquals(ContractBook.STAGE_DRAFT, ContractBook.stage(after));
  }

  @Test
  void draftingAndReviewingAContractProducesReviewBooksAndAnOfferInstruction() {
    MercenaryCompany company = company(0);
    Player captain = player("Captain");
    ItemStack draft = ContractBook.draftBook(company);
    captain.getInventory().setItem(0, draft);
    assertTrue(sign(captain, draft).isCancelled());
    fixture.ui.runTasks();
    ItemStack review = captain.getInventory().getItem(0);
    BookMeta meta = (BookMeta) review.getItemMeta();
    assertEquals(ContractBook.STAGE_REVIEW, ContractBook.stage(meta));
    assertEquals(ContractTerms.defaults(), ContractBook.parseTerms(meta));
    assertTrue(ContractBook.matchesSnapshot(meta));
    sign(captain, review);
    fixture.ui.runTasks();
    verify(captain).sendMessage("§7Choose who to offer this to with §e/company offer <faction>");
    assertEquals(
        ContractBook.STAGE_REVIEW,
        ContractBook.stage((BookMeta) captain.getInventory().getItem(0).getItemMeta()));
    assertTrue(company.getContractHandler().getAll().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"not_leader", "tampered"})
  void reviewingAContractRequiresCurrentLeadershipAndTheSignedTerms(String reason) {
    MercenaryCompany company = company(0);
    Player captain = player("Captain");
    ItemStack review = ContractBook.reviewBook(company, ContractTerms.defaults());
    if (reason.equals("not_leader")) company.getGuild().setLeader("Successor");
    else edit(review, meta -> meta.getPersistentDataContainer().remove(Keys.SECONDARY_STRING_KEY));
    captain.getInventory().setItem(0, review);
    assertTrue(sign(captain, review).isCancelled());
    fixture.ui.runTasks();
    verify(captain)
        .sendMessage(
            contains(reason.equals("not_leader") ? "Only the company leader" : "tampered"));
    assertTrue(company.getContractHandler().getAll().isEmpty());
    if (reason.equals("tampered"))
      assertNull(ContractBook.stage((BookMeta) captain.getInventory().getItem(0).getItemMeta()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "tampered", "outsider", "accepted", "away"})
  void refusedMercenaryAgreementsDoNotActivateOrReplaceAnExistingContract(String reason) {
    MercenaryCompany company = company(0);
    Faction hirer = fixture.saved("hirer", "Hirer");
    hirer.getOrCreateMainGuild();
    var offer = company.getContractHandler().offer(hirer, ContractTerms.defaults());
    assertTrue(offer.ok(), offer.message());
    MercenaryContract contract = offer.contract();
    ItemStack book = ContractBook.agreementBook(contract);
    Player signer = player(reason.equals("outsider") ? "Stranger" : "Hirer");
    if (reason.equals("away"))
      when(net.tfminecraft.simplefactions.rest.RestServer.getProvince(signer)).thenReturn(99);
    if (reason.equals("missing")) company.getContractHandler().remove(contract.getId());
    if (reason.equals("tampered"))
      edit(book, meta -> meta.getPersistentDataContainer().remove(Keys.SECONDARY_STRING_KEY));
    if (reason.equals("accepted"))
      assertTrue(company.getContractHandler().accept(contract.getId(), hirer, "Hirer").ok());
    boolean wasActive = contract.isActive();
    signer.getInventory().setItem(0, book);
    assertTrue(sign(signer, book).isCancelled());
    fixture.ui.runTasks();
    assertEquals(wasActive, contract.isActive());
    assertTrue(messages(signer).stream().anyMatch(m -> m.startsWith("§c")), reason);
    assertNotEquals(Material.WRITTEN_BOOK, signer.getInventory().getItem(0).getType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"online", "offline", "absent"})
  void acceptingMercenaryServiceActivatesTheOfferAndCreatesASignedAgreement(String notification) {
    MercenaryCompany company = company(0);
    Faction hirer = fixture.saved("hirer", "Hirer");
    Guild treasury = hirer.getOrCreateMainGuild();
    treasury.setBank(new Bank(treasury, 1000, null));
    company.getGuild().setBank(new Bank(company.getGuild(), 500, null));
    Player signer = player("Hirer");
    Player captain = player("Captain");
    if (!notification.equals("absent")) {
      when(Bukkit.getPlayer("Captain")).thenReturn(captain);
      when(captain.isOnline()).thenReturn(notification.equals("online"));
    }
    var offer = company.getContractHandler().offer(hirer, ContractTerms.defaults());
    assertTrue(offer.ok(), offer.message());
    MercenaryContract contract = offer.contract();
    ItemStack book = ContractBook.agreementBook(contract);
    signer.getInventory().setItem(0, book);
    sign(signer, book);
    assertTrue(contract.isActive());
    assertEquals(1, company.getContractHandler().getActive().size());
    assertEquals(1000, treasury.getBank().getWealth());
    assertEquals(500, company.getGuild().getBank().getWealth());
    fixture.ui.runTasks();
    ItemStack signed = signer.getInventory().getItem(0);
    assertEquals(Material.WRITTEN_BOOK, signed.getType());
    BookMeta meta = (BookMeta) signed.getItemMeta();
    assertEquals("Captain and Hirer", meta.getAuthor());
    assertTrue(meta.getTitle().contains("Mercenary Contract"));
    assertEquals(6, meta.getPageCount());
    verify(signer).sendMessage(contains("entered your service"));
    if (notification.equals("online"))
      verify(captain).sendMessage(contains("entered your service"));
    else verify(captain, never()).sendMessage(contains("entered your service"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "review",
        "offer",
        "receipt",
        "contract_review",
        "contract_offer",
        "contract_receipt",
        "expired_loan",
        "expired_contract"
      })
  void everyDeferredBookStagePreservesANewlySelectedStack(String stage) {
    Player signer = leader;
    ItemStack book;
    if (stage.startsWith("contract") || stage.equals("expired_contract")) {
      MercenaryCompany company = company(0);
      signer = player("Captain");
      if (stage.equals("contract_receipt")) {
        Faction hirer = fixture.saved("hirer", "Hirer");
        hirer.getOrCreateMainGuild();
        signer = player("Hirer");
        var offer = company.getContractHandler().offer(hirer, ContractTerms.defaults());
        assertTrue(offer.ok(), offer.message());
        book = ContractBook.agreementBook(offer.contract());
      } else if (stage.equals("contract_offer")) {
        book = ContractBook.reviewBook(company, ContractTerms.defaults());
      } else book = ContractBook.draftBook(company);
    } else if (stage.equals("receipt")) {
      Guild borrower = borrower();
      signer = player("Borrower");
      book = loanBook(3, borrower);
    } else book = loanBook(stage.equals("offer") ? 2 : 1, null);
    if (stage.startsWith("expired"))
      edit(
          book,
          meta -> meta.getPersistentDataContainer().set(Keys.LONG, PersistentDataType.LONG, 1L));
    signer.getInventory().setItem(0, book);
    signer.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 3));
    assertTrue(sign(signer, book).isCancelled());
    signer.getInventory().setHeldItemSlot(1);
    fixture.ui.runTasks();
    assertEquals(Material.DIAMOND, signer.getInventory().getItem(1).getType());
    assertEquals(3, signer.getInventory().getItem(1).getAmount());
    ItemStack replacement = signer.getInventory().getItem(0);
    if (stage.endsWith("receipt")) assertEquals(Material.WRITTEN_BOOK, replacement.getType());
    else {
      assertEquals(Material.WRITABLE_BOOK, replacement.getType());
      BookMeta meta = (BookMeta) replacement.getItemMeta();
      if (stage.startsWith("expired")) assertFalse(meta.hasPages());
      else if (stage.startsWith("contract"))
        assertEquals(ContractBook.STAGE_REVIEW, ContractBook.stage(meta));
      else
        assertEquals(
            stage.equals("offer") ? 3 : 2,
            meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"offline", "removed", "different_book", "amount_changed"})
  void aDelayedBookUpdateRequiresTheOriginalUnchangedStackAndAnOnlinePlayer(String changed) {
    ItemStack draft = LoanBook.getBaseBook(lender);
    leader.getInventory().setItem(0, draft);
    sign(leader, draft);
    switch (changed) {
      case "offline" -> when(leader.isOnline()).thenReturn(false);
      case "removed" -> leader.getInventory().setItem(0, null);
      case "different_book" -> {
        ItemStack personal = new ItemStack(Material.WRITABLE_BOOK);
        edit(personal, meta -> meta.setPages("My private notes"));
        leader.getInventory().setItem(0, personal);
      }
      case "amount_changed" -> draft.setAmount(2);
    }
    fixture.ui.runTasks();
    ItemStack result = leader.getInventory().getItem(0);
    if (changed.equals("removed")) assertNull(result);
    else if (changed.equals("different_book"))
      assertEquals("My private notes", ((BookMeta) result.getItemMeta()).getPage(1));
    else {
      assertEquals(
          1,
          ((BookMeta) result.getItemMeta())
              .getPersistentDataContainer()
              .get(Keys.INT, PersistentDataType.INTEGER));
      assertEquals(changed.equals("amount_changed") ? 2 : 1, result.getAmount());
    }
  }

  @Test
  void loanBookSignedInTheOffHandNeverReplacesTheMainHandItem() {
    ItemStack book = LoanBook.getBaseBook(lender);
    leader.getInventory().setItem(0, new ItemStack(Material.NETHERITE_SWORD));
    leader.getInventory().setItem(40, book);
    BookMeta meta = (BookMeta) book.getItemMeta();
    PlayerEditBookEvent event = new PlayerEditBookEvent(leader, 40, meta, meta, true);
    listener.signBook(event);
    assertTrue(event.isCancelled());
    fixture.ui.runTasks();
    assertEquals(Material.NETHERITE_SWORD, leader.getInventory().getItem(0).getType());
    BookMeta review = (BookMeta) leader.getInventory().getItem(40).getItemMeta();
    assertEquals(2, review.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER));
  }

  private Guild borrower() {
    lender.setBank(new Bank(lender, 10000, null));
    Faction other = fixture.saved("borrowers", "Borrower");
    Guild borrower = other.getOrCreateMainGuild();
    borrower.setBank(new Bank(borrower, 100, null));
    return borrower;
  }

  private ItemStack loanBook(int stage, Guild borrower) {
    Loan loan =
        new Loan("draft", 500, lender, borrower, System.currentTimeMillis(), 30, 6, 2, true);
    return switch (stage) {
      case 1 -> LoanBook.getBaseBook(lender);
      case 2 -> LoanBook.getEstimatedBook(loan);
      default -> LoanBook.getLoanBook(loan);
    };
  }

  private MercenaryCompany company(int seconds) {
    Guild guild = fixture.guild(faction, "company", "Captain");
    fixture.provinceData.put(
        10, new net.tfminecraft.simplefactions.map.provinces.Province(10, "PLAINS", 50));
    guild.setCapital(10, false);
    MercenaryCompany company =
        new MercenaryCompany(
            guild, "River Guard", fixture.regiment("company", false, 1, 0), seconds);
    guild.setCompany(company);
    return company;
  }

  private void edit(ItemStack book, Consumer<BookMeta> edit) {
    BookMeta meta = (BookMeta) book.getItemMeta();
    edit.accept(meta);
    book.setItemMeta(meta);
  }

  private Player player(String name) {
    Player result = fixture.player(name);
    int[] selected = {0};
    PlayerInventory inventory = result.getInventory();
    when(inventory.getHeldItemSlot()).thenAnswer(call -> selected[0]);
    doAnswer(
            call -> {
              selected[0] = call.getArgument(0);
              return null;
            })
        .when(inventory)
        .setHeldItemSlot(anyInt());
    when(inventory.getItemInMainHand()).thenAnswer(call -> inventory.getItem(selected[0]));
    doAnswer(
            call -> {
              inventory.setItem(selected[0], call.getArgument(0));
              return null;
            })
        .when(inventory)
        .setItemInMainHand(any());
    return result;
  }

  private <T> MockedStatic<T> scoped(Class<T> type) {
    MockedStatic<T> scope = mockStatic(type);
    scopes.add(scope);
    return scope;
  }

  private List<String> messages(Player player) {
    return mockingDetails(player).getInvocations().stream()
        .filter(
            call ->
                call.getMethod().getName().equals("sendMessage")
                    && call.getArguments().length == 1
                    && call.getArgument(0) instanceof String)
        .map(call -> (String) call.getArgument(0))
        .toList();
  }

  private PlayerEditBookEvent sign(Player player, ItemStack book) {
    BookMeta meta = (BookMeta) book.getItemMeta();
    PlayerEditBookEvent event = new PlayerEditBookEvent(player, 0, meta, meta, true);
    listener.signBook(event);
    return event;
  }
}
