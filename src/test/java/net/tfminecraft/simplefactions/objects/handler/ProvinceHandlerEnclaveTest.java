package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;

/** A guild capital cut off from the realm must trace back to the faction capital, not to itself. */
class ProvinceHandlerEnclaveTest {

	private ProvinceManager manager;
	private Faction faction;
	private GuildHandler guilds;
	private MockedStatic<SimpleFactions> pluginStatic;
	private MockedStatic<FactionManager> factionsStatic;

	@BeforeEach
	void setUp() {
		manager = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		pluginStatic = mockStatic(SimpleFactions.class);
		pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
		when(plugin.getProvinceManager()).thenReturn(manager);
		factionsStatic = mockStatic(FactionManager.class);
		factionsStatic.when(FactionManager::getMap).thenReturn(mock(MapSystem.class));
		faction = mock(Faction.class);
		guilds = new GuildHandler(faction);
		when(faction.getGuildHandler()).thenReturn(guilds);
		when(faction.getSettlementHandler()).thenReturn(mock(SettlementHandler.class));
		when(faction.getInstallationHandler()).thenReturn(mock(InstallationHandler.class));
		when(faction.getRGB()).thenReturn("1,2,3");
	}

	@AfterEach
	void tearDown() {
		factionsStatic.close();
		pluginStatic.close();
	}

	@Test
	void enclaveTouchingItsOwnLakeLosesItsCapitalAndLand() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.PLAINS, 5, Terrain.PLAINS,
				6, Terrain.PLAINS, 7, Terrain.WATER));
		link(1, 2); link(5, 6); link(5, 7); link(6, 7);
		Guild guild = guild("cicatrix", 5);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 2, 5, 6));

		handler.revalidateClaims();

		assertEquals(List.of(1, 2), handler.getProvinces());
		verify(guild).setCapital(-1);
	}

	@Test
	void colonyReachedBySeaFromTheRealmKeepsItsCapital() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.SEA,
				4, Terrain.PLAINS, 5, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4); link(4, 5);
		Guild guild = guild("colony", 4);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 4, 5));

		handler.revalidateClaims();

		assertEquals(List.of(1, 4, 5), handler.getProvinces());
		verify(guild, never()).setCapital(anyInt());
	}

	@Test
	void aColonyCanAnchorAnotherColonyAcrossADifferentSea() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.SEA, 4, Terrain.PLAINS,
				6, Terrain.SEA, 7, Terrain.SEA, 8, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4); link(4, 6); link(6, 7); link(7, 8);
		// Listed far colony first so it only becomes legal once the near one is traced.
		Guild far = guild("far", 8);
		Guild near = guild("near", 4);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 4, 8));

		handler.revalidateClaims();

		assertEquals(List.of(1, 4, 8), handler.getProvinces());
		verify(far, never()).setCapital(anyInt());
		verify(near, never()).setCapital(anyInt());
	}

	@Test
	void twoEnclavesOnOneLakeCannotVouchForEachOther() {
		load(Map.of(1, Terrain.PLAINS, 5, Terrain.PLAINS, 7, Terrain.WATER, 8, Terrain.PLAINS));
		link(5, 7); link(7, 8);
		Guild west = guild("west", 5);
		Guild east = guild("east", 8);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 5, 8));

		handler.revalidateClaims();

		assertEquals(List.of(1), handler.getProvinces());
		verify(west).setCapital(-1);
		verify(east).setCapital(-1);
	}

	@Test
	void seaClaimsCannotSetSailFromAnEnclave() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.PLAINS, 5, Terrain.PLAINS,
				7, Terrain.SEA, 9, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(5, 7); link(7, 9);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 5));

		assertFalse(handler.canClaim(9, true));
		assertTrue(handler.getClaimDeniedReason(9, true).contains("connected"));
		assertTrue(handler.canClaim(3, true));
	}

	@Test
	void realmWithoutACapitalKeepsSailingFromWhatItOwns() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.SEA, 5, Terrain.PLAINS,
				10, Terrain.SEA, 11, Terrain.PLAINS));
		link(1, 2); link(2, 10); link(2, 3); link(3, 5); link(10, 11);
		Guild guild = guild("harbour", 5);
		ProvinceHandler handler = new ProvinceHandler(faction, -1, List.of(1, 5));

		assertTrue(handler.canClaim(11, true));
		handler.revalidateClaims();

		assertEquals(List.of(5), handler.getProvinces());
		verify(guild, never()).setCapital(anyInt());
	}

	@Test
	void anInlandGuildCapitalNeedsALandLinkToTheCapital() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.PLAINS, 4, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4);
		Guild guild = guild("inland", 4);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 3, 4));

		handler.revalidateClaims();

		assertEquals(List.of(1, 3, 4), handler.getProvinces());
		verify(guild).setCapital(-1);
	}

	@Test
	void previewLeavesOutAnEnclaveCapital() {
		load(Map.of(1, Terrain.PLAINS, 5, Terrain.PLAINS, 7, Terrain.WATER));
		link(5, 7);
		guild("cicatrix", 5);
		ProvinceHandler handler = new ProvinceHandler(faction, 1, List.of(1, 5));

		assertEquals(Set.of(1), handler.previewLegalProvinces(List.of(1, 5)));
	}

	private Guild guild(String id, int capital) {
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn(id);
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(capital);
		guilds.addGuild(guild);
		return guild;
	}

	private void load(Map<Integer, Terrain> terrain) {
		Map<Integer, Province> provinces = new HashMap<>();
		terrain.forEach((id, type) -> provinces.put(id, new Province(id, type.name(), 50)));
		manager.start(provinces);
	}

	private void link(int a, int b) {
		manager.get(a).addNeighbour(b);
		manager.get(b).addNeighbour(a);
	}
}
