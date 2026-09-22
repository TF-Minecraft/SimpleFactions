package me.Plugins.SimpleFactions.War.civilwar.wartime;



import me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarMemberMove;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarUntangleService;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarWartimeVassalEnd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.GuildType;
import me.Plugins.SimpleFactions.Loaders.GuildLoader;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Map.MapSystem;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class CivilWarUntangleServiceTest {

	@Test
	void restore_mergesTempRebelsIntoHost() {
		Faction host = mock(Faction.class);
		Faction rebels = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		when(rebels.getId()).thenReturn("rebels");

		Regiment hostProfessional = regiment("professional", 5);
		Regiment rebelProfessional = regiment("professional", 5);
		attachMilitary(host, List.of(hostProfessional));
		attachMilitary(rebels, List.of(rebelProfessional));

		InstallationHandler hostInstalls = new InstallationHandler(host);
		InstallationHandler rebelInstalls = new InstallationHandler(rebels);
		when(host.getInstallationHandler()).thenReturn(hostInstalls);
		when(rebels.getInstallationHandler()).thenReturn(rebelInstalls);
		rebelInstalls.acceptTransferred(new Installation("port-1", "Harbour", InstallationKind.PORT, 2, 0, 0, 1L));

		SettlementHandler hostSettlements = new SettlementHandler(host);
		SettlementHandler rebelSettlements = new SettlementHandler(rebels);
		when(host.getSettlementHandler()).thenReturn(hostSettlements);
		when(rebels.getSettlementHandler()).thenReturn(rebelSettlements);
		rebelSettlements.acceptTransferred(new Settlement("city", "City", 2, 0, 0));

		when(rebels.getProvinces()).thenReturn(new ArrayList<>(List.of(2)));
		doAnswer(invocation -> {
			int province = invocation.getArgument(0);
			rebels.getInstallationHandler().onProvinceLost(province);
			rebels.getSettlementHandler().onProvinceLost(province);
			return null;
		}).when(rebels).removeProvince(anyInt(), anyBoolean());

		Guild relocated = mock(Guild.class);
		when(relocated.isBase()).thenReturn(false);
		when(relocated.hasCapital()).thenReturn(true);
		when(relocated.getCapital()).thenReturn(2);
		Guild base = mock(Guild.class);
		when(base.isBase()).thenReturn(true);
		when(base.hasCapital()).thenReturn(false);
		when(base.getOwnName()).thenReturn("Lantan Rebels");
		GuildHandler rebelGuilds = mock(GuildHandler.class);
		when(rebelGuilds.getGuilds()).thenReturn(new ArrayList<>(List.of(relocated, base)));
		when(rebels.getGuildHandler()).thenReturn(rebelGuilds);
		when(rebels.getOrCreateMainGuild()).thenReturn(base);
		GuildHandler hostGuilds = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(hostGuilds);

		GuildType defaultType = mock(GuildType.class);
		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		RelationType vassalType = mock(RelationType.class);
		when(vassalType.isVassalage()).thenReturn(true);
		Title movedTitle = mock(Title.class);
		when(movedTitle.getId()).thenReturn("county_gaba");
		when(rebels.hasTitle(movedTitle)).thenReturn(true);
		when(host.getId()).thenReturn("host");
		when(rebels.getId()).thenReturn("rebels");

		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		snapshot.setTempRebelFactionId("rebels");
		Map<Integer, String> transferred = new LinkedHashMap<>();
		transferred.put(2, "host");
		snapshot.setTransferredProvinces(transferred);
		snapshot.setHostOldCapitalId(2);
		snapshot.setRebelMainGuildOwnName("Cape Wells");
		snapshot.setMovedTitleId("county_gaba");
		snapshot.setWartimeVassalEnds(List.of(new CivilWarWartimeVassalEnd("vassal", "host", "vassal_type")));

		War war = new War(1, rebels, host);
		war.setCivilWarSnapshot(snapshot);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<RelationLoader> relationLoader = mockStatic(RelationLoader.class);
				MockedStatic<TitleLoader> titles = mockStatic(TitleLoader.class);
				MockedStatic<GuildLoader> guildLoader = mockStatic(GuildLoader.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayerExact(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
			factions.when(FactionManager::getMap).thenReturn(mock(MapSystem.class));
			factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
			factions.when(() -> FactionManager.getByString("rebels")).thenReturn(rebels);
			factions.when(() -> FactionManager.getByString("vassal")).thenReturn(vassal);
			titles.when(() -> TitleLoader.getById("county_gaba")).thenReturn(movedTitle);
			guildLoader.when(GuildLoader::getDefaultType).thenReturn(defaultType);
			doAnswer(invocation -> {
				when(base.isBase()).thenReturn(false);
				return null;
			}).when(base).convert(defaultType);
			relationLoader.when(() -> RelationLoader.getType("vassal_type")).thenReturn(vassalType);

			CivilWarUntangleService.restore(war);

			assertEquals(10, hostProfessional.getCurrentSlots());
			assertEquals(0, rebelProfessional.getCurrentSlots());
			assertNull(rebelInstalls.getById("port-1"));
			assertEquals("port-1", hostInstalls.getById("port-1").getId());
			assertNull(rebelSettlements.getById("city"));
			assertEquals("city", hostSettlements.getById("city").getId());
			verify(host).addProvince(2);
			verify(host).setCapital(2, true, false);
			verify(relocated).relocate(host, 2);
			verify(base).relocate(host, -1);
			verify(base).setName("Cape Wells");
			verify(rebels).stripTitle(movedTitle);
			verify(host).addTitle(movedTitle);
			relations.verify(() -> RelationManager.setRelationForced(vassalType, vassal, host));
			factions.verify(() -> FactionManager.deleteFaction(rebels));
		}
	}

	@Test
	void restore_pureVassal_doesNotTouchHostArmy() {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		Regiment hostProfessional = regiment("professional", 10);
		attachMilitary(host, List.of(hostProfessional));

		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		RelationType vassalType = mock(RelationType.class);
		when(vassalType.isVassalage()).thenReturn(true);

		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		snapshot.setWartimeVassalEnds(List.of(new CivilWarWartimeVassalEnd("vassal", "host", "vassal_type")));

		War war = new War(2, vassal, host);
		war.setCivilWarSnapshot(snapshot);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<RelationLoader> relationLoader = mockStatic(RelationLoader.class)) {
			factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
			factions.when(() -> FactionManager.getByString("vassal")).thenReturn(vassal);
			relationLoader.when(() -> RelationLoader.getType("vassal_type")).thenReturn(vassalType);

			CivilWarUntangleService.restore(war);

			assertEquals(10, hostProfessional.getCurrentSlots());
			verify(host, never()).addProvince(anyInt());
			relations.verify(() -> RelationManager.setRelationForced(vassalType, vassal, host));
			factions.verify(() -> FactionManager.deleteFaction(any()), never());
		}
	}

	@Test
	void restore_mapsOverlordLinkToVassalageType() {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		RelationType overlordType = mock(RelationType.class);
		RelationType vassalType = mock(RelationType.class);
		when(overlordType.isOverlord()).thenReturn(true);
		when(overlordType.isVassalage()).thenReturn(false);
		when(overlordType.getLink()).thenReturn(vassalType);
		when(vassalType.isVassalage()).thenReturn(true);

		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		snapshot.setWartimeVassalEnds(List.of(new CivilWarWartimeVassalEnd("vassal", "host", "overlord")));

		War war = new War(3, vassal, host);
		war.setCivilWarSnapshot(snapshot);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<RelationLoader> relationLoader = mockStatic(RelationLoader.class)) {
			factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
			factions.when(() -> FactionManager.getByString("vassal")).thenReturn(vassal);
			relationLoader.when(() -> RelationLoader.getType("overlord")).thenReturn(overlordType);

			CivilWarUntangleService.restore(war);

			relations.verify(() -> RelationManager.setRelationForced(vassalType, vassal, host));
		}
	}

	@Test
	void snapshotVassalageTypeId_usesOverlordVassalageRow() {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		RelationType vassalage = mock(RelationType.class);
		when(vassalage.isVassalage()).thenReturn(true);
		when(vassalage.getId()).thenReturn("vassal_type");
		RelationType overlordLink = mock(RelationType.class);
		when(overlordLink.isOverlord()).thenReturn(true);
		when(overlordLink.isVassalage()).thenReturn(false);
		Relation fromOverlord = mock(Relation.class);
		when(fromOverlord.getType()).thenReturn(vassalage);
		Relation fromVassal = mock(Relation.class);
		when(fromVassal.getType()).thenReturn(overlordLink);
		when(host.getRelation("vassal")).thenReturn(fromOverlord);
		when(vassal.getRelation("host")).thenReturn(fromVassal);

		assertEquals("vassal_type", CivilWarUntangleService.snapshotVassalageTypeId(vassal, host));
	}

	@Test
	void restore_putsMemberBackOnOriginGuild() {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		Guild origin = mock(Guild.class);
		when(origin.isBase()).thenReturn(false);
		GuildHandler handler = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(handler);
		when(handler.getGuild("guild-a")).thenReturn(origin);

		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		snapshot.setMemberMoves(List.of(new CivilWarMemberMove("dummy_11", "guild-a", true)));

		War war = new War(4, mock(Faction.class), host);
		war.setCivilWarSnapshot(snapshot);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
			CivilWarUntangleService.restore(war, WarEndReason.DEFENDER_VICTORY);
			verify(handler).forceKick("dummy_11");
			verify(origin).addMember("dummy_11");
			verify(origin).setLeader("dummy_11");
		}
	}

	@Test
	void restore_attackerWin_movesWantedLeaderToMainGuild() {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		Guild origin = mock(Guild.class);
		when(origin.isBase()).thenReturn(false);
		Guild main = mock(Guild.class);
		GuildHandler handler = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(handler);
		when(host.getOrCreateMainGuild()).thenReturn(main);
		when(handler.getGuild("guild-a")).thenReturn(origin);

		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		snapshot.setWantedLeaderName("dummy_11");
		snapshot.setMemberMoves(List.of(new CivilWarMemberMove("dummy_11", "guild-a", true)));

		War war = new War(5, mock(Faction.class), host);
		war.setCivilWarSnapshot(snapshot);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("host")).thenReturn(host);
			CivilWarUntangleService.restore(war, WarEndReason.ATTACKER_VICTORY);
			verify(handler).forceKick("dummy_11");
			verify(main).addMember("dummy_11");
			verify(origin, never()).addMember("dummy_11");
			verify(origin, never()).setLeader("dummy_11");
		}
	}

	private static void attachMilitary(Faction faction, List<Regiment> regiments) {
		Military military = mock(Military.class);
		when(military.getRegiments()).thenReturn(regiments);
		when(military.getRegiment(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
			String wanted = invocation.getArgument(0);
			for (Regiment regiment : regiments) {
				if (regiment.getId().equalsIgnoreCase(wanted)) {
					return regiment;
				}
			}
			return null;
		});
		when(faction.getMilitary()).thenReturn(military);
	}

	private static Regiment regiment(String id, int slots) {
		Regiment regiment = mock(Regiment.class);
		when(regiment.getId()).thenReturn(id);
		when(regiment.isLevy()).thenReturn(false);
		AtomicInteger current = new AtomicInteger(slots);
		when(regiment.getCurrentSlots()).thenAnswer(invocation -> current.get());
		doAnswer(invocation -> {
			current.set(invocation.getArgument(0));
			return null;
		}).when(regiment).setCurrentSlots(anyInt());
		return regiment;
	}
}
