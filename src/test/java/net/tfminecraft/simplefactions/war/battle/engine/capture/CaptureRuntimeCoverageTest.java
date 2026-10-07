package net.tfminecraft.simplefactions.war.battle.engine.capture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class CaptureRuntimeCoverageTest {
  private GuiTestFixture ui;
  private MockedStatic<VehicleFramework> vehicleFramework;
  private VehicleManager vehicles;
  private Player alice, bob;
  private Battle battle;
  private PointManager points;
  private Scoreboard main;
  private final List<Board> boards = new ArrayList<>();
  private final List<Dust> aliceDust = new ArrayList<>(), bobDust = new ArrayList<>();
  private int previousCaptureMinimum;

  @BeforeEach
  void setup() {
    previousCaptureMinimum = Cache.battleCaptureMinPlayers;
    Cache.battleCaptureMinPlayers = 1;
    ui = new GuiTestFixture();
    when(Bukkit.getScoreboardCriteria(anyString()))
        .thenAnswer(call -> new NamedCriteria(call.getArgument(0)));
    // Paper initializes these constants through Bukkit; finish that before registering matchers.
    Criteria dummy = Criteria.DUMMY;
    when(ui.world.getName()).thenReturn("capture_world");
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
        .thenAnswer(call -> mock(BossBar.class));
    ScoreboardManager scoreboards = mock(ScoreboardManager.class);
    main = mock(Scoreboard.class);
    when(scoreboards.getMainScoreboard()).thenReturn(main);
    when(scoreboards.getNewScoreboard()).thenAnswer(call -> newBoard().board());
    when(Bukkit.getScoreboardManager()).thenReturn(scoreboards);
    alice = player("Alice", aliceDust);
    bob = player("Bob", bobDust);
    when(Bukkit.getPlayer(any(UUID.class)))
        .thenAnswer(call -> call.getArgument(0).equals(alice.getUniqueId()) ? alice : bob);
    when(ui.world.getPlayers()).thenReturn(List.of(alice, bob));
    when(ui.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    when(ui.world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    vehicleFramework = mockStatic(VehicleFramework.class);
    vehicles = mock(VehicleManager.class);
    vehicleFramework.when(VehicleFramework::getVehicleManager).thenReturn(vehicles);
    battle = BattleFactory.createBlank(BattleType.FIELD, "capture_runtime");
    battle.getSideById("attacker").addBand(new Warband("capture_attackers", alice));
    battle.getSideById("defender").addBand(new Warband("capture_defenders", bob));
    points = battle.getPointManager();
  }

  @AfterEach
  void cleanup() {
    try {
      if (battle != null) battle.end();
    } finally {
      try {
        if (vehicleFramework != null) vehicleFramework.close();
      } finally {
        Cache.battleCaptureMinPlayers = previousCaptureMinimum;
        if (ui != null) ui.close();
      }
    }
  }

  @Test
  void tiedTeamsCannotChangeEitherFullyHeldPoint() {
    addPoint("West", "attacker", new Location(ui.world, 0, 64, 0), 100);
    addPoint("East", "defender", new Location(ui.world, 5, 64, 0), 100);
    when(ui.world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(alice, bob));
    start();

    points.tick();

    assertAll(
        () -> assertEquals(100, points.getPoints().get(0).getCaptureProgress()),
        () -> assertEquals(100, points.getPoints().get(1).getCaptureProgress()),
        () -> assertSame(battle.getSideById("attacker"), points.getPoints().get(0).getController()),
        () ->
            assertSame(battle.getSideById("defender"), points.getPoints().get(1).getController()));
  }

  @Test
  void pointsInDifferentWorldsRenderForThePlayersNearEachPoint() {
    World second = mock(World.class);
    when(second.getName()).thenReturn("capture_second_world");
    when(second.getPlayers()).thenReturn(List.of(bob));
    when(second.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    when(second.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    when(bob.getWorld()).thenReturn(second);
    when(bob.getLocation()).thenReturn(new Location(second, 0, 64, 0));
    when(ui.world.getPlayers()).thenReturn(List.of(alice));
    addPoint("West", "attacker", new Location(ui.world, 0, 64, 0), 100);
    addPoint("East", "defender", new Location(second, 0, 64, 0), 100);
    start();

    tick(10);

    assertAll(
        () -> assertEquals(50, aliceDust.size()),
        () -> assertEquals(50, bobDust.size()),
        () ->
            assertTrue(
                aliceDust.stream().allMatch(d -> d.color().equals(Color.fromRGB(0, 255, 0)))),
        () ->
            assertTrue(bobDust.stream().allMatch(d -> d.color().equals(Color.fromRGB(0, 255, 0)))));
  }

  @Test
  void replacingRuntimePointsFromTheirCurrentListPreservesThemAndResetsCopies() {
    addPoint("West", "attacker", new Location(ui.world, 0, 64, 0), 100);
    start();
    CapturePoint original = points.getPoints().getFirst();

    points.setPoints(points.getPoints());

    assertEquals(1, points.getPoints().size());
    assertNotSame(original, points.getPoints().getFirst());
    assertEquals(original.getId(), points.getPoints().getFirst().getId());
    assertSame(original.getController(), points.getPoints().getFirst().getController());
    assertEquals(original.getCaptureProgress(), points.getPoints().getFirst().getCaptureProgress());
  }

  @Test
  void tickShowsFriendlyAndEnemyScoresAndTitlesAndUnmountRestoresTheScoreboard() {
    addPoint("West", "attacker", new Location(ui.world, 0, 64, 0), 100);
    addPoint("East", "defender", new Location(ui.world, 30, 64, 0), 100);
    when(ui.world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(alice, bob, mock(Entity.class)));
    start();
    points.tick();
    assertEquals(2, boards.size());
    assertEquals(
        Map.of(
            "§aWest - Friendly §f: 100%",
            0, "§cEast - Enemy §f: 100%", 1, "§c=-=-=-=-=-=-=-=-=-=-=", 2),
        boards.getFirst().scores());
    verify(boards.getFirst().objective()).setDisplaySlot(DisplaySlot.SIDEBAR);
    verify(alice).sendTitle(eq(" "), contains("West - FRIENDLY"), eq(0), eq(20), eq(0));
    verify(bob).sendTitle(eq(" "), contains("West - ENEMY"), eq(0), eq(20), eq(0));
    Board previous = boards.getFirst();
    when(vehicles.get(alice)).thenReturn(mock(ActiveVehicle.class));
    points.tick();
    verify(previous.objective()).unregister();
    assertEquals(3, boards.size());
    when(vehicles.get(alice)).thenReturn(null);
    points.tick();
    assertEquals(5, boards.size());
    assertSame(boards.get(3).board(), alice.getScoreboard());
  }

  @Test
  void markerCadenceGeometryAndColorsRestartCleanlyWithTheBattle() {
    addPoint("Beacon", "attacker", new Location(ui.world, 0, 64, 0), 100);
    start();
    tick(9);
    assertTrue(aliceDust.isEmpty());
    tick(1);
    assertEquals(50, aliceDust.size());
    assertEquals(50, bobDust.size());
    assertEquals(new Dust(0.5, 66, 0.5, Color.fromRGB(0, 255, 0), 3.5f), aliceDust.getFirst());
    assertEquals(164, aliceDust.getLast().y());
    assertTrue(bobDust.stream().allMatch(dust -> dust.color().equals(Color.RED)));
    tick(5);
    battle.end();
    assertSame(main, alice.getScoreboard());
    aliceDust.clear();
    bobDust.clear();
    start();
    tick(9);
    assertTrue(aliceDust.isEmpty());
    tick(1);
    assertEquals(50, aliceDust.size());
  }

  @Test
  void markersRequireLoadedChunksAndIncludeTheExactViewRangeBoundary() {
    addPoint("Beacon", "attacker", new Location(ui.world, 0, 64, 0), 100);
    when(alice.getLocation()).thenReturn(new Location(ui.world, 192, 64, 0));
    when(bob.getLocation()).thenReturn(new Location(ui.world, 193, 64, 0));
    when(ui.world.isChunkLoaded(0, 0)).thenReturn(false);
    start();
    tick(10);
    assertTrue(aliceDust.isEmpty());
    assertTrue(bobDust.isEmpty());
    when(ui.world.isChunkLoaded(0, 0)).thenReturn(true);
    tick(10);
    assertEquals(50, aliceDust.size());
    assertTrue(bobDust.isEmpty());
    verify(ui.world, never()).getChunkAt(anyInt(), anyInt());
  }

  @Test
  void sequentialCaptureChangesOnlyFrontPointsAndColorsContestedAndInactiveMarkers() {
    battle.setSequentialCapture(true);
    battle.getSideById("defender").setSpawn(new Location(ui.world, 0, 64, 0));
    battle.getSideById("attacker").setSpawn(new Location(ui.world, 90, 64, 0));
    addPoint("A", "defender", new Location(ui.world, 0, 64, 0), 100);
    addPoint("B", "defender", new Location(ui.world, 30, 64, 0), 50);
    addPoint("C", "attacker", new Location(ui.world, 60, 64, 0), 100);
    addPoint("D", "attacker", new Location(ui.world, 90, 64, 0), 100);
    when(ui.world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenAnswer(
            call -> ((Location) call.getArgument(0)).getX() < 60 ? List.of(alice) : List.of(bob));
    start();
    tick(10);
    assertEquals(
        List.of(100, 40, 100, 100),
        points.getPoints().stream().map(CapturePoint::getCaptureProgress).toList());
    assertEquals(200, aliceDust.size());
    assertEquals(
        List.of(
            Color.fromRGB(128, 128, 128),
            Color.YELLOW,
            Color.fromRGB(128, 128, 128),
            Color.fromRGB(128, 128, 128)),
        List.of(
            aliceDust.get(0).color(),
            aliceDust.get(50).color(),
            aliceDust.get(100).color(),
            aliceDust.get(150).color()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void endingPointsRemovesOnlyTheirObjectiveEvenWithoutAScoreboardManager(
      boolean managerAvailable) {
    addPoint("Beacon", "attacker", new Location(ui.world, 0, 64, 0), 100);
    start();
    points.tick();
    Scoreboard lastBoard = alice.getScoreboard();
    Objective owned = lastBoard.getObjective("pointDummy");
    if (!managerAvailable) when(Bukkit.getScoreboardManager()).thenReturn(null);
    points.end(Arrays.asList(null, alice, bob));
    verify(owned).unregister();
    assertNull(lastBoard.getObjective("pointDummy"));
    assertSame(managerAvailable ? main : lastBoard, alice.getScoreboard());
  }

  @Test
  void disabledCaptureAndEmptyRuntimePointsNeverRenderMarkers() {
    addPoint("Beacon", "attacker", new Location(ui.world, 0, 64, 0), 100);
    battle.setCapturePointsEnabled(false);
    start();
    tick(10);
    assertTrue(aliceDust.isEmpty());
    battle.end();
    battle.clearPoints();
    battle.setCapturePointsEnabled(true);
    start();
    tick(10);
    assertTrue(points.getPoints().isEmpty());
    assertTrue(aliceDust.isEmpty());
  }

  private Player player(String name, List<Dust> output) {
    Player player = ui.player(name);
    when(player.getWorld()).thenReturn(ui.world);
    Scoreboard[] current = {main};
    when(player.getScoreboard()).thenAnswer(call -> current[0]);
    doAnswer(
            call -> {
              current[0] = call.getArgument(0);
              return null;
            })
        .when(player)
        .setScoreboard(any(Scoreboard.class));
    doAnswer(
            call -> {
              Particle.DustOptions dust = call.getArgument(9);
              output.add(
                  new Dust(
                      call.getArgument(1),
                      call.getArgument(2),
                      call.getArgument(3),
                      dust.getColor(),
                      dust.getSize()));
              assertEquals(1, (int) call.getArgument(4));
              assertEquals(true, call.getArgument(10));
              return null;
            })
        .when(player)
        .spawnParticle(
            eq(Particle.DUST),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            any(Particle.DustOptions.class),
            anyBoolean());
    return player;
  }

  private Board newBoard() {
    Scoreboard board = mock(Scoreboard.class);
    Objective objective = mock(Objective.class);
    Map<String, Integer> scores = new LinkedHashMap<>();
    when(board.registerNewObjective(eq("pointDummy"), eq(Criteria.DUMMY), any(Component.class)))
        .thenReturn(objective);
    boolean[] registered = {true};
    when(board.getObjective("pointDummy")).thenAnswer(call -> registered[0] ? objective : null);
    doAnswer(
            call -> {
              registered[0] = false;
              return null;
            })
        .when(objective)
        .unregister();
    when(objective.getScore(anyString()))
        .thenAnswer(
            call -> {
              String label = call.getArgument(0);
              Score score = mock(Score.class);
              doAnswer(
                      changed -> {
                        scores.put(label, changed.getArgument(0));
                        return null;
                      })
                  .when(score)
                  .setScore(anyInt());
              return score;
            });
    Board state = new Board(board, objective, scores);
    boards.add(state);
    return state;
  }

  private void addPoint(String id, String owner, Location location, int progress) {
    battle.addPoint(new CapturePoint(id, location, battle.getSideById(owner), progress));
  }

  private void start() {
    assertNull(battle.start());
    assertTrue(battle.hasStarted());
  }

  private void tick(int count) {
    for (int i = 0; i < count; i++) points.tick();
  }

  private record Board(Scoreboard board, Objective objective, Map<String, Integer> scores) {}

  private record Dust(double x, double y, double z, Color color, float size) {}

  private record NamedCriteria(String name) implements Criteria {
    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }

    @Override
    public RenderType getDefaultRenderType() {
      return RenderType.INTEGER;
    }
  }
}
