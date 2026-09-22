package me.Plugins.SimpleFactions.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.income.Ledger;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.player.PlayerEconomyManager;
import me.Plugins.SimpleFactions.player.income.PlayerCashflow;
import me.Plugins.SimpleFactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;

class PostSettlementPayoutsTest {

	@Test
	void playerPayoutDebitsGuildAndCreditsPlayer() {
		Guild guild = mock(Guild.class);
		Bank bank = mock(Bank.class);
		when(guild.getBank()).thenReturn(bank);
		when(guild.isBankrupt()).thenReturn(false);
		when(bank.getWealth()).thenReturn(100.0);

		UUID player = UUID.randomUUID();
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		buffer.addPlayerPayout(guild, player, 25.0);

		RecordingBank playerBank = new RecordingBank();
		PostSettlementPayouts.apply(buffer, playerBank, new PlayerEconomyManager(), name -> null);

		assertEquals(25.0, playerBank.balance(player), 1e-9);
		verify(bank).withdraw(25.0);
	}

	@Test
	void clampScalesWhenGuildCannotCoverPool() {
		Guild guild = mock(Guild.class);
		Bank bank = mock(Bank.class);
		Faction faction = mock(Faction.class);
		Ledger ledger = mock(Ledger.class);
		when(guild.getBank()).thenReturn(bank);
		when(guild.isBankrupt()).thenReturn(false);
		when(guild.isBase()).thenReturn(false);
		when(guild.getFaction()).thenReturn(faction);
		when(guild.getLedger()).thenReturn(ledger);
		when(bank.getWealth()).thenReturn(5.0);

		UUID ann = UUID.randomUUID();
		when(guild.getDividendEligibleMembers()).thenReturn(List.of("Ann"));
		when(ledger.breakdownForPool(5.0)).thenReturn(
				new me.Plugins.SimpleFactions.Guild.income.DividendBreakdown(100, 5.0, 0.0, 5.0, 1, 5.0));

		DailyGuildTransfers buffer = new DailyGuildTransfers();
		buffer.setPendingDividendPool(guild, 20.0);

		RecordingBank playerBank = new RecordingBank();
		PlayerEconomyManager economy = new PlayerEconomyManager();
		PostSettlementPayouts.apply(buffer, playerBank, economy, name -> "Ann".equals(name) ? ann : null);

		assertEquals(5.0, playerBank.balance(ann), 1e-9);
		assertEquals(5.0, economy.getLedger(ann).getAmount(PlayerCashflow.DIVIDEND_PAYOUT), 1e-9);
		verify(bank).withdraw(5.0);
	}

	@Test
	void bankruptGuildPaysNothing() {
		Guild guild = mock(Guild.class);
		Bank bank = mock(Bank.class);
		when(guild.getBank()).thenReturn(bank);
		when(guild.isBankrupt()).thenReturn(true);
		when(bank.getWealth()).thenReturn(-10.0);

		UUID player = UUID.randomUUID();
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		buffer.addPlayerPayout(guild, player, 10.0);

		RecordingBank playerBank = new RecordingBank();
		PostSettlementPayouts.apply(buffer, playerBank, new PlayerEconomyManager(), name -> null);

		assertEquals(0.0, playerBank.balance(player), 1e-9);
		verify(bank, never()).withdraw(org.mockito.ArgumentMatchers.any());
	}

	/**
	 * A wage is the only guild to player leg besides dividends, so the soldier has
	 * to be able to see where the money came from in {@code /ledger}.
	 */
	@Test
	void aWageShowsAsItsOwnIncomeLineInThePlayersLedger() {
		Guild guild = mock(Guild.class);
		Bank bank = mock(Bank.class);
		when(guild.getBank()).thenReturn(bank);
		when(guild.isBankrupt()).thenReturn(false);
		when(bank.getWealth()).thenReturn(100.0);

		UUID soldier = UUID.randomUUID();
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		buffer.addPlayerPayout(guild, soldier, 12.0);

		PlayerEconomyManager economy = new PlayerEconomyManager();
		PostSettlementPayouts.apply(buffer, new RecordingBank(), economy, name -> null);

		assertEquals(12.0, economy.getLedger(soldier).getAmount(PlayerCashflow.WAGES), 1e-9);
		assertEquals(12.0, economy.getLedger(soldier).getNetDaily(), 1e-9);
		assertEquals(List.of(PlayerCashflow.WAGES), economy.getLedger(soldier).getIncomeFlows());
		assertEquals(0.0, economy.getLedger(soldier).getAmount(PlayerCashflow.DIVIDEND_PAYOUT), 1e-9);
	}

	@Test
	void aClampedWageIsWrittenAtTheAmountActuallyPaid() {
		Guild guild = mock(Guild.class);
		Bank bank = mock(Bank.class);
		when(guild.getBank()).thenReturn(bank);
		when(guild.isBankrupt()).thenReturn(false);
		when(bank.getWealth()).thenReturn(5.0);

		UUID soldier = UUID.randomUUID();
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		buffer.addPlayerPayout(guild, soldier, 20.0);

		PlayerEconomyManager economy = new PlayerEconomyManager();
		RecordingBank playerBank = new RecordingBank();
		PostSettlementPayouts.apply(buffer, playerBank, economy, name -> null);

		// The ledger line and the bank deposit cannot disagree, or the soldier reads
		// a wage they never received.
		assertEquals(5.0, playerBank.balance(soldier), 1e-9);
		assertEquals(5.0, economy.getLedger(soldier).getAmount(PlayerCashflow.WAGES), 1e-9);
	}

	@Test
	void severalGuildsAggregateToOnePlayer() {
		Guild one = mock(Guild.class);
		Guild two = mock(Guild.class);
		Bank bankOne = mock(Bank.class);
		Bank bankTwo = mock(Bank.class);
		when(one.getBank()).thenReturn(bankOne);
		when(two.getBank()).thenReturn(bankTwo);
		when(one.isBankrupt()).thenReturn(false);
		when(two.isBankrupt()).thenReturn(false);
		when(bankOne.getWealth()).thenReturn(50.0);
		when(bankTwo.getWealth()).thenReturn(50.0);

		UUID player = UUID.randomUUID();
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		buffer.addPlayerPayout(one, player, 4.0);
		buffer.addPlayerPayout(two, player, 6.0);

		RecordingBank playerBank = new RecordingBank();
		PostSettlementPayouts.apply(buffer, playerBank, new PlayerEconomyManager(), name -> null);

		assertEquals(10.0, playerBank.balance(player), 1e-9);
		verify(bankOne).withdraw(4.0);
		verify(bankTwo).withdraw(6.0);
	}

	private static final class RecordingBank implements PlayerBank {
		private final Map<UUID, Double> balances = new HashMap<>();

		double balance(UUID playerUuid) {
			return balances.getOrDefault(playerUuid, 0.0);
		}

		@Override
		public double getBankBalance(UUID playerUuid) {
			return balance(playerUuid);
		}

		@Override
		public boolean withdrawFromBank(UUID playerUuid, double amount) {
			return false;
		}

		@Override
		public boolean depositToBank(UUID playerUuid, double amount) {
			if (playerUuid == null || amount <= 0) {
				return false;
			}
			balances.put(playerUuid, balance(playerUuid) + amount);
			return true;
		}
	}
}
