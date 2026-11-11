package net.tfminecraft.simplefactions.war.battle.military;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.wounds.BattleForce;
import net.tfminecraft.simplefactions.war.wounds.ForceKind;
import net.tfminecraft.simplefactions.war.wounds.ForcePart;
import net.tfminecraft.simplefactions.war.wounds.TestForces;
import net.tfminecraft.simplefactions.war.wounds.WarLedger;

class BattleForcesTest {
	private static final int PROVINCE_ID = 42;
	private List<War> savedWars;
	private int savedLives;
	private int savedDeaths;

	@BeforeEach
	void setUp() {
		savedWars = new ArrayList<>(WarManager.get());
		WarManager.get().clear();
		savedLives = Cache.warBattleLivesPerRegiment;
		savedDeaths = Cache.warBattleDeathsPerRegimentLoss;
		Cache.warBattleLivesPerRegiment = 5;
		Cache.warBattleDeathsPerRegimentLoss = 5;
	}

	@AfterEach
	void tearDown() {
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
		Cache.warBattleLivesPerRegiment = savedLives;
		Cache.warBattleDeathsPerRegimentLoss = savedDeaths;
	}

	@Test
	void invasionAttackerBringsOffensiveRegimentsAndDefenderEverythingWithMilitiaAtHome() {
		Faction attacker = faction("atk", regiment("professional", 10, 0, true), regiment("militia", 4, 4, false));
		Faction defender = faction("def", regiment("professional", 8, 2, true), regiment("garrison", 2, 0, false),
				regiment("militia", 6, 6, false));
		War war = war(1, attacker, defender);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(defender);

			assertEquals(PoolMode.OFFENSIVE, BattleForces.resolvePoolMode(war, war.getAttackers()));
			assertEquals(PoolMode.DEFENSIVE, BattleForces.resolvePoolMode(war, war.getDefenders()));
			List<BattleForce> attack = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null);
			assertEquals(List.of(new ForcePart("professional", false, 0, 10)), attack.get(0).parts());
			assertEquals(50, BattleForce.lives(attack));

