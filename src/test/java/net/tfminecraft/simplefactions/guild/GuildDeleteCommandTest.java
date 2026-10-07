package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.loans.LoanHandler;
import net.tfminecraft.simplefactions.managers.CommandManager;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.handler.ProvinceHandler;
import net.tfminecraft.simplefactions.utils.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class GuildDeleteCommandTest {
  @ParameterizedTest
  @EnumSource(FinancialBlock.class)
  void aLeaderCannotDeleteGuildFinancesThatStillNeedSettling(FinancialBlock block) {
    try (Fixture fixture = new Fixture("Leader", false)) {
      fixture.block(block);

      assertTrue(fixture.run("delete", "smiths"));

      verify(fixture.player).sendMessage(block.message);
      fixture.assertUntouched();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"Leader", "lEaDeR"})
  void aLeaderCanDeleteAnEmptyDebtFreeGuildAndReturnItsMembers(String name) {
    try (Fixture fixture = new Fixture(name, false)) {
      assertTrue(fixture.run("delete", "smiths"));

      fixture.assertDeleted();
    }
  }

  @Test
  void aLeaderCanDeleteAGuildWithoutABank() {
    try (Fixture fixture = new Fixture("Leader", false)) {
      when(fixture.guild.getBank()).thenReturn(null);

      assertTrue(fixture.run("delete", "smiths"));

      fixture.assertDeleted();
    }
  }

  @Test
  void anAdministratorCanBypassTheFinancialGuards() {
    try (Fixture fixture = new Fixture("Administrator", true)) {
      for (FinancialBlock block : FinancialBlock.values()) fixture.block(block);

      assertTrue(fixture.run("delete", "smiths"));

      fixture.assertDeleted();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void evenAdministratorsCannotDeleteABaseGuild(boolean admin) {
    try (Fixture fixture = new Fixture(admin ? "Administrator" : "Leader", admin)) {
      when(fixture.guild.isBase()).thenReturn(true);

      assertTrue(fixture.run("delete", "smiths"));

      verify(fixture.player).sendMessage("§cYou cannot delete the base guild of a faction");
      fixture.assertUntouched();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"Member", "Outsider"})
  void membershipAloneDoesNotAuthorizeDeletion(String name) {
    try (Fixture fixture = new Fixture(name, false)) {
      assertTrue(fixture.run("delete", "smiths"));

      verify(fixture.player).sendMessage("§cYou are not the leader of this guild");
      fixture.assertUntouched();
    }
  }

  @Test
  void deletingAnUnknownGuildLeavesExistingGuildsUntouched() {
    try (Fixture fixture = new Fixture("Leader", false)) {
      assertTrue(fixture.run("delete", "missing"));

      verify(fixture.player).sendMessage("§cNo guild by the id missing");
      fixture.assertUntouched();
    }
  }

  @Test
  void joiningAnUnknownGuildReportsTheMissingGuildWithoutRemovingCurrentMembership() {
    try (Fixture fixture = new Fixture("Joiner", false)) {
      Guild previous = mock(Guild.class);
      fixture.factions.when(() -> FactionManager.canJoinGuild(fixture.player)).thenReturn(true);
      fixture.factions.when(() -> FactionManager.getGuildByMember("Joiner")).thenReturn(previous);

      assertTrue(fixture.run("join", "missing"));

      verify(fixture.player).sendMessage("§cNo guild by the id missing");
      verifyNoInteractions(previous);
      fixture.assertUntouched();
    }
  }

  @Test
  void anExistingInvitationStillTransfersMembershipAndNotifiesTheRealm() {
    try (Fixture fixture = new Fixture("Joiner", false)) {
      Guild previous = mock(Guild.class);
      fixture.factions.when(() -> FactionManager.canJoinGuild(fixture.player)).thenReturn(true);
      fixture.factions.when(() -> FactionManager.getGuildByMember("Joiner")).thenReturn(previous);
      when(fixture.guild.consumeInvite("Joiner")).thenReturn(true);
      fixture.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.member));

      assertTrue(fixture.run("join", "smiths"));

      verify(previous).kick("Joiner");
      verify(fixture.guild).consumeInvite("Joiner");
      assertEquals(List.of("Leader", "Member", "Offline", "Joiner"), fixture.members);
      verify(fixture.faction).updatePrestige();
      verify(fixture.player).sendMessage("§aJoined Smiths");
      verify(fixture.member).sendMessage("§aJoiner joined the faction!");
      assertSame(fixture.guild, fixture.guilds.getGuild("smiths"));
      verifyNoInteractions(fixture.provinces);
    }
  }

  private enum FinancialBlock {
    BANKRUPTCY("§cCannot delete a guild that is in bankruptcy!"),
    BANK_BALANCE("§cCannot delete a guild while the bank balance is above 0"),
    ACTIVE_LOAN("§cCannot delete a guild with active loans");

    private final String message;

    FinancialBlock(String message) {
      this.message = message;
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final Player player = mock(Player.class);
    private final Player leader = mock(Player.class);
    private final Player member = mock(Player.class);
    private final Command command = mock(Command.class);
    private final Faction faction = mock(Faction.class);
    private final Guild guild = mock(Guild.class);
    private final Bank bank = mock(Bank.class);
    private final LoanHandler loans = mock(LoanHandler.class);
    private final ProvinceHandler provinces = mock(ProvinceHandler.class);
    private final GuildHandler guilds = new GuildHandler(faction);
    private final List<String> members = new ArrayList<>(List.of("Leader", "Member", "Offline"));
    private final List<String> returnedMembers = new ArrayList<>();
    private final MockedStatic<FactionManager> factions;
    private final MockedStatic<Bukkit> bukkit;
    private final MockedStatic<SimpleFactions> plugin;

    private Fixture(String name, boolean admin) {
      when(player.getName()).thenReturn(name);
      when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(admin);
      when(leader.getName()).thenReturn("Leader");
      when(member.getName()).thenReturn("Member");
      when(command.getName()).thenReturn("guild");
      when(guild.getId()).thenReturn("smiths");
      when(guild.getName()).thenReturn("Smiths");
      when(guild.getLeader()).thenReturn("Leader");
      when(guild.getFaction()).thenReturn(faction);
      when(guild.getBank()).thenReturn(bank);
      when(guild.getLoanHandler()).thenReturn(loans);
      when(loans.getLoansTaken()).thenReturn(List.of());
      when(guild.getMembers()).thenReturn(members);
      doAnswer(
              call -> {
                members.add(call.getArgument(0));
                return null;
              })
          .when(guild)
          .addMember(anyString());
      when(faction.getName()).thenReturn("Realm");
      when(faction.getMembers()).thenAnswer(call -> new ArrayList<>(members));
      when(faction.getGuildHandler()).thenReturn(guilds);
      when(faction.getProvinceHandler()).thenReturn(provinces);
      doAnswer(
              call -> {
                returnedMembers.add(call.getArgument(0));
                return null;
              })
          .when(faction)
          .addMember(anyString());
      guilds.addGuild(guild);

      factions = mockStatic(FactionManager.class);
      factions
          .when(() -> FactionManager.getGuildByString("smiths"))
          .thenAnswer(call -> guilds.getGuild("smiths"));
      bukkit = mockStatic(Bukkit.class);
      bukkit.when(() -> Bukkit.getPlayerExact("Leader")).thenReturn(leader);
      bukkit.when(() -> Bukkit.getPlayerExact("Member")).thenReturn(member);
      plugin = mockStatic(SimpleFactions.class);
    }

    private void block(FinancialBlock block) {
      switch (block) {
        case BANKRUPTCY -> when(guild.isBankrupt()).thenReturn(true);
        case BANK_BALANCE -> when(bank.getWealth()).thenReturn(0.01);
        case ACTIVE_LOAN -> when(loans.getLoansTaken()).thenReturn(List.of(mock(Loan.class)));
      }
    }

    private boolean run(String... args) {
      return new CommandManager().onCommand(player, command, "guild", args);
    }

    private void assertUntouched() {
      assertSame(guild, guilds.getGuild("smiths"));
      assertEquals(List.of("Leader", "Member", "Offline"), members);
      assertTrue(returnedMembers.isEmpty());
      verifyNoInteractions(provinces, leader, member);
      bukkit.verifyNoInteractions();
    }

    private void assertDeleted() {
      assertNull(guilds.getGuild("smiths"));
      assertEquals(List.of("Leader", "Member", "Offline"), returnedMembers);
      verify(provinces, atLeastOnce()).revalidateClaims();
      verify(member).sendMessage("§cYour guild has been deleted!");
      verify(member).sendMessage("§aJoined Realm");
      verify(leader, never()).sendMessage(anyString());
      verify(player).sendMessage("§cGuild Smiths §cdeleted!");
    }

    @Override
    public void close() {
      plugin.close();
      bukkit.close();
      factions.close();
    }
  }
}
