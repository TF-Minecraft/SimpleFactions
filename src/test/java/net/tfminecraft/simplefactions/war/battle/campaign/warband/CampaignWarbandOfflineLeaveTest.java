package net.tfminecraft.simplefactions.war.battle.campaign.warband;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

class CampaignWarbandOfflineLeaveTest {
	@Test
	void anOfflineLeaderLeavesAndTheLeadershipPasses() {
		UUID leader = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Warband warband = Warband.createWithMemberIds("host", leader, true, leader, other);
		try (var joins = mockStatic(CampaignBattleJoinService.class);
				var persistence = mockStatic(BattlePersistenceService.class)) {
			CampaignWarbandBattleService.processLeave(leader, null, warband, true);
			assertFalse(warband.hasMember(leader));
			assertEquals(other, warband.getLeaderId());
			persistence.verify(() -> BattlePersistenceService.persistWarband(warband));
		}
	}
}
