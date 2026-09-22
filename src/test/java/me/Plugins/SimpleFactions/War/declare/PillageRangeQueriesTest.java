package me.Plugins.SimpleFactions.War.declare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.SimpleFactions.settlement.Settlement;

class PillageRangeQueriesTest {
	private ProvinceManager pm;

	@BeforeEach
	void setUp() {
		pm = new ProvinceManager();
	}

	@Test
	void adjacentToAttackerLand_inLandRangeAtOne() {
		Province attackerLand = province(1, Terrain.PLAINS);
		Province settlementLand = province(2, Terrain.PLAINS);
		link(attackerLand, settlementLand);
		load(attackerLand, settlementLand);

		Faction attacker = faction(List.of(1));
		Faction owner = faction(List.of(2));
		Settlement settlement = settlement(2);

		assertEquals(OptionalInt.of(1), PillageRangeQueries.landDistanceFromAttacker(pm, attacker, 2));
		assertTrue(PillageRangeQueries.inLandRange(pm, attacker, 2, 1));
		assertTrue(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, owner, Set.of(2), 1));
	}

	@Test
	void threeHops_landFailsAtRangeTwo_seaPassesOnSharedOcean() {
		Province attackerLand = province(1, Terrain.PLAINS);
		Province hop1 = province(2, Terrain.PLAINS);
		Province hop2 = province(3, Terrain.PLAINS);
		Province settlementLand = province(4, Terrain.PLAINS);
		Province sea = province(5, Terrain.SEA);
		link(attackerLand, hop1);
		link(hop1, hop2);
		link(hop2, settlementLand);
		link(attackerLand, sea);
		link(settlementLand, sea);
		load(attackerLand, hop1, hop2, settlementLand, sea);

		Faction attacker = faction(List.of(1));
		Faction owner = faction(List.of(4));
		Settlement settlement = settlement(4);

		assertEquals(OptionalInt.of(3), PillageRangeQueries.landDistanceFromAttacker(pm, attacker, 4));
		assertFalse(PillageRangeQueries.inLandRange(pm, attacker, 4, 2));
		assertEquals(OptionalInt.of(0), PillageRangeQueries.distanceToCoast(pm, 4));
		assertTrue(PillageRangeQueries.inSeaRange(pm, attacker, owner, 4, 2));
		assertTrue(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, owner, Set.of(4), 2));
	}

	@Test
	void landlockedAttacker_inlandBeyondRange_cannotPillage() {
		Province attackerLand = province(1, Terrain.PLAINS);
		Province hop1 = province(2, Terrain.PLAINS);
		Province hop2 = province(3, Terrain.PLAINS);
		Province settlementLand = province(4, Terrain.PLAINS);
		Province otherCoast = province(5, Terrain.PLAINS);
		Province sea = province(6, Terrain.SEA);
		link(attackerLand, hop1);
		link(hop1, hop2);
		link(hop2, settlementLand);
		link(otherCoast, sea);
		load(attackerLand, hop1, hop2, settlementLand, otherCoast, sea);

		Faction attacker = faction(List.of(1));
		Faction owner = faction(List.of(4));
		Settlement settlement = settlement(4);

		assertFalse(PillageRangeQueries.inLandRange(pm, attacker, 4, 2));
		assertFalse(PillageRangeQueries.inSeaRange(pm, attacker, owner, 4, 3));
		assertFalse(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, owner, Set.of(4), 2));
	}

	@Test
	void disconnectedOceans_cannotSeabornePillage() {
		Province attackerLand = province(1, Terrain.PLAINS);
		Province oceanA = province(2, Terrain.SEA);
		Province settlementLand = province(3, Terrain.PLAINS);
		Province oceanB = province(4, Terrain.SEA);
		link(attackerLand, oceanA);
		link(settlementLand, oceanB);
		load(attackerLand, oceanA, settlementLand, oceanB);

		Faction attacker = faction(List.of(1));
		Faction owner = faction(List.of(3));
		Settlement settlement = settlement(3);

		assertTrue(PillageRangeQueries.landDistanceFromAttacker(pm, attacker, 3).isEmpty());
		assertFalse(PillageRangeQueries.inLandRange(pm, attacker, 3, 3));
		assertFalse(PillageRangeQueries.inSeaRange(pm, attacker, owner, 3, 3));
		assertFalse(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, owner, Set.of(3), 3));
	}

	@Test
	void seaRange_usesSettlementOwnerNotOverlord() {
		Province attackerLand = province(1, Terrain.PLAINS);
		Province sea = province(2, Terrain.SEA);
		Province vassalLand = province(3, Terrain.PLAINS);
		link(attackerLand, sea);
		link(sea, vassalLand);
		load(attackerLand, sea, vassalLand);

		Faction attacker = faction(List.of(1));
		Faction vassal = faction(List.of(3));
		Faction overlord = faction(List.of(99));
		Settlement settlement = settlement(3);

		assertTrue(PillageRangeQueries.inSeaRange(pm, attacker, vassal, 3, 3));
		assertFalse(PillageRangeQueries.inSeaRange(pm, attacker, overlord, 3, 3));
		assertTrue(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, vassal, Set.of(3), 3));
		assertFalse(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, overlord, Set.of(3), 3));
	}

	@Test
	void attackerOwnsCenter_cannotPillage() {
		Province attackerLand = province(1, Terrain.PLAINS);
		Province other = province(2, Terrain.PLAINS);
		link(attackerLand, other);
		load(attackerLand, other);

		Faction attacker = faction(List.of(1));
		Settlement settlement = settlement(1);

		assertTrue(PillageRangeQueries.inLandRange(pm, attacker, 1, 1));
		assertFalse(PillageRangeQueries.canPillageSettlement(
				pm, attacker, settlement, attacker, Set.of(1), 1));
	}

	@Test
	void centerMissingFromRealm_cannotPillageEvenIfAdjacent() {
		Province attackerLand = province(1, Terrain.PLAINS);
		Province settlementLand = province(2, Terrain.PLAINS);
		link(attackerLand, settlementLand);
		load(attackerLand, settlementLand);

		Faction attacker = faction(List.of(1));
		Faction owner = faction(List.of(2));
		Settlement settlement = settlement(2);

		assertTrue(PillageRangeQueries.inLandRange(pm, attacker, 2, 1));
		assertFalse(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, owner, Set.of(), 1));
		assertFalse(PillageRangeQueries.canPillageSettlement(pm, attacker, settlement, owner, Set.of(9), 1));
	}

	private static Faction faction(List<Integer> provinces) {
		Faction faction = mock(Faction.class);
		when(faction.getProvinces()).thenReturn(provinces);
		return faction;
	}

	private static Settlement settlement(int center) {
		return new Settlement("s-" + center, "Settlement " + center, center, 0, 0);
	}

	private static Province province(int id, Terrain terrain) {
		return new Province(id, terrain.name(), 50);
	}

	private static void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}

	private void load(Province... provinces) {
		Map<Integer, Province> map = new HashMap<>();
		for (Province province : provinces) {
			map.put(province.getId(), province);
		}
		pm.start(map);
	}
}
