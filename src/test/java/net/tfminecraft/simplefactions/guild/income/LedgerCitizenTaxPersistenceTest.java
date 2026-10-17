package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.economy.FrozenReads;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.LoanHandler;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;

class LedgerCitizenTaxPersistenceTest {

	@Test
	void transactionContributorsCombineWithLegacyRowsWithoutChangingSavedAmounts() {
		Guild guild = mock(Guild.class);
		UUID incarnation = UUID.randomUUID();
		UUID character = UUID.randomUUID();
		when(guild.getIncarnationId()).thenReturn(incarnation);
		Ledger ledger = new Ledger(guild);
		ledger.setCitizenTaxes(Map.of(character.toString(), 0.50));
		try (var reads = mockStatic(FrozenReads.class)) {
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(1234L);
			reads.when(() -> FrozenReads.taxContributors(incarnation)).thenReturn(Map.of(character, 1000L));
			assertEquals(Map.of(character.toString(), 10.50, "Other citizen taxes", 1.84), ledger.getCitizenTaxBreakdown());
			assertEquals(Map.of(character.toString(), 0.50), ledger.getCitizenTaxesCopy());
		}
	}

	@Test
	void heldTaxIsVisibleWithoutLegacyContributorEntries() {
		Guild guild = mock(Guild.class);
		UUID incarnation = UUID.randomUUID();
		when(guild.getIncarnationId()).thenReturn(incarnation);
		when(guild.isBase()).thenReturn(true);
		when(guild.getBank()).thenReturn(mock(Bank.class));
		when(guild.getFaction()).thenReturn(mock(Faction.class));
		Ledger ledger = new Ledger(guild);
		try (var reads = mockStatic(FrozenReads.class)) {
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(1234L);
			assertTrue(ledger.getCitizenTaxesCopy().isEmpty());
			assertEquals(12.34, ledger.getIncome(Cashflow.CITIZENS));
			assertEquals(Map.of("Other citizen taxes", 12.34), ledger.getCitizenTaxBreakdown());
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(0L);
			assertEquals(0.0, ledger.getIncome(Cashflow.CITIZENS));
			assertTrue(ledger.getCitizenTaxBreakdown().isEmpty());
		}
	}

	@Test
	void fractionalLegacyContributorsAreRoundedBeforeReconcilingHeldTax() {
		Guild guild = mock(Guild.class);
		UUID incarnation = UUID.randomUUID();
		when(guild.getIncarnationId()).thenReturn(incarnation);
		Ledger ledger = new Ledger(guild);
		ledger.setCitizenTaxes(Map.of("Alice", 0.005));
		try (var reads = mockStatic(FrozenReads.class)) {
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(1L);
			assertEquals(Map.of("Alice", 0.01), ledger.getCitizenTaxBreakdown());
			assertEquals(Map.of("Alice", 0.005), ledger.getCitizenTaxesCopy());
			ledger.setCitizenTaxes(Map.of("Alice", 0.005, "Bob", 0.005));
			assertEquals(Map.of("Other citizen taxes", 0.01), ledger.getCitizenTaxBreakdown());
			ledger.setCitizenTaxes(Map.of("Alice", 0.004, "Bob", 0.004));
			assertEquals(Map.of("Other citizen taxes", 0.01), ledger.getCitizenTaxBreakdown());
		}
	}

	@Test
	void contributorBreakdownReconcilesToHeldTaxAndDoesNotChangeSavedEntries() {
		Guild guild = mock(Guild.class);
		UUID incarnation = UUID.randomUUID();
		when(guild.getIncarnationId()).thenReturn(incarnation);
		Ledger ledger = new Ledger(guild);
		ledger.setCitizenTaxes(Map.of("Alice", 0.20, "Bob", 0.10));
		try (var reads = mockStatic(FrozenReads.class)) {
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(80L);
			assertEquals(Map.of("Alice", 0.20, "Bob", 0.10, "Other citizen taxes", 0.50), ledger.getCitizenTaxBreakdown());
			assertEquals("Other citizen taxes", ledger.getCitizenTaxEntriesDescending().getFirst().getKey());
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(30L);
			Map<String, Double> displayed = ledger.getCitizenTaxBreakdown();
			assertEquals(Map.of("Alice", 0.20, "Bob", 0.10), displayed);
			displayed.clear();
			assertEquals(Map.of("Alice", 0.20, "Bob", 0.10), ledger.getCitizenTaxesCopy());
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(15L);
			assertEquals(Map.of("Other citizen taxes", 0.15), ledger.getCitizenTaxBreakdown());
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(0L);
			assertTrue(ledger.getCitizenTaxBreakdown().isEmpty());
			ledger.setCitizenTaxes(Map.of("Alice", Double.NaN));
			reads.when(() -> FrozenReads.balance("guildtax:" + incarnation)).thenReturn(30L);
			assertEquals(Map.of("Other citizen taxes", 0.30), ledger.getCitizenTaxBreakdown());
		}
	}

