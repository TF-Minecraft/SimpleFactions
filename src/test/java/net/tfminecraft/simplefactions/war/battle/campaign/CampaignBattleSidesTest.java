package net.tfminecraft.simplefactions.war.battle.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.core.War;

class CampaignBattleSidesTest {
	@Test
	void missingSnapshot_keepsWarAttackersOnAttackerSide() {
		Faction attacker = faction("atk");
		Faction defender = faction("def");
		War war = new War(1, attacker, defender);
		Battle battle = mock(Battle.class);

		assertEquals(war.getAttackers(), CampaignBattleSides.warSideFor(war, battle, BattleTemplate.ATTACKER_SIDE));
		assertEquals(war.getDefenders(), CampaignBattleSides.warSideFor(war, battle, BattleTemplate.DEFENDER_SIDE));
		assertEquals(
				BattleTemplate.ATTACKER_SIDE,
				CampaignBattleSides.battleSideFor(war, battle, war.getAttackers()));
		assertEquals(BelligerentRole.ATTACKER, CampaignBattleSides.roleFor(war, battle, "Attacker"));
		assertNull(CampaignBattleSides.roleFor(war, battle, "raider"));
	}

	@Test
	void counterPush_attackerSideIsWarDefenders() {
		Faction attacker = faction("atk");
		Faction defender = faction("def");
		War war = new War(1, attacker, defender);
		Battle battle = mock(Battle.class);
		when(battle.getOffensiveCoalition()).thenReturn(CampaignCoalition.DEFENDER);

		assertEquals(war.getDefenders(), CampaignBattleSides.warSideFor(war, battle, BattleTemplate.ATTACKER_SIDE));
		assertEquals(war.getAttackers(), CampaignBattleSides.warSideFor(war, battle, BattleTemplate.DEFENDER_SIDE));
		assertEquals(
				BattleTemplate.ATTACKER_SIDE,
				CampaignBattleSides.battleSideFor(war, battle, war.getDefenders()));
		assertEquals(
				BattleTemplate.DEFENDER_SIDE,
				CampaignBattleSides.battleSideFor(war, battle, war.getAttackers()));
		assertEquals(BelligerentRole.DEFENDER, CampaignBattleSides.roleFor(war, battle, BattleTemplate.ATTACKER_SIDE));
		assertEquals(BelligerentRole.ATTACKER, CampaignBattleSides.roleFor(war, battle, BattleTemplate.DEFENDER_SIDE));
	}

	private static Faction faction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		return faction;
	}
}
