package net.tfminecraft.simplefactions.war.battle.campaign.warband;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.core.War;

class CampaignWarbandSideLockTest {
	private final UUID account = UUID.randomUUID();

	@Test
	void anUnboundAccountMayJoinEitherSideAndIsSavedOnceWhenBound() {
		War war = war();
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			assertNull(CampaignWarbandSideLock.refusal(war, CampaignCoalition.DEFENDER, account));

			CampaignWarbandSideLock.bind(war, CampaignCoalition.AGGRESSOR, account);
			CampaignWarbandSideLock.bind(war, CampaignCoalition.DEFENDER, account);

			assertEquals(CampaignCoalition.AGGRESSOR, war.getAccountCoalition(account));
			assertNull(CampaignWarbandSideLock.refusal(war, CampaignCoalition.AGGRESSOR, account));
			assertEquals(CampaignWarbandSideLock.OTHER_SIDE,
					CampaignWarbandSideLock.refusal(war, CampaignCoalition.DEFENDER, account));
			wars.verify(() -> WarManager.persist(war));
		}
	}

	@Test
	void missingInputsNeitherRefuseNorBind() {
		War war = war();
		war.bindAccountCoalition(account, CampaignCoalition.AGGRESSOR);
		assertNull(CampaignWarbandSideLock.refusal(null, CampaignCoalition.DEFENDER, account));
		assertNull(CampaignWarbandSideLock.refusal(war, null, account));
		assertNull(CampaignWarbandSideLock.refusal(war, CampaignCoalition.DEFENDER, null));
		assertNull(war.getAccountCoalition(null));
		assertFalse(war.bindAccountCoalition(null, CampaignCoalition.DEFENDER));
		assertFalse(war.bindAccountCoalition(UUID.randomUUID(), null));
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			CampaignWarbandSideLock.bind(null, CampaignCoalition.DEFENDER, account);
			wars.verify(() -> WarManager.persist(org.mockito.ArgumentMatchers.any()), never());
		}
	}

	@Test
	void aWarbandOutsideAnyBattleOrRaidHasNoTie() {
		War war = war();
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(java.util.List.of(war));
			assertNull(CampaignWarbandSideLock.tieOf(
					net.tfminecraft.simplefactions.war.battle.warband.Warband.createWithMemberIds("free", account, false, account)));
		}
	}

	private static War war() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		return new War(1, attacker, defender);
	}
}
