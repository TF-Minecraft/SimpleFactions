package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.LoanData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.loans.LoanStatus;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettlementPaymentBoundaryCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Guild guild;
  private boolean previousEligibility;
  private final UUID player =
      UUID.nameUUIDFromBytes("Bob".getBytes(java.nio.charset.StandardCharsets.UTF_8));

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    faction = fixture.saved("home", "Alice");
    guild = fixture.guild(faction, "traders", "Bob");
    faction.getOrCreateMainGuild();
    faction.getTaxHandler().setDividendTax(0);
    guild.getBank().setWealth(100.0);
    previousEligibility = Cache.dividendRequirePreviousTickMembership;
    Cache.dividendRequirePreviousTickMembership = false;
  }

  @AfterEach
  void close() {
    Cache.dividendRequirePreviousTickMembership = previousEligibility;
    fixture.close();
  }

  private DailyGuildTransfers dividend(double amount) {
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    transfers.setPendingDividendPool(guild, amount);
    return transfers;
  }

  private DailyGuildTransfers wage(double amount) {
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    transfers.addPlayerPayout(guild, player, amount);
    return transfers;
  }

  @Test
  void unavailablePlayerBankCannotConsumeDividendOrReportIncome() {
    PlayerEconomyManager economy = new PlayerEconomyManager();
    PostSettlementPayouts.apply(dividend(25), null, economy, name -> player);
    assertEquals(100, guild.getBank().getWealth());
    assertEquals(0, economy.getLedger(player).getNetDaily());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rejectedExternalDepositCannotConsumeGuildFundsOrReportIncome(boolean dividends) {
    RecordingBank bank = new RecordingBank(false);
    PlayerEconomyManager economy = new PlayerEconomyManager();
    PostSettlementPayouts.apply(dividends ? dividend(25) : wage(25), bank, economy, name -> player);
    assertEquals(1, bank.attempts);
    assertEquals(0, bank.total());
    assertEquals(100, guild.getBank().getWealth());
    assertEquals(0, economy.getLedger(player).getNetDaily());
  }

  @Test
  void successfulDividendsAndWagesBalanceAllAccountsAndLedger() {
    DailyGuildTransfers transfers = dividend(30);
    transfers.addPlayerPayout(guild, player, 20);
    RecordingBank bank = new RecordingBank(true);
    PlayerEconomyManager economy = new PlayerEconomyManager();
    PostSettlementPayouts.apply(transfers, bank, economy, name -> player);
    assertEquals(50, guild.getBank().getWealth());
    assertEquals(50, bank.total());
    assertEquals(30, economy.getLedger(player).getAmount(PlayerCashflow.DIVIDEND_PAYOUT));
    assertEquals(20, economy.getLedger(player).getAmount(PlayerCashflow.WAGES));
  }

  @Test
  void noBudgetAndNoKnownRecipientsPreserveMoney() {
    RecordingBank bank = new RecordingBank(true);
    PostSettlementPayouts.apply(null, bank, null, null);
    PostSettlementPayouts.apply(new DailyGuildTransfers(), bank, null, null);
    PostSettlementPayouts.apply(dividend(25), bank, null, name -> null);
    assertEquals(100, guild.getBank().getWealth());
    guild.getBank().setWealth(0.0);
    PostSettlementPayouts.apply(wage(25), bank, null, name -> player);
    assertEquals(0, bank.attempts);
    assertEquals(0, guild.getBank().getWealth());
  }

  @Test
  void publicBufferRejectsEmptyTransfersAndClearDropsAllQueues() {
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    transfers.addPlayerPayout(null, player, 1);
    transfers.addPlayerPayout(guild, null, 1);
    transfers.addPlayerPayout(guild, player, 0);
    transfers.setPendingDividendPool(null, 1);
    transfers.setPendingDividendPool(guild, 0);
    assertTrue(transfers.getPlayerPayouts().isEmpty());
    assertTrue(transfers.getPendingDividendPools().isEmpty());
    transfers.add(guild, faction.getOrCreateMainGuild(), 1);
    transfers.addExternalDelta(guild, 2);
    transfers.addPlayerPayout(guild, player, 3);
    transfers.setPendingDividendPool(guild, 4);
    transfers.clear();
    assertTrue(transfers.getTransfers().isEmpty());
    assertTrue(transfers.getExternalDeltas().isEmpty());
    assertTrue(transfers.getPlayerPayouts().isEmpty());
    assertTrue(transfers.getPendingDividendPools().isEmpty());
  }

  @Test
  void corruptPublicBufferedRowsDoNotBlockValidWages() {
    DailyGuildTransfers transfers = wage(10);
    Map<UUID, Double> payouts = transfers.getPlayerPayouts().get(guild);
    payouts.put(null, 5.0);
    payouts.put(UUID.randomUUID(), null);
    payouts.put(UUID.randomUUID(), -10.0);
    transfers.getPendingDividendPools().put(null, 20.0);
    RecordingBank bank = new RecordingBank(true);
    PostSettlementPayouts.apply(transfers, bank, null, null);
    assertEquals(10, bank.total());
    assertEquals(90, guild.getBank().getWealth());
  }

  @Test
  void roundedSubCentWagesKeepMoneyInGuild() {
    RecordingBank bank = new RecordingBank(true);
    PostSettlementPayouts.apply(wage(0.001), bank, null, null);
    assertEquals(0, bank.attempts);
    assertEquals(100, guild.getBank().getWealth());
  }

  @Test
  void restoredLoanRejectsUnknownAccountsAndOverdueCreditIsChargedOncePerDay() {
    LoanData invalid =
        new LoanData(
            new Loan(
                100,
                faction.getOrCreateMainGuild(),
                guild,
                System.currentTimeMillis(),
                1,
                0,
                0,
                false));
    invalid.issuer = "missing";
    invalid.borrower = "unknown";
    assertThrows(IllegalArgumentException.class, () -> new Loan(invalid));
    Guild lender = faction.getOrCreateMainGuild();
    Loan overdue =
        new Loan(100, lender, guild, System.currentTimeMillis() - 5 * 86400000L, 1, 0, 0, false);
    int before = guild.getLoanHandler().getCreditScore();
    overdue.tickDay();
    assertTrue(guild.getLoanHandler().getCreditScore() < before);
    assertEquals(LoanStatus.ACTIVE, overdue.getStatus());
  }

  @Test
  void paymentOfAccruedInterestReducesDebtWithoutDoubleChargingTodaysInterest() {
    Guild lender = faction.getOrCreateMainGuild();
    Loan loan = new Loan(100, lender, guild, System.currentTimeMillis(), 10, 7, 0, false);
    loan.tickDay();
    assertEquals(1, loan.getUnpaidInterest(), 0.00001);
    loan.setAutoPay(true);
    double today = loan.getDailyInterestChange();
    loan.setTempInterestPayment(today + 1);
    loan.tickDay();
    assertEquals(0, loan.getUnpaidInterest(), 0.00001);
    assertEquals(1, loan.getPaidInterest(), 0.00001);
    assertEquals(100, loan.getTotalOwed(), 0.00001);
  }

  @Test
  void dividendTaxAndMemberPaymentConserveTheBudget() {
    faction.getTaxHandler().setDividendTax(20);
    Guild capital = faction.getOrCreateMainGuild();
    double before = capital.getBank().getWealth();
    RecordingBank bank = new RecordingBank(true);
    PlayerEconomyManager economy = new PlayerEconomyManager();
    PostSettlementPayouts.apply(dividend(40), bank, economy, name -> player);
    double tax = capital.getBank().getWealth() - before;
    assertTrue(tax > 0);
    assertEquals(40, tax + bank.total(), 0.00001);
    assertEquals(60, guild.getBank().getWealth(), 0.00001);
    assertEquals(bank.total(), economy.getLedger(player).getAmount(PlayerCashflow.DIVIDEND_PAYOUT));
  }

  @Test
  void invalidMemberNamesAndEmptyQueuedPayoutsCannotMoveMoney() {
    RecordingBank bank = new RecordingBank(true);
    guild.getMembers().add("");
    guild.getMembers().add(null);
    PostSettlementPayouts.apply(
        dividend(20),
        bank,
        null,
        name -> {
          assertNotNull(name);
          assertFalse(name.isBlank());
          return null;
        });
    DailyGuildTransfers empty = new DailyGuildTransfers();
    empty.getPlayerPayouts().put(guild, new HashMap<>());
    PostSettlementPayouts.apply(empty, bank, null, null);
    assertEquals(100, guild.getBank().getWealth());
    assertEquals(0, bank.attempts);
  }

  private static final class RecordingBank implements PlayerBank {
    final Map<UUID, Double> paid = new HashMap<>();
    final boolean accepting;
    int attempts;

    RecordingBank(boolean accepting) {
      this.accepting = accepting;
    }

    double total() {
      return paid.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getBankBalance(UUID id) {
      return paid.getOrDefault(id, 0.0);
    }

    public boolean withdrawFromBank(UUID id, double amount) {
      return false;
    }

    public boolean depositToBank(UUID id, double amount) {
      attempts++;
      if (!accepting) return false;
      paid.merge(id, amount, Double::sum);
      return true;
    }

    public UUID resolve(String name) {
      return null;
    }
  }
}
