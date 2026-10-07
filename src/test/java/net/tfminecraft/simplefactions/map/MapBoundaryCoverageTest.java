package net.tfminecraft.simplefactions.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.fertility.FertilityProvinceResolver;
import net.tfminecraft.simplefactions.map.infra.TrackProvinceCache;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MapBoundaryCoverageTest {
  @TempDir Path directory;

  @Test
  void fertilityUsesTheConfiguredWorldInsteadOfMatchingCoordinatesInAnotherWorld()
      throws Exception {
    try (var ui = new GuiTestFixture()) {
      String previous = Cache.worldName;
      try {
        Cache.worldName = "world";
        var grid = grid(2, 2, new int[] {7, 0, 0, 9});
        var provinces = new ProvinceManager();
        provinces.start(Map.of(7, new Province(7, "plains", 62), 9, new Province(9, "forest", 34)));
        when(ui.plugin.getProvinceGrid()).thenReturn(grid);
        when(ui.plugin.getProvinceManager()).thenReturn(provinces);
        World other = mock(World.class);
        when(other.getName()).thenReturn("other_world");
        assertEquals(62, FertilityProvinceResolver.fertilityAt(new Location(ui.world, 0, 64, 0)));
        assertEquals(0, FertilityProvinceResolver.fertilityAt(new Location(other, 0, 64, 0)));
        assertEquals(
            34, FertilityProvinceResolver.fertilityAt(new Location(ui.world, 1.9, 64, 1.1)));
      } finally {
        Cache.worldName = previous;
      }
    }
  }

  @Test
  void fertilityWithoutALocationWorldOrLivePluginReturnsNoBonus() {
    String previous = Cache.worldName;
    try (var ui = new GuiTestFixture()) {
      Cache.worldName = "world";
      assertEquals(0, FertilityProvinceResolver.fertilityAt((Location) null));
      assertEquals(0, FertilityProvinceResolver.fertilityAt(new Location(null, 0, 64, 0)));
      SimpleFactions.plugin = null;
      assertEquals(0, FertilityProvinceResolver.fertilityAt(new Location(ui.world, 0, 64, 0)));
    } finally {
      Cache.worldName = previous;
    }
  }

  @Test
  void overflowingGridDimensionsAreRejectedBeforeAnUnusableGridIsPublished() throws Exception {
    Path file =
        payload(
            ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(65536)
                .putInt(65536)
                .array());
    assertThrows(IOException.class, () -> ProvinceGrid.load(file.toFile()));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void nonpositiveGridDimensionsAreRejected(int dimension) throws Exception {
    Path file =
        payload(
            ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(dimension)
                .putInt(2)
                .array());
    assertTrue(
        assertThrows(IOException.class, () -> ProvinceGrid.load(file.toFile()))
            .getMessage()
            .contains("dimensions"));
  }

  @Test
  void missingTruncatedAndInconsistentGridsCannotBecomeLiveLookupData() throws Exception {
    assertThrows(IOException.class, () -> ProvinceGrid.load(null));
    assertThrows(
        IOException.class, () -> ProvinceGrid.load(directory.resolve("missing.gz").toFile()));
    Path tooShort = payload(new byte[] {1, 2, 3});
    assertTrue(
        assertThrows(IOException.class, () -> ProvinceGrid.load(tooShort.toFile()))
            .getMessage()
            .contains("header"));
    Path missingCell =
        payload(
            ByteBuffer.allocate(10)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(2)
                .putInt(1)
                .putShort((short) 7)
                .array());
    assertTrue(
        assertThrows(IOException.class, () -> ProvinceGrid.load(missingCell.toFile()))
            .getMessage()
            .contains("body length"));
  }

  @Test
  void loadedGridPreservesUnsignedProvinceIdsAndRowMajorBoundaries() throws Exception {
    ProvinceGrid grid = grid(2, 2, new int[] {7, 65535, 9, 42});
    assertEquals(2, grid.getWidth());
    assertEquals(2, grid.getHeight());
    assertEquals(7, grid.getAt(0, 0));
    assertEquals(65535, grid.getAt(1, 0));
    assertEquals(9, grid.getAt(0, 1));
    assertEquals(42, grid.getAt(1, 1));
    assertEquals(0, grid.getAt(-1, 0));
    assertEquals(0, grid.getAt(0, -1));
    assertEquals(0, grid.getAt(2, 1));
    assertEquals(0, grid.getAt(1, 2));
  }

  @Test
  void liveTrackSnapshotIsSharedImmutableAndRecalculatesOnlyOnChange() {
    TrackProvinceCache live = TrackProvinceCache.live();
    Set<Integer> previous = live.provinces();
    Set<Integer> replacement =
        previous.equals(Set.of(101, 102)) ? Set.of(103, 104) : Set.of(101, 102);
    AtomicInteger recalculations = new AtomicInteger();
    try {
      assertTrue(
          live.refresh(
              () -> replacement, recalculations::incrementAndGet, message -> fail(message)));
      assertSame(live, TrackProvinceCache.live());
      assertEquals(replacement, TrackProvinceCache.live().provinces());
      assertThrows(UnsupportedOperationException.class, () -> live.provinces().add(999));
      assertFalse(
          live.refresh(
              () -> Set.copyOf(replacement),
              recalculations::incrementAndGet,
              message -> fail(message)));
      assertEquals(1, recalculations.get());
    } finally {
      live.refresh(() -> previous, () -> {}, message -> fail(message));
    }
  }

  private ProvinceGrid grid(int width, int height, int[] ids) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(8 + ids.length * 2).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(width).putInt(height);
    for (int id : ids) buffer.putShort((short) id);
    return ProvinceGrid.load(payload(buffer.array()).toFile());
  }

  private Path payload(byte[] bytes) throws IOException {
    Path file = Files.createTempFile(directory, "grid-", ".bin.gz");
    try (var compressed = new GZIPOutputStream(Files.newOutputStream(file))) {
      compressed.write(bytes);
    }
    return file;
  }
}
