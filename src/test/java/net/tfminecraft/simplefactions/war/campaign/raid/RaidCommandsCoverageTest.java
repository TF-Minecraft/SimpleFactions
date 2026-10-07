package net.tfminecraft.simplefactions.war.campaign.raid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

class RaidCommandsCoverageTest {
  private FactionDomainFixture fixture;
  private MockedConstruction<Database> databases;
  private Player alice;
  private Faction attacker;
  private Faction defender;
  private Command command;
  private RaidCommandManager commands;
  private RaidTabCompletion tabs;
  private List<Warband> previousBands;

  @BeforeEach
  void setUp() {
    databases = mockConstruction(Database.class);
    fixture = new FactionDomainFixture();
    alice = fixture.player("Alice");
    when(Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);
    attacker = fixture.saved("iron", "Alice");
    defender = fixture.saved("river", "Bob");
    command = mock(Command.class);
    when(command.getName()).thenReturn("raid");
    commands = new RaidCommandManager();
    tabs = new RaidTabCompletion();
    previousBands = new ArrayList<>(WarbandManager.get());
    WarbandManager.get().clear();
  }

  @AfterEach
  void close() {
    WarbandManager.get().clear();
    WarbandManager.get().addAll(previousBands);
    fixture.close();
    databases.close();
  }

  private War raid(String id) {
    War war = new War(WarManager.newId(), attacker, defender);
    war.setBattleDay(LocalDate.of(2026, 10, 10));
    CampaignRaid raid = new CampaignRaid();
    raid.setId(id);
    raid.setDisplayName("Iron Port Raid");
    raid.setWarId(war.getId());
    raid.setBattleDay(war.getBattleDay());
    raid.setState(CampaignRaidState.MUSTER);
    raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
    raid.setMusterEndsAt(Instant.parse("2026-10-10T18:00:00Z"));
    war.setActiveCampaignRaid(raid);
    WarManager.addWar(war);
    return war;
  }

  private boolean run(String... args) {
    return commands.onCommand(alice, command, "raid", args);
  }

  private List<String> complete(String... args) {
    return tabs.onTabComplete(alice, command, "raid", args);
  }

  private long writes(War war) {
    return databases.constructed().stream()
        .flatMap(db -> mockingDetails(db).getInvocations().stream())
        .filter(call -> call.getMethod().getName().equals("saveWar") && call.getArgument(0) == war)
        .count();
  }

