package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.government.proposal.FeeChange;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.CreditCalculator;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.fees.VfBuildersCatalog;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class FinancialMenusCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Guild lender;
  private Guild borrower;
  private Player leader;
  private Player borrowerLeader;
  private InventoryManager navigation;
  private LoanView loans;
  private VehicleFeeView fees;
  private String oldYear;
  private int oldIrlYear;
  private final List<MockedStatic<?>> scopes = new ArrayList<>();
  private final List<VfBuildersCatalog.Category> categories = new ArrayList<>();

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    faction = fixture.saved("lenders", "Leader");
    lender = faction.getOrCreateMainGuild();
    borrower = fixture.saved("borrowers", "Borrower").getOrCreateMainGuild();
    leader = fixture.player("Leader");
    borrowerLeader = fixture.player("Borrower");
    oldYear = Cache.baseYear;
    oldIrlYear = Cache.baseIrlYear;
    Cache.baseYear = "322 AE";
    Cache.baseIrlYear = 2026;
    navigation = mock(InventoryManager.class);
    navigation.confirming = new HashMap<>();
    navigation.governmentView = mock(GovernmentView.class);
    when(navigation.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.BARRIER));
    loans = new LoanView(navigation);
    fees = new VehicleFeeView(navigation);
    ItemAPI items = mock(ItemAPI.class);
    ItemCreator creator = mock(ItemCreator.class);
    when(items.getCreator()).thenReturn(creator);
    when(creator.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    when(creator.getItemFromPath(anyString()))
        .thenAnswer(call -> new ItemStack(Material.GOLD_NUGGET));
    scope(TLibs.class).when(TLibs::getItemAPI).thenReturn(items);
    scope(IconGetter.class)
        .when(() -> IconGetter.getIconOrDefault(anyString(), any()))
        .thenAnswer(call -> new ItemStack((Material) call.getArgument(1)));
    MockedStatic<VfBuildersCatalog> catalog = scope(VfBuildersCatalog.class);
    catalog.when(VfBuildersCatalog::categories).thenAnswer(call -> new ArrayList<>(categories));
    catalog
        .when(() -> VfBuildersCatalog.category(anyString()))
        .thenAnswer(
            call ->
                categories.stream()
                    .filter(c -> c.id().equalsIgnoreCase(call.getArgument(0)))
                    .findFirst()
                    .orElse(null));
  }

  @AfterEach
  void close() {
    try {
      for (int i = scopes.size() - 1; i >= 0; i--) scopes.get(i).close();
    } finally {
      Cache.baseYear = oldYear;
      Cache.baseIrlYear = oldIrlYear;
      fixture.close();
    }
  }

  @Test
  void vehicleFeeBackReturnsToTheSameFeeCategorySelection() {
    faction.getVehicleFeeHandler().applyBracket(FeeKind.VEHICLE_TAX, new Bracket(0, 100));
    categories.add(
        new VfBuildersCatalog.Category(
            "cars", null, List.of(new VfBuildersCatalog.Entry("car", null))));
    fees.feeVehicleView(leader, faction, FeeKind.VEHICLE_TAX, "cars", null);
    SFInventoryHolder holder = (SFInventoryHolder) top(leader).getHolder();
    fees.back(leader, faction, holder);
    SFInventoryHolder after = (SFInventoryHolder) top(leader).getHolder();
    assertEquals(SFGUI.FEE_CATEGORY_VIEW, after.getType());
    assertEquals("VEHICLE_TAX", after.getSecondaryId());
  }

  @Test
  void aLoanPaidAfterOpeningTheMenuCannotBePutIntoDefault() {
    Loan loan = loan("loan", System.currentTimeMillis());
    loans.loanDetailView(borrowerLeader, borrower, loan, true);
    Inventory oldMenu = top(borrowerLeader);
    loan.makePayment(loan.getTotalOwed(), false);
    assertTrue(loan.isPaidOff());
    int credit = borrower.getLoanHandler().getCreditScore();
    loans.click(fixture.ui.click(borrowerLeader, 14), oldMenu, borrowerLeader);
    assertFalse(loan.hasDefaulted());
    assertEquals(credit, borrower.getLoanHandler().getCreditScore());
    assertEquals(0, loan.getTotalOwed());
  }

  @Test
  void aTakenLoanWhoseIssuerDisappearedWhileTheMenuWasOpenIsIgnored() {
    loan("loan", System.currentTimeMillis());
    loans.loansTakenView(borrowerLeader, borrower);
    Inventory oldMenu = top(borrowerLeader);
    FactionManager.factions.remove(faction);
    assertDoesNotThrow(
        () -> loans.click(fixture.ui.click(borrowerLeader, 0), oldMenu, borrowerLeader));
    assertSame(oldMenu, top(borrowerLeader));
  }

  @Test
  void loanMainMenuTotalsAndIssuingControlsReflectCurrentGuildLeadership() {
    loan("first", System.currentTimeMillis());
    loans.loanMainView(leader, lender);
    Inventory menu = top(leader);
    assertEquals(SFGUI.LOAN_MAIN_VIEW, ((SFInventoryHolder) menu.getHolder()).getType());
    assertEquals("Loans Given", name(menu.getItem(2)));
    assertTrue(lore(menu.getItem(2)).contains("Total Lent: 1000.00d"));
    assertNotNull(menu.getItem(6));
    faction.setLeader("Successor");
    loans.loanMainView(leader, lender, menu);
    assertNull(menu.getItem(6));
    assertSame(menu, top(leader));
    loans.loanMainView(borrowerLeader, borrower);
    assertTrue(lore(top(borrowerLeader).getItem(4)).contains("Total Owed: 1000.0d"));
  }

  @Test
  void reportedLoanMenusExposeUnknownTotalsAndNoIssuingActionOnOpenAndRefresh() {
    scope(EspionageService.class)
        .when(() -> EspionageService.canViewExact(any(), any()))
        .thenReturn(false);
    loans.loanMainView(leader, lender);
    Inventory menu = top(leader);
    assertTrue(((SFInventoryHolder) menu.getHolder()).isReported());
    assertTrue(lore(menu.getItem(2)).contains("Unknown"));
    assertNull(menu.getItem(6));
    menu.setItem(6, new ItemStack(Material.WRITABLE_BOOK));
    loans.loanMainView(leader, lender, menu);
    assertSame(menu, top(leader));
    assertNull(menu.getItem(6));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void loanListNavigationDisplaysOnlyTheCorrectSideAndRefreshesRemovedRecords(boolean given) {
    Loan loan = loan("first", System.currentTimeMillis());
    Player actor = given ? leader : borrowerLeader;
    Guild owner = given ? lender : borrower;
    loans.loanMainView(actor, owner);
    loans.click(fixture.ui.click(actor, given ? 2 : 4), top(actor), actor);
    Inventory list = top(actor);
    assertEquals(loan.getId(), data(list.getItem(0), Keys.STRING_KEY));
    assertTrue(name(list.getItem(0)).startsWith(given ? "Loan to" : "Loan from"));
    loans.click(fixture.ui.click(actor, 0), list, actor);
    assertEquals(
        given ? SFGUI.ISSUED_LOAN_DETAIL_VIEW : SFGUI.TAKEN_LOAN_DETAIL_VIEW,
        ((SFInventoryHolder) top(actor).getHolder()).getType());
    assertEquals(loan.getId(), data(top(actor).getItem(15), Keys.STRING_KEY));
    lender.getLoanHandler().removeLoan(loan.getId());
    if (given) loans.loansGivenView(actor, owner, list);
    else loans.loansTakenView(actor, owner, list);
    assertNull(list.getItem(0));
    assertEquals(Material.BARRIER, list.getItem(53).getType());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void loanListsReserveTheBottomRowWhenMoreThanFortyFiveLoansExist(boolean given) {
    for (int n = 0; n < 46; n++) loan("loan-" + n, System.currentTimeMillis());
    Player actor = given ? leader : borrowerLeader;
    if (given) loans.loansGivenView(actor, lender);
    else loans.loansTakenView(actor, borrower);
    Inventory list = top(actor);
    for (int n = 0; n < 45; n++) assertNotNull(data(list.getItem(n), Keys.STRING_KEY));
    assertNull(list.getItem(45));
    assertEquals(Material.BARRIER, list.getItem(53).getType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"book", "wrong_item", "former_leader"})
  void issuingUsesTheCurrentLeaderAndRequiresABookAndQuill(String state) {
    ItemStack held =
        new ItemStack(state.equals("wrong_item") ? Material.DIAMOND : Material.WRITABLE_BOOK);
    var inventory = leader.getInventory();
    inventory.setItem(0, held);
    when(inventory.getItemInMainHand()).thenAnswer(call -> inventory.getItem(0));
    doAnswer(
            call -> {
              inventory.setItem(0, call.getArgument(0));
              return null;
            })
        .when(inventory)
        .setItemInMainHand(any());
    loans.loanMainView(leader, lender);
    Inventory menu = top(leader);
    if (state.equals("former_leader")) faction.setLeader("Successor");
    InventoryClickEvent event = fixture.ui.click(leader, 6);
    loans.click(event, menu, leader);
    assertTrue(event.isCancelled());
    if (state.equals("book")) {
      ItemMeta meta = leader.getInventory().getItem(0).getItemMeta();
      assertEquals(
          lender.getId(),
          meta.getPersistentDataContainer().get(Keys.STRING_KEY, PersistentDataType.STRING));
      assertEquals(1, meta.getPersistentDataContainer().get(Keys.INT, PersistentDataType.INTEGER));
      verify(leader).closeInventory();
    } else {
      assertSame(held, leader.getInventory().getItem(0));
      assertSame(menu, top(leader));
      if (state.equals("wrong_item")) verify(leader).sendMessage(contains("book and quill"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"active", "overdue", "defaulted", "paid", "resumed", "interest"})
  void loanDetailLoreAndControlsMatchTheActualLoanState(String state) {
    long start = System.currentTimeMillis() - (state.equals("overdue") ? 45L : 15L) * 86400000;
    Loan loan = loan("first", start);
    if (state.equals("defaulted") || state.equals("resumed")) loan.setDefaulted(true);
    if (state.equals("resumed")) loan.setDefaulted(false);
    if (state.equals("paid")) loan.makePayment(loan.getTotalOwed(), false);
    if (state.equals("interest")) {
      loan.setAutoPay(false);
      loan.tickDay();
    }
    loans.loanDetailView(borrowerLeader, borrower, loan, true);
    Inventory menu = top(borrowerLeader);
    String detail = lore(menu.getItem(15));
    assertTrue(detail.contains("Original Amount: 1000.0d"));
    assertEquals(lender.getId(), data(menu.getItem(15), Keys.SECONDARY_STRING_KEY));
    assertTrue(detail.contains("Issue Date:"));
    if (state.equals("defaulted")) {
      assertTrue(detail.contains("in default"));
      assertNull(menu.getItem(11));
      assertEquals("Resume Payments", name(menu.getItem(14)));
    }
    if (state.equals("paid")) {
      assertTrue(detail.contains("paid off"));
      assertNull(menu.getItem(11));
      assertNull(menu.getItem(14));
    }
    if (state.equals("overdue")) {
      assertTrue(detail.contains("overdue"));
      assertTrue(detail.contains("Daily Penalty:"));
    }
    if (state.equals("interest")) assertTrue(detail.contains("unpaid interest"));
    if (state.equals("resumed"))
      assertTrue(lore(menu.getItem(14)).contains("already defaulted before"));
    loans.loanDetailView(leader, lender, loan, false);
    assertEquals(
        state.equals("paid") ? "Settle Loan" : "Forgive Loan", name(top(leader).getItem(14)));
    assertNotNull(top(leader).getItem(13));
  }

  @Test
  void loanPaymentAndInterestButtonsExplainRealCreditConsequences() {
    Loan early = loan("early", System.currentTimeMillis());
    assertEquals(0, CreditCalculator.calculatePayoffBonus(early));
    assertTrue(lore(loans.creator.createPayOffLoanButton(early)).contains("no effect"));
    Loan mature = loan("mature", System.currentTimeMillis() - 29L * 86400000);
    assertTrue(CreditCalculator.calculatePayoffBonus(mature) > 0);
    assertTrue(lore(loans.creator.createPayOffLoanButton(mature)).contains("credit score bonus"));
    mature.makePayment(mature.getTotalOwed(), false);
    assertFalse(lore(loans.creator.createPayOffLoanButton(mature)).contains("credit score bonus"));
    Loan tinyDebt = loan("tiny", System.currentTimeMillis() - 45L * 86400000);
    tinyDebt.makePayment(999.99, false);
    assertEquals(0, CreditCalculator.calculateDefaultPenalty(tinyDebt));
    assertTrue(
        lore(loans.creator.createDefaultItem(tinyDebt)).contains("no effect on your credit score"));
  }

  @Test
  void borrowerActionsToggleAutopayAndDefaultWithoutDirectlyChangingBalances() {
    Loan loan = loan("first", System.currentTimeMillis());
    double borrowerBalance = borrower.getBank().getWealth(),
        lenderBalance = lender.getBank().getWealth();
    loans.loanDetailView(borrowerLeader, borrower, loan, true);
    loans.click(fixture.ui.click(borrowerLeader, 11), top(borrowerLeader), borrowerLeader);
    verify(navigation).setPayingLoan(borrowerLeader, loan);
    loans.loanDetailView(borrowerLeader, borrower, loan, true);
    loans.click(fixture.ui.click(borrowerLeader, 12), top(borrowerLeader), borrowerLeader);
    assertFalse(loan.isAutoPay());
    loans.click(fixture.ui.click(borrowerLeader, 12), top(borrowerLeader), borrowerLeader);
    assertTrue(loan.isAutoPay());
    int credit = borrower.getLoanHandler().getCreditScore();
    loans.click(fixture.ui.click(borrowerLeader, 14), top(borrowerLeader), borrowerLeader);
    assertTrue(loan.hasDefaulted());
    assertFalse(loan.isAutoPay());
    assertTrue(borrower.getLoanHandler().getCreditScore() < credit);
    int afterDefault = borrower.getLoanHandler().getCreditScore();
    loans.click(fixture.ui.click(borrowerLeader, 14), top(borrowerLeader), borrowerLeader);
    assertFalse(loan.hasDefaulted());
    assertEquals(afterDefault, borrower.getLoanHandler().getCreditScore());
    assertEquals(borrowerBalance, borrower.getBank().getWealth());
    assertEquals(lenderBalance, lender.getBank().getWealth());
  }

  @Test
  void lenderMayPauseResumeAndForgiveWithoutDebitingEitherBank() {
    Loan loan = loan("first", System.currentTimeMillis());
    double total = lender.getBank().getWealth() + borrower.getBank().getWealth();
    loans.loanDetailView(leader, lender, loan, false);
    loans.click(fixture.ui.click(leader, 13), top(leader), leader);
    assertTrue(loan.isInterestPaused());
    assertEquals("Resume Interest", name(top(leader).getItem(13)));
    loans.click(fixture.ui.click(leader, 13), top(leader), leader);
    assertFalse(loan.isInterestPaused());
    loans.click(fixture.ui.click(leader, 14), top(leader), leader);
    assertTrue(lender.getLoanHandler().getLoansGiven().isEmpty());
    assertTrue(borrower.getLoanHandler().getLoansTaken().isEmpty());
    assertEquals(SFGUI.LOANS_GIVEN_VIEW, ((SFInventoryHolder) top(leader).getHolder()).getType());
    assertEquals(total, lender.getBank().getWealth() + borrower.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 12, 14})
  void formerBorrowerLeaderCannotUseAnyStaleLoanAction(int slot) {
    Loan loan = loan("first", System.currentTimeMillis());
    loans.loanDetailView(borrowerLeader, borrower, loan, true);
    Inventory menu = top(borrowerLeader);
    borrower.getFaction().setLeader("Successor");
    loans.click(fixture.ui.click(borrowerLeader, slot), menu, borrowerLeader);
    assertTrue(loan.isAutoPay());
    assertFalse(loan.hasDefaulted());
    verify(navigation, never()).setPayingLoan(any(), any());
    assertEquals(1000, loan.getTotalOwed());
  }

  @ParameterizedTest
  @ValueSource(ints = {13, 14})
  void formerLenderLeaderCannotPauseOrForgiveLoans(int slot) {
    Loan loan = loan("first", System.currentTimeMillis());
    loans.loanDetailView(leader, lender, loan, false);
    faction.setLeader("Successor");
    loans.click(fixture.ui.click(leader, slot), top(leader), leader);
    assertFalse(loan.isInterestPaused());
    assertSame(loan, lender.getLoanHandler().getLoanById(loan.getId()));
  }

  @Test
  void staleAndUnrelatedLoanMenusAreIgnoredWithoutChangingTheLoan() {
    Loan loan = loan("first", System.currentTimeMillis());
    loans.loansGivenView(leader, lender);
    Inventory menu = top(leader);
    lender.getLoanHandler().removeLoan(loan.getId());
    loans.click(fixture.ui.click(leader, 0), menu, leader);
    assertSame(menu, top(leader));
    FactionManager.factions.remove(faction);
    InventoryClickEvent removed = fixture.ui.click(leader, 0);
    loans.click(removed, menu, leader);
    assertTrue(removed.isCancelled());
    Inventory plain = fixture.ui.inventory(null, 9, "Ordinary");
    leader.openInventory(plain);
    InventoryClickEvent unrelated = fixture.ui.click(leader, 0);
    loans.click(unrelated, plain, leader);
    assertFalse(unrelated.isCancelled());
  }

  @Test
  void feeSelectionReflectsAllConfiguredBracketsAndPerVehicleOverrides() {
    assertFalse(VehicleFeeView.anyChargeable(faction));
    for (FeeKind kind : FeeKind.values()) {
      faction.getVehicleFeeHandler().applyBracket(kind, new Bracket(0, 100));
      faction.getVehicleFeeHandler().setRate(kind, null, 5);
      faction.getVehicleFeeHandler().setRate(kind, "car", 10);
    }
    assertTrue(VehicleFeeView.anyChargeable(faction));
    fees.feeProposalView(leader, faction, null);
    Inventory menu = top(leader);
    for (int n = 0; n < FeeKind.values().length; n++) {
      assertEquals(FeeKind.values()[n].name(), data(menu.getItem(n), Keys.STRING_KEY));
      assertTrue(lore(menu.getItem(n)).contains("Vehicles with their own rate: 1"));
      assertTrue(lore(menu.getItem(n)).contains("The leader is exempt"));
    }
    faction.getVehicleFeeHandler().applyBracket(FeeKind.VEHICLE_TAX, new Bracket(0, 0));
    fees.feeProposalView(leader, faction, menu);
    assertEquals("REGISTRATION_FEE", data(menu.getItem(0), Keys.STRING_KEY));
    assertNull(menu.getItem(2));
  }

  @ParameterizedTest
  @EnumSource(FeeKind.class)
  void feeMenusShowTheChosenUnitsChargeAndStartGeneralOrSpecificInput(FeeKind kind) {
    faction.getVehicleFeeHandler().applyBracket(kind, new Bracket(0, 100));
    faction.getVehicleFeeHandler().setRate(kind, null, 5);
    faction.getVehicleFeeHandler().setRate(kind, "car", 10);
    scope(VehiclesConfigLoader.class)
        .when(() -> VehiclesConfigLoader.getUpkeep(anyString()))
        .thenReturn(20.0);
    categories.add(
        new VfBuildersCatalog.Category(
            "cars",
            null,
            List.of(
                new VfBuildersCatalog.Entry("car", null),
                new VfBuildersCatalog.Entry("truck", null))));
    fees.feeProposalView(leader, faction, null);
    fees.click(fixture.ui.click(leader, 0), top(leader), leader);
    Inventory category = top(leader);
    assertEquals(SFGUI.FEE_CATEGORY_VIEW, ((SFInventoryHolder) category.getHolder()).getType());
    assertEquals("*", data(category.getItem(0), Keys.STRING_KEY));
    assertTrue(lore(category.getItem(1)).contains("With their own rate: 1"));
    fees.click(fixture.ui.click(leader, 1), category, leader);
    Inventory vehicles = top(leader);
    assertTrue(lore(vehicles.getItem(0)).contains("Own Rate: " + kind.formatRate(10)));
    assertTrue(lore(vehicles.getItem(1)).contains("(general)"));
    assertTrue(
        lore(vehicles.getItem(0))
            .contains("Charge: " + (kind.isPercent() ? "2.00d per day" : "200.00d")));
    fees.click(fixture.ui.click(leader, 0), vehicles, leader);
    verify(navigation).setChangingFee(faction, leader, kind, "car");
    verify(leader)
        .sendMessage(contains(kind.isPercent() ? "percentage of upkeep" : "multiple of upkeep"));
    fees.feeCategoryView(leader, faction, kind, null);
    fees.click(fixture.ui.click(leader, 0), top(leader), leader);
    verify(navigation).setChangingFee(faction, leader, kind, null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void feeInputRechecksProposalConflictsAndThePlayersCurrentPermission(boolean conflict) {
    FeeKind kind = FeeKind.VEHICLE_TAX;
    faction.getVehicleFeeHandler().applyBracket(kind, new Bracket(0, 100));
    Player actor = conflict ? leader : fixture.player("Outsider");
    fees.feeCategoryView(actor, faction, kind, null);
    if (conflict) {
      Proposal active = new Proposal("Other", faction.getGovernment());
      active.setFeeProposal(new FeeChange(kind, null, 20));
      faction.getGovernment().propose(active);
    }
    fees.click(fixture.ui.click(actor, 0), top(actor), actor);
    verify(navigation, never()).setChangingFee(any(), any(), any(), any());
    verify(actor).sendMessage(contains("cannot propose right now"));
    fees.feeCategoryView(actor, faction, kind, top(actor));
    assertTrue(lore(top(actor).getItem(0)).contains("Another proposal is active"));
  }

  @Test
  void feeMenuCapacityAndRefreshDoNotLeaveStaleItemsOrOverwriteBackNavigation() {
    FeeKind kind = FeeKind.TRANSFER_FEE;
    for (int n = 0; n < 54; n++) {
      List<VfBuildersCatalog.Entry> entries = new ArrayList<>();
      for (int j = 0; j < 55; j++) entries.add(new VfBuildersCatalog.Entry("vehicle-" + j, null));
      categories.add(new VfBuildersCatalog.Category("category-" + n, null, entries));
    }
    fees.feeCategoryView(leader, faction, kind, null);
    assertNotNull(top(leader).getItem(52));
    assertEquals(Material.BARRIER, top(leader).getItem(53).getType());
    fees.feeVehicleView(leader, faction, kind, "category-0", null);
    Inventory vehicles = top(leader);
    assertEquals("vehicle-52", data(vehicles.getItem(52), Keys.STRING_KEY));
    assertEquals(Material.BARRIER, vehicles.getItem(53).getType());
    categories.clear();
    categories.add(
        new VfBuildersCatalog.Category(
            "category-0", null, List.of(new VfBuildersCatalog.Entry("only", null))));
    fees.feeVehicleView(leader, faction, kind, "category-0", vehicles);
    assertNull(vehicles.getItem(1));
    assertEquals("only", data(vehicles.getItem(0), Keys.STRING_KEY));
  }

  @Test
  void vehicleFeeNavigationFallsBackSafelyForRemovedCategoriesAndInvalidKinds() {
    fees.feeVehicleView(leader, faction, FeeKind.VEHICLE_TAX, "missing", null);
    assertEquals(SFGUI.FEE_CATEGORY_VIEW, ((SFInventoryHolder) top(leader).getHolder()).getType());
    fees.back(leader, faction, (SFInventoryHolder) top(leader).getHolder());
    assertEquals(SFGUI.FEE_PROPOSAL_VIEW, ((SFInventoryHolder) top(leader).getHolder()).getType());
    fees.back(leader, faction, (SFInventoryHolder) top(leader).getHolder());
    verify(navigation.governmentView).proposalView(leader, faction, null);
    fees.back(
        leader,
        faction,
        new SFInventoryHolder(faction.getId(), SFGUI.FEE_VEHICLE_VIEW, "removed_kind:cars"));
    assertEquals(SFGUI.FEE_PROPOSAL_VIEW, ((SFInventoryHolder) top(leader).getHolder()).getType());
    fees.back(leader, faction, new SFInventoryHolder(faction.getId(), SFGUI.FEE_VEHICLE_VIEW));
    fees.back(leader, faction, new SFInventoryHolder(faction.getId(), SFGUI.LOAN_MAIN_VIEW));
    assertEquals(SFGUI.FEE_PROPOSAL_VIEW, ((SFInventoryHolder) top(leader).getHolder()).getType());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ordinary",
        "empty",
        "no_key",
        "missing_faction",
        "invalid_kind",
        "missing_kind",
        "invalid_vehicle_kind",
        "unrelated_type"
      })
  void staleOrUnrelatedFeeClicksNeverStartInputOrMutateAnyRate(String state) {
    FeeKind kind = FeeKind.VEHICLE_TAX;
    faction.getVehicleFeeHandler().applyBracket(kind, new Bracket(0, 100));
    faction.getVehicleFeeHandler().setRate(kind, null, 5);
    fees.feeCategoryView(leader, faction, kind, null);
    Inventory menu = top(leader);
    ItemStack general = menu.getItem(0);
    switch (state) {
      case "ordinary" -> menu = fixture.ui.inventory(null, 9, "Other plugin");
      case "empty" -> menu.setItem(0, null);
      case "no_key" -> menu.setItem(0, new ItemStack(Material.PAPER));
      case "missing_faction" -> FactionManager.factions.remove(faction);
      case "invalid_kind", "missing_kind", "invalid_vehicle_kind", "unrelated_type" -> {
        SFGUI type =
            state.equals("invalid_vehicle_kind")
                ? SFGUI.FEE_VEHICLE_VIEW
                : state.equals("unrelated_type") ? SFGUI.LOAN_MAIN_VIEW : SFGUI.FEE_CATEGORY_VIEW;
        menu =
            fixture.ui.inventory(
                new SFInventoryHolder(
                    faction.getId(), type, state.equals("missing_kind") ? null : "obsolete:cars"),
                54,
                "Stale menu");
        menu.setItem(0, general);
      }
    }
    leader.openInventory(menu);
    InventoryClickEvent event = fixture.ui.click(leader, 0);
    fees.click(event, menu, leader);
    assertTrue(event.isCancelled());
    assertSame(menu, top(leader));
    assertEquals(5, faction.getVehicleFeeHandler().getRate(kind));
    verify(navigation, never()).setChangingFee(any(), any(), any(), any());
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

  private Loan loan(String id, long start) {
    Loan loan = new Loan(id, 1000, lender, borrower, start, 30, 7, 2, true);
    lender.getLoanHandler().issueLoan(loan);
    return loan;
  }

  private Inventory top(Player player) {
    return player.getOpenInventory().getTopInventory();
  }

  private <T> MockedStatic<T> scope(Class<T> type) {
    MockedStatic<T> scope = mockStatic(type);
    scopes.add(scope);
    return scope;
  }
}
