package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;

class GuildIdIndexTest {
	/** U+01C5 and U+01C6 are one letter in title and lower case; U+10428 and U+10400 a pair outside the BMP. */
	private static final List<String> ASKED = Arrays.asList("merchants", "MERCHANTS", "Smiths", "smiths", "sMiThS",
			"bakers", "BAKERS", "Bakers", "masons", "Masons", "tanners", "ǅuro", "ǆURO",
			"𐐨lder", "𐐀LDER", "merchant", "merchants ", "", " ", "nobody");

	private MockedStatic<Bukkit> bukkit;
	private final AtomicBoolean serverThread = new AtomicBoolean(true);
	private final List<Faction> factions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
		bukkit.when(Bukkit::isPrimaryThread).thenAnswer(call -> serverThread.get());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	void anIndexedLookupAnswersWhatTheWalkAnswers() {
		Guild smiths = guild("Smiths");
		Guild shoutedSmiths = guild("SMITHS");
		Faction north = realm(guild("merchants"), smiths, guild("bakers"), guild("BAKERS"), guild("ǅuro"),
				guild(""));
		Faction south = realm(shoutedSmiths, guild("Masons"), guild("𐐨lder"));
		factions.add(north);
		factions.add(realm());
		factions.add(south);
		assertSameAsTheWalk();
		// Asked again, the same index answers.
		assertSameAsTheWalk();
		assertSame(smiths, GuildIdIndex.find(factions, "smiths"));
		assertNull(GuildIdIndex.find(factions, null));

		// The realm listed first wins a tie, so the order of the realms is part of the answer.
		Collections.reverse(factions);
		assertSame(shoutedSmiths, GuildIdIndex.find(factions, "smiths"));
		assertSameAsTheWalk();
		// The same realm listed twice changes nothing.
		factions.add(south);
		assertSameAsTheWalk();
	}

	@Test
	void theIndexFollowsEveryGuildAndRealmThatComesOrGoes() {
		Guild merchants = guild("merchants");
		Faction north = realm(merchants);
		Faction south = realm(guild("Masons"));
		factions.add(north);
		assertSame(merchants, GuildIdIndex.find(factions, "Merchants"));
		assertNull(GuildIdIndex.find(factions, "tanners"));

		Guild tanners = guild("Tanners");
		long before = GuildHandler.changes();
		north.getGuildHandler().addGuild(tanners);
		assertNotEquals(before, GuildHandler.changes());
		assertSame(tanners, GuildIdIndex.find(factions, "tanners"));

		// A guild moves realm: out of one handler, into another that is not listed yet.
		before = GuildHandler.changes();
		north.getGuildHandler().removeGuild("Tanners", false, false);
		assertNotEquals(before, GuildHandler.changes());
		assertNull(GuildIdIndex.find(factions, "tanners"));
		south.getGuildHandler().addGuild(tanners);
		assertNull(GuildIdIndex.find(factions, "tanners"));
		factions.add(south);
		assertSame(tanners, GuildIdIndex.find(factions, "tanners"));
		assertSameAsTheWalk();

		// A guild replaced under the same id.
		Guild refounded = guild("merchants");
		north.getGuildHandler().addGuild(refounded);
		assertSame(refounded, GuildIdIndex.find(factions, "merchants"));

		// Journal replay swaps a realm's whole membership.
		Guild bakers = guild("bakers");
		before = GuildHandler.changes();
		north.getGuildHandler().restoreTopology(List.of(bakers));
		assertNotEquals(before, GuildHandler.changes());
		assertNull(GuildIdIndex.find(factions, "merchants"));
		assertSame(bakers, GuildIdIndex.find(factions, "BAKERS"));
		assertSameAsTheWalk();

		// A realm is deleted, then another takes its place in the list.
		factions.remove(south);
		assertNull(GuildIdIndex.find(factions, "tanners"));
		assertNull(GuildIdIndex.find(factions, "masons"));
		Guild weavers = guild("weavers");
		factions.set(0, realm(weavers));
		assertNull(GuildIdIndex.find(factions, "bakers"));
		assertSame(weavers, GuildIdIndex.find(factions, "Weavers"));

		// A realm given a new handler.
		Guild miners = guild("miners");
		GuildHandler replaced = new GuildHandler(factions.get(0));
		replaced.addGuild(miners);
		assertSame(weavers, GuildIdIndex.find(factions, "weavers"));
		when(factions.get(0).getGuildHandler()).thenReturn(replaced);
		assertNull(GuildIdIndex.find(factions, "weavers"));
		assertSame(miners, GuildIdIndex.find(factions, "MINERS"));

		factions.clear();
		assertNull(GuildIdIndex.find(factions, "miners"));
	}

