package me.Plugins.SimpleFactions.Guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class LedgerDividendTest {
	private MockedStatic<FactionManager> factionManagerStatic;
	private MockedStatic<RelationManager> relationManagerStatic;
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		Cache.dividendRequirePreviousTickMembership = true;
		tempDir = Files.createTempDirectory("sf-ledger-dividend-");
		loadInstallationFixtures();
		factionManagerStatic = mockStatic(FactionManager.class);
		factionManagerStatic.when(FactionManager::getAllGuilds).thenReturn(Collections.emptyList());
		FactionManager.factions = new ArrayList<>();
		relationManagerStatic = mockStatic(RelationManager.class);
		relationManagerStatic.when(() -> RelationManager.getSubjects(any())).thenReturn(Collections.emptyList());
	}

	@AfterEach
	void tearDown() throws IOException {
		if (factionManagerStatic != null) {
			factionManagerStatic.close();
		}
		if (relationManagerStatic != null) {
			relationManagerStatic.close();
		}
		FactionManager.factions = new ArrayList<>();
		if (tempDir != null) {
			Files.walk(tempDir)
				.sorted(java.util.Comparator.reverseOrder())
				.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void baseExcludesDividendCashflows() {
		Ledger ledger = payingLedger(100.0, 20.0, 10.0, List.of("Ann", "Bob"), false, 0.0, 0.0);
		double base = ledger.getDividendBase();
		double net = ledger.getNetIncome();
		assertEquals(100.0, base, 1e-9);
		assertEquals(
				base,
				net - ledger.getIncome(Cashflow.DIVIDEND_PAYOUT) - ledger.getIncome(Cashflow.DIVIDEND_PAYMENT),
				1e-9);
	}

	@Test
	void taxIsWithheldFromPool() {
		Ledger ledger = payingLedger(100.0, 20.0, 10.0, List.of("Ann", "Bob"), false, 0.0, 0.0);
		DividendBreakdown breakdown = ledger.getDividendBreakdown();
		assertEquals(20.0, breakdown.pool(), 1e-9);
		assertEquals(2.0, breakdown.tax(), 1e-9);
		assertEquals(18.0, breakdown.payout(), 1e-9);
		assertEquals(breakdown.pool(), breakdown.tax() + breakdown.payout(), 1e-9);
		assertEquals(9.0, breakdown.perMember(), 1e-9);
		assertEquals(-18.0, ledger.getIncome(Cashflow.DIVIDEND_PAYOUT), 1e-9);
		assertEquals(-2.0, ledger.getIncome(Cashflow.DIVIDEND_PAYMENT), 1e-9);
	}

	@Test
	void negativeBasePaysNothing() {
		Ledger ledger = payingLedger(0.0, 20.0, 10.0, List.of("Ann"), false, 0.0, 0.0);
		extractGuild(ledger).getTradeBreakdown().setUpkeep(40.0);
		assertEquals(0.0, ledger.getDividendBreakdown().pool(), 1e-9);
		assertEquals(0.0, ledger.getIncome(Cashflow.DIVIDEND_PAYOUT), 1e-9);
	}

	@Test
	void baseGuildPaysNothing() {
		Ledger ledger = payingLedger(100.0, 20.0, 10.0, List.of("Ann"), true, 0.0, 0.0);
		assertEquals(0.0, ledger.getIncome(Cashflow.DIVIDEND_PAYOUT), 1e-9);
		assertEquals(0.0, ledger.getIncome(Cashflow.DIVIDEND_PAYMENT), 1e-9);
	}

	@Test
	void bankruptGuildPaysNothing() {
		Ledger ledger = payingLedger(100.0, 20.0, 10.0, List.of("Ann"), false, 0.0, 0.0);
		when(extractGuild(ledger).isBankrupt()).thenReturn(true);
		assertEquals(0.0, ledger.getDividendBase(), 1e-9);
		assertEquals(0.0, ledger.getIncome(Cashflow.DIVIDEND_PAYOUT), 1e-9);
	}

	@Test
	void newMemberExcludedWhenTenureRequired() {
		Ledger ledger = payingLedger(100.0, 20.0, 0.0, List.of("Ann"), false, 0.0, 0.0);
		assertEquals(1, ledger.getDividendBreakdown().eligibleCount());
		assertEquals(20.0, ledger.getDividendBreakdown().perMember(), 1e-9);
	}

	private Ledger payingLedger(
			double trade,
			double percent,
			double taxRate,
			List<String> eligible,
			boolean baseGuild,
			double militaryUpkeep,
			double penalty) {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		Military military = mock(Military.class);
		InstallationHandler installationHandler = mock(InstallationHandler.class);
		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(militaryUpkeep);
		when(faction.getInstallationHandler()).thenReturn(installationHandler);
		when(installationHandler.getAll()).thenReturn(Collections.emptyList());
		when(faction.getPenalty()).thenReturn(penalty);
		when(faction.getTaxRate(eq(TaxTarget.DIVIDENDS), eq("traders"), anyBoolean())).thenReturn(taxRate);
		when(guild.isBankrupt()).thenReturn(false);
		when(guild.isBase()).thenReturn(baseGuild);
		when(guild.getFaction()).thenReturn(faction);
		when(guild.getId()).thenReturn("traders");
		when(guild.getDividendPercent()).thenReturn(percent);
		when(guild.getDividendEligibleMembers()).thenReturn(eligible);
		TradeBreakdown breakdown = new TradeBreakdown();
		breakdown.setIncome(trade);
		when(guild.getTradeBreakdown()).thenReturn(breakdown);
		LoanHandler loanHandler = mock(LoanHandler.class);
		when(loanHandler.getLoansTaken()).thenReturn(Collections.emptyList());
		when(loanHandler.getLoansGiven()).thenReturn(Collections.emptyList());
		when(guild.getLoanHandler()).thenReturn(loanHandler);
		when(guild.getUpgrades()).thenReturn(Collections.emptyList());
		GuildHandler guildHandler = mock(GuildHandler.class);
		when(faction.getGuildHandler()).thenReturn(guildHandler);
		when(guildHandler.getGuilds()).thenReturn(List.of(guild));
		return new Ledger(guild);
	}

	private Guild extractGuild(Ledger ledger) {
		try {
			var field = Ledger.class.getDeclaredField("guild");
			field.setAccessible(true);
			return (Guild) field.get(ledger);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private void loadInstallationFixtures() throws IOException {
		Path vehiclesYaml = tempDir.resolve("vehicles.yml");
		Files.writeString(vehiclesYaml, """
			personal-slot-limit: 1
			categories:
			  ships:
			    ironclad:
			      upkeep: 20
			      size: 1
			  static_emplacements: {}
			  aircraft: {}
			""");
		VehiclesConfigLoader.load(vehiclesYaml.toFile());
		Path installationsYaml = tempDir.resolve("installations.yml");
		Files.writeString(installationsYaml, """
			consent-proximity-blocks: 20
			transfer-request-timeout-seconds: 60
			fort:
			  radius: 80
			  daily-upkeep: 50
			  construction-time: 10
			  slots:
			    static_emplacements: 8
			port:
			  radius: 80
			  daily-upkeep: 20
			  construction-time: 10
			  slots:
			    ships: 8
			airport:
			  radius: 80
			  daily-upkeep: 35
			  construction-time: 10
			  slots:
			    aircraft: 10
			""");
		InstallationConfigLoader.load(installationsYaml.toFile());
	}
}
