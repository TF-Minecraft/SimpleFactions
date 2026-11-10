package net.tfminecraft.simplefactions.diplomacy.coalition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.war.core.CallToArms;
import net.tfminecraft.simplefactions.war.core.CallToArmsEligibility;
import net.tfminecraft.simplefactions.war.core.CallToArmsService;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;

class CoalitionWarsTest extends CoalitionTestBase {

	@AfterEach
	void clearRequests() throws Exception {
		requests().clear();
	}

	@SuppressWarnings("unchecked")
	private static Map<Player, Request> requests() throws Exception {
		Field field = RequestManager.class.getDeclaredField("requests");
		field.setAccessible(true);
		return (Map<Player, Request>) field.get(null);
	}

	/** A war that starts after the league was formed; the test declares it with {@code WarManager.addWar}. */
	private War war(int id, Faction attacker, Faction defender) {
		War war = new War(id, attacker, defender);
		clock.now = war.getStartedAt().minus(Duration.ofHours(1));
		return war;
	}

	@Test
	void aDefensiveCoalitionAnswersWhenAMemberIsAttackedByTheTarget() {
		War war = war(80, holy, thalendor);
		Coalition league = league();
		WarManager.addWar(war);
		CallToArmsService.snapshotCallable(war);

		assertSame(league, CoalitionWars.callingCoalition(war, thalendor, bog));
		assertEquals(List.of(bog, fig), CoalitionWars.callableMembers(war, thalendor));
		assertTrue(CoalitionWars.isCoalitionWar(war, league, thalendor));
		assertTrue(CoalitionWars.activeCoalitionWar(league));
		assertTrue(CallToArmsEligibility.canCall(war, thalendor, bog).allowed());
		assertTrue(war.call(thalendor, bog));
		assertFalse(war.call(thalendor, bog));
		assertTrue(war.getParticipant(thalendor).hasCoalitionAlly(bog));
		assertTrue(war.isParticipating(bog));
		assertSame(war.getDefenders(), war.getSide(bog));
		assertTrue(CoalitionWars.joinedThroughCoalition(bog));
		assertFalse(CoalitionWars.joinedThroughCoalition(fig));
		assertEquals(List.of(fig), CoalitionWars.callableMembers(war, thalendor));
		// A member called in is no main participant, so it cannot call the others.
		assertNull(CoalitionWars.callingCoalition(war, bog, fig));
	}

	@Test
	void aDefensiveCoalitionDoesNotJoinItsMembersAttacks() {
		War war = war(81, thalendor, holy);
		Coalition league = league();
		WarManager.addWar(war);
		assertNull(CoalitionWars.callingCoalition(war, thalendor, bog));
		assertFalse(war.call(thalendor, bog));
		league.setOffensive(true);
		assertSame(league, CoalitionWars.callingCoalition(war, thalendor, bog));

		// Joining mid-war only counts for defence, for the caller as much as the called.
		Faction late = faction("late", "Lea", 50);
		War lateWar = war(99, late, holy);
		clock.now = war.getStartedAt().plus(Duration.ofMinutes(5));
		join(league, late);
		assertNull(CoalitionWars.callingCoalition(war, thalendor, late));
		assertNull(CoalitionWars.callingCoalition(lateWar, late, bog));
	}

	@Test
	void aTargetCalledInAsAnAllyBringsTheCoalitionIn() {
		Faction raider = faction("raider", "Rex", 300);
		War war = war(82, raider, thalendor);
		Coalition league = league();
		WarManager.addWar(war);
		assertNull(CoalitionWars.callingCoalition(war, thalendor, bog));

		war.getParticipant(raider).getAllies().put(holy, true);

		assertSame(league, CoalitionWars.callingCoalition(war, thalendor, bog));
	}

