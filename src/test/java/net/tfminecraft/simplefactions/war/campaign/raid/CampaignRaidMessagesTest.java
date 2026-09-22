package net.tfminecraft.simplefactions.war.campaign.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.JoinResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.LaunchResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.ValidateLaunchResult;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class CampaignRaidMessagesTest {

	@Test
	void messageForLaunchResult_mapsWindowQuotaAndMutex() {
		assertEquals(
				CampaignRaidMessages.OUTSIDE_WINDOW,
				CampaignRaidMessages.messageForLaunchResult(LaunchResult.REJECTED_OUTSIDE_WINDOW));
		assertEquals(
				CampaignRaidMessages.SIDE_QUOTA_SPENT,
				CampaignRaidMessages.messageForLaunchResult(LaunchResult.REJECTED_QUOTA_SPENT));
		assertEquals(
				CampaignRaidMessages.RAID_IN_PROGRESS,
				CampaignRaidMessages.messageForLaunchResult(LaunchResult.REJECTED_RAID_IN_PROGRESS));
		assertNull(CampaignRaidMessages.messageForLaunchResult(LaunchResult.STARTED));
	}

	@Test
	void messageForValidateResult_mapsInvalidSourceTargetAndKind() {
		assertEquals(
				CampaignRaidMessages.INVALID_SOURCE,
				CampaignRaidMessages.messageForValidateResult(ValidateLaunchResult.REJECTED_INVALID_SOURCE));
		assertEquals(
				CampaignRaidMessages.INVALID_TARGET,
				CampaignRaidMessages.messageForValidateResult(ValidateLaunchResult.REJECTED_INVALID_TARGET));
		assertEquals(
				CampaignRaidMessages.KIND_MISMATCH,
				CampaignRaidMessages.messageForValidateResult(ValidateLaunchResult.REJECTED_KIND_MISMATCH));
	}

	@Test
	void messageForJoinResult_mapsWarbandAndMuster() {
		assertEquals(
				CampaignRaidMessages.IN_WARBAND,
				CampaignRaidMessages.messageForJoinResult(JoinResult.REJECTED_IN_WARBAND));
		assertEquals(
				CampaignRaidMessages.NOT_MUSTER,
				CampaignRaidMessages.messageForJoinResult(JoinResult.REJECTED_NOT_MUSTER));
	}

	@Test
	void buildRaidCalledMessage_containsTargetNameAndRaidId() {
		Faction launcher = mock(Faction.class);
		when(launcher.getName()).thenReturn("Attackers");
		Installation target = new Installation("port-def", "Harbor", InstallationKind.PORT, 20, 0, 0, 0L);

		String message = CampaignRaidMessages.buildRaidCalledMessage(launcher, target, "harbor_raid");

		assertNotNull(message);
		assertTrue(message.contains("Harbor"));
		assertTrue(message.contains("harbor_raid"));
		assertTrue(message.contains("/raid join"));
	}

	@Test
	void buildRaidStartedMessage_containsDisplayName() {
		Installation target = new Installation("port-def", "Harbor", InstallationKind.PORT, 20, 0, 0, 0L);
		assertTrue(CampaignRaidMessages.buildRaidStartedMessage(target, "Harbor Raid").contains("Harbor Raid"));
	}
}
