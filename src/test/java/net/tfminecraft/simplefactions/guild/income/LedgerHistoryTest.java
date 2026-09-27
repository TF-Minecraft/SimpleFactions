package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;

class LedgerHistoryTest {

	@Test
	void closeDay_setsLastDayAndAddsToLifetime() {
		LedgerHistory history = new LedgerHistory();
		history.closeDay(Map.of(LedgerHistory.Source.CITIZENS, Map.of("Alice", 2.0, "Bob", 1.0)));
		history.closeDay(Map.of(LedgerHistory.Source.CITIZENS, Map.of("Alice", 3.0)));

		assertEquals(Map.of("Alice", 3.0), history.getLastDay(LedgerHistory.Source.CITIZENS));
		assertEquals(5.0, history.getLifetime(LedgerHistory.Source.CITIZENS).get("Alice"), 1e-9);
		assertEquals(1.0, history.getLifetime(LedgerHistory.Source.CITIZENS).get("Bob"), 1e-9);
	}

	@Test
	void closeDay_withNothingSettled_clearsLastDayButKeepsLifetime() {
		LedgerHistory history = new LedgerHistory();
		history.closeDay(Map.of(LedgerHistory.Source.TARIFFS, Map.of("Rome", 4.0)));
		history.closeDay(null);

		assertTrue(history.getLastDay(LedgerHistory.Source.TARIFFS).isEmpty());
		assertEquals(4.0, history.getLifetime(LedgerHistory.Source.TARIFFS).get("Rome"), 1e-9);
	}

	@Test
	void deposits_rollOverAtDayEnd() {
		LedgerHistory history = new LedgerHistory();
		history.addDeposit("Alice", 10.0);
		history.addDeposit("Alice", 5.0);
		history.addDeposit("Bob", -1.0);
		assertEquals(Map.of("Alice", 15.0), history.getDepositsToday());
		assertTrue(history.getLastDay(LedgerHistory.Source.DEPOSITS).isEmpty());

		history.closeDay(Map.of());
		assertTrue(history.getDepositsToday().isEmpty());
		assertEquals(15.0, history.getLastDay(LedgerHistory.Source.DEPOSITS).get("Alice"), 1e-9);
		assertEquals(15.0, history.getLifetime(LedgerHistory.Source.DEPOSITS).get("Alice"), 1e-9);
	}

	@Test
	void saveThenParse_roundTrips() {
		LedgerHistory history = new LedgerHistory();
		history.closeDay(Map.of(LedgerHistory.Source.VASSALS, Map.of("Gaul", 7.5)));
		history.addDeposit("Alice", 3.0);

		GuildData data = new GuildData();
		data.ledgerLastDay = history.getLastDayCopy();
		data.ledgerLifetime = history.getLifetimeCopy();
		data.depositsToday = history.getDepositsTodayCopy();
		GuildData restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(data), GuildData.class);

		LedgerHistory loaded = new LedgerHistory();
		loaded.load(restored.ledgerLastDay, restored.ledgerLifetime, restored.depositsToday);
		assertEquals(7.5, loaded.getLastDay(LedgerHistory.Source.VASSALS).get("Gaul"), 1e-9);
		assertEquals(7.5, loaded.getLifetime(LedgerHistory.Source.VASSALS).get("Gaul"), 1e-9);
		assertEquals(3.0, loaded.getDepositsToday().get("Alice"), 1e-9);
	}

	@Test
	void oldJsonWithoutHistory_loadsEmpty() {
		GuildData data = JsonUtil.GSON.fromJson("{\"id\":\"capital\"}", GuildData.class);
		LedgerHistory loaded = new LedgerHistory();
		loaded.load(data.ledgerLastDay, data.ledgerLifetime, data.depositsToday);
		for (LedgerHistory.Source source : LedgerHistory.Source.values()) {
			assertTrue(loaded.getLastDay(source).isEmpty());
			assertTrue(loaded.getLifetime(source).isEmpty());
		}
		assertTrue(loaded.getDepositsToday().isEmpty());
	}

	@Test
	void collectHistoryDay_recordsCitizensAndTariffsForTheReceivingCapital() {
		Faction rome = mock(Faction.class);
		when(rome.getName()).thenReturn("Rome");
		Guild romeCapital = capital(rome, mock(Bank.class));
		romeCapital.getLedger().addCitizenTaxEntry("Alice", 2.0);

		Faction gaul = mock(Faction.class);
		when(gaul.getName()).thenReturn("Gaul");
		Guild gaulCapital = capital(gaul, mock(Bank.class));
		TradeBreakdown trade = mock(TradeBreakdown.class);
		when(trade.getTariffsByFactionMap()).thenReturn(new java.util.HashMap<>(Map.of(rome, 4.0)));
		when(gaulCapital.getTradeBreakdown()).thenReturn(trade);

		var day = Ledger.collectHistoryDay(List.of(romeCapital, gaulCapital));

		assertEquals(Map.of("Alice", 2.0), day.get(romeCapital).get(LedgerHistory.Source.CITIZENS));
		assertEquals(Map.of("Gaul", 4.0), day.get(romeCapital).get(LedgerHistory.Source.TARIFFS));
		assertFalse(day.containsKey(gaulCapital));
		// Collecting is a read: settlement still clears the citizen taxes afterwards.
		assertEquals(2.0, romeCapital.getLedger().getCitizenTaxesCopy().get("Alice"), 1e-9);
	}

	@Test
	void collectHistoryDay_skipsBankruptPayers() {
		Faction rome = mock(Faction.class);
		when(rome.getName()).thenReturn("Rome");
		Guild romeCapital = capital(rome, mock(Bank.class));
		when(romeCapital.isBankrupt()).thenReturn(true);
		romeCapital.getLedger().addCitizenTaxEntry("Alice", 2.0);

		assertTrue(Ledger.collectHistoryDay(List.of(romeCapital)).isEmpty());
	}

	private static Guild capital(Faction faction, Bank bank) {
		Guild guild = mock(Guild.class);
		when(guild.getFaction()).thenReturn(faction);
		when(guild.isBase()).thenReturn(true);
		when(guild.getBank()).thenReturn(bank);
		when(faction.getOrCreateMainGuild()).thenReturn(guild);
		// No tribute modifiers, so the heavy taxable-income path is not walked.
		when(faction.getModifiers()).thenReturn(null);
		Ledger ledger = new Ledger(guild);
		when(guild.getLedger()).thenReturn(ledger);
		return guild;
	}
}
