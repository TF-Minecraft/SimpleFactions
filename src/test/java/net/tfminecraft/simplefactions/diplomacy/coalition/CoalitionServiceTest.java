package net.tfminecraft.simplefactions.diplomacy.coalition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.truce.TruceService;

class CoalitionServiceTest extends CoalitionTestBase {

	@Test
	void theDesignExampleFormsAndTheTargetIsTold() {
		Coalition league = league();

		assertEquals(List.of("thalendor", "bog", "fig"), league.getMemberIds());
		assertTrue(league.isLeader("THALENDOR"));
		assertTrue(league.isTarget("holy"));
		assertFalse(league.isOffensive());
		assertNull(league.getSingleMemberSince());
		assertSame(league, CoalitionService.of(fig));
		assertSame(league, CoalitionService.byName("grand league"));
		assertSame(league, CoalitionService.byName(league.getId()));
		assertEquals(List.of(league), CoalitionService.targeting(holy));
		assertTrue(CoalitionService.sameCoalition(bog, fig));
		assertFalse(CoalitionService.sameCoalition(bog, holy));
		assertFalse(CoalitionService.sameCoalition(holy, bog));
		assertFalse(CoalitionService.sameCoalition(bog, null));
		verify(hana).sendMessage(contains("has formed the"));
		verify(tom).sendMessage("§abog §ajoined the Grand League §acoalition.");
	}

	@Test
	void queriesTolerateMissingFactionsAndNames() {
		assertNull(CoalitionService.of(null));
		assertNull(CoalitionService.byName(null));
		assertNull(CoalitionService.byName("nobody"));
		assertTrue(CoalitionService.targeting(null).isEmpty());
		assertFalse(CoalitionService.isIndependent(null));
		assertFalse(CoalitionService.isIndependent(sanctuary));
		assertEquals("Iron Pact", CoalitionService.plainName("#ff0000Iron §lPact "));
	}

