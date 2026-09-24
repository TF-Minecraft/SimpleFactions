package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.utils.TabCompletion;

class GuildKickCommandTest {

	@Test
	void leaderRemovesAMemberAndNotifiesThem() {
		Player leader = player("Leader");
		Player member = player("Steve");
		Guild guild = guild(false, "Smiths", "Leader", "Leader", "Steve");
		Command command = guildCommand();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(member));

			assertTrue(new CommandManager().onCommand(leader, command, "guild", new String[] {"kick", "steve"}));

			verify(guild).kick("steve");
			verify(leader).sendMessage("§aKicked steve");
			verify(member).sendMessage("§aLeader kicked you from Smiths");
		}
	}

	@Test
	void onlyTheGuildLeaderCanKick() {
		Player player = player("Steve");
		Command command = guildCommand();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getGuildByLeader("Steve")).thenReturn(null);

			assertTrue(new CommandManager().onCommand(player, command, "guild", new String[] {"kick", "Alex"}));

			verify(player).sendMessage("§cYou are not the leader of a guild");
			bukkit.verifyNoInteractions();
		}
	}

	@Test
	void baseGuildUsesFactionKick() {
		Player leader = player("Leader");
		Guild guild = guild(true, "Nation", "Leader", "Leader", "Steve");
		Command command = guildCommand();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);

			assertTrue(new CommandManager().onCommand(leader, command, "guild", new String[] {"kick", "Steve"}));

			verify(leader).sendMessage("§cThis is the base guild, use /faction kick instead");
			verify(guild, never()).kick(org.mockito.ArgumentMatchers.anyString());
			bukkit.verifyNoInteractions();
		}
	}

	@Test
	void leaderCannotKickThemselves() {
		Player leader = player("Leader");
		Guild guild = guild(false, "Smiths", "Leader", "Leader", "Steve");
		Command command = guildCommand();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);

			assertTrue(new CommandManager().onCommand(leader, command, "guild", new String[] {"kick", "leader"}));

			verify(leader).sendMessage("§cCant kick the leader!");
			bukkit.verifyNoInteractions();
		}
	}

	@Test
	void unknownNameIsRejected() {
		Player leader = player("Leader");
		Guild guild = guild(false, "Smiths", "Leader", "Leader", "Steve");
		Command command = guildCommand();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);

			assertTrue(new CommandManager().onCommand(leader, command, "guild", new String[] {"kick", "Alex"}));

			verify(leader).sendMessage("§cPlayer is not a member");
			bukkit.verifyNoInteractions();
		}
	}

	@Test
	void tabCompletionOffersKickAndOtherMembers() {
		Player leader = player("Leader");
		Guild guild = guild(false, "Smiths", "Leader", "Leader", "Steve");
		Command command = guildCommand();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(guild);
			factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(guild);

			List<String> commands = new TabCompletion().onTabComplete(leader, command, "guild", new String[] {""});
			assertTrue(commands.contains("kick"));

			assertEquals(List.of("Steve"),
					new TabCompletion().onTabComplete(leader, command, "guild", new String[] {"kick", ""}));
		}
	}

	private static Player player(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		return player;
	}

	private static Command guildCommand() {
		Command command = mock(Command.class);
		when(command.getName()).thenReturn("guild");
		return command;
	}

	private static Guild guild(boolean base, String name, String leader, String... members) {
		Guild guild = mock(Guild.class);
		when(guild.isBase()).thenReturn(base);
		when(guild.getName()).thenReturn(name);
		when(guild.getLeader()).thenReturn(leader);
		when(guild.getMembers()).thenReturn(new ArrayList<>(List.of(members)));
		when(guild.isMember(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
			String asked = invocation.getArgument(0);
			for (String member : members) {
				if (member.equalsIgnoreCase(asked)) {
					return true;
				}
			}
			return false;
		});
		return guild;
	}
}
