package net.tfminecraft.simplefactions.war.battle.loot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.enums.*;
import net.tfminecraft.simplefactions.war.battle.events.*;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class BattleLootLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private BattleLootService service;
  private Player alice, bob;
  private List<String> commands;
  private Logger logger;
  private BattleLootMode priorMode;
  private List<String> priorCommands;
  private String priorPath;
  private int priorAmount;
  private Locale priorLocale;
  private MockedStatic<TLibs> tlibs;
  private ItemCreator creator;

  @BeforeEach
  void setup() {
    priorMode = Cache.battleLootMode;
    priorCommands = Cache.battleLootCommands;
    priorPath = Cache.battleLootItemPath;
    priorAmount = Cache.battleLootItemAmount;
    priorLocale = Locale.getDefault();
    fixture = new FactionDomainFixture();
    service = new BattleLootService();
    alice = fixture.player("Alice");
    bob = fixture.player("Bob");
    when(Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);
    when(Bukkit.getPlayer(bob.getUniqueId())).thenReturn(bob);
    when(alice.getWorld()).thenReturn(fixture.ui.world);
    when(bob.getWorld()).thenReturn(fixture.ui.world);
    when(alice.getScoreboard()).thenReturn(mock(org.bukkit.scoreboard.Scoreboard.class));
    when(bob.getScoreboard()).thenReturn(mock(org.bukkit.scoreboard.Scoreboard.class));
    commands = new ArrayList<>();
    logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);
    ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    when(Bukkit.getConsoleSender()).thenReturn(console);
    when(Bukkit.dispatchCommand(eq(console), anyString()))
        .thenAnswer(
            c -> {
              commands.add(c.getArgument(1));
              return true;
            });
    when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
        .thenAnswer(c -> mock(BossBar.class));
    PluginManager plugins = mock(PluginManager.class);
    when(Bukkit.getPluginManager()).thenReturn(plugins);
    doAnswer(
            c -> {
              deliver(c.getArgument(0));
              return null;
            })
        .when(plugins)
        .callEvent(any(Event.class));
    tlibs = mockStatic(TLibs.class);
    ItemAPI api = mock(ItemAPI.class);
    creator = mock(ItemCreator.class);
    tlibs.when(TLibs::getItemAPI).thenReturn(api);
    when(api.getCreator()).thenReturn(creator);
    Cache.battleLootMode = BattleLootMode.COMMAND;
    Cache.battleLootCommands = List.of("/reward %player%");
  }

  @AfterEach
  void close() {
    tlibs.close();
    fixture.close();
    Locale.setDefault(priorLocale);
    Cache.battleLootMode = priorMode;
    Cache.battleLootCommands = priorCommands;
    Cache.battleLootItemPath = priorPath;
    Cache.battleLootItemAmount = priorAmount;
  }

  private void deliver(Event event) throws Exception {
    for (Method method : BattleLootService.class.getMethods())
      if (method.isAnnotationPresent(EventHandler.class)
          && method.getParameterTypes()[0].isInstance(event)) method.invoke(service, event);
  }

  private BattleEndedEvent event(String id, boolean raid, boolean loot, UUID... players) {
    return new BattleEndedEvent(
        id,
        BattleType.FIELD,
        null,
        "attacker",
        Map.of(),
        new LinkedHashSet<>(Arrays.asList(players)),
        BattleEndReason.SIDE_WIN,
        raid,
        loot);
  }

  @Test
  void paysOnlineParticipantsOnceAndSkipsDisabledOrCampaignRaids() {
    when(bob.isOnline()).thenReturn(false);
    service.onBattleEnded(null);
    service.onBattleEnded(event(null, false, true, alice.getUniqueId()));
    service.onBattleEnded(event(" ", false, true, alice.getUniqueId()));
    service.onBattleEnded(event("raid", true, true, alice.getUniqueId()));
    service.onBattleEnded(event("disabled", false, false, alice.getUniqueId()));
    BattleEndedEvent ended =
        event("battle", false, true, alice.getUniqueId(), bob.getUniqueId(), UUID.randomUUID());
    service.onBattleEnded(ended);
    service.onBattleEnded(ended);
    assertEquals(List.of("reward Alice"), commands);
  }

  @Test
  void battleIdentityDoesNotDependOnServerLocale() {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    service.onBattleEnded(event("INLAND", false, true, alice.getUniqueId()));
    service.onBattleEnded(event("inland", false, true, alice.getUniqueId()));
    assertEquals(List.of("reward Alice"), commands);
  }

  @Test
  void restartingTheSameManualBattlePaysEachCompletedFightOnce() {
    Battle battle = BattleFactory.createBlank(BattleType.RAID, "reusable");
    Warband band = Warband.createWithMemberIds("fighters", alice.getUniqueId(), false);
    battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(band);
    assertNull(battle.start());
    BattleEndSupport.endBattle(battle, null);
    BattleEndSupport.endBattle(battle, null);
    assertEquals(List.of("reward Alice"), commands);
    assertNull(battle.start());
    BattleEndSupport.endBattle(battle, null);
    BattleEndSupport.endBattle(battle, null);
    assertEquals(List.of("reward Alice", "reward Alice"), commands);
  }

  @Test
  void commandsUseBothPlaceholdersAndSkipEmptyTemplates() {
    Cache.battleLootCommands =
        Arrays.asList(null, " ", "/", "/give %player% diamond", "tell #player# won");
    service.onBattleEnded(event("commands", false, true, bob.getUniqueId()));
    assertEquals(List.of("give Bob diamond", "tell Bob won"), commands);
    assertEquals("say ", BattleLootService.formatCommand("/say %player%", null));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void absentCommandConfigurationWarnsOncePerBattle(boolean missing) {
    Cache.battleLootCommands = missing ? null : List.of();
    service.onBattleEnded(event("empty", false, true, alice.getUniqueId(), bob.getUniqueId()));
    assertTrue(commands.isEmpty());
    verify(logger, times(1)).warning(contains("commands is empty"));
    service.onBattleEnded(event("next", false, true, alice.getUniqueId()));
    verify(logger, times(2)).warning(contains("commands is empty"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "blank", "air", "unknown", "exception"})
  void invalidItemConfigurationDoesNotConsumeOrRewardAnything(String kind) {
    Cache.battleLootMode = BattleLootMode.ITEM;
    Cache.battleLootItemPath =
        kind.equals("missing") ? null : kind.equals("blank") ? " " : "test.reward";
    if (kind.equals("air")) {
      ItemStack air = new ItemStack(Material.AIR);
      when(creator.getItemFromPath(anyString())).thenReturn(air);
    }
    if (kind.equals("exception"))
      when(creator.getItemFromPath(anyString()))
          .thenThrow(new IllegalArgumentException("bad path"));
    service.onBattleEnded(event("item", false, true, alice.getUniqueId(), bob.getUniqueId()));
    assertEquals(0, diamonds(alice));
    assertEquals(0, diamonds(bob));
    verify(logger, times(1)).warning(anyString());
  }

  @Test
  void absentPluginDoesNotThrowWhileReportingUnavailableLoot() {
    Cache.battleLootCommands = List.of();
    SimpleFactions.plugin = null;
    service.onBattleEnded(event("no_plugin", false, true, alice.getUniqueId()));
    assertTrue(commands.isEmpty());
    verifyNoInteractions(logger);
  }

  @ParameterizedTest
  @ValueSource(ints = {-2, 0, 1, 3, 70})
  void itemAmountIsAtLeastOneAndEveryFighterReceivesTheirOwnReward(int amount) {
    Cache.battleLootMode = BattleLootMode.ITEM;
    Cache.battleLootItemPath = "test.reward";
    Cache.battleLootItemAmount = amount;
    when(creator.getItemFromPath("test.reward")).thenAnswer(c -> new ItemStack(Material.DIAMOND));
    service.onBattleEnded(event("loot", false, true, alice.getUniqueId(), bob.getUniqueId()));
    assertEquals(Math.max(1, amount), diamonds(alice));
    assertEquals(Math.max(1, amount), diamonds(bob));
    assertNotSame(alice.getInventory().getItem(0), bob.getInventory().getItem(0));
  }

  @Test
  void fullInventoryDropsExactlyTheUnstoredReward() {
    Cache.battleLootMode = BattleLootMode.ITEM;
    Cache.battleLootItemPath = "test.reward";
    Cache.battleLootItemAmount = 3;
    when(creator.getItemFromPath("test.reward")).thenAnswer(c -> new ItemStack(Material.DIAMOND));
    for (int i = 0; i < alice.getInventory().getSize(); i++)
      alice.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
    var drops = new ArrayList<ItemStack>();
    when(fixture.ui.world.dropItemNaturally(eq(alice.getLocation()), any(ItemStack.class)))
        .thenAnswer(
            c -> {
              drops.add(c.getArgument(1));
              return null;
            });
    service.onBattleEnded(event("full", false, true, alice.getUniqueId()));
    assertEquals(0, diamonds(alice));
    assertEquals(1, drops.size());
    assertEquals(Material.DIAMOND, drops.get(0).getType());
    assertEquals(3, drops.get(0).getAmount());
  }

  private int diamonds(Player player) {
    return Arrays.stream(player.getInventory().getContents())
        .filter(Objects::nonNull)
        .filter(i -> i.getType() == Material.DIAMOND)
        .mapToInt(ItemStack::getAmount)
        .sum();
  }
}
