package net.tfminecraft.simplefactions.war.campaign.progression;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;

class CampaignCoalitionServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
	}

	@Test
	void coalitionMapping_roundTripsSides() {
		War war = baseWar();
		assertEquals(CampaignCoalition.AGGRESSOR, CampaignCoalitionService.coalitionOf(war, war.getAttackers()));
		assertEquals(CampaignCoalition.DEFENDER, CampaignCoalitionService.coalitionOf(war, war.getDefenders()));
		assertEquals(war.getAttackers(), CampaignCoalitionService.toSide(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void fuelSpend_reducesCoalitionFuel() {
		War war = baseWar();
		CampaignCoalitionService.spendFuel(war, CampaignCoalition.AGGRESSOR);
		assertEquals(3, CampaignCoalitionService.getFuel(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void derivePushTargetFromLegacyPhase_mapsKnownPhases() {
		assertEquals(
				CampaignPushTarget.TOWARD_OBJECTIVE,
				CampaignCoalitionService.derivePushTargetFromLegacyPhase(CampaignPhase.INVASION, ObjectiveHolder.DEFENDER));
		assertEquals(
				CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL,
				CampaignCoalitionService.derivePushTargetFromLegacyPhase(CampaignPhase.COUNTER_PUSH, ObjectiveHolder.DEFENDER));
		assertEquals(
				CampaignPushTarget.RETAKE_OBJECTIVE,
				CampaignCoalitionService.derivePushTargetFromLegacyPhase(CampaignPhase.RETAKE, ObjectiveHolder.ATTACKER));
	}

	@Test
	void isCoalitionWarLeader_matchesLeaderFaction() {
		War war = baseWar();
		assertTrue(CampaignCoalitionService.isCoalitionWarLeader(war, attacker, CampaignCoalition.AGGRESSOR));
		assertFalse(CampaignCoalitionService.isCoalitionWarLeader(war, defender, CampaignCoalition.AGGRESSOR));
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		return war;
	}
}
