package net.tfminecraft.simplefactions.war.battle.warband;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSideLock;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;

class WarbandMembershipServiceTest {
	private UUID leaderId;
	private UUID memberId;
	private WarbandMembershipService service;
	private Player member;

	@BeforeEach
	void setUp() {
		WarbandMembershipService.resetForTests();
		WarbandManager.resetForTests();
		leaderId = UUID.randomUUID();
		memberId = UUID.randomUUID();
		service = WarbandMembershipService.getInstance();
		member = mock(Player.class);
		when(member.getUniqueId()).thenReturn(memberId);
		when(member.getName()).thenReturn("member");
	}

	@Test
	void quit_savesRejoinStateAndRemovesMember() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);

		service.handleQuit(member);

		assertNull(WarbandManager.getByMemberId(memberId));
		WarbandRejoinState state = service.getPendingRejoin(memberId);
		assertNotNull(state);
		assertEquals("alpha", state.getWarbandId());
		assertFalse(state.hasFaction());
		assertEquals(1, warband.getMemberCount());
	}

	@Test
	void join_restoresWhenWarbandStillOpen() {
		createOpenWarband("alpha", leaderId, memberId);
		service.handleQuit(member);

		assertTrue(service.attemptRejoin(memberId, "member", null));
		assertNull(service.getPendingRejoin(memberId));
		assertNotNull(WarbandManager.getByMemberId(memberId));
	}

	@Test
	void rejoinIsRefusedToAnAccountTiedToTheOtherSideAndTiesOtherwise() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		War war = new War(1, attacker, defender);
		try (var lock = mockStatic(CampaignWarbandSideLock.class, CALLS_REAL_METHODS);
				var wars = mockStatic(net.tfminecraft.simplefactions.managers.WarManager.class)) {
			lock.when(() -> CampaignWarbandSideLock.tieOf(warband))
					.thenReturn(new CampaignWarbandSideLock.Tie(war, CampaignSideRole.AGGRESSOR));

			war.bindAccountCoalition(memberId, CampaignSideRole.DEFENDER);
			service.handleQuit(member);
			assertFalse(service.attemptRejoin(memberId, "member", null));
			assertFalse(warband.hasMember(memberId));

			war.setAccountCoalitions(null);
			warband.addMember(memberId, Members.session(member).orElseThrow().ref());
			service.handleQuit(member);
			assertTrue(service.attemptRejoin(memberId, "member", null));
			assertEquals(CampaignSideRole.AGGRESSOR, war.getAccountCoalition(memberId));
		}
	}

	@Test
	void join_skipsWhenWarbandDeleted() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		service.handleQuit(member);
		WarbandManager.deleteWarband(warband);

		assertFalse(service.attemptRejoin(memberId, "member", null));
		assertNull(WarbandManager.getByMemberId(memberId));
	}

	@Test
	void evaluateRejoin_factionMismatchBlocksRejoin() throws Exception {
		Faction slotFaction = mock(Faction.class);
		when(slotFaction.getId()).thenReturn("fac1");
		Faction otherFaction = mock(Faction.class);
		when(otherFaction.getId()).thenReturn("fac2");

		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		markAsFaction(warband);

		WarbandRejoinState state = new WarbandRejoinState("alpha", slotFaction, null);

		assertFalse(service.evaluateRejoin(warband, memberId, "member", otherFaction, state));
	}

	@Test
	void rejoinNeedsPendingStateAndAFactionCapturedForAFactionWarband() throws Exception {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		markAsFaction(warband);

		assertFalse(service.attemptRejoin(memberId, "member", null));
		assertFalse(service.evaluateRejoin(
				warband, memberId, "member", null, new WarbandRejoinState("alpha", null, null)));
	}

	@Test
	void quitWithoutARecordedCharacterPreservesTheHeldMembership() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		warband.restoreMemberCharacters(java.util.Map.of());

		service.handleQuit((Player) null);
		assertTrue(warband.hasMember(memberId));
		service.handleQuit(memberId);

		assertTrue(warband.hasMember(memberId));
		assertNull(service.getPendingRejoin(memberId));
	}

	@Test
	void sessionStart_restoresTheSameCharacter() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		service.handleQuit(member);

		assertTrue(service.handleSessionStart(member, Members.session(member).orElseThrow()));
		assertTrue(warband.hasMember(memberId));
		assertNull(service.getPendingRejoin(memberId));
	}

	@Test
	void sessionStart_dropsTheStateForAnotherCharacterOnTheSameAccount() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		service.handleQuit(member);
		CharacterSession other = new CharacterSession(new CharacterRef(memberId, UUID.randomUUID()), 2L);

		try (var members = org.mockito.Mockito.mockStatic(Members.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			members.when(() -> Members.session(member)).thenReturn(java.util.Optional.of(other));
			assertFalse(service.handleSessionStart(member, other));
		}
		assertNull(service.getPendingRejoin(memberId));
		assertFalse(warband.hasMember(memberId));
		assertFalse(service.handleSessionStart(member, Members.session(member).orElseThrow()));
		assertFalse(warband.hasMember(memberId));
	}

	@Test
	void sessionStart_ignoresMissingArgumentsAndPlayersWithNothingPending() {
		CharacterSession session = Members.session(member).orElseThrow();

		assertFalse(service.handleSessionStart(null, session));
		assertFalse(service.handleSessionStart(member, null));
		assertFalse(service.handleSessionStart(member, session));
	}

	// Exercise the retained compatibility entry point so legacy behavior stays covered.
	@SuppressWarnings("deprecation")
	@Test
	void pendingLeaderDisplayName() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Brume");
		when(attacker.getLeader()).thenReturn("Alice");

		War war = new War(1, attacker, defender);
		Warband shell = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);

		assertTrue(shell.isPendingLeader());
		assertEquals("Pending signup", shell.getLeaderDisplayName());
		assertEquals("The Brume Host", shell.getName());
	}

	@Test
	void aCharacterWhoLeftTheirFactionLeavesItsWarHostButNotOtherWarbands() throws Exception {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		CharacterRef ref = warband.getMemberCharacter(memberId).orElseThrow();
		try (var battles = mockStatic(
				net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandBattleService.class)) {
			assertFalse(service.removeFromFactionWarband(ref));
			markAsFaction(warband);
			assertFalse(service.removeFromFactionWarband(new CharacterRef(memberId, UUID.randomUUID())));
			assertFalse(service.removeFromFactionWarband(new CharacterRef(UUID.randomUUID(), UUID.randomUUID())));
			assertTrue(service.removeFromFactionWarband(ref));
			battles.verify(() -> net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandBattleService
					.processLeave(memberId, null, warband, true));
			battles.verifyNoMoreInteractions();
		}
	}

	@Test
	void aPlaceKeptAfterQuittingIsDroppedOnlyForThatCharacter() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);
		CharacterRef ref = warband.getMemberCharacter(memberId).orElseThrow();
		service.handleQuit(member);
		assertFalse(service.removeFromFactionWarband(new CharacterRef(memberId, UUID.randomUUID())));
		assertNotNull(service.getPendingRejoin(memberId));
		assertFalse(service.removeFromFactionWarband(ref));
		assertNull(service.getPendingRejoin(memberId));
	}

	private Warband createOpenWarband(String id, UUID leader, UUID member) {
		Warband warband = Warband.createWithMemberIds(id, leader, false, member);
		warband.addMember(member, Members.session(this.member).orElseThrow().ref());
		WarbandManager.addWarband(warband);
		return warband;
	}

	private static void markAsFaction(Warband warband) throws Exception {
		Field factionField = Warband.class.getDeclaredField("faction");
		factionField.setAccessible(true);
		factionField.set(warband, true);
	}
}
