package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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

import net.tfminecraft.simplefactions.economy.TreasuryActions;
import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;

class GuildBankCommandTest {

	private MockedStatic<FactionManager> factions;
	private MockedStatic<TreasuryActions> treasury;
	private final Player player = mock(Player.class);
	private final Guild guild = mock(Guild.class);
	private final Bank bank = mock(Bank.class);
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
        treasury = mockStatic(TreasuryActions.class);
        treasury.when(() -> TreasuryActions.pouch(player)).thenReturn(5000d);
        treasury.when(() -> TreasuryActions.bank(player, guild, 250d, false)).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		factions.close();
		treasury.close();
	}

	private boolean run(String... args) {
		return new CommandManager().onCommand(player, command, "guild", args);
	}

	@Test
	void aMemberWhoIsNotTheLeaderCannotWithdraw() {
		// A member of the main guild could otherwise empty the faction treasury.
		assertTrue(run("withdraw", "5000"));

		verify(player).sendMessage("§cYou need to be a guild leader to withdraw from the guild bank");
		treasury.verifyNoInteractions();
	}

	@Test
	void theLeaderCanWithdraw() {
		factions.when(() -> FactionManager.getGuildByLeader("Steve")).thenReturn(guild);

		run("withdraw", "250");

		treasury.verify(() -> TreasuryActions.bank(player, guild, 250d, false));
	}

	@Test
	void fractionsOfACentAreRefused() {
		factions.when(() -> FactionManager.getGuildByLeader("Steve")).thenReturn(guild);

		run("withdraw", "0.005");
		run("deposit", "0.005");

		treasury.verifyNoInteractions();
	}
}
