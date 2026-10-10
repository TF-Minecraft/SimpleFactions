package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.tfminecraft.rpcharacters.enums.Status;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeData;
import net.tfminecraft.simplefactions.army.LevyEntry;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Real office/report workflows with optional character storage and Paper at the boundary. */
class EspionageOperationsCoverageTest {
  @TempDir Path directory;
  private FactionDomainFixture fixture;
  private Map<Object, Object> names;
  private Map<Object, Object> originalNames;
  private final Map<Field, Object> originalConfig = new LinkedHashMap<>();
  private final Map<Map<Object, Object>, Map<Object, Object>> originalConfigMaps =
      new java.util.IdentityHashMap<>();
  private Field aptitudeField;
  private Object originalAptitudes;
  private Path aptitudeFile;
  private MockedConstruction<Database> databases;
  private MockedStatic<net.tfminecraft.rpcharacters.managers.PlayerManager> roleplayPlayers;
  private MockedStatic<TLibs> items;
  private final Map<UUID, PlayerData> characterData = new LinkedHashMap<>();
  private final Map<String, RPCharacter> characters = new LinkedHashMap<>();
  private final AtomicBoolean saveSucceeds = new AtomicBoolean(true);
  private Faction home;
  private Player leader;
  private Player candidate;
  private Player visitor;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    Field cache = CharacterNames.class.getDeclaredField("cache");
    cache.setAccessible(true);
    names = (Map<Object, Object>) cache.get(null);
    originalNames = new LinkedHashMap<>(names);
    names.clear();
    PluginManager plugins = Bukkit.getPluginManager();
    Plugin roleplay = mock(Plugin.class);
    when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
    when(plugins.getPlugin("RPCharacters")).thenReturn(roleplay);
    when(roleplay.getDataFolder()).thenReturn(directory.toFile());
    when(Bukkit.getOfflinePlayer(anyString()))
        .thenAnswer(
            call -> {
              OfflinePlayer player = mock(OfflinePlayer.class);
              when(player.getUniqueId()).thenReturn(accountId(call.getArgument(0)));
              return player;
            });
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (items != null) items.close();
      if (roleplayPlayers != null) roleplayPlayers.close();
      if (databases != null) databases.close();
      if (aptitudeField != null) aptitudeField.set(null, originalAptitudes);
      for (var entry : originalConfig.entrySet()) entry.getKey().set(null, entry.getValue());
      originalConfigMaps.forEach(
          (map, original) -> {
            map.clear();
            map.putAll(original);
          });
      names.clear();
      names.putAll(originalNames);
    } finally {
      fixture.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "{\"active\":true,\"name\":null}"})
  void malformedOldCharacterDoesNotHideALaterValidActiveCharacter(String malformed)
      throws Exception {
    Path folder = directory.resolve("data/characterdata").resolve(accountId("Offline").toString());
    Files.createDirectories(folder);
    Files.writeString(folder.resolve("00-old.json"), malformed);
    String active = "{\"active\":true,\"name\":\"Lady Rowan\"}";
    Files.writeString(folder.resolve("01-active.json"), active);

    assertEquals("Lady Rowan", CharacterNames.forForeign("Offline"));
    assertEquals("Lady Rowan", CharacterNames.of("Offline"));
    assertEquals(malformed, Files.readString(folder.resolve("00-old.json")));
    assertEquals(active, Files.readString(folder.resolve("01-active.json")));
  }

  @Test
  void quotedCharacterResolutionRejectsAmbiguityAndExactAccountsStillTakePrecedence()
      throws Exception {
    offices();
    when(characters.get("Candidate").getName()).thenReturn("§aLady Rowan");
    assertSame(candidate, CharacterNames.resolveOnline(leader, " \"lady rowan\" "));
    Player duplicate = characterPlayer("Duplicate");
    when(characters.get("Duplicate").getName()).thenReturn("Lady Rowan");
    assertNull(CharacterNames.resolveOnline(leader, "Lady Rowan"));
    verify(leader).sendMessage(contains("Several characters"));
    assertSame(candidate, CharacterNames.resolveOnline(leader, "candidate"));
    assertSame(duplicate, CharacterNames.resolveOnline(leader, "Duplicate"));
    assertNull(CharacterNames.resolveOnline(leader, "No Such Player"));
    verify(leader).sendMessage(contains("No online player"));
    assertEquals("§aLady Rowan", CharacterNames.forForeign("Candidate"));
  }

  @Test
  void offlineNamesIgnoreInactiveAndNonJsonFilesAndCacheWithoutEditingCharacterState()
      throws Exception {
    Path folder = directory.resolve("data/characterdata").resolve(accountId("Offline").toString());
    Files.createDirectories(folder);
    Files.writeString(folder.resolve("00-note.txt"), "This is not character data");
    Files.writeString(folder.resolve("01-empty.json"), "null");
    Files.writeString(folder.resolve("02-inactive.json"), "{\"active\":false,\"name\":\"Old\"}");
    Files.writeString(folder.resolve("03-active.json"), "{\"active\":true,\"name\":\"Current\"}");
    assertEquals("Current", CharacterNames.of("Offline"));
    Files.delete(folder.resolve("03-active.json"));
    assertEquals("Current", CharacterNames.forForeign("Offline"));
    assertEquals("Unknown", CharacterNames.forForeign(null));
  }

  @Test
  void unreadableCharacterDirectoryKeepsForeignIdentityHidden() throws Exception {
    Path folder = directory.resolve("data/characterdata").resolve(accountId("Offline").toString());
    Files.createDirectories(folder);
    try (MockedStatic<Files> storage = mockStatic(Files.class)) {
      storage.when(() -> Files.isDirectory(folder)).thenReturn(true);
      storage.when(() -> Files.list(folder)).thenThrow(new IOException("directory unavailable"));
      assertEquals("Unknown", CharacterNames.forForeign("Offline"));
      assertEquals("Offline", CharacterNames.of("Offline"));
      storage.verify(() -> Files.list(folder));
    }
    assertTrue(Files.isDirectory(folder));
  }

  @Test
  void persistedCharacterAptitudesOverrideOlderOfficeCopiesAndRemainBoundToTheCharacter()
      throws Exception {
    offices();
    SpecialPositionAssignment holder = home.getEspionage().getSpymaster();
    int original = holder.aptitude;
    int canonical = original == 91 ? 92 : 91;
    Files.writeString(aptitudeFile, "{\"character-Leader\":" + canonical + "}");
    EspionageService.loadAptitudes(aptitudeFile);
    assertSame(holder, home.getEspionage().getSpymaster());
    assertEquals(canonical, holder.aptitude);
    assertEquals(canonical, EspionageService.effectiveAptitude(home, holder));
    assertTrue(Files.readString(aptitudeFile).contains("character-Leader"));
    assertFalse(EspionageService.appoint(leader, home, leader));
    assertEquals(canonical, holder.aptitude);
  }

  @Test
  void founderStorageFailureKeepsRetryIntentAndNeverInstallsAnUnsavedOffice() throws Exception {
    offices();
    home.getEspionage().removeSpymaster();
    when(characters.get("Leader").getId()).thenReturn("replacement-character");
    Files.delete(aptitudeFile);
    Files.createDirectory(aptitudeFile);
    EspionageService.initializeFounder(home);
    assertNull(home.getEspionage().getSpymaster());
    assertTrue(home.getEspionage().isPendingFounder(SpecialPosition.SPYMASTER));
    assertEquals(
        "replacement-character",
        home.getEspionage().pendingFounderCharacter(SpecialPosition.SPYMASTER));
    verify(leader).sendMessage(contains("could not be initialized"));
    Files.delete(aptitudeFile);
    EspionageService.initializeFounder(home);
    assertEquals("replacement-character", home.getEspionage().getSpymaster().characterId);
    assertTrue(home.getEspionage().getSpymaster().automatic);
    assertFalse(home.getEspionage().hasPendingFounder());
  }

  @Test
  void legacyCharacterRollsMigrateWithoutRerollingRemovedOffices() throws Exception {
    offices();
    EspionageState legacy =
        net.tfminecraft.simplefactions.database.JsonUtil.GSON.fromJson(
            "{\"appointmentAptitudes\":{\"old-faction:legacy-character\":78,\"unqualified\":15}}",
            EspionageState.class);
    home.setEspionage(legacy);
    EspionageService.loadAptitudes(aptitudeFile);
    CharacterAptitudes saved = new CharacterAptitudes(aptitudeFile);
    saved.load();
    assertEquals(
        78,
        saved.aptitude(
            "legacy-character",
            () -> {
              throw new AssertionError("The permanent roll must not be rerolled");
            }));
    assertFalse(Files.readString(aptitudeFile).contains("unqualified"));
    assertNull(home.getEspionage().getSpymaster());
  }

  @Test
  void reportRefreshPersistsFounderRetryIntentWithoutInventingAnIntelligenceReport()
      throws Exception {
    offices();
    home.getEspionage().removeSpymaster();
    when(characters.get("Leader").getId()).thenReturn("replacement-character");
    Files.delete(aptitudeFile);
    Files.createDirectory(aptitudeFile);
    int before = databases.constructed().size();
    EspionageService.refreshReports(leader);
    assertNull(home.getEspionage().getSpymaster());
    assertTrue(home.getEspionage().isPendingFounder(SpecialPosition.SPYMASTER));
    assertEquals(
        "replacement-character",
        home.getEspionage().pendingFounderCharacter(SpecialPosition.SPYMASTER));
    assertEquals(before + 1, databases.constructed().size());
    verify(databases.constructed().getLast()).saveFaction(home);
    verify(leader).sendMessage(contains("could not be initialized"));
  }

  @Test
  void publicReportRefreshCapturesRealGuildArmyAndOfficeStateOncePerDay() throws Exception {
    offices();
    YamlConfiguration settings = new YamlConfiguration();
    settings.set("espionage.appointments.build-up-days", 0);
    settings.set("espionage.checks.luck-spread", 0);
    settings.set("espionage.checks.aptitude-multiplier", 2);
    EspionageConfig.load(settings);
    home.getEspionage().getSpymaster().aptitude = 100;
    Faction target = fixture.saved("target", "Foreign");
    target.getOrCreateMainGuild();
    target.addMember("Ordinary");
    Player foreign = characterPlayer("Foreign");
    characterPlayer("Ordinary");
    YamlConfiguration upgradeData = new YamlConfiguration();
    upgradeData.set("upgrade.allowed-types", List.of("guild"));
    UpgradeLoader.map.put(
        "workshop", new Upgrade("workshop", upgradeData.getConfigurationSection("upgrade")));
    Guild guild = fixture.guild(target, "merchants", "Baron");
    guild.addMember("Trader");
    Player baron = characterPlayer("Baron");
    characterPlayer("Trader");
    fixture.provinceData.put(37, new Province(37, "PLAINS", 50));
    guild.setCapital(37, false);
    guild.getUpgrade("workshop").setLevel(2);
    SpecialPositionAssignment defender = new SpecialPositionAssignment();
    defender.playerName = baron.getName();
    defender.playerId = baron.getUniqueId();
    defender.characterId = "character-Baron";
    target.getEspionage().appoint(defender, 0, 0);
    Regiment regiment = fixture.regiment("guards", false, 8, 1);
    Regiment levies = fixture.regiment("levies", true, 0, 0);
    levies.setLevyEntries(List.of(new LevyEntry(target, 6)));
    target.getMilitary().getRegiments().add(regiment);
    target.getMilitary().getRegiments().add(levies);
    target.getMilitary().addQueueItem(regiment, 123);
    installFortConfiguration();
    target
        .getInstallationHandler()
        .acceptTransferred(new Installation("fort", "Fort", InstallationKind.FORT, 37, 0, 0, 1));
    target.getBank().deposit(2000.0);
    guild.getBank().deposit(700.0);
    double capturedWealth = target.getWealth();
    assertTrue(capturedWealth > 0);

    EspionageService.refreshReports(leader);

    IntelligenceReport report = EspionageService.report(leader, target);
    assertNotNull(report);
    assertEquals(IntelligenceTier.DETAILED, report.tier());
    assertEquals(LocalDate.now(ZoneOffset.UTC).toEpochDay(), report.day);
    assertEquals("Character Baron", report.officeHolder(SpecialPosition.SPYMASTER));
    assertTrue(report.estimates.containsKey("Training:0"));
    assertTrue(report.estimates.containsKey("Regiment:guards:Soldiers"));
    assertTrue(report.estimates.containsKey("Regiment:levies:Levies"));
    assertTrue(report.estimates.containsKey("Installation:fort:Level"));
    assertTrue(report.estimates.containsKey(IntelligenceLedger.key(guild, "Upgrade:workshop")));
    assertTrue(report.estimates.containsKey(IntelligenceLedger.key(guild, "Branch:commerce")));
    assertTrue(report.estimates.containsKey(IntelligenceLedger.key(guild, "Trade power")));
    var roster =
        report.roster.stream()
            .filter(member -> member.character().equals("Character Baron"))
            .findFirst()
            .orElseThrow();
    assertEquals(guild.getId(), roster.guildId());
    assertTrue(roster.guildLeader());
    assertEquals(List.of(SpecialPosition.SPYMASTER), roster.offices());
    assertFalse(
        report.roster.stream().anyMatch(member -> member.character().equals("Character Foreign")));
    var wealthEstimate = report.estimate("Wealth");
    assertNotNull(wealthEstimate);
    assertTrue(
        wealthEstimate.lower() <= capturedWealth && wealthEstimate.upper() >= capturedWealth);
    target.getBank().deposit(9000.0);
    EspionageService.refreshReports(candidate);
    assertSame(report, EspionageService.report(candidate, target));
    assertEquals(wealthEstimate, report.estimate("Wealth"));
    assertTrue(report.estimates.containsKey("Army"));
    // Today's report from before army and vehicle intelligence is rebuilt under the same rolls.
    report.version = 1;
    EspionageService.refreshReports(candidate);
    IntelligenceReport rebuilt = EspionageService.report(candidate, target);
    assertNotSame(report, rebuilt);
    assertEquals(IntelligenceReport.VERSION, rebuilt.version);
    assertEquals(report.tier(), rebuilt.tier());
    report = rebuilt;
    assertEquals(2, EspionageService.regenerateReports());
    assertNotSame(report, EspionageService.report(leader, target));
    assertNotNull(EspionageService.report(foreign, home));
  }

  @SuppressWarnings("unchecked")
  private void installFortConfiguration() throws Exception {
    Field field =
        net.tfminecraft.simplefactions.loaders.InstallationConfigLoader.class.getDeclaredField(
            "byKind");
    field.setAccessible(true);
    Map<Object, Object> map = (Map<Object, Object>) field.get(null);
    originalConfigMaps.put(map, new LinkedHashMap<>(map));
    map.put(
        InstallationKind.FORT,
        new net.tfminecraft.simplefactions.installation.InstallationKindConfig(
            12,
            Map.of(
                1,
                new net.tfminecraft.simplefactions.installation.InstallationKindConfig.Level(
                    0, 0, Map.of()))));
  }

  @Test
  void trailingRemoveArgumentsMustNotDismissTheCurrentSpymaster() throws Exception {
    offices();
    assertTrue(EspionageService.appoint(leader, home, candidate));
    SpecialPositionAssignment before = home.getEspionage().getSpymaster();
    assertTrue(
        EspionageCommands.handle(leader, new String[] {"spymaster", "remove", "accidental"}));
    assertSame(before, home.getEspionage().getSpymaster());
    verify(leader).sendMessage(contains("<player|remove>"));
  }

  @Test
  void aDeadActiveCharacterCannotDisplaceTheCurrentSpymaster() throws Exception {
    offices();
    SpecialPositionAssignment before = home.getEspionage().getSpymaster();
    when(characters.get("Candidate").getStatus()).thenReturn(Status.DEAD);
    assertFalse(EspionageService.appoint(leader, home, candidate));
    assertSame(before, home.getEspionage().getSpymaster());
    assertEquals(0, home.getEspionage().appointmentCount(SpecialPosition.SPYMASTER));
  }

  @ParameterizedTest
  @ValueSource(strings = {"espionage", "positions", "spymaster"})
  void commandAliasesOpenOnlyTheCallersPermittedOfficeView(String alias) throws Exception {
    offices();
    assertTrue(EspionageCommands.matches(alias.toUpperCase()));
    assertFalse(EspionageCommands.matches("unrelated"));
    assertTrue(EspionageCommands.handle(leader, new String[] {alias}));
    assertEquals(
        alias.equals("positions") ? SFGUI.SPECIAL_POSITIONS : SFGUI.SPYMASTER_SETTINGS,
        ((SFInventoryHolder) leader.getOpenInventory().getTopInventory().getHolder()).getType());
    assertTrue(EspionageCommands.handle(candidate, new String[] {alias}));
    assertEquals(
        alias.equals("positions") ? SFGUI.SPECIAL_POSITIONS : SFGUI.SPYMASTER_VIEW,
        ((SFInventoryHolder) candidate.getOpenInventory().getTopInventory().getHolder()).getType());
    assertTrue(EspionageCommands.handle(visitor, new String[] {alias}));
    verify(visitor).sendMessage(contains("belong to a faction"));
    verify(visitor, never()).openInventory(any(org.bukkit.inventory.Inventory.class));
  }

  @Test
  void commandAppointmentSabotageAndRemovalPreservePrivateConductAndAuthority() throws Exception {
    offices();
    assertTrue(
        EspionageCommands.handle(leader, new String[] {"spymaster", "Character", "Candidate"}));
    SpecialPositionAssignment appointed = home.getEspionage().getSpymaster();
    assertEquals(candidate.getUniqueId(), appointed.playerId);
    assertFalse(appointed.automatic);
    clearInvocations(leader, candidate);
    assertTrue(
        EspionageCommands.handle(
            candidate, new String[] {"spymaster", "sabotage", "offense", "50"}));
    assertTrue(
        EspionageCommands.handle(
            candidate, new String[] {"spymaster", "sabotage", "defense", "25"}));
    assertEquals(50, appointed.offenseReduction);
    assertEquals(25, appointed.defenseReduction);
    verifyNoInteractions(leader);
    assertTrue(
        EspionageCommands.handle(leader, new String[] {"spymaster", "sabotage", "offense", "100"}));
    assertEquals(50, appointed.offenseReduction);
    assertTrue(EspionageCommands.handle(candidate, new String[] {"spymaster", "remove"}));
    assertSame(appointed, home.getEspionage().getSpymaster());
    assertTrue(EspionageCommands.handle(leader, new String[] {"spymaster", "remove"}));
    assertTrue(home.getEspionage().getSpymaster().automatic);
    assertEquals(leader.getUniqueId(), home.getEspionage().getSpymaster().playerId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "invalid-number",
        "invalid-side",
        "missing-value",
        "wrong-command",
        "missing-player"
      })
  void invalidCommandArgumentsNeverChangeTheOffice(String kind) throws Exception {
    offices();
    SpecialPositionAssignment before = home.getEspionage().getSpymaster();
    String[] args =
        switch (kind) {
          case "invalid-number" -> new String[] {"spymaster", "sabotage", "offense", "NaN"};
          case "invalid-side" -> new String[] {"spymaster", "sabotage", "other", "25"};
          case "missing-value" -> new String[] {"spymaster", "sabotage", "offense"};
          case "missing-player" -> new String[] {"spymaster", "Absent"};
          default -> new String[] {"positions", "extra"};
        };
    assertTrue(EspionageCommands.handle(leader, args));
    assertSame(before, home.getEspionage().getSpymaster());
    assertEquals(0, before.offenseReduction);
    assertEquals(0, before.defenseReduction);
    verify(leader)
        .sendMessage(
            contains(kind.equals("missing-player") ? "No online player" : "<player|remove>"));
  }

  @Test
  void completionListsOnlyAuthorizedOnlineAppointmentsAndPrivateConductChoices() throws Exception {
    offices();
    home.addMember("OfflineMember");
    assertTrue(
        EspionageCommands.complete(leader, new String[] {"spymaster", ""})
            .containsAll(List.of("remove", "Leader", "Candidate", "sabotage")));
    assertFalse(
        EspionageCommands.complete(leader, new String[] {"spymaster", ""})
            .contains("OfflineMember"));
    assertEquals(
        List.of("Candidate"), EspionageCommands.complete(leader, new String[] {"spymaster", "Ca"}));
    assertTrue(EspionageCommands.complete(candidate, new String[] {"spymaster", ""}).isEmpty());
    assertTrue(EspionageCommands.complete(visitor, new String[] {"spymaster", ""}).isEmpty());
    assertTrue(EspionageCommands.complete(leader, new String[] {"positions", ""}).isEmpty());
    assertEquals(
        List.of("offense", "defense"),
        EspionageCommands.complete(leader, new String[] {"spymaster", "sabotage", ""}));
    assertEquals(
        List.of("50"),
        EspionageCommands.complete(leader, new String[] {"spymaster", "sabotage", "defense", "5"}));
  }

  @Test
  void spymastersShareByCommandAndInvalidSharingArgumentsChangeNothing() throws Exception {
    offices();
    assertTrue(
        EspionageCommands.handle(leader, new String[] {"spymaster", "share", "vassals", "broad"}));
    assertEquals(IntelligenceTier.BROAD, home.getEspionage().sharing(SharingPartner.VASSALS));
    assertTrue(
        EspionageCommands.handle(
            leader, new String[] {"spymaster", "share", "overlord", "reliable"}));
    assertEquals(IntelligenceTier.RELIABLE, home.getEspionage().sharing(SharingPartner.OVERLORD));
    assertTrue(
        EspionageCommands.handle(leader, new String[] {"spymaster", "share", "vassals", "none"}));
    assertEquals(IntelligenceTier.UNKNOWN, home.getEspionage().sharing(SharingPartner.VASSALS));
    assertTrue(
        EspionageCommands.handle(leader, new String[] {"spymaster", "share", "allies", "broad"}));
    assertEquals(IntelligenceTier.BROAD, home.getEspionage().sharing(SharingPartner.ALLIES));
    clearInvocations(leader);
    assertTrue(
        EspionageCommands.handle(leader, new String[] {"spymaster", "share", "friends", "broad"}));
    assertTrue(
        EspionageCommands.handle(
            leader, new String[] {"spymaster", "share", "overlord", "everything"}));
    assertEquals(IntelligenceTier.RELIABLE, home.getEspionage().sharing(SharingPartner.OVERLORD));
    verify(leader, times(2)).sendMessage(contains("<overlord|vassals|allies>"));
    assertTrue(EspionageCommands.complete(leader, new String[] {"spymaster", ""}).contains("share"));
    assertEquals(
        List.of("overlord", "vassals", "allies"),
        EspionageCommands.complete(leader, new String[] {"spymaster", "share", ""}));
    assertEquals(
        List.of("rumours", "reliable"),
        EspionageCommands.complete(leader, new String[] {"spymaster", "share", "vassals", "r"}));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"null", "offline", "foreign", "no-character", "already-holder", "cooldown"})
  void appointmentDenialsRetainTheCurrentOfficeAndItsAppointmentCount(String reason)
      throws Exception {
    offices();
    Player choice = candidate;
    if (reason.equals("null")) choice = null;
    if (reason.equals("offline")) when(candidate.isOnline()).thenReturn(false);
    if (reason.equals("foreign")) choice = visitor;
    if (reason.equals("no-character"))
      when(characterData.get(candidate.getUniqueId()).getActiveCharacter()).thenReturn(null);
    if (reason.equals("already-holder")) choice = leader;
    if (reason.equals("cooldown")) {
      assertTrue(EspionageService.appoint(leader, home, candidate));
      YamlConfiguration settings = new YamlConfiguration();
      settings.set("espionage.appointments.change-cooldown-days", 2);
      EspionageConfig.load(settings);
      choice = leader;
    }
    SpecialPositionAssignment before = home.getEspionage().getSpymaster();
    int appointments = home.getEspionage().appointmentCount(SpecialPosition.SPYMASTER);
    assertFalse(EspionageService.appoint(leader, home, choice));
    assertSame(before, home.getEspionage().getSpymaster());
    assertEquals(appointments, home.getEspionage().appointmentCount(SpecialPosition.SPYMASTER));
  }

  @ParameterizedTest
  @ValueSource(strings = {"database", "aptitude-storage", "unavailable"})
  void failedAppointmentPersistenceLeavesThePreviousHolderAndUnrestUntouched(String failure)
      throws Exception {
    offices();
    SpecialPositionAssignment before = home.getEspionage().getSpymaster();
    if (failure.equals("database")) saveSucceeds.set(false);
    if (failure.equals("unavailable")) aptitudeField.set(null, null);
    if (failure.equals("aptitude-storage")) {
      Files.delete(aptitudeFile);
      Files.createDirectory(aptitudeFile);
    }
    assertFalse(EspionageService.appoint(leader, home, candidate));
    assertSame(before, home.getEspionage().getSpymaster());
    assertEquals(0, home.getEspionage().appointmentCount(SpecialPosition.SPYMASTER));
    assertTrue(home.getEspionage().unrestModifiers(System.currentTimeMillis()).isEmpty());
    verify(leader).sendMessage(contains("could not be saved"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"denied", "extra-argument", "missing-resource", "valid"})
  void reloadRequiresPermissionAndReportsConfigurationFailuresWithoutTouchingReports(String state)
      throws Exception {
    offices();
    CommandSender sender = mock(CommandSender.class);
    when(sender.hasPermission(EspionageConfig.reloadPermission()))
        .thenReturn(!state.equals("denied"));
    when(fixture.ui.plugin.getDataFolder()).thenReturn(directory.toFile());
    if (state.equals("valid"))
      when(fixture.ui.plugin.getResource("special-positions.yml"))
          .thenAnswer(
              call -> Files.newInputStream(Path.of("src/main/resources/special-positions.yml")));
    String[] args =
        state.equals("extra-argument")
            ? new String[] {"reloadespionage", "extra"}
            : new String[] {"reloadespionage"};
    assertTrue(EspionageCommands.reload(sender, args));
    String expected =
        switch (state) {
          case "denied" -> "do not have permission";
          case "extra-argument" -> "/faction reloadespionage";
          case "missing-resource" -> "Could not reload";
          default -> "0 intelligence reports regenerated";
        };
    verify(sender).sendMessage(contains(expected));
    if (!state.equals("valid"))
      assertFalse(Files.exists(directory.resolve("special-positions.yml")));
  }

  @SuppressWarnings("unchecked")
  private void offices() throws Exception {
    for (String name :
        List.of(
            "bypassPermission",
            "weights",
            "stabilityPenalty",
            "penaltyDays",
            "buildUpDays",
            "startingAptitude",
            "changeCooldownDays",
            "reloadPermission",
            "base",
            "extraPositionPenalty",
            "rollMultiplier",
            "center",
            "cap",
            "aptitudeSpread",
            "luckSpread",
            "luckDraws",
            "rosterLimit",
            "tiers",
            "minimums",
            "cashflows",
            "vacancyPenalties")) {
      Field field = EspionageConfig.class.getDeclaredField(name);
      field.setAccessible(true);
      if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
        Map<Object, Object> value = (Map<Object, Object>) field.get(null);
        originalConfigMaps.put(value, new LinkedHashMap<>(value));
      } else originalConfig.put(field, field.get(null));
    }
    YamlConfiguration settings = new YamlConfiguration();
    settings.set("espionage.appointments.change-cooldown-days", 0);
    settings.set("espionage.appointments.build-up-days", 0);
    EspionageConfig.load(settings);
    databases =
        mockConstruction(
            Database.class,
            (database, context) ->
                when(database.saveFactionChecked(any())).thenAnswer(call -> saveSucceeds.get()));
    roleplayPlayers = mockStatic(net.tfminecraft.rpcharacters.managers.PlayerManager.class);
    roleplayPlayers
        .when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(any(Player.class)))
        .thenAnswer(call -> characterData.get(((Player) call.getArgument(0)).getUniqueId()));
    roleplayPlayers
        .when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(any(UUID.class)))
        .thenAnswer(call -> characterData.get(call.getArgument(0)));
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> fixture.online.values());
    when(Bukkit.getOfflinePlayer(any(UUID.class)))
        .thenAnswer(
            call -> {
              OfflinePlayer player = mock(OfflinePlayer.class);
              when(player.getUniqueId()).thenReturn(call.getArgument(0));
              return player;
            });
    home = fixture.saved("home", "Leader");
    home.getOrCreateMainGuild();
    home.addMember("Candidate");
    leader = characterPlayer("Leader");
    candidate = characterPlayer("Candidate");
    visitor = characterPlayer("Visitor");
    aptitudeField = EspionageService.class.getDeclaredField("characterAptitudes");
    aptitudeField.setAccessible(true);
    originalAptitudes = aptitudeField.get(null);
    aptitudeFile = directory.resolve("aptitudes.json");
    EspionageService.loadAptitudes(aptitudeFile);
    EspionageService.initializeFounder(home);
    assertNotNull(home.getEspionage().getSpymaster());
    items = mockStatic(TLibs.class);
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator creator = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(creator);
    items.when(TLibs::getItemAPI).thenReturn(api);
    when(creator.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
  }

  private Player characterPlayer(String name) {
    Player player = fixture.player(name);
    RPCharacter character = mock(RPCharacter.class);
    when(character.getId()).thenReturn("character-" + name);
    when(character.getName()).thenReturn("Character " + name);
    when(character.getStatus()).thenReturn(Status.ALIVE);
    AttributeData attributes = mock(AttributeData.class);
    when(attributes.getAmount(
            any(net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier.class)))
        .thenReturn(6);
    when(character.getAttributeData()).thenReturn(attributes);
    PlayerData data = mock(PlayerData.class);
    when(data.getActiveCharacter()).thenReturn(character);
    when(data.getCharacters()).thenReturn(List.of(character));
    characterData.put(player.getUniqueId(), data);
    characters.put(name, character);
    return player;
  }

  private static UUID accountId(String name) {
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }
}
