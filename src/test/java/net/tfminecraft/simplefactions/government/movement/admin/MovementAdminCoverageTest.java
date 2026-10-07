package net.tfminecraft.simplefactions.government.movement.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.database.PoolData;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MovementAdminCoverageTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture disk;
  private Command command;

  @BeforeEach
  void setup() throws Exception {
    disk = new PersistenceFilesFixture();
    Files.createDirectories(disk.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    command = mock(Command.class);
    when(command.getName()).thenReturn("movement");
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (fixture != null) fixture.close();
    } finally {
      if (disk != null) disk.close();
    }
  }

  private Faction host() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    return faction;
  }

  private Movement political(Faction faction, Action action) {
    Proposal proposal = new Proposal("Bob", faction.getGovernment());
    proposal.setPoliticalActionProposal(new PoliticalAction(action));
    faction.getGovernment().startMovement("Bob", proposal);
    return faction.getGovernment().getMovementByLeader("Bob");
  }

  @Test
  void wantedLeaderCommandCannotReplaceAWarTargetWithAPlayerName() {
    Faction faction = host();
    Movement movement = political(faction, Action.WHITE_PEACE);
    Proposal proposal = movement.getCauses().getFirst().getProposal();
    proposal.setTarget("19");
    MovementAdminService.Result result = MovementAdminService.target(movement.getId(), "0", "Cara");
    assertFalse(result.ok());
    assertEquals("19", proposal.getTarget());
  }

  @Test
  void acceptingDemandsRevalidatesTargetsBeforeApplyingAnyCauseOrEndingTheMovement() {
    Faction faction = host();
    Movement movement = political(faction, Action.CHANGE_LEADER);
    movement.getCauses().getFirst().getProposal().setTarget("Cara");
    Proposal taxes = new Proposal("Cara", faction.getGovernment());
    taxes.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 17));
    movement.createCause("Cara", taxes);
    faction.forceRemoveMember("Cara");
    MovementAdminService.Result result = MovementAdminService.demands(movement.getId(), "accept");
    assertFalse(result.ok());
    assertTrue(faction.getGovernment().getMovements().contains(movement));
    assertEquals("Alice", faction.getLeader());
    assertEquals(5, faction.getTaxRate());
  }

  @Test
  void uppercaseAdminCommandsRemainCaseInsensitiveUnderTurkishLocale() {
    Faction faction = host();
    Movement movement = political(faction, Action.NONE);
    Player staff = fixture.player("Staff");
    when(staff.hasPermission("simplefactions.admin")).thenReturn(true);
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertTrue(
          new MovementCommandManager()
              .onCommand(
                  staff,
                  command,
                  "movement",
                  new String[] {
                    "ADMIN", "JOIN", movement.getId(), "SUPPORTER", "CITIZEN", "Cara"
                  }));
      assertTrue(movement.getSupporters().getCitizens().contains("Cara"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  private MovementData persistedMovement() throws Exception {
    FactionData data =
        new Gson()
            .fromJson(Files.readString(disk.root.resolve("Data/home.json")), FactionData.class);
    assertEquals(1, data.governmentData.movements.size());
    return data.governmentData.movements.getFirst();
  }

  private Player staff() {
    Player player = fixture.player("Staff");
    when(player.hasPermission("simplefactions.admin")).thenReturn(true);
    return player;
  }

  private void execute(Player player, String... args) {
    assertTrue(new MovementCommandManager().onCommand(player, command, "movement", args));
  }

  private List<String> complete(Player player, String... args) {
    return new MovementTabCompletion().onTabComplete(player, command, "movement", args);
  }

  @ParameterizedTest
  @CsvSource({
    "supporter,citizen",
    "cause,citizen",
    "supporter,guild",
    "cause,guild",
    "supporter,vassal",
    "cause,vassal"
  })
  void commandsAddAndRemoveOnlyTheSelectedMemberSlotAndPersistIt(String slot, String type)
      throws Exception {
    Faction faction = host();
    Movement movement = political(faction, Action.NONE);
    String target = "Cara";
    if (type.equals("guild")) {
      Guild guild = fixture.guild(faction, "merchants", "Eve");
      guild.setStance(Stance.OPPOSE);
      target = guild.getId();
    } else if (type.equals("vassal")) {
      Faction subject = fixture.saved("subject", "Eve");
      fixture.subject(faction, subject);
      subject.getOrCreateMainGuild().setStance(Stance.OPPOSE);
      target = subject.getId();
    }
    Player staff = staff();
    List<String> args = new ArrayList<>(List.of("admin", "join", movement.getId(), slot));
    if (slot.equals("cause")) args.add("0");
    args.add(type);
    args.add(target);
    execute(staff, args.toArray(String[]::new));
    var pool =
        slot.equals("cause") ? movement.getCauses().getFirst().getPool() : movement.getSupporters();
    assertEquals(slot.equals("cause") ? 2 : 1, pool.getAllMembers().size());
    MovementData stored = persistedMovement();
    PoolData storedPool =
        slot.equals("cause") ? stored.causes.getFirst().members : stored.supporters;
    List<String> storedMembers =
        switch (type) {
          case "guild" -> storedPool.guilds;
          case "vassal" -> storedPool.factions;
          default -> storedPool.citizens;
        };
    assertTrue(storedMembers.contains(target));
    assertEquals(2, movement.getAllMembers().size());
    args.set(1, "leave");
    execute(staff, args.toArray(String[]::new));
    assertEquals(slot.equals("cause") ? 1 : 0, pool.getAllMembers().size());
    assertEquals(List.of("Bob"), movement.getAllMembers());
    MovementData afterLeave = persistedMovement();
    PoolData remaining =
        slot.equals("cause") ? afterLeave.causes.getFirst().members : afterLeave.supporters;
    assertFalse(remaining.citizens.contains(target));
    assertFalse(remaining.guilds.contains(target));
    assertFalse(remaining.factions.contains(target));
    verify(staff).sendMessage(contains("Added " + type));
    verify(staff).sendMessage(contains("Removed " + type));
  }

  @Test
  void commandBackerLifecycleRejectsDuplicateSameRealmFrozenAndAbsentBacking() {
    Faction faction = host();
    Movement movement = political(faction, Action.NONE);
    Faction foreign = fixture.saved("foreign", "Drew");
    Player staff = staff();
    execute(staff, "admin", "join", movement.getId(), "backer", foreign.getId());
    assertEquals(List.of(foreign), movement.getForeignBackers());
    assertFalse(MovementAdminService.joinBacker(movement.getId(), foreign.getId()).ok());
    execute(staff, "admin", "leave", movement.getId(), "backer", foreign.getId());
    assertTrue(movement.getForeignBackers().isEmpty());
    assertFalse(MovementAdminService.leaveBacker(movement.getId(), foreign.getId()).ok());
    assertFalse(MovementAdminService.joinBacker(movement.getId(), faction.getId()).ok());
    movement.setFrozen(true);
    assertFalse(MovementAdminService.joinBacker(movement.getId(), foreign.getId()).ok());
    assertTrue(movement.getForeignBackers().isEmpty());
    assertEquals(
        MovementAdminService.JOIN_USAGE,
        MovementAdminService.joinBacker(movement.getId(), null).message());
    assertEquals(
        MovementAdminService.LEAVE_USAGE,
        MovementAdminService.leaveBacker(movement.getId(), " ").message());
    assertFalse(MovementAdminService.joinBacker(movement.getId(), "missing").ok());
    assertFalse(MovementAdminService.joinBacker("missing", foreign.getId()).ok());
  }

  @Test
  void
      movementMemberMutationsRejectStaleIdsInvalidTypesAndDifferentSlotsWithoutChangingMembership() {
    Movement movement = political(host(), Action.NONE);
    assertFalse(MovementAdminService.joinSupporter(null, "citizen", "Cara").ok());
    assertFalse(MovementAdminService.joinSupporter(" ", "citizen", "Cara").ok());
    assertFalse(MovementAdminService.joinSupporter("missing", "citizen", "Cara").ok());
    for (String index : List.of("-1", "1", "bad")) {
      assertFalse(MovementAdminService.joinCause(movement.getId(), index, "citizen", "Cara").ok());
    }
    assertFalse(MovementAdminService.joinSupporter(movement.getId(), null, "Cara").ok());
    assertFalse(MovementAdminService.joinSupporter(movement.getId(), "citizen", null).ok());
    assertFalse(MovementAdminService.joinSupporter(movement.getId(), "citizen", " ").ok());
    for (String kind : List.of("bad", "guild", "vassal")) {
      assertFalse(MovementAdminService.joinSupporter(movement.getId(), kind, "missing").ok());
    }
    assertFalse(MovementAdminService.joinSupporter(movement.getId(), "citizen", "Alice").ok());
    assertFalse(MovementAdminService.leaveSupporter(movement.getId(), "citizen", "Cara").ok());
    assertEquals(
        "§cCould not add: join did not stick.",
        MovementAdminService.joinSupporter(movement.getId(), "citizen", "Bob").message());
    assertTrue(MovementAdminService.joinSupporter(movement.getId(), "citizen", "Cara").ok());
    assertFalse(MovementAdminService.joinSupporter(movement.getId(), "citizen", "Cara").ok());
    assertFalse(MovementAdminService.joinCause(movement.getId(), "0", "citizen", "Cara").ok());
    assertEquals(List.of("Cara"), movement.getSupporters().getCitizens());
    movement.setFrozen(true);
    assertFalse(MovementAdminService.joinCause(movement.getId(), "0", "citizen", "Cara").ok());
    assertEquals(List.of("Bob"), movement.getCauses().getFirst().getPool().getCitizens());
  }

  @Test
  void commandPermissionsAndIncompleteArgumentsReturnTheCorrectUsageWithoutMutating() {
    Movement movement = political(host(), Action.NONE);
    CommandSender console = mock(CommandSender.class);
    assertTrue(
        new MovementCommandManager()
            .onCommand(console, command, "movement", new String[] {"admin", "list"}));
    verifyNoInteractions(console);
    Player denied = fixture.player("Guest");
    execute(denied, "admin", "list");
    verify(denied).sendMessage(contains("do not have access"));
    Player staff = staff();
    for (String[] args : new String[][] {{}, {"other"}, {"admin"}, {"admin", "unknown"}}) {
      clearInvocations(staff);
      execute(staff, args);
      verify(staff).sendMessage(MovementAdminService.USAGE);
    }
    for (String action : List.of("join", "leave")) {
      String usage =
          action.equals("join")
              ? MovementAdminService.JOIN_USAGE
              : MovementAdminService.LEAVE_USAGE;
      for (String[] args :
          new String[][] {
            {"admin", action},
            {"admin", action, movement.getId(), "backer"},
            {"admin", action, movement.getId(), "supporter", "citizen"},
            {"admin", action, movement.getId(), "cause", "0", "citizen"},
            {"admin", action, movement.getId(), "invalid"}
          }) {
        clearInvocations(staff);
        execute(staff, args);
        verify(staff).sendMessage(usage);
      }
    }
    execute(staff, "admin", "demands");
    verify(staff).sendMessage(MovementAdminService.DEMANDS_USAGE);
    execute(staff, "admin", "target", movement.getId());
    verify(staff).sendMessage(MovementAdminService.TARGET_USAGE);
    assertEquals(List.of("Bob"), movement.getAllMembers());
  }

  @Test
  void listsDescribeLiveMovementsAndLookupCollectionsAreIndependentCopies() {
    assertEquals(List.of("§7No movements."), MovementAdminService.listLines());
    assertTrue(MovementAdminService.allMovementIds().isEmpty());
    Faction faction = host();
    assertEquals(List.of("§7No movements."), MovementAdminService.listLines());
    Movement movement = political(faction, Action.NONE);
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    Faction subject = fixture.saved("subject", "Finn");
    fixture.subject(faction, subject);
    Faction foreign = fixture.saved("foreign", "Drew");
    movement.join("Cara", null);
    movement.joinAsForeignBacker(foreign);
    assertEquals(List.of(movement.getId()), MovementAdminService.allMovementIds());
    List<String> citizens = MovementAdminService.hostCitizenNames(movement);
    assertTrue(citizens.containsAll(List.of("Alice", "Bob", "Cara")));
    citizens.clear();
    assertTrue(faction.getMembers().contains("Cara"));
    assertEquals(List.of(guild.getId()), MovementAdminService.hostGuildIds(movement));
    assertEquals(List.of(subject.getId()), MovementAdminService.hostSubjectIds(movement));
    assertEquals(
        List.of(subject.getId(), foreign.getId()), MovementAdminService.otherFactionIds(movement));
    assertTrue(
        MovementAdminService.wantedLeaderNames(movement).containsAll(List.of("Bob", "Cara")));
    assertTrue(MovementAdminService.wantedLeaderNames(null).isEmpty());
    assertEquals(List.of("0"), MovementAdminService.causeIndices(movement));
    String row = MovementAdminService.listLines().getFirst();
    for (String part :
        List.of(
            movement.getId(),
            "home",
            "Bob",
            "GATHERING",
            "false",
            "causes: §f1",
            "supporters: §f1",
            "backers: §f1")) assertTrue(row.contains(part), row);
    Player staff = staff();
    execute(staff, "admin", "list");
    verify(staff).sendMessage(row);
    movement.setLeader(null);
    assertTrue(MovementAdminService.listLines().getFirst().contains("leader: §fNone"));
  }

  @Test
  void settingLeaderTargetValidatesTheLiveCauseAndPersistsTheAcceptedName() throws Exception {
    Faction faction = host();
    Movement movement = political(faction, Action.CHANGE_LEADER);
    assertFalse(MovementAdminService.target("missing", "0", "Cara").ok());
    assertFalse(MovementAdminService.target(movement.getId(), "bad", "Cara").ok());
    assertFalse(MovementAdminService.target(movement.getId(), "0", null).ok());
    assertFalse(MovementAdminService.target(movement.getId(), "0", " ").ok());
    assertFalse(MovementAdminService.target(movement.getId(), "0", "Stranger").ok());
    movement.setFrozen(true);
    assertFalse(MovementAdminService.target(movement.getId(), "0", "Cara").ok());
    movement.setFrozen(false);
    Player staff = staff();
    execute(staff, "admin", "target", movement.getId(), "0", "Cara");
    assertEquals("Cara", movement.getCauses().getFirst().getProposal().getTarget());
    assertEquals("Cara", persistedMovement().causes.getFirst().proposal.target);
    verify(staff).sendMessage(contains("Set wanted leader"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void acceptingDemandsSupportsBothCommandFormsAndAppliesValidLeaderChange(boolean legacy) {
    Faction faction = host();
    Movement movement = political(faction, Action.CHANGE_LEADER);
    movement.getCauses().getFirst().getProposal().setTarget("Cara");
    Player staff = staff();
    if (legacy) execute(staff, "admin", movement.getId(), "demands", "accept");
    else execute(staff, "admin", "demands", movement.getId(), "accept");
    assertEquals("Cara", faction.getLeader());
    assertTrue(faction.getGovernment().getMovements().isEmpty());
    verify(staff).sendMessage(contains("Accepted demands"));
  }

  @Test
  void demandsRejectInvalidInputAndUnstartableCivilWarWithoutEndingTheMovement() {
    Faction faction = host();
    Movement movement = political(faction, Action.CHANGE_LEADER);
    assertFalse(MovementAdminService.demands("missing", "accept").ok());
    assertEquals(
        MovementAdminService.DEMANDS_USAGE,
        MovementAdminService.demands(movement.getId(), null).message());
    assertFalse(MovementAdminService.demands(movement.getId(), "accept").ok());
    movement.getCauses().getFirst().getProposal().setTarget("Cara");
    assertEquals(
        MovementAdminService.DEMANDS_USAGE,
        MovementAdminService.demands(movement.getId(), "invalid").message());
    for (String outcome : List.of("reject", "decline")) {
      assertFalse(MovementAdminService.demands(movement.getId(), outcome).ok());
      assertTrue(faction.getGovernment().getMovements().contains(movement));
      assertFalse(movement.isFrozen());
    }
  }

  @Test
  void completionFollowsEachCommandShapeAndRealFactionMembership() {
    Faction faction = host();
    Movement movement = political(faction, Action.CHANGE_LEADER);
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    Faction subject = fixture.saved("subject", "Finn");
    fixture.subject(faction, subject);
    Player staff = staff();
    assertEquals(List.of("admin"), complete(staff, "A"));
    assertEquals(List.of("list", "leave"), complete(staff, "admin", "l"));
    assertTrue(complete(staff, "other", "").isEmpty());
    assertTrue(complete(fixture.player("Guest"), "").isEmpty());
    when(command.getName()).thenReturn("other");
    assertTrue(complete(staff, "").isEmpty());
    when(command.getName()).thenReturn("movement");
    for (String action : List.of("target", "demands", "join", "leave")) {
      assertEquals(List.of(movement.getId()), complete(staff, "admin", action, ""));
      assertTrue(complete(staff, "admin", action, "missing").isEmpty());
    }
    assertEquals(List.of("0"), complete(staff, "admin", "target", movement.getId(), ""));
    assertEquals(List.of("Cara"), complete(staff, "admin", "target", movement.getId(), "0", "C"));
    assertTrue(complete(staff, "admin", "target", "missing", "").isEmpty());
    assertTrue(complete(staff, "admin", "target", movement.getId(), "0", "Cara", "").isEmpty());
    assertEquals(
        List.of("accept", "reject"), complete(staff, "admin", "demands", movement.getId(), ""));
    assertTrue(complete(staff, "admin", "demands", movement.getId(), "accept", "").isEmpty());
    assertEquals(List.of("demands"), complete(staff, "admin", movement.getId(), "d"));
    assertEquals(List.of("reject"), complete(staff, "admin", movement.getId(), "demands", "r"));
    assertTrue(complete(staff, "admin", movement.getId(), "bad", "").isEmpty());
    for (String action : List.of("join", "leave")) {
      assertEquals(
          List.of("supporter", "cause", "backer"),
          complete(staff, "admin", action, movement.getId(), ""));
      assertTrue(complete(staff, "admin", action, "missing", "backer", "").isEmpty());
      assertEquals(
          List.of(subject.getId()),
          complete(staff, "admin", action, movement.getId(), "backer", ""));
      assertTrue(
          complete(staff, "admin", action, movement.getId(), "backer", "subject", "").isEmpty());
      assertEquals(
          List.of("citizen", "guild", "vassal"),
          complete(staff, "admin", action, movement.getId(), "supporter", ""));
      assertEquals(
          List.of("Cara"),
          complete(staff, "admin", action, movement.getId(), "supporter", "citizen", "C"));
      assertEquals(
          List.of(guild.getId()),
          complete(staff, "admin", action, movement.getId(), "supporter", "guild", ""));
      assertEquals(
          List.of(subject.getId()),
          complete(staff, "admin", action, movement.getId(), "supporter", "vassal", ""));
      assertTrue(
          complete(staff, "admin", action, movement.getId(), "supporter", "invalid", "").isEmpty());
      assertTrue(
          complete(staff, "admin", action, movement.getId(), "supporter", "citizen", "Cara", "")
              .isEmpty());
      assertEquals(List.of("0"), complete(staff, "admin", action, movement.getId(), "cause", ""));
      assertEquals(
          List.of("citizen", "guild", "vassal"),
          complete(staff, "admin", action, movement.getId(), "cause", "0", ""));
      assertEquals(
          List.of("Cara"),
          complete(staff, "admin", action, movement.getId(), "cause", "0", "citizen", "C"));
      assertTrue(
          complete(staff, "admin", action, movement.getId(), "cause", "0", "citizen", "Cara", "")
              .isEmpty());
      assertTrue(complete(staff, "admin", action, movement.getId(), "unknown", "").isEmpty());
    }
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals(List.of("join"), complete(staff, "ADMIN", "J"));
      assertEquals(
          List.of("Cara"),
          complete(staff, "ADMIN", "JOIN", movement.getId(), "SUPPORTER", "CITIZEN", "C"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void publicRegistryQueriesSkipMissingEntriesWithoutHidingLiveMovementsOrForeignFactions() {
    Faction faction = host();
    Movement movement = political(faction, Action.NONE);
    Faction foreign = fixture.saved("foreign", "Drew");
    FactionManager.factions.addFirst(null);
    faction.getGovernment().getMovements().addFirst(null);
    assertEquals(List.of(movement.getId()), MovementAdminService.allMovementIds());
    assertEquals(1, MovementAdminService.listLines().size());
    assertTrue(MovementAdminService.listLines().getFirst().contains(movement.getId()));
    assertEquals(List.of(foreign.getId()), MovementAdminService.otherFactionIds(movement));
    List<Faction> registry = FactionManager.factions;
    try {
      FactionManager.factions = null;
      assertEquals(List.of("§7No movements."), MovementAdminService.listLines());
      assertTrue(MovementAdminService.allMovementIds().isEmpty());
      assertTrue(MovementAdminService.otherFactionIds(movement).isEmpty());
    } finally {
      FactionManager.factions = registry;
    }
  }

  @Test
  void incompletePublicCauseEntriesDoNotHideAnotherCausesMissingLeaderTarget() {
    Faction faction = host();
    Movement movement = political(faction, Action.CHANGE_LEADER);
    movement.getCauses().addFirst(null);
    MovementAdminService.Result result = MovementAdminService.demands(movement.getId(), "accept");
    assertFalse(result.ok());
    assertEquals("§cOne or more causes lack a target.", result.message());
    assertTrue(faction.getGovernment().getMovements().contains(movement));
    assertEquals("Alice", faction.getLeader());
  }

  @Test
  void baseGuildRegisteredThroughThePublicHandlerIsExcludedFromDomesticGuildChoices() {
    Faction faction = host();
    Movement movement = political(faction, Action.NONE);
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    faction.getGuildHandler().addGuild(faction.getOrCreateMainGuild());
    assertEquals(List.of(guild.getId()), MovementAdminService.hostGuildIds(movement));
    assertTrue(faction.getGuildHandler().getGuilds().contains(faction.getOrCreateMainGuild()));
  }

  @Test
  void importedMovementWithoutAnAttachedHostHasNoHostSpecificChoices() {
    Faction faction = host();
    Movement movement = new Movement(null, new MovementData());
    assertNull(movement.getFaction());
    assertTrue(MovementAdminService.hostCitizenNames(movement).isEmpty());
    assertTrue(MovementAdminService.hostGuildIds(movement).isEmpty());
    assertTrue(MovementAdminService.hostSubjectIds(movement).isEmpty());
    assertTrue(MovementAdminService.wantedLeaderNames(movement).isEmpty());
    assertTrue(MovementAdminService.causeIndices(movement).isEmpty());
    assertEquals(List.of(faction.getId()), MovementAdminService.otherFactionIds(movement));
  }

  @Test
  void selectionListsOmitImportedEntriesWithoutIdsWhileKeepingUsableNeighbors() {
    Faction faction = host();
    Movement movement = political(faction, Action.NONE);
    Guild usableGuild = fixture.guild(faction, "merchants", "Eve");
    Guild incompleteGuild = fixture.guild(faction, null, "Finn");
    Faction usableFaction = fixture.saved("foreign", "Grace");
    Faction incompleteFaction = fixture.saved("importing", "Hank");
    incompleteFaction.setId(null);
    assertNull(incompleteGuild.getId());
    assertEquals(List.of(usableGuild.getId()), MovementAdminService.hostGuildIds(movement));
    assertEquals(List.of(usableFaction.getId()), MovementAdminService.otherFactionIds(movement));
    assertTrue(faction.getGuildHandler().getGuilds().contains(incompleteGuild));
    assertTrue(FactionManager.factions.contains(incompleteFaction));
  }
}
