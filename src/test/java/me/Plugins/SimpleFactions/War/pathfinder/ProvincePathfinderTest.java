package me.Plugins.SimpleFactions.War.pathfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.enums.Terrain;

class ProvincePathfinderTest {
	private static final String ATK = "atk";
	private static final String DEF = "def";
	private static final String NEUTRAL = "neutral";

	private ProvinceManager pm;
	private Map<Integer, String> ownerByProvince;
	private BelligerentTerritory territory;
	private ProvincePathfinder pathfinder;

	@BeforeEach
	void setUp() {
		Cache.tradeCarry.clear();
		Cache.tradeCarry.put(Terrain.PLAINS, 0.85);
		Cache.tradeCarry.put(Terrain.WATER, 0.75);
		Cache.tradeCarry.put(Terrain.MOUNTAIN, 0.3);
		Cache.tradeCarry.put(Terrain.SEA, 0.6);
		Cache.warPathfinderNeutralPenalty = 8.0;
		Cache.warPathfinderSeaPassEnabled = true;
		Cache.warPathfinderWaterCost = 0.0;

		pm = new ProvinceManager();
		ownerByProvince = new HashMap<>();
		ProvinceOwnerLookup owners = ownerByProvince::get;
		territory = new BelligerentTerritory(
				Set.of(ATK),
				Set.of(DEF),
				owners);
		pathfinder = new ProvincePathfinder(pm, owners);
	}

	@Test
	void crossesWaterCheaperThanDetour() {
		Province a = province(1, Terrain.PLAINS, ATK);
		Province water = province(2, Terrain.WATER, ATK);
		Province c = province(3, Terrain.PLAINS, ATK);
		Province d = province(4, Terrain.PLAINS, DEF);
		Province m1 = province(5, Terrain.MOUNTAIN, ATK);
		Province m2 = province(6, Terrain.MOUNTAIN, ATK);
		Province m3 = province(7, Terrain.MOUNTAIN, ATK);

		link(a, water);
		link(water, c);
		link(c, d);
		link(a, m1);
		link(m1, m2);
		link(m2, m3);
		link(m3, c);

		load(a, water, c, d, m1, m2, m3);

		PathfinderResult result = pathfinder.findRoute(1, 4, PathfinderPass.LAND_NO_NEUTRAL, territory);
		assertTrue(result.isFound());
		assertEquals(List.of(1, 2, 3, 4), result.getPath());
	}

	@Test
	void seaBlockedOnLandPass() {
		Province a = province(1, Terrain.PLAINS, ATK);
		Province sea = province(2, Terrain.SEA, NEUTRAL);
		Province g = province(3, Terrain.PLAINS, DEF);

		link(a, sea);
		link(sea, g);
		load(a, sea, g);

		PathfinderResult land = pathfinder.findRoute(1, 3, PathfinderPass.LAND_NO_NEUTRAL, territory);
		assertFalse(land.isFound());
	}

	@Test
	void seaPassUsesOcean() {
		Province a = province(1, Terrain.PLAINS, ATK);
		Province sea = province(2, Terrain.SEA, NEUTRAL);
		Province g = province(3, Terrain.PLAINS, DEF);

		link(a, sea);
		link(sea, g);
		load(a, sea, g);

		PathfinderResult seaPass = pathfinder.findRoute(1, 3, PathfinderPass.SEA_NO_NEUTRAL, territory);
		assertTrue(seaPass.isFound());
		assertEquals(PathfinderPass.SEA_NO_NEUTRAL, seaPass.getPassUsed());
		assertEquals(List.of(1, 2, 3), seaPass.getPath());
	}

	@Test
	void wildernessCrossesOnLandPass() {
		Province c = province(3, Terrain.PLAINS, ATK);
		Province wilderness = wilderness(8, Terrain.PLAINS);
		Province d = province(4, Terrain.PLAINS, DEF);

		link(c, wilderness);
		link(wilderness, d);
		load(c, wilderness, d);

		PathfinderResult pass1 = pathfinder.findRoute(3, 4, PathfinderPass.LAND_NO_NEUTRAL, territory);
		assertTrue(pass1.isFound());
		assertEquals(List.of(3, 8, 4), pass1.getPath());
		double expectedCost = ProvincePathfinder.terrainEnterCost(Terrain.PLAINS) * 2;
		assertEquals(expectedCost, pass1.getTotalCost(), 0.001);
	}

