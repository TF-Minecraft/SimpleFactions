package me.Plugins.SimpleFactions.Guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.contract.ContractHandler;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;

/**
 * The six new cashflows read absolute denars accrued onto contract objects and
 * pushed maps, never another guild's ledger. That is the whole reason they are
 * structurally incapable of recursing.
 */
class LedgerMercenaryTest {

	private MockedStatic<FactionManager> factionManagerStatic;
	private MockedStatic<RelationManager> relationManagerStatic;

	@BeforeEach
	void setUp() {
		factionManagerStatic = mockStatic(FactionManager.class);
		factionManagerStatic.when(FactionManager::getAllGuilds).thenReturn(Collections.emptyList());
		FactionManager.factions = new ArrayList<>();
		relationManagerStatic = mockStatic(RelationManager.class);
		relationManagerStatic.when(() -> RelationManager.getSubjects(any())).thenReturn(Collections.emptyList());
	}

	@AfterEach
	void tearDown() {
		if (factionManagerStatic != null) factionManagerStatic.close();
		if (relationManagerStatic != null) relationManagerStatic.close();
		FactionManager.factions = new ArrayList<>();
	}

	@Test
	void companyEarnsContractsAndOwesRefundsAndWages() {
		Fixture host = hostGuild(false);
		host.contract(300.0, 50.0);
		host.wages(Map.of("Sigrun", 12.0));

		assertEquals(300.0, host.ledger.getIncome(Cashflow.MERCENARY_CONTRACT), 1e-9);
		assertEquals(-50.0, host.ledger.getIncome(Cashflow.REFUND_PAYMENTS), 1e-9);
		assertEquals(-12.0, host.ledger.getIncome(Cashflow.WAGE_PAYMENTS), 1e-9);
	}

	@Test
	void hiringCapitalReadsOnlyWhatWasPushedToIt() {
		Fixture capital = plainGuild(true);
		assertEquals(0.0, capital.ledger.getIncome(Cashflow.MERCENARY_PAYMENTS), 1e-9);

		capital.ledger.addMercenaryPaymentEntry("company", 300.0);
		capital.ledger.addRefundEntry("company", 50.0);

		assertEquals(-300.0, capital.ledger.getIncome(Cashflow.MERCENARY_PAYMENTS), 1e-9);
		assertEquals(50.0, capital.ledger.getIncome(Cashflow.REFUNDS), 1e-9);
	}

	@Test
	void aNonCapitalGuildNeverCarriesTheHirersBill() {
		Fixture sub = plainGuild(false);
		sub.ledger.addMercenaryPaymentEntry("company", 300.0);
		sub.ledger.addRefundEntry("company", 50.0);

		assertEquals(0.0, sub.ledger.getIncome(Cashflow.MERCENARY_PAYMENTS), 1e-9);
		assertEquals(0.0, sub.ledger.getIncome(Cashflow.REFUNDS), 1e-9);
	}

	@Test
	void noNewArmReadsAnotherGuildsLedger() {
		Fixture host = hostGuild(true);
		host.contract(300.0, 50.0);
		host.wages(Map.of("Sigrun", 12.0));
		host.ledger.addMercenaryPaymentEntry("elsewhere", 40.0);
		host.ledger.addRefundEntry("elsewhere", 30.0);

		Guild sibling = mock(Guild.class);
		when(sibling.isBase()).thenReturn(false);
		when(sibling.getLedger()).thenThrow(new AssertionError("a mercenary arm read a foreign ledger"));
		when(host.guildHandler.getGuilds()).thenReturn(List.of(host.guild, sibling));
		Faction vassal = mock(Faction.class);
		when(vassal.getOrCreateMainGuild()).thenReturn(sibling);
		relationManagerStatic.when(() -> RelationManager.getSubjects(any())).thenReturn(List.of(vassal));

		for (Cashflow cf : List.of(
				Cashflow.MERCENARY_CONTRACT,
				Cashflow.MERCENARY_PAYMENTS,
				Cashflow.REFUNDS,
				Cashflow.REFUND_PAYMENTS,
				Cashflow.WAGE_PAYMENTS)) {
			host.ledger.getIncome(cf);
		}
	}

	@Test
	void contractIncomeIsTaxedByGuildTaxButRefundsAreNot() {
		Fixture earning = hostGuild(false);
		earning.contract(300.0, 0.0);
		double taxedOnEarnings = earning.ledger.getIncome(Cashflow.GUILD_PAYMENTS);

		Fixture refunded = hostGuild(false);
		refunded.ledger.addRefundEntry("company", 300.0);
		double taxedOnRefunds = refunded.ledger.getIncome(Cashflow.GUILD_PAYMENTS);

		assertEquals(-30.0, taxedOnEarnings, 1e-9);
		assertEquals(0.0, taxedOnRefunds, 1e-9);
	}

	@Test
	void incomeAndRefundsStayTwoLines() {
		Fixture host = hostGuild(false);
		host.contract(300.0, 300.0);

		double earned = host.ledger.getIncome(Cashflow.MERCENARY_CONTRACT);
		double owed = host.ledger.getIncome(Cashflow.REFUND_PAYMENTS);

		assertEquals(300.0, earned, 1e-9);
		assertEquals(-300.0, owed, 1e-9);
		assertNotEquals(0.0, earned);
		assertTrue(earned + owed == 0.0, "equal and opposite, but never collapsed into one entry");
	}

