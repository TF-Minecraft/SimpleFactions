package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.HeldRealms;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompanyService;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;

class StaffJoinsTest {
	private MockedStatic<Members> members;
	private MockedStatic<FactionManager> factions;
	private MockedStatic<Bukkit> bukkit;
	private Faction realm;
	private Guild guild;
	private Player bob;
	private Player carl;
	private Player stranger;

	@BeforeEach
	void setUp() {
		members = mockStatic(Members.class, CALLS_REAL_METHODS);
		factions = mockStatic(FactionManager.class);
		bukkit = mockStatic(Bukkit.class);
		realm = mock(Faction.class);
		when(realm.getName()).thenReturn("Realm");
		guild = mock(Guild.class);
		when(guild.getName()).thenReturn("Merchants");
		when(guild.getFaction()).thenReturn(realm);
		bob = online("Bob");
		carl = online("Carl");
		stranger = mock(Player.class);
		bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(bob, carl, stranger));
		members.when(() -> Members.findOnline(anyString())).thenReturn(null);
		members.when(() -> Members.findOnline("Bob")).thenReturn("Bob");
		members.when(() -> Members.findOnline("Ghost")).thenReturn("Ghost");
		members.when(() -> Members.info("Bob")).thenReturn(Optional.of(info("Bob", CharacterStatus.ALIVE)));
		members.when(() -> Members.info("Ghost")).thenReturn(Optional.of(info("Ghost", CharacterStatus.DEAD)));
		members.when(() -> Members.display(anyString())).thenAnswer(call -> call.getArgument(0));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		factions.close();
		members.close();
	}

	private static CharacterInfo info(String name, CharacterStatus status) {
		return new CharacterInfo(new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), name, status, "human",
				Set.of(), 0L, Instant.EPOCH);
	}

	private Player online(String key) {
		Player player = mock(Player.class);
		members.when(() -> Members.key(player)).thenReturn(key);
		members.when(() -> Members.online(key)).thenReturn(Optional.of(player));
		return player;
	}

	@Test
	void guildJoinsRefuseTheBaseGuildAClosingRealmAndUnknownCharacters() {
		when(guild.isBase()).thenReturn(true);
		assertEquals("§cThis is the base guild, use /faction forcejoin instead", StaffJoins.guild(guild, "Bob"));
		when(guild.isBase()).thenReturn(false);
		GuildHandler handler = mock(GuildHandler.class);
		Guild main = mock(Guild.class);
		when(realm.getId()).thenReturn("realm");
		when(realm.getGuildHandler()).thenReturn(handler);
		when(handler.getGuild("realm")).thenReturn(main);
		when(main.getClosingSince()).thenReturn(5L);
		assertEquals(HeldRealms.CLOSING, StaffJoins.guild(guild, "Bob"));
		when(main.getClosingSince()).thenReturn(0L);
		assertEquals(StaffJoins.noMatch("Nobody"), StaffJoins.guild(guild, "Nobody"));
		assertEquals(StaffJoins.noMatch("Ghost"), StaffJoins.guild(guild, "Ghost"));
		members.when(() -> Members.findOnline("Unknown")).thenReturn("Unknown");
		members.when(() -> Members.info("Unknown")).thenReturn(Optional.empty());
		assertEquals(StaffJoins.noMatch("Unknown"), StaffJoins.guild(guild, "Unknown"));
		when(guild.isMember("Bob")).thenReturn(true);
		assertEquals("§cBob §cis already in Merchants", StaffJoins.guild(guild, "Bob"));
		verify(guild, never()).addMember(anyString());
	}

	@Test
	void guildJoinsRefuseOtherRealmsOtherGuildsAndTheRealmLeader() {
		Guild current = mock(Guild.class);
		factions.when(() -> FactionManager.getGuildByMember("Bob")).thenReturn(current);
		Faction other = mock(Faction.class);
		when(other.getName()).thenReturn("Other");
		when(current.getFaction()).thenReturn(other);
		assertEquals("§cBob §cbelongs to Other§c, another faction", StaffJoins.guild(guild, "Bob"));
		when(current.getFaction()).thenReturn(realm);
		when(current.getName()).thenReturn("Smiths");
		assertEquals("§cBob §cis already in the guild Smiths", StaffJoins.guild(guild, "Bob"));
		when(current.isBase()).thenReturn(true);
		when(current.isLeader("Bob")).thenReturn(true);
		assertEquals("§cBob §cleads the faction and cannot join a guild", StaffJoins.guild(guild, "Bob"));
		verify(guild, never()).addMember(anyString());
	}

	@Test
	void guildJoinsAddTheCharacterAndTellTheGuild() {
		assertEquals("§aAdded Bob §ato Merchants", StaffJoins.guild(guild, "Bob"));
		verify(guild).addMember("Bob");
		verify(realm).updatePrestige();
		verify(bob).sendMessage("§aStaff added you to the guild Merchants");
		verify(carl, never()).sendMessage(anyString());

		Guild base = mock(Guild.class);
		when(base.getFaction()).thenReturn(realm);
		when(base.isBase()).thenReturn(true);
		factions.when(() -> FactionManager.getGuildByMember("Bob")).thenReturn(base);
		when(guild.isMember("Carl")).thenReturn(true);
		assertEquals("§aAdded Bob §ato Merchants", StaffJoins.guild(guild, "Bob"));
		verify(carl).sendMessage("§aBob joined the guild Merchants§a!");
		verify(stranger, never()).sendMessage(anyString());
	}

	@Test
	void companyJoinsUseTheCompanyRules() {
		try (var service = mockStatic(MercenaryCompanyService.class)) {
			MercenaryCompany company = mock(MercenaryCompany.class);
			when(company.getName()).thenReturn("Black Lances");
			when(company.getLeader()).thenReturn("Carl");
			assertEquals(StaffJoins.noMatch("Nobody"), StaffJoins.company(company, "Nobody"));
			service.when(() -> MercenaryCompanyService.join(company, "Bob"))
					.thenReturn(MercenaryResult.deny("Every slot is already filled."));
			assertEquals("§cCannot enlist Bob: Every slot is already filled.", StaffJoins.company(company, "Bob"));
			service.when(() -> MercenaryCompanyService.join(company, "Bob")).thenReturn(MercenaryResult.ok("Joined."));
			assertEquals("§aEnlisted Bob §ain Black Lances", StaffJoins.company(company, "Bob"));
			verify(bob).sendMessage("§aStaff enlisted you in Black Lances.");
			verify(carl).sendMessage("§aBob signed on with the company.");
		}
	}

	@Test
	void warbandJoinsSignTheCharacterUp() {
		try (var signup = mockStatic(CampaignWarbandSignupService.class)) {
			Warband warband = mock(Warband.class);
			when(warband.getId()).thenReturn("vanguard");
			UUID account = UUID.randomUUID();
			assertEquals(StaffJoins.noMatch("Nobody"), StaffJoins.warband(warband, "Nobody"));
			members.when(() -> Members.accountId("Bob")).thenReturn(Optional.empty());
			assertEquals(StaffJoins.noMatch("Bob"), StaffJoins.warband(warband, "Bob"));
			members.when(() -> Members.accountId("Bob")).thenReturn(Optional.of(account));
			when(warband.hasMember(account)).thenReturn(true);
			assertEquals("§cBob §cis already in vanguard", StaffJoins.warband(warband, "Bob"));
			when(warband.hasMember(account)).thenReturn(false);
			when(warband.isFaction()).thenReturn(true);
			assertEquals("§cThis is a faction warband, Bob §cneeds a faction to join", StaffJoins.warband(warband, "Bob"));
			factions.when(() -> FactionManager.getByMember("Bob")).thenReturn(realm);
			signup.when(() -> CampaignWarbandSignupService.signupMember(account, "Bob", warband, realm, bob))
					.thenReturn("Warband identity is held for staff review");
			assertEquals("§cCannot add Bob: Warband identity is held for staff review", StaffJoins.warband(warband, "Bob"));
			signup.when(() -> CampaignWarbandSignupService.signupMember(account, "Bob", warband, realm, bob)).thenReturn(null);
			assertEquals("§aAdded Bob §ato vanguard", StaffJoins.warband(warband, "Bob"));
			verify(bob).sendMessage("§aStaff added you to the warband vanguard");

			when(warband.isFaction()).thenReturn(false);
			members.when(() -> Members.online("Bob")).thenReturn(Optional.empty());
			signup.when(() -> CampaignWarbandSignupService.signupMember(any(), any(), any(), any(), any())).thenReturn(null);
			assertEquals("§aAdded Bob §ato vanguard", StaffJoins.warband(warband, "Bob"));
		}
	}
}