			List<BattleForce> defence = BattleForces.build(war, PROVINCE_ID, war.getDefenders(), null, null);
			assertEquals(ForceKind.REGIMENTS, defence.get(0).kind());
			assertEquals("def", defence.get(0).ownerId());
			assertEquals(List.of(new ForcePart("professional", false, 2, 6), new ForcePart("garrison", false, 0, 2),
					new ForcePart("militia", true, 6, 0)), defence.get(0).parts());
			assertEquals(16, BattleForces.regimentSlots(war, PROVINCE_ID, war.getDefenders(), PoolMode.DEFENSIVE));
		}
	}

	@Test
	void counterPushSwapsWhoAttacks() {
		Faction attacker = faction("atk", regiment("professional", 10, 0, true), regiment("militia", 4, 0, false));
		Faction defender = faction("def", regiment("professional", 8, 0, true), regiment("militia", 6, 0, false));
		War war = war(2, attacker, defender);
		war.setCampaignPhase(CampaignPhase.COUNTER_PUSH);
		war.setInitiativeHolder(BelligerentRole.DEFENDER);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(attacker);
			assertEquals(PoolMode.DEFENSIVE, BattleForces.resolvePoolMode(war, war.getAttackers()));
			assertEquals(14, BattleForce.regimentSlots(BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null)));
			assertEquals(8, BattleForce.regimentSlots(BattleForces.build(war, PROVINCE_ID, war.getDefenders(), null, null)));
		}
	}

	@Test
	void militiaOnlyDefendsItsOwnersProvinceUnlessItIsACivilWar() {
		Faction owner = faction("owner", regiment("militia", 5, 5, false));
		Faction ally = faction("ally", regiment("militia", 7, 7, false));
		War war = war(3, owner, faction("def", regiment("professional", 1, 0, true)));
		war.getAttackers().getMainParticipants().get(0).getAllies().put(ally, true);

		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(owner);
			assertEquals(5, BattleForces.regimentSlots(war, PROVINCE_ID, war.getAttackers(), PoolMode.DEFENSIVE));
			assertEquals(0, BattleForces.regimentSlots(war, PROVINCE_ID, war.getAttackers(), PoolMode.OFFENSIVE));
			assertEquals(0, BattleForces.regimentSlots(war, null, war.getAttackers(), PoolMode.DEFENSIVE));

			war.setMovementId("mov-cw");
			assertEquals(12, BattleForces.regimentSlots(war, PROVINCE_ID, war.getAttackers(), PoolMode.OFFENSIVE));
		}
	}

	@Test
	void equipmentMercenaryAndLevyRowsAreNotSoldiers() {
		Regiment artillery = regiment("artillery", 3, 0, true);
		when(artillery.isEquipment()).thenReturn(true);
		Regiment company = regiment("mercenary", 2, 0, true);
		when(company.isMercenary()).thenReturn(true);
		Regiment levy = regiment("levy", 9, 0, true);
		when(levy.isLevy()).thenReturn(true);
		Faction attacker = faction("atk", regiment("professional", 4, 0, true), artillery, company, levy);
		War war = war(4, attacker, faction("def"));

		assertEquals(4, BattleForces.regimentSlots(war, PROVINCE_ID, war.getAttackers(), PoolMode.OFFENSIVE));
		assertTrue(BattleForces.build(war, PROVINCE_ID, war.getDefenders(), PoolMode.DEFENSIVE, null, null).isEmpty());
	}

	@Test
	void woundedPaidSlotsStayHomeButLawSlotsAlwaysFight() {
		Faction attacker = faction("atk", regiment("professional", 10, 4, true));
		War war = war(5, attacker, faction("def"));
		War other = war(6, attacker, faction("def2"));
		other.getLedger().addAdjustment(new WarLedger.Adjustment("ATK", "professional", 17, "staff", null, "now"));
		WarManager.get().add(other);

		List<BattleForce> forces = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null);
		// 17 wounded lives are 3 wounded slots out of the 6 paid ones; the 4 law slots are whole.
		assertEquals(List.of(new ForcePart("professional", false, 4, 3)), forces.get(0).parts());

		other.getLedger().markSettled();
		assertEquals(10, BattleForce.regimentSlots(BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null)));
	}

	@Test
	void subjectsLendTheirLevyShareOfEveryRegimentIncludingMilitia() {
		Faction holder = faction("holder", regiment("professional", 2, 0, true));
		Faction subject = faction("subject", regiment("militia", 6, 4, false), regiment("garrison", 4, 0, false));
		levyPercent(subject, 50);
		War war = war(7, holder, faction("def"));

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("holder")).thenReturn(holder);
			subjects(relations, holder, subject);
			relations.when(() -> RelationManager.getOverlord(subject)).thenReturn("holder");
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(null);

			List<BattleForce> forces = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null);

			assertEquals(2, forces.size());
			BattleForce levy = forces.get(1);
			assertEquals(ForceKind.LEVY, levy.kind());
			assertEquals("subject", levy.ownerId());
			// Half of 6 militia (4 law, 2 paid) and half of 4 paid garrison, in any battle type.
			assertEquals(List.of(new ForcePart("militia", true, 2, 1), new ForcePart("garrison", false, 0, 2)),
					levy.parts());
		}
	}

	@Test
	void aSubjectDefendingItsOwnProvinceBringsAllItsMilitiaAndLendsTheRest() {
		Faction holder = faction("holder", regiment("professional", 2, 0, true));
		Faction subject = faction("subject", regiment("militia", 6, 4, false), regiment("garrison", 4, 0, false));
		levyPercent(subject, 50);
		War war = war(8, faction("atk"), holder);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("holder")).thenReturn(holder);
			subjects(relations, holder, subject);
			relations.when(() -> RelationManager.getOverlord(subject)).thenReturn("holder");
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(subject);

			List<BattleForce> defence = BattleForces.build(war, PROVINCE_ID, war.getDefenders(), null, null);
			assertEquals(ForceKind.MILITIA, defence.get(1).kind());
			assertEquals(List.of(new ForcePart("militia", true, 4, 2)), defence.get(1).parts());
			assertEquals(List.of(new ForcePart("garrison", false, 0, 2)), defence.get(2).parts());

			// Attacking into its own province (a counter-attack) the militia stays a levy share.
			List<BattleForce> attack = BattleForces.build(war, PROVINCE_ID, war.getDefenders(), PoolMode.OFFENSIVE, null, null);
			assertEquals(ForceKind.LEVY, attack.get(1).kind());
			assertEquals(2, attack.size());
		}
	}

	@Test
	void aSubjectWithoutMilitiaDefendingHomeStillLends() {
		Faction holder = faction("holder", regiment("professional", 2, 0, true));
		Faction subject = faction("subject", regiment("garrison", 4, 0, false));
		War war = war(9, faction("atk"), holder);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("holder")).thenReturn(holder);
			subjects(relations, holder, subject);
			relations.when(() -> RelationManager.getOverlord(subject)).thenReturn("holder");
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(subject);

			List<BattleForce> defence = BattleForces.build(war, PROVINCE_ID, war.getDefenders(), null, null);
			assertEquals(List.of(ForceKind.REGIMENTS, ForceKind.LEVY), defence.stream().map(BattleForce::kind).toList());
		}
	}

	@Test
	void nestedLeviesGoToTheNearestFighterAndEachOverlordTakesItsShare() {
		Faction main = faction("m", regiment("professional", 1, 0, true));
		Faction middle = faction("v", regiment("professional", 1, 0, true));
		Faction deep = faction("v3", regiment("garrison", 8, 0, false), regiment("militia", 3, 3, false));
		levyPercent(middle, 50);
		Faction orphan = faction("orphan", regiment("garrison", 8, 0, false));
		War war = war(10, main, faction("def"));

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			subjects(relations, main, middle);
			subjects(relations, middle, deep, orphan);
			relations.when(() -> RelationManager.getOverlord(deep)).thenReturn("v");
			relations.when(() -> RelationManager.getOverlord(middle)).thenReturn("m");
			relations.when(() -> RelationManager.getOverlord(orphan)).thenReturn("");
			factions.when(() -> FactionManager.getByString("v")).thenReturn(middle);
			factions.when(() -> FactionManager.getByString("m")).thenReturn(main);

			// The middle overlord does not fight, so the deep levy passes through it at 50%.
			List<BattleForce> forces = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null);
			BattleForce deepLevy = forces.stream().filter(force -> force.ownerId().equals("v3")).findFirst().orElseThrow();
			assertEquals(List.of(new ForcePart("garrison", false, 0, 4), new ForcePart("militia", true, 2, 0)),
					deepLevy.parts());
			assertFalse(forces.stream().anyMatch(force -> force.ownerId().equals("orphan")));

			// When the middle overlord fights, the deep levy goes to it whole.
			war.getAttackers().getMainParticipants().get(0).getSubjects().add(middle);
			BattleForce direct = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null).stream()
					.filter(force -> force.ownerId().equals("v3")).findFirst().orElseThrow();
			assertEquals(11, direct.slots());
		}
	}

	@Test
	void levyStopsAtTheSubjectsActiveMembers() {
		Faction holder = faction("holder");
		Faction subject = faction("subject", regiment("garrison", 8, 0, false), regiment("professional", 8, 0, true),
				regiment("militia", 8, 0, false));
		when(subject.getMembers()).thenReturn(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
		War war = war(11, holder, faction("def"));

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("holder")).thenReturn(holder);
			subjects(relations, holder, subject);
			relations.when(() -> RelationManager.getOverlord(subject)).thenReturn("holder");

			BattleForce levy = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null).get(0);
			assertEquals(List.of(new ForcePart("garrison", false, 0, 8), new ForcePart("professional", false, 0, 2)),
					levy.parts());

			when(subject.getMembers()).thenReturn(null);
			assertTrue(BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null).isEmpty());
		}
	}

	@Test
	void aLevyRoundedAwayByAnOverlordIsNotLent() {
		Faction main = faction("m");
		Faction middle = faction("v");
		levyPercent(middle, 10);
		Faction deep = faction("v3", regiment("garrison", 2, 0, false));
		War war = war(12, main, faction("def"));

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			subjects(relations, main, middle);
			subjects(relations, middle, deep);
			relations.when(() -> RelationManager.getOverlord(deep)).thenReturn("v");
			relations.when(() -> RelationManager.getOverlord(middle)).thenReturn("m");
			factions.when(() -> FactionManager.getByString("v")).thenReturn(middle);
			factions.when(() -> FactionManager.getByString("m")).thenReturn(main);

			// 10% of the deep subject's 2 slots rounds to nothing, and the middle one has no army.
			assertTrue(BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null).isEmpty());
		}
	}

	@Test
	void holderLookupFollowsTheOverlordChain() {
		Faction child = faction("child");
		Faction parent = faction("parent");
		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			relations.when(() -> RelationManager.getOverlord(child)).thenReturn("parent");
			relations.when(() -> RelationManager.getOverlord(parent)).thenReturn(null);
			factions.when(() -> FactionManager.getByString("parent")).thenReturn(parent);

			assertEquals(parent, BattleForces.findNearestFighterHolder(child, Set.of("parent")));
			assertNull(BattleForces.findNearestFighterHolder(child, Set.of("other")));
			factions.when(() -> FactionManager.getByString("parent")).thenReturn(null);
			assertNull(BattleForces.findNearestFighterHolder(child, Set.of("other")));
		}
	}

	@Test
	void mercenariesBringTheirMembersOnTheRosterUpToTheirHealthySlots() {
		War war = war(13, faction("atk", regiment("professional", 1, 0, true)), faction("def"));
		MercenaryCompany company = company("guild-a", 3);
		MercenaryEngagements.Engagement first = new MercenaryEngagements.Engagement(company,
				mock(net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract.class));
		MercenaryEngagements.Engagement second = new MercenaryEngagements.Engagement(company,
				mock(net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract.class));
		BattleSide roster = mock(BattleSide.class);
		Battle battle = mock(Battle.class);
		Battle running = mock(Battle.class);
		BattleSide runningSide = mock(BattleSide.class);
		when(running.hasStarted()).thenReturn(true);
		when(running.getSides()).thenReturn(List.of(runningSide));
		when(runningSide.getForces()).thenReturn(List.of(TestForces.mercenary("GUILD-A", 1),
				TestForces.mercenary("guild-b", 4), TestForces.regiments(2).get(0)));
		Battle idle = mock(Battle.class);
		BattleManager.get().addAll(List.of(running, idle, battle));
		try (MockedStatic<MercenaryEngagements> engagements = mockStatic(MercenaryEngagements.class)) {
			engagements.when(() -> MercenaryEngagements.on(war, war.getAttackers())).thenReturn(List.of(first, second));
			engagements.when(() -> MercenaryEngagements.coveringMembers(first, roster)).thenReturn(5);
			engagements.when(() -> MercenaryEngagements.coveringMembers(second, roster)).thenReturn(1);
			engagements.when(() -> MercenaryEngagements.membersOnRoster(company, roster)).thenReturn(5);

			List<BattleForce> forces = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), battle, roster);

			// 3 healthy slots, 1 already fighting elsewhere: the first contract takes the other 2.
			assertEquals(2, forces.size());
			assertEquals(new BattleForce(ForceKind.MERCENARY, "guild-a", 5,
					List.of(new ForcePart("mercenary", false, 0, 2))), forces.get(1));
			assertEquals(1, BattleForce.regimentSlots(forces));
		} finally {
			BattleManager.get().removeAll(List.of(running, idle, battle));
		}
	}

	@Test
	void aCompanyHiredByTwoAlliesOnOneSideCountsItsRosterOnce() {
		War war = war(16, faction("atk"), faction("def"));
		MercenaryCompany company = company("guild-c", 10);
		MercenaryEngagements.Engagement first = new MercenaryEngagements.Engagement(company,
				mock(net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract.class));
		MercenaryEngagements.Engagement second = new MercenaryEngagements.Engagement(company,
				mock(net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract.class));
		BattleSide roster = mock(BattleSide.class);
		try (MockedStatic<MercenaryEngagements> engagements = mockStatic(MercenaryEngagements.class)) {
			engagements.when(() -> MercenaryEngagements.on(war, war.getAttackers())).thenReturn(List.of(first, second));
			engagements.when(() -> MercenaryEngagements.coveringMembers(any(), any())).thenReturn(3);
			engagements.when(() -> MercenaryEngagements.membersOnRoster(company, roster)).thenReturn(3);

			List<BattleForce> forces = BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, roster);

			// Three members on the roster are three soldiers, not three per contract.
			assertEquals(3, forces.stream().mapToInt(BattleForce::slots).sum());
		}
	}

	@Test
	void nothingWithoutAWarOrSide() {
		assertTrue(BattleForces.build(null, PROVINCE_ID, null, PoolMode.OFFENSIVE, null, null).isEmpty());
		War war = war(14, faction("atk"), faction("def"));
		assertTrue(BattleForces.build(war, PROVINCE_ID, war.getAttackers(), null, null, null).isEmpty());
		assertTrue(BattleForces.build(war, PROVINCE_ID, null, PoolMode.OFFENSIVE, null, null).isEmpty());
		assertEquals(PoolMode.DEFENSIVE, BattleForces.resolvePoolMode(null, war.getAttackers()));
		assertEquals(PoolMode.DEFENSIVE, BattleForces.resolvePoolMode(war, null));
		assertFalse(BattleForces.isMilitiaEligible(null, PROVINCE_ID));
		Faction noId = mock(Faction.class);
		assertFalse(BattleForces.isMilitiaEligible(noId, PROVINCE_ID));
		Faction factionWithoutMilitary = mock(Faction.class);
		when(factionWithoutMilitary.getId()).thenReturn("bare");
		War bare = war(15, factionWithoutMilitary, faction("def"));
		assertTrue(BattleForces.build(bare, PROVINCE_ID, bare.getAttackers(), null, null).isEmpty());
		try (MockedStatic<TitleManager> titles = mockStatic(TitleManager.class)) {
			titles.when(() -> TitleManager.getByProvince(PROVINCE_ID)).thenReturn(null);
			assertFalse(BattleForces.isMilitiaEligible(factionWithoutMilitary, PROVINCE_ID));
		}
	}

	@Test
	void aSideWithNoOffensiveCoalitionDefends() {
		War war = mock(War.class);
		when(war.getInitiativeHolder()).thenReturn(null);
		assertEquals(PoolMode.DEFENSIVE, BattleForces.resolvePoolMode(war, mock(net.tfminecraft.simplefactions.war.core.Side.class)));
	}

	private static MercenaryCompany company(String guildId, int healthySlots) {
		MercenaryCompany company = mock(MercenaryCompany.class);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn(guildId);
		when(company.getGuild()).thenReturn(guild);
		Regiment regiment = mock(Regiment.class);
		when(regiment.getId()).thenReturn("mercenary");
		when(company.getRegiment()).thenReturn(regiment);
		when(company.getHealthySlots(any())).thenReturn(healthySlots);
		return company;
	}

	private static void subjects(MockedStatic<RelationManager> relations, Faction overlord, Faction... subjects) {
		relations.when(() -> RelationManager.getSubjects(overlord)).thenReturn(Arrays.asList(subjects));
	}

	private static void levyPercent(Faction faction, double percent) {
		FactionModifier levy = mock(FactionModifier.class);
		when(levy.getAmount()).thenReturn(percent);
		when(faction.getModifier(FactionModifiers.LEVY)).thenReturn(levy);
	}

	private static War war(int id, Faction attacker, Faction defender) {
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		War war = new War(id, attacker, defender);
		war.setCampaignProvinces(List.of(PROVINCE_ID, 43, 44));
		war.setCursorIndex(0);
		war.setCampaignPhase(CampaignPhase.INVASION);
		return war;
	}

	static Faction faction(String id, Regiment... regiments) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l",
				"m", "n", "o", "p", "q", "r", "s", "t"));
		levyPercent(faction, 100);
		Military military = mock(Military.class);
		when(military.getRegiments()).thenReturn(List.of(regiments));
		for (Regiment regiment : regiments) {
			when(military.getRegiment(regiment.getId())).thenReturn(regiment);
		}
		when(faction.getMilitary()).thenReturn(military);
		return faction;
	}

	static Regiment regiment(String id, int current, int free, boolean offensive) {
		Regiment regiment = mock(Regiment.class);
		when(regiment.getId()).thenReturn(id);
		when(regiment.isOffensive()).thenReturn(offensive);
		when(regiment.getCurrentSlots()).thenReturn(current);
		when(regiment.getFreeSlots()).thenReturn(free);
		when(regiment.getPaidSlots()).thenReturn(Math.max(0, current - free));
		return regiment;
	}
}