	@Test
	void foreignNationBlocksLandPass() {
		Province c = province(3, Terrain.PLAINS, ATK);
		Province foreign = province(8, Terrain.PLAINS, NEUTRAL);
		Province d = province(4, Terrain.PLAINS, DEF);

		link(c, foreign);
		link(foreign, d);
		load(c, foreign, d);

		PathfinderResult pass1 = pathfinder.findRoute(3, 4, PathfinderPass.LAND_NO_NEUTRAL, territory);
		assertFalse(pass1.isFound());
	}

	@Test
	void internalWarLandPassCrossesKingLand() {
		String king = "king";
		Province dukeA = province(1, Terrain.PLAINS, ATK);
		Province kingLand = province(2, Terrain.PLAINS, king);
		Province dukeB = province(3, Terrain.PLAINS, DEF);
		link(dukeA, kingLand);
		link(kingLand, dukeB);
		load(dukeA, kingLand, dukeB);

		BelligerentTerritory internal = new BelligerentTerritory(
				Set.of(ATK),
				Set.of(DEF),
				Set.of(DEF),
				ownerByProvince::get,
				king,
				id -> king.equalsIgnoreCase(id) ? null : king);

		assertFalse(internal.isAttackerSide(2));
		assertFalse(internal.isDefenderSide(2));
		assertTrue(internal.isLiegeTransit(2));

		PathfinderResult result = pathfinder.findRoute(1, 3, PathfinderPass.LAND_NO_NEUTRAL, internal);
		assertTrue(result.isFound());
		assertEquals(List.of(1, 2, 3), result.getPath());
	}

	@Test
	void externalWarStillBlocksThirdNationLand() {
		Province c = province(3, Terrain.PLAINS, ATK);
		Province foreign = province(8, Terrain.PLAINS, NEUTRAL);
		Province d = province(4, Terrain.PLAINS, DEF);
		link(c, foreign);
		link(foreign, d);
		load(c, foreign, d);

		PathfinderResult pass1 = pathfinder.findRoute(3, 4, PathfinderPass.LAND_NO_NEUTRAL, territory);
		assertFalse(pass1.isFound());
	}

	@Test
	void findRouteWithFallbackUsesWildernessOnLandPass() {
		Province c = province(3, Terrain.PLAINS, ATK);
		Province wilderness = wilderness(8, Terrain.PLAINS);
		Province d = province(4, Terrain.PLAINS, DEF);
		link(c, wilderness);
		link(wilderness, d);
		load(c, wilderness, d);

		PathfinderResult result = pathfinder.findRouteWithFallback(3, 4, territory);
		assertTrue(result.isFound());
		assertEquals(PathfinderPass.LAND_NO_NEUTRAL, result.getPassUsed());
	}

	@Test
	void wildernessPreferredOverSea() {
		Province a = province(1, Terrain.PLAINS, ATK);
		Province wilderness = wilderness(2, Terrain.PLAINS);
		Province g = province(3, Terrain.PLAINS, DEF);
		Province sea = wilderness(4, Terrain.SEA);

		link(a, wilderness);
		link(wilderness, g);
		link(a, sea);
		link(sea, g);
		load(a, wilderness, g, sea);

		PathfinderResult result = pathfinder.findRouteWithFallback(1, 3, territory);
		assertTrue(result.isFound());
		assertEquals(PathfinderPass.LAND_NO_NEUTRAL, result.getPassUsed());
		assertEquals(List.of(1, 2, 3), result.getPath());
	}

	@Test
	void borderStart_prefersShallowestEntryWhenInlandProvinceIsCheaperToObjective() {
		Province capital = province(452, Terrain.PLAINS, ATK);
		Province march = province(672, Terrain.PLAINS, ATK);
		Province border = province(709, Terrain.PLAINS, DEF);
		Province fort = province(713, Terrain.PLAINS, DEF);
		Province objective = province(705, Terrain.PLAINS, DEF);

		link(capital, march);
		link(march, border);
		link(march, fort);
		link(border, fort);
		link(fort, objective);
		load(capital, march, border, fort, objective);

		War war = warWithCapital(452);
		PathfinderResult result = pathfinder.computeCampaignLine(war, 705);

		assertTrue(result.isFound());
		assertEquals(709, result.getStartProvinceId());
		assertEquals(List.of(709, 713, 705), result.getPath());
	}

