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
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(guild.getBank(), 100.0);
    previousEligibility = Cache.dividendRequirePreviousTickMembership;
    Cache.dividendRequirePreviousTickMembership = false;
  }

  @AfterEach
  void close() {
    Cache.dividendRequirePreviousTickMembership = previousEligibility;
    fixture.close();
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

}
