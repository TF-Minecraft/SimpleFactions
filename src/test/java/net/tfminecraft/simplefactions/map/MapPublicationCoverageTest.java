package net.tfminecraft.simplefactions.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.*;
import net.tfminecraft.simplefactions.map.provinces.*;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.testsupport.*;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Exercises actual disk payloads with the HTTP boundary and Bukkit task queues isolated. */
class MapPublicationCoverageTest {
  private FactionDomainFixture fixture;
  private PersistenceFilesFixture files;
  private MockedStatic<RestServer> rest;
  private MapSystem map;
  private Faction faction;
  private final List<String> uploads = new ArrayList<>();
  private final List<String> regenerations = new ArrayList<>();
  private final List<JsonObject> sentQueues = new ArrayList<>();
  private boolean oldChronicle;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    fixture = new FactionDomainFixture();
    faction = fixture.saved("home", "Leader");
    faction.getOrCreateMainGuild();
    fixture.guild(faction, "merchants", "Trader");
    fixture.provinceData.put(7, new Province(7, "PLAINS", 50));
    files.write("Input/county.json", "{}");
    files.write("Input/duchy.json", "{}");
    files.write("Input/kingdom.json", "{}");
    files.write("Input/empire.json", "{}");
    Files.createDirectories(files.root.resolve("MapAPI"));
    Files.createDirectories(files.root.resolve("Data"));
    oldChronicle = Cache.chronicleEnabled;
    Cache.chronicleEnabled = true;
    rest = mockStatic(RestServer.class);
    rest.when(() -> RestServer.upload(anyString(), any(File.class)))
        .thenAnswer(
            call -> {
              String kind = call.getArgument(0);
              File file = call.getArgument(1);
              assertTrue(file.isFile(), kind + " payload must exist before upload");
              uploads.add(kind);
              if (kind.equals("queue"))
                sentQueues.add(
                    JsonParser.parseString(Files.readString(file.toPath())).getAsJsonObject());
              return null;
            });
    rest.when(() -> RestServer.commenceRegen(anyString()))
        .thenAnswer(
            call -> {
              regenerations.add(call.getArgument(0));
              return null;
            });
    map = new MapSystem();
  }

  @AfterEach
  void close() throws Exception {
    try {
      rest.close();
      Cache.chronicleEnabled = oldChronicle;
      fixture.close();
    } finally {
      files.close();
    }
  }

  private JsonElement read(String name) throws Exception {
    return JsonParser.parseString(Files.readString(files.root.resolve("MapAPI/" + name + ".json")));
  }

  private void completeUpload() {
    List<Runnable> pending = new ArrayList<>(fixture.ui.asyncTasks);
    fixture.ui.asyncTasks.clear();
    pending.forEach(Runnable::run);
    fixture.ui.runTasks();
  }

  @Test
  void mutationsDuringAnUploadRemainQueuedForTheNextPublication() throws Exception {
    map.enqueue("nation", "first");
    map.enqueue("nation", "first");
    map.updateMap();
    assertEquals(
        List.of("first"),
        read("queue").getAsJsonObject().getAsJsonArray("nation").asList().stream()
            .map(JsonElement::getAsString)
            .toList());
    map.enqueue("nation", "next");
    map.updateMap();
    assertEquals(
        1,
        fixture.ui.asyncTasks.size(),
        "Do not overwrite the queue file while it is being uploaded");
    completeUpload();
    map.updateMap();
    completeUpload();
    assertEquals(2, sentQueues.size());
    assertTrue(sentQueues.get(1).has("nation"));
    assertEquals("next", sentQueues.get(1).getAsJsonArray("nation").get(0).getAsString());
    assertEquals(List.of("queued", "queued"), regenerations);
  }

  @Test
  void repeatedMutationOfTheSameNationDuringUploadIsNotDropped() {
    map.enqueue("nation", "same");
    map.updateMap();
    map.enqueue("nation", "same");
    completeUpload();
    map.updateMap();
    completeUpload();
    assertTrue(
        sentQueues.get(1).has("nation"),
        "A second change of the same nation still needs publication");
  }

  @Test
  void quietCyclesPublishLiveDataAndOnlyShipNationWhenLeaderNamesChange() throws Exception {
    fixture.provincesEnabled(true);
    map.tick();
    assertEquals(1, fixture.ui.asyncTasks.size());
    completeUpload();
    assertEquals(List.of("province_data", "guilds", "chronicle"), uploads);
    assertEquals(List.of("trade"), regenerations);
    assertEquals(
        7, read("province_data").getAsJsonArray().get(0).getAsJsonObject().get("id").getAsInt());
    assertEquals(2, read("guilds").getAsJsonArray().size());
    uploads.clear();
    map.markLeaderNamesChanged();
    map.updateLiveData();
    completeUpload();
    assertTrue(uploads.contains("nation"));
    assertTrue(read("nation").getAsJsonObject().has("home"));
    uploads.clear();
    map.updateLiveData();
    completeUpload();
    assertFalse(uploads.contains("nation"));
    for (int i = 0; i < 300; i++) map.tick();
    assertTrue(fixture.ui.asyncTasks.isEmpty());
    map.tick();
    assertEquals(1, fixture.ui.asyncTasks.size());
    completeUpload();
  }

  @Test
  void disabledProvinceTicksDoNothingAndHourlyRefreshCannotBeStarvedByQueuedChanges() {
    map.tick();
    assertTrue(fixture.ui.asyncTasks.isEmpty());
    fixture.provincesEnabled(true);
    for (int i = 0; i < 3601; i++) {
      map.enqueue("province", Integer.toString(i));
      map.tick();
      completeUpload();
    }
    assertTrue(
        sentQueues.getLast().getAsJsonArray("nation").asList().stream()
            .anyMatch(e -> e.getAsString().equals(faction.getRGB())));
    assertEquals("queued", regenerations.getLast());
  }

  @Test
  void fullRegenerationPersistsNationsAndUploadsOptionalRegionsOnlyWhenPresent() throws Exception {
    map.fullRegen();
    assertTrue(uploads.isEmpty());
    completeUpload();
    assertEquals(List.of("fullregen"), regenerations);
    assertFalse(uploads.contains("regions"));
    assertEquals(
        "home", read("nation").getAsJsonObject().getAsJsonObject("home").get("id").getAsString());
    files.write("Input/regions.json", "{}");
    uploads.clear();
    map.uploadAll();
    assertTrue(
        uploads.containsAll(
            List.of(
                "province_data",
                "guilds",
                "chronicle",
                "nation",
                "map_markers",
                "county",
                "duchy",
                "kingdom",
                "empire",
                "regions")));
    map.enqueue("nation", "old");
    map.clear();
    map.updateMap();
    completeUpload();
    assertEquals(0, sentQueues.getLast().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"province_data", "guilds", "map_markers", "chronicle"})
  void failedPayloadExportLogsItsNameAndDoesNotThrow(String payload) throws Exception {
    Logger logger = mock(Logger.class);
    when(Bukkit.getLogger()).thenReturn(logger);
    Files.createDirectory(files.root.resolve("MapAPI/" + payload + ".json"));
    switch (payload) {
      case "province_data" -> map.exportProvinces();
      case "guilds" -> map.exportGuilds();
      case "map_markers" -> map.exportMarkers();
      case "chronicle" -> map.exportChronicle();
    }
    verify(logger).severe("[SimpleFactions] Failed to export " + payload + ".json");
  }

  @Test
  void disabledChronicleDoesNotReplaceAnExistingExport() throws Exception {
    files.write("MapAPI/chronicle.json", "sentinel");
    Cache.chronicleEnabled = false;
    map.exportChronicle();
    assertEquals("sentinel", Files.readString(files.root.resolve("MapAPI/chronicle.json")));
  }

  @Test
  void nationCompilerSkipsMalformedFilesWithoutLosingGoodUtf8Names() throws Exception {
    files.write("Data/good.json", "{\"id\":\"realm\",\"name\":\"雪山\"}");
    files.write("Data/bad.json", "[bad");
    files.write("Data/ignored.txt", "not json");
    Compiler compiler = new Compiler();
    compiler.exportAllFactionsToNationJson();
    assertEquals(1, read("nation").getAsJsonObject().size());
    assertEquals(
        "雪山", read("nation").getAsJsonObject().getAsJsonObject("realm").get("name").getAsString());
    files.remove("Data");
    compiler.exportAllFactionsToNationJson();
    assertEquals(0, read("nation").getAsJsonObject().size());
    Files.createDirectory(files.root.resolve("Data"));
    compiler.exportAllFactionsToNationJson();
    assertEquals(0, read("nation").getAsJsonObject().size());
    files.remove("MapAPI/nation.json");
    Files.createDirectory(files.root.resolve("MapAPI/nation.json"));
    assertDoesNotThrow(compiler::exportAllFactionsToNationJson);
    Files.createDirectory(files.root.resolve("MapAPI/queue.json"));
    assertDoesNotThrow(() -> compiler.exportQueue(new HashMap<>()));
  }

  @Test
  void occupationChangesQueueBothRealmsAndDeduplicateNationColors() throws Exception {
    var data = fixture.data("other", "Other");
    data.rgb = "8,9,10";
    Faction other = fixture.saved(data);
    other.getOrCreateMainGuild();
    var war = new net.tfminecraft.simplefactions.war.core.War(17, faction, other);
    map.enqueueOccupationFromWar(null);
    map.enqueueOccupationFromWar(war);
    map.enqueueOccupationFromWar(war);
    map.updateMap();
    completeUpload();
    assertEquals(
        Set.of(faction.getRGB(), other.getRGB()),
        new HashSet<>(
            sentQueues.getFirst().getAsJsonArray("nation").asList().stream()
                .map(JsonElement::getAsString)
                .toList()));
    assertEquals(2, sentQueues.getFirst().getAsJsonArray("nation").size());
  }

  @Test
  void provincePayloadOmitsNegligibleTradeAndRetainsOccupationAndRoundedProduction() {
    Province province = fixture.provinceData.get(7);
    ProvinceDataEntry value = new ProvinceDataEntry(faction.getOrCreateMainGuild());
    value.setTrade(1.239);
    value.setProduction(2.345);
    ProvinceDataEntry tiny = new ProvinceDataEntry(faction.getOrCreateMainGuild());
    tiny.setTrade(.09);
    province.getAllData().put("merchants", value);
    province.getAllData().put("tiny", tiny);
    JsonObject row = Compiler.provinceToJson(province, "occupier");
    assertEquals("occupier", row.get("occupied_by").getAsString());
    assertFalse(row.getAsJsonObject("trade").has("tiny"));
    assertEquals(
        1.24, row.getAsJsonObject("trade").getAsJsonObject("merchants").get("trade").getAsDouble());
    assertEquals(
        2.35,
        row.getAsJsonObject("trade").getAsJsonObject("merchants").get("production").getAsDouble());
    assertFalse(Compiler.provinceToJson(province, null).has("occupied_by"));
  }
}
