package net.tfminecraft.simplefactions.inactivity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.utils.FactionCleanup;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InactivityCoverageTest {
  private PersistenceFilesFixture disk;
  private FactionDomainFixture fixture;
  private Field historyField;
  private Object originalHistory;

  @BeforeEach
  void setup() throws Exception {
    disk = new PersistenceFilesFixture();
    Files.createDirectories(disk.root.resolve("Data"));
    fixture = new FactionDomainFixture();
    when(fixture.ui.plugin.getLogger()).thenReturn(mock(Logger.class));
    historyField = FactionCleanup.class.getDeclaredField("offlineDays");
    historyField.setAccessible(true);
    originalHistory = historyField.get(null);
    historyField.set(null, null);
  }

  @AfterEach
  void cleanup() throws Exception {
    try {
      if (historyField != null) historyField.set(null, originalHistory);
      if (fixture != null) fixture.close();
    } finally {
      if (disk != null) disk.close();
    }
  }

  private void state(String guild, int output, String faction, int prestige, long due)
      throws Exception {
    InactivityService.SavedState state = new InactivityService.SavedState();
    InactivityService.SavedClock guildClock = new InactivityService.SavedClock();
    guildClock.percent = output;
    guildClock.nextAt = due;
    state.guilds.put(guild, guildClock);
    InactivityService.SavedClock factionClock = new InactivityService.SavedClock();
    factionClock.percent = prestige;
    factionClock.nextAt = due;
    state.factions.put(faction, factionClock);
    disk.write("Cache/inactivity.json", JsonUtil.GSON.toJson(state));
  }

  private InactivityService.SavedState saved() throws Exception {
    return JsonUtil.readJson(
        disk.root.resolve("Cache/inactivity.json").toFile(), InactivityService.SavedState.class);
  }

  private void history(Map<String, Integer> days) throws Exception {
    disk.write("Cache/logins.json", JsonUtil.GSON.toJson(days));
    historyField.set(null, null);
  }

  @Test
  void failedReloadPreservesUsableClocksAndDoesNotOverwriteCorruptHistory() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    state(guild.getId(), 40, faction.getId(), 25, 12345);
    InactivityService.load();
    assertEquals(40, InactivityService.guildPercent(guild));
    assertEquals(25, InactivityService.prestigePercent(faction));
    String corrupt = "{\"guilds\": {\"" + guild.getId() + "\": broken}";
    disk.write("Cache/inactivity.json", corrupt);

    assertDoesNotThrow(InactivityService::load);

    assertEquals(40, InactivityService.guildPercent(guild));
    assertEquals(25, InactivityService.prestigePercent(faction));
    InactivityService.save();
    assertEquals(corrupt, Files.readString(disk.root.resolve("Cache/inactivity.json")));
  }

  @Test
  void coldStartupWithCorruptHistoryDoesNotDestroyItDuringShutdownSave() throws Exception {
    String corrupt = "{\"factions\": {\"old_realm\":{\"percent\":75}, unfinished}";
    disk.write("Cache/inactivity.json", corrupt);
    assertDoesNotThrow(InactivityService::load);
    InactivityService.save();
    assertEquals(corrupt, Files.readString(disk.root.resolve("Cache/inactivity.json")));
  }

  @Test
  void unreadableHistoryDoesNotClearTheLastSuccessfullyLoadedPenalties() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    state(guild.getId(), 40, faction.getId(), 25, 12345);
    InactivityService.load();
    disk.remove("Cache/inactivity.json");
    Files.createDirectory(disk.root.resolve("Cache/inactivity.json"));
    InactivityService.load();
    assertEquals(40, InactivityService.guildPercent(guild));
    assertEquals(25, InactivityService.prestigePercent(faction));
    assertTrue(Files.isDirectory(disk.root.resolve("Cache/inactivity.json")));
  }

  @Test
  void repairedHistoryCanBeReloadedAndSavedAfterAnEarlierReadFailure() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    disk.write("Cache/inactivity.json", "broken {");
    assertDoesNotThrow(InactivityService::load);
    state(guild.getId(), 12, faction.getId(), 18, 54321);
    InactivityService.load();
    assertEquals(12, InactivityService.guildPercent(guild));
    assertEquals(.88, InactivityService.outputFactor(guild));
    assertEquals(18, InactivityService.prestigePercent(faction));
    InactivityService.save();
    assertEquals(54321, saved().guilds.get(guild.getId()).nextAt);
    assertEquals(18, saved().factions.get(faction.getId()).percent);
  }

  @Test
  void missingNullAndPartialSavedStatesUseDefaultsAndClampPersistedPenalties() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    Field loaded = InactivityService.class.getDeclaredField("loaded");
    loaded.setAccessible(true);
    loaded.setBoolean(null, false);
    assertEquals(0, InactivityService.guildPercent(guild));
    assertEquals(0, InactivityService.prestigePercent(faction));
    assertEquals(1, InactivityService.outputFactor(null));
    assertEquals(0, InactivityService.guildPercent((Guild) null));
    assertEquals(0, InactivityService.prestigePercent(null));
    assertFalse(Files.exists(disk.root.resolve("Cache/inactivity.json")));
    InactivityService.save();
    assertTrue(saved().guilds.isEmpty());
    assertTrue(saved().factions.isEmpty());
    disk.write("Cache/inactivity.json", "null");
    InactivityService.load();
    assertEquals(0, InactivityService.guildPercent(guild));
    disk.write("Cache/inactivity.json", "{\"guilds\":null,\"factions\":null}");
    InactivityService.load();
    assertEquals(0, InactivityService.prestigePercent(faction));
    state(guild.getId(), 180, faction.getId(), -5, -10);
    InactivityService.load();
    assertEquals(100, InactivityService.guildPercent(guild));
    assertEquals(0, InactivityService.outputFactor(guild));
    assertEquals(0, InactivityService.prestigePercent(faction));
    InactivityService.save();
    assertEquals(0, saved().guilds.get(guild.getId()).nextAt);
    assertTrue(saved().factions.isEmpty());
    disk.write(
        "Cache/inactivity.json", "{\"guilds\":{\"removed\":null,\"empty\":{}},\"factions\":{}}");
    InactivityService.load();
    InactivityService.save();
    assertTrue(saved().guilds.isEmpty());
  }

  @Test
  void inactivityMembershipSkipsPlaceholdersAndUsesCurrentOnlinePresence() throws Exception {
    history(Map.of("alice", 21, "bob", 20, "cara", 35));
    assertFalse(InactivityService.isMemberInactive(null));
    assertFalse(InactivityService.isMemberInactive(" "));
    assertFalse(InactivityService.isMemberInactive("DUMMY_guard"));
    assertTrue(InactivityService.isMemberInactive("ALICE"));
    assertFalse(InactivityService.isMemberInactive("Bob"));
    assertFalse(InactivityService.isFullyInactive(null));
    assertFalse(InactivityService.isFullyInactive(List.of("dummy_guard")));
    assertFalse(InactivityService.isFullyInactive(List.of("Alice", "Bob")));
    assertTrue(
        InactivityService.isFullyInactive(Arrays.asList(null, "dummy_guard", "Alice", "Cara")));
    Player online = fixture.player("aLiCe");
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> Arrays.asList(null, online));
    assertFalse(InactivityService.isMemberInactive("ALICE"));
    when(Bukkit.getServer()).thenReturn(null);
    assertTrue(InactivityService.isMemberInactive("Alice"));
    when(Bukkit.getServer()).thenReturn(fixture.ui.server);
    when(Bukkit.getOnlinePlayers()).thenThrow(new IllegalStateException("server stopping"));
    assertTrue(InactivityService.isMemberInactive("Alice"));
  }

  @Test
  void armingAndDueTicksApplyOneStepWithoutKickingMembersOrChangingTheirBanks() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild base = faction.getOrCreateMainGuild();
    Guild guild = fixture.guild(faction, "merchants", "Bob");
    base.getBank().deposit(37.0);
    guild.getBank().deposit(19.0);
    history(Map.of("alice", 21, "bob", 22));
    InactivityService.armAll(null, 1000);
    InactivityService.armAll(Arrays.asList(null, faction), 1000);
    assertEquals(
        1000 + InactivityRules.DECAY_INTERVAL_MILLIS, saved().factions.get(faction.getId()).nextAt);
    assertEquals(
        1000 + InactivityRules.DECAY_INTERVAL_MILLIS, saved().guilds.get(guild.getId()).nextAt);
    assertEquals(0, InactivityService.guildPercent(base));
    String armed = Files.readString(disk.root.resolve("Cache/inactivity.json"));
    InactivityService.armAll(List.of(faction), 2000);
    assertEquals(armed, Files.readString(disk.root.resolve("Cache/inactivity.json")));
    InactivityService.tickIfDue(List.of(faction));
    assertEquals(1, InactivityService.prestigePercent(faction));
    assertEquals(1, InactivityService.guildPercent(base));
    assertEquals(1, InactivityService.guildPercent(guild));
    assertEquals(.99, InactivityService.outputFactor(guild));
    assertEquals(List.of("Alice", "Bob"), faction.getMembers());
    assertEquals(37, base.getBank().getWealth());
    assertEquals(19, guild.getBank().getWealth());
    verify(fixture.provinces).recalculate();
    InactivityService.tickIfDue(List.of(faction));
    assertEquals(1, InactivityService.guildPercent(guild));
    verify(fixture.provinces, times(1)).recalculate();
  }

  @Test
  void dueSweepArmsUntrackedInactiveGroupsAndSkipsGroupsStillWaiting() throws Exception {
    Faction armed = fixture.saved("armed", "Alice");
    Faction fresh = fixture.saved("fresh", "Bob");
    history(Map.of("alice", 21, "bob", 22));
    state(armed.getOrCreateMainGuild().getId(), 0, armed.getId(), 0, 1);
    InactivityService.load();
    InactivityService.tickIfDue(null);
    InactivityService.tickIfDue(Arrays.asList(null, armed, fresh));
    assertEquals(1, InactivityService.prestigePercent(armed));
    assertEquals(0, InactivityService.prestigePercent(fresh));
    assertTrue(saved().factions.get(fresh.getId()).nextAt > System.currentTimeMillis());
    assertEquals(0, InactivityService.guildPercent(fresh.getOrCreateMainGuild()));
    // A separately due faction triggers the sweep while the fresh clocks remain unchanged.
    var snapshot = saved();
    snapshot.factions.get(armed.getId()).nextAt = 1;
    disk.write("Cache/inactivity.json", JsonUtil.GSON.toJson(snapshot));
    InactivityService.load();
    InactivityService.tickIfDue(List.of(armed, fresh));
    assertEquals(2, InactivityService.prestigePercent(armed));
    assertEquals(0, InactivityService.guildPercent(fresh.getOrCreateMainGuild()));
  }

  @Test
  void activityReconciliationClearsBothPenaltiesAndRefreshesDerivedOutput() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    history(Map.of("alice", 20));
    state(guild.getId(), 30, faction.getId(), 40, 1);
    InactivityService.load();
    InactivityService.tickIfDue(List.of(faction));
    assertEquals(0, InactivityService.guildPercent(guild));
    assertEquals(0, InactivityService.prestigePercent(faction));
    assertTrue(saved().guilds.isEmpty());
    assertTrue(saved().factions.isEmpty());
    verify(fixture.provinces).recalculate();
    state(guild.getId(), 30, faction.getId(), 40, 100000);
    InactivityService.load();
    InactivityService.armAll(List.of(faction), 50);
    assertEquals(0, InactivityService.guildPercent(guild));
    assertTrue(saved().factions.isEmpty());
    InactivityService.armAll(List.of(faction), 60);
    assertTrue(saved().factions.isEmpty());
  }

  @Test
  void loginClearsOnlyTheReturningMembersGuildAndItsFactionAndPersistsActivity() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = fixture.guild(faction, "merchants", "Bob");
    Guild base = faction.getOrCreateMainGuild();
    history(Map.of("alice", 22, "bob", 30, "stranger", 21, "cara", 20));
    state(guild.getId(), 35, faction.getId(), 40, 1);
    var snapshot = saved();
    InactivityService.SavedClock baseClock = new InactivityService.SavedClock();
    baseClock.percent = 45;
    baseClock.nextAt = 1;
    snapshot.guilds.put(base.getId(), baseClock);
    disk.write("Cache/inactivity.json", JsonUtil.GSON.toJson(snapshot));
    InactivityService.load();
    Player bob = fixture.player("Bob");
    InactivityService.onLogin(bob);
    assertEquals(0, FactionCleanup.daysOffline("bob"));
    assertEquals(0, InactivityService.guildPercent(guild));
    assertEquals(0, InactivityService.prestigePercent(faction));
    assertEquals(45, InactivityService.guildPercent(base));
    verify(bob)
        .sendMessage(
            argThat(
                (String message) ->
                    message.contains("output penalty cleared")
                        && message.contains("prestige penalty cleared")));
    verify(fixture.provinces).recalculate();
    assertFalse(saved().guilds.containsKey(guild.getId()));
    InactivityService.onLogin(null);
    Player active = fixture.player("Cara");
    InactivityService.onLogin(active);
    verify(active, never()).sendMessage(anyString());
    Player stranger = fixture.player("Stranger");
    InactivityService.onLogin(stranger);
    verify(stranger).sendMessage("§aYou are active again.");
    history(Map.of("bob", 21));
    InactivityService.onLogin(bob);
    verify(bob).sendMessage("§aYou are active again.");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void provinceDecayPreservesTheCapitalUntilLastAndSavesTheNewClaims(boolean capitalOnly)
      throws Exception {
    int previousCost = Cache.provinceCost;
    try {
      Cache.provinceCost = 100000;
      Faction faction = fixture.saved("home", "Alice");
      fixture.provinceData.put(1, new Province(1, "PLAINS", 1));
      fixture.provinceData.put(2, new Province(2, "PLAINS", 1));
      faction.addProvince(1);
      if (!capitalOnly) faction.addProvince(2);
      faction.setCapital(1, false, false);
      Guild guild = fixture.guild(faction, "merchants", "Bob");
      if (!capitalOnly) guild.setCapital(2, false);
      history(Map.of("alice", 21, "bob", 21));
      state(guild.getId(), 0, faction.getId(), 0, 1);
      InactivityService.load();
      InactivityService.tickIfDue(List.of(faction));
      assertEquals(capitalOnly ? List.of() : List.of(1), faction.getProvinces());
      assertEquals(capitalOnly ? -1 : 1, faction.getCapital());
      assertEquals(-1, guild.getCapital());
      assertTrue(Files.isRegularFile(disk.root.resolve("Data/home.json")));
      FactionData persisted =
          JsonUtil.readJson(disk.root.resolve("Data/home.json").toFile(), FactionData.class);
      assertEquals(
          faction.getProvinces(), persisted.provinces.stream().map(Number::intValue).toList());
      verify(fixture.map, atLeastOnce()).enqueue("nation", faction.getRGB());
    } finally {
      Cache.provinceCost = previousCost;
    }
  }

  @Test
  void affordableAndCivilWarLockedProvincesAreRetainedDuringPenaltyTicks() throws Exception {
    int previousCost = Cache.provinceCost;
    try {
      Cache.provinceCost = 0;
      Faction faction = fixture.saved("home", "Alice");
      fixture.provinceData.put(1, new Province(1, "PLAINS", 1));
      faction.addProvince(1);
      history(Map.of("alice", 21));
      state(faction.getOrCreateMainGuild().getId(), 0, faction.getId(), 0, 1);
      InactivityService.load();
      InactivityService.tickIfDue(List.of(faction));
      assertEquals(List.of(1), faction.getProvinces());
      Cache.provinceCost = 100000;
      War civil = new War(13, faction, fixture.saved("enemy", "Bob"));
      civil.setMovementId("rebellion");
      WarManager.get().add(civil);
      state(faction.getOrCreateMainGuild().getId(), 1, faction.getId(), 1, 1);
      InactivityService.load();
      InactivityService.tickIfDue(List.of(faction));
      assertEquals(List.of(1), faction.getProvinces());
      assertEquals(2, InactivityService.prestigePercent(faction));
    } finally {
      Cache.provinceCost = previousCost;
    }
  }

  @Test
  void persistenceAndEconomyFailuresAreLoggedWithoutEscapingTheLifecycle() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    history(Map.of("alice", 21));
    state(guild.getId(), 10, faction.getId(), 20, 1);
    InactivityService.load();
    doThrow(new IllegalStateException("province refresh unavailable"))
        .when(fixture.provinces)
        .recalculate();
    assertDoesNotThrow(() -> InactivityService.tickIfDue(List.of(faction)));
    assertEquals(11, InactivityService.guildPercent(guild));
    verify(fixture.ui.plugin.getLogger())
        .log(eq(Level.WARNING), contains("Could not refresh guild output"), any(Throwable.class));
    disk.remove("Cache/inactivity.json");
    Files.createDirectory(disk.root.resolve("Cache/inactivity.json"));
    assertDoesNotThrow(InactivityService::save);
    assertTrue(Files.isDirectory(disk.root.resolve("Cache/inactivity.json")));
    verify(fixture.ui.plugin.getLogger())
        .log(eq(Level.WARNING), contains("Could not save inactivity timers"), any(Throwable.class));
    SimpleFactions previous = SimpleFactions.plugin;
    SimpleFactions.plugin = null;
    try {
      assertDoesNotThrow(InactivityService::save);
      assertDoesNotThrow(InactivityService::load);
    } finally {
      SimpleFactions.plugin = previous;
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "null"})
  void emptyOrNullSavedHistoryIsPreservedUntilAValidExplicitReload(String contents)
      throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    state(guild.getId(), 30, faction.getId(), 40, 1);
    InactivityService.load();
    disk.write("Cache/inactivity.json", contents);
    InactivityService.load();
    InactivityService.save();
    assertEquals(contents, Files.readString(disk.root.resolve("Cache/inactivity.json")));
    assertEquals(30, InactivityService.guildPercent(guild));
    assertEquals(40, InactivityService.prestigePercent(faction));
    state(guild.getId(), 4, faction.getId(), 7, 100);
    InactivityService.load();
    InactivityService.save();
    assertEquals(4, saved().guilds.get(guild.getId()).percent);
    assertEquals(7, saved().factions.get(faction.getId()).percent);
  }

  @Test
  void failedAtomicSaveKeepsTheCompletePreviousHistoryAndCanBeRetried() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    Guild guild = faction.getOrCreateMainGuild();
    history(Map.of("alice", 21));
    state(guild.getId(), 10, faction.getId(), 20, 1);
    InactivityService.load();
    var file = disk.root.resolve("Cache/inactivity.json");
    String original = Files.readString(file);
    Assumptions.assumeTrue(Files.getFileAttributeView(file, PosixFileAttributeView.class) != null);
    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
    try {
      Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ));
      Assumptions.assumeFalse(Files.isWritable(file), "User bypasses POSIX write permissions");
      InactivityService.tickIfDue(List.of(faction));
      assertEquals(original, Files.readString(file));
      assertEquals(11, InactivityService.guildPercent(guild));
      assertEquals(21, InactivityService.prestigePercent(faction));
      try (var cache = Files.list(file.getParent())) {
        assertFalse(cache.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
      }
    } finally {
      Files.setPosixFilePermissions(file, permissions);
    }
    InactivityService.save();
    assertEquals(11, saved().guilds.get(guild.getId()).percent);
    assertEquals(21, saved().factions.get(faction.getId()).percent);
  }

  @Test
  void oneFailingGuildLogDoesNotPreventTheNextFactionFromAdvancing() throws Exception {
    Faction failing = fixture.saved("failing", "Alice");
    Faction next = fixture.saved("next", "Bob");
    history(Map.of("alice", 21, "bob", 21));
    InactivityService.armAll(List.of(failing, next), 0);
    String failedGuildName = failing.getOrCreateMainGuild().getName();
    Logger logger = fixture.ui.plugin.getLogger();
    doThrow(new IllegalStateException("log sink unavailable"))
        .when(logger)
        .log(eq(Level.INFO), startsWith(failedGuildName + " inactivity output"));
    assertDoesNotThrow(() -> InactivityService.tickIfDue(List.of(failing, next)));
    assertEquals(1, InactivityService.guildPercent(failing.getOrCreateMainGuild()));
    assertEquals(1, InactivityService.guildPercent(next.getOrCreateMainGuild()));
    assertEquals(1, InactivityService.prestigePercent(next));
    verify(fixture.ui.plugin.getLogger())
        .log(eq(Level.SEVERE), eq("Inactivity tick failed for failing"), any(Throwable.class));
  }

  @Test
  void duePenaltiesCanBeLoggedDuringShutdownWithoutALivePlugin() throws Exception {
    Faction faction = fixture.saved("home", "Alice");
    history(Map.of("alice", 21));
    state(faction.getOrCreateMainGuild().getId(), 0, faction.getId(), 0, 1);
    InactivityService.load();
    SimpleFactions previous = SimpleFactions.plugin;
    SimpleFactions.plugin = null;
    try {
      assertDoesNotThrow(() -> InactivityService.tickIfDue(List.of(faction)));
      assertEquals(1, InactivityService.prestigePercent(faction));
      assertEquals(1, InactivityService.guildPercent(faction.getOrCreateMainGuild()));
    } finally {
      SimpleFactions.plugin = previous;
    }
  }
}