  @Test
  void uppercaseJoinCompletionsAreIndependentOfServerLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      raid("iron-port-raid");
      assertEquals(List.of("join"), complete("JOI"));
      assertEquals(List.of("iron-port-raid"), complete("join", "I"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void consoleAndInvalidArgumentsDoNotJoinOrPersistAnyRaid() {
    ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    assertTrue(
        commands.onCommand(console, command, "raid", new String[] {"join", "iron-port-raid"}));
    verifyNoInteractions(console);
    for (String[] args :
        List.of(
            new String[0],
            new String[] {"wrong"},
            new String[] {"join"},
            new String[] {"join", "id", "extra"})) {
      assertTrue(run(args));
    }
    verify(alice, times(4)).sendMessage("§a[Raid]§c Usage: /raid join <raid>");
    assertTrue(WarManager.get().isEmpty());
    assertTrue(WarbandManager.get().isEmpty());
  }

  @Test
  void nonFactionPlayersAndUnknownRaidIdsReceiveSpecificErrorsWithoutStateChanges() {
    Player visitor = fixture.player("Visitor");
    assertTrue(commands.onCommand(visitor, command, "raid", new String[] {"join", "missing"}));
    verify(visitor).sendMessage(CampaignRaidMessages.NOT_PARTICIPANT);
    assertTrue(run("join", "missing"));
    verify(alice).sendMessage(CampaignRaidMessages.RAID_NOT_FOUND);
    assertTrue(WarbandManager.get().isEmpty());
    assertTrue(WarManager.get().isEmpty());
  }

  @Test
  void successfulJoinAddsThePlayerExactlyOnceToTheRealRaidAndWarbandAndPersists() {
    War war = raid("iron-port-raid");
    long before = writes(war);
    assertTrue(run("JOIN", "IRON-PORT-RAID"));
    assertEquals(
        java.util.Set.of(alice.getUniqueId().toString()),
        war.getActiveCampaignRaid().getMusterParticipantIds());
    Warband band = CampaignRaidWarbandService.getAttackerWarband(war.getActiveCampaignRaid());
    assertNotNull(band);
    assertTrue(band.hasMember(alice));
    assertEquals(1, band.getRealMemberCount());
    assertSame(band, WarbandManager.getByPlayer(alice));
    assertEquals(before + 1, writes(war));
    verify(alice).sendMessage(CampaignRaidMessages.JOINED);
  }

  @Test
  void joiningAnExistingMusterAgainIsIdempotentAndReportsAlreadyJoined() {
    War war = raid("iron-port-raid");
    assertTrue(run("join", "iron-port-raid"));
    Warband band = WarbandManager.getByPlayer(alice);
    long before = writes(war);
    assertTrue(run("join", "iron-port-raid"));
    assertEquals(before, writes(war));
    assertSame(band, WarbandManager.getByPlayer(alice));
    assertEquals(1, band.getRealMemberCount());
    assertEquals(
        java.util.Set.of(alice.getUniqueId().toString()),
        war.getActiveCampaignRaid().getMusterParticipantIds());
    verify(alice).sendMessage(CampaignRaidMessages.ALREADY_JOINED);
    verify(alice, never()).sendMessage(CampaignRaidMessages.IN_WARBAND);
  }

  @ParameterizedTest
  @ValueSource(strings = {"fighting", "warband", "vehicle", "defender", "outsider"})
  void rejectedJoinLeavesRosterAndPersistenceUnchanged(String reason) {
    War war = raid("iron-port-raid");
    Player caller = alice;
    String message;
    switch (reason) {
      case "fighting" -> {
        war.getActiveCampaignRaid().setState(CampaignRaidState.FIGHTING);
        message = CampaignRaidMessages.NOT_MUSTER;
      }
      case "warband" -> {
        WarbandManager.addWarband(new Warband("existing", alice));
        message = CampaignRaidMessages.IN_WARBAND;
      }
      case "vehicle" -> {
        when(alice.isInsideVehicle()).thenReturn(true);
        message = CampaignRaidMessages.MOUNTED_ON_VEHICLE;
      }
      case "defender" -> {
        caller = fixture.player("Bob");
        message = CampaignRaidMessages.NOT_ATTACKER_COALITION;
      }
      case "outsider" -> {
        caller = fixture.player("Outsider");
        fixture.saved("outside", "Outsider");
        message = CampaignRaidMessages.NOT_PARTICIPANT;
      }
      default -> throw new AssertionError(reason);
    }
    List<Warband> beforeBands = new ArrayList<>(WarbandManager.get());
    long before = writes(war);
    assertTrue(
        commands.onCommand(caller, command, "raid", new String[] {"join", "iron-port-raid"}));
    verify(caller).sendMessage(message);
    assertTrue(war.getActiveCampaignRaid().getMusterParticipantIds().isEmpty());
    assertEquals(beforeBands, WarbandManager.get());
    assertEquals(before, writes(war));
  }

  @Test
  void completionsOnlyOfferMatchingActiveMustersForThePlayersCoalition() {
    raid("iron-port-raid");
    raid("coast-port-raid");
    raid("finished-raid").end(WarEndReason.ADMIN_END);
    raid("fighting-raid").getActiveCampaignRaid().setState(CampaignRaidState.FIGHTING);
    War defending = raid("enemy-raid");
    defending.getActiveCampaignRaid().setAttackerCoalition(CampaignCoalition.DEFENDER);
    assertEquals(List.of("join"), complete(""));
    assertTrue(complete("x").isEmpty());
    assertEquals(List.of("iron-port-raid", "coast-port-raid"), complete("join", ""));
    assertEquals(List.of("iron-port-raid"), complete("join", "iron"));
    assertTrue(complete("join", "missing").isEmpty());
    assertTrue(complete("other", "").isEmpty());
    assertTrue(complete("join", "iron", "extra").isEmpty());
    Player outsider = fixture.player("Outsider");
    assertTrue(tabs.onTabComplete(outsider, command, "raid", new String[] {"join", ""}).isEmpty());
    ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    assertTrue(tabs.onTabComplete(console, command, "raid", new String[] {""}).isEmpty());
    when(command.getName()).thenReturn("other");
    assertTrue(complete("").isEmpty());
  }
}
