package me.Plugins.SimpleFactions.War.battle.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Database.WarbandData;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

class WarbandMapperTest {
	@Test
	void roundTrip_preservesRosterAndCampaignFields() {
		UUID leader = UUID.randomUUID();
		UUID member = UUID.randomUUID();
		UUID invited = UUID.randomUUID();

		Warband warband = Warband.fromPersistence(
				"campaign_w1_attacker",
				"The Attacker Host",
				leader,
				List.of(leader, member),
				List.of(invited),
				true,
				true,
				"attacker");

		WarbandData data = WarbandMapper.toData(warband);
		Warband restored = WarbandMapper.fromData(data);

		assertNotNull(restored);
		assertEquals("campaign_w1_attacker", restored.getId());
		assertEquals("The Attacker Host", restored.getName());
		assertEquals(leader, restored.getLeaderId());
		assertTrue(restored.hasMember(member));
		assertTrue(restored.isLocked());
		assertTrue(restored.isFaction());
		assertEquals("attacker", restored.getCampaignSideId());
	}

	@Test
	void toData_omitsDummyMembers() {
		UUID leader = UUID.randomUUID();
		UUID dummy = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("test_band", leader, true);
		warband.addDummyMembers(List.of(dummy), Map.of(dummy, "Aldric"));

		WarbandData data = WarbandMapper.toData(warband);

		assertFalse(data.memberIds.contains(dummy.toString()));
	}
}
