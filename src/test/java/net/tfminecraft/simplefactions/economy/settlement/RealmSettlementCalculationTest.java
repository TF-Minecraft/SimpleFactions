package net.tfminecraft.simplefactions.economy.settlement;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.LoanData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealmSettlementCalculationTest {
    FactionDomainFixture fixture;
    MockedStatic<Denars> denars;
    Faction faction;
    Guild capital;
    Guild guild;
    UUID character = UUID.randomUUID();
    boolean eligibility;

    @BeforeEach void setup() {
        fixture = new FactionDomainFixture();
        faction = fixture.saved("realm", UUID.randomUUID().toString());
        capital = faction.getOrCreateMainGuild();
        guild = fixture.guild(faction, "guild", character.toString());
        net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(capital.getBank(), 100.0);
        net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(guild.getBank(), 100.0);
        faction.getTaxHandler().setGuildTax(0);
        faction.getTaxHandler().setDividendTax(0);
        denars = mockStatic(Denars.class, CALLS_REAL_METHODS);
        
        eligibility = Cache.dividendRequirePreviousTickMembership;
        Cache.dividendRequirePreviousTickMembership = false;
        Ledger.setNodeUpkeepLookup(g -> 0);
        Ledger.setVehiclePoolUpkeepForTests(id -> 0);
    }

    @AfterEach void close() {
        Cache.dividendRequirePreviousTickMembership = eligibility;
        Ledger.setNodeUpkeepLookup(null);
        Ledger.setVehiclePoolUpkeepForTests(null);
        denars.close();
        fixture.close();
    }

    SettlementJournal.Plan plan(List<RealmSettlementCalculation.Claim> claims) {
        return new RealmSettlementCalculation(10, 1000000000L, FactionManager.getAllGuilds(), claims)
                .calculate((balances, legs, changes) -> {});
    }

    long net(SettlementJournal.Plan plan, String account) {
        return plan.legs().stream().filter(leg -> leg.account().equals(account)).mapToLong(Leg::cents).sum();
    }

    RealmSettlementChanges changes(SettlementJournal.Plan plan) {
        return JsonUtil.GSON.fromJson(plan.changes(), RealmSettlementChanges.class);
    }

    @Test void heldCitizenTaxMovesOnceAndPlanningLeavesEverythingUntouched() {
        denars.when(() -> Denars.balance(Denars.guildTax(capital.getIncarnationId()))).thenReturn(1250L);
        capital.getLedger().addCitizenTaxEntry("quarantine:old", 12.5);
        var plan = plan(List.of());
        assertEquals(1250, net(plan, capital.getBank().accountId()));
        assertEquals(-1250, net(plan, Denars.guildTax(capital.getIncarnationId())));
        assertEquals(0, net(plan, Denars.MINT));
        assertEquals(100, capital.getBank().getWealth());
        assertEquals(Map.of("quarantine:old", 12.5), capital.getLedger().getCitizenTaxesCopy());
        assertEquals(JsonUtil.GSON.toJson(plan), JsonUtil.GSON.toJson(plan(List.of())));
        assertEquals(0, plan.legs().stream().mapToLong(Leg::cents).sum());
        assertTrue(changes(plan).guilds.stream().filter(c -> c.incarnation.equals(capital.getIncarnationId())).findFirst().orElseThrow().citizenTaxes.isEmpty());
    }

    @Test void debtIsPreservedAndDailyShortfallsLiquidateWithoutTouchingLiveBranches() {
        guild.getBranch(0).levelUp();
        guild.getBranch(0).levelUp();
        Ledger.setNodeUpkeepLookup(g -> g == guild ? 200 : 0);
        var plan = plan(List.of());
        assertEquals(-4000, net(plan, guild.getBank().accountId()));
        var after = changes(plan).guilds.stream().filter(c -> c.incarnation.equals(guild.getIncarnationId())).findFirst().orElseThrow();
        assertEquals(1, after.branches.get(0));
        assertEquals(2, guild.getSize());
        assertEquals(100, guild.getBank().getWealth());
        net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(guild.getBank(), -100);
        var debt = plan(List.of());
        assertEquals(0, net(debt, guild.getBank().accountId()));
        assertEquals(2, changes(debt).guilds.stream().filter(c -> c.incarnation.equals(guild.getIncarnationId())).findFirst().orElseThrow().branches.get(0));
    }

    @Test void fixedDeathDividendsReserveNormalPoolAndPartialWagesStayOwed() {
        net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(guild.getBank(), 10.0);
        var company = new MercenaryCompany(guild, "Company", null, 0);
        company.accrueWage(character.toString(), 30);
        guild.setCompany(company);
        UUID deceased = UUID.randomUUID();
        var claim = new RealmSettlementCalculation.Claim("death:dividend", guild.getIncarnationId(), deceased, 1000, net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.DIVIDENDS);
        var wages = new RealmSettlementCalculation.Claim("death:wage", guild.getIncarnationId(), deceased, 1000, net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.WAGES);
        var plan = plan(List.of(claim, wages));
        assertEquals(-1000, net(plan, guild.getBank().accountId()));
        assertEquals(600, net(plan, "char:" + character + ":bank"));
        assertEquals(400, net(plan, "char:" + deceased + ":bank"));
        var after = changes(plan).guilds.stream().filter(c -> c.incarnation.equals(guild.getIncarnationId())).findFirst().orElseThrow();
        assertEquals(24, after.company.pendingWages.get(character.toString()));
        assertEquals(30, company.getPendingWages().get(character.toString()));
        assertEquals(Map.of("death:dividend", 200L, "death:wage", 200L), changes(plan).deathPayments);
    }

    @Test void dissolvedGuildStillPaysOwedCharacterFromItsIncarnationTreasury() {
        UUID incarnation = UUID.randomUUID();
        denars.when(() -> Denars.balance(Denars.guild(incarnation))).thenReturn(500L);
        var claim = new RealmSettlementCalculation.Claim("dissolved", incarnation, character, 1000, net.tfminecraft.simplefactions.identity.death.OwedPayment.Kind.WAGES);
        var plan = plan(List.of(claim));
        assertEquals(-500, net(plan, Denars.guild(incarnation)));
        assertEquals(500, net(plan, "char:" + character + ":bank"));
        assertEquals(500, changes(plan).deathPayments.get("dissolved"));
    }

    @Test void loansRecordExactAfterValuesAndCreditWithoutChangingLiveLoan() {
        net.tfminecraft.simplefactions.testsupport.TestTreasuries.set(guild.getBank(), 10.0);
        Loan loan = new Loan("test", 100, capital, guild, 1000000000L - 86400000L * 3, 1, 7, 5, true);
        capital.getLoanHandler().issueLoan(loan);
        String before = JsonUtil.GSON.toJson(new LoanData(loan));
        var plan = plan(List.of());
        assertEquals(-1000, net(plan, guild.getBank().accountId()));
        assertEquals(1000, net(plan, capital.getBank().accountId()));
        assertEquals(before, JsonUtil.GSON.toJson(new LoanData(loan)));
        var changes = changes(plan);
        LoanData after = changes.guilds.stream().filter(c -> c.incarnation.equals(capital.getIncarnationId())).findFirst().orElseThrow().loans.getFirst();
        assertTrue(after.paid > 0);
        assertTrue(changes.guilds.stream().filter(c -> c.incarnation.equals(guild.getIncarnationId())).findFirst().orElseThrow().creditScore < guild.getLoanHandler().getCreditScore());
        loan.restoreSettlement(after);
        double owed = loan.getTotalOwed();
        loan.restoreSettlement(after);
        assertEquals(owed, loan.getTotalOwed());
        after.id = "wrong";
        assertThrows(IllegalArgumentException.class, () -> loan.restoreSettlement(after));
    }

    @Test void multiDayReparationsCarryWithOneLessDayWhileExpiringOnesDrop() {
        faction.addWarReparationsObligation(new net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation("payee", 10, 3));
        faction.addWarReparationsObligation(new net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation("payee", 25, 1));
        faction.getWarReparationsObligations().add(null);
        var carried = changes(plan(List.of())).reparations.get(faction.getId());
        assertEquals(1, carried.size());
        assertEquals("payee", carried.get(0).getPayeeFactionId());
        assertEquals(10, carried.get(0).getIncomePercent());
        assertEquals(2, carried.get(0).getDaysRemaining());
        assertEquals(3, faction.getWarReparationsObligations().get(0).getDaysRemaining(), "planning leaves the live obligation untouched");
    }

    @Test void aLoanWhoseBorrowerIsOutsideTheSettlementIsRejected() {
        Loan loan = new Loan("test", 100, capital, guild, 1000000000L - 86400000L * 3, 1, 7, 5, true);
        capital.getLoanHandler().issueLoan(loan);
        var partial = new RealmSettlementCalculation(10, 1000000000L, List.of(capital), List.of());
        var error = assertThrows(IllegalStateException.class, () -> partial.calculate((balances, legs, changes) -> {}));
        assertEquals("Loan parties missing from settlement: test", error.getMessage());
    }

    MercenaryCompany formedCompany(Guild host) {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("item.material", "IRON_SWORD");
        yaml.set("mercenary", true);
        var company = new MercenaryCompany(host, "Company",
                new net.tfminecraft.simplefactions.army.Regiment("mercenary", yaml), 0);
        host.setCompany(company);
        return company;
    }

    net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract activeContract(MercenaryCompany company, long issue, int durationDays) {
        var contract = new net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract(company, faction,
                net.tfminecraft.simplefactions.mercenary.contract.ContractKind.MERCENARY,
                new net.tfminecraft.simplefactions.mercenary.contract.ContractTerms(1, 50, 10, durationDays, 50, 500), issue);
        company.getContractHandler().add(contract);
        assertTrue(contract.activate());
        return contract;
    }

    @Test void contractDayPriceWagesAndRefundSettleThenAnElapsedContractCompletesAndClearsAccruals() {
        var company = formedCompany(guild);
        assertTrue(company.enlist(character.toString()));
        company.getWageSettings().setPeacetimePerDay(5);
        company.getWageSettings().setActivePercent(50);
        var contract = activeContract(company, 1000000000L - 15L * 86400000L, 14);
        assertTrue(contract.accrueBattleCharge("battle:1", 20));
        assertTrue(contract.accrueAbsenceRefund("battle:1", 7));
        var plan = plan(List.of());
        assertEquals(0, plan.legs().stream().mapToLong(Leg::cents).sum(), "the posting balances");
        assertEquals(-2300, net(plan, capital.getBank().accountId()),
                "hirer capital pays the 20 battle charge and 10 day price, less the 7 refund");
        var changes = changes(plan);
        assertEquals(10.0, changes.playerIncome.get(character).get("WAGES"),
                "5 peacetime wage plus half of the 10 day price for the covered slot");
        var after = changes.guilds.stream().filter(c -> c.incarnation.equals(guild.getIncarnationId())).findFirst().orElseThrow();
        assertEquals(0.0, after.company.pendingWages.getOrDefault(character.toString(), 0.0));
        var saved = after.company.contracts.get(0);
        assertEquals("COMPLETED", saved.status);
        assertEquals(1, saved.daysServed);
        assertEquals(0.0, saved.accruedToCompany, "settled accruals are cleared so a day cannot be paid twice");
        assertEquals(0.0, saved.accruedToHirer);
        assertEquals(60, after.company.reputation, "a clean fourteen-day completion earns the bonus");
        assertEquals("ACTIVE", contract.getStatus().name(), "planning leaves the live contract untouched");
        assertEquals(20, contract.getAccruedToCompany());
        assertEquals(0, contract.getDaysServed());
    }

    @Test void anUnexpiredContractEndsWhenItsHostEndsTheDayBankrupt() {
        var company = formedCompany(guild);
        assertTrue(company.enlist(character.toString()));
        var contract = activeContract(company, 1000000000L, 14);
        Ledger.setNodeUpkeepLookup(g -> g == guild ? 500 : 0);
        var plan = plan(List.of());
        var after = changes(plan).guilds.stream().filter(c -> c.incarnation.equals(guild.getIncarnationId())).findFirst().orElseThrow();
        assertEquals("TERMINATED", after.company.contracts.get(0).status);
        assertEquals(1, after.company.contracts.get(0).daysServed);
        assertTrue(after.company.reputation < 50, "bankruptcy is punished through reputation");
        assertEquals("ACTIVE", contract.getStatus().name());
    }
}
