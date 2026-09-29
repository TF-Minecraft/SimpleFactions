package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.data.Account;
import net.tfminecraft.denareconomy.data.PlayerData;
import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;

class GuildBankCommandTest {

	private MockedStatic<FactionManager> factions;
	private MockedStatic<DenarEconomy> economy;
	private final Player player = mock(Player.class);
	private final Guild guild = mock(Guild.class);
	private final Bank bank = mock(Bank.class);
	private final Account pouch = mock(Account.class);
	private final Command command = mock(Command.class);

	@BeforeEach
	void setUp() {
		when(player.getName()).thenReturn("Steve");
		Chunk chunk = mock(Chunk.class);
		Location location = mock(Location.class);
		when(location.getChunk()).thenReturn(chunk);
		when(player.getLocation()).thenReturn(location);
		when(guild.getBank()).thenReturn(bank);
		when(bank.getChunk()).thenReturn(chunk);
		when(bank.getWealth()).thenReturn(5_000.0);
		when(command.getName()).thenReturn("guild");

		factions = mockStatic(FactionManager.class);
		factions.when(() -> FactionManager.getGuildByMember("Steve")).thenReturn(guild);
		economy = mockStatic(DenarEconomy.class);
		net.tfminecraft.denareconomy.managers.PlayerManager players =
				mock(net.tfminecraft.denareconomy.managers.PlayerManager.class);
		PlayerData data = mock(PlayerData.class);
		economy.when(DenarEconomy::getPlayerManager).thenReturn(players);
		when(players.get(player)).thenReturn(data);
		when(data.getPouch()).thenReturn(pouch);
		when(pouch.getBal()).thenReturn(5_000.0);
	}

	@AfterEach
	void tearDown() {
		factions.close();
		economy.close();
	}

	private boolean run(String... args) {
		return new CommandManager().onCommand(player, command, "guild", args);
	}

	@Test
	void aMemberWhoIsNotTheLeaderCannotWithdraw() {
		// A member of the main guild could otherwise empty the faction treasury.
		assertTrue(run("withdraw", "5000"));

		verify(player).sendMessage("§cYou need to be a guild leader to withdraw from the guild bank");
		verify(bank, never()).withdraw(anyDouble());
		verify(pouch, never()).change(anyDouble());
	}

	@Test
	void theLeaderCanWithdraw() {
		factions.when(() -> FactionManager.getGuildByLeader("Steve")).thenReturn(guild);

		run("withdraw", "250");

		verify(bank).withdraw(250.0);
		verify(pouch).change(250.0);
	}

	@Test
	void fractionsOfACentAreRefused() {
		factions.when(() -> FactionManager.getGuildByLeader("Steve")).thenReturn(guild);

		run("withdraw", "0.005");
		run("deposit", "0.005");

		verify(bank, never()).withdraw(anyDouble());
		verify(bank, never()).deposit(anyDouble());
		verify(pouch, never()).change(anyDouble());
	}
}
