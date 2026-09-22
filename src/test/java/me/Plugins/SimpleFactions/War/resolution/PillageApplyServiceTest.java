package me.Plugins.SimpleFactions.War.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.settlement.Settlement;

class PillageApplyServiceTest {

	@Test
	void twoGuildsInSettlement_lootIsSumTimesDays_bothGetHit() {
		Settlement settlement = new Settlement("town", "Town", 10, 0, 0);
		Guild inA = guild(10);
		Guild inB = guild(10);
		Guild outside = guild(99);

		List<Guild> hit = PillageApplyService.guildsInSettlement(settlement, List.of(inA, inB, outside));
		assertEquals(List.of(inA, inB), hit);

		Map<Guild, Double> income = Map.of(inA, 50.0, inB, 20.0);
		double loot = PillageApplyService.snapshotLoot(hit, income::get, 10);
		assertEquals(700.0, loot);

		PillageApplyService.attachHits(hit, -100, 0.5);
		assertEquals(1, inA.getPillageHits().size());
		assertEquals(1, inB.getPillageHits().size());
		assertTrue(outside.getPillageHits().isEmpty());
		assertEquals(PillageTradeHit.NAME, inA.getPillageHits().get(0).getName());
		assertEquals(-100, inA.getPillageHits().get(0).getModifier());
		assertEquals(0.5, inA.getPillageHits().get(0).getDecay());
		assertEquals(-100, inB.getPillageHits().get(0).getModifier());
	}

	@Test
	void guildCapitalOutsideSettlement_ignored() {
		Settlement settlement = new Settlement("town", "Town", 10, 0, 0);
		Guild outside = guild(11);
		assertTrue(PillageApplyService.guildsInSettlement(settlement, List.of(outside)).isEmpty());
	}

	@Test
	void attachHits_replacesExistingPillageModifier() {
		Guild guild = guild(10);
		guild.getPillageHits().add(new StabilityModifier(PillageTradeHit.NAME, -40, 1));
		PillageApplyService.attachHits(List.of(guild), -100, 0.25);
		assertEquals(1, guild.getPillageHits().size());
		assertEquals(-100, guild.getPillageHits().get(0).getModifier());
		assertEquals(0.25, guild.getPillageHits().get(0).getDecay());
	}

	private static Guild guild(int capital) {
		Guild guild = mock(Guild.class);
		when(guild.getCapital()).thenReturn(capital);
		List<StabilityModifier> hits = new ArrayList<>();
		when(guild.getPillageHits()).thenReturn(hits);
		return guild;
	}
}
