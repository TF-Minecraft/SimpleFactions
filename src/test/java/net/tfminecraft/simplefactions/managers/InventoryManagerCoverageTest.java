package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.enums.Member;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.loans.LoanHandler;
import net.tfminecraft.simplefactions.installation.handler.ConstructResult;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.holder.*;
import net.tfminecraft.simplefactions.managers.inventory.*;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompanyService;
import net.tfminecraft.simplefactions.mercenary.contract.ContractHandler;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.objects.handler.VehicleFeeHandler;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.BattleWarbandRetreatService.ConfirmHandler;
import net.tfminecraft.simplefactions.war.campaign.progression.WhitePeaceService;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Tests InventoryManager's routing contract; individual views have their own rendering tests. */
@SuppressWarnings("deprecation")
class InventoryManagerCoverageTest {
  private GuiTestFixture gui;
  private InventoryManager manager;
  private MockedStatic<FactionManager> factions;
  private Player player;
  private Faction faction;
  private Guild guild;
  private Government government;
  private TaxHandler taxes;
  private VehicleFeeHandler fees;

  @BeforeEach
  void setUp() {
    gui = new GuiTestFixture();
    player = gui.player("Leader");
    faction = mock(Faction.class);
    guild = mock(Guild.class);
    government = mock(Government.class);
    when(faction.getId()).thenReturn("realm");
    when(faction.getLeader()).thenReturn("Leader");
    when(faction.isLeader("Leader")).thenReturn(true);
    when(faction.getGovernment()).thenReturn(government);
    when(government.getFaction()).thenReturn(faction);
    when(guild.getId()).thenReturn("guild");
    when(guild.getFaction()).thenReturn(faction);
    when(guild.isLeader(player)).thenReturn(true);
    taxes = new TaxHandler(faction, 10, 0, 0, 0, 0);
    fees = new VehicleFeeHandler(faction);
    when(faction.getTaxHandler()).thenReturn(taxes);
    when(faction.getVehicleFeeHandler()).thenReturn(fees);
    when(faction.hasFactionRule(any(Rules.class))).thenReturn(true);
    fees.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(0, 100));
    factions = mockStatic(FactionManager.class);
    factions.when(() -> FactionManager.getByString("realm")).thenReturn(faction);
    factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    manager = new InventoryManager();
    manager.warView = mock(WarView.class);
    manager.campaignView = mock(CampaignView.class);
    manager.campaignInstallationPickView = mock(CampaignInstallationPickView.class);
    manager.campaignRaidLaunchView = mock(CampaignRaidLaunchView.class);
    manager.declareWarView = mock(DeclareWarView.class);
    manager.factionView = mock(FactionView.class);
    manager.guildView = mock(GuildView.class);
    manager.companyView = mock(CompanyView.class);
    manager.contractView = mock(ContractView.class);
    manager.mercenaryMarketView = mock(MercenaryMarketView.class);
    manager.lawView = mock(LawView.class);
    manager.governmentView = mock(GovernmentView.class);
    manager.vehicleFeeView = mock(VehicleFeeView.class);
    manager.movementView = mock(MovementView.class);
    manager.tierTitleView = mock(TierTitleView.class);
    manager.militaryView = mock(MilitaryView.class);
    manager.installationView = mock(InstallationView.class);
    manager.relationView = mock(RelationView.class);
    manager.electionView = mock(ElectionView.class);
    manager.taxView = mock(TaxView.class);
    manager.loanView = mock(LoanView.class);
  }

  @AfterEach
  void close() {
    try {
      if (factions != null) factions.close();
    } finally {
      if (gui != null) gui.close();
    }
  }

  @Test
  void builtButtonsCarryTheirActionAndCopiesKeepIndependentMetadataAndAmounts() {
    ItemStack button = manager.createButton("confirm", "action", "original");
    assertEquals(Material.GREEN_CONCRETE, button.getType());
    assertEquals("§cConfirm", button.getItemMeta().getDisplayName());
    assertEquals("original", gui.data(button, "action"));
    ItemStack copy = button.clone();
    copy.setAmount(4);
    ItemMeta edited = copy.getItemMeta();
    edited.setDisplayName("Edited");
    edited.setLore(List.of("Details"));
    edited
        .getPersistentDataContainer()
        .set(gui.key("action"), PersistentDataType.STRING, "changed");
    assertEquals("original", gui.data(copy, "action"), "getItemMeta returns an independent copy");
    copy.setItemMeta(edited);
    assertEquals(1, button.getAmount());
    assertEquals(4, copy.getAmount());
    assertEquals("original", gui.data(button, "action"));
    assertEquals("changed", gui.data(copy, "action"));
    assertEquals(List.of("Details"), copy.getItemMeta().getLore());
    assertFalse(button.isSimilar(copy));
    assertTrue(button.isSimilar(new ItemStack(button)));
    ItemStack cancel = manager.createButton("CaNcEl", "action", "original");
    assertEquals(Material.RED_CONCRETE, cancel.getType());
    assertEquals("§cCancel", cancel.getItemMeta().getDisplayName());
    ItemStack back = manager.createBackButton(SFGUI.GUILD_VIEW);
    assertEquals(Material.BARRIER, back.getType());
    assertEquals("§cBack", back.getItemMeta().getDisplayName());
    assertEquals("GUILD_VIEW", gui.data(back, "gui"));
    ItemStack filler = manager.getFiller(Material.GRAY_STAINED_GLASS_PANE);
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, filler.getType());
    assertEquals("§c", filler.getItemMeta().getDisplayName());
  }

  @Test
  void chatIsCancelledImmediatelyButRateMutationRunsOnTheScheduledMainThread() {
    manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
    AsyncPlayerChatEvent event = chat("25.126");
    manager.setRate(event);
    assertTrue(event.isCancelled());
    assertEquals(10, taxes.getCitizenTax());
    assertEquals(1, gui.tasks.size());
    gui.runTasks();
    assertEquals(25.13, taxes.getCitizenTax());
    assertFalse(manager.chatTrigger(player));
    verify(manager.governmentView).governmentView(player, faction, null);
    AsyncPlayerChatEvent publicChat = chat("hello");
    manager.setRate(publicChat);
    assertFalse(publicChat.isCancelled());
    assertTrue(gui.tasks.isEmpty());
  }

  @Test
  void startExpiresEveryPromptAfterThirtyTicksWithoutChangingValues() {
    manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
    manager.setChangingFee(faction, player, FeeKind.REGISTRATION_FEE, null);
    manager.loanPayments.put(player, new LoanPayment(guild, mock(Loan.class)));
    manager.setChangingDividend(player, guild);
    manager.setChangingDonation(player, guild);
    manager.slotChanges.put(player, new SlotChangePrompt("guild", "contract"));
    manager.start();
    assertNotNull(manager.getUpdater());
    verify(manager.guildView).setProvinceManager(gui.plugin.getProvinceManager());
    verify(gui.scheduler).runTaskTimer(eq(gui.plugin), any(Runnable.class), eq(0L), eq(20L));
    assertEquals(1, gui.repeatingTasks.size());
    Runnable tick = gui.repeatingTasks.getFirst();
    for (int i = 0; i < 29; i++) tick.run();
    assertTrue(manager.chatTrigger(player));
    assertEquals(1, manager.taxChange.size());
    assertEquals(1, manager.feeChange.size());
    assertTrue(manager.isPayingLoan(player));
    assertEquals(1, manager.dividendChange.size());
    assertEquals(1, manager.donationChange.size());
    assertEquals(1, manager.slotChanges.size());
    tick.run();
    assertFalse(manager.chatTrigger(player));
    for (String message :
        List.of(
            "Tax change timed out.",
            "Fee change timed out.",
            "Loan payment timed out.",
            "Dividend change timed out.",
            "Donation change timed out.",
            "Slot change cancelled.")) verify(player).sendMessage("§c" + message);
    assertEquals(10, taxes.getCitizenTax());
    verify(guild, never()).setDividendPercent(anyDouble());
    verify(guild, never()).setDonationAmount(anyDouble());
  }

  @Test
  void queuedChatDoesNotResurrectAnExpiredPrompt() {
    manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
    manager.setRate(chat("40"));
    manager.start();
    for (int i = 0; i < 30; i++) gui.repeatingTasks.getFirst().run();
    gui.runTasks();
    assertFalse(manager.chatTrigger(player));
    assertEquals(10, taxes.getCitizenTax());
    verifyNoInteractions(manager.governmentView);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rateOutsideItsCurrentLawBracketCanBeRetried(boolean tax) {
    taxes.applyBracket(TaxTarget.CITIZENS, new Bracket(5, 30));
    fees.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(5, 30));
    if (tax) manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
    else manager.setChangingFee(faction, player, FeeKind.REGISTRATION_FEE, null);
    for (String invalid : List.of("31", "4")) {
      if (tax) manager.taxChat(player, chat(invalid));
      else manager.feeChat(player, chat(invalid));
      assertTrue(manager.chatTrigger(player));
    }
    verify(player).sendMessage(startsWith("§cThe maximum"));
    verify(player).sendMessage(startsWith("§cThe minimum"));
    verifyNoInteractions(manager.governmentView);
    if (tax) manager.taxChat(player, chat("20"));
    else manager.feeChat(player, chat("20"));
    assertFalse(manager.chatTrigger(player));
    assertEquals(20, tax ? taxes.getCitizenTax() : fees.getRate(FeeKind.REGISTRATION_FEE));
  }

  @Test
  void deletedFactionPromptsAreDiscardedWithoutSubmitting() {
    manager.setChanging(null, player, TaxTarget.CITIZENS, null);
    manager.setChangingFee(null, player, FeeKind.REGISTRATION_FEE, null);
    manager.taxChat(player, chat("20"));
    manager.feeChat(player, chat("20"));
    assertFalse(manager.chatTrigger(player));
    verifyNoInteractions(government, manager.governmentView);
  }

  @ParameterizedTest
  @EnumSource(FeeKind.class)
  void feeErrorDescribesTheRightUnit(FeeKind kind) {
    manager.setChangingFee(faction, player, kind, "cart");
    manager.feeChat(player, chat("invalid"));
    assertTrue(manager.chatTrigger(player));
    verify(player).sendMessage(contains(kind.isPercent() ? "15.5% of upkeep" : "1.5 times upkeep"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void councilReceivesTheRequestedTargetAndRoundedRateWithoutApplyingIt(boolean tax) {
    when(government.hasCouncil()).thenReturn(true);
    when(government.canBeProposed(any())).thenReturn(true);
    when(government.canPropose(player)).thenReturn(true);
    if (tax) {
      manager.setChanging(faction, player, TaxTarget.GUILD_ID, "merchants");
      manager.taxChat(player, chat("15.126"));
    } else {
      manager.setChangingFee(faction, player, FeeKind.REGISTRATION_FEE, "cart");
      manager.feeChat(player, chat("15.126"));
    }
    ArgumentCaptor<Proposal> captured = ArgumentCaptor.forClass(Proposal.class);
    verify(government).propose(captured.capture());
    Proposal proposal = captured.getValue();
    if (tax) {
      assertEquals(TaxTarget.GUILD_ID, proposal.getTaxChange().getTarget());
      assertEquals("merchants", proposal.getTaxChange().getId());
      assertEquals(15.13, proposal.getTaxChange().getNewTax());
    } else {
      assertEquals(FeeKind.REGISTRATION_FEE, proposal.getFeeChange().getKind());
      assertEquals("cart", proposal.getFeeChange().getVehicleTypeId());
      assertEquals(15.13, proposal.getFeeChange().getNewRate());
    }
    assertEquals(10, taxes.getCitizenTax());
    assertEquals(0, fees.getRate(FeeKind.REGISTRATION_FEE));
    assertFalse(manager.chatTrigger(player));
    verify(player).sendTitle("", "§aProposal Added", 20, 80, 20);
    verify(manager.governmentView).governmentView(player, faction, null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void duplicateOrRevokedProposalPermissionDoesNotApplyTheRate(boolean duplicate) {
    when(government.hasCouncil()).thenReturn(true);
    when(government.canBeProposed(any())).thenReturn(!duplicate);
    manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
    manager.taxChat(player, chat("20"));
    assertFalse(manager.chatTrigger(player));
    assertEquals(10, taxes.getCitizenTax());
    verify(government, never()).propose(any());
    verify(government, never()).startMovement(anyString(), any());
    verify(player)
        .sendMessage(
            duplicate
                ? "§cThere is already a proposal active for this target."
                : "§cYou can no longer propose this change.");
    verifyNoInteractions(manager.governmentView);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void eligibleMemberAddsToAnExistingMovementOrStartsOne(boolean existing) {
    when(government.hasCouncil()).thenReturn(true);
    when(government.canBeProposed(any())).thenReturn(true);
    when(government.canProposeOrStartMovement(player)).thenReturn(true);
    Movement movement = mock(Movement.class);
    when(government.getMovementByMember("Leader")).thenReturn(existing ? movement : null);
    manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
    manager.taxChat(player, chat("20"));
    ArgumentCaptor<Proposal> proposal = ArgumentCaptor.forClass(Proposal.class);
    if (existing) {
      verify(movement).createCause(eq("Leader"), proposal.capture());
      verify(government, never()).startMovement(anyString(), any());
    } else verify(government).startMovement(eq("Leader"), proposal.capture());
    assertEquals(20, proposal.getValue().getTaxChange().getNewTax());
    assertEquals(10, taxes.getCitizenTax());
    assertFalse(manager.chatTrigger(player));
    verify(manager.governmentView).governmentView(player, faction, null);
  }

  @Test
  void singleProvinceHostCannotStartAGuildMovement() {
    when(government.hasCouncil()).thenReturn(true);
    when(government.canBeProposed(any())).thenReturn(true);
    when(government.canProposeOrStartMovement(player)).thenReturn(true);
    when(faction.getProvinces()).thenReturn(List.of(1));
    when(faction.getRelationToFaction("Leader")).thenReturn(Member.GUILD_LEADER);
    manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
    manager.taxChat(player, chat("20"));
    verify(government, never()).startMovement(anyString(), any());
    verify(player).sendMessage(CivilWarCopy.ONE_PROVINCE_HOST_GUILD);
    assertEquals(10, taxes.getCitizenTax());
    assertFalse(manager.chatTrigger(player));
  }

  @ParameterizedTest
  @CsvSource({"25.126,25.13,174.87,74.87", "150,100,100,0"})
  void loanChatWithdrawsOnlyTheActualRoundedPayment(
      String text, double paid, double balance, double owed) {
    Loan loan = loan(100, 200);
    manager.setPayingLoan(player, loan);
    assertTrue(manager.isPayingLoan(player));
    manager.setRate(chat(text));
    assertEquals(200, guild.getBank().getWealth());
    gui.runTasks();
    assertEquals(paid, loan.getPaid());
    assertEquals(balance, guild.getBank().getWealth(), 0.001);
    assertEquals(owed, loan.getTotalOwed(), 0.001);
    assertFalse(manager.isPayingLoan(player));
    verify(loan.getIssuer().getLedger()).addLoanPaymentEntry("guild", paid);
    verify(guild).updateWealth();
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "0", "-2", "NaN", "201"})
  void badLoanPaymentPreservesDebtFundsAndPrompt(String text) {
    Loan loan = loan(100, 200);
    manager.setPayingLoan(player, loan);
    Object prompt = manager.loanPayments.get(player);
    clearInvocations(player);
    manager.loanPaymentChat(player, chat(text));
    assertSame(prompt, manager.loanPayments.get(player));
    assertEquals(200, guild.getBank().getWealth());
    assertEquals(100, loan.getTotalOwed());
    verify(player).sendMessage("§4Type 'cancel' to cancel.");
    verifyNoInteractions(loan.getIssuer().getLedger());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void leavingOrChangingGuildAbandonsLoanPayment(boolean left) {
    Loan loan = loan(100, 200);
    manager.setPayingLoan(player, loan);
    Guild other = mock(Guild.class);
    when(other.getId()).thenReturn("other");
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(left ? null : other);
    manager.loanPaymentChat(player, chat("25"));
    assertFalse(manager.isPayingLoan(player));
    assertEquals(100, loan.getTotalOwed());
    assertEquals(200, guild.getBank().getWealth());
  }

  @Test
  void loanPromptRequiresALeadingGuildAndCanBeCancelled() {
    Loan loan = loan(100, 200);
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(null);
    manager.setPayingLoan(player, loan);
    assertFalse(manager.isPayingLoan(player));
    verify(player, never()).closeInventory();
    factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
    manager.setPayingLoan(player, loan);
    manager.loanPaymentChat(player, chat("CANCEL"));
    assertFalse(manager.isPayingLoan(player));
    assertEquals(200, guild.getBank().getWealth());
    verify(manager.loanView).loanMainView(player, guild);
  }

  @ParameterizedTest
  @ValueSource(strings = {"25.126", "0"})
  void donationChatStoresRoundedDenarsAndDisplaysTheCostOrRemoval(String text) {
    AtomicReference<Double> donation = donation(50);
    manager.setChangingDonation(player, guild);
    clearInvocations(player);
    manager.setRate(chat(text));
    assertEquals(50, donation.get());
    gui.runTasks();
    assertEquals(Double.parseDouble(text) == 0 ? 0 : 25.13, donation.get());
    assertFalse(manager.chatTrigger(player));
    verify(player)
        .sendMessage(
            contains(text.equals("0") ? "Daily donation cleared." : "Daily donation set to"));
    verify(manager.guildView).guildView(player, guild);
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad", "NaN", "Infinity", "-1"})
  void invalidDonationCanBeRetriedWithoutChangingTheAmount(String text) {
    AtomicReference<Double> donation = donation(50);
    manager.setChangingDonation(player, guild);
    Object prompt = manager.donationChange.get(player);
    manager.donationChat(player, chat(text));
    assertEquals(50, donation.get());
    assertSame(prompt, manager.donationChange.get(player));
    verify(player).sendMessage("§4Type 'cancel' to cancel.");
    verifyNoInteractions(manager.guildView);
  }

  @Test
  void donationAndDividendNullGuardsDoNotOpenPrompts() {
    manager.setChangingDonation(player, null);
    manager.setChangingDonation(null, guild);
    manager.setChangingDividend(player, null);
    manager.setChangingDividend(null, guild);
    when(guild.isBase()).thenReturn(true);
    manager.setChangingDonation(player, guild);
    manager.donationChat(player, chat("20"));
    manager.dividendChat(player, chat("20"));
    manager.donationChange.put(player, new DonationChange(null));
    manager.dividendChange.put(player, new DividendChange(null));
    manager.donationChat(player, chat("20"));
    manager.dividendChat(player, chat("20"));
    assertFalse(manager.chatTrigger(player));
    verify(player, never()).closeInventory();
    verify(guild, never()).setDonationAmount(anyDouble());
    verify(guild, never()).setDividendPercent(anyDouble());
  }

  @Test
  void donationCancellationLeavesTheExistingGiftAndReturnsToTheGuild() {
    AtomicReference<Double> donation = donation(50);
    manager.setChangingDonation(player, guild);
    manager.donationChat(player, chat("CANCEL"));
    assertEquals(50, donation.get());
    assertFalse(manager.chatTrigger(player));
    verify(manager.guildView).guildView(player, guild);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void donationPromptRechecksLeadershipAndBaseGuildStatus(boolean becameBase) {
    donation(50);
    manager.setChangingDonation(player, guild);
    when(guild.isBase()).thenReturn(becameBase);
    when(guild.isLeader(player)).thenReturn(becameBase);
    manager.donationChat(player, chat("20"));
    assertFalse(manager.chatTrigger(player));
    verify(guild, never()).setDonationAmount(anyDouble());
    verify(player).sendMessage("§cOnly the guild leader can set donations.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "101"})
  void dividendOutsidePercentRangeCanBeRetried(String text) {
    manager.setChangingDividend(player, guild);
    Object prompt = manager.dividendChange.get(player);
    manager.dividendChat(player, chat(text));
    assertSame(prompt, manager.dividendChange.get(player));
    verify(guild, never()).setDividendPercent(anyDouble());
    verify(player).sendMessage("§cDividend percentage must be between §e0 §cand §e100§c.");
  }

  @Test
  void dividendPromptRechecksLeadershipBeforeApplying() {
    manager.setChangingDividend(player, guild);
    when(guild.isLeader(player)).thenReturn(false);
    manager.dividendChat(player, chat("20"));
    assertFalse(manager.chatTrigger(player));
    verify(guild, never()).setDividendPercent(anyDouble());
    verify(player).sendMessage("§cOnly the guild leader can set dividends.");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void slotChatSubmitsAnIntegerAmendmentAndDisplaysItsResult(boolean accepted) {
    MercenaryContract contract = contract();
    ContractHandler handler = guild.getCompany().getContractHandler();
    when(handler.proposeSlots(eq("contract"), eq("Leader"), eq(4), anyLong()))
        .thenReturn(new MercenaryResult(accepted, accepted ? "Offer sent" : "Capacity changed"));
    manager.beginSlotChange(player, guild, contract);
    assertEquals("guild", manager.slotChanges.get(player).getGuildId());
    assertEquals("contract", manager.slotChanges.get(player).getContractId());
    verify(player).sendMessage(contains("slots."));
    manager.setRate(chat(" 4 "));
    verify(handler, never()).proposeSlots(anyString(), anyString(), anyInt(), anyLong());
    gui.runTasks();
    verify(handler).proposeSlots(eq("contract"), eq("Leader"), eq(4), anyLong());
    assertFalse(manager.chatTrigger(player));
    verify(player).sendMessage(accepted ? "§aOffer sent" : "§cCapacity changed");
    verify(manager.contractView).detailView(player, guild, "contract");
  }

  @Test
  void slotInputRejectsFractionsThenCanBeCancelledWithoutAnAmendment() {
    MercenaryContract contract = contract();
    manager.beginSlotChange(player, guild, contract);
    Object prompt = manager.slotChanges.get(player);
    manager.slotChat(player, chat("1.5"));
    assertSame(prompt, manager.slotChanges.get(player));
    verify(player).sendMessage("§cEnter a whole number of slots, such as §e4§c.");
    manager.slotChat(player, chat("cancel"));
    assertFalse(manager.chatTrigger(player));
    verify(guild.getCompany().getContractHandler(), never())
        .proposeSlots(anyString(), anyString(), anyInt(), anyLong());
    verify(manager.contractView).detailView(player, guild, "contract");
  }

  @ParameterizedTest
  @ValueSource(strings = {"guild", "company", "contract", "leader"})
  void slotPromptRechecksItsOwnerContractAndLeader(String removed) {
    MercenaryContract contract = contract();
    MercenaryCompany company = guild.getCompany();
    ContractHandler handler = company.getContractHandler();
    manager.beginSlotChange(player, guild, contract);
    switch (removed) {
      case "guild" ->
          factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(null);
      case "company" -> when(guild.getCompany()).thenReturn(null);
      case "contract" -> when(handler.getById("contract")).thenReturn(null);
      case "leader" -> when(company.isLeader("Leader")).thenReturn(false);
    }
    manager.slotChat(player, chat("4"));
    assertFalse(manager.chatTrigger(player));
    verify(handler, never()).proposeSlots(anyString(), anyString(), anyInt(), anyLong());
    verify(player)
        .sendMessage(
            removed.equals("leader")
                ? "§cOnly the guild leader can change a contract's slots."
                : "§cThat contract no longer exists.");
  }

  @Test
  void incompleteSlotPromptsDoNotCloseTheCurrentInventory() {
    MercenaryContract contract = mock(MercenaryContract.class);
    manager.beginSlotChange(null, guild, contract);
    manager.beginSlotChange(player, null, contract);
    manager.beginSlotChange(player, guild, null);
    manager.beginSlotChange(player, guild, contract);
    manager.slotChat(player, chat("4"));
    assertFalse(manager.chatTrigger(player));
    verify(player, never()).closeInventory();
  }

  @Test
  void scheduledDividendInputUsesTheCurrentLeaderAndClearsItsPrompt() {
    manager.setChangingDividend(player, guild);
    manager.setRate(chat("20"));
    verify(guild, never()).setDividendPercent(anyDouble());
    gui.runTasks();
    verify(guild).setDividendPercent(20);
    assertFalse(manager.chatTrigger(player));
    verify(manager.guildView).guildView(player, guild);
  }

  @Test
  void scheduledFeeInputAppliesToItsRequestedVehicleType() {
    manager.setChangingFee(faction, player, FeeKind.REGISTRATION_FEE, "cart");
    manager.setRate(chat("2.5"));
    assertFalse(fees.hasTypeRate(FeeKind.REGISTRATION_FEE, "cart"));
    gui.runTasks();
    assertEquals(2.5, fees.getRate(FeeKind.REGISTRATION_FEE, "cart"));
    assertEquals(0, fees.getRate(FeeKind.REGISTRATION_FEE));
    assertFalse(manager.chatTrigger(player));
  }

  @Test
  void quittingClearsCompanyAndSlotConfirmationsWithoutClearingAnotherPlayersState() {
    Player other = gui.player("Other");
    manager.pendingCompanyFounds.put(player, "Company");
    manager.confirming.put(player, faction);
    manager.slotChanges.put(player, new SlotChangePrompt("guild", "contract"));
    manager.pendingCompanyFounds.put(other, "Other Company");
    manager.confirming.put(other, faction);
    manager.clearPendingOnQuit(new PlayerQuitEvent(player, "quit"));
    assertFalse(manager.pendingCompanyFounds.containsKey(player));
    assertFalse(manager.confirming.containsKey(player));
    assertFalse(manager.slotChanges.containsKey(player));
    assertEquals("Other Company", manager.pendingCompanyFounds.get(other));
    assertSame(faction, manager.confirming.get(other));
    manager.confirming.put(player, faction);
    manager.clearPendingOnQuit(new PlayerQuitEvent(player, "quit"));
    assertSame(
        faction,
        manager.confirming.get(player),
        "Other confirmation types retain their own lifecycle");
  }

  @Test
  void publicRealmNavigationPreservesTheInventoryAndDomainArguments() {
    Inventory inventory = gui.inventory(null, 54, "Menu");
    Movement movement = mock(Movement.class);
    Cause cause = mock(Cause.class);
    Tier tier = mock(Tier.class);
    manager.factionList(player);
    manager.factionView(player, faction);
    manager.lawView(player, faction, inventory);
    manager.governmentView(player, faction, inventory);
    manager.proposalView(player, faction, inventory);
    manager.movementView(player, faction, movement, inventory);
    manager.movementListView(player, faction, inventory);
    manager.causesView(player, faction, movement, inventory);
    manager.causeView(player, faction, movement, cause, inventory);
    manager.tierView(inventory, player, faction, true);
    manager.titleView(inventory, player, faction, false);
    manager.titleTypeView(inventory, player, faction, tier, true, 3);
    manager.militaryView(inventory, player, faction, false);
    manager.installationsView(inventory, player, faction, true);
    manager.installationDetailView(player, faction, "fort");
    manager.installationDetailView(player, faction, "fort", inventory);
    manager.diplomacyView(inventory, player, faction, true);
    manager.diplomacyListView(inventory, player, faction, false);
    manager.attitudeView(inventory, player, faction, true);
    manager.relationView(inventory, player, faction, false);
    manager.electionView(player, faction);
    manager.taxView(player, faction);
    verify(manager.factionView).factionList(player);
    verify(manager.factionView).factionView(player, faction);
    verify(manager.lawView).lawView(player, faction, inventory);
    verify(manager.governmentView).governmentView(player, faction, inventory);
    verify(manager.governmentView).proposalView(player, faction, inventory);
    verify(manager.movementView).movementView(player, faction, movement, inventory);
    verify(manager.movementView).movementListView(player, faction, inventory);
    verify(manager.movementView).causesView(player, faction, movement, inventory);
    verify(manager.movementView).causeView(player, faction, movement, cause, inventory);
    verify(manager.tierTitleView).tierView(inventory, player, faction, true);
    verify(manager.tierTitleView).titleView(inventory, player, faction, false);
    verify(manager.tierTitleView).titleTypeView(inventory, player, faction, tier, true, 3);
    verify(manager.militaryView).militaryView(inventory, player, faction, false);
    verify(manager.installationView).installationsView(inventory, player, faction, true);
    verify(manager.installationView).installationDetailView(player, faction, "fort");
    verify(manager.installationView).installationDetailView(player, faction, "fort", inventory);
    verify(manager.relationView).diplomacyView(inventory, player, faction, true);
    verify(manager.relationView).diplomacyListView(inventory, player, faction, false);
    verify(manager.relationView).attitudeView(inventory, player, faction, true);
    verify(manager.relationView).relationView(inventory, player, faction, false);
    verify(manager.electionView).electionView(player, faction);
    verify(manager.taxView).taxView(player, faction);
  }

  @Test
  void publicGuildNavigationPreservesTheSelectedGuildContractAndLoan() {
    Inventory inventory = gui.inventory(null, 54, "Guild");
    Loan loan = mock(Loan.class);
    manager.guildList(player);
    manager.guildView(player, guild);
    manager.guildView(player, guild, inventory);
    manager.upgradeView(player, guild);
    manager.upgradeView(player, guild, inventory);
    manager.ledgerView(player, guild, inventory);
    manager.companyView(player, guild);
    manager.companyView(player, guild, inventory);
    manager.companySlotsView(player, guild, inventory);
    manager.companyRosterView(player, guild, inventory);
    manager.companyUpgradeView(player, guild, inventory);
    manager.contractListView(player, guild);
    manager.contractListView(player, guild, inventory);
    manager.contractDetailView(player, guild, "contract");
    manager.contractDetailView(player, guild, inventory, "contract");
    manager.mercenaryMarketList(player);
    manager.mercenaryMarketList(player, inventory);
    manager.loanMainView(player, guild);
    manager.loanMainView(player, guild, inventory);
    manager.loansGivenView(player, guild);
    manager.loansGivenView(player, guild, inventory);
    manager.loansTakenView(player, guild);
    manager.loansTakenView(player, guild, inventory);
    manager.loanDetailView(player, guild, loan, true, inventory);
    verify(manager.guildView).guildList(player);
    verify(manager.guildView).guildView(player, guild);
    verify(manager.guildView).guildView(player, guild, inventory);
    verify(manager.guildView).upgradeView(player, guild);
    verify(manager.guildView).upgradeView(player, guild, inventory);
    verify(manager.guildView).ledgerView(player, guild, inventory);
    verify(manager.companyView).companyView(player, guild);
    verify(manager.companyView).companyView(player, guild, inventory);
    verify(manager.companyView).slotsView(player, guild, inventory);
    verify(manager.companyView).rosterView(player, guild, inventory);
    verify(manager.companyView).companyUpgradeView(player, guild, inventory);
    verify(manager.contractView).listView(player, guild);
    verify(manager.contractView).listView(player, guild, inventory);
    verify(manager.contractView).detailView(player, guild, "contract");
    verify(manager.contractView).detailView(player, guild, inventory, "contract");
    verify(manager.mercenaryMarketView).marketList(player);
    verify(manager.mercenaryMarketView).marketList(player, inventory);
    verify(manager.loanView).loanMainView(player, guild);
    verify(manager.loanView).loanMainView(player, guild, inventory);
    verify(manager.loanView).loansGivenView(player, guild);
    verify(manager.loanView).loansGivenView(player, guild, inventory);
    verify(manager.loanView).loansTakenView(player, guild);
    verify(manager.loanView).loansTakenView(player, guild, inventory);
    verify(manager.loanView).loanDetailView(player, guild, loan, true, inventory);
  }

  @Test
  void publicWarNavigationPreservesWarParticipantAndPickerTargets() {
    Inventory inventory = gui.inventory(null, 54, "War");
    War war = mock(War.class);
    Participant participant = mock(Participant.class);
    Faction defender = mock(Faction.class);
    manager.warList(player);
    manager.warList(player, inventory);
    manager.populateWarList(inventory);
    manager.warView(inventory, player, war, false);
    manager.participantView(inventory, player, war, participant, true);
    manager.openDeclareWarGoalPicker(player, faction, defender);
    verify(manager.warView).warList(player);
    verify(manager.warView).warList(player, inventory);
    verify(manager.warView).populateWarList(inventory);
    verify(manager.warView).warView(inventory, player, war, false);
    verify(manager.warView).participantView(inventory, player, war, participant, true);
    verify(manager.declareWarView).openGoalPicker(player, faction, defender);
    manager.openCampaignView(player, null);
    manager.openCampaignView(player, war);
    verify(player, times(2)).sendMessage("§cWar not found.");
    verifyNoInteractions(manager.campaignView);
    when(war.isActive()).thenReturn(true);
    try (MockedStatic<WhitePeaceService> peace = mockStatic(WhitePeaceService.class);
        MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
      manager.openCampaignView(player, war);
      peace.verify(() -> WhitePeaceService.recalculateProposals(war));
      wars.verify(() -> WarManager.persist(war));
      verify(manager.campaignView).campaignView(player, war, true);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "FACTION_LIST,FACTION",
    "FACTION_VIEW,FACTION",
    "FACTION_GUILDS,FACTION",
    "GUILD_LIST,GUILD",
    "GUILD_VIEW,GUILD",
    "UPGRADE_VIEW,GUILD",
    "COMPANY_VIEW,COMPANY",
    "COMPANY_SLOTS_VIEW,COMPANY",
    "COMPANY_ROSTER_VIEW,COMPANY",
    "COMPANY_UPGRADE_VIEW,COMPANY",
    "CONTRACT_LIST_VIEW,CONTRACT",
    "CONTRACT_DETAIL_VIEW,CONTRACT",
    "MERCENARY_MARKET_LIST,MARKET",
    "LOAN_MAIN_VIEW,LOAN",
    "LOANS_GIVEN_VIEW,LOAN",
    "LOANS_TAKEN_VIEW,LOAN",
    "TAKEN_LOAN_DETAIL_VIEW,LOAN",
    "ISSUED_LOAN_DETAIL_VIEW,LOAN",
    "LAW_VIEW,LAW",
    "LAW_SELECT,LAW",
    "MILITARY_VIEW,MILITARY",
    "INSTALLATIONS_VIEW,INSTALLATION",
    "INSTALLATION_DETAIL_VIEW,INSTALLATION",
    "GOVERNMENT_VIEW,GOVERNMENT",
    "PROPOSAL_VIEW,GOVERNMENT",
    "PROPOSALS,GOVERNMENT",
    "LAW_PROPOSAL_VIEW,GOVERNMENT",
    "LAW_PROPOSAL_SELECT,GOVERNMENT",
    "TAX_PROPOSAL_VIEW,GOVERNMENT",
    "SPECIFIC_TAX_PROPOSAL_VIEW,GOVERNMENT",
    "POLITICAL_PROPOSAL_VIEW,GOVERNMENT",
    "WAR_PEACE_SELECT,GOVERNMENT",
    "COUNCIL_VIEW,GOVERNMENT",
    "COUNCIL_SELECT,GOVERNMENT",
    "FAVOUR_REPRESS_MAIN,GOVERNMENT",
    "FAVOUR_REPRESS_TYPE,GOVERNMENT",
    "FAVOUR_REPRESS_SELECT,GOVERNMENT",
    "FEE_PROPOSAL_VIEW,FEE",
    "FEE_CATEGORY_VIEW,FEE",
    "FEE_VEHICLE_VIEW,FEE",
    "MOVEMENT_VIEW,MOVEMENT",
    "MOVEMENT_LIST,MOVEMENT",
    "CAUSES_VIEW,MOVEMENT",
    "CAUSE_VIEW,MOVEMENT",
    "MOVEMENT_DEMANDS,MOVEMENT",
    "MOVEMENT_CRACKDOWN,MOVEMENT",
    "TARGET_SELECT,MOVEMENT",
    "DIPLOMACY_VIEW,RELATION",
    "DIPLOMACY_LIST,RELATION",
    "ATTITUDE_VIEW,RELATION",
    "RELATION_VIEW,RELATION",
    "TRADE_AGREEMENT_VIEW,RELATION",
    "TREATY_VIEW,RELATION",
    "ELECTION_VIEW,ELECTION",
    "ELECTION_VOTING_VIEW,ELECTION",
    "TIER_VIEW,TITLE",
    "TITLE_VIEW,TITLE",
    "TITLE_TYPE_VIEW,TITLE",
    "WAR_LIST,WAR",
    "TAX_VIEW,TAX",
    "TAX_VIEW_SPECIFIC,TAX"
  })
  void topMenuButtonsReachExactlyTheirOwningView(SFGUI type, String route) {
    Inventory inventory = open(type, false);
    inventory.setItem(4, new ItemStack(Material.PAPER));
    InventoryClickEvent click = gui.click(player, 4);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    switch (route) {
      case "FACTION" -> verify(manager.factionView).click(click, inventory, player);
      case "GUILD" -> verify(manager.guildView).click(click, inventory, player);
      case "COMPANY" -> verify(manager.companyView).click(click, inventory, player);
      case "CONTRACT" -> verify(manager.contractView).click(click, inventory, player);
      case "MARKET" -> verify(manager.mercenaryMarketView).click(click, inventory, player);
      case "LOAN" -> verify(manager.loanView).click(click, inventory, player);
      case "LAW" -> verify(manager.lawView).click(click, inventory, player);
      case "MILITARY" -> verify(manager.militaryView).click(click, inventory, player);
      case "INSTALLATION" -> verify(manager.installationView).click(click, inventory, player);
      case "GOVERNMENT" -> verify(manager.governmentView).click(click, inventory, player);
      case "FEE" -> verify(manager.vehicleFeeView).click(click, inventory, player);
      case "MOVEMENT" -> verify(manager.movementView).click(click, inventory, player);
      case "RELATION" -> verify(manager.relationView).click(click, inventory, player);
      case "ELECTION" -> verify(manager.electionView).click(click, inventory, player);
      case "TITLE" -> verify(manager.tierTitleView).click(click, inventory, player);
      case "WAR" -> verify(manager.warView).click(click, inventory, player);
      case "TAX" -> verify(manager.taxView).click(click, inventory, player);
      default -> fail("Unspecified owning view: " + route);
    }
    verifyNoMoreInteractions(views());
  }

  @ParameterizedTest
  @MethodSource("warHolders")
  void warHolderButtonsAreRoutedToTheirSpecificView(InventoryHolder holder, String route) {
    Inventory inventory = gui.inventory(holder, 54, "War menu");
    inventory.setItem(0, new ItemStack(Material.PAPER));
    player.openInventory(inventory);
    InventoryClickEvent click = gui.click(player, 0);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    switch (route) {
      case "war" -> verify(manager.warView).click(click, inventory, player);
      case "campaign" -> verify(manager.campaignView).click(click, inventory, player);
      case "installation" ->
          verify(manager.campaignInstallationPickView).click(click, inventory, player);
      case "raid" -> verify(manager.campaignRaidLaunchView).click(click, inventory, player);
      case "declare" -> verify(manager.declareWarView).click(click, inventory, player);
      default -> fail(route);
    }
    verifyNoMoreInteractions(views());
  }

  static Stream<Arguments> warHolders() {
    return Stream.of(
        Arguments.of(new WarInventoryHolder(7, SFGUI.WAR_VIEW), "war"),
        Arguments.of(new WarInventoryHolder(7, SFGUI.WAR_COUNTER_GOAL), "war"),
        Arguments.of(new CampaignInventoryHolder(7, SFGUI.CAMPAIGN_VIEW), "campaign"),
        Arguments.of(
            new CampaignInventoryHolder(7, SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW), "installation"),
        Arguments.of(new CampaignRaidLaunchHolder(7, "fort"), "raid"),
        Arguments.of(new SFCombinedInventoryHolder(7, "realm", SFGUI.PARTICIPANT_VIEW), "war"),
        Arguments.of(
            new SFCombinedInventoryHolder(7, "realm", SFGUI.MERCENARY_ENGAGEMENT_LIST), "war"),
        Arguments.of(new DeclareWarHolder("realm", "enemy", SFGUI.WAR_DECLARE_GOAL), "declare"));
  }

  @ParameterizedTest
  @ValueSource(ints = {-999, 54, 70})
  void playerInventoryAndOutsideClicksCannotDispatchFactionMenuButtons(int rawSlot) {
    open(SFGUI.FACTION_VIEW, false);
    player.getInventory().setItem(0, new ItemStack(Material.PAPER));
    InventoryClickEvent click = gui.click(player, rawSlot);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    verifyNoInteractions(views());
  }

  @Test
  void emptySlotsAndReadOnlyPlayerLedgersCannotDispatchActions() {
    open(SFGUI.FACTION_VIEW, false);
    InventoryClickEvent empty = gui.click(player, 0);
    manager.clickButton(empty);
    assertTrue(empty.isCancelled());
    Inventory ledger = open(SFGUI.PLAYER_LEDGER_VIEW, false);
    ledger.setItem(0, new ItemStack(Material.PAPER));
    InventoryClickEvent readOnly = gui.click(player, 0);
    manager.clickButton(readOnly);
    assertTrue(readOnly.isCancelled());
    verifyNoInteractions(views());
  }

  @Test
  void missingPrivateMenuOwnerClosesTheInventoryBeforeDispatch() {
    Inventory inventory =
        gui.inventory(new SFInventoryHolder("deleted", SFGUI.MILITARY_VIEW), 54, "Military");
    inventory.setItem(0, new ItemStack(Material.PAPER));
    player.openInventory(inventory);
    InventoryClickEvent click = gui.click(player, 0);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    verify(player).closeInventory();
    verifyNoInteractions(views());
  }

  @ParameterizedTest
  @CsvSource({
    "ATTITUDE_VIEW,DIPLOMACY",
    "DIPLOMACY_LIST,FACTION",
    "DIPLOMACY_VIEW,FACTION",
    "FACTION_VIEW,FACTION_LIST",
    "FACTION_GUILDS,FACTION",
    "GUILD_VIEW,GUILD_LIST",
    "MILITARY_VIEW,FACTION",
    "INSTALLATIONS_VIEW,FACTION",
    "INSTALLATION_DETAIL_VIEW,INSTALLATIONS",
    "RELATION_VIEW,DIPLOMACY",
    "TRADE_AGREEMENT_VIEW,DIPLOMACY",
    "TREATY_VIEW,DIPLOMACY",
    "TIER_VIEW,FACTION",
    "TITLE_VIEW,FACTION",
    "TITLE_TYPE_VIEW,TITLE",
    "LAW_VIEW,FACTION",
    "LAW_SELECT,LAW",
    "GOVERNMENT_VIEW,FACTION",
    "STABILITY_VIEW,GOVERNMENT",
    "PROPOSAL_VIEW,GOVERNMENT",
    "POLITICAL_PROPOSAL_VIEW,PROPOSAL",
    "WAR_PEACE_SELECT,POLITICAL",
    "LAW_PROPOSAL_VIEW,PROPOSAL",
    "LAW_PROPOSAL_SELECT,LAW_PROPOSAL",
    "TAX_PROPOSAL_VIEW,PROPOSAL",
    "SPECIFIC_TAX_PROPOSAL_VIEW,TAX_PROPOSAL",
    "FEE_PROPOSAL_VIEW,FEE",
    "FEE_CATEGORY_VIEW,FEE",
    "FEE_VEHICLE_VIEW,FEE",
    "PROPOSALS,GOVERNMENT",
    "COUNCIL_VIEW,GOVERNMENT",
    "COUNCIL_SELECT,COUNCIL",
    "MOVEMENT_VIEW,MOVEMENTS",
    "MOVEMENT_LIST,GOVERNMENT",
    "CAUSES_VIEW,MOVEMENT",
    "CAUSE_VIEW,CAUSES",
    "LEDGER_VIEW,FACTION",
    "TAX_VIEW_SPECIFIC,TAX",
    "TAX_VIEW,FACTION",
    "UPGRADE_VIEW,GUILD",
    "COMPANY_VIEW,GUILD",
    "COMPANY_SLOTS_VIEW,COMPANY",
    "COMPANY_ROSTER_VIEW,COMPANY",
    "COMPANY_UPGRADE_VIEW,COMPANY",
    "CONTRACT_LIST_VIEW,COMPANY",
    "CONTRACT_DETAIL_VIEW,CONTRACTS",
    "LOAN_MAIN_VIEW,GUILD",
    "LOANS_GIVEN_VIEW,LOANS",
    "LOANS_TAKEN_VIEW,LOANS",
    "ISSUED_LOAN_DETAIL_VIEW,GIVEN",
    "TAKEN_LOAN_DETAIL_VIEW,TAKEN",
    "FAVOUR_REPRESS_SELECT,FAVOUR_TYPE",
    "FAVOUR_REPRESS_TYPE,FAVOUR_MAIN",
    "FAVOUR_REPRESS_MAIN,GOVERNMENT"
  })
  void backButtonsReturnToTheirDocumentedParentMenu(SFGUI type, String destination) {
    Movement movement = mock(Movement.class);
    when(movement.getFaction()).thenReturn(faction);
    factions.when(() -> FactionManager.getMovementById("realm")).thenReturn(movement);
    Inventory inventory = open(type, false);
    inventory.setItem(53, manager.createBackButton(type));
    InventoryClickEvent click = gui.click(player, 53);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    switch (destination) {
      case "DIPLOMACY" -> verify(manager.relationView).diplomacyView(null, player, faction, true);
      case "FACTION" -> verify(manager.factionView).factionView(player, faction);
      case "FACTION_LIST" -> verify(manager.factionView).factionList(player);
      case "GUILD_LIST" -> verify(manager.guildView).guildList(player);
      case "INSTALLATIONS" ->
          verify(manager.installationView).installationsView(null, player, faction, true);
      case "TITLE" -> verify(manager.tierTitleView).titleView(null, player, faction, true);
      case "LAW" -> verify(manager.lawView).lawView(player, faction, null);
      case "GOVERNMENT" -> verify(manager.governmentView).governmentView(player, faction, null);
      case "PROPOSAL" -> verify(manager.governmentView).proposalView(player, faction, null);
      case "POLITICAL" ->
          verify(manager.governmentView).politicalProposalView(player, faction, null);
      case "LAW_PROPOSAL" -> verify(manager.governmentView).lawProposalView(player, faction, null);
      case "TAX_PROPOSAL" -> verify(manager.governmentView).taxProposalView(player, faction, null);
      case "FEE" ->
          verify(manager.vehicleFeeView)
              .back(player, faction, (SFInventoryHolder) inventory.getHolder());
      case "COUNCIL" -> verify(manager.governmentView).councilView(player, faction, null);
      case "MOVEMENTS" -> verify(manager.movementView).movementListView(player, faction, null);
      case "MOVEMENT" -> verify(manager.movementView).movementView(player, faction, movement, null);
      case "CAUSES" -> verify(manager.movementView).causesView(player, faction, movement, null);
      case "TAX" -> verify(manager.taxView).taxView(player, faction);
      case "GUILD" -> verify(manager.guildView).guildView(player, guild);
      case "COMPANY" -> verify(manager.companyView).companyView(player, guild);
      case "CONTRACTS" -> verify(manager.contractView).listView(player, guild);
      case "LOANS" -> verify(manager.loanView).loanMainView(player, guild);
      case "GIVEN" -> verify(manager.loanView).loansGivenView(player, guild);
      case "TAKEN" -> verify(manager.loanView).loansTakenView(player, guild);
      case "FAVOUR_TYPE" ->
          verify(manager.governmentView).favourRepressTypeView(player, faction, false, null);
      case "FAVOUR_MAIN" ->
          verify(manager.governmentView).favourRepressMainView(player, faction, null);
      default -> fail(destination);
    }
    verify(player).playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
  }

  @Test
  void guildAndLedgerBackButtonsRetainTheirEntryContext() {
    Inventory guildMenu = open(SFGUI.GUILD_VIEW, true);
    guildMenu.setItem(53, manager.createBackButton(SFGUI.GUILD_VIEW));
    manager.clickButton(gui.click(player, 53));
    verify(manager.factionView).factionGuildsView(player, faction, null);
    Inventory ledger = open(SFGUI.LEDGER_VIEW, true);
    ledger.setItem(53, manager.createBackButton(SFGUI.LEDGER_VIEW));
    manager.clickButton(gui.click(player, 53));
    verify(manager.guildView).guildView(player, guild);
    factions.when(() -> FactionManager.getByMember("Leader")).thenReturn(faction);
    Inventory diplomacy = open(SFGUI.DIPLOMACY_VIEW, false);
    diplomacy.setItem(53, manager.createBackButton(SFGUI.DIPLOMACY_VIEW));
    manager.clickButton(gui.click(player, 53));
    verify(manager.relationView).diplomacyListView(null, player, faction, true);
  }

  @Test
  void ledgerClosesIfItsGuildLosesTheFactionAfterTheAccessSnapshot() {
    Guild accessSnapshot = mock(Guild.class);
    when(accessSnapshot.getFaction()).thenReturn(faction);
    // Navigation performs a fresh registry lookup after the access check. Model an orphaned
    // replacement between those lookups, without bypassing the actual permission predicate.
    factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(accessSnapshot, guild);
    when(guild.getFaction()).thenReturn(null);
    Inventory inventory = open(SFGUI.LEDGER_VIEW, false);
    inventory.setItem(53, manager.createBackButton(SFGUI.LEDGER_VIEW));
    InventoryClickEvent click = gui.click(player, 53);
    manager.clickButton(click);
    assertTrue(click.isCancelled());
    verify(player).closeInventory();
    verifyNoInteractions(manager.factionView, manager.guildView);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void politicalMovementPickerReturnsToItsCauseOrSurvivingGovernment(boolean survivingCause) {
    Movement movement = mock(Movement.class);
    Cause cause = mock(Cause.class);
    when(government.getMovementByMember("Leader")).thenReturn(movement);
    when(movement.getCauses()).thenReturn(survivingCause ? List.of(cause) : List.of());
    Inventory inventory = open(SFGUI.WAR_PEACE_SELECT, true);
    when(cause.getMovement()).thenReturn(movement);
    when(manager.governmentView.displayedWarPeaceCause(player, faction, inventory))
        .thenReturn(survivingCause ? cause : null);
    inventory.setItem(53, manager.createBackButton(SFGUI.WAR_PEACE_SELECT));
    manager.clickButton(gui.click(player, 53));
    if (survivingCause)
      verify(manager.movementView).causeView(player, faction, movement, cause, null);
    else verify(manager.governmentView).governmentView(player, faction, null);
  }

  @ParameterizedTest
  @CsvSource({"true,25,2", "false,0,0"})
  void capitalConfirmationDisplaysCostBalanceAndLosses(boolean rename, double cost, int losses) {
    when(faction.getBank()).thenReturn(new Bank(guild, 100, mock(Chunk.class)));
    manager.confirmCapitalMoveView(player, faction, rename, cost, losses);
    Inventory inventory = player.getOpenInventory().getTopInventory();
    assertEquals("§7Confirm Action", player.getOpenInventory().getTitle());
    assertEquals(27, inventory.getSize());
    ItemMeta info = inventory.getItem(13).getItemMeta();
    assertEquals(
        rename ? "§eRename faction capital?" : "§eMove faction capital?", info.getDisplayName());
    assertTrue(info.getLore().stream().anyMatch(line -> line.contains(cost > 0 ? "25" : "Free")));
    if (cost > 0)
      assertTrue(
          info.getLore().stream()
              .anyMatch(line -> line.contains("Faction bank:") && line.contains("100")));
    if (losses > 0) assertTrue(info.getLore().contains("§cYou will lose 2 provinces"));
    assertEquals("1", gui.data(inventory.getItem(11), "setcapital"));
    assertEquals(Material.RED_CONCRETE, inventory.getItem(15).getType());
  }

  @Test
  void capitalConfirmationOmitsUnknownBankBalance() {
    manager.confirmCapitalMoveView(player, faction, false, 50, 0);
    List<String> lore =
        player.getOpenInventory().getTopInventory().getItem(13).getItemMeta().getLore();
    assertEquals(List.of("§7Cost: §e50.00d §7from the faction bank"), lore);
  }

  @ParameterizedTest
  @EnumSource(QueueCancelPayload.Type.class)
  void queueConfirmationCarriesTheOriginalOwnerAndCancelsExactlyThatEntry(
      QueueCancelPayload.Type type) {
    QueueState queue = queue(type, true);
    manager.openQueueCancelConfirm(player, faction, queue.payload(), "Cancel queue entry");
    Inventory inventory = player.getOpenInventory().getTopInventory();
    assertEquals("Cancel queue entry", inventory.getItem(13).getItemMeta().getDisplayName());
    assertEquals(queue.payload(), gui.data(inventory.getItem(11), "queue_cancel"));
    assertEquals(queue.payload(), gui.data(inventory.getItem(15), "queue_cancel"));
    assertSame(faction, manager.confirming.get(player));
    manager.clickButton(gui.click(player, 11));
    queue.verifyCancellation();
    verify(player).sendMessage("§aQueue item cancelled.");
    assertFalse(manager.confirming.containsKey(player));
    verifyQueueParent(type);
  }

  @ParameterizedTest
  @EnumSource(QueueCancelPayload.Type.class)
  void cancellingQueueConfirmationKeepsTheQueueAndReturnsToItsOwner(QueueCancelPayload.Type type) {
    QueueState queue = queue(type, true);
    manager.openQueueCancelConfirm(player, faction, queue.payload(), "Cancel queue entry");
    manager.clickButton(gui.click(player, 15));
    queue.verifyUnchanged();
    assertFalse(manager.confirming.containsKey(player));
    verifyQueueParent(type);
    verify(player, never()).sendMessage("§aQueue item cancelled.");
  }

  @ParameterizedTest
  @EnumSource(QueueCancelPayload.Type.class)
  void aQueueThatChangedWhileTheConfirmationWasOpenReportsFailure(QueueCancelPayload.Type type) {
    QueueState queue = queue(type, false);
    manager.openQueueCancelConfirm(player, faction, queue.payload(), "Cancel queue entry");
    manager.clickButton(gui.click(player, 11));
    queue.verifyCancellation();
    verify(player).sendMessage("§cCould not cancel queue item.");
    assertFalse(manager.confirming.containsKey(player));
    verifyQueueParent(type);
  }

  @ParameterizedTest
  @EnumSource(QueueCancelPayload.Type.class)
  void disappearingQueueOwnerDoesNotCancelOrNavigateToADifferentOwner(
      QueueCancelPayload.Type type) {
    QueueState queue = queue(type, true);
    manager.openQueueCancelConfirm(player, faction, queue.payload(), "Cancel queue entry");
    factions.when(() -> FactionManager.getByString("realm")).thenReturn(null);
    factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(null);
    manager.clickButton(gui.click(player, 11));
    queue.verifyUnchanged();
    verify(player).sendMessage("§cCould not cancel queue item.");
    assertFalse(manager.confirming.containsKey(player));
    verifyNoInteractions(views());
  }

  @Test
  void malformedQueuePayloadIsDiscardedWithoutDomainMutation() {
    manager.openQueueCancelConfirm(player, faction, "not a queue", "Cancel queue entry");
    manager.clickButton(gui.click(player, 11));
    assertFalse(manager.confirming.containsKey(player));
    verifyNoInteractions(views());
    verify(faction, never()).getMilitary();
  }

  @ParameterizedTest
  @EnumSource(QueueCancelPayload.Type.class)
  void queueCancellationRechecksLeadershipBeforeMutation(QueueCancelPayload.Type type) {
    QueueState queue = queue(type, true);
    manager.openQueueCancelConfirm(player, faction, queue.payload(), "Cancel queue entry");
    when(faction.getLeader()).thenReturn("NewLeader");
    when(faction.isLeader("Leader")).thenReturn(false);
    when(guild.isLeader(player)).thenReturn(false);
    when(queue.company().isLeader("Leader")).thenReturn(false);
    manager.clickButton(gui.click(player, 11));
    queue.verifyUnchanged();
    assertFalse(manager.confirming.containsKey(player));
  }

  @ParameterizedTest
  @EnumSource(
      value = QueueCancelPayload.Type.class,
      names = {"COMPANY_SLOT", "COMPANY_UPGRADE"})
  void disappearingCompanyDoesNotCancelItsFormerQueue(QueueCancelPayload.Type type) {
    QueueState queue = queue(type, true);
    manager.openQueueCancelConfirm(player, faction, queue.payload(), "Cancel queue entry");
    when(guild.getCompany()).thenReturn(null);
    manager.clickButton(gui.click(player, 11));
    queue.verifyUnchanged();
    verify(player).sendMessage("§cCould not cancel queue item.");
    assertFalse(manager.confirming.containsKey(player));
  }

  @ParameterizedTest
  @ValueSource(ints = {-999, 27})
  void confirmationIgnoresButtonsInThePlayersInventoryOrOutsideIt(int rawSlot) {
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, "campaign_push", "7");
    player.getInventory().setItem(0, manager.createButton("confirm", "campaign_push", "7"));
    InventoryClickEvent click = gui.click(player, rawSlot);
    manager.clickButton(click);
    assertEquals(rawSlot >= 0, click.isCancelled());
    verifyNoInteractions(manager.campaignView);
    assertSame(faction, manager.confirming.get(player));
  }

  @Test
  void unregisteredAndNonActionItemsCannotConfirmAnything() {
    manager.confirmView(player, faction, "campaign_push", "7");
    manager.clickButton(gui.click(player, 11));
    assertTrue(manager.confirming.isEmpty());
    manager.confirming.put(player, faction);
    Inventory inventory = player.getOpenInventory().getTopInventory();
    inventory.setItem(11, new ItemStack(Material.PAPER));
    manager.clickButton(gui.click(player, 11));
    inventory.setItem(11, manager.getFiller(Material.PAPER));
    manager.clickButton(gui.click(player, 11));
    verifyNoInteractions(views());
    assertSame(faction, manager.confirming.get(player));
  }

  @ParameterizedTest
  @MethodSource("campaignActions")
  void campaignConfirmationsForwardTheExactActionTargetAndDecision(
      String action, boolean confirmed) {
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, action, "campaign-7");
    manager.clickButton(gui.click(player, confirmed ? 11 : 15));
    verify(manager.campaignView).handleConfirm(player, action, "campaign-7", confirmed);
    verifyNoMoreInteractions(manager.campaignView);
  }

  static Stream<Arguments> campaignActions() {
    return Stream.of(
            "campaign_push",
            "campaign_hold",
            "campaign_attack",
            "campaign_loser_peace",
            "campaign_retreat",
            "campaign_surrender",
            "campaign_accept_peace")
        .flatMap(action -> Stream.of(Arguments.of(action, true), Arguments.of(action, false)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void retreatAndCapitalDecisionsReachTheirOwningConfirmationServices(boolean confirmed) {
    manager.confirming.put(player, faction);
    manager.confirmBattleRetreatView(player, "battle-7");
    Inventory retreat = player.getOpenInventory().getTopInventory();
    assertEquals("§eRetreat from battle?", retreat.getItem(13).getItemMeta().getDisplayName());
    assertEquals(
        List.of("§7Your side will lose the battle.", "§7Casualties already taken will apply."),
        retreat.getItem(13).getItemMeta().getLore());
    assertEquals("battle-7", gui.data(retreat.getItem(11), "warband_battle_retreat"));
    try (MockedStatic<ConfirmHandler> handler = mockStatic(ConfirmHandler.class)) {
      manager.clickButton(gui.click(player, confirmed ? 11 : 15));
      handler.verify(() -> ConfirmHandler.handleConfirm(player, confirmed));
    }
    manager.confirmView(player, faction, "setcapital", "1");
    try (MockedStatic<Cache> cache = mockStatic(Cache.class);
        MockedStatic<CapitalMovePrompt> capital = mockStatic(CapitalMovePrompt.class)) {
      cache.when(() -> Cache.requireProvinces(player)).thenReturn(true);
      manager.clickButton(gui.click(player, confirmed ? 11 : 15));
      capital.verify(() -> CapitalMovePrompt.handleConfirm(player, confirmed));
    }
  }

  @Test
  void disabledProvincesCloseCapitalConfirmationBeforeItCanMoveAnything() {
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, "setcapital", "1");
    try (MockedStatic<Cache> cache = mockStatic(Cache.class);
        MockedStatic<CapitalMovePrompt> capital = mockStatic(CapitalMovePrompt.class)) {
      cache.when(() -> Cache.requireProvinces(player)).thenReturn(false);
      manager.clickButton(gui.click(player, 11));
      capital.verifyNoInteractions();
      verify(player).closeInventory();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void movementEndingConfirmationKeepsTheMovementIdAndDecision(boolean confirmed) {
    Movement movement = mock(Movement.class);
    when(movement.getId()).thenReturn("movement");
    when(movement.getFaction()).thenReturn(faction);
    when(movement.getSupporters())
        .thenReturn(new net.tfminecraft.simplefactions.government.movement.Pool());
    manager.movementView.creator = new MovementCreator();
    manager.confirming.put(player, faction);
    manager.confirmEndMovementView(player, movement);
    Inventory inventory = player.getOpenInventory().getTopInventory();
    assertTrue(inventory.getItem(13).getItemMeta().getDisplayName().contains("End this movement?"));
    assertEquals("movement", gui.data(inventory.getItem(11), "end_movement"));
    manager.clickButton(gui.click(player, confirmed ? 11 : 15));
    verify(manager.movementView).handleEndConfirm(player, "movement", confirmed);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void regimentSizeChangeRequiresConfirmationAndKeepsTheSelectedRegiment(boolean confirmed) {
    Military military = mock(Military.class);
    Regiment regiment = mock(Regiment.class);
    when(faction.getMilitary()).thenReturn(military);
    when(military.getRegiment("archers")).thenReturn(regiment);
    when(regiment.getName()).thenReturn("Archers");
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, "regiment", "archers");
    manager.clickButton(gui.click(player, confirmed ? 11 : 15));
    verify(regiment, times(confirmed ? 1 : 0)).sizeDecrease();
    if (confirmed) verify(player).sendMessage("§cDecreased size of Archers");
    verify(manager.militaryView).militaryView(null, player, faction, true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"faction", "leader", "regiment"})
  void staleRegimentConfirmationCannotChangeTheRegiment(String missing) {
    Military military = mock(Military.class);
    Regiment regiment = mock(Regiment.class);
    when(faction.getMilitary()).thenReturn(military);
    when(military.getRegiment("archers")).thenReturn(regiment);
    manager.confirming.put(player, missing.equals("faction") ? null : faction);
    if (missing.equals("leader")) when(faction.getLeader()).thenReturn("NewLeader");
    if (missing.equals("regiment")) when(military.getRegiment("archers")).thenReturn(null);
    manager.confirmView(player, faction, "regiment", "archers");
    manager.clickButton(gui.click(player, 11));
    verify(regiment, never()).sizeDecrease();
    verifyNoInteractions(manager.militaryView);
  }

  @Test
  void allowedDissolutionReturnsToItsSuccessorAndClearsTheConfirmation() {
    GuildHandler handler = new GuildHandler(faction);
    when(faction.getGuildHandler()).thenReturn(handler);
    when(faction.canDissolve()).thenReturn(true);
    Faction successor = mock(Faction.class);
    List<Faction> subjects = List.of(mock(Faction.class));
    List<Guild> guilds = handler.getGuilds();
    when(faction.getSubjects()).thenReturn(subjects);
    when(faction.dissolve(subjects, guilds)).thenReturn(successor);
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, "dissolve", "true");
    manager.clickButton(gui.click(player, 11));
    verify(faction).dissolve(subjects, guilds);
    assertFalse(manager.confirming.containsKey(player));
    verify(manager.factionView).factionView(player, successor);
  }

  @ParameterizedTest
  @CsvSource({"true,false,11", "false,true,11", "true,true,15"})
  void cancelledOrIneligibleDissolutionKeepsTheFaction(String payload, boolean eligible, int slot) {
    when(faction.canDissolve()).thenReturn(eligible);
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, "dissolve", payload);
    manager.clickButton(gui.click(player, slot));
    verify(faction, never()).dissolve(anyList(), anyList());
    if (slot == 15) verify(manager.factionView).factionView(player, faction);
    else verifyNoInteractions(manager.factionView);
  }

  @ParameterizedTest
  @CsvSource({
    "installation,true",
    "installation,false",
    "installation_upgrade,true",
    "installation_upgrade,false",
    "installation_cancel_upgrade,true",
    "installation_cancel_upgrade,false"
  })
  void installationConfirmationReportsDomainResultAndReturnsToInstallations(
      String action, boolean success) {
    InstallationHandler handler = mock(InstallationHandler.class);
    when(faction.getInstallationHandler()).thenReturn(handler);
    when(handler.deconstruct("fort"))
        .thenReturn(new ConstructResult(success, "Deconstruction result"));
    when(handler.upgrade("fort")).thenReturn(new ConstructResult(success, "Upgrade result"));
    when(handler.cancelPendingUpgrade("fort")).thenReturn(success);
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, action, "fort");
    manager.clickButton(gui.click(player, 11));
    switch (action) {
      case "installation" -> {
        verify(handler).deconstruct("fort");
        verify(player).sendMessage("Deconstruction result");
      }
      case "installation_upgrade" -> {
        verify(handler).upgrade("fort");
        verify(player).sendMessage("Upgrade result");
      }
      case "installation_cancel_upgrade" -> {
        verify(handler).cancelPendingUpgrade("fort");
        verify(player)
            .sendMessage(
                success
                    ? "§aCancelled upgrade for installation §ffort"
                    : "§cNo pending upgrade for §ffort");
      }
      default -> fail(action);
    }
    verifyNoMoreInteractions(handler);
    verify(manager.installationView).installationsView(null, player, faction, true);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"installation", "installation_upgrade", "installation_cancel_upgrade", "dissolve"})
  void destructiveConfirmationRechecksLeadershipBeforeMutation(String action) {
    InstallationHandler installations = mock(InstallationHandler.class);
    when(faction.getInstallationHandler()).thenReturn(installations);
    when(installations.deconstruct("fort")).thenReturn(ConstructResult.ok("Removed"));
    when(installations.upgrade("fort")).thenReturn(ConstructResult.ok("Upgrade queued"));
    when(installations.cancelPendingUpgrade("fort")).thenReturn(true);
    when(faction.getGuildHandler()).thenReturn(new GuildHandler(faction));
    when(faction.canDissolve()).thenReturn(true);
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, action, action.equals("dissolve") ? "true" : "fort");
    when(faction.getLeader()).thenReturn("NewLeader");
    when(faction.isLeader("Leader")).thenReturn(false);
    manager.clickButton(gui.click(player, 11));
    verify(installations, never()).deconstruct(anyString());
    verify(installations, never()).upgrade(anyString());
    verify(installations, never()).cancelPendingUpgrade(anyString());
    verify(faction, never()).dissolve(anyList(), anyList());
  }

  @ParameterizedTest
  @CsvSource({
    "installation,false",
    "installation,true",
    "installation_upgrade,false",
    "installation_cancel_upgrade,false"
  })
  void cancellingInstallationConfirmationPreservesItAndReturnsToTheEntryPage(
      String action, boolean fromCommand) {
    InstallationHandler handler = mock(InstallationHandler.class);
    when(faction.getInstallationHandler()).thenReturn(handler);
    manager.confirming.put(player, faction);
    if (fromCommand) manager.installationConfirmFromCommand.put(player, true);
    manager.confirmView(player, faction, action, "fort");
    manager.clickButton(gui.click(player, 15));
    verifyNoInteractions(handler);
    if (fromCommand)
      verify(manager.installationView).installationsView(null, player, faction, true);
    else verify(manager.installationView).installationDetailView(player, faction, "fort");
    assertFalse(manager.installationConfirmFromCommand.containsKey(player));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void companyFoundingSubmitsTheOriginalNameAndDisplaysTheServiceResult(boolean success) {
    manager.companyView.creator = new CompanyCreator();
    manager.confirmCompanyFoundView(player, guild, "Silver Shields");
    Inventory inventory = player.getOpenInventory().getTopInventory();
    assertTrue(inventory.getItem(13).getItemMeta().getDisplayName().contains("Silver Shields"));
    assertEquals("guild", gui.data(inventory.getItem(11), "company_found"));
    assertEquals("Silver Shields", manager.pendingCompanyFounds.get(player));
    assertSame(faction, manager.confirming.get(player));
    try (MockedStatic<MercenaryCompanyService> service =
        mockStatic(MercenaryCompanyService.class)) {
      service
          .when(() -> MercenaryCompanyService.requestFormation(guild, "Leader", "Silver Shields"))
          .thenReturn(
              new MercenaryResult(success, success ? "Charter started" : "Not enough funds"));
      manager.clickButton(gui.click(player, 11));
      service.verify(
          () -> MercenaryCompanyService.requestFormation(guild, "Leader", "Silver Shields"));
    }
    assertFalse(manager.pendingCompanyFounds.containsKey(player));
    assertFalse(manager.confirming.containsKey(player));
    verify(player).sendMessage(success ? "§aCharter started" : "§cNot enough funds");
    if (success) verify(manager.companyView).companyView(player, guild);
    else verify(player).closeInventory();
  }

  @Test
  void cancellingCompanyFoundingDoesNotRequestOrChargeForTheCompany() {
    manager.companyView.creator = new CompanyCreator();
    manager.confirmCompanyFoundView(player, guild, "Silver Shields");
    try (MockedStatic<MercenaryCompanyService> service =
        mockStatic(MercenaryCompanyService.class)) {
      manager.clickButton(gui.click(player, 15));
      service.verifyNoInteractions();
    }
    assertFalse(manager.pendingCompanyFounds.containsKey(player));
    assertFalse(manager.confirming.containsKey(player));
    verify(player).sendMessage("§7Company founding cancelled. Nothing was charged.");
    verify(player).closeInventory();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void lostCompanyNameOrGuildDiscardsTheStaleFoundingPrompt(boolean missingGuild) {
    manager.companyView.creator = new CompanyCreator();
    manager.confirmCompanyFoundView(player, guild, "Silver Shields");
    if (missingGuild)
      factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(null);
    else manager.pendingCompanyFounds.remove(player);
    try (MockedStatic<MercenaryCompanyService> service =
        mockStatic(MercenaryCompanyService.class)) {
      manager.clickButton(gui.click(player, 11));
      service.verifyNoInteractions();
    }
    assertFalse(manager.pendingCompanyFounds.containsKey(player));
    assertFalse(manager.confirming.containsKey(player));
    verify(player).closeInventory();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void warDeclarationConfirmationKeepsItsOriginalRequestAndDecision(boolean confirmed) {
    Faction defender = mock(Faction.class);
    when(defender.getId()).thenReturn("enemy");
    WarDeclareRequest request =
        WarDeclareRequest.of(
            faction, defender, net.tfminecraft.simplefactions.war.enums.WarGoalType.WAR);
    ItemStack summary = manager.getFiller(Material.IRON_SWORD);
    manager.declareWarView.creator = mock(DeclareWarCreator.class);
    when(manager.declareWarView.creator.createConfirmSummaryItem(request)).thenReturn(summary);
    manager.confirmWarDeclareView(player, request);
    Inventory inventory = player.getOpenInventory().getTopInventory();
    assertSame(summary, inventory.getItem(13));
    assertEquals("enemy", gui.data(inventory.getItem(11), "war_declare"));
    assertSame(request, manager.pendingWarDeclares.get(player));
    assertSame(faction, manager.confirming.get(player));
    manager.clickButton(gui.click(player, confirmed ? 11 : 15));
    verify(manager.declareWarView).handleConfirm(player, request, confirmed);
    assertFalse(manager.pendingWarDeclares.containsKey(player));
    assertFalse(manager.confirming.containsKey(player));
  }

  @Test
  void missingWarRequestIsDiscardedWithoutDeclaringWar() {
    manager.confirming.put(player, faction);
    manager.confirmView(player, faction, "war_declare", "enemy");
    manager.clickButton(gui.click(player, 11));
    assertFalse(manager.confirming.containsKey(player));
    verifyNoInteractions(manager.declareWarView);
  }

  @Test
  void espionageMenuAndReportedInformationUseTheirRestrictedDispatchers() {
    Inventory inventory = open(SFGUI.SPECIAL_POSITIONS, false);
    inventory.setItem(0, new ItemStack(Material.PAPER));
    InventoryClickEvent click = gui.click(player, 0);
    SFInventoryHolder holder = (SFInventoryHolder) inventory.getHolder();
    try (MockedStatic<EspionageView> espionage = mockStatic(EspionageView.class)) {
      espionage.when(() -> EspionageView.handles(SFGUI.SPECIAL_POSITIONS)).thenReturn(true);
      manager.clickButton(click);
      espionage.verify(() -> EspionageView.click(click, player, holder, manager));
    }
    assertTrue(click.isCancelled());
    inventory = open(SFGUI.MILITARY_VIEW, false);
    inventory.setItem(0, new ItemStack(Material.PAPER));
    SFInventoryHolder reportedHolder = (SFInventoryHolder) inventory.getHolder();
    reportedHolder.markReported();
    InventoryClickEvent reportedClick = gui.click(player, 0);
    try (MockedStatic<net.tfminecraft.simplefactions.espionage.EspionageService> access =
            mockStatic(net.tfminecraft.simplefactions.espionage.EspionageService.class);
        MockedStatic<ReportedMenus> reports = mockStatic(ReportedMenus.class)) {
      manager.clickButton(reportedClick);
      reports.verify(() -> ReportedMenus.navigate(reportedClick, player, reportedHolder, manager));
    }
    assertTrue(reportedClick.isCancelled());
    verifyNoInteractions(manager.militaryView);
  }

  private record QueueState(
      QueueCancelPayload.Type type,
      String payload,
      Military military,
      Guild guild,
      InstallationHandler installations,
      MercenaryCompany company) {
    void verifyCancellation() {
      switch (type) {
        case MILITARY -> verify(military).cancelQueue(2);
        case GUILD_UPGRADE -> verify(guild).cancelUpgradeQueue(2);
        case INSTALLATION -> verify(installations).cancelPending("construction");
        case COMPANY_SLOT -> verify(company).cancelSlotQueue(2);
        case COMPANY_UPGRADE -> verify(company).cancelUpgradeQueue(2);
      }
    }

    void verifyUnchanged() {
      verify(military, never()).cancelQueue(anyInt());
      verify(guild, never()).cancelUpgradeQueue(anyInt());
      verify(installations, never()).cancelPending(anyString());
      verify(company, never()).cancelSlotQueue(anyInt());
      verify(company, never()).cancelUpgradeQueue(anyInt());
    }
  }

  private QueueState queue(QueueCancelPayload.Type type, boolean result) {
    Military military = mock(Military.class);
    InstallationHandler installations = mock(InstallationHandler.class);
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(faction.getMilitary()).thenReturn(military);
    when(faction.getInstallationHandler()).thenReturn(installations);
    when(guild.getCompany()).thenReturn(company);
    when(company.isLeader("Leader")).thenReturn(true);
    when(military.cancelQueue(2)).thenReturn(result);
    when(guild.cancelUpgradeQueue(2)).thenReturn(result);
    when(installations.cancelPending("construction")).thenReturn(result);
    when(company.cancelSlotQueue(2)).thenReturn(result);
    when(company.cancelUpgradeQueue(2)).thenReturn(result);
    String payload =
        switch (type) {
          case MILITARY -> QueueCancelPayload.military("realm", 2);
          case GUILD_UPGRADE -> QueueCancelPayload.guildUpgrade("guild", 2);
          case INSTALLATION -> QueueCancelPayload.installation("realm", "construction");
          case COMPANY_SLOT -> QueueCancelPayload.companySlot("guild", 2);
          case COMPANY_UPGRADE -> QueueCancelPayload.companyUpgrade("guild", 2);
        };
    return new QueueState(type, payload, military, guild, installations, company);
  }

  private void verifyQueueParent(QueueCancelPayload.Type type) {
    switch (type) {
      case MILITARY -> verify(manager.militaryView).militaryView(null, player, faction, true);
      case GUILD_UPGRADE -> verify(manager.guildView).upgradeView(player, guild);
      case INSTALLATION ->
          verify(manager.installationView).installationsView(null, player, faction, true);
      case COMPANY_SLOT -> verify(manager.companyView).slotsView(player, guild);
      case COMPANY_UPGRADE -> verify(manager.companyView).companyUpgradeView(player, guild);
    }
  }

  private Inventory open(SFGUI type, boolean flag) {
    String id =
        switch (type) {
          case GUILD_VIEW,
                  UPGRADE_VIEW,
                  LEDGER_VIEW,
                  COMPANY_VIEW,
                  COMPANY_SLOTS_VIEW,
                  COMPANY_ROSTER_VIEW,
                  COMPANY_UPGRADE_VIEW,
                  CONTRACT_LIST_VIEW,
                  CONTRACT_DETAIL_VIEW,
                  LOAN_MAIN_VIEW,
                  LOANS_GIVEN_VIEW,
                  LOANS_TAKEN_VIEW,
                  ISSUED_LOAN_DETAIL_VIEW,
                  TAKEN_LOAN_DETAIL_VIEW ->
              "guild";
          default -> "realm";
        };
    Inventory inventory = gui.inventory(new SFInventoryHolder(id, type, 0, flag), 54, "Menu");
    player.openInventory(inventory);
    return inventory;
  }

  private Object[] views() {
    return new Object[] {
      manager.factionView,
      manager.guildView,
      manager.companyView,
      manager.contractView,
      manager.mercenaryMarketView,
      manager.loanView,
      manager.lawView,
      manager.militaryView,
      manager.installationView,
      manager.governmentView,
      manager.vehicleFeeView,
      manager.movementView,
      manager.relationView,
      manager.electionView,
      manager.tierTitleView,
      manager.warView,
      manager.taxView,
      manager.campaignView,
      manager.campaignInstallationPickView,
      manager.campaignRaidLaunchView,
      manager.declareWarView
    };
  }

  private MercenaryContract contract() {
    MercenaryCompany company = mock(MercenaryCompany.class);
    ContractHandler handler = mock(ContractHandler.class);
    MercenaryContract contract = mock(MercenaryContract.class);
    when(contract.getId()).thenReturn("contract");
    when(contract.getSlots()).thenReturn(2);
    when(contract.getDueDate()).thenReturn(System.currentTimeMillis() + 86_400_000);
    when(company.getSlots()).thenReturn(5);
    when(company.isLeader("Leader")).thenReturn(true);
    when(company.getContractHandler()).thenReturn(handler);
    when(handler.getById("contract")).thenReturn(contract);
    when(guild.getCompany()).thenReturn(company);
    return contract;
  }

  private AsyncPlayerChatEvent chat(String text) {
    return new AsyncPlayerChatEvent(true, player, text, Set.of());
  }

  private Loan loan(double amount, double balance) {
    Bank bank = new Bank(guild, balance, mock(Chunk.class));
    when(guild.getBank()).thenReturn(bank);
    when(guild.getLoanHandler()).thenReturn(new LoanHandler(guild));
    Guild issuer = mock(Guild.class);
    when(issuer.getLedger()).thenReturn(mock(Ledger.class));
    return new Loan("loan", amount, issuer, guild, System.currentTimeMillis(), 7, 0, 0, false);
  }

  private AtomicReference<Double> donation(double amount) {
    AtomicReference<Double> current = new AtomicReference<>(amount);
    when(guild.getDonationAmount()).thenAnswer(call -> current.get());
    doAnswer(
            call -> {
              current.set(call.getArgument(0));
              return null;
            })
        .when(guild)
        .setDonationAmount(anyDouble());
    return current;
  }
}
