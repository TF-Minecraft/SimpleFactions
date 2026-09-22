package me.Plugins.SimpleFactions.War.campaign.progression;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class CampaignProgressionServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		Cache.warFirstBattleAtBorder = true;
		Cache.warProvincesBetweenBattles = 1;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
	}

	@Test
	void holdsInitiative_followsInitiativeHolderCoalition() {
		War war = baseWar();
		assertTrue(CampaignProgressionService.holdsInitiative(war, CampaignCoalition.AGGRESSOR));
		assertFalse(CampaignProgressionService.holdsInitiative(war, CampaignCoalition.DEFENDER));

		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setInitiativeHolder(BelligerentRole.DEFENDER);
		assertTrue(CampaignProgressionService.holdsInitiative(war, CampaignCoalition.DEFENDER));
	}

	@Test
	void applyPostponedBattle_doesNotSpendInitiative() {
		War war = baseWar();
		CampaignProgressionService.applyPostponedBattle(war);
		assertEquals(4, war.getInitiativeAttacker());
		assertEquals(4, war.getInitiativeDefender());
		assertEquals(0, war.getCampaignBattlesFought());
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolder(BelligerentRole.ATTACKER);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setCampaignBattlesFought(0);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		return war;
	}
}
