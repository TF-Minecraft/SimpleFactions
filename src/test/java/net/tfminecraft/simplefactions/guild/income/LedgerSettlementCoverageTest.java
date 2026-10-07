package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.diplomacy.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.*;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.utils.*;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

class LedgerSettlementCoverageTest {
  private FactionDomainFixture fixture;
  private Faction home;
  private Guild capital, guild;
  private boolean previousEligibility;
  private PostSettlementPayouts.PlayerUuidLookup previousLookup;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    fixture.lawGroup(
        "tax",
        Map.of(
            "effects.faction.rules",
            List.of(
                "CITIZEN_TAX true",
                "GUILD_TAX true",
                "VASSAL_TAX true",
                "DIVIDEND_TAX true",
                "TARIFFS true")));
    YamlConfiguration u = new YamlConfiguration();
    u.set("u.upkeep", 3);
    u.set("u.allowed-types", List.of("guild"));
    UpgradeLoader.map.put("workshop", new Upgrade("workshop", u.getConfigurationSection("u")));
    home = fixture.saved("home", "Alice");
    capital = home.getOrCreateMainGuild();
    guild = fixture.guild(home, "traders", "Bob");
    previousEligibility = Cache.dividendRequirePreviousTickMembership;
    Cache.dividendRequirePreviousTickMembership = false;
    previousLookup = MercenaryEngagements.uuidLookup();
    capital.getBank().setWealth(1000.0);
    guild.getBank().setWealth(1000.0);
  }

  @AfterEach
  void close() {
    Cache.dividendRequirePreviousTickMembership = previousEligibility;
    MercenaryEngagements.setUuidLookup(previousLookup);
    fixture.close();
  }

  @Test
  void independentDailyEntriesAccumulateAndSettlementClearsThemExactlyOnce() {
    Ledger ledger = capital.getLedger();
    Map<String, Double> saved = new LinkedHashMap<>();
    saved.put(null, 2.0);
    saved.put("", 2.0);
    saved.put("null", null);
    saved.put("negative", -2.0);
    saved.put("zero", 0.0);
    saved.put("Alice", 2.0);
    ledger.setCitizenTaxes(saved);
    ledger.addCitizenTaxEntry("Alice", 3.0);
    ledger.addCitizenTaxEntry("Bob", 9.0);
    assertEquals(Map.of("Alice", 5.0, "Bob", 9.0), ledger.getCitizenTaxesCopy());
    assertEquals(
        List.of("Bob", "Alice"),
        ledger.getCitizenTaxEntriesDescending().stream().map(Map.Entry::getKey).toList());
    ledger.addLoanPaymentEntry("borrower", 10.0);
    ledger.addLoanPaymentEntry("borrower", 2.0);
    ledger.addInterestPaymentEntry("borrower", 3.0);
    ledger.addInterestPaymentEntry("borrower", 4.0);
    ledger.addMercenaryPaymentEntry("hirer", 5.0);
    ledger.addMercenaryPaymentEntry("hirer", 2.0);
    ledger.addRefundEntry("hirer", 4.0);
    ledger.addRefundEntry("hirer", 3.0);
    ledger.addCasinoProfitEntry(null);
    ledger.addCasinoProfitEntry(-1.0);
    ledger.addCasinoProfitEntry(20.0);
    ledger.addCasinoProfitEntry(5.0);
    assertEquals(12, ledger.getIncome(Cashflow.LOANS));
    assertEquals(7, ledger.getIncome(Cashflow.INTEREST));
    assertEquals(14, ledger.getIncome(Cashflow.CITIZENS));
    assertEquals(25, ledger.getIncome(Cashflow.GAMBLING));
    assertEquals(-7, ledger.getIncome(Cashflow.MERCENARY_PAYMENTS));
    assertEquals(7, ledger.getIncome(Cashflow.REFUNDS));
    var detached = ledger.getCitizenTaxesCopy();
    detached.clear();
    assertEquals(14, ledger.getIncome(Cashflow.CITIZENS));
    DailyGuildTransfers first = new DailyGuildTransfers();
    ledger.populateDailyTransfers(first);
    // Citizen receipts plus loan principal and interest already paid outside guild transfers.
    assertEquals(33, first.getExternalDeltas().get(capital));
    assertEquals(0, ledger.getCasinoProfit());
    assertTrue(ledger.getCitizenTaxesCopy().isEmpty());
    for (Cashflow kind :
        List.of(Cashflow.LOANS, Cashflow.INTEREST, Cashflow.MERCENARY_PAYMENTS, Cashflow.REFUNDS))
      assertEquals(0, ledger.getIncome(kind));
    DailyGuildTransfers second = new DailyGuildTransfers();
    ledger.populateDailyTransfers(second);
    assertTrue(second.getExternalDeltas().isEmpty());
    ledger.addCitizenTaxEntry("Alice", 3.0);
    ledger.clearDailyIncome();
    assertTrue(ledger.getCitizenTaxesCopy().isEmpty());
  }

  @Test
  void vassalTaxAndGuildTaxHistoryMatchActualBufferedTransfers() {
    Faction subject = fixture.saved("subject", "Cara");
    fixture.subject(home, subject);
    Guild subjectCapital = subject.getOrCreateMainGuild();
    subjectCapital.getTradeBreakdown().setIncome(100);
    Guild merchants = fixture.guild(subject, "merchants", "Dan");
    merchants.getTradeBreakdown().setIncome(80);
    assertEquals(0.75, subject.getGovernment().getTaxEfficiency());
    assertEquals(6, merchants.getLedger().getIncome(Cashflow.GUILD_PAYMENTS) * -1);
    assertEquals(21.2, subjectCapital.getLedger().getOverlordTax(), 0.001);
    assertEquals(21.2, capital.getLedger().getIncome(Cashflow.VASSALS), 0.001);
    var history = Ledger.collectHistoryDay(List.of(subjectCapital, merchants));
    assertEquals(
        21.2, history.get(capital).get(LedgerHistory.Source.VASSALS).get(subject.getName()), 0.001);
    assertEquals(
        6,
        history.get(subjectCapital).get(LedgerHistory.Source.GUILD_TAXES).get(merchants.getName()),
        0.001);
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    subjectCapital.getLedger().populateDailyTransfers(transfers);
    merchants.getLedger().populateDailyTransfers(transfers);
    assertEquals(21.2, transfers.getTransfers().get(subjectCapital).get(capital), 0.001);
    assertEquals(6, transfers.getTransfers().get(merchants).get(subjectCapital), 0.001);
    assertEquals(0, capital.getLedger().getOverlordTax());
  }

  @Test
  void bankruptPayersAndGuildsWithoutTradeDoNotPayReparations() {
    Faction creditor = fixture.saved("creditor", "Cara");
    Guild receiver = creditor.getOrCreateMainGuild();
    home.addWarReparationsObligation(new WarReparationsObligation("creditor", 25, 4));
    guild.getTradeBreakdown().setIncome(120);
    guild.getBank().setWealth(-1.0);
    assertTrue(guild.isBankrupt());
    assertEquals(0, receiver.getLedger().getWarReparationsReceived());
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    capital.getLedger().populateDailyTransfers(transfers);
    guild.getLedger().populateDailyTransfers(transfers);
    assertTrue(transfers.getTransfers().isEmpty());
    assertEquals(0, receiver.getBank().getWealth());
  }

  @Test
  void tributeTariffsAndReparationsUseCurrentRecipientsAndPreserveConservation() {
    Faction creditor = fixture.saved("creditor", "Cara");
    Guild creditorCapital = creditor.getOrCreateMainGuild();
    YamlConfiguration y = new YamlConfiguration();
    y.set("r.recieve-modifiers", List.of("TRIBUTE(10)", "PRESTIGE(2)"));
    home.getDiplomacyHandler()
        .setRelation(
            creditor,
            new Relation(
                new RelationType("tribute", y.getConfigurationSection("r")),
                RelationLoader.getDefaultAttitude()));
    capital.getTradeBreakdown().setIncome(200);
    capital.getTradeBreakdown().registerTariffs(creditor, 4);
    home.getWarReparationsObligations().add(new WarReparationsObligation(creditor.getId(), 25, 10));
    home.getWarReparationsObligations().add(new WarReparationsObligation("missing", 0, 10));
    assertEquals(20, capital.getLedger().getTributeTax(), 0.001);
    assertEquals(20, creditorCapital.getLedger().getTributeRecieved(), 0.001);
    assertEquals(50, creditorCapital.getLedger().getWarReparationsReceived(), 0.001);
    assertEquals(0, guild.getLedger().getWarReparationsReceived());
    assertEquals(200, capital.getLedger().getReparationsTaxableIncome(), 0.001);
    var history = Ledger.collectHistoryDay(List.of(capital));
    assertEquals(
        20,
        history.get(creditorCapital).get(LedgerHistory.Source.TRIBUTES).get(home.getName()),
        0.001);
    assertEquals(
        4,
        history.get(creditorCapital).get(LedgerHistory.Source.TARIFFS).get(home.getName()),
        0.001);
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    capital.getLedger().populateDailyTransfers(transfers);
    assertEquals(74, transfers.getTransfers().get(capital).get(creditorCapital), 0.001);
    assertEquals(1000, capital.getBank().getWealth());
  }

  @Test
  void dividendsReserveAPoolOnlyForEligibleMembersAndUpkeepUsesUpgradeLevels() {
    guild.getTradeBreakdown().setIncome(100);
    guild.setDividendPercent(50);
    assertEquals(DividendBreakdown.none(), capital.getLedger().getDividendBreakdown());
    for (Upgrade upgrade : guild.getUpgrades()) upgrade.setLevel(2);
    assertEquals(-6, guild.getLedger().getIncome(Cashflow.UPGRADES_UPKEEP));
    var breakdown = guild.getLedger().getDividendBreakdown();
    assertTrue(breakdown.pool() > 0);
    assertEquals(1, breakdown.eligibleCount());
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    guild.getLedger().populateDailyTransfers(transfers);
    assertEquals(breakdown.pool(), transfers.getPendingDividendPools().get(guild));
    guild.getMembers().clear();
    var empty = guild.getLedger().breakdownForPool(10);
    assertEquals(0, empty.eligibleCount());
    assertEquals(0, empty.payout());
  }

  @Test
  void bankruptGuildKeepsCitizenTaxAndStopsAutomaticLoanPayments() {
    Loan debt = new Loan(700, capital, guild, System.currentTimeMillis(), 7, 7, 0, true);
    Loan manual = new Loan(20, capital, guild, System.currentTimeMillis(), 7, 7, 0, false);
    capital.getLoanHandler().issueLoan(debt);
    capital.getLoanHandler().issueLoan(manual);
    guild.getBank().setWealth(-1.0);
    guild.getLedger().addCitizenTaxEntry("Bob", 3.0);
    assertEquals(0, guild.getLedger().getInternalTaxableIncome());
    assertEquals(DividendBreakdown.none(), guild.getLedger().getDividendBreakdown());
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    guild.getLedger().populateDailyTransfers(transfers);
    assertFalse(debt.isAutoPay());
    assertFalse(manual.isAutoPay());
    assertTrue(transfers.getTransfers().isEmpty());
    assertTrue(transfers.getExternalDeltas().isEmpty());
    assertEquals(Map.of("Bob", 3.0), guild.getLedger().getCitizenTaxesCopy());
  }

  @Test
  void realCompanyWagesReachOnlyResolvablePlayersAndClearAfterSettlement() {
    var regiment = fixture.regiment("guards", false, 2, 1);
    MercenaryCompany company = new MercenaryCompany(guild, "Guards", regiment, 0);
    guild.setCompany(company);
    assertTrue(company.isFormed());
    company.accrueWage("Bob", 12);
    company.accrueWage("Missing", 8);
    UUID bob = UUID.randomUUID();
    MercenaryEngagements.setUuidLookup(name -> name.equals("Bob") ? bob : null);
    DailyGuildTransfers transfers = new DailyGuildTransfers();
    guild.getLedger().populateDailyTransfers(transfers);
    assertEquals(Map.of(bob, 12.0), transfers.getPlayerPayouts().get(guild));
    assertTrue(company.getPendingWages().isEmpty());
  }
}
