package net.tfminecraft.simplefactions.objects.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

class ProvinceHandlerUnclaimTest {

	private ProvinceManager manager;
	private Faction faction;
	private GuildHandler guilds;
	private MapSystem map;
	private MockedStatic<SimpleFactions> pluginStatic;
	private MockedStatic<FactionManager> factionsStatic;

	@BeforeEach
	void setUp() {
		manager = new ProvinceManager();
		SimpleFactions plugin = mock(SimpleFactions.class);
		pluginStatic = mockStatic(SimpleFactions.class);
		pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
		when(plugin.getProvinceManager()).thenReturn(manager);
		map = mock(MapSystem.class);
		factionsStatic = mockStatic(FactionManager.class);
		factionsStatic.when(FactionManager::getMap).thenReturn(map);
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

	@ParameterizedTest
	@EnumSource(value = Terrain.class, names = {"WATER", "SEA"})
	void unclaimUnrelatedProvinceKeepsCoastalHoldingAndItsInlandProvinces(Terrain terrain) {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.PLAINS, 3, terrain,
				4, Terrain.PLAINS, 5, Terrain.PLAINS, 6, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4); link(4, 5); link(1, 6);
		ProvinceHandler handler = handler(1, 1, 2, 6);
		assertTrue(handler.canClaim(4, false));
		handler.addProvince(4);
		handler.addProvince(5);
		clearInvocations(map, faction);

		new MapSystem().unclaim(mock(Player.class), faction, 6);

		assertEquals(List.of(1, 2, 4, 5), handler.getProvinces());
		verify(faction.getSettlementHandler(), never()).onProvinceLost(4);
		verify(faction.getInstallationHandler(), never()).onProvinceLost(5);
		verify(map).enqueue("nation", "1,2,3");
		verify(faction).updateTier();
	}

	@Test
	void waterBridgesCanContinueFromAnOverseasHolding() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.PLAINS,
				4, Terrain.WATER, 5, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4); link(4, 5);
		ProvinceHandler handler = handler(1, 1, 3, 5);

		handler.revalidateClaims();

		assertEquals(List.of(1, 3, 5), handler.getProvinces());
	}

	@Test
	void losingTheOnlyOwnedLandBridgeStillRemovesDisconnectedTerritory() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.PLAINS, 3, Terrain.PLAINS));
		link(1, 2); link(2, 3);
		ProvinceHandler handler = handler(1, 1, 2, 3);

		new MapSystem().unclaim(mock(Player.class), faction, 2);

		assertEquals(List.of(1), handler.getProvinces());
		verify(faction.getSettlementHandler()).onProvinceLost(3);
		verify(faction.getInstallationHandler()).onProvinceLost(3);
	}

	@Test
	void seaCrossingsCannotChainFromAnOverseasHolding() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.PLAINS,
				4, Terrain.SEA, 5, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4); link(4, 5);
		ProvinceHandler handler = handler(1, 1, 3, 5);

		handler.revalidateClaims();

		assertEquals(List.of(1, 3), handler.getProvinces());
	}

	@Test
	void twoSeaTilesCannotConnectAnOrdinaryHolding() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.SEA, 4, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4);
		ProvinceHandler handler = handler(1, 1, 4);

		handler.revalidateClaims();

		assertEquals(List.of(1), handler.getProvinces());
	}

	@Test
	void losingTheCapitalConnectedCoastRemovesItsOverseasHolding() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.PLAINS, 3, Terrain.SEA, 4, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4);
		ProvinceHandler handler = handler(1, 1, 2, 4);

		new MapSystem().unclaim(mock(Player.class), faction, 2);

		assertEquals(List.of(1), handler.getProvinces());
	}

	@Test
	void unclaimedGuildCapitalCannotAnchorItsFormerNeighbours() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.SEA, 3, Terrain.PLAINS, 4, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn("overseas");
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(3);
		guilds.addGuild(guild);
		ProvinceHandler handler = handler(1, 1, 3, 4);

		new MapSystem().unclaim(mock(Player.class), faction, 3);

		assertEquals(List.of(1), handler.getProvinces());
		verify(guild).setCapital(-1);
	}

	@Test
	void capitalMovePreviewUsesTheProposedCapitalCoast() {
		load(Map.of(1, Terrain.PLAINS, 2, Terrain.PLAINS, 3, Terrain.SEA,
				4, Terrain.PLAINS, 5, Terrain.PLAINS));
		link(1, 2); link(2, 3); link(3, 4); link(4, 5);
		ProvinceHandler handler = handler(1, 1, 2, 4, 5);

		assertEquals(List.of(), handler.previewProvincesLostIfCapitalMoved(5));
	}

	private ProvinceHandler handler(int capital, Integer... owned) {
		ProvinceHandler handler = new ProvinceHandler(faction, capital, List.of(owned));
		when(faction.getProvinceHandler()).thenReturn(handler);
		when(faction.getProvinces()).thenAnswer(invocation -> handler.getProvinces());
		when(faction.getCapital()).thenReturn(capital);
		doAnswer(invocation -> {
			handler.removeProvince(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(faction).removeProvince(anyInt(), anyBoolean());
		return handler;
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
