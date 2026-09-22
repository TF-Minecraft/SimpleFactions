package me.Plugins.SimpleFactions.War.pathfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.enums.Terrain;

class BelligerentTerritoryTest {
	private static final String ATK = "atk";
	private static final String COATK = "coatk";
	private static final String DEF = "def";
	private static final String EXTRA_DEF = "extra_def";
	private static final String FOREIGN = "foreign";

	private Map<Integer, String> ownerByProvince;
	private BelligerentTerritory territory;

	@BeforeEach
	void setUp() {
		ownerByProvince = new HashMap<>();
		ProvinceOwnerLookup owners = ownerByProvince::get;
		territory = new BelligerentTerritory(Set.of(ATK), Set.of(DEF), owners);
	}

	@Test
	void wildernessHasNoOwnerAndIsNeutral() {
		int wildernessId = 100;
		assertTrue(territory.isWilderness(wildernessId));
		assertFalse(territory.isForeignNation(wildernessId));
		assertTrue(territory.isNeutral(wildernessId));
	}

	@Test
	void foreignNationIsNeutralButNotWilderness() {
		ownerByProvince.put(200, FOREIGN);
		assertFalse(territory.isWilderness(200));
		assertTrue(territory.isForeignNation(200));
		assertTrue(territory.isNeutral(200));
	}

	@Test
	void belligerentIsNeitherWildernessNorForeignNorNeutral() {
		ownerByProvince.put(300, ATK);
		ownerByProvince.put(400, DEF);
		assertFalse(territory.isWilderness(300));
		assertFalse(territory.isForeignNation(300));
		assertFalse(territory.isNeutral(300));
		assertFalse(territory.isNeutral(400));
	}

	@Test
	void extraMainDefenderStaysWalkableButIsNotInvasionEntry() {
		ProvinceManager pm = new ProvinceManager();
		Province attacker = province(1, ATK);
		Province extraDef = province(2, EXTRA_DEF);
		Province leaderDef = province(3, DEF);
		link(attacker, extraDef);
		link(leaderDef, attacker);
		pm.start(Map.of(1, attacker, 2, extraDef, 3, leaderDef));

		ProvinceOwnerLookup owners = ownerByProvince::get;
		BelligerentTerritory warTerritory = new BelligerentTerritory(
				Set.of(ATK),
				Set.of(DEF, EXTRA_DEF),
				Set.of(DEF),
				owners);

		assertTrue(warTerritory.isDefenderSide(2));
		assertFalse(warTerritory.isForeignNation(2));
		assertFalse(warTerritory.isMainDefenderRealm(2));
		assertEquals(List.of(3), warTerritory.findInvasionEntryProvinces(pm));
	}

	@Test
	void coAttackerBorderStillCreatesInvasionEntry() {
		ProvinceManager pm = new ProvinceManager();
		Province coAttacker = province(1, COATK);
		Province leaderDef = province(2, DEF);
		link(coAttacker, leaderDef);
		pm.start(Map.of(1, coAttacker, 2, leaderDef));

		ProvinceOwnerLookup owners = ownerByProvince::get;
		BelligerentTerritory warTerritory = new BelligerentTerritory(
				Set.of(ATK, COATK),
				Set.of(DEF),
				Set.of(DEF),
				owners);

		assertEquals(List.of(2), warTerritory.findInvasionEntryProvinces(pm));
	}

	@Test
	void kingLandIsLiegeTransitOnInternalWar() {
		ownerByProvince.put(1, "dukeA");
		ownerByProvince.put(2, "king");
		ownerByProvince.put(3, "dukeB");
		BelligerentTerritory internal = internalTerritory();

		assertTrue(internal.isLiegeTransit(2));
		assertFalse(internal.isAttackerSide(2));
		assertFalse(internal.isDefenderSide(2));
		assertFalse(internal.isForeignNation(2));
		assertTrue(internal.isNeutral(2));
		assertTrue(internal.isAttackerSide(1));
		assertTrue(internal.isDefenderSide(3));
	}

	@Test
	void uninvolvedSiblingLandIsLiegeTransit() {
		ownerByProvince.put(5, "dukeC");
		BelligerentTerritory internal = internalTerritory();

		assertTrue(internal.isLiegeTransit(5));
		assertFalse(internal.isForeignNation(5));
		assertTrue(internal.isNeutral(5));
		assertFalse(internal.isAttackerSide(5));
		assertFalse(internal.isDefenderSide(5));
	}

	@Test
	void externalWarThirdNationStaysForeign() {
		ownerByProvince.put(200, FOREIGN);
		assertTrue(territory.isForeignNation(200));
		assertFalse(territory.isLiegeTransit(200));
		assertTrue(territory.isNeutral(200));
	}

	private BelligerentTerritory internalTerritory() {
		return new BelligerentTerritory(
				Set.of("dukeA"),
				Set.of("dukeB"),
				Set.of("dukeB"),
				ownerByProvince::get,
				"king",
				id -> {
					if (id == null) {
						return null;
					}
					if ("king".equalsIgnoreCase(id)) {
						return null;
					}
					if ("dukeA".equalsIgnoreCase(id)
							|| "dukeB".equalsIgnoreCase(id)
							|| "dukeC".equalsIgnoreCase(id)) {
						return "king";
					}
					return null;
				});
	}

	private Province province(int id, String ownerId) {
		ownerByProvince.put(id, ownerId);
		return new Province(id, Terrain.PLAINS.name(), 50);
	}

	private void link(Province a, Province b) {
		a.addNeighbour(b.getId());
		b.addNeighbour(a.getId());
	}
}
