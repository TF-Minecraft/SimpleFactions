package me.Plugins.SimpleFactions.War.campaign.ui;


import me.Plugins.SimpleFactions.War.campaign.ui.CampaignBattleIconLore;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.Side;

class CampaignBattleIconLoreTest {
	@Test
	void countSoldiers_sumsNonLevyCurrentSlots() {
		Regiment professional = mock(Regiment.class);
		when(professional.isLevy()).thenReturn(false);
		when(professional.getCurrentSlots()).thenReturn(10);
		Regiment levy = mock(Regiment.class);
		when(levy.isLevy()).thenReturn(true);
		when(levy.getCurrentSlots()).thenReturn(50);

		Military military = mock(Military.class);
		when(military.getRegiments()).thenReturn(List.of(professional, levy));

		Faction leader = mock(Faction.class);
		when(leader.getMilitary()).thenReturn(military);

		Participant participant = mock(Participant.class);
		when(participant.getLeader()).thenReturn(leader);
		when(participant.getSubjects()).thenReturn(List.of());
		when(participant.getJoinedSecondaries()).thenReturn(List.of());

		Side side = mock(Side.class);
		when(side.getMainParticipants()).thenReturn(List.of(participant));

		assertEquals(10, CampaignBattleIconLore.countSoldiers(side));
	}
}
