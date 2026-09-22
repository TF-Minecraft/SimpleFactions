package me.Plugins.SimpleFactions.War.civilwar;


import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarSeaPortGate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Map.MapSystem;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.enums.Member;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.Pool;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CivilWarStartServiceTest {

	@Test
	void unmappableFirstCause_doesNotFreeze() {
		Movement movement = baseMovement(Action.INDEPENDENCE, Member.GUILD_LEADER);
		when(movement.isFrozen()).thenReturn(false);

		String error = CivilWarStartService.start(movement);

		assertEquals(CivilWarCopy.UNMAPPABLE_CAUSE, error);
		verify(movement, never()).setFrozen(true);
	}

	@Test
	void whitePeaceCause_isNotUnmappable() {
		Movement movement = baseMovement(Action.WHITE_PEACE, Member.GUILD_LEADER);
		String error = CivilWarStartService.start(movement);
		assertNotEquals(CivilWarCopy.UNMAPPABLE_CAUSE, error);
	}

	@Test
	void surrenderCause_isNotUnmappable() {
		Movement movement = baseMovement(Action.SURRENDER, Member.GUILD_LEADER);
		String error = CivilWarStartService.start(movement);
		assertNotEquals(CivilWarCopy.UNMAPPABLE_CAUSE, error);
	}

	@Test
	void navyRefuse_doesNotFreeze() {
		Movement movement = baseMovement(Action.CHANGE_LEADER, Member.MEMBER);
		Faction host = movement.getFaction();
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(1, 2)));
		when(host.getCapital()).thenReturn(1);
		GuildHandler handler = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(handler);
		when(handler.getGuilds()).thenReturn(List.of());
		when(movement.getAllSupportingGuilds()).thenReturn(List.of());
		when(movement.getAllSupportingFactions()).thenReturn(List.of());

		try (MockedStatic<CivilWarSeaPortGate> gate = mockStatic(CivilWarSeaPortGate.class)) {
			gate.when(() -> CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(eq(host), any()))
					.thenReturn(false);
			String error = CivilWarStartService.start(movement);
			assertEquals(CivilWarCopy.NO_PORT_ON_SEA, error);
			verify(movement, never()).setFrozen(true);
		}
	}

	@Test
	void hostGuildRebels_createsTempFactionAndDoesNotEndMovement() {
		Movement movement = baseMovement(Action.CHANGE_LEADER, Member.GUILD_LEADER);
		Faction host = movement.getFaction();
		Government government = host.getGovernment();
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(1, 2)));
		when(host.getCapital()).thenReturn(1);
		GuildHandler hostGuilds = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(hostGuilds);
		Guild rebelGuild = mock(Guild.class);
		when(rebelGuild.getId()).thenReturn("guild-a");
		when(rebelGuild.isBase()).thenReturn(false);
		when(rebelGuild.getFaction()).thenReturn(host);
		when(rebelGuild.isMember("Alice")).thenReturn(true);
		when(rebelGuild.hasCapital()).thenReturn(false);
		when(hostGuilds.getGuilds()).thenReturn(List.of(rebelGuild));
		when(movement.getAllSupportingGuilds()).thenReturn(List.of(rebelGuild));
		when(movement.getAllSupportingFactions()).thenReturn(List.of());
		when(movement.getLeader()).thenReturn("Alice");
		when(movement.getId()).thenReturn("mov-1");
		when(movement.getSupporters()).thenReturn(new Pool());
		Cause cause = movement.getCauses().get(0);
		Proposal proposal = cause.getProposal();
		when(proposal.hasTarget()).thenReturn(false);

		Faction rebels = mock(Faction.class);
		when(rebels.getId()).thenReturn("host_rebels");
		InstallationHandler hostHandler = new InstallationHandler(host);
		InstallationHandler rebelHandler = new InstallationHandler(rebels);
		when(host.getInstallationHandler()).thenReturn(hostHandler);
		when(rebels.getInstallationHandler()).thenReturn(rebelHandler);
		when(rebels.getOrCreateMainGuild()).thenReturn(mock(Guild.class));
		stubRebelVassalage(rebels);
		hostHandler.acceptTransferred(new Installation("port-1", "Harbour", InstallationKind.PORT, 2, 0, 0, 1L));

		War war = mock(War.class);
		when(war.getMovementId()).thenReturn("mov-1");

		try (MockedStatic<CivilWarSeaPortGate> gate = mockStatic(CivilWarSeaPortGate.class);
				MockedStatic<CivilWarTempRebelFactory> factory = mockStatic(CivilWarTempRebelFactory.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<me.Plugins.SimpleFactions.Loaders.GuildLoader> guildLoader =
						mockStatic(me.Plugins.SimpleFactions.Loaders.GuildLoader.class)) {
			guildLoader.when(me.Plugins.SimpleFactions.Loaders.GuildLoader::getBaseType).thenReturn(null);
			gate.when(() -> CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(eq(host), any()))
					.thenReturn(true);
			factory.when(() -> CivilWarTempRebelFactory.createFromMainGuild(eq(host), eq(rebelGuild), eq("Alice")))
					.thenReturn(new Guild.RebelNation(rebels, "Gaba Gaba"));
			wars.when(() -> WarManager.startCivilWar(
					eq(rebels),
					eq(host),
					eq(WarGoalType.OVERTHROW),
					eq("mov-1"),
					any(),
					any(),
					any())).thenReturn(war);

			String error = CivilWarStartService.start(movement);

			assertNull(error);
			verify(movement).setFrozen(true);
			verify(government, never()).endMovement(any());
			assertNull(hostHandler.getById("port-1"));
			assertEquals("port-1", rebelHandler.getById("port-1").getId());
			verify(host).removeProvince(2, false);
			verify(rebels).addProvince(2);
		}
	}

	@Test
	void hostGuildRebels_foldsDirectVassalUnderTempRebels_skipsNestedExtraMain() {
		Movement movement = baseMovement(Action.CHANGE_LEADER, Member.GUILD_LEADER);
		Faction host = movement.getFaction();
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(1, 2)));
		when(host.getCapital()).thenReturn(1);
		GuildHandler hostGuilds = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(hostGuilds);
		Guild rebelGuild = mock(Guild.class);
		when(rebelGuild.getId()).thenReturn("guild-a");
		when(rebelGuild.isBase()).thenReturn(false);
		when(rebelGuild.getFaction()).thenReturn(host);
		when(rebelGuild.isMember("Alice")).thenReturn(true);
		when(rebelGuild.hasCapital()).thenReturn(false);
		when(hostGuilds.getGuilds()).thenReturn(List.of(rebelGuild));
		when(movement.getAllSupportingGuilds()).thenReturn(List.of(rebelGuild));
		when(movement.getLeader()).thenReturn("Alice");
		when(movement.getId()).thenReturn("mov-fold");
		when(movement.getSupporters()).thenReturn(new Pool());
		Cause cause = movement.getCauses().get(0);
		Proposal proposal = cause.getProposal();
		when(proposal.hasTarget()).thenReturn(false);

		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		Faction nested = mock(Faction.class);
		when(nested.getId()).thenReturn("nested");
		Relation vassalRelation = relation("vassal_type", true, false);
		RelationType vassalType = vassalRelation.getType();
		when(host.getRelation("vassal")).thenReturn(vassalRelation);
		when(movement.getAllSupportingFactions()).thenReturn(List.of(vassal, nested));

		Faction rebels = mock(Faction.class);
		when(rebels.getId()).thenReturn("host_rebels");
		InstallationHandler hostHandler = new InstallationHandler(host);
		InstallationHandler rebelHandler = new InstallationHandler(rebels);
		when(host.getInstallationHandler()).thenReturn(hostHandler);
		when(rebels.getInstallationHandler()).thenReturn(rebelHandler);
		when(rebels.getOrCreateMainGuild()).thenReturn(mock(Guild.class));
		stubRebelVassalage(rebels);
		hostHandler.acceptTransferred(new Installation("port-1", "Harbour", InstallationKind.PORT, 2, 0, 0, 1L));

		War war = mock(War.class);
		when(war.getMovementId()).thenReturn("mov-fold");

		try (MockedStatic<CivilWarSeaPortGate> gate = mockStatic(CivilWarSeaPortGate.class);
				MockedStatic<CivilWarTempRebelFactory> factory = mockStatic(CivilWarTempRebelFactory.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<RelationLoader> types = mockStatic(RelationLoader.class);
				MockedStatic<me.Plugins.SimpleFactions.Loaders.GuildLoader> guildLoader =
						mockStatic(me.Plugins.SimpleFactions.Loaders.GuildLoader.class)) {
			guildLoader.when(me.Plugins.SimpleFactions.Loaders.GuildLoader::getBaseType).thenReturn(null);
			gate.when(() -> CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(eq(host), any()))
					.thenReturn(true);
			factory.when(() -> CivilWarTempRebelFactory.createFromMainGuild(eq(host), eq(rebelGuild), eq("Alice")))
					.thenReturn(new Guild.RebelNation(rebels, "Gaba Gaba"));
			relations.when(() -> RelationManager.getOverlord(vassal)).thenReturn("host");
			relations.when(() -> RelationManager.getOverlord(nested)).thenReturn("vassal");
			relations.when(() -> RelationManager.endVassalage(eq(vassal), eq(host), eq(false))).thenReturn(true);
			factions.when(FactionManager::getMap).thenReturn(mock(MapSystem.class));
			factions.when(() -> FactionManager.getByString("vassal")).thenReturn(vassal);
			factions.when(() -> FactionManager.getByString("host_rebels")).thenReturn(rebels);
			types.when(() -> RelationLoader.getType("vassal_type")).thenReturn(vassalType);
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<Faction>> extras = ArgumentCaptor.forClass(List.class);
			wars.when(() -> WarManager.startCivilWar(
					eq(rebels),
					eq(host),
					eq(WarGoalType.OVERTHROW),
					eq("mov-fold"),
					extras.capture(),
					any(),
					any())).thenReturn(war);

			String error = CivilWarStartService.start(movement);

			assertNull(error);
			relations.verify(() -> RelationManager.setRelationForced(
					eq(vassalType),
					eq(vassal),
					eq(rebels)));
			relations.verify(() -> RelationManager.endVassalage(eq(nested), any(), anyBoolean()), never());
			assertEquals(List.of(vassal), extras.getValue());
		}
	}

	@Test
	void pureVassal_noTempFactionNoHostLandMoved() {
		Movement movement = baseMovement(Action.CHANGE_LEADER, Member.VASSAL_LEADER);
		Faction host = movement.getFaction();
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(1, 2, 3)));
		when(movement.getAllSupportingGuilds()).thenReturn(List.of());
		Faction vassal = mock(Faction.class);
		when(vassal.getId()).thenReturn("vassal");
		Relation vassalRelation = relation("vassal_type", true, false);
		when(host.getRelation("vassal")).thenReturn(vassalRelation);
		Relation overlordRelation = relation("overlord", false, true);
		when(vassal.getRelation("host")).thenReturn(overlordRelation);
		when(movement.getAllSupportingFactions()).thenReturn(List.of(vassal));
		when(movement.getLeader()).thenReturn("Alice");
		when(movement.getId()).thenReturn("mov-2");
		when(movement.getSupporters()).thenReturn(new Pool());

		War war = mock(War.class);
		when(war.getMovementId()).thenReturn("mov-2");

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<CivilWarTempRebelFactory> factory = mockStatic(CivilWarTempRebelFactory.class)) {
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(vassal);
			relations.when(() -> RelationManager.getOverlord(vassal)).thenReturn("host");
			ArgumentCaptor<CivilWarSnapshot> snapshot = ArgumentCaptor.forClass(CivilWarSnapshot.class);
			wars.when(() -> WarManager.startCivilWar(
					eq(vassal),
					eq(host),
					eq(WarGoalType.OVERTHROW),
					eq("mov-2"),
					any(),
					any(),
					snapshot.capture())).thenReturn(war);
			relations.when(() -> RelationManager.endVassalage(eq(vassal), eq(host), eq(false))).thenReturn(true);

			String error = CivilWarStartService.start(movement);

			assertNull(error);
			verify(movement).setFrozen(true);
			factory.verifyNoInteractions();
			verify(host, never()).removeProvince(anyInt(), anyBoolean());
			verify(host, never()).addProvince(anyInt());
			assertEquals("vassal_type", snapshot.getValue().getWartimeVassalEnds().get(0).relationTypeId());
		}
	}

	@Test
	void failedStartCivilWar_restoresHostRegimentSlots() {
		Movement movement = baseMovement(Action.CHANGE_LEADER, Member.GUILD_LEADER);
		when(movement.getPower()).thenReturn(50.0);
		Faction host = movement.getFaction();
		Government government = host.getGovernment();
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(1, 2)));
		when(host.getCapital()).thenReturn(1);
		GuildHandler hostGuilds = mock(GuildHandler.class);
		when(host.getGuildHandler()).thenReturn(hostGuilds);
		Guild rebelGuild = mock(Guild.class);
		when(rebelGuild.getId()).thenReturn("guild-a");
		when(rebelGuild.isBase()).thenReturn(false);
		when(rebelGuild.getFaction()).thenReturn(host);
		when(rebelGuild.isMember("Alice")).thenReturn(true);
		when(rebelGuild.hasCapital()).thenReturn(false);
		when(hostGuilds.getGuilds()).thenReturn(List.of(rebelGuild));
		when(movement.getAllSupportingGuilds()).thenReturn(List.of(rebelGuild));
		when(movement.getAllSupportingFactions()).thenReturn(List.of());
		when(movement.getLeader()).thenReturn("Alice");
		when(movement.getId()).thenReturn("mov-rollback");
		when(movement.getSupporters()).thenReturn(new Pool());
		Cause cause = movement.getCauses().get(0);
		Proposal proposal = cause.getProposal();
		when(proposal.hasTarget()).thenReturn(false);

		Regiment hostProfessional = mockRegiment("professional", 10);
		Regiment rebelProfessional = mockRegiment("professional", 0);
		attachMilitary(host, List.of(hostProfessional));

		Faction rebels = mock(Faction.class);
		when(rebels.getId()).thenReturn("host_rebels");
		attachMilitary(rebels, List.of(rebelProfessional));
		InstallationHandler hostHandler = new InstallationHandler(host);
		InstallationHandler rebelHandler = new InstallationHandler(rebels);
		when(host.getInstallationHandler()).thenReturn(hostHandler);
		when(rebels.getInstallationHandler()).thenReturn(rebelHandler);
		when(rebels.getOrCreateMainGuild()).thenReturn(mock(Guild.class));
		stubRebelVassalage(rebels);

		try (MockedStatic<CivilWarSeaPortGate> gate = mockStatic(CivilWarSeaPortGate.class);
				MockedStatic<CivilWarTempRebelFactory> factory = mockStatic(CivilWarTempRebelFactory.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<me.Plugins.SimpleFactions.Loaders.GuildLoader> guildLoader =
						mockStatic(me.Plugins.SimpleFactions.Loaders.GuildLoader.class)) {
			guildLoader.when(me.Plugins.SimpleFactions.Loaders.GuildLoader::getBaseType).thenReturn(null);
			gate.when(() -> CivilWarSeaPortGate.rebelsWouldHaveRequiredPort(eq(host), any()))
					.thenReturn(true);
			factory.when(() -> CivilWarTempRebelFactory.createFromMainGuild(eq(host), eq(rebelGuild), eq("Alice")))
					.thenReturn(new Guild.RebelNation(rebels, "Gaba Gaba"));
			wars.when(() -> WarManager.startCivilWar(
					eq(rebels),
					eq(host),
					eq(WarGoalType.OVERTHROW),
					eq("mov-rollback"),
					any(),
					any(),
					any())).thenReturn(null);
			wars.when(WarManager::getLastDeclareError).thenReturn("populate failed");

			String error = CivilWarStartService.start(movement);

			assertEquals("populate failed", error);
			verify(movement, never()).setFrozen(true);
			verify(government, never()).endMovement(any());
			assertEquals(10, hostProfessional.getCurrentSlots());
			assertEquals(0, rebelProfessional.getCurrentSlots());
		}
	}

	@Test
	void secondCivilWar_doesNotFreeze() {
		Movement movement = baseMovement(Action.CHANGE_LEADER, Member.MEMBER);
		Faction host = movement.getFaction();
		when(host.getProvinces()).thenReturn(new ArrayList<>(List.of(1, 2)));

		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			lock.when(() -> CivilWarBorderLock.refuseStart(movement, host))
					.thenReturn(CivilWarCopy.ALREADY_IN_CIVIL_WAR);
			String error = CivilWarStartService.start(movement);
			assertEquals(CivilWarCopy.ALREADY_IN_CIVIL_WAR, error);
			verify(movement, never()).setFrozen(true);
		}
	}

	@Test
	void hostAsTransferPayload_doesNotFreeze() {
		Movement movement = baseMovement(Action.CHANGE_LEADER, Member.MEMBER);
		Faction host = movement.getFaction();
		try (MockedStatic<CivilWarBorderLock> lock = mockStatic(CivilWarBorderLock.class)) {
			lock.when(() -> CivilWarBorderLock.refuseStart(movement, host))
					.thenReturn(CivilWarCopy.HOST_IS_WAR_PAYLOAD);
			String error = CivilWarStartService.start(movement);
			assertEquals(CivilWarCopy.HOST_IS_WAR_PAYLOAD, error);
			verify(movement, never()).setFrozen(true);
		}
	}

	@Test
	void applyConfiguredVassalage_setsLawFromCacheIds() {
		Faction rebels = mock(Faction.class);
		stubRebelVassalage(rebels);
		assertNull(CivilWarStartService.applyConfiguredVassalage(rebels));
		verify(rebels.getLawHandler().getGroup("vassalage")).setCurrent(org.mockito.ArgumentMatchers.any());
	}

	private static Movement baseMovement(Action action, Member leaderRelation) {
		Movement movement = mock(Movement.class);
		Faction host = mock(Faction.class);
		Government government = mock(Government.class);
		when(host.getId()).thenReturn("host");
		when(host.getGovernment()).thenReturn(government);
		when(host.getRelationToFaction("Alice")).thenReturn(leaderRelation);
		when(movement.getFaction()).thenReturn(host);
		when(movement.getLeader()).thenReturn("Alice");
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getForeignBackers()).thenReturn(List.of());
		Cause cause = mock(Cause.class);
		Proposal proposal = mock(Proposal.class);
		when(cause.getAction()).thenReturn(action);
		when(cause.getProposal()).thenReturn(proposal);
		when(cause.getPool()).thenReturn(new Pool());
		when(movement.getCauses()).thenReturn(List.of(cause));
		when(movement.getSupporters()).thenReturn(new Pool());
		when(movement.getAllSupportingGuilds()).thenReturn(List.of());
		when(movement.getAllSupportingFactions()).thenReturn(List.of());
		return movement;
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

	private static Regiment mockRegiment(String id, int slots) {
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

	private static Relation relation(String typeId, boolean vassalage, boolean overlord) {
		Relation relation = mock(Relation.class);
		RelationType type = mock(RelationType.class);
		when(type.getId()).thenReturn(typeId);
		when(type.isVassalage()).thenReturn(vassalage);
		when(type.isOverlord()).thenReturn(overlord);
		when(relation.getType()).thenReturn(type);
		return relation;
	}

	private static void stubRebelVassalage(Faction rebels) {
		LawGroup group = mock(LawGroup.class);
		Law law = mock(Law.class);
		when(law.getScopedEffects()).thenReturn(java.util.Map.of());
		when(group.getLaw("inclusive")).thenReturn(law);
		LawHandler handler = mock(LawHandler.class);
		when(handler.getGroup("vassalage")).thenReturn(group);
		when(rebels.getLawHandler()).thenReturn(handler);
	}
}
