package me.Plugins.SimpleFactions.Guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Loaders.InstallationConfigLoader;
import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class LedgerNetIncomeTest {
	private MockedStatic<FactionManager> factionManagerStatic;
	private MockedStatic<RelationManager> relationManagerStatic;
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-ledger-net-income-");
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
		Ledger.setNodeUpkeepLookup(null);
		if (tempDir != null) {
			Files.walk(tempDir)
				.sorted(java.util.Comparator.reverseOrder())
				.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void installations_includedInNetIncome_forBaseGuild() {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		InstallationHandler installationHandler = mock(InstallationHandler.class);
		Military military = mock(Military.class);
		Installation fort = new Installation("f1", "Fort", InstallationKind.FORT, 1, 0, 0, 0L);

		when(faction.getInstallationHandler()).thenReturn(installationHandler);
		when(installationHandler.getAll()).thenReturn(List.of(fort));
		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(0.0);

		Ledger ledger = baseLedger(faction, guild, true);

		assertEquals(-50.0, ledger.getIncome(Cashflow.INSTALLATIONS));
		assertEquals(-50.0, ledger.getNetIncome());
	}

	@Test
	void militaryUpkeep_shownAndIncludedInNetIncome_forBaseGuild() {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		Military military = mock(Military.class);
		InstallationHandler installationHandler = mock(InstallationHandler.class);

		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(120.0);
		when(faction.getInstallationHandler()).thenReturn(installationHandler);
		when(installationHandler.getAll()).thenReturn(Collections.emptyList());

		Ledger ledger = baseLedger(faction, guild, true);

		assertEquals(-120.0, ledger.getIncome(Cashflow.MILITARY_UPKEEP));
		assertEquals(-120.0, ledger.getNetIncome());
	}

	@Test
	void installationsAndMilitary_returnZero_forSubGuild() {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		Military military = mock(Military.class);
		InstallationHandler installationHandler = mock(InstallationHandler.class);

		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(120.0);
		when(faction.getInstallationHandler()).thenReturn(installationHandler);
		when(installationHandler.getAll()).thenReturn(List.of(
				new Installation("f1", "Fort", InstallationKind.FORT, 1, 0, 0, 0L)));

		Ledger ledger = baseLedger(faction, guild, false);

		assertEquals(0.0, ledger.getIncome(Cashflow.INSTALLATIONS));
		assertEquals(0.0, ledger.getIncome(Cashflow.MILITARY_UPKEEP));
	}

	@Test
	void nodes_includedInNetIncome_fromLookup() {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		Military military = mock(Military.class);
		InstallationHandler installationHandler = mock(InstallationHandler.class);

		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(0.0);
		when(faction.getInstallationHandler()).thenReturn(installationHandler);
		when(installationHandler.getAll()).thenReturn(Collections.emptyList());

		Ledger.setNodeUpkeepLookup(g -> 40.0);
		Ledger ledger = baseLedger(faction, guild, true);

		assertEquals(-40.0, ledger.getIncome(Cashflow.NODES));
		assertEquals(-40.0, ledger.getNetIncome());
	}

	@Test
	void nodes_zeroWhenLookupUnset() {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		Military military = mock(Military.class);
		InstallationHandler installationHandler = mock(InstallationHandler.class);

		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(0.0);
		when(faction.getInstallationHandler()).thenReturn(installationHandler);
		when(installationHandler.getAll()).thenReturn(Collections.emptyList());

		Ledger ledger = baseLedger(faction, guild, true);

		assertEquals(0.0, ledger.getIncome(Cashflow.NODES));
		assertEquals(0.0, ledger.getNetIncome());
	}

	@Test
	void netIncome_sumsTradeInstallationsAndMilitary() {
		Faction faction = mock(Faction.class);
		Guild guild = mock(Guild.class);
		Military military = mock(Military.class);
		InstallationHandler installationHandler = mock(InstallationHandler.class);

		when(faction.getMilitary()).thenReturn(military);
		when(military.getTotalUpkeep()).thenReturn(100.0);
		when(faction.getInstallationHandler()).thenReturn(installationHandler);
		when(installationHandler.getAll()).thenReturn(List.of(
				new Installation("f1", "Fort", InstallationKind.FORT, 1, 0, 0, 0L)));

		TradeBreakdown breakdown = new TradeBreakdown();
		breakdown.setIncome(200.0);

		Ledger ledger = baseLedger(faction, guild, true, breakdown);

		assertEquals(50.0, ledger.getNetIncome());
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

	private Ledger baseLedger(Faction faction, Guild guild, boolean baseGuild) {
		return baseLedger(faction, guild, baseGuild, new TradeBreakdown());
	}

	private Ledger baseLedger(Faction faction, Guild guild, boolean baseGuild, TradeBreakdown breakdown) {
		when(guild.isBankrupt()).thenReturn(false);
		when(guild.isBase()).thenReturn(baseGuild);
		when(guild.getFaction()).thenReturn(faction);
		when(guild.getTradeBreakdown()).thenReturn(breakdown);

		LoanHandler loanHandler = mock(LoanHandler.class);
		when(loanHandler.getLoansTaken()).thenReturn(Collections.emptyList());
		when(loanHandler.getLoansGiven()).thenReturn(Collections.emptyList());
		when(guild.getLoanHandler()).thenReturn(loanHandler);

		when(guild.getUpgrades()).thenReturn(Collections.emptyList());
		when(faction.getPenalty()).thenReturn(0.0);

		GuildHandler guildHandler = mock(GuildHandler.class);
		when(faction.getGuildHandler()).thenReturn(guildHandler);
		when(guildHandler.getGuilds()).thenReturn(List.of(guild));

		return new Ledger(guild);
	}
}
