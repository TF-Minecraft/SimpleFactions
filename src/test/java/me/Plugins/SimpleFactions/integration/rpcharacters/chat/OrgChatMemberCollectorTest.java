package me.Plugins.SimpleFactions.integration.rpcharacters.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class OrgChatMemberCollectorTest {

	@Test
	void emptyMemberListReturnsEmpty() {
		List<Player> online = OrgChatMemberCollector.onlinePlayersNamed(List.of(), name -> null);
		assertTrue(online.isEmpty());
	}

	@Test
	void offlineNamesAreSkipped() {
		List<Player> online = OrgChatMemberCollector.onlinePlayersNamed(
				List.of("Alice", "Bob"),
				name -> null);
		assertTrue(online.isEmpty());
	}

	@Test
	void onlineMatchingNamesIncludedCaseInsensitivelyOnce() {
		Player alice = onlinePlayer();
		Map<String, Player> lookup = new HashMap<>();
		lookup.put("alice", alice);

		List<Player> online = OrgChatMemberCollector.onlinePlayersNamed(
				List.of("Alice", "alice", "Bob"),
				name -> lookup.get(name.toLowerCase(Locale.ROOT)));

		assertEquals(1, online.size());
		assertEquals(alice, online.get(0));
	}

	@Test
	void duplicateRosterNamesDoNotDuplicatePlayers() {
		Player bob = onlinePlayer();
		Function<String, Player> lookup = name -> "Bob".equalsIgnoreCase(name) ? bob : null;

		List<Player> online = OrgChatMemberCollector.onlinePlayersNamed(
				List.of("Bob", "bob", "BOB"),
				lookup);

		assertEquals(1, online.size());
		assertEquals(bob, online.get(0));
	}

	private static Player onlinePlayer() {
		Player player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		return player;
	}
}
