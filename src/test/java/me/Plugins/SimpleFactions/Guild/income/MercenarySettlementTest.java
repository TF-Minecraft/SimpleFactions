package me.Plugins.SimpleFactions.Guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.Utils.DailyGuildTransfers;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.mercenary.company.CompanyFixture;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;
import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompanyService;
import me.Plugins.SimpleFactions.mercenary.contract.ContractAccrualService;
import me.Plugins.SimpleFactions.mercenary.contract.ContractFixture;
import me.Plugins.SimpleFactions.mercenary.contract.ContractKind;
import me.Plugins.SimpleFactions.mercenary.contract.ContractTerms;
import me.Plugins.SimpleFactions.mercenary.contract.MercenaryContract;

/**
 * The daily leg end to end: the pre-pass accrues and pushes, each ledger buffers
 * its own side, and the buckets are empty afterwards so a day cannot be paid
 * twice. Drives the three steps of {@code FactionManager.settleIncome} directly,
 * because the rest of that method needs a running server.
 */
class MercenarySettlementTest {

	private MercenaryCompany company;
	private Party host;
	private Party hirer;

	@BeforeEach
	void setUp() {
		ContractFixture.installConfig();
		CompanyFixture.installMercenaryPrototype();
		CompanyFixture.installCompanyUpgrades();

		host = new Party("hired_blades", "hired_blades_realm", false, 10000);
		hirer = new Party("brume_capital", "brume", true, 10000);
		FactionManager.factions.clear();
		FactionManager.factions.add(host.faction);
		FactionManager.factions.add(hirer.faction);

		MercenaryCompanyService.requestFormation(host.guild, "Ivar", "Hired Blades");
		company = host.company();
		for (int i = 0; i < me.Plugins.SimpleFactions.Cache.mercenaryFormationSeconds; i++) {
			company.tick();
		}
		while (company.getSlots() < 2) {
			for (int i = 0; i < company.getSlots(); i++) {
				company.enlist("Soldier" + i);
			}
			company.enqueueExpansion();
			for (int i = 0; i < me.Plugins.SimpleFactions.Cache.mercenaryFormationSeconds; i++) {
				company.tick();
			}
		}
		host.balance = 10000;
		hirer.balance = 10000;
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		CompanyFixture.clearCompanyUpgrades();
		CompanyFixture.clearRegiments();
	}

	@Test
	void oneDayMovesExactlyTheAccruedDayPrice() {
		activeContract(2);

		settle();

		// Two slots at the 10 denar minimum, less two slots of 8 denar upkeep.
		assertEquals(10000 + 20 - 16, host.balance, 1e-9);
		assertEquals(10000 - 20, hirer.balance, 1e-9);
	}

	@Test
	void aBattleDayCostsTheDayPriceAndTheBattlePrice() {
		MercenaryContract contract = activeContract(2);
		contract.accrueBattleCharge("battle-1", contract.getBattlePrice());

		settle();

		// 100 denars of battle plus 20 of day, less 16 of slot upkeep.
		assertEquals(10000 + 120 - 16, host.balance, 1e-9);
		assertEquals(10000 - 120, hirer.balance, 1e-9);
	}

	@Test
	void anAbsenceRefundTravelsBackToTheHirerAsItsOwnLeg() {
		MercenaryContract contract = activeContract(2);
		contract.accrueAbsenceRefund("battle-1", 2 * contract.getAbsenceRefundPerSlotPerBattle());

		double owedToCompany = contract.getAccruedToCompany() + contract.getDailyPrice();
		double owedToHirer = contract.getAccruedToHirer();
		assertEquals(100.0, owedToHirer, 1e-9);

		settle();

		assertEquals(10000 + owedToCompany - owedToHirer - 16, host.balance, 1e-9);
		assertEquals(10000 - owedToCompany + owedToHirer, hirer.balance, 1e-9);
	}

	@Test
	void bucketsAreEmptyAfterSettlementAndASecondPassMovesNothingNew() {
		MercenaryContract contract = activeContract(2);
		contract.accrueBattleCharge("battle-1", contract.getBattlePrice());

		settle();
		assertEquals(0.0, contract.getAccruedToCompany(), 1e-9);
		assertEquals(0.0, contract.getAccruedToHirer(), 1e-9);

		double hostAfterFirst = host.balance;
		double hirerAfterFirst = hirer.balance;

		settle();

		// Only the next day's price and upkeep, never the battle again.
		assertEquals(hostAfterFirst + 20 - 16, host.balance, 1e-9);
		assertEquals(hirerAfterFirst - 20, hirer.balance, 1e-9);
	}

