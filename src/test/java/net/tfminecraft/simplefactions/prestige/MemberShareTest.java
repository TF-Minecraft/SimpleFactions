package net.tfminecraft.simplefactions.prestige;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;

class MemberShareTest {

	private static final int HOUR = 3600;

	@BeforeEach
	void setUp() {
		RecentPlaytime.reset();
		RecentPlaytime.setToday(() -> 20_000L);
	}

	@AfterEach
	void tearDown() {
		RecentPlaytime.reset();
		NameKeyedMembers.install();
	}

	private static double heads(List<String> members) {
		return MemberShare.count(members).heads();
	}

	/** Lifetime playtime is set so tests can tell the recent split from the fallback. */
	private static String character(SharedIdentity identity, UUID owner, CharacterStatus status, long lifetime) {
		CharacterRef ref = new CharacterRef(owner, UUID.randomUUID());
		identity.infos.put(ref.characterId(), new CharacterInfo(ref, "Character", status, null, Set.of(), lifetime, Instant.EPOCH));
		return Members.key(ref);
	}

	@Test
	void anAccountSplitsOneMemberByRecentPlay() {
		try (var identity = new SharedIdentity()) {
			UUID auta = UUID.randomUUID();
			String main = character(identity, auta, CharacterStatus.ALIVE, 900 * HOUR);
			String second = character(identity, auta, CharacterStatus.ALIVE, 10 * HOUR);
			String third = character(identity, auta, CharacterStatus.ALIVE, 10 * HOUR);
			RecentPlaytime.credit(main, 7 * HOUR);
			RecentPlaytime.credit(second, 2 * HOUR);
			RecentPlaytime.credit(third, HOUR);

			assertEquals(0.7, MemberShare.shareOf(main), 1e-9);
			assertEquals(0.2, MemberShare.shareOf(second), 1e-9);
			assertEquals(0.1, MemberShare.shareOf(third), 1e-9);
			assertEquals(1.0, heads(List.of(main, second, third)), 1e-9,
					"All of an account's characters in one faction are still one member");
		}
	}

	@Test
	void anAltThatIsNotPlayedAddsNothing() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			String main = character(identity, account, CharacterStatus.ALIVE, 300 * HOUR);
			String alt = character(identity, account, CharacterStatus.ALIVE, 0);
			RecentPlaytime.credit(main, 20 * HOUR);

