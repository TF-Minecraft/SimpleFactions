package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.government.stability.StabilityReport;
import net.tfminecraft.simplefactions.government.stability.StabilityStatus;
import net.tfminecraft.simplefactions.government.stability.StateStability;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.tiers.admin.TitleAdminCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Real realm/title state with only Paper scheduling and title-file persistence isolated. */
class TitleManagementCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Player player;
  private Map<Player, Tier> previousPending;
  private MockedStatic<TitleLoader> persistence;
  private final List<Title> created = new ArrayList<>();
  private final TitleManager manager = new TitleManager();

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    faction = fixture.saved("realm", "Leader");
    faction.getOrCreateMainGuild();
    faction.addProvince(10);
    player = fixture.player("Leader");
    previousPending = TitleManager.isFormingTitle;
    TitleManager.isFormingTitle = new HashMap<>();
    List<Title> registry = TitleLoader.getTitles();
    persistence = mockStatic(TitleLoader.class);
    persistence.when(TitleLoader::getTitles).thenReturn(registry);
    persistence.when(() -> TitleLoader.getById(any())).thenCallRealMethod();
    persistence.when(() -> TitleLoader.getByProvince(anyInt())).thenCallRealMethod();
    persistence.when(() -> TitleLoader.getByTitle(any())).thenCallRealMethod();
    persistence.when(() -> TitleLoader.getByTier(any())).thenCallRealMethod();
    persistence.when(() -> TitleLoader.saveTitle(any())).thenReturn(true);
    persistence
        .when(
            () ->
                TitleLoader.createNewTitle(
                    any(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyList(),
                    anyList(),
                    anyBoolean()))
        .thenAnswer(
            call -> {
              Tier tier = call.getArgument(0);
              JsonObject json = new JsonObject();
              json.addProperty("name", (String) call.getArgument(2));
              json.addProperty("rgb", (String) call.getArgument(3));
              List<Integer> provinces = call.getArgument(4);
              List<String> children = call.getArgument(5);
              JsonArray parts = new JsonArray();
              if (tier.getTier() == 2) {
                provinces.forEach(parts::add);
                json.add("provinces", parts);
              } else {
                children.forEach(parts::add);
                json.add("titles", parts);
              }
              Title title = new Title(tier, call.getArgument(1), json);
              registry.add(title);
              created.add(title);
              return title;
            });
  }

  @AfterEach
  void close() {
    try {
      persistence.close();
      TitleManager.isFormingTitle = previousPending;
    } finally {
      fixture.close();
    }
  }

  @Test
  void asynchronousTitleChatDefersAllGameStateChangesUntilTheMainThread() {
    Tier county = tier("county", 2, 1);
    TitleManager.isFormingTitle.put(player, county);
    AsyncPlayerChatEvent event = chat("North Vale");
    manager.formTitle(event);
    assertTrue(event.isCancelled());
    assertTrue(created.isEmpty(), "Async chat must not persist or claim a title");
    assertTrue(faction.getTitles().isEmpty());
    fixture.ui.runTasks();
    assertEquals(1, created.size());
    Title title = created.getFirst();
    assertEquals("North_Vale", title.getId());
    assertEquals("North Vale", title.getName());
    assertEquals(List.of(10), title.getProvinces());
    assertTrue(faction.hasTitle(title));
    verify(fixture.map).enqueue("county", title.getRgb());
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
  }

  @ParameterizedTest
  @ValueSource(strings = {"leader", "land", "liege", "members", "disabled", "removed_tier"})
  void titleRequirementsAreRevalidatedWhenTheNameArrives(String change) {
    Tier county = tier("county", 2, 1);
    TitleManager.isFormingTitle.put(player, county);
    switch (change) {
      case "leader" -> {
        faction.addMember("Successor");
        faction.setLeader("Successor");
      }
      case "land" -> faction.removeProvince(10, false);
      case "liege" -> {
        Faction liege = fixture.saved("liege", "Liege");
        liege.addProvince(20);
        fixture.subject(liege, faction);
      }
      case "members" -> {
        faction.addTitle(fixture.title("existing", "county", 10));
        faction.addProvince(11);
      }
      case "disabled" -> tier("county", 2, 0);
      case "removed_tier" -> TierLoader.oList.remove(county);
    }
    AsyncPlayerChatEvent event = chat("Stale Title");
    manager.formTitle(event);
    fixture.ui.runTasks();
    assertTrue(event.isCancelled());
    assertTrue(created.isEmpty(), "Changed " + change + " must prevent creation");
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
    verify(fixture.map, never()).enqueue(eq("county"), anyString());
  }

  @Test
  void aGovernmentThatCollapsesBeforeTheNameArrivesCannotCreateATitle() {
    TitleManager.isFormingTitle.put(player, tier("county", 2, 1));
    StabilityReport report = new StabilityReport();
    report.status = StabilityStatus.COLLAPSING;
    report.stability = 5;
    try (MockedStatic<StateStability> state = mockStatic(StateStability.class)) {
      state.when(() -> StateStability.of(faction)).thenReturn(report);
      manager.formTitle(chat("Collapsing County"));
      fixture.ui.runTasks();
      assertTrue(created.isEmpty());
      assertTrue(faction.getTitles().isEmpty());
      assertFalse(TitleManager.isFormingTitle.containsKey(player));
    }
  }

  @Test
  void realmUntitledProvincesCountEverySubjectProvinceOnce() {
    Faction subject = fixture.saved("subject", "Subject");
    Faction nested = fixture.saved("nested", "Nested");
    subject.addProvince(20);
    nested.addProvince(30);
    fixture.subject(faction, subject);
    fixture.subject(subject, nested);
    assertEquals(List.of(10, 20, 30), TitleManager.getAllUntitledProvinces(faction));
  }

  @Test
  void findingAParentSkipsMissingChildrenInPersistedTitleDefinitions() {
    Title child = fixture.title("child", "county", 10);
    Title parent = composite("parent", "duchy", "deleted_child", "child");
    assertSame(parent, TitleManager.getParent(child));
    assertNull(TitleManager.getParent(parent));
  }

  @Test
  void chatWithoutAPendingTitleRemainsPublicAndDoesNotScheduleWork() {
    AsyncPlayerChatEvent event = chat("hello everyone");
    manager.formTitle(event);
    assertFalse(event.isCancelled());
    assertTrue(fixture.ui.tasks.isEmpty());
    assertTrue(created.isEmpty());
  }

  @Test
  void duplicateIdsAreRejectedWithoutChangingTheExistingTitle() {
    Title existing = fixture.title("north_vale", "county", 90);
    TitleManager.isFormingTitle.put(player, tier("county", 2, 1));
    manager.formTitle(chat("North Vale"));
    fixture.ui.runTasks();
    assertTrue(created.isEmpty());
    assertEquals(List.of(90), existing.getProvinces());
    assertTrue(faction.getTitles().isEmpty());
    verify(player).sendMessage("§cA title with that ID already exists!");
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
  }

  @Test
  void failureToPersistTheTitleDoesNotClaimItOrQueueTheMap() {
    persistence
        .when(
            () ->
                TitleLoader.createNewTitle(
                    any(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyList(),
                    anyList(),
                    anyBoolean()))
        .thenReturn(null);
    TitleManager.isFormingTitle.put(player, tier("county", 2, 1));
    manager.formTitle(chat("North Vale"));
    fixture.ui.runTasks();
    assertTrue(faction.getTitles().isEmpty());
    assertTrue(created.isEmpty());
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
    verify(fixture.map, never()).enqueue(eq("county"), anyString());
  }

  @Test
  void higherTitleFormationUsesTheRealFreeLowerTitles() {
    faction.addMember("Member");
    faction.addProvince(11);
    Title north = fixture.title("north", "county", 10);
    Title south = fixture.title("south", "county", 11);
    faction.addTitle(north);
    faction.addTitle(south);
    TitleManager.isFormingTitle.put(player, tier("duchy", 3, 2));
    manager.formTitle(chat("River Duchy"));
    fixture.ui.runTasks();
    assertEquals(1, created.size());
    Title duchy = created.getFirst();
    assertEquals(List.of("north", "south"), duchy.getTitles());
    assertTrue(faction.hasTitle(duchy));
    assertEquals(List.of(10, 11), TitleManager.getProvinces(duchy));
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
  }

  @Test
  void realmQueriesIncludeNestedSubjectsAndKeepTitleAndProvinceOwnershipSeparate() {
    Faction subject = fixture.saved("subject", "Subject");
    Faction nested = fixture.saved("nested", "Nested");
    subject.addProvince(20);
    nested.addProvince(30);
    fixture.subject(faction, subject);
    fixture.subject(subject, nested);
    Title home = fixture.title("home", "county", 10);
    Title vassal = fixture.title("vassal", "county", 20);
    faction.addTitle(home);
    subject.addTitle(vassal);
    Title vacant = fixture.title("vacant", "county", 40);
    assertEquals(3, TitleManager.getRealmSize(faction));
    assertEquals(List.of(10, 20, 30), TitleManager.getProvinces(faction));
    assertEquals(List.of(home, vassal), TitleManager.getTitles(faction));
    assertEquals(List.of(30), TitleManager.getAllUntitledProvinces(faction));
    assertSame(subject, TitleManager.getByProvince(20));
    assertNull(TitleManager.getByProvince(99));
    assertTrue(TitleManager.titleIsInRealm(faction, "VASSAL"));
    assertFalse(TitleManager.titleIsInRealm(faction, "vacant"));
    assertSame(faction, TitleManager.getOwner(home));
    assertNull(TitleManager.getOwner(vacant));
    assertEquals(List.of(home, vassal), TitleManager.getAllOwnedTitles());
    assertEquals(List.of(vacant), TitleManager.getAllUnownedTitles());
    int oldCost = Cache.provinceCost;
    try {
      Cache.provinceCost = 1000000;
      assertEquals(1000000, TitleManager.getClaimCost(faction));
      assertFalse(TitleManager.overProvinceCap(faction));
      faction.addProvince(11);
      assertTrue(TitleManager.overProvinceCap(faction));
      assertEquals(2000000, TitleManager.getClaimCost(faction));
    } finally {
      Cache.provinceCost = oldCost;
    }
  }

  @Test
  void grantableTitlesRequireSpareTitlesOfTheRequestedTierAndReceiverLand() {
    Faction receiver = fixture.saved("receiver", "Receiver");
    receiver.addProvince(20);
    Title home = fixture.title("home", "county", 10);
    Title spare = fixture.title("spare", "county", 20);
    faction.addTitle(home);
    faction.addMember("Member");
    faction.addTitle(spare);
    Tier county = TierLoader.getByString("county");
    assertEquals(List.of(spare), TitleManager.getGrantableTitles(faction, receiver, county));
    assertTrue(
        TitleManager.getGrantableTitles(faction, receiver, TierLoader.getByString("duchy"))
            .isEmpty());
    faction.removeTitle(home);
    assertTrue(TitleManager.getGrantableTitles(faction, receiver, county).isEmpty());
  }

  @Test
  void administratorsMustHavePermissionAndTheProvinceFeatureEnabled() {
    CommandSender sender = mock(CommandSender.class);
    assertTrue(TitleAdminCommand.handle(sender, new String[] {"title", "list"}));
    verify(sender).sendMessage("§a[SimpleFactions]§c You do not have access to this command");
    when(sender.hasPermission("simplefactions.admin")).thenReturn(true);
    clearInvocations(sender);
    assertTrue(TitleAdminCommand.handle(sender, new String[] {"title", "list"}));
    assertTrue(messages(sender).stream().anyMatch(m -> m.toLowerCase().contains("disabled")));
    assertTrue(TitleLoader.getTitles().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "info",
        "where",
        "rename",
        "setcolour",
        "addprovince",
        "removeprovince",
        "addtitle",
        "removetitle",
        "setcomplete"
      })
  void incompleteAdminCommandsPrintTheirSpecificUsageWithoutChangingTitles(String sub) {
    CommandSender sender = admin();
    Title title = fixture.title("home", "county", 10);
    assertTrue(TitleAdminCommand.handle(sender, new String[] {"title", sub}));
    assertTrue(
        messages(sender).stream().anyMatch(m -> m.startsWith("§6/faction title " + sub + " ")));
    assertEquals("home", title.getName());
    persistence.verify(() -> TitleLoader.saveTitle(any()), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"short", "unknown"})
  void generalAdminUsageListsAllSupportedCommands(String shape) {
    CommandSender sender = admin();
    String[] args =
        shape.equals("short") ? new String[] {"title"} : new String[] {"title", "unsupported"};
    assertTrue(TitleAdminCommand.handle(sender, args));
    ArgumentCaptor<String[]> usage = ArgumentCaptor.forClass(String[].class);
    verify(sender).sendMessage(usage.capture());
    assertEquals(TitleAdminCommand.SUBCOMMANDS.size(), usage.getValue().length);
    assertTrue(usage.getValue()[0].contains("title list"));
  }

  @Test
  void adminListAndInfoDescribePartsParentsHoldersAndMissingDefinitions() {
    CommandSender sender = admin();
    Title county = fixture.title("north", "county", 10);
    faction.addTitle(county);
    Title duchy = composite("river", "duchy", "north", "missing");
    command(sender, "list");
    assertTrue(
        messages(sender).stream()
            .anyMatch(
                m ->
                    m.contains("north")
                        && m.contains("1 provinces")
                        && m.contains("held by realm")));
    assertTrue(
        messages(sender).stream().anyMatch(m -> m.contains("river") && m.contains("2 titles")));
    clearInvocations(sender);
    command(sender, "list", "county");
    assertTrue(messages(sender).stream().anyMatch(m -> m.contains("north")));
    assertFalse(messages(sender).stream().anyMatch(m -> m.contains("river")));
    clearInvocations(sender);
    command(sender, "list", "missing");
    verify(sender).sendMessage("§a[SimpleFactions]§c No tier with the id missing");
    command(sender, "info", "missing");
    verify(sender).sendMessage("§a[SimpleFactions]§c No title with the id missing");
    command(sender, "info", county.getId());
    verify(sender).sendMessage("§7Provinces: §f[10]");
    verify(sender).sendMessage("§7Part of: §friver (river)");
    verify(sender).sendMessage("§7Holder: §frealm");
    command(sender, "info", duchy.getId());
    verify(sender).sendMessage(contains("missing (missing)"));
    verify(sender).sendMessage("§7All provinces: §f[10]");
    verify(sender).sendMessage("§7Part of: §fnone");
    verify(sender).sendMessage("§7Holder: §fnone");
  }

  @Test
  void adminWhereExplainsTheProvinceChainAndRejectsUnknownInputs() {
    CommandSender sender = admin();
    when(fixture.provinces.contains(10)).thenReturn(true);
    when(fixture.provinces.contains(90)).thenReturn(true);
    fixture.title("north", "county", 10);
    composite("river", "duchy", "north");
    command(sender, "where", "ten");
    verify(sender).sendMessage("§a[SimpleFactions]§c Province ids must be numbers");
    command(sender, "where", "99");
    verify(sender).sendMessage("§a[SimpleFactions]§c No province with the id 99");
    command(sender, "where", "10");
    verify(sender).sendMessage("§7Province §f10§7: §fnorth (north) §7→ §friver (river)");
    verify(sender).sendMessage("§7Controlled by: §frealm");
    command(sender, "where", "90");
    verify(sender).sendMessage("§7Province §f90§7: §funtitled");
    verify(sender).sendMessage("§7Controlled by: §fnobody");
  }

  @Test
  void adminRenameAndColourChangesPersistAndScheduleTheAffectedMapColours() {
    CommandSender sender = admin();
    Title title = fixture.title("north", "county", 10);
    faction.addTitle(title);
    command(sender, "rename", "north", "North", "Vale");
    assertEquals("North Vale", title.getName());
    persistence.verify(() -> TitleLoader.saveTitle(title));
    verify(sender).sendMessage(contains("is held by realm"));
    command(sender, "setcolor", "north", "7,", "8,", "9");
    assertEquals("7,8,9", title.getRgb());
    verify(fixture.map, atLeastOnce()).enqueue("county", "4,5,6");
    verify(fixture.map, atLeastOnce()).enqueue("county", "7,8,9");
    verify(sender, atLeastOnce()).sendMessage("§7The web map updates on its next cycle.");
    command(sender, "setcolour", "north", "10,11,12");
    assertEquals("10,11,12", title.getRgb());
  }

  @Test
  void adminProvinceEditsPreserveTitleOwnershipAndExplainMissingLand() {
    CommandSender sender = admin();
    Title title = fixture.title("north", "county", 10);
    faction.addTitle(title);
    when(fixture.provinces.contains(11)).thenReturn(true);
    command(sender, "addprovince", "north", "11");
    assertEquals(List.of(10, 11), title.getProvinces());
    assertTrue(faction.hasTitle(title));
    command(sender, "removeprovince", "north", "10");
    assertEquals(List.of(11), title.getProvinces());
    assertTrue(faction.hasTitle(title));
    verify(sender).sendMessage(startsWith("§enorth is held by realm"));
    command(sender, "setcomplete", "north", "true");
    assertTrue(title.isTitleComplete());
    command(sender, "setcomplete", "north", "false");
    assertFalse(title.isTitleComplete());
  }

  @Test
  void adminCompositeEditsUpdateChildrenWithoutGrantingUnownedTitles() {
    CommandSender sender = admin();
    Title north = fixture.title("north", "county", 10);
    Title south = fixture.title("south", "county", 11);
    Title river = composite("river", "duchy", "north");
    faction.addTitle(north);
    faction.addTitle(river);
    command(sender, "addtitle", "river", "south");
    assertEquals(List.of("north", "south"), river.getTitles());
    verify(sender).sendMessage(contains("of its titles"));
    command(sender, "removetitle", "river", "south");
    assertEquals(List.of("north"), river.getTitles());
    assertFalse(faction.hasTitle(south));
  }

  @Test
  void failedAdminEditsDoNotPersistAndSaveFailuresAreReported() {
    CommandSender sender = admin();
    Title title = fixture.title("north", "county", 10);
    command(sender, "setcolour", "north", "invalid");
    assertEquals("4,5,6", title.getRgb());
    persistence.verify(() -> TitleLoader.saveTitle(any()), never());
    persistence.when(() -> TitleLoader.saveTitle(title)).thenReturn(false);
    command(sender, "rename", "north", "Renamed");
    assertEquals("Renamed", title.getName());
    verify(sender).sendMessage(contains("The change is live but will be lost on restart"));
  }

  @Test
  void adminCompletionsOfferOnlyRelevantTiersTitlesPartsAndValues() {
    fixture.title("north", "county", 10, 11);
    fixture.title("south", "county", 20);
    composite("river", "duchy", "north");
    assertEquals(List.of("setcolour", "setcomplete"), complete("set"));
    assertEquals(List.of("county", "duchy"), complete("list", ""));
    assertEquals(List.of("<province>"), complete("where", ""));
    assertEquals(List.of("north", "south"), complete("addprovince", ""));
    assertEquals(List.of("river"), complete("addtitle", ""));
    assertEquals(List.of("river"), complete("removetitle", ""));
    assertEquals(List.of("north"), complete("info", "N"));
    assertEquals(List.of("north", "south", "river"), complete("setcolor", ""));
    assertTrue(complete("unknown", "").isEmpty());
    assertEquals(List.of("<name>"), complete("rename", "north", ""));
    assertEquals(List.of("4,5,6"), complete("setcolour", "north", ""));
    assertEquals(List.of("R,G,B"), complete("setcolor", "missing", ""));
    assertEquals(List.of("<province>"), complete("addprovince", "north", ""));
    assertEquals(List.of("10", "11"), complete("removeprovince", "north", ""));
    assertEquals(List.of("south"), complete("addtitle", "river", ""));
    assertEquals(List.of("north"), complete("removetitle", "river", ""));
    assertEquals(List.of("true", "false"), complete("setcomplete", "north", ""));
    assertTrue(complete("unknown", "north", "").isEmpty());
    assertTrue(complete("removeprovince", "missing", "").isEmpty());
    assertTrue(complete("rename", "north", "New", "Name").isEmpty());
  }

  private CommandSender admin() {
    fixture.provincesEnabled(true);
    CommandSender sender = mock(CommandSender.class);
    when(sender.hasPermission("simplefactions.admin")).thenReturn(true);
    when(sender.getName()).thenReturn("Admin");
    return sender;
  }

  private void command(CommandSender sender, String... args) {
    List<String> full = new ArrayList<>(List.of("title"));
    full.addAll(List.of(args));
    assertTrue(TitleAdminCommand.handle(sender, full.toArray(String[]::new)));
  }

  private List<String> complete(String... args) {
    List<String> full = new ArrayList<>(List.of("title"));
    full.addAll(List.of(args));
    return TitleAdminCommand.complete(full.toArray(String[]::new));
  }

  private List<String> messages(CommandSender sender) {
    return mockingDetails(sender).getInvocations().stream()
        .filter(
            call ->
                call.getMethod().getName().equals("sendMessage")
                    && call.getArguments().length == 1
                    && call.getArgument(0) instanceof String)
        .map(call -> (String) call.getArgument(0))
        .toList();
  }

  @Test
  void aNameThatNormalizesToAnEmptyIdDoesNotCreateAnUnaddressableTitle() {
    TitleManager.isFormingTitle.put(player, tier("county", 2, 1));
    manager.formTitle(chat("§c!!!"));
    fixture.ui.runTasks();
    assertTrue(created.isEmpty());
    assertTrue(faction.getTitles().isEmpty());
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
  }

  @ParameterizedTest
  @ValueSource(strings = {"leadership", "land", "offline", "cancelled"})
  void delayedTitleCreationRechecksTheStateAtTaskExecution(String change) {
    TitleManager.isFormingTitle.put(player, tier("county", 2, 1));
    manager.formTitle(chat("North Vale"));
    assertTrue(created.isEmpty());
    switch (change) {
      case "leadership" -> {
        faction.addMember("Successor");
        faction.setLeader("Successor");
      }
      case "land" -> faction.removeProvince(10, false);
      case "offline" -> when(player.isOnline()).thenReturn(false);
      case "cancelled" -> TitleManager.isFormingTitle.remove(player);
    }
    fixture.ui.runTasks();
    assertTrue(created.isEmpty());
    assertTrue(faction.getTitles().isEmpty());
    assertFalse(TitleManager.isFormingTitle.containsKey(player));
  }

  @Test
  void severalQueuedNamesCanConsumeOnePendingFormationOnlyOnce() {
    TitleManager.isFormingTitle.put(player, tier("county", 2, 1));
    AsyncPlayerChatEvent first = chat("First Name"), second = chat("Second Name");
    manager.formTitle(first);
    manager.formTitle(second);
    assertTrue(first.isCancelled());
    assertTrue(second.isCancelled());
    assertTrue(created.isEmpty());
    fixture.ui.runTasks();
    assertEquals(1, created.size());
    assertEquals("First_Name", created.getFirst().getId());
    assertEquals(1, faction.getTitles().size());
  }

  @Test
  void destroyingASubjectsTitleTargetsItsDirectHolderRatherThanAnEarlierRegisteredLiege() {
    Faction subject = fixture.saved("subject", "Subject");
    subject.addProvince(20);
    fixture.subject(faction, subject);
    Title title = fixture.title("subject_county", "county", 20);
    subject.addTitle(title);
    fixture.provincesEnabled(true);
    when(player.hasPermission("simplefactions.admin")).thenReturn(true);
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("faction");
    new CommandManager()
        .onCommand(player, command, "faction", new String[] {"destroytitle", title.getId()});
    assertFalse(
        subject.hasTitle(title), "The admin command must remove the title from its actual holder");
    assertTrue(faction.getTitles().isEmpty());
    verify(player).sendMessage("§aDestroyed title subject_county §7(subject_county)");
  }

  @Test
  void adminInfoNamesTheSubjectThatDirectlyHoldsATitle() {
    Faction subject = fixture.saved("subject", "Subject");
    subject.addProvince(20);
    fixture.subject(faction, subject);
    Title title = fixture.title("subject_county", "county", 20);
    subject.addTitle(title);
    CommandSender sender = admin();
    command(sender, "info", title.getId());
    verify(sender).sendMessage("§7Holder: §fsubject");
    assertSame(subject, TitleManager.getOwner(title));
  }

  private AsyncPlayerChatEvent chat(String message) {
    return new AsyncPlayerChatEvent(true, player, message, new HashSet<>());
  }

  private Tier tier(String id, int level, int cost) {
    TierLoader.oList.removeIf(t -> t.getId().equals(id));
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("tier", level);
    yaml.set("form-cost", cost);
    Tier tier = new Tier(id, yaml);
    TierLoader.oList.add(tier);
    return tier;
  }

  private Title composite(String id, String tier, String... children) {
    JsonObject json = new JsonObject();
    json.addProperty("name", id);
    json.addProperty("rgb", "4,5,6");
    JsonArray parts = new JsonArray();
    for (String child : children) parts.add(child);
    json.add("titles", parts);
    Title title = new Title(TierLoader.getByString(tier), id, json);
    TitleLoader.getTitles().add(title);
    return title;
  }
}
