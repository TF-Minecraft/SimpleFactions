package me.Plugins.SimpleFactions.Guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsObligation;
import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class WarReparationsLedgerTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions = new ArrayList<>();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void payerMainGuild_paymentIsNegativePercentOfGross() {
		Faction payer = mockFaction("atk");
		Faction payee = mockFaction("def");
		when(payer.getWarReparationsObligations()).thenReturn(List.of(
				new WarReparationsObligation("def", 25, 10)));
		when(payee.getWarReparationsObligations()).thenReturn(List.of());
		FactionManager.factions.add(payer);
		FactionManager.factions.add(payee);

		Guild payerGuild = mockGuild(payer, true, 200.0);
		when(payer.getOrCreateMainGuild()).thenReturn(payerGuild);
		Ledger ledger = new Ledger(payerGuild);

		assertEquals(-50.0, ledger.getIncome(Cashflow.WAR_REPARATIONS_PAYMENT));
	}

	@Test
	void payeeMainGuild_incomeIsPositiveFromPayerGross() {
		Faction payer = mockFaction("atk");
		Faction payee = mockFaction("def");
		when(payer.getWarReparationsObligations()).thenReturn(List.of(
				new WarReparationsObligation("def", 25, 10)));
		when(payee.getWarReparationsObligations()).thenReturn(List.of());
		FactionManager.factions.add(payer);
		FactionManager.factions.add(payee);

		Guild payerGuild = mockGuild(payer, true, 200.0);
		Guild payeeGuild = mockGuild(payee, true, 0.0);
		when(payer.getOrCreateMainGuild()).thenReturn(payerGuild);
		when(payee.getOrCreateMainGuild()).thenReturn(payeeGuild);

		Ledger payeeLedger = new Ledger(payeeGuild);
		assertEquals(50.0, payeeLedger.getIncome(Cashflow.WAR_REPARATIONS));
	}

	@Test
	void subsidiaryGuild_reparationsAreZero() {
		Faction payer = mockFaction("atk");
		when(payer.getWarReparationsObligations()).thenReturn(List.of(
				new WarReparationsObligation("def", 25, 10)));
		Guild sub = mockGuild(payer, false, 200.0);
		Ledger ledger = new Ledger(sub);
		assertEquals(0.0, ledger.getIncome(Cashflow.WAR_REPARATIONS_PAYMENT));
		assertEquals(0.0, ledger.getIncome(Cashflow.WAR_REPARATIONS));
	}

	@Test
	void tributeAndReparationsTogether_doNotRecursivelyOverflow() {
		Faction suzerain = mockFaction("def");
		Faction tributary = mockFaction("atk");
		when(tributary.getModifiers()).thenReturn(List.of(
				new FactionModifier(suzerain, FactionModifiers.TRIBUTE, 10)));
		when(tributary.getWarReparationsObligations()).thenReturn(List.of(
				new WarReparationsObligation("def", 25, 10)));
		when(suzerain.getWarReparationsObligations()).thenReturn(List.of());
		when(suzerain.getModifiers()).thenReturn(List.of());
		FactionManager.factions.add(suzerain);
		FactionManager.factions.add(tributary);

		Guild tributaryGuild = mockGuild(tributary, true, 200.0);
		Guild suzerainGuild = mockGuild(suzerain, true, 0.0);
		when(tributary.getOrCreateMainGuild()).thenReturn(tributaryGuild);
		when(suzerain.getOrCreateMainGuild()).thenReturn(suzerainGuild);

		Ledger suzerainLedger = new Ledger(suzerainGuild);
		Ledger tributaryLedger = new Ledger(tributaryGuild);

		assertEquals(50.0, suzerainLedger.getIncome(Cashflow.WAR_REPARATIONS));
		assertEquals(20.0, suzerainLedger.getIncome(Cashflow.TRIBUTES));
		assertEquals(-50.0, tributaryLedger.getIncome(Cashflow.WAR_REPARATIONS_PAYMENT));
		assertEquals(70.0, suzerainLedger.getGrossTaxableIncome());
	}

	private static Faction mockFaction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		Military military = mock(Military.class);
		when(military.getTotalUpkeep()).thenReturn(0.0);
		when(faction.getMilitary()).thenReturn(military);
		InstallationHandler installations = mock(InstallationHandler.class);
		when(installations.getAll()).thenReturn(List.of());
		when(faction.getInstallationHandler()).thenReturn(installations);
		when(faction.getPenalty()).thenReturn(0.0);
		GuildHandler guildHandler = mock(GuildHandler.class);
		when(faction.getGuildHandler()).thenReturn(guildHandler);
		when(guildHandler.getGuilds()).thenReturn(List.of());
		return faction;
	}

	private static Guild mockGuild(Faction faction, boolean base, double tradeIncome) {
		Guild guild = mock(Guild.class);
		when(guild.isBankrupt()).thenReturn(false);
		when(guild.isBase()).thenReturn(base);
		when(guild.getFaction()).thenReturn(faction);
		TradeBreakdown breakdown = new TradeBreakdown();
		breakdown.setIncome(tradeIncome);
		when(guild.getTradeBreakdown()).thenReturn(breakdown);
		LoanHandler loanHandler = mock(LoanHandler.class);
		when(loanHandler.getLoansTaken()).thenReturn(Collections.emptyList());
		when(loanHandler.getLoansGiven()).thenReturn(Collections.emptyList());
		when(guild.getLoanHandler()).thenReturn(loanHandler);
		when(guild.getUpgrades()).thenReturn(Collections.emptyList());
		when(guild.getLedger()).thenAnswer(invocation -> new Ledger(guild));
		return guild;
	}
}