	@Test
	void everyOtherWarIsLeftAlone() {
		War war = war(83, holy, thalendor);
		Coalition league = league();
		WarManager.addWar(war);
		Faction outsider = faction("outsider", "Otto", 20);
		assertNull(CoalitionWars.callingCoalition(war, null, bog));
		assertNull(CoalitionWars.callingCoalition(war, thalendor, null));
		assertNull(CoalitionWars.callingCoalition(war, thalendor, thalendor));
		assertNull(CoalitionWars.callingCoalition(war, outsider, bog));
		assertNull(CoalitionWars.callingCoalition(war, thalendor, outsider));
		assertTrue(CoalitionWars.callableMembers(war, outsider).isEmpty());

		// An ally is called as an ally, so it is listed once.
		allies(thalendor, bog);
		war.getParticipant(thalendor).update(war);
		assertNull(CoalitionWars.callingCoalition(war, thalendor, bog));

		league.setTargetLostAt(CoalitionRegistry.now());
		assertNull(CoalitionWars.callingCoalition(war, thalendor, fig));
		league.setTargetLostAt(null);
		league.setTargetId("gone");
		assertNull(CoalitionWars.callingCoalition(war, thalendor, fig));
		assertFalse(CoalitionWars.activeCoalitionWar(league));
		league.setTargetId("holy");

		assertFalse(CoalitionWars.isCoalitionWar(null, league, thalendor));
		war.end(WarEndReason.ADMIN_END);
		assertNull(CoalitionWars.callingCoalition(war, thalendor, fig));
	}

	@Test
	void aMemberCannotBeCalledAgainstAFellowMember() {
		Faction raider = faction("raider", "Rex", 300);
		War war = war(84, raider, thalendor);
		league();
		WarManager.addWar(war);
		allies(raider, bog);
		war.getParticipant(raider).update(war);
		CallToArmsService.snapshotCallable(war);

		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(war, raider, bog);

		assertFalse(result.allowed());
		assertEquals("§cThat faction is in a coalition with your enemy.", result.message());
		assertNull(CoalitionWars.enemyCoalitionBlock(war, thalendor, bog));
	}

	@Test
	void declaringWarAcrossACoalitionWarnsBothWays() {
		Coalition league = league();
		assertEquals(List.of(
				"§cthalendor §cis in the Grand League §ccoalition, which targets you.",
				"§cIts members can be called into this war."), CoalitionWars.declareWarnings(holy, thalendor));
		assertTrue(CoalitionWars.declareWarnings(thalendor, holy).isEmpty());
		league.setOffensive(true);
		assertEquals(List.of("§aYou can call members of the Grand League §acoalition into this war."),
				CoalitionWars.declareWarnings(thalendor, holy));
		league.setTargetLostAt(CoalitionRegistry.now());
		assertTrue(CoalitionWars.declareWarnings(thalendor, holy).isEmpty());
		assertTrue(CoalitionWars.declareWarnings(holy, thalendor).isEmpty());
		assertTrue(CoalitionWars.declareWarnings(fig, bog).isEmpty());
	}

	@Test
	void callingTheTargetAsAnAllyWarnsTheCallerAndTheAlly() {
		Faction raider = faction("raider", "Rex", 300);
		War war = war(85, raider, thalendor);
		Coalition league = league();
		WarManager.addWar(war);
		assertEquals("§cCalling holy §cwill let the Grand League §ccoalition (thalendor§c, bog§c, fig§c) join this war against you.",
				CoalitionWars.allyCallWarning(war, raider, holy));
		assertEquals("§cAccepting will let the Grand League §ccoalition, which targets you, join this war.",
				CoalitionWars.acceptWarning(war, raider, holy));
		assertNull(CoalitionWars.allyCallWarning(war, raider, bog));
		assertNull(CoalitionWars.allyCallWarning(war, thalendor, holy));

		War attack = war(86, thalendor, raider);
		assertNull(CoalitionWars.allyCallWarning(attack, raider, holy));
		league.setOffensive(true);
		assertNull(CoalitionWars.acceptWarning(attack, thalendor, holy));
		assertTrue(CoalitionWars.allyCallWarning(attack, raider, holy).startsWith("§cCalling holy"));
		league.setTargetLostAt(CoalitionRegistry.now());
		assertNull(CoalitionWars.allyCallWarning(attack, raider, holy));
	}

	@Test
	void membersHearOnceThatTheyCanBeCalled() {
		War war = war(87, holy, thalendor);
		league();
		WarManager.addWar(war);
		Faction outsider = faction("outsider", "Otto", 20);
		war.getAttackers().addNewParticipant(outsider, war.getParticipant(holy));

		CoalitionWars.announce(war);
		CoalitionWars.announce(war);

		verify(bea, times(1)).sendMessage(contains("can now be called into"));
	}

