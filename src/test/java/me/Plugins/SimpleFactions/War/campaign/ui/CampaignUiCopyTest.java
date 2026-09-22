package me.Plugins.SimpleFactions.War.campaign.ui;


import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;

class CampaignUiCopyTest {

	@Test
	void navyBlockadeDeclareMessage_usesLockedCopy() {
		assertEquals(
				"§cYou need an operational port for a naval path. Source ships before the battle.",
				CampaignUiCopy.navyBlockadeDeclareMessage());
	}

	@Test
	void navalAutoLossCopy_usesLockedStrings() {
		assertEquals(
				"§cBerth a naval vehicle at a committed port before the naval battle or you will auto-lose.",
				CampaignUiCopy.navalAutoLossLeaderPing());
		assertEquals(
				"§eAttacker had no berthed navy at a committed port. §7Defender wins the naval slot.",
				CampaignUiCopy.navalAutoLossBroadcast());
	}

	@Test
	void formatBattleKind_formatsNavalKinds() {
		assertEquals("Field Battle", CampaignUiCopy.formatBattleKind(CampaignBattleKind.FIELD));
		assertEquals("Siege", CampaignUiCopy.formatBattleKind(CampaignBattleKind.SIEGE));
		assertEquals("Naval Battle", CampaignUiCopy.formatBattleKind(CampaignBattleKind.NAVAL));
		assertEquals("Naval Invasion", CampaignUiCopy.formatBattleKind(CampaignBattleKind.NAVAL_INVASION));
		assertEquals("Field Battle", CampaignUiCopy.formatBattleKind(null));
	}

	@Test
	void titleCasePhase_formatsKnownPhases() {
		assertEquals("Invasion", CampaignUiCopy.titleCasePhase(CampaignPhase.INVASION));
		assertEquals("Retake", CampaignUiCopy.titleCasePhase(CampaignPhase.RETAKE));
		assertEquals("Counter Push", CampaignUiCopy.titleCasePhase(CampaignPhase.COUNTER_PUSH));
		assertEquals("Invasion", CampaignUiCopy.titleCasePhase(null));
	}

	@Test
	void formatInitiativeHolder_formatsRoles() {
		assertEquals("Attacker", CampaignUiCopy.formatInitiativeHolder(BelligerentRole.ATTACKER));
		assertEquals("Defender", CampaignUiCopy.formatInitiativeHolder(BelligerentRole.DEFENDER));
		assertEquals("Attacker", CampaignUiCopy.formatInitiativeHolder(null));
	}

	@Test
	void formatBattleDay_usesDdMmYyyy() {
		assertEquals("23/08/2026", CampaignUiCopy.formatBattleDay(LocalDate.of(2026, 8, 23)));
		assertEquals("-", CampaignUiCopy.formatBattleDay(null));
	}

	@Test
	void resolveActivityStatus_prefersVotingOverIdle() {
		War war = baseWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		assertEquals("Currently Voting", CampaignUiCopy.resolveActivityStatus(war));
	}

	@Test
	void resolveActivityStatus_awaitingDecisionWhenChoicePending() {
		War war = baseWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);
		assertEquals("Awaiting Decision", CampaignUiCopy.resolveActivityStatus(war));
	}

	@Test
	void resolveActivityStatus_awaitingBattleWhenScheduled() {
		War war = baseWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		assertEquals("Awaiting Battle", CampaignUiCopy.resolveActivityStatus(war));
	}

	@Test
	void resolveActivityStatus_betweenBattlesWhenIdle() {
		War war = baseWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);
		assertEquals("Between Battles", CampaignUiCopy.resolveActivityStatus(war));
	}

	private War baseWar() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		return war;
	}
}
