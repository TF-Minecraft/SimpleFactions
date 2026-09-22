package me.Plugins.SimpleFactions.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;

class DailyGuildTransfersTest {

	@Test
	void playerPayoutsMergeAndIgnoreNonPositive() {
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		Guild guild = org.mockito.Mockito.mock(Guild.class);
		UUID player = UUID.randomUUID();
		buffer.addPlayerPayout(guild, player, 10.0);
		buffer.addPlayerPayout(guild, player, 5.0);
		buffer.addPlayerPayout(guild, player, 0.0);
		buffer.addPlayerPayout(guild, player, -1.0);
		assertEquals(15.0, buffer.getPlayerPayouts().get(guild).get(player), 1e-9);
	}

	@Test
	void dividendPoolsMerge() {
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		Guild guild = org.mockito.Mockito.mock(Guild.class);
		buffer.setPendingDividendPool(guild, 8.0);
		buffer.setPendingDividendPool(guild, 2.0);
		assertEquals(10.0, buffer.getPendingDividendPools().get(guild), 1e-9);
	}

	@Test
	void payoutsToOnePlayerFromSeveralGuildsStaySeparate() {
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		Guild one = org.mockito.Mockito.mock(Guild.class);
		Guild two = org.mockito.Mockito.mock(Guild.class);
		UUID player = UUID.randomUUID();
		buffer.addPlayerPayout(one, player, 4.0);
		buffer.addPlayerPayout(two, player, 6.0);
		assertEquals(4.0, buffer.getPlayerPayouts().get(one).get(player), 1e-9);
		assertEquals(6.0, buffer.getPlayerPayouts().get(two).get(player), 1e-9);
	}

	@Test
	void clearEmptiesNewMaps() {
		DailyGuildTransfers buffer = new DailyGuildTransfers();
		Guild guild = org.mockito.Mockito.mock(Guild.class);
		buffer.addPlayerPayout(guild, UUID.randomUUID(), 1.0);
		buffer.setPendingDividendPool(guild, 2.0);
		buffer.clear();
		assertTrue(buffer.getPlayerPayouts().isEmpty());
		assertTrue(buffer.getPendingDividendPools().isEmpty());
	}
}