	@Test
	void aCoalitionCallIsSentAnsweredAndRememberedAsOne() throws Exception {
		War war = war(88, holy, thalendor);
		Coalition league = league();
		WarManager.addWar(war);
		// The league already stood when the war was declared.
		CallToArmsService.snapshotCallable(war);
		CallToArmsService.send(tom, thalendor, bog, war);
		CallToArms call = war.getCallsToArms().get("bog");
		assertTrue(call.isCoalitionCall());
		assertEquals(league.getId(), call.coalitionId());
		verify(bea).sendMessage(contains("as a fellow member of the Grand League"));
		CallToArms stored = WarMapper.fromData(WarMapper.toData(war)).getCallsToArms().get("bog");
		assertEquals(call, stored);

		CallToArmsService.accept(bea, war);

		assertTrue(war.getParticipant(thalendor).hasCoalitionAlly(bog));
		assertTrue(war.getCallsToArms().isEmpty());
		verify(tom).sendMessage("bog §aaccepted your call to arms: " + war.getName() + "§a.");
		War restored = WarMapper.fromData(WarMapper.toData(war));
		assertTrue(restored.getParticipant(thalendor).hasCoalitionAlly(bog));
		assertTrue(restored.isParticipating(bog));
		Participant participant = restored.getParticipant(thalendor);
		participant.clean(bog);
		assertFalse(participant.hasCoalitionAlly(bog));
		assertFalse(participant.addCoalitionAlly(null));
		assertFalse(participant.hasCoalitionAlly(null));
	}

	@Test
	void decliningACoalitionCallCostsMoreAndTellsTheCoalitionLeader() throws Exception {
		War war = war(89, holy, fig);
		league();
		WarManager.addWar(war);
		CallToArmsService.snapshotCallable(war);
		Government government = mock(Government.class);
		Field field = Faction.class.getDeclaredField("government");
		field.setAccessible(true);
		field.set(bog, government);
		CallToArmsService.send(finn, fig, bog, war);

		CallToArmsService.decline(bea, war);

		ArgumentCaptor<StabilityModifier> captor = ArgumentCaptor.forClass(StabilityModifier.class);
		verify(government).addStabilityModifier(captor.capture());
		assertEquals(Cache.warDeclinedCoalitionStabilityPenalty, captor.getValue().getModifier());
		verify(tom).sendMessage("bog §cdeclined a coalition call to arms from fig§c.");
		verify(finn).sendMessage("bog §cdeclined your call to arms: " + war.getName() + "§c.");

		// The leader calling needs no second notice.
		War second = war(90, holy, thalendor);
		CallToArmsService.send(tom, thalendor, bog, second);
		CallToArmsService.decline(bea, second);
		verify(tom, never()).sendMessage("bog §cdeclined a coalition call to arms from thalendor§c.");
		verify(government, times(2)).addStabilityModifier(any());
	}

	@Test
	void anAllyBeingTheTargetIsWarnedInTheCall() {
		Faction raider = faction("raider", "Rex", 300);
		allies(raider, holy);
		War war = war(91, raider, thalendor);
		league();
		WarManager.addWar(war);
		CallToArmsService.send(fixture.player("Rex"), raider, holy, war);
		verify(hana).sendMessage("§cAccepting will let the Grand League §ccoalition, which targets you, join this war.");
		assertFalse(war.getCallsToArms().get("holy").isCoalitionCall());

		CallToArmsService.accept(hana, war);

		verify(tom).sendMessage(contains("can now be called into"));
	}

	@Test
	void aCoalitionMemberWhoJoinedAfterTheDeclarationCannotBeCalledIn() {
		War war = war(92, holy, thalendor);
		assertTrue(CoalitionService.create(thalendor, "Grand League", holy).ok());
		Coalition league = CoalitionService.of(thalendor);
		join(league, fig);
		WarManager.addWar(war);
		// The Bog is not at war, so it may still join, but it was no partner when the war began.
		join(league, bog);

		CallToArmsEligibility.Result result = CallToArmsEligibility.canCall(war, thalendor, bog);

		assertFalse(result.allowed());
		assertEquals(CallToArmsService.NOT_ALLIED_AT_DECLARATION, result.message());
		CallToArmsService.send(tom, thalendor, bog, war);
		verify(tom).sendMessage(CallToArmsService.NOT_ALLIED_AT_DECLARATION);
		assertTrue(war.getCallsToArms().isEmpty());
	}

	@Test
	void membersCannotDeclareWarOnEachOther() {
		league();
		assertEquals("§cYou cannot declare war on a member of your coalition.",
				new WarGoalValidator().validate(WarDeclareRequest.of(bog, fig, WarGoalType.TRIBUTARY)).getMessage());
	}
}
