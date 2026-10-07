package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.tiers.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

class TitlePersistenceCoverageTest {
  private FactionDomainFixture domain;
  private PersistenceFilesFixture files;
  private Tier county, duchy;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    domain = new FactionDomainFixture();
    TierLoader.oList.clear();
    county = tier("county", 1);
    duchy = tier("duchy", 2);
  }

  private Tier tier(String id, int level) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("tier", level);
    Tier tier = new Tier(id, config);
    TierLoader.oList.add(tier);
    return tier;
  }

  @AfterEach
  void close() throws Exception {
    if (domain != null) domain.close();
    if (files != null) files.close();
  }

  @Test
  void creatingAndReloadingTitlesPreservesOtherEntriesAndCompositeMembership() throws Exception {
    files.write(
        "Input/county.json",
        "{\"old\":{\"name\":\"Old\",\"rgb\":\"1,2,3\",\"provinces\":[9],\"extra\":42}}");
    Title first =
        TitleLoader.createNewTitle(county, "first", "First", "3,4,5", List.of(1, 2), null, false);
    Title second =
        TitleLoader.createNewTitle(
            duchy, "second", "Second", "4,5,6", null, List.of("first", "old"), true);
    assertNotNull(first);
    assertNotNull(second);
    assertEquals(
        42,
        JsonParser.parseString(Files.readString(files.root.resolve("Input/county.json")))
            .getAsJsonObject()
            .getAsJsonObject("old")
            .get("extra")
            .getAsInt());
    second.setName("Renamed");
    assertTrue(TitleLoader.saveTitle(second));
    new TitleLoader().reload();
    assertEquals(3, TitleLoader.getTitles().size());
    assertEquals(List.of(1, 2), TitleLoader.getById("FIRST").getProvinces());
    Title reloaded = TitleLoader.getById("second");
    assertEquals("Renamed", reloaded.getName());
    assertTrue(reloaded.isComposite());
    assertTrue(reloaded.isTitleComplete());
    assertEquals(List.of("first", "old"), reloaded.getTitles());
    assertSame(reloaded, TitleLoader.getByTitle(TitleLoader.getById("first")));
  }

  @Test
  void failureToSaveNewTitleDoesNotPublishItInMemory() throws Exception {
    files.write("Input", "ordinary file blocks the directory");
    Title created =
        TitleLoader.createNewTitle(
            county, "failed", "Failed", "1,2,3", List.of(1), List.of(), false);
    assertNull(created);
    assertNull(TitleLoader.getById("failed"));
    assertEquals(
        "ordinary file blocks the directory", Files.readString(files.root.resolve("Input")));
  }

  @Test
  void malformedExistingTierFileIsPreservedWhenCreatingATitle() throws Exception {
    Path path = files.write("Input/county.json", "{ not json");
    Title created =
        assertDoesNotThrow(
            () ->
                TitleLoader.createNewTitle(
                    county, "failed", "Failed", "1,2,3", List.of(1), null, false));
    assertNull(created);
    assertTrue(TitleLoader.getTitles().isEmpty());
    assertEquals("{ not json", Files.readString(path));
  }

  @Test
  void malformedReloadPreservesAllPreviouslyLoadedTitles() throws Exception {
    Path path =
        files.write("Input/county.json", "{\"first\":{\"name\":\"First\",\"provinces\":[1]}}");
    new TitleLoader().loadAll();
    Title original = TitleLoader.getById("first");
    assertNotNull(original);
    Files.writeString(
        path, "{\"replacement\":{\"name\":\"Replacement\",\"provinces\":[2]},\"invalid\":42}");
    new TitleLoader().reload();
    assertEquals(List.of(original), TitleLoader.getTitles());
    assertSame(original, TitleLoader.getById("first"));
    assertNull(TitleLoader.getById("replacement"));
  }

  @Test
  void blockedTemporaryFileLeavesOriginalTierDataUntouched() throws Exception {
    Path file =
        files.write("Input/county.json", "{\"first\":{\"name\":\"First\",\"provinces\":[1]}}");
    new TitleLoader().loadAll();
    Title first = TitleLoader.getById("first");
    first.setName("Changed");
    Files.createDirectory(files.root.resolve("Input/county.json.tmp"));
    files.write("Input/county.json.tmp/keep", "keep");
    assertFalse(TitleLoader.saveTitle(first));
    assertTrue(Files.readString(file).contains("First"));
    assertEquals("keep", Files.readString(files.root.resolve("Input/county.json.tmp/keep")));
  }

  @Test
  void tierFilenamesAreIndependentOfTheServerLocale() throws Exception {
    Tier viscount = tier("VISCOUNT", 3);
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      Files.createDirectories(files.root.resolve("Input"));
      Title created =
          TitleLoader.createNewTitle(
              viscount, "north", "North", "1,2,3", List.of(), List.of(), false);
      assertNotNull(created);
      assertTrue(Files.exists(files.root.resolve("Input/viscount.json")));
      new TitleLoader().loadAll();
      assertNotNull(TitleLoader.getById("north"));
    } finally {
      Locale.setDefault(original);
    }
  }
}
