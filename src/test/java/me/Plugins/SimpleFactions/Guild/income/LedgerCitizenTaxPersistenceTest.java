package me.Plugins.SimpleFactions.Guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Database.GuildData;
import me.Plugins.SimpleFactions.Database.JsonUtil;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Utils.DailyGuildTransfers;

class LedgerCitizenTaxPersistenceTest {

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
