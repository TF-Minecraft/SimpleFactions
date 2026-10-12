package net.tfminecraft.simplefactions.war.battle.warband;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
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
	void quitWithoutACharacterLeavesNoRejoinState() {
		Warband warband = createOpenWarband("alpha", leaderId, memberId);

		service.handleQuit((Player) null);
		assertTrue(warband.hasMember(memberId));
		service.handleQuit(memberId);

		assertFalse(warband.hasMember(memberId));
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

		assertFalse(service.handleSessionStart(member, other));
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

	private Warband createOpenWarband(String id, UUID leader, UUID member) {
		Warband warband = Warband.createWithMemberIds(id, leader, false, member);
		WarbandManager.addWarband(warband);
		return warband;
	}

	private static void markAsFaction(Warband warband) throws Exception {
		Field factionField = Warband.class.getDeclaredField("faction");
		factionField.setAccessible(true);
		factionField.set(warband, true);
	}
}