	@Test
	void formingIsRefusedForEveryRuleInTurn() {
		CoalitionSettings.enabled = false;
		assertEquals(CoalitionService.DISABLED, CoalitionService.create(thalendor, "League", holy).message());
		CoalitionSettings.enabled = true;

		assertEquals("§cOnly independent factions can be in a coalition.",
				CoalitionService.create(sanctuary, "League", holy).message());
		assertEquals("§cA coalition name must be 3 to 32 characters long.",
				CoalitionService.create(thalendor, "#123456ab", holy).message());
		assertEquals("§cA coalition name must be 3 to 32 characters long.",
				CoalitionService.create(thalendor, null, holy).message());
		assertEquals("§cA coalition name may only use letters, numbers, spaces, apostrophes and hyphens.",
				CoalitionService.create(thalendor, "League!", holy).message());
		assertEquals(CoalitionService.NO_FACTION, CoalitionService.create(thalendor, "League", null).message());
		assertEquals("§cA coalition cannot target one of its own members.",
				CoalitionService.create(thalendor, "League", thalendor).message());
		assertEquals("§cA coalition can only target an independent faction.",
				CoalitionService.create(thalendor, "League", sanctuary).message());
		assertTrue(CoalitionService.create(fig, "Weak Pact", bog).message().contains("is not strong enough"));

		allies(thalendor, holy);
		assertEquals("§cthalendor §cand holy §care allies.", CoalitionService.create(thalendor, "League", holy).message());
		holy.setRelation(thalendor, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
		thalendor.setRelation(holy, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
		allies(holy, thalendor);
		thalendor.setRelation(holy, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
		assertTrue(CoalitionService.create(thalendor, "League", holy).message().endsWith("§care allies."));
		holy.setRelation(thalendor, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));

		var pact = fixture.relationType("pact", Map.of("treaty", true, "blocks-war", true));
		thalendor.getDiplomacyHandler().setTreatyRelation(holy, pact);
		assertEquals("§cthalendor §chas a treaty with holy §cthat forbids war.",
				CoalitionService.create(thalendor, "League", holy).message());
		thalendor.getDiplomacyHandler().removeTreatyRelation("holy");

		TruceService.record("thalendor", "holy", Instant.now().plus(Duration.ofDays(3)), 9);
		assertEquals("§cthalendor §chas a truce with holy§c.", CoalitionService.create(thalendor, "League", holy).message());
		TruceService.reset();

		CoalitionSettings.costBase = 1_000_000;
		assertTrue(CoalitionService.create(thalendor, "League", holy).message().contains("free diplomatic capacity"));
		CoalitionSettings.costBase = 0;

		assertTrue(CoalitionService.create(thalendor, "League", holy).ok());
		assertEquals("§cThere is already a coalition with that name.",
				CoalitionService.create(fig, "league", holy).message());
		assertEquals("§cthalendor §cis already in a coalition.",
				CoalitionService.create(thalendor, "Other", holy).message());
	}

	@Test
	void theGateComparesWholeRealmsAgainstTheStrongestMember() {
		thalendor.setPrestige(1150.0);
		// The Holy Order alone (1000) is weaker, but its realm with Sanctuary (1200) is not.
		assertTrue(CoalitionService.create(thalendor, "League", holy).ok());
		CoalitionSettings.gateRatio = 2.0;
		assertTrue(CoalitionService.gateBlock(List.of(thalendor), holy).contains("(1150)"));
		assertNull(CoalitionService.gateBlock(List.of(), holy));
	}

	@Test
	void invitesAreCheckedAndOnlyTheLeaderMayInvite() {
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.invite(bog, fig).message());
		CoalitionService.create(thalendor, "League", holy);
		Coalition league = CoalitionService.of(thalendor);
		assertEquals(CoalitionService.NO_FACTION, CoalitionService.invite(thalendor, null).message());
		assertEquals("§cThat faction is already in your coalition.", CoalitionService.invite(thalendor, thalendor).message());
		assertEquals("§cYou cannot invite your coalition's target.", CoalitionService.invite(thalendor, holy).message());
		assertEquals("§cOnly independent factions can be in a coalition.", CoalitionService.invite(thalendor, sanctuary).message());
		Faction rival = faction("rival", "Ria", 300);
		CoalitionService.create(rival, "Other Pact", holy);
		assertEquals("§crival §cis already in a coalition.", CoalitionService.invite(thalendor, rival).message());

		assertTrue(CoalitionService.invite(thalendor, bog).ok());
		verify(bea).sendMessage(contains("/coalition join League"));
		assertTrue(league.isInvited("bog", CoalitionRegistry.now()));
		join(league, bog);
		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.invite(bog, fig).message());

		league.setTargetId("gone");
		assertTrue(CoalitionService.invite(thalendor, fig).ok());
		verify(finn).sendMessage(contains("against its target"));
	}

	@Test
	void joiningIsRefusedForEveryRuleInTurn() {
		CoalitionService.create(thalendor, "League", holy);
		Coalition league = CoalitionService.of(thalendor);
		assertEquals(CoalitionService.NO_COALITION, CoalitionService.join(bog, null).message());
		assertEquals("§cYou have not been invited to that coalition.", CoalitionService.join(bog, league).message());
		CoalitionService.invite(thalendor, bog);
		CoalitionSettings.enabled = false;
		assertEquals(CoalitionService.DISABLED, CoalitionService.join(bog, league).message());
		CoalitionSettings.enabled = true;

		CoalitionRegistry.setCooldown("bog", CoalitionRegistry.now() + Duration.ofHours(30).toMillis());
		assertEquals("§cbog §cleft a coalition recently and can join or form one again in 1d 6h.",
				CoalitionService.join(bog, league).message());
		CoalitionRegistry.clearCooldown("bog");

		allies(bog, holy);
		assertEquals("§cbog §cand holy §care allies.", CoalitionService.join(bog, league).message());
		bog.setRelation(holy, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));
		holy.setRelation(bog, new Relation(RelationLoader.getDefaultType(), RelationLoader.getDefaultAttitude()));

		bog.setPrestige(5000.0);
		assertTrue(CoalitionService.join(bog, league).message().contains("is not strong enough"));
		bog.setPrestige(124.0);

