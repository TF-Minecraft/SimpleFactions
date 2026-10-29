package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;

class RecalcLookupsTest {
	private MockedStatic<Bukkit> bukkit;
	private final AtomicBoolean serverThread = new AtomicBoolean(true);
	private final AtomicInteger finds = new AtomicInteger();
	private final Map<Integer, Faction> owners = new HashMap<>();
	private final Map<String, Guild> guilds = new HashMap<>();
	private List<Faction> previousFactions;

	private Faction owner(int province) {
		finds.incrementAndGet();
		return owners.get(province);
	}

	private Guild guild(String id) {
		finds.incrementAndGet();
		return guilds.get(id);
	}

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
		bukkit.when(Bukkit::isPrimaryThread).thenAnswer(call -> serverThread.get());
		previousFactions = new ArrayList<>(FactionManager.factions);
		FactionManager.factions.clear();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(previousFactions);
		bukkit.close();
	}

	@Test
	void aServerRunFindsEachOwnerOnceIncludingWildernessAndANestedRunFindsItsOwn() {
		Faction realm = mock(Faction.class);
		Faction other = mock(Faction.class);
		owners.put(1, realm);
		RecalcLookups.Run outer = RecalcLookups.begin();
		try {
			assertSame(realm, RecalcLookups.owner(1, this::owner));
			assertNull(RecalcLookups.owner(2, this::owner));
			owners.put(1, other);
			owners.put(2, other);
			assertSame(realm, RecalcLookups.owner(1, this::owner));
			assertNull(RecalcLookups.owner(2, this::owner));
			assertEquals(2, finds.get());
			RecalcLookups.Run inner = RecalcLookups.begin();
			try {
				assertSame(other, RecalcLookups.owner(1, this::owner));
				assertSame(other, RecalcLookups.owner(1, this::owner));
			} finally {
				RecalcLookups.end(inner);
			}
			assertSame(realm, RecalcLookups.owner(1, this::owner));
			assertEquals(3, finds.get());
		} finally {
			RecalcLookups.end(outer);
		}
		assertSame(other, RecalcLookups.owner(1, this::owner));
		assertEquals(4, finds.get());
	}

	@Test
	void aServerRunKeepsFoundGuildsButAsksAgainForAMissingOne() {
		Guild merchants = mock(Guild.class);
		guilds.put("merchants", merchants);
		RecalcLookups.Run run = RecalcLookups.begin();
		try {
			assertSame(merchants, RecalcLookups.guild("merchants", this::guild));
			guilds.remove("merchants");
			assertSame(merchants, RecalcLookups.guild("merchants", this::guild));
			assertNull(RecalcLookups.guild("realm", this::guild));
			Guild created = mock(Guild.class);
			guilds.put("realm", created);
			assertSame(created, RecalcLookups.guild("realm", this::guild));
			assertNull(RecalcLookups.guild(null, this::guild));
			assertEquals(4, finds.get());
		} finally {
			RecalcLookups.end(run);
		}
	}

	@Test
	void runsOffTheServerThreadOrWithoutAServerReadLive() {
		Faction realm = mock(Faction.class);
		owners.put(1, realm);
		serverThread.set(false);
		RecalcLookups.Run async = RecalcLookups.begin();
		try {
			RecalcLookups.owner(1, this::owner);
			RecalcLookups.owner(1, this::owner);
			Guild merchants = mock(Guild.class);
			guilds.put("merchants", merchants);
			assertSame(merchants, RecalcLookups.guild("merchants", this::guild));
			guilds.remove("merchants");
			assertNull(RecalcLookups.guild("merchants", this::guild));
		} finally {
			RecalcLookups.end(async);
		}
		assertEquals(4, finds.get());
		bukkit.when(Bukkit::getServer).thenReturn(null);
		RecalcLookups.Run noServer = RecalcLookups.begin();
		try {
			RecalcLookups.owner(1, this::owner);
			RecalcLookups.owner(1, this::owner);
		} finally {
			RecalcLookups.end(noServer);
		}
		assertEquals(6, finds.get());
	}

	@Test
	void titleAndFactionLookupsGoThroughTheRun() {
		Faction realm = mock(Faction.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
		org.mockito.Mockito.when(realm.hasProvince(7)).thenReturn(true);
		Guild merchants = mock(Guild.class);
		org.mockito.Mockito.when(realm.getGuildHandler().findGuild("merchants")).thenReturn(merchants);
		FactionManager.factions.add(realm);
		RecalcLookups.Run run = RecalcLookups.begin();
		try {
			assertSame(realm, TitleManager.getByProvince(7));
			assertSame(merchants, FactionManager.getGuildByString("merchants"));
			FactionManager.factions.clear();
			assertSame(realm, TitleManager.getByProvince(7));
			assertSame(merchants, FactionManager.getGuildByString("merchants"));
		} finally {
			RecalcLookups.end(run);
		}
		assertNull(TitleManager.getByProvince(7));
		assertNull(FactionManager.getGuildByString("merchants"));
	}
}
