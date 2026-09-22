package me.Plugins.SimpleFactions.War.campaign.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;

class ScheduledCampaignBattleTest {

	@Test
	void battleType_siegeKindReturnsSiege() {
		ScheduledCampaignBattle slot = new ScheduledCampaignBattle(
				20,
				CampaignBattleKind.SIEGE,
				false,
				"fort_a");

		assertEquals(BattleType.SIEGE, slot.battleType());
	}

	@Test
	void battleType_nonSiegeKindsReturnField() {
		assertEquals(BattleType.FIELD, fieldSlot(CampaignBattleKind.FIELD).battleType());
		assertEquals(BattleType.FIELD, fieldSlot(CampaignBattleKind.NAVAL).battleType());
		assertEquals(BattleType.FIELD, fieldSlot(CampaignBattleKind.NAVAL_INVASION).battleType());
	}

	@Test
	void compactConstructor_normalizesBlankFortId() {
		ScheduledCampaignBattle slot = new ScheduledCampaignBattle(
				20,
				CampaignBattleKind.SIEGE,
				false,
				"   ");

		assertNull(slot.fortInstallationId());
	}

	@Test
	void fiveArgConstructor_normalizesBlankPortId() {
		ScheduledCampaignBattle slot = new ScheduledCampaignBattle(
				20,
				CampaignBattleKind.NAVAL,
				false,
				null,
				"   ");

		assertNull(slot.portInstallationId());
	}

	@Test
	void fiveArgConstructor_preservesPortId() {
		ScheduledCampaignBattle slot = new ScheduledCampaignBattle(
				20,
				CampaignBattleKind.NAVAL,
				false,
				null,
				"port_a");

		assertEquals("port_a", slot.portInstallationId());
	}

	private static ScheduledCampaignBattle fieldSlot(CampaignBattleKind kind) {
		return new ScheduledCampaignBattle(10, kind, false, null);
	}
}
