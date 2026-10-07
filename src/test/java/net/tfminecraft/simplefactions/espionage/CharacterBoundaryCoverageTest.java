package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeData;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CharacterBoundaryCoverageTest {
  @TempDir Path directory;

  @Test
  void mmocoreSuppliesPermanentAttributesAndRoleplayFillsMissingInstances() {
    try (var fixture = new FactionDomainFixture();
        var roleplay = mockStatic(net.tfminecraft.rpcharacters.managers.PlayerManager.class);
        var mmocore = mockStatic(net.Indyuce.mmocore.api.player.PlayerData.class)) {
      var player = fixture.player("Spy");
      when(Bukkit.getPluginManager().isPluginEnabled("RPCharacters")).thenReturn(true);
      when(Bukkit.getPluginManager().isPluginEnabled("MMOCore")).thenReturn(true);
      var roleplayData = mock(net.tfminecraft.rpcharacters.objects.PlayerData.class);
      var character = mock(RPCharacter.class);
      var roleplayAttributes = mock(AttributeData.class);
      roleplay
          .when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(player))
          .thenReturn(roleplayData);
      when(roleplayData.getActiveCharacter()).thenReturn(character);
      when(character.getAttributeData()).thenReturn(roleplayAttributes);
      when(roleplayAttributes.getAmount(any(AttributeModifier.class))).thenReturn(8);
      var mmoData = mock(net.Indyuce.mmocore.api.player.PlayerData.class);
      var mmoAttributes = mock(PlayerAttributes.class);
      var intelligence = mock(PlayerAttributes.AttributeInstance.class);
      when(intelligence.getBase()).thenReturn(15);
      when(intelligence.getTotal()).thenReturn(99);
      when(mmoData.getAttributes()).thenReturn(mmoAttributes);
      when(mmoAttributes.getInstance("intelligence")).thenReturn(intelligence);
      mmocore.when(() -> net.Indyuce.mmocore.api.player.PlayerData.get(player)).thenReturn(mmoData);
      Map<String, Integer> values = OfficeCharacters.attributes(player);
      assertEquals(EspionageConfig.DEFAULT_WEIGHTS.keySet(), values.keySet());
      assertEquals(15, values.get("intelligence"));
      for (String attribute : EspionageConfig.DEFAULT_WEIGHTS.keySet()) {
        if (!attribute.equals("intelligence")) assertEquals(8, values.get(attribute));
      }
      verify(intelligence, never()).getTotal();
      verify(mmoAttributes, times(EspionageConfig.DEFAULT_WEIGHTS.size())).getInstance(anyString());
    }
  }

  @Test
  void aptitudeRegistryFallsBackToRegularMoveWithoutLosingEarlierCharacters() throws Exception {
    Path file = directory.resolve("aptitudes.json");
    Path pending = directory.resolve("aptitudes.json.tmp");
    var registry = new CharacterAptitudes(file);
    assertEquals(34, registry.aptitude("first", () -> 34));
    AtomicInteger atomicAttempts = new AtomicInteger();
    AtomicInteger fallbackAttempts = new AtomicInteger();
    try (var files =
        mockStatic(
            Files.class,
            call -> {
              if (call.getMethod().getName().equals("move")) {
                Object[] args = call.getRawArguments();
                if (pending.equals(args[0]) && file.equals(args[1])) {
                  CopyOption[] options = (CopyOption[]) args[2];
                  if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                    atomicAttempts.incrementAndGet();
                    throw new AtomicMoveNotSupportedException(
                        pending.toString(), file.toString(), "filesystem boundary");
                  }
                  fallbackAttempts.incrementAndGet();
                }
              }
              return call.callRealMethod();
            })) {
      assertEquals(78, registry.aptitude("second", () -> 78));
    }
    assertEquals(1, atomicAttempts.get());
    assertEquals(1, fallbackAttempts.get());
    assertFalse(Files.exists(pending));
    var restarted = new CharacterAptitudes(file);
    restarted.load();
    assertEquals(34, restarted.aptitude("first", () -> fail("Existing identity rerolled")));
    assertEquals(78, restarted.aptitude("second", () -> fail("Fallback write lost new identity")));
    assertEquals(
        Map.of("first", 34.0, "second", 78.0),
        JsonUtil.GSON.fromJson(Files.readString(file), Map.class));
  }

  @Test
  void failedFallbackDoesNotRetainAnUnpersistedAptitude() throws Exception {
    Path file = directory.resolve("aptitudes.json");
    Path pending = directory.resolve("aptitudes.json.tmp");
    var registry = new CharacterAptitudes(file);
    assertEquals(34, registry.aptitude("existing", () -> 34));
    String original = Files.readString(file);
    try (var files =
        mockStatic(
            Files.class,
            call -> {
              if (call.getMethod().getName().equals("move")) {
                Object[] args = call.getRawArguments();
                if (pending.equals(args[0]) && file.equals(args[1])) {
                  CopyOption[] options = (CopyOption[]) args[2];
                  if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                    throw new AtomicMoveNotSupportedException(
                        pending.toString(), file.toString(), "filesystem boundary");
                  }
                  throw new IOException("replacement refused");
                }
              }
              return call.callRealMethod();
            })) {
      assertEquals(
          "replacement refused",
          assertThrows(IOException.class, () -> registry.aptitude("new", () -> 88)).getMessage());
    }
    assertEquals(original, Files.readString(file));
    assertEquals(34, registry.aptitude("existing", () -> fail("Existing aptitude changed")));
    assertEquals(
        51,
        registry.aptitude("new", () -> 51),
        "Failed save must permit a later durable first roll");
    var restarted = new CharacterAptitudes(file);
    restarted.load();
    assertEquals(51, restarted.aptitude("new", () -> fail("Successful retry was not persisted")));
  }

  @Test
  void unknownFutureCashflowRetainsASignedUsefulRangeWithoutChangingTheSnapshot() {
    String metric = "Guild:traders:Cashflow:FUTURE_CATEGORY";
    var raw = new EspionageMath.Estimate(-80, -40);
    assertFalse(IntelligenceRanges.nonnegative(metric));
    assertEquals(raw, IntelligenceRanges.reasonable(metric, raw));
    var report = new IntelligenceReport();
    report.quality = IntelligenceTier.RELIABLE.key();
    report.estimates.put(metric, raw);
    assertEquals("-80 to -40", report.display(metric));
    assertSame(raw, report.estimates.get(metric));
    assertNull(IntelligenceRanges.reasonable(metric, new EspionageMath.Estimate(-80, 40)));
  }

  @Test
  void trainingDetailsRemainTheDailySnapshotAfterTheLiveQueueChanges() {
    try (var fixture = new FactionDomainFixture()) {
      var first = trainingRegiment("spearmen");
      var second = trainingRegiment("archers");
      var third = trainingRegiment("cavalry");
      fixture.lawGroup("tariffs", Map.of());
      var target = fixture.saved("target", "Leader");
      assertTrue(target.getMilitary().enqueue(first));
      assertTrue(target.getMilitary().enqueue(second));
      assertTrue(target.getMilitary().enqueue(third));
      var report = new IntelligenceReport();
      report.quality = IntelligenceTier.RELIABLE.key();
      ReportDetails.capture(report, target);
      assertEquals(
          List.of("spearmen", "archers", "cavalry"), report.details("training", "training"));
      assertEquals(List.of("Current tariffs"), report.details("laws", "law:tariffs"));
      var changedLaw = fixture.law("tariffs", "tax_cut", Map.of());
      target.getLawHandler().getGroup("tariffs").setCurrent(changedLaw);
      assertTrue(target.getMilitary().cancelQueue(0));
      assertEquals(2, target.getMilitary().getQueue().size());
      var restarted =
          JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(report), IntelligenceReport.class);
      assertEquals(
          List.of("spearmen", "archers", "cavalry"), restarted.details("training", "training"));
      assertEquals(List.of("Current tariffs"), restarted.details("laws", "law:tariffs"));
      assertEquals("tax_cut", target.getLawHandler().getGroup("tariffs").getCurrent().getName());
      var lowerQuality = new IntelligenceReport();
      lowerQuality.quality = IntelligenceTier.RUMOURS.key();
      ReportDetails.capture(lowerQuality, target);
      assertFalse(lowerQuality.details.containsKey("training"));
      assertTrue(lowerQuality.details("training", "training").isEmpty());
    }
  }

  @Test
  void ownRosterCombinesCurrentGuildLeadershipAndOfficeWithoutDuplicatingTheFactionLeader() {
    try (var fixture = new FactionDomainFixture()) {
      var leader = fixture.player("Alice");
      var home = fixture.saved("home", "Alice");
      home.addMember("Ordinary");
      var artisans = fixture.guild(home, "Artisans", "Boris");
      artisans.addMember("Clerk");
      var appointment = new SpecialPositionAssignment();
      appointment.playerName = "Boris";
      appointment.playerId = fixture.player("Boris").getUniqueId();
      appointment.characterId = "boris-character";
      home.getEspionage().appoint(appointment, 80);
      var lines = RosterLore.faction(leader, home);
      assertEquals(1, lines.stream().filter(line -> line.contains("Alice")).count());
      assertTrue(
          lines.stream()
              .anyMatch(
                  line -> line.contains("Guild Leader / Spymaster") && line.endsWith("Boris")));
      assertTrue(lines.stream().anyMatch(line -> line.endsWith("- Ordinary")));
      assertTrue(lines.stream().anyMatch(line -> line.endsWith("- Clerk")));
      var guildLines = RosterLore.guild(leader, artisans);
      int boris =
          java.util.stream.IntStream.range(0, guildLines.size())
              .filter(i -> guildLines.get(i).endsWith("Boris"))
              .findFirst()
              .orElseThrow();
      int clerk =
          java.util.stream.IntStream.range(0, guildLines.size())
              .filter(i -> guildLines.get(i).endsWith("Clerk"))
              .findFirst()
              .orElseThrow();
      assertTrue(boris < clerk);
      assertFalse(guildLines.stream().anyMatch(line -> line.contains("Ordinary")));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nestedGuiCommandsRestoreTheOuterObserversIntelligenceContext(boolean nestedFails) {
    try (var fixture = new FactionDomainFixture();
        var database = mockConstruction(Database.class)) {
      var alice = fixture.player("Alice");
      var bob = fixture.player("Bob");
      var first = fixture.saved("first", "Alice");
      var second = fixture.saved("second", "Bob");
      appointLeader(first, alice);
      appointLeader(second, bob);
      assertNull(EspionageService.report(alice, second));
      assertTrue(
          CommandIntelligence.execute(
              alice,
              () -> {
                java.util.function.Supplier<Boolean> inner =
                    () -> {
                      new SFInventoryHolder(second.getId(), SFGUI.FACTION_VIEW);
                      if (nestedFails) throw new IllegalArgumentException("inner command rejected");
                      return true;
                    };
                if (nestedFails)
                  assertThrows(
                      IllegalArgumentException.class,
                      () -> CommandIntelligence.execute(bob, inner));
                else assertTrue(CommandIntelligence.execute(bob, inner));
                assertNotNull(EspionageService.report(bob, first));
                assertNull(EspionageService.report(alice, second));
                new SFInventoryHolder(first.getId(), SFGUI.FACTION_VIEW);
                assertNotNull(EspionageService.report(alice, second));
                return true;
              }));
      assertFalse(database.constructed().isEmpty());
      first.getEspionage().resetReportsAndRolls();
      new SFInventoryHolder(first.getId(), SFGUI.FACTION_VIEW);
      assertNull(
          EspionageService.report(alice, second),
          "A later click outside either command cannot regenerate reports");
    }
  }

  private void appointLeader(Faction faction, Player leader) {
    var appointment = new SpecialPositionAssignment();
    appointment.playerId = leader.getUniqueId();
    appointment.playerName = leader.getName();
    appointment.characterId = leader.getName() + "-character";
    faction.getEspionage().assignFounder(SpecialPosition.SPYMASTER, appointment, 80);
  }

  private Regiment trainingRegiment(String name) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("name", name);
    config.set("item.material", "PAPER");
    config.set("levy", true);
    config.set("default-slots", 1);
    Regiment regiment = new Regiment(name, config);
    RegimentLoader.oList.add(regiment);
    return regiment;
  }
}
