package me.Plugins.SimpleFactions.War.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.government.StabilityModifier;

class PillageTradeHitTest {

	@Test
	void decayPerPowerTick_matchesTenDayWindowAtHourlyTicks() {
		assertEquals(100.0 / (10 * 24), PillageTradeHit.decayPerPowerTick(-100, 10));
	}

	@Test
	void applyToIncome_atMinusHundredIsZero() {
		Guild guild = guildWithHit(-100, 1);
		assertEquals(0, PillageTradeHit.applyToIncome(guild, 80));
	}

	@Test
	void applyToIncome_clampsMultiplierAtZero() {
		Guild guild = guildWithHit(-150, 1);
		assertEquals(0, PillageTradeHit.applyToIncome(guild, 80));
	}

	@Test
	void breakdownLine_nullWhenNoHit() {
		assertEquals(null, PillageTradeHit.breakdownLine(guildWithHit(0, 1)));
		assertEquals(null, PillageTradeHit.breakdownLine(null));
	}

	@Test
	void breakdownLine_showsRoundedPercent() {
		assertEquals("#d4c9aePillage: #c45749-99%", PillageTradeHit.breakdownLine(guildWithHit(-99.4, 1)));
	}

	@Test
	void ledgerSuffix_emptyWhenNoHit() {
		assertEquals("", PillageTradeHit.ledgerSuffix(guildWithHit(0, 1)));
		assertEquals("", PillageTradeHit.ledgerSuffix(null));
	}

	@Test
	void ledgerSuffix_whenHit() {
		assertEquals(" §7(§cPillaged§7)", PillageTradeHit.ledgerSuffix(guildWithHit(-100, 1)));
	}

	@Test
	void tick_removesHitAfterDecay() {
		Guild guild = guildWithHit(-100, 50);
		PillageTradeHit.tick(guild);
		assertEquals(-50, guild.getPillageHits().get(0).getModifier());
		PillageTradeHit.tick(guild);
		assertTrue(guild.getPillageHits().isEmpty());
	}

	private static Guild guildWithHit(double percent, double decay) {
		Guild guild = mock(Guild.class);
		List<StabilityModifier> hits = new ArrayList<>();
		hits.add(new StabilityModifier(PillageTradeHit.NAME, percent, decay));
		when(guild.getPillageHits()).thenReturn(hits);
		return guild;
	}
}