		War war = WarManager.addWar(new War(70, bog, thalendor));
		assertEquals("§cYou are at war with thalendor§c, a member of that coalition.", CoalitionService.join(bog, league).message());
		WarManager.get().remove(war);

		CoalitionSettings.costBase = 1_000_000;
		assertTrue(CoalitionService.join(bog, league).message().contains("free diplomatic capacity"));
		CoalitionSettings.costBase = 0;

		league.setTargetLostAt(CoalitionRegistry.now());
		assertEquals("§cThat coalition has lost its target.", CoalitionService.join(bog, league).message());
		league.setTargetLostAt(null);
		league.setTargetId("gone");
		assertEquals("§cThat coalition has lost its target.", CoalitionService.join(bog, league).message());
		league.setTargetId("holy");

		clock.advance(Duration.ofDays(8));
		assertEquals("§cYou have not been invited to that coalition.", CoalitionService.join(bog, league).message());
	}

	@Test
	void leavingStartsACooldownAndHandsOnTheLead() {
		Coalition league = league();
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.leave(holy).message());

		assertEquals("§7You left the Grand League §7coalition.", CoalitionService.leave(thalendor).message());
		// FIG has more realm prestige than the Bog.
		assertTrue(league.isLeader("fig"));
		assertNotNull(CoalitionRegistry.cooldownUntil("thalendor"));
		verify(bea).sendMessage("§efig §enow leads the Grand League §ecoalition.");
		assertTrue(CoalitionService.create(thalendor, "Again", holy).message().contains("left a coalition recently"));

		CoalitionService.leave(bog);
		assertNotNull(league.getSingleMemberSince());
		CoalitionService.leave(fig);
		assertNull(CoalitionRegistry.get(league.getId()));
	}

	@Test
	void aMemberFightingAWarItWasCalledIntoCannotLeave() {
		Coalition league = league();
		War war = WarManager.addWar(new War(71, holy, thalendor));
		war.getParticipant(thalendor).addCoalitionAlly(bog);

		assertEquals("§cYou cannot leave while fighting in a war you joined through the coalition.",
				CoalitionService.leave(bog).message());
		assertTrue(league.isMember("bog"));
		assertTrue(CoalitionService.leave(fig).ok());
	}

	@Test
	void theLeaderManagesMembers() {
		Coalition league = league();
		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.kick(bog, fig).message());
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.kick(holy, fig).message());
		assertEquals("§cThat faction is not in your coalition.", CoalitionService.kick(thalendor, holy).message());
		assertEquals("§cThat faction is not in your coalition.", CoalitionService.kick(thalendor, null).message());
		assertEquals("§cYou cannot remove yourself. Leave or disband instead.", CoalitionService.kick(thalendor, thalendor).message());
		assertTrue(CoalitionService.kick(thalendor, bog).ok());
		verify(bea).sendMessage("§cYou were removed from the Grand League §ccoalition.");
		assertNotNull(CoalitionRegistry.cooldownUntil("bog"));

		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.transferLeadership(fig, thalendor).message());
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.transferLeadership(holy, thalendor).message());
		assertEquals("§cThat faction is not in your coalition.", CoalitionService.transferLeadership(thalendor, bog).message());
		assertEquals("§cThat faction is not in your coalition.", CoalitionService.transferLeadership(thalendor, null).message());
		assertEquals("§cYou already lead the coalition.", CoalitionService.transferLeadership(thalendor, thalendor).message());
		assertTrue(CoalitionService.transferLeadership(thalendor, fig).ok());
		assertTrue(league.isLeader("fig"));
	}

	@Test
	void anewTargetCanOnlyBeChosenOnceTheOldOneIsLost() {
		Coalition league = league();
		league.setOffensive(true);
		Faction sunsora = faction("sunsora", "Sol", 1100);
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.retarget(holy, sunsora).message());
		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.retarget(bog, sunsora).message());
		assertEquals("§cYou can only choose a new target after losing the old one.",
				CoalitionService.retarget(thalendor, sunsora).message());

		league.setTargetLostAt(CoalitionRegistry.now());
		assertEquals(CoalitionService.NO_FACTION, CoalitionService.retarget(thalendor, null).message());
		assertEquals("§cA coalition cannot target one of its own members.", CoalitionService.retarget(thalendor, fig).message());
		sunsora.setPrestige(10.0);
		assertTrue(CoalitionService.retarget(thalendor, sunsora).message().contains("is not strong enough"));
		sunsora.setPrestige(1100.0);

		assertTrue(CoalitionService.retarget(thalendor, sunsora).ok());
		assertTrue(league.isTarget("sunsora"));
		assertNull(league.getTargetLostAt());
		assertFalse(league.isOffensive());
		verify(fixture.player("Sol"), never()).sendMessage(contains("ignored"));
	}

	@Test
	void disbandingWaitsForCoalitionWarsAndStartsCooldowns() {
		Coalition league = league();
		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.disband(bog).message());
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.disband(holy).message());
		War war = WarManager.addWar(new War(72, holy, bog));
		assertEquals("§cYou cannot disband the coalition while one of its wars is running.",
				CoalitionService.disband(thalendor).message());
		WarManager.get().remove(war);

		assertTrue(CoalitionService.disband(thalendor).ok());
		assertNull(CoalitionRegistry.get(league.getId()));
		assertNotNull(CoalitionRegistry.cooldownUntil("fig"));
		verify(finn).sendMessage("§7The Grand League §7coalition was disbanded by its leader.");
	}

	@Test
	void bannersFollowTheLeaderUntilOneIsSet() {
		Coalition league = league();
		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.setBanner(bog, new ItemStack(Material.RED_BANNER)).message());
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.setBanner(holy, null).message());
		assertEquals("§cHold a banner in your main hand.", CoalitionService.setBanner(thalendor, null).message());
		assertEquals("§cHold a banner in your main hand.",
				CoalitionService.setBanner(thalendor, new ItemStack(Material.DIRT)).message());
		assertEquals(thalendor.getBanner().getType(), CoalitionService.banner(league).getType());

		assertTrue(CoalitionService.setBanner(thalendor, new ItemStack(Material.RED_BANNER)).ok());
		assertEquals(List.of("RED.BASE"), league.getBannerPatterns());
		assertEquals(Material.RED_BANNER, CoalitionService.banner(league).getType());

		assertEquals(CoalitionService.NOT_LEADER, CoalitionService.resetBanner(bog).message());
		assertEquals(CoalitionService.NOT_IN_COALITION, CoalitionService.resetBanner(holy).message());
		assertTrue(CoalitionService.resetBanner(thalendor).ok());
		assertNull(league.getBannerPatterns());

		league.setLeaderId("gone");
		assertEquals(Material.WHITE_BANNER, CoalitionService.banner(league).getType());
	}

	@Test
	void offensiveStatusIsAnnouncedToMembersAndTarget() {
		Coalition league = league();
		CoalitionService.setOffensive(league, true);
		assertTrue(league.isOffensive());
		verify(hana).sendMessage("§eThe Grand League §ecoalition against you §cis now offensive§e.");
		league.setTargetId("gone");
		CoalitionService.setOffensive(league, false);
		verify(bea).sendMessage("§eThe Grand League §ecoalition §eis now defensive§e.");
	}

	@Test
	void staffCanDisbandRemoveAndAppoint() {
		Coalition league = league();
		assertFalse(CoalitionService.adminKick(league, holy).ok());
		assertFalse(CoalitionService.adminKick(league, null).ok());
		assertTrue(CoalitionService.adminKick(league, bog).ok());
		assertNull(CoalitionRegistry.cooldownUntil("bog"));
		assertFalse(CoalitionService.adminSetLeader(league, bog).ok());
		assertFalse(CoalitionService.adminSetLeader(league, null).ok());
		assertTrue(CoalitionService.adminSetLeader(league, fig).ok());
		assertTrue(league.isLeader("fig"));
		CoalitionService.adminDisband(league);
		assertNull(CoalitionRegistry.get(league.getId()));
		assertNull(CoalitionRegistry.cooldownUntil("fig"));
	}

	@Test
	void staffAddWithoutAnInviteButUnderEveryOtherRule() {
		CoalitionService.create(thalendor, "League", holy);
		Coalition league = CoalitionService.of(thalendor);
		assertEquals(CoalitionService.NO_FACTION, CoalitionService.adminAdd(league, null).message());
		CoalitionSettings.enabled = false;
		assertEquals(CoalitionService.DISABLED, CoalitionService.adminAdd(league, bog).message());
		CoalitionSettings.enabled = true;
		assertEquals("§cOnly independent factions can be in a coalition.", CoalitionService.adminAdd(league, sanctuary).message());
		assertEquals("§cthalendor §cis already in a coalition.", CoalitionService.adminAdd(league, thalendor).message());

		CoalitionService.Result added = CoalitionService.adminAdd(league, bog);
		assertEquals("§aAdded bog §ato the League §acoalition.", added.message());
		assertTrue(league.isMember("bog"));
		assertNull(league.getSingleMemberSince());
		verify(bea).sendMessage("§aStaff added you to the League §acoalition.");
	}

	@Test
	void alliancesAcrossACoalitionAndItsTargetAreRefused() {
		Coalition league = league();
		assertNotNull(CoalitionService.allianceBlock(bog, holy));
		assertNotNull(CoalitionService.allianceBlock(holy, bog));
		assertNull(CoalitionService.allianceBlock(bog, fig));
		Faction outsider = faction("outsider", "Otto", 10);
		assertNull(CoalitionService.allianceBlock(outsider, holy));

		assertFalse(RelationManager.setRelation(null, ally, holy, bog, false, false));
		assertTrue(RelationManager.setRelation(null, ally, holy, bog, false, true));
		assertTrue(RelationManager.isAlly(bog, holy));
		assertTrue(league.isMember("bog"));
	}

	@Test
	void theTimerDropsMembersThatLoseTheirIndependenceAndPassesTheLead() {
		Coalition league = league();
		league.invite("outsider", CoalitionRegistry.now() + 1000);
		fixture.subject(holy, thalendor);
		clock.advance(Duration.ofSeconds(5));

		CoalitionService.tick();

		assertFalse(league.isMember("thalendor"));
		assertTrue(league.isLeader("fig"));
		assertTrue(league.getInvites().isEmpty());
		verify(bea).sendMessage("§7thalendor §7is no longer independent and left the Grand League §7coalition.");
		assertNull(CoalitionRegistry.cooldownUntil("thalendor"));
	}

	@Test
	void theTimerWaitsForTheFactionsAndEndsEmptyCoalitions() {
		Coalition league = league();
		List<Faction> saved = List.copyOf(FactionManager.factions);
		FactionManager.factions.clear();
		CoalitionService.tick();
		assertNotNull(CoalitionRegistry.get(league.getId()));
		FactionManager.loading = true;
		FactionManager.factions.addAll(saved);
		CoalitionService.tick();
		assertNotNull(CoalitionRegistry.get(league.getId()));
		FactionManager.loading = false;

		for (Faction member : List.of(thalendor, bog, fig)) fixture.subject(holy, member);
		CoalitionService.tick();
		assertNull(CoalitionRegistry.get(league.getId()));
	}

	@Test
	void aLostTargetStartsACountdownThatWaitsForCoalitionWars() {
		Coalition league = league();
		Faction overlord = faction("overlord", "Ove", 5000);
		fixture.subject(overlord, holy);
		CoalitionService.onRelationChanged();
		assertNotNull(league.getTargetLostAt());
		verify(tom).sendMessage(contains("has lost its target"));
		assertEquals(Duration.ofDays(7), CoalitionService.targetLossRemaining(league));

		// A war between a member and the old target pauses the countdown.
		War war = WarManager.addWar(new War(73, holy, bog));
		CoalitionService.tick();
		clock.advance(Duration.ofMinutes(5));
		CoalitionService.tick();
		assertEquals(Duration.ofMinutes(5).toMillis(), league.getTargetLossPausedMillis());
		// A relation change in between re-checks the coalition without pausing it twice.
		clock.advance(Duration.ofMinutes(1));
		CoalitionService.onRelationChanged();
		assertEquals(Duration.ofMinutes(5).toMillis(), league.getTargetLossPausedMillis());
		WarManager.get().remove(war);

		clock.advance(Duration.ofDays(7).minusMinutes(2));
		CoalitionService.tick();
		assertNotNull(CoalitionRegistry.get(league.getId()));
		clock.advance(Duration.ofMinutes(1));
		CoalitionService.tick();
		assertNull(CoalitionRegistry.get(league.getId()));
		verify(finn).sendMessage("§cThe Grand League §ccoalition disbanded: it found no new target in time.");
	}

	@Test
	void aTargetThatRegainsIndependenceStaysTheTarget() {
		Coalition league = league();
		league.setTargetLostAt(CoalitionRegistry.now());
		CoalitionService.tick();
		assertNull(league.getTargetLostAt());
		verify(bea).sendMessage("§aholy §ais independent again and remains the coalition's target.");
		assertNull(CoalitionService.targetLossRemaining(league));
	}

	@Test
	void aCoalitionNobodyJoinsDisbandsAfterAWeek() {
		CoalitionService.create(thalendor, "League", holy);
		Coalition league = CoalitionService.of(thalendor);
		league.setSingleMemberSince(null);
		CoalitionService.tick();
		assertNotNull(league.getSingleMemberSince());
		CoalitionService.tick();
		assertNotNull(CoalitionRegistry.get(league.getId()));

		join(league, bog);
		league.setSingleMemberSince(CoalitionRegistry.now());
		CoalitionService.tick();
		assertNull(league.getSingleMemberSince());
		CoalitionService.kick(thalendor, bog);

		clock.advance(Duration.ofDays(7));
		CoalitionService.tick();
		assertNull(CoalitionRegistry.get(league.getId()));
		verify(tom).sendMessage("§cThe League §ccoalition disbanded: no other faction joined it.");
	}

	@Test
	void aDeletedTargetIsNeverReplacedByANewFactionWithItsId() {
		Coalition league = league();
		FactionManager.factions.remove(holy);
		CoalitionService.forget("holy");
		assertNotNull(league.getTargetLostAt());
		assertEquals("holy (disbanded)", league.getTargetId());
		faction("holy", "Newcomer", 5000);
		CoalitionService.tick();
		assertNotNull(league.getTargetLostAt());
	}

	@Test
	void turningCoalitionsOffStopsEverythingButLeavingAndStaffTools() {
		Coalition league = league();
		CoalitionSettings.enabled = false;
		assertEquals(CoalitionService.DISABLED, CoalitionService.invite(thalendor, holy).message());
		assertEquals(CoalitionService.DISABLED, CoalitionService.retarget(thalendor, holy).message());
		assertEquals(0.0, CoalitionCost.costFor(thalendor));
		CoalitionCodePrompt.begin(tom, thalendor);
		verify(tom).sendMessage(CoalitionService.DISABLED);
		War war = WarManager.addWar(new War(74, holy, thalendor));
		assertNull(CoalitionWars.callingCoalition(war, thalendor, bog));
		assertTrue(CoalitionService.leave(fig).ok());
		assertTrue(league.isMember("bog"));
	}

	@Test
	void aDeletedFactionIsForgottenEverywhere() {
		Coalition league = league();
		league.invite("ghost", CoalitionRegistry.now() + 1000);
		CoalitionRegistry.setCooldown("ghost", CoalitionRegistry.now() + 1000);
		FactionManager.factions.remove(bog);
		CoalitionService.forget("bog");
		CoalitionService.forget("ghost");
		assertFalse(league.isMember("bog"));
		assertFalse(league.getInvites().containsKey("ghost"));
		assertNull(CoalitionRegistry.cooldownUntil("ghost"));
	}
}
