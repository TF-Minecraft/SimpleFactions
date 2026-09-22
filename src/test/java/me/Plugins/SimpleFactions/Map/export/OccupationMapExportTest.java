package me.Plugins.SimpleFactions.Map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class OccupationMapExportTest {
	private Faction attacker;
	private Faction defender;
	private Faction deJure;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		deJure = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(deJure.getId()).thenReturn("host");
		when(attacker.getRGB()).thenReturn("10,20,30");
		when(defender.getRGB()).thenReturn("40,50,60");
		when(deJure.getRGB()).thenReturn("70,80,90");
	}

	@Test
	void occupierByProvince_mapsLeadersAndLaterWarWins() {
		War first = campaignWar(1);
		first.setOccupiedByAttacker(new ArrayList<>(List.of(10)));
		War second = campaignWar(2);
		second.setOccupiedByDefender(new ArrayList<>(List.of(10)));

		Map<Integer, String> occupiers = OccupationMapExport.occupierByProvince(List.of(first, second));

		assertEquals("def", occupiers.get(10));
	}

	@Test
	void occupierByProvince_skipsWhenOccupierIsDeJureOwner() {
		War war = campaignWar(1);
		war.setOccupiedByAttacker(new ArrayList<>(List.of(10, 20)));

		Map<Integer, String> occupiers = OccupationMapExport.occupierByProvince(
				List.of(war),
				pid -> pid == 10 ? attacker : defender);

		assertFalse(occupiers.containsKey(10));
		assertEquals("atk", occupiers.get(20));
	}

	@Test
	void occupierByProvince_skipsRaids() {
		War raid = campaignWar(1);
		raid.setWarType(WarType.RAID);
		raid.setOccupiedByAttacker(new ArrayList<>(List.of(10)));

		assertTrue(OccupationMapExport.occupierByProvince(List.of(raid)).isEmpty());
	}

	@Test
	void occupierByProvince_skipsInactive() {
		War war = campaignWar(1);
		war.setOccupiedByAttacker(new ArrayList<>(List.of(10)));
		war.end(me.Plugins.SimpleFactions.War.enums.WarEndReason.WHITE_PEACE);

		assertTrue(OccupationMapExport.occupierByProvince(List.of(war)).isEmpty());
	}

	@Test
	void nationRgbsToEnqueue_includesLeadersAndDeJure() {
		War war = campaignWar(1);
		war.setOccupiedByAttacker(new ArrayList<>(List.of(17)));

		List<String> rgbs = OccupationMapExport.nationRgbsToEnqueue(war, pid -> pid == 17 ? deJure : null);

		assertEquals(List.of("10,20,30", "40,50,60", "70,80,90"), rgbs);
	}

	@Test
	void nationRgbsToEnqueue_dedupesWhenDeJureIsLeader() {
		War war = campaignWar(1);
		war.setOccupiedByDefender(new ArrayList<>(List.of(30)));

		List<String> rgbs = OccupationMapExport.nationRgbsToEnqueue(war, pid -> defender);

		assertEquals(List.of("10,20,30", "40,50,60"), rgbs);
		assertFalse(rgbs.contains("70,80,90"));
	}

	private War campaignWar(int id) {
		War war = new War(id, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		return war;
	}
}
