package net.tfminecraft.simplefactions.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import net.tfminecraft.simplefactions.installation.InstallationTransferService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.ProvinceHandler;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;

class AdminLandServiceTest {
	private MockedStatic<SimpleFactions> pluginStatic;
	private MockedStatic<FactionManager> factionsStatic;
	private MockedStatic<CivilWarBorderLock> lockStatic;
	private MockedStatic<InstallationTransferService> transferStatic;
	private MapSystem map;
	private Faction village;
	private ProvinceHandler villageLand;
	private final List<Integer> villageProvinces = new ArrayList<>();

	@BeforeEach
	void setUp() {
		ProvinceManager manager = new ProvinceManager();
		manager.start(Map.of(
				5, new Province(5, Terrain.PLAINS.name(), 50),
				6, new Province(6, Terrain.SEA.name(), 0)));
		SimpleFactions plugin = mock(SimpleFactions.class);
		pluginStatic = mockStatic(SimpleFactions.class);
		pluginStatic.when(SimpleFactions::getInstance).thenReturn(plugin);
		when(plugin.getProvinceManager()).thenReturn(manager);
		map = mock(MapSystem.class);
		factionsStatic = mockStatic(FactionManager.class);
		factionsStatic.when(FactionManager::getMap).thenReturn(map);
		lockStatic = mockStatic(CivilWarBorderLock.class);
		transferStatic = mockStatic(InstallationTransferService.class);

		village = mock(Faction.class);
		villageLand = mock(ProvinceHandler.class);
		when(village.getName()).thenReturn("The Village");
		when(village.getProvinceHandler()).thenReturn(villageLand);
		when(village.getProvinces()).thenReturn(villageProvinces);
		doAnswer(invocation -> villageProvinces.add(invocation.getArgument(0))).when(village).addProvince(anyInt());
		when(villageLand.previewLegalProvinces(any())).thenReturn(Set.of(5));
	}

	@AfterEach
	void tearDown() {
		transferStatic.close();
		lockStatic.close();
		factionsStatic.close();
		pluginStatic.close();
	}

	@Test
	void forceClaimRefusesNoProvinceOrSea() {
		assertTrue(AdminLandService.forceClaim(village, 0).get(0).contains("no province"));
		assertTrue(AdminLandService.forceClaim(village, 6).get(0).contains("no province"));
		verify(village, never()).addProvince(anyInt());
	}

	@Test
	void forceClaimRefusesAProvinceTheRealmAlreadyOwns() {
		when(village.ownsProvince(5)).thenReturn(true);

		assertTrue(AdminLandService.forceClaim(village, 5).get(0).contains("already owns"));
	}

	@Test
	void forceClaimRespectsCivilWarLocks() {
		lockStatic.when(() -> CivilWarBorderLock.isLocked(village)).thenReturn(true);
		assertEquals(List.of(CivilWarCopy.CANNOT_CLAIM), AdminLandService.forceClaim(village, 5));

		lockStatic.when(() -> CivilWarBorderLock.isLocked(village)).thenReturn(false);
		Faction owner = mock(Faction.class);
		factionsStatic.when(() -> FactionManager.getByProvince(5)).thenReturn(owner);
		lockStatic.when(() -> CivilWarBorderLock.isLocked(owner)).thenReturn(true);
		assertEquals(List.of(CivilWarCopy.CANNOT_STEAL), AdminLandService.forceClaim(village, 5));
		verify(village, never()).addProvince(anyInt());
	}

	@Test
	void forceClaimWillNotTakeAFactionCapital() {
		Faction owner = mock(Faction.class);
		when(owner.getCapital()).thenReturn(5);
		factionsStatic.when(() -> FactionManager.getByProvince(5)).thenReturn(owner);

		assertTrue(AdminLandService.forceClaim(village, 5).get(0).contains("is the capital"));
		verify(map, never()).unclaim(any(), any(), anyInt());
	}

	@Test
	void forceClaimTakesTheProvinceFromItsOwner() {
		Faction owner = mock(Faction.class);
		when(owner.getName()).thenReturn("Sunsora");
		factionsStatic.when(() -> FactionManager.getByProvince(5)).thenReturn(owner);

		List<String> lines = AdminLandService.forceClaim(village, 5);

		transferStatic.verify(() -> InstallationTransferService.transfer(owner, village, 5));
		verify(map).unclaim(null, owner, 5);
		verify(village).addProvince(5);
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("from Sunsora"));
	}

	@Test
	void forceClaimWarnsWhenTheGiftIsNotConnected() {
		when(villageLand.previewLegalProvinces(any())).thenReturn(Set.of());

		List<String> lines = AdminLandService.forceClaim(village, 5);

		verify(village).addProvince(5);
		assertTrue(lines.get(lines.size() - 1).contains("not connected"));
	}

	@Test
	void forceRemoveCapitalRefusesWithoutACapitalOrOnTheFactionCapital() {
		Guild guild = guild(-1);
		assertTrue(AdminLandService.forceRemoveCapital(guild).get(0).contains("has no capital"));

		Guild main = guild(1);
		when(village.getCapital()).thenReturn(1);
		assertTrue(AdminLandService.forceRemoveCapital(main).get(0).contains("faction capital"));
		verify(main, never()).setCapital(anyInt());
	}

	@Test
	void forceRemoveCapitalRespectsCivilWarLock() {
		Guild guild = guild(5);
		lockStatic.when(() -> CivilWarBorderLock.isLocked(village)).thenReturn(true);

		assertEquals(List.of(CivilWarCopy.CANNOT_UNCLAIM), AdminLandService.forceRemoveCapital(guild));
		verify(guild, never()).setCapital(anyInt());
	}

	@Test
	void forceRemoveCapitalUnseatsTheGuildAndReportsDroppedLand() {
		villageProvinces.addAll(List.of(1, 5, 7));
		Guild guild = guild(5);
		doAnswer(invocation -> villageProvinces.removeAll(List.of(5, 7))).when(villageLand).revalidateClaims();

		List<String> lines = AdminLandService.forceRemoveCapital(guild);

		verify(guild).setCapital(-1);
		assertEquals(2, lines.size());
		assertTrue(lines.get(1).contains("[5, 7]"));
	}

	@Test
	void forceRemoveCapitalWithNothingCutOffReportsOneLine() {
		villageProvinces.addAll(List.of(1, 5));
		Guild guild = guild(5);

		assertEquals(1, AdminLandService.forceRemoveCapital(guild).size());
		verify(villageLand).revalidateClaims();
	}

	private Guild guild(int capital) {
		Guild guild = mock(Guild.class);
		when(guild.getName()).thenReturn("The Crescent Sanctum");
		when(guild.getFaction()).thenReturn(village);
		when(guild.hasCapital()).thenReturn(capital != -1);
		when(guild.getCapital()).thenReturn(capital);
		return guild;
	}
}