	@Test
	void aBankruptHostMovesNothing() {
		activeContract(2);
		when(host.guild.isBankrupt()).thenReturn(true);

		settle();

		assertEquals(10000, host.balance, 1e-9);
		assertEquals(10000, hirer.balance, 1e-9);
	}

	@Test
	void aBankruptHirerMovesNothing() {
		activeContract(2);
		when(hirer.guild.isBankrupt()).thenReturn(true);

		settle();

		// The host still pays its own slot upkeep; only the contract stops moving.
		assertEquals(10000 - 16, host.balance, 1e-9);
		assertEquals(10000, hirer.balance, 1e-9);
	}

	/**
	 * Phase 6 checklist step 16. Five guild lines plus the player's own {@code WAGES}
	 * make the six a mid-contract tick is supposed to show. Read after the pre-pass
	 * and before {@code populateDailyTransfers}, which is what clears the buckets.
	 */
	@Test
	void aMidContractDayShowsEveryLineWithItsOwnSign() {
		company.getWageSettings().setActivePercent(20);
		MercenaryContract contract = activeContract(2);
		contract.accrueAbsenceRefund("battle-1", 2 * contract.getAbsenceRefundPerSlotPerBattle());

		ContractAccrualService.accrueDailyAndPush();

		Ledger hostLedger = host.guild.getLedger();
		Ledger hirerLedger = hirer.guild.getLedger();

		// The company earns the day price, owes the refund, and owes its payroll.
		assertEquals(20.0, hostLedger.getIncome(Cashflow.MERCENARY_CONTRACT), 1e-9);
		assertEquals(-100.0, hostLedger.getIncome(Cashflow.REFUND_PAYMENTS), 1e-9);
		assertEquals(-2.0, hostLedger.getIncome(Cashflow.WAGE_PAYMENTS), 1e-9);
		// Slot upkeep rides MILITARY_UPKEEP rather than a sixth guild cashflow.
		assertEquals(-16.0, hostLedger.getIncome(Cashflow.MILITARY_UPKEEP), 1e-9);

		// The hirer owns no contract object, so it reads only what was pushed to it.
		assertEquals(-20.0, hirerLedger.getIncome(Cashflow.MERCENARY_PAYMENTS), 1e-9);
		assertEquals(100.0, hirerLedger.getIncome(Cashflow.REFUNDS), 1e-9);

		// Income and refunds are never netted into one line on either side.
		assertTrue(hostLedger.getIncome(Cashflow.MERCENARY_CONTRACT) > 0);
		assertTrue(hostLedger.getIncome(Cashflow.REFUND_PAYMENTS) < 0);
	}

	/**
	 * Batch 5.2 of the phase asks whether the new gross-counted contract income
	 * feeding tribute and reparations is intended. It is: contract income is
	 * business income. Refunds and payroll are not, so they must not move the base.
	 */
	@Test
	void contractIncomeFeedsTheTributeBaseButRefundsAndWagesDoNot() {
		company.getWageSettings().setActivePercent(20);
		Ledger hostLedger = host.guild.getLedger();
		double before = hostLedger.getInternalTaxableIncome();

		MercenaryContract contract = activeContract(2);
		contract.accrueAbsenceRefund("battle-1", 2 * contract.getAbsenceRefundPerSlotPerBattle());
		ContractAccrualService.accrueDailyAndPush();

		// The 20 denars of day price, and nothing else that moved this tick.
		assertEquals(before + 20.0, hostLedger.getInternalTaxableIncome(), 1e-9);
		assertEquals(before + 20.0, hostLedger.getReparationsTaxableIncome(), 1e-9);
	}

	@Test
	void slotAndUpgradeUpkeepLeaveTheHostGuild() {
		company.enqueueUpgrade(company.getUpgrade("company_health"));
		for (int i = 0; i < 10; i++) {
			company.tick();
		}
		assertTrue(company.getUpgradeUpkeep() > 0, "the upgrade should be levelled and costing");

		settle();

		assertEquals(10000 - company.getSlotUpkeep() - company.getUpgradeUpkeep(), host.balance, 1e-9);
	}

	/* =====================================================
	 * Harness
	 * ===================================================== */