	@Test
	void borderStartSkipsObjectiveSelfPathWhenOtherEntriesExist() {
		Province atkBorder = province(672, Terrain.PLAINS, ATK);
		Province border = province(709, Terrain.PLAINS, DEF);
		Province fort = province(713, Terrain.PLAINS, DEF);
		Province capital = province(705, Terrain.PLAINS, DEF);

		link(atkBorder, border);
		link(border, fort);
		link(fort, capital);
		load(atkBorder, border, fort, capital);

		War war = warWithCapital(672);
		PathfinderResult result = pathfinder.computeCampaignLine(war, 705);

		assertTrue(result.isFound());
		assertEquals(709, result.getStartProvinceId());
		assertEquals(List.of(709, 713, 705), result.getPath());
	}

	@Test
	void borderStartAllowsObjectiveSelfPathWhenOnlyEntry() {
		Province atk = province(1, Terrain.PLAINS, ATK);
		Province capital = province(705, Terrain.PLAINS, DEF);
		Province sea = province(795, Terrain.SEA, NEUTRAL);

		link(atk, sea);
		link(sea, capital);
		load(atk, sea, capital);

		War war = war(ATK, DEF);
		PathfinderResult result = pathfinder.computeCampaignLine(war, 705);

		assertTrue(result.isFound());
		assertEquals(705, result.getStartProvinceId());
	}

	@Test
	void borderStartPicksCheapestB() {
		Province b1 = province(10, Terrain.PLAINS, ATK);
		Province b2 = province(11, Terrain.PLAINS, ATK);
		Province d1 = province(20, Terrain.PLAINS, DEF);
		Province d2 = province(21, Terrain.PLAINS, DEF);
		Province d3 = province(22, Terrain.PLAINS, DEF);
		Province objective = province(30, Terrain.PLAINS, DEF);

		link(b1, d1);
		link(d1, objective);
		link(b2, d2);
		link(d2, d3);
		link(d3, objective);
		load(b1, b2, d1, d2, d3, objective);

		War war = war(ATK, DEF);
		PathfinderResult result = pathfinder.computeCampaignLine(war, 30);

		assertTrue(result.isFound());
		assertEquals(20, result.getStartProvinceId());
		assertEquals(List.of(20, 30), result.getPath());
	}

	@Test
	void seaContactFallbackWhenNoLandBorder() {
		Province a = province(1, Terrain.PLAINS, ATK);
		Province sea = province(2, Terrain.SEA, NEUTRAL);
		Province g = province(3, Terrain.PLAINS, DEF);
		Province objective = province(4, Terrain.PLAINS, DEF);

		link(a, sea);
		link(sea, g);
		link(g, objective);
		load(a, sea, g, objective);

		War war = war(ATK, DEF);
		PathfinderResult result = pathfinder.computeCampaignLine(war, 4);

		assertTrue(result.isFound());
		assertEquals(3, result.getStartProvinceId());
		assertEquals(PathfinderPass.LAND_NO_NEUTRAL, result.getPassUsed());
		assertEquals(List.of(3, 4), result.getPath());
	}

	private War warWithCapital(int capital) {
		Faction attacker = mock(Faction.class);
		when(attacker.getId()).thenReturn(ATK);
		when(attacker.getCapital()).thenReturn(capital);
		Faction defender = mock(Faction.class);
		when(defender.getId()).thenReturn(DEF);
		return new War(1, attacker, defender);
	}

	private War war(String attackerId, String defenderId) {
		Faction attacker = mock(Faction.class);
		when(attacker.getId()).thenReturn(attackerId);
		Faction defender = mock(Faction.class);
		when(defender.getId()).thenReturn(defenderId);
		return new War(1, attacker, defender);
	}

	private Province province(int id, Terrain terrain, String ownerId) {
		ownerByProvince.put(id, ownerId);
		return new Province(id, terrain.name(), 50);
	}

	private Province wilderness(int id, Terrain terrain) {
		return new Province(id, terrain.name(), 50);
	}

	private void link(Province a, Province b) {
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
