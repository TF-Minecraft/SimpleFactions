package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.loans.LoanHandler;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;

/**
 * Daily loan repayments and interest credit the lender in full, so they may only move money
 * the borrower has. Anything the borrower cannot cover stays owed on the loan.
 */
class LedgerLoanSettlementTest {

	private MockedStatic<FactionManager> factionManagerStatic;
	private MockedStatic<RelationManager> relationManagerStatic;
	private final Guild lender = mock(Guild.class);

	@BeforeEach
	void setUp() {
		factionManagerStatic = mockStatic(FactionManager.class);
		factionManagerStatic.when(FactionManager::getAllGuilds).thenReturn(Collections.emptyList());
		FactionManager.factions = new ArrayList<>();
		relationManagerStatic = mockStatic(RelationManager.class);
		relationManagerStatic.when(() -> RelationManager.getSubjects(any())).thenReturn(Collections.emptyList());
		when(lender.getId()).thenReturn("lender");
	}

	@AfterEach
	void tearDown() {
		factionManagerStatic.close();
		relationManagerStatic.close();
		FactionManager.factions = new ArrayList<>();
	}

	/** A 1 denar loan due tomorrow at 70,000,000% a week: 100,000 denars of interest a day. */
	private Loan runawayLoan() {
		return runawayLoan(null);
	}

	private Loan runawayLoan(Guild borrower) {
		return new Loan(1.0, lender, borrower, System.currentTimeMillis(), 1, 70_000_000.0, 0.0, true);
	}

	@Test
	void aBorrowerPaysOnlyWhatItHolds() {
		Loan loan = runawayLoan();
		Guild borrower = borrower(10.0, loan);

		DailyGuildTransfers buffer = new DailyGuildTransfers();
		new Ledger(borrower).populateDailyTransfers(buffer);

		assertEquals(10.0, paid(buffer, borrower), 1e-9);
	}

	@Test
	void whatTheBorrowerCouldNotPayStaysOwed() {
		Loan loan = runawayLoan();
		Guild borrower = borrower(10.0, loan);
		double owedBefore = loan.getTotalOwed() + loan.getDailyInterestChange();

		new Ledger(borrower).populateDailyTransfers(new DailyGuildTransfers());
		loan.tickDay();

		assertEquals(owedBefore - 10.0, loan.getTotalOwed(), 1e-6);
	}

	@Test
	void aBorrowerWithNoBankPaysNothing() {
		Loan loan = runawayLoan();
		Guild borrower = borrower(0.0, loan);
		when(borrower.getBank()).thenReturn(null);

		DailyGuildTransfers buffer = new DailyGuildTransfers();
		new Ledger(borrower).populateDailyTransfers(buffer);

		assertNull(buffer.getTransfers().get(borrower));
	}

	@Test
	void aBorrowerWhoCanAffordItPaysInFull() {
		Loan loan = new Loan(700.0, lender, null, System.currentTimeMillis(), 1, 7.0, 0.0, true);
		Guild borrower = borrower(10_000.0, loan);
		double due = loan.getDailyPayment(true) + loan.getDailyInterest();

		DailyGuildTransfers buffer = new DailyGuildTransfers();
		new Ledger(borrower).populateDailyTransfers(buffer);

		assertEquals(due, paid(buffer, borrower), 1e-9);
	}

	@Test
	void theBudgetIsSharedAcrossEveryLoanTheBorrowerHas() {
		Guild borrower = borrower(50.0, runawayLoan(), runawayLoan());

		DailyGuildTransfers buffer = new DailyGuildTransfers();
		new Ledger(borrower).populateDailyTransfers(buffer);

		assertEquals(50.0, paid(buffer, borrower), 1e-9);
	}