	@Test
	void companyUpkeepLeavesTheHostGuildEvenWhenItIsNotTheCapital() {
		Fixture host = hostGuild(false);
		when(host.company.getSlotUpkeep()).thenReturn(24.0);
		when(host.company.getUpgradeUpkeep()).thenReturn(30.0);

		assertEquals(-24.0, host.ledger.getIncome(Cashflow.MILITARY_UPKEEP), 1e-9);
		assertEquals(-30.0, host.ledger.getIncome(Cashflow.UPGRADES_UPKEEP), 1e-9);
	}

	@Test
	void aCapitalPaysBothItsArmyAndItsCompany() {
		Fixture host = hostGuild(true);
		when(host.military.getTotalUpkeep()).thenReturn(100.0);
		when(host.company.getSlotUpkeep()).thenReturn(24.0);

		assertEquals(-124.0, host.ledger.getIncome(Cashflow.MILITARY_UPKEEP), 1e-9);
	}

	@Test
	void aGuildWithoutACompanyIsUntouched() {
		Fixture plain = plainGuild(false);
		assertEquals(0.0, plain.ledger.getIncome(Cashflow.MERCENARY_CONTRACT), 1e-9);
		assertEquals(0.0, plain.ledger.getIncome(Cashflow.REFUND_PAYMENTS), 1e-9);
		assertEquals(0.0, plain.ledger.getIncome(Cashflow.WAGE_PAYMENTS), 1e-9);
		assertEquals(0.0, plain.ledger.getIncome(Cashflow.MILITARY_UPKEEP), 1e-9);
		assertEquals(0.0, plain.ledger.getIncome(Cashflow.UPGRADES_UPKEEP), 1e-9);
	}

	/* =====================================================
	 * Fixture
	 * ===================================================== */

	private static final class Fixture {
		Guild guild;
		Faction faction;
		Military military;
		GuildHandler guildHandler;
		MercenaryCompany company;
		ContractHandler contractHandler;
		Ledger ledger;
		final List<MercenaryContract> contracts = new ArrayList<>();

		void contract(double toCompany, double toHirer) {
			MercenaryContract contract = mock(MercenaryContract.class);
			when(contract.getAccruedToCompany()).thenReturn(toCompany);
			when(contract.getAccruedToHirer()).thenReturn(toHirer);
			contracts.add(contract);
			when(contractHandler.getAll()).thenReturn(new ArrayList<>(contracts));
		}

		void wages(Map<String, Double> pending) {
			when(company.getPendingWages()).thenReturn(pending);
		}
	}

	private Fixture plainGuild(boolean base) {
		Fixture f = new Fixture();
		f.faction = mock(Faction.class);
		f.guild = mock(Guild.class);
		f.military = mock(Military.class);
		InstallationHandler installations = mock(InstallationHandler.class);
		when(f.faction.getMilitary()).thenReturn(f.military);
		when(f.military.getTotalUpkeep()).thenReturn(0.0);
		when(f.faction.getInstallationHandler()).thenReturn(installations);
		when(installations.getAll()).thenReturn(Collections.emptyList());
		when(f.faction.getPenalty()).thenReturn(0.0);
		when(f.faction.getTaxRate(eq(TaxTarget.GUILDS), anyString(), anyBoolean())).thenReturn(10.0);
		when(f.faction.getTaxRate(eq(TaxTarget.DIVIDENDS), anyString(), anyBoolean())).thenReturn(0.0);
		when(f.guild.isBankrupt()).thenReturn(false);
		when(f.guild.isBase()).thenReturn(base);
		when(f.guild.getFaction()).thenReturn(f.faction);
		when(f.guild.getId()).thenReturn("company");
		when(f.guild.getDividendPercent()).thenReturn(0.0);
		when(f.guild.getDividendEligibleMembers()).thenReturn(Collections.emptyList());
		when(f.guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
		when(f.guild.getUpgrades()).thenReturn(Collections.emptyList());
		LoanHandler loans = mock(LoanHandler.class);
		when(loans.getLoansTaken()).thenReturn(Collections.emptyList());
		when(loans.getLoansGiven()).thenReturn(Collections.emptyList());
		when(f.guild.getLoanHandler()).thenReturn(loans);
		f.guildHandler = mock(GuildHandler.class);
		when(f.faction.getGuildHandler()).thenReturn(f.guildHandler);
		when(f.guildHandler.getGuilds()).thenReturn(List.of(f.guild));
		f.ledger = new Ledger(f.guild);
		return f;
	}

	private Fixture hostGuild(boolean base) {
		Fixture f = plainGuild(base);
		f.company = mock(MercenaryCompany.class);
		f.contractHandler = mock(ContractHandler.class);
		when(f.company.isFormed()).thenReturn(true);
		when(f.company.getContractHandler()).thenReturn(f.contractHandler);
		when(f.contractHandler.getAll()).thenReturn(Collections.emptyList());
		when(f.company.getPendingWages()).thenReturn(Collections.emptyMap());
		when(f.company.getSlotUpkeep()).thenReturn(0.0);
		when(f.company.getUpgradeUpkeep()).thenReturn(0.0);
		when(f.guild.getCompany()).thenReturn(f.company);
		return f;
	}
}