	private MercenaryContract activeContract(int slots) {
		MercenaryContract contract = new MercenaryContract(
				company, hirer.faction, ContractKind.MERCENARY, ContractFixture.validTerms(slots),
				System.currentTimeMillis());
		company.getContractHandler().add(contract);
		contract.activate();
		return contract;
	}

	/** The three steps of settleIncome that Phase 5 touches. */
	private void settle() {
		ContractAccrualService.accrueDailyAndPush();

		DailyGuildTransfers buffer = new DailyGuildTransfers();
		for (Guild g : FactionManager.getAllGuilds()) {
			g.getLedger().populateDailyTransfers(buffer);
		}

		Map<Guild, Double> deltas = new HashMap<>();
		for (var from : buffer.getTransfers().entrySet()) {
			for (var to : from.getValue().entrySet()) {
				deltas.merge(from.getKey(), -to.getValue(), Double::sum);
				deltas.merge(to.getKey(), to.getValue(), Double::sum);
			}
		}
		for (var entry : buffer.getExternalDeltas().entrySet()) {
			deltas.merge(entry.getKey(), entry.getValue(), Double::sum);
		}
		for (var entry : deltas.entrySet()) {
			double amount = Formatter.formatDouble(entry.getValue());
			if (amount == 0.0) continue;
			entry.getKey().getBank().deposit(amount);
		}
	}

	private static final class Party {
		final Guild guild = mock(Guild.class);
		final Bank bank = mock(Bank.class);
		final Faction faction = mock(Faction.class);
		double balance;
		private MercenaryCompany company;

		Party(String guildId, String factionId, boolean base, double balance) {
			this.balance = balance;

			Military military = mock(Military.class);
			when(military.getTotalUpkeep()).thenReturn(0.0);
			InstallationHandler installations = mock(InstallationHandler.class);
			when(installations.getAll()).thenReturn(Collections.emptyList());
			when(faction.getId()).thenReturn(factionId);
			when(faction.getName()).thenReturn(factionId);
			when(faction.getLeader()).thenReturn(factionId + "_ruler");
			when(faction.getMilitary()).thenReturn(military);
			when(faction.getInstallationHandler()).thenReturn(installations);
			when(faction.getPenalty()).thenReturn(0.0);
			when(faction.getModifiers()).thenReturn(new ArrayList<>());
			when(faction.getWarReparationsObligations()).thenReturn(new ArrayList<>());
			when(faction.getOrCreateMainGuild()).thenReturn(guild);
			GuildHandler handler = mock(GuildHandler.class);
			when(handler.getGuilds()).thenReturn(new ArrayList<>(List.of(guild)));
			when(faction.getGuildHandler()).thenReturn(handler);

			when(guild.getId()).thenReturn(guildId);
			when(guild.getLeader()).thenReturn("Ivar");
			when(guild.getBannerPatterns()).thenReturn(new ArrayList<>(List.of("white.base")));
			when(guild.isBase()).thenReturn(base);
			when(guild.isBankrupt()).thenReturn(false);
			when(guild.getFaction()).thenReturn(faction);
			when(guild.getBank()).thenReturn(bank);
			when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
			when(guild.getUpgrades()).thenReturn(Collections.emptyList());
			when(guild.getDividendPercent()).thenReturn(0.0);
			when(guild.getDividendEligibleMembers()).thenReturn(Collections.emptyList());
			LoanHandler loans = mock(LoanHandler.class);
			when(loans.getLoansTaken()).thenReturn(Collections.emptyList());
			when(loans.getLoansGiven()).thenReturn(Collections.emptyList());
			when(guild.getLoanHandler()).thenReturn(loans);
			when(guild.getCompany()).thenAnswer(i -> company);
			when(guild.hasCompany()).thenAnswer(i -> company != null && company.isFormed());
			when(guild.isFoundingCompany()).thenAnswer(i -> company != null && company.isForming());
			doAnswer(i -> {
				company = i.getArgument(0);
				return null;
			}).when(guild).setCompany(org.mockito.ArgumentMatchers.any());
			when(guild.getLedger()).thenReturn(new Ledger(guild));

			when(bank.getWealth()).thenAnswer(i -> this.balance);
			doAnswer(i -> {
				this.balance -= (double) (Double) i.getArgument(0);
				return null;
			}).when(bank).withdraw(anyDouble());
			doAnswer(i -> {
				this.balance += (double) (Double) i.getArgument(0);
				return null;
			}).when(bank).deposit(anyDouble());
		}

		MercenaryCompany company() {
			return company;
		}
	}
}
