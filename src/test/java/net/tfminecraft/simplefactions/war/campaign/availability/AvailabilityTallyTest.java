package net.tfminecraft.simplefactions.war.campaign.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally.HourCount;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.core.War;

class AvailabilityTallyTest {
	private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

	private int[] saved;
	private War war;

	@BeforeEach
	void setUp() {
		saved = new int[] {Cache.warBattleWindowStartHour, Cache.warBattleWindowEndHour, Cache.warAvailabilityMinPerSide};
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warAvailabilityMinPerSide = 3;
		war = new War(4, faction("atk", "Alice", "Bob", "Cid", "Dot"), faction("def", "Eve", "Fay"));
	}

	@AfterEach
	void tearDown() {
		Cache.warBattleWindowStartHour = saved[0];
		Cache.warBattleWindowEndHour = saved[1];
		Cache.warAvailabilityMinPerSide = saved[2];
	}

	private static Faction faction(String id, String... members) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getMembers()).thenReturn(List.of(members));
		return faction;
	}

	private static UUID spoof(String name) {
		return BattleScheduleLookups.spoofMemberUuid(name);
	}

	@Test
	void marksAreCountedPerSideAtEachStartHourOfTheDate() {
		war.getAvailability().setMarks(spoof("Alice"), DAY, Set.of(21, 25));
		war.getAvailability().setMarks(spoof("Bob"), DAY, Set.of(21));
		war.getAvailability().setMarks(spoof("Eve"), DAY, Set.of(21, 30));
		war.getAvailability().setMarks(spoof("Eve"), DAY.plusDays(1), Set.of(22));
		war.getAvailability().setMarks(UUID.randomUUID(), DAY, Set.of(21));

		List<HourCount> counts = AvailabilityTally.count(war, DAY);

		assertEquals(List.of(new HourCount(21, 2, 1), new HourCount(22, 0, 0), new HourCount(23, 0, 0),
				new HourCount(24, 0, 0), new HourCount(25, 1, 0)), counts);
		assertEquals(1, counts.get(0).both());
		assertEquals(3, counts.get(0).total());
	}

	@Test
	void aSideLookupIsMadeOncePerAccount() {
		AtomicInteger lookups = new AtomicInteger();
		war.getAvailability().setMarks(spoof("Alice"), DAY, Set.of(21));
		war.getAvailability().setMarks(spoof("Alice"), DAY.plusDays(1), Set.of(21));
		var sides = AvailabilityTally.sides(war);
		java.util.function.Function<UUID, BelligerentRole> counted = account -> {
			lookups.incrementAndGet();
			return sides.apply(account);
		};
		assertEquals(BelligerentRole.ATTACKER, sides.apply(spoof("Alice")));
		assertEquals(BelligerentRole.ATTACKER, sides.apply(spoof("Alice")));
		assertNull(sides.apply(UUID.randomUUID()));
		assertEquals(BelligerentRole.DEFENDER, sides.apply(spoof("Fay")));
		AvailabilityTally.count(war, DAY, counted);
		assertEquals(1, lookups.get());
	}

	@Test
	void aSideNeedsMinPerSideOrAllOfItselfAndAtLeastOne() {
		assertEquals(3, AvailabilityTally.need(war, BelligerentRole.ATTACKER), "Four members, three needed");
		assertEquals(2, AvailabilityTally.need(war, BelligerentRole.DEFENDER), "Only two members: both");
		Cache.warAvailabilityMinPerSide = 1;
		assertEquals(1, AvailabilityTally.need(war, BelligerentRole.ATTACKER));
		War empty = new War(5, faction("a"), faction("b"));
		Cache.warAvailabilityMinPerSide = 3;
		assertEquals(1, AvailabilityTally.need(empty, BelligerentRole.DEFENDER), "An empty side still needs one");
	}

	@Test
	void inactiveMembersDoNotCountAndMercenariesDoOncePerAccount() {
		MercenaryCompany company = mock(MercenaryCompany.class);
		when(company.getEnlisted()).thenReturn(List.of("Merc", "Gone"));
		try (MockedStatic<InactivityService> inactivity = mockStatic(InactivityService.class);
				MockedStatic<MercenaryEngagements> engagements = mockStatic(MercenaryEngagements.class)) {
			inactivity.when(() -> InactivityService.activeOnly(any())).thenAnswer(call -> {
				List<String> active = new ArrayList<>();
				for (Object name : (Collection<?>) call.getArgument(0)) {
					if (!name.equals("Dot") && !name.equals("Gone")) {
						active.add((String) name);
					}
				}
				return active;
			});
			engagements.when(() -> MercenaryEngagements.on(war, war.getDefenders()))
					.thenReturn(List.of(new MercenaryEngagements.Engagement(company, null)));
			engagements.when(() -> MercenaryEngagements.on(war, war.getAttackers())).thenReturn(List.of());

			assertEquals(3, AvailabilityTally.sideSize(war, BelligerentRole.ATTACKER), "Dot is inactive");
			assertEquals(3, AvailabilityTally.sideSize(war, BelligerentRole.DEFENDER), "Eve, Fay and the mercenary");
		}
		try (MockedStatic<BattleScheduleLookups> lookups = mockStatic(BattleScheduleLookups.class, CALLS_REAL_METHODS)) {
			UUID shared = UUID.randomUUID();
			lookups.when(BattleScheduleLookups::memberNameToUuid)
					.thenReturn((java.util.function.Function<String, UUID>) name -> name.equals("Cid") ? null : shared);
			assertEquals(1, AvailabilityTally.sideSize(war, BelligerentRole.ATTACKER),
					"Characters of one account count once; one with no account not at all");
		}
	}

	@Test
	void coalitionAlliesCountForTheSideTheyJoined() {
		Faction ally = faction("ally", "Gus", "Hal");
		war.getDefenders().getMainParticipants().get(0).addCoalitionAlly(ally);
		assertEquals(4, AvailabilityTally.sideSize(war, BelligerentRole.DEFENDER));
		assertEquals(3, AvailabilityTally.need(war, BelligerentRole.DEFENDER));
	}

	@Test
	void eligibleAccountsListMembersThenMercenariesAttackersFirst() {
		MercenaryCompany company = mock(MercenaryCompany.class);
		when(company.getEnlisted()).thenReturn(List.of("Merc", "Alice"));
		UUID alice = UUID.randomUUID();
		UUID eve = UUID.randomUUID();
		UUID merc = UUID.randomUUID();
		try (MockedStatic<Members> members = mockStatic(Members.class);
				MockedStatic<MercenaryEngagements> engagements = mockStatic(MercenaryEngagements.class)) {
			members.when(() -> Members.accountId(any())).thenReturn(Optional.empty());
			members.when(() -> Members.accountId("Alice")).thenReturn(Optional.of(alice));
			members.when(() -> Members.accountId("Eve")).thenReturn(Optional.of(eve));
			members.when(() -> Members.accountId("Merc")).thenReturn(Optional.of(merc));
			engagements.when(() -> MercenaryEngagements.on(war, war.getAttackers())).thenReturn(List.of());
			engagements.when(() -> MercenaryEngagements.on(war, war.getDefenders()))
					.thenReturn(List.of(new MercenaryEngagements.Engagement(company, null)));

			Map<UUID, BelligerentRole> eligible = AvailabilityTally.eligibleAccounts(war);

			assertEquals(List.of(alice, eve, merc), List.copyOf(eligible.keySet()));
			assertEquals(BelligerentRole.ATTACKER, eligible.get(alice), "A member keeps their own side");
			assertEquals(BelligerentRole.DEFENDER, eligible.get(merc));
		}
	}

	@Test
	void aDateMeetsTheNeedOnlyWhenOneHourHasEnoughOfBothSides() {
		List<HourCount> counts = List.of(new HourCount(21, 3, 1), new HourCount(22, 1, 2), new HourCount(23, 2, 2));
		assertTrue(AvailabilityTally.meetsNeed(counts, 2, 2));
		assertFalse(AvailabilityTally.meetsNeed(counts, 3, 2), "Three attackers and two defenders never at one time");
	}

	@Test
	void theStartHourIsTheMostOfBothSidesThenTheMostPeopleThenTheEarliest() {
		assertEquals(23, AvailabilityTally.pickHour(List.of(
				new HourCount(21, 5, 1), new HourCount(22, 2, 2), new HourCount(23, 3, 2), new HourCount(24, 2, 3))));
		assertEquals(22, AvailabilityTally.pickHour(List.of(
				new HourCount(21, 2, 2), new HourCount(22, 2, 3), new HourCount(23, 2, 2))), "Ties go to the most people");
		assertEquals(21, AvailabilityTally.pickHour(List.of(new HourCount(21, 2, 2), new HourCount(22, 2, 2))));
		assertEquals(21, AvailabilityTally.pickHour(List.of(new HourCount(21, 0, 0), new HourCount(22, 0, 0))));
	}

	@Test
	void aForcedDayTakesTheMostMarkedHourAndKnowsWhenASideNeverMarked() {
		List<HourCount> oneSided = List.of(new HourCount(21, 1, 0), new HourCount(22, 3, 0), new HourCount(23, 3, 0));
		assertEquals(22, AvailabilityTally.pickForcedHour(oneSided));
		assertTrue(AvailabilityTally.oneSideAbsent(oneSided));
		List<HourCount> nobody = List.of(new HourCount(21, 0, 0), new HourCount(22, 0, 0));
		assertEquals(21, AvailabilityTally.pickForcedHour(nobody));
		assertTrue(AvailabilityTally.oneSideAbsent(nobody));
		assertFalse(AvailabilityTally.oneSideAbsent(List.of(new HourCount(21, 1, 0), new HourCount(22, 0, 1))));
		assertTrue(AvailabilityTally.oneSideAbsent(List.of(new HourCount(21, 0, 2))));
	}
}
