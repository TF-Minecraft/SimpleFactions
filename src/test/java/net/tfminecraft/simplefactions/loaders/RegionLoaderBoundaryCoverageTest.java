package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegionLoaderBoundaryCoverageTest {
  @TempDir Path folder;
  private JsonObject before;

  @BeforeEach
  void snapshot() {
    before = new JsonObject();
    for (var region : RegionLoader.getRegions()) {
      JsonObject data = new JsonObject();
      data.addProperty("name", region.getName());
      JsonArray provinces = new JsonArray();
      region.getProvinces().forEach(provinces::add);
      data.add("provinces", provinces);
      before.add(region.getId(), data);
    }
    RegionLoader.loadFrom(
        JsonParser.parseString("{\"old\":{\"name\":\"Old\",\"provinces\":[3]}}").getAsJsonObject());
  }

  @AfterEach
  void restore() {
    RegionLoader.loadFrom(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "[]", "\"wrong shape\"", "{\"partial\":"})
  void aFailedReloadKeepsAllPreviouslyPublishedIndices(String invalid) throws Exception {
    var live = RegionLoader.getById("old");
    Path file = Files.writeString(folder.resolve("regions.json"), invalid);
    RegionLoader.loadAll(file.toFile());
    assertSame(live, RegionLoader.getById("old"));
    assertSame(live, RegionLoader.getByProvince(3));
    assertEquals(List.of(live), RegionLoader.getRegions());
  }

  @Test
  void aSuccessfulReloadReplacesAllIndicesAndSkipsMalformedOptionalRows() throws Exception {
    Path file =
        Files.writeString(
            folder.resolve("regions.json"),
            """
{" ":{}, "ignored":[], "NORTH":{"name":"  Northern Reach  ","provinces":[7,7,"bad",null,{},8]},
 "south":{"name":" ","provinces":[9]}, "unnamed":{"name":[],"provinces":[]}}
""");
    RegionLoader.loadAll(file.toFile());
    assertNull(RegionLoader.getById("old"));
    assertNull(RegionLoader.getByProvince(3));
    assertNull(RegionLoader.getById(null));
    assertNull(RegionLoader.getById(" "));
    var north = RegionLoader.getById("north");
    assertEquals("Northern Reach", north.getName());
    assertEquals(Set.of(7, 8), north.getProvinces());
    assertTrue(north.containsProvince(8));
    assertFalse(north.containsProvince(9));
    assertSame(north, RegionLoader.getByProvince(7));
    assertEquals("south", RegionLoader.getByProvince(9).getName());
    assertEquals("unnamed", RegionLoader.getById("unnamed").getName());
    assertEquals(3, RegionLoader.getRegions().size());
    assertThrows(UnsupportedOperationException.class, () -> RegionLoader.getRegions().clear());
    assertThrows(UnsupportedOperationException.class, () -> north.getProvinces().clear());
  }

  @Test
  void defaultInputPathPublishesTheFileContent() throws Exception {
    Path file = Path.of("plugins/SimpleFactions/Input/regions.json");
    byte[] previous =
        Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(file) : null;
    List<Path> createdDirectories = new ArrayList<>();
    for (Path ancestor = file.getParent();
        ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS);
        ancestor = ancestor.getParent()) {
      createdDirectories.add(ancestor);
    }
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, "{\"default_path\":{\"provinces\":[31]}}");
      RegionLoader.loadAll();
      assertEquals("default_path", RegionLoader.getByProvince(31).getId());
      assertNull(RegionLoader.getById("old"));
    } finally {
      if (previous == null) Files.deleteIfExists(file);
      else Files.write(file, previous);
      for (Path created : createdDirectories) {
        if (!Files.isDirectory(created, LinkOption.NOFOLLOW_LINKS)) continue;
        try {
          Files.deleteIfExists(created);
        } catch (DirectoryNotEmptyException retained) {
          // Preserve contents added by another fixture; ancestors are visited deepest first.
        }
      }
    }
  }

  @Test
  void removingTheOptionalFileOrExplicitlyClearingRemovesAllRegions() throws Exception {
    RegionLoader.loadAll(folder.resolve("missing.json").toFile());
    assertTrue(RegionLoader.getRegions().isEmpty());
    assertNull(RegionLoader.getByProvince(3));
    RegionLoader.loadFrom(null);
    assertTrue(RegionLoader.getRegions().isEmpty());
    RegionLoader.loadAll(null);
    assertTrue(RegionLoader.getRegions().isEmpty());
  }
}