	@Test
	void aDueLoansCarriedInterestIsTransferredOnce() {
		// Due today, with a day of interest left unpaid from before auto-pay was switched on.
		Loan loan = new Loan(700.0, lender, null, System.currentTimeMillis(), 1, 7.0, 0.0, false);
		loan.tickDay();
		loan.setAutoPay(true);
		double owed = loan.getTotalOwed() + loan.getDailyInterestChange();
		Guild borrower = borrower(1_000_000.0, loan);

		DailyGuildTransfers buffer = new DailyGuildTransfers();
		new Ledger(borrower).populateDailyTransfers(buffer);

		assertEquals(owed, paid(buffer, borrower), 1e-9);
	}

	@Test
	void loansArePaidFromWhatIsLeftAfterTheDaysOtherPayments() {
		Guild borrower = borrower(10.0, runawayLoan());
		Guild overlord = mock(Guild.class);
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		buffer.add(borrower, overlord, 6.0);
		buffer.addExternalDelta(borrower, -3.0);

		new Ledger(borrower).populateDailyTransfers(buffer);

		assertEquals(1.0, paid(buffer, borrower), 1e-9);
	}

	@Test
	void theLedgerShowsOnlyWhatTheBorrowerCanFund() {
		Guild borrower = guild("borrower", 10.0);
		Loan loan = runawayLoan(borrower);
		when(borrower.getLoanHandler().getLoansTaken()).thenReturn(List.of(loan));
		Guild lenderGuild = guild("lender", 0.0);
		when(lenderGuild.getLoanHandler().getLoansGiven()).thenReturn(List.of(loan));

		Ledger lenders = new Ledger(lenderGuild);
		Ledger borrowers = new Ledger(borrower);

		assertEquals(10.0, lenders.getIncome(Cashflow.LOANS) + lenders.getIncome(Cashflow.INTEREST), 1e-9);
		assertEquals(-10.0,
				borrowers.getIncome(Cashflow.LOAN_PAYMENTS) + borrowers.getIncome(Cashflow.INTEREST_PAYMENTS), 1e-9);
	}

	private double paid(DailyGuildTransfers buffer, Guild borrower) {
		Map<Guild, Double> to = buffer.getTransfers().get(borrower);
		return to == null ? 0.0 : to.getOrDefault(lender, 0.0);
	}

	private Guild borrower(double wealth, Loan... loans) {
		Guild guild = guild("borrower", wealth);
		when(guild.getLoanHandler().getLoansTaken()).thenReturn(List.of(loans));
		return guild;
	}

	private Guild guild(String id, double wealth) {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		Military military = mock(Military.class);
		InstallationHandler installations = mock(InstallationHandler.class);
		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(0.0);
		when(faction.getInstallationHandler()).thenReturn(installations);
		when(installations.getAll()).thenReturn(Collections.emptyList());
		when(faction.getPenalty()).thenReturn(0.0);
		when(faction.getTaxRate(eq(TaxTarget.GUILDS), anyString(), anyBoolean())).thenReturn(0.0);
		when(faction.getTaxRate(eq(TaxTarget.DIVIDENDS), anyString(), anyBoolean())).thenReturn(0.0);
		Bank bank = mock(Bank.class);
		when(bank.getWealth()).thenReturn(wealth);
		when(guild.getBank()).thenReturn(bank);
		when(guild.isBankrupt()).thenReturn(false);
		when(guild.isBase()).thenReturn(false);
		when(guild.getFaction()).thenReturn(faction);
		when(guild.getId()).thenReturn(id);
		when(guild.getDividendPercent()).thenReturn(0.0);
		when(guild.getDividendEligibleMembers()).thenReturn(Collections.emptyList());
		when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
		when(guild.getUpgrades()).thenReturn(Collections.emptyList());
		LoanHandler handler = mock(LoanHandler.class);
		when(handler.getLoansTaken()).thenReturn(Collections.emptyList());
		when(handler.getLoansGiven()).thenReturn(Collections.emptyList());
		when(guild.getLoanHandler()).thenReturn(handler);
		GuildHandler guildHandler = mock(GuildHandler.class);
		when(faction.getGuildHandler()).thenReturn(guildHandler);
		when(guildHandler.getGuilds()).thenReturn(List.of(guild));
		return guild;
	}
}