	@Test
	void whatOnlyTheWalkCanAnswerForGoesToTheWalk() {
		Guild merchants = guild("merchants");
		Faction north = realm(merchants);
		factions.add(north);
		assertSame(merchants, GuildIdIndex.find(factions, "merchants"));

		// A realm without a handler, or no realm at all: the walk finds what comes before it and then fails.
		factions.add(mock(Faction.class));
		assertSame(merchants, GuildIdIndex.find(factions, "merchants"));
		assertThrows(NullPointerException.class, () -> GuildIdIndex.find(factions, "nobody"));
		factions.set(1, null);
		assertSame(merchants, GuildIdIndex.find(factions, "merchants"));
		assertThrows(NullPointerException.class, () -> GuildIdIndex.find(factions, "nobody"));
		// The index is in use again, and a realm that goes missing after it was built is still noticed.
		factions.set(1, realm());
		assertNull(GuildIdIndex.find(factions, "nobody"));
		factions.set(1, null);
		assertSame(merchants, GuildIdIndex.find(factions, "merchants"));
		assertThrows(NullPointerException.class, () -> GuildIdIndex.find(factions, "nobody"));
		factions.remove(1);

		// A guild without an id, in a realm listed after the match.
		factions.add(realm(guild(null)));
		assertSame(merchants, GuildIdIndex.find(factions, "merchants"));
		assertThrows(NullPointerException.class, () -> GuildIdIndex.find(factions, "nobody"));
		factions.remove(1);

		// An id that no longer matches the guild found under it.
		assertSame(merchants, GuildIdIndex.find(factions, "merchants"));
		when(merchants.getId()).thenReturn("renamed");
		assertNull(GuildIdIndex.find(factions, "merchants"));
		assertNull(walk("merchants"));
	}

	@Test
	void offTheServerThreadOrWithoutAServerTheWalkAnswers() {
		Guild merchants = guild("merchants");
		factions.add(realm(merchants));
		assertSame(merchants, GuildIdIndex.find(factions, "merchants"));
		// Only the walk asks a handler to find the guild; the index reads the handler's guilds, here none.
		Guild other = guild("merchants");
		GuildHandler handler = mock(GuildHandler.class);
		when(handler.findGuild("merchants")).thenReturn(other);
		Faction preview = mock(Faction.class);
		when(preview.getGuildHandler()).thenReturn(handler);
		assertNotSame(merchants, other);
		assertNull(GuildIdIndex.find(List.of(preview), "merchants"));

		serverThread.set(false);
		assertSame(merchants, GuildIdIndex.find(factions, "Merchants"));
		assertSame(other, GuildIdIndex.find(List.of(preview), "merchants"));
		bukkit.when(Bukkit::getServer).thenReturn(null);
		serverThread.set(true);
		assertSame(merchants, GuildIdIndex.find(factions, "Merchants"));
		assertSame(other, GuildIdIndex.find(List.of(preview), "merchants"));
	}

	@Test
	void theIndexOrdersIdsTheWayEqualsIgnoreCaseComparesThem() {
		// Every code point against its other cases and its neighbour: the two must agree on what is the same id.
		for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
			String one = "a" + Character.toString(codePoint) + "z";
			for (int other : new int[] {Character.toUpperCase(codePoint), Character.toLowerCase(codePoint),
					Character.toTitleCase(codePoint), Math.min(codePoint + 1, Character.MAX_CODE_POINT)}) {
				String two = "A" + Character.toString(other) + "Z";
				if (one.equalsIgnoreCase(two) != (String.CASE_INSENSITIVE_ORDER.compare(one, two) == 0)) {
					throw new AssertionError(Integer.toHexString(codePoint) + " and " + Integer.toHexString(other));
				}
			}
		}
	}

	private void assertSameAsTheWalk() {
		for (String id : ASKED) assertSame(walk(id), GuildIdIndex.find(factions, id), id);
	}

	/** The lookup as it was before the index: realm by realm, each realm's guilds in listed order. */
	private Guild walk(String id) {
		for (Faction faction : factions) {
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				if (guild.getId().equalsIgnoreCase(id)) return guild;
			}
		}
		return null;
	}

	private static Faction realm(Guild... guilds) {
		Faction faction = mock(Faction.class);
		GuildHandler handler = new GuildHandler(faction);
		for (Guild guild : guilds) handler.addGuild(guild);
		when(faction.getGuildHandler()).thenReturn(handler);
		return faction;
	}

	private static Guild guild(String id) {
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn(id);
		return guild;
	}
}
