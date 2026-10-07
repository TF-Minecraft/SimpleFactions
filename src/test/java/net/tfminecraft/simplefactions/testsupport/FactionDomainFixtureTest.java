package net.tfminecraft.simplefactions.testsupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.LeaderCharacters;
import net.tfminecraft.simplefactions.inactivity.DecayClock;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.loaders.*;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.prestige.MemberPlaytime;
import net.tfminecraft.simplefactions.rest.BannerFetcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.exceptions.base.MockitoException;

class FactionDomainFixtureTest {
  @Test
  void failedConstructionRestoresGlobalsAndOnlyClosesItsOwnMocks() {
    // Isolate thread-scoped leaks if this regression fails, without clearing other tests' mocks.
    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () -> {
          try (Globals original = new Globals()) {
            Cache.worldName = "retained-world";
            Cache.maxMembers = 237;
            Cache.branchUpgradeCost = 321.0;
            RankLoader.ranks = new ArrayList<>(RankLoader.ranks);
            clocks("guildClocks").put("retained-guild", new DecayClock(10, 12345));
            clocks("factionClocks").put("retained-faction", new DecayClock(20, 23456));
            Globals expected = new Globals();
            List<String> externalBanner = List.of("externally-owned-banner");

            try (var external = mockStatic(BannerFetcher.class)) {
              external.when(BannerFetcher::placeholder).thenReturn(externalBanner);

              MockitoException failure =
                  assertThrows(MockitoException.class, FactionDomainFixture::new);

              assertTrue(failure.getMessage().contains("already registered"));
              assertEquals(0, failure.getSuppressed().length);
              assertAll(
                  expected::assertRestored,
                  () -> assertFalse(external.isClosed()),
                  () -> assertSame(externalBanner, BannerFetcher.placeholder()),
                  () -> {
                    try (var freshUi = new GuiTestFixture()) {
                      assertSame(freshUi.world, Bukkit.getWorld("world"));
                      assertEquals(Material.PAPER, new ItemStack(Material.PAPER).getType());
                    }
                  });
            }

            try (var fresh = new FactionDomainFixture()) {
              assertSame(fresh.ui.plugin, SimpleFactions.plugin);
              assertEquals(List.of("white"), BannerFetcher.placeholder());
              assertEquals(
                  List.of("common", "renowned"),
                  RankLoader.ranks.stream().map(rank -> rank.getId()).toList());
            }
            expected.assertRestored();
          }
        });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, DecayClock> clocks(String name) throws ReflectiveOperationException {
    Field field = InactivityService.class.getDeclaredField(name);
    field.setAccessible(true);
    return (Map<String, DecayClock>) field.get(null);
  }

  /** Snapshot only the named globals that the fixture owns; never invoke methods reflectively. */
  private static final class Globals implements AutoCloseable {
    private final Map<Field, Object> values = new LinkedHashMap<>();
    private final Map<Map<Object, Object>, Map<Object, Object>> contents = new IdentityHashMap<>();

    private Globals() throws ReflectiveOperationException {
      capture(SimpleFactions.class, "plugin");
      capture(FactionManager.class, "factions", "map", "inv", "loading");
      capture(RankLoader.class, "ranks");
      capture(TierLoader.class, "oList");
      capture(TitleLoader.class, "titles");
      capture(GuildLoader.class, "map");
      capture(BranchLoader.class, "map");
      capture(UpgradeLoader.class, "map");
      capture(LawLoader.class, "map");
      capture(RegimentLoader.class, "oList");
      capture(RelationLoader.class, "types", "attitudes");
      capture(WarManager.class, "wars");
      capture(MemberPlaytime.class, "probe");
      capture(LeaderCharacters.class, "probe");
      capture(
          InactivityService.class,
          "loaded",
          "saveBlocked",
          "guildClocks",
          "factionClocks",
          "earliestDue");
      capture(LogManager.class, "enabled");
      capture(
          Cache.class,
          "worldName",
          "provincesEnabled",
          "branchUpgradeCost",
          "branchUpgradeExponent",
          "maxWealthPrestige",
          "maxMembers",
          "maxExtraNodeCapacity",
          "maxUntitledProvinces",
          "maxFreeTitles",
          "baseEffects");
    }

    @SuppressWarnings("unchecked")
    private void capture(Class<?> owner, String... names) throws ReflectiveOperationException {
      for (String name : names) {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(null);
        values.put(field, value);
        if (value instanceof Map<?, ?> map) {
          contents.put((Map<Object, Object>) map, new LinkedHashMap<>((Map<Object, Object>) map));
        }
      }
    }

    private void assertRestored() {
      assertAll(
          values.entrySet().stream()
              .map(
                  entry ->
                      () -> {
                        Field field = entry.getKey();
                        if (field.getType().isPrimitive()) {
                          assertEquals(entry.getValue(), field.get(null), field.toString());
                        } else {
                          assertSame(entry.getValue(), field.get(null), field.toString());
                        }
                      }));
      contents.forEach((map, saved) -> assertEquals(saved, map));
    }

    @Override
    public void close() throws ReflectiveOperationException {
      contents.forEach(
          (map, saved) -> {
            map.clear();
            map.putAll(saved);
          });
      for (var entry : values.entrySet()) {
        if (!Modifier.isFinal(entry.getKey().getModifiers())) {
          entry.getKey().set(null, entry.getValue());
        }
      }
    }
  }
}