	@Test
	void oldJsonWithoutCitizenTaxes_loadsEmpty() {
		GuildData data = JsonUtil.GSON.fromJson("{\"id\":\"capital\"}", GuildData.class);
		assertNull(data.citizenTaxes);

		Ledger ledger = new Ledger(mock(Guild.class));
		ledger.setCitizenTaxes(data.citizenTaxes);
		assertTrue(ledger.getCitizenTaxesCopy().isEmpty());
	}

	@Test
	void jsonWithCitizenTaxes_restoresAggregate() {
		GuildData data = JsonUtil.GSON.fromJson(
				"{\"citizen taxes\":{\"Alice\":0.20}}",
				GuildData.class);
		Ledger ledger = new Ledger(mock(Guild.class));
		ledger.setCitizenTaxes(data.citizenTaxes);
		assertEquals(0.20, ledger.getCitizenTaxesCopy().get("Alice"), 1e-9);
		assertEquals(0.20, sum(ledger.getCitizenTaxesCopy()), 1e-9);
	}

	@Test
	void saveThenParse_roundTripsAmounts() {
		GuildData original = new GuildData();
		original.citizenTaxes = Map.of("Alice", 0.20, "Bob", 0.10);
		String json = JsonUtil.GSON.toJson(original);
		assertTrue(json.contains("citizen taxes"));

		GuildData restored = JsonUtil.GSON.fromJson(json, GuildData.class);
		Ledger ledger = new Ledger(mock(Guild.class));
		ledger.setCitizenTaxes(restored.citizenTaxes);
		assertEquals(0.20, ledger.getCitizenTaxesCopy().get("Alice"), 1e-9);
		assertEquals(0.10, ledger.getCitizenTaxesCopy().get("Bob"), 1e-9);
		assertEquals(0.30, sum(ledger.getCitizenTaxesCopy()), 1e-9);
	}

	@Test
	void nullBankSettle_movesNothingAndKeepsCitizenTaxes() {
		Guild guild = mock(Guild.class);
		LoanHandler loans = mock(LoanHandler.class);
		when(loans.getLoansTaken()).thenReturn(Collections.emptyList());
		when(guild.getLoanHandler()).thenReturn(loans);
		when(guild.isBankrupt()).thenReturn(false);
		when(guild.getBank()).thenReturn(null);
		when(guild.isBase()).thenReturn(false);

		Ledger ledger = new Ledger(guild);
		ledger.addCitizenTaxEntry("Alice", 0.20);
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		ledger.populateDailyTransfers(buffer);

		assertEquals(0.20, ledger.getCitizenTaxesCopy().get("Alice"), 1e-9);
		assertTrue(buffer.getTransfers().isEmpty());
		assertTrue(buffer.getExternalDeltas().isEmpty());
		assertTrue(buffer.getPendingDividendPools().isEmpty());
		assertEquals(0.0, ledger.getIncome(net.tfminecraft.simplefactions.guild.income.Cashflow.TRADE), 1e-9);
	}

	@Test
	void bankruptSettle_keepsCitizenTaxes() {
		Guild guild = mock(Guild.class);
		LoanHandler loans = mock(LoanHandler.class);
		when(loans.getLoansTaken()).thenReturn(Collections.emptyList());
		when(guild.getLoanHandler()).thenReturn(loans);
		when(guild.isBankrupt()).thenReturn(true);

		Ledger ledger = new Ledger(guild);
		ledger.addCitizenTaxEntry("Alice", 0.20);
		ledger.populateDailyTransfers(new DailyGuildTransfers());
		assertEquals(0.20, ledger.getCitizenTaxesCopy().get("Alice"), 1e-9);
	}

	private static double sum(Map<String, Double> taxes) {
		double total = 0;
		for (Double value : taxes.values()) {
			total += value;
		}
		return total;
	}
}