			assertEquals(1.0, MemberShare.shareOf(main), 1e-9);
			assertEquals(0.0, MemberShare.shareOf(alt), 1e-9);
		}
	}

	@Test
	void unaffiliatedAndOldPlayStillCountTowardTheSplit() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			String member = character(identity, account, CharacterStatus.ALIVE, 0);
			String wanderer = character(identity, account, CharacterStatus.ALIVE, 0);
			RecentPlaytime.setToday(() -> 19_000L);
			RecentPlaytime.credit(member, 100 * HOUR);
			RecentPlaytime.setToday(() -> 20_000L);
			RecentPlaytime.credit(member, HOUR);
			RecentPlaytime.credit(wanderer, 3 * HOUR);

			assertEquals(0.25, heads(List.of(member)), 1e-9,
					"Time on a character outside the faction counts; time outside the window does not");
		}
	}

	@Test
	void withoutRecentPlayTheAccountSplitsByLifetime() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			String veteran = character(identity, account, CharacterStatus.ALIVE, 300 * HOUR);
			String alt = character(identity, account, CharacterStatus.ALIVE, 100 * HOUR);

			assertEquals(0.75, MemberShare.shareOf(veteran), 1e-9);
			assertEquals(0.25, MemberShare.shareOf(alt), 1e-9);
		}
	}

	@Test
	void withNoPlayAtAllTheAccountSplitsEvenly() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			String first = character(identity, account, CharacterStatus.ALIVE, 0);
			String second = character(identity, account, CharacterStatus.ALIVE, 0);

			assertEquals(0.5, MemberShare.shareOf(first), 1e-9);
			assertEquals(0.5, MemberShare.shareOf(second), 1e-9);
		}
	}

	@Test
	void deadCharactersTakeNoShare() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			String dead = character(identity, account, CharacterStatus.DEAD, 400 * HOUR);
			String living = character(identity, account, CharacterStatus.ALIVE, 0);
			RecentPlaytime.credit(dead, 40 * HOUR);
			RecentPlaytime.credit(living, HOUR);

			assertEquals(0.0, MemberShare.shareOf(dead), 1e-9);
			assertEquals(1.0, MemberShare.shareOf(living), 1e-9);

			UUID allDead = UUID.randomUUID();
			String last = character(identity, allDead, CharacterStatus.DEAD, 0);
			assertEquals(0.0, MemberShare.shareOf(last), 1e-9);

			UUID unreadable = UUID.randomUUID();
			String lost = character(identity, unreadable, CharacterStatus.DEAD, 0);
			when(identity.provider.characters(unreadable)).thenReturn(Optional.empty());
			assertEquals(0.0, MemberShare.shareOf(lost), 1e-9, "Dead counts nothing even when the account cannot be read");
		}
	}

	@Test
	void membersTheDirectoryCannotPlaceCountWhole() {
		try (var identity = new SharedIdentity()) {
			UUID unreadable = UUID.randomUUID();
			String member = character(identity, unreadable, CharacterStatus.ALIVE, 0);
			when(identity.provider.characters(unreadable)).thenReturn(Optional.empty());

			assertEquals(1.0, MemberShare.shareOf(member), 1e-9);
			assertEquals(3.0, heads(List.of(member, Members.key(UUID.randomUUID()), "Pending")), 1e-9);
			assertEquals(0.0, heads(List.of()), 1e-9);
			assertEquals(0.0, heads(null), 1e-9);
		}
	}

	@Test
	void aPassSplitsEachAccountOnce() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			String first = character(identity, account, CharacterStatus.ALIVE, HOUR);
			String second = character(identity, account, CharacterStatus.ALIVE, HOUR);
			UUID unreadable = UUID.randomUUID();
			String lost = character(identity, unreadable, CharacterStatus.ALIVE, 0);
			when(identity.provider.characters(unreadable)).thenReturn(Optional.empty());

			MemberShare.withinPass(() -> {
				assertEquals(1.0, heads(List.of(first, second)), 1e-9);
				MemberShare.withinPass(() -> assertEquals(3.0, heads(List.of(first, second, lost, lost)), 1e-9));
			});
			verify(identity.provider, times(1)).characters(account);
			verify(identity.provider, times(1)).characters(unreadable);
			verify(identity.provider, times(3)).info(any());

			MemberShare.shareOf(first);
			verify(identity.provider, times(2)).characters(account);
			heads(List.of(first, second));
			verify(identity.provider, times(3)).characters(account);
		}
	}

	@Test
	void membersAsksTheResolverForAnAccountsCharacters() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			character(identity, account, CharacterStatus.ALIVE, 0);
			assertEquals(1, Members.characters(account).orElseThrow().size());
			assertTrue(Members.characters(null).isEmpty());
			identity.unregister();
			assertTrue(Members.characters(account).isEmpty(), "No provider, no answer");
			identity.register();
		}
		NameKeyedMembers.install();
		assertTrue(Members.characters(UUID.randomUUID()).isEmpty(), "Resolvers without a directory know no accounts");
	}

	@Test
	void aLivingMemberMissingFromItsAccountCountsWhole() {
		try (var identity = new SharedIdentity()) {
			when(identity.provider.characters(any())).thenReturn(Optional.of(List.of()));
			String member = character(identity, UUID.randomUUID(), CharacterStatus.ALIVE, 0);
			assertEquals(1.0, MemberShare.shareOf(member), 1e-9);
		}
	}

	@Test
	void onlyMembersWithAShareCountTowardPlaytime() {
		try (var identity = new SharedIdentity()) {
			UUID account = UUID.randomUUID();
			String main = character(identity, account, CharacterStatus.ALIVE, 50 * HOUR);
			String alt = character(identity, account, CharacterStatus.ALIVE, 0);
			String friend = character(identity, UUID.randomUUID(), CharacterStatus.ALIVE, 0);
			RecentPlaytime.credit(main, HOUR);

			MemberShare.Headcount headcount = MemberShare.count(List.of(main, alt, friend));
			assertEquals(2.0, headcount.heads(), 1e-9);
			assertEquals(List.of(main, friend), headcount.counted());
			assertEquals(List.of(), MemberShare.count(List.of()).counted());
			assertEquals(List.of(), MemberShare.count(null).counted());
			assertEquals(List.of(friend), MemberShare.count(java.util.Arrays.asList(null, friend)).counted());
		}
	}
}
