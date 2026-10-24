package net.tfminecraft.simplefactions.guild;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.espionage.EspionageState;
import net.tfminecraft.simplefactions.government.Council;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.session.Session;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import net.tfminecraft.tlibs.character.CharacterRef;

class MemberDeparturesTest {
	private MockedStatic<SimpleFactions> plugin;
	private MockedStatic<Members> members;
	private MockedStatic<WarbandMembershipService> warbands;
	private WarbandMembershipService warbandService;
	private Faction faction;
	private Guild main;
	private Guild smiths;
	private Government government;
	private Council council;
	private EspionageState espionage;

	@BeforeEach
	void setUp() {
		plugin = mockStatic(SimpleFactions.class);
		members = mockStatic(Members.class, CALLS_REAL_METHODS);
		warbands = mockStatic(WarbandMembershipService.class);
		warbandService = mock(WarbandMembershipService.class);
		warbands.when(WarbandMembershipService::getInstance).thenReturn(warbandService);
		faction = mock(Faction.class);
		main = mock(Guild.class);
		smiths = mock(Guild.class);
		GuildHandler guilds = mock(GuildHandler.class);
		when(guilds.getGuilds()).thenReturn(List.of(main, smiths));
		when(faction.getGuildHandler()).thenReturn(guilds);
		government = mock(Government.class);
		council = mock(Council.class);
		when(government.getCouncil()).thenReturn(council);
		when(faction.getGovernment()).thenReturn(government);
		espionage = mock(EspionageState.class);
		when(faction.getEspionage()).thenReturn(espionage);
		when(smiths.getFaction()).thenReturn(faction);
	}

	@AfterEach
	void tearDown() {
		warbands.close();
		members.close();
		plugin.close();
	}

	@Test
	void leavingAGuildClearsItsOffersAndReturnsToTheMainGuild() {
		MemberDepartures.leaveGuild(smiths, "Bob");
		InOrder order = inOrder(smiths, faction);
		order.verify(smiths).kick("Bob");
		order.verify(smiths).forgetDeparted("Bob");
		order.verify(faction).addMember("Bob");
		verify(government, never()).removeRights("Bob");
	}

	@Test
	void leavingTheRealmDropsEveryRightLikeADeath() {
		SimpleFactions instance = mock(SimpleFactions.class);
		SessionManager sessions = mock(SessionManager.class);
		Session session = mock(Session.class);
		when(instance.getSessionManager()).thenReturn(sessions);
		when(sessions.getSession(council)).thenReturn(session);
		plugin.when(SimpleFactions::getInstance).thenReturn(instance);
		String key = UUID.randomUUID().toString();
		CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.fromString(key));
		members.when(() -> Members.ref(key)).thenReturn(Optional.of(ref));

		MemberDepartures.leaveRealm(faction, key);

		verify(faction).forceRemoveMember(key);
		verify(main).forgetDeparted(key);
		verify(smiths).forgetDeparted(key);
		verify(government).removeRights(key);
		verify(session).removeDeadCharacter(key);
		verify(espionage).vacateFor(key);
		verify(warbandService).removeFromFactionWarband(ref);
		verify(faction).updatePrestige();
	}

	@Test
	void noPluginSessionOrCharacterIsSkipped() {
		MemberDepartures.leaveRealm(faction, "Legacy Name");
		SimpleFactions instance = mock(SimpleFactions.class);
		plugin.when(SimpleFactions::getInstance).thenReturn(instance);
		MemberDepartures.leaveRealm(faction, "Legacy Name");
		SessionManager sessions = mock(SessionManager.class);
		when(instance.getSessionManager()).thenReturn(sessions);
		MemberDepartures.leaveRealm(faction, "Legacy Name");
		verify(government, org.mockito.Mockito.times(3)).removeRights("Legacy Name");
		verify(sessions).getSession(council);
		verify(warbandService, never()).removeFromFactionWarband(any());
	}
}
