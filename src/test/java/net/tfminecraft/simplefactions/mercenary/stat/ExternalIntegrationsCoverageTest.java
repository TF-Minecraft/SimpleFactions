package net.tfminecraft.simplefactions.mercenary.stat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.SharedStat;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.trait.Trait;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.api.GatewayClient;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.CompanyUpgradeLoader;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryEligibility;
import net.tfminecraft.simplefactions.mercenary.company.RpCharactersMercenaryTraitProbe;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class ExternalIntegrationsCoverageTest {
  private static final NamespacedKey HEALTH_KEY =
      new NamespacedKey("simplefactions", "mercenary_max_health");
  private FactionDomainFixture fixture;
  private MercenaryCompany company;
  private Player player;
  private PluginManager plugins;
  private Locale previousLocale;
  private final Map<Field, Object> globals = new LinkedHashMap<>();
  private Map<UUID, MercenaryStatPlan> previousApplied;
  private Map<String, Upgrade> previousUpgrades;

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    previousLocale = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
    for (String name : List.of("gate", "applier", "missingLogged")) {
      Field field = MercenaryStatService.class.getDeclaredField(name);
      field.setAccessible(true);
      globals.put(field, field.get(null));
    }
    previousApplied = new HashMap<>(applied());
    MercenaryStatService.reset();
    previousUpgrades = new LinkedHashMap<>(CompanyUpgradeLoader.get());
    new CompanyUpgradeLoader()
        .load(Path.of("src/main/resources/Guilds/company-upgrades.yml").toFile());
    Faction faction = fixture.saved("home", "Ivar");
    Guild guild = faction.getOrCreateMainGuild();
    Regiment regiment = fixture.regiment("mercenary", false, 2, 1);
    company = new MercenaryCompany(guild, "Hired Blades", regiment, 0);
    guild.setCompany(company);
    regiment.setCurrentSlots(2);
    assertTrue(company.enlist("Sigrun"));
    company.getUpgrade("company_health").setLevel(4);
    company.getUpgrade("company_mana").setLevel(3);
    company.getUpgrade("company_mana_regen").setLevel(2);
    player = fixture.player("Sigrun");
    when(Bukkit.getPlayer(any(UUID.class)))
        .thenAnswer(
            call ->
                fixture.online.values().stream()
                    .filter(online -> online.getUniqueId().equals(call.getArgument(0)))
                    .findFirst()
                    .orElse(null));
    when(Bukkit.getOnlinePlayers()).thenAnswer(call -> fixture.online.values());
    plugins = Bukkit.getPluginManager();
    when(plugins.isPluginEnabled(anyString())).thenReturn(true);
    MercenaryStatService.setGate(name -> name.equalsIgnoreCase("Sigrun"));
  }

  @AfterEach
  void close() throws Exception {
    applied().clear();
    applied().putAll(previousApplied);
    for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    CompanyUpgradeLoader.get().clear();
    CompanyUpgradeLoader.get().putAll(previousUpgrades);
    Locale.setDefault(previousLocale);
    GatewayProvider.response = null;
    fixture.close();
  }

  @Test
  void mythicAndHealthModifiersCarryThePlanAndStripOnlyTheirOwnContributions() {
    Health health = health(player);
    AttributeModifier foreign = modifier("another_plugin", "healthy", 8);
    AttributeModifier stale = modifier("simplefactions", "mercenary_max_health", 99);
    health.modifiers.addAll(List.of(foreign, stale));
    MMOPlayerData data = mock(MMOPlayerData.class);
    List<List<?>> arguments = new ArrayList<>();
    try (MockedStatic<MMOPlayerData> players = mockStatic(MMOPlayerData.class);
        MockedConstruction<StatModifier> modifiers =
            mockConstruction(
                StatModifier.class,
                (modifier, context) -> arguments.add(new ArrayList<>(context.arguments())))) {
      players.when(() -> MMOPlayerData.getOrNull(player)).thenReturn(data);
      MythicLibStatApplier applier = new MythicLibStatApplier();
      assertTrue(applier.isAvailable());

      applier.apply(player, new MercenaryStatPlan(2, 3, 0.2));

      assertEquals(
          List.of("simplefactions_mercenary_max_mana", SharedStat.MAX_MANA, 3.0, ModifierType.FLAT),
          arguments.get(0));
      assertEquals(
          List.of(
              "simplefactions_mercenary_mana_regeneration",
              SharedStat.MANA_REGENERATION,
              0.2,
              ModifierType.FLAT),
          arguments.get(1));
      for (StatModifier modifier : modifiers.constructed()) verify(modifier).register(data);
      assertEquals(2, health.modifiers.size());
      assertTrue(health.modifiers.contains(foreign));
      assertFalse(health.modifiers.contains(stale));
      AttributeModifier bonus =
          health.modifiers.stream()
              .filter(m -> HEALTH_KEY.equals(m.getKey()))
              .findFirst()
              .orElseThrow();
      assertEquals(2, bonus.getAmount());
      assertEquals(AttributeModifier.Operation.ADD_NUMBER, bonus.getOperation());
      assertEquals(EquipmentSlotGroup.ANY, bonus.getSlotGroup());

      applier.strip(player);
      applier.strip(player);

      assertEquals(List.of(foreign), health.modifiers);
      for (StatModifier modifier : modifiers.constructed()) verify(modifier).unregister(data);
    }
  }

  @Test
  void statModifierIdentifiersAreStableUnderTurkishLocale() {
    health(player);
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    MMOPlayerData data = mock(MMOPlayerData.class);
    List<List<?>> arguments = new ArrayList<>();
    try (MockedStatic<MMOPlayerData> players = mockStatic(MMOPlayerData.class);
        MockedConstruction<StatModifier> modifiers =
            mockConstruction(
                StatModifier.class,
                (modifier, context) -> arguments.add(new ArrayList<>(context.arguments())))) {
      players.when(() -> MMOPlayerData.getOrNull(player)).thenReturn(data);

      new MythicLibStatApplier().apply(player, new MercenaryStatPlan(0, 0, 1));

      assertEquals(1, modifiers.constructed().size());
      assertEquals("simplefactions_mercenary_mana_regeneration", arguments.getFirst().getFirst());
    }
  }

  @Test
  void joiningAfterACompanionPluginWasDisabledStillRemovesPersistedHealth() {
    Health health = health(player);
    AttributeModifier foreign = modifier("another_plugin", "healthy", 8);
    health.modifiers.addAll(
        List.of(foreign, modifier("simplefactions", "mercenary_max_health", 2)));
    when(plugins.isPluginEnabled("MMOCore")).thenReturn(false);
    MercenaryStatService.setApplier(new MythicLibStatApplier());

    new MercenaryStatService.Listener().onJoin(new PlayerJoinEvent(player, "joined"));

    assertEquals(List.of(foreign), health.modifiers);
    assertFalse(MercenaryStatService.isApplied(player));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clearingAfterCompanionShutdownUsesOnlyTheStillAvailableProvider(boolean mythicLibRemains) {
    Health health = health(player);
    AttributeModifier foreign = modifier("another_plugin", "healthy", 8);
    health.modifiers.add(foreign);
    MMOPlayerData data = mock(MMOPlayerData.class);
    try (MockedStatic<MMOPlayerData> players = mockStatic(MMOPlayerData.class);
        MockedConstruction<StatModifier> modifiers = mockConstruction(StatModifier.class)) {
      players.when(() -> MMOPlayerData.getOrNull(player)).thenReturn(data);
      MercenaryStatService.setApplier(new MythicLibStatApplier());
      assertTrue(MercenaryStatService.apply(player));
      assertEquals(2, modifiers.constructed().size());
      assertEquals(2, health.modifiers.size());
      players.clearInvocations();
      when(plugins.isPluginEnabled("MMOCore")).thenReturn(false);
      when(plugins.isPluginEnabled("MythicLib")).thenReturn(mythicLibRemains);

      new MercenaryStatService.Listener().onQuit(new PlayerQuitEvent(player, "left"));

      assertEquals(List.of(foreign), health.modifiers);
      assertFalse(MercenaryStatService.isApplied(player));
      players.verify(() -> MMOPlayerData.getOrNull(player), times(mythicLibRemains ? 1 : 0));
      for (StatModifier modifier : modifiers.constructed()) {
        verify(modifier, times(mythicLibRemains ? 1 : 0)).unregister(data);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"join", "quit", "death", "battle"})
  void lifecycleEventsStripAppliedCompanyBonuses(String event) {
    RecordingStatApplier applier = new RecordingStatApplier();
    MercenaryStatService.setApplier(applier);
    assertTrue(MercenaryStatService.apply(player));
    assertEquals(new MercenaryStatPlan(2, 3, 0.2), MercenaryStatService.appliedTo(player));
    MercenaryStatService.Listener listener = new MercenaryStatService.Listener();

    switch (event) {
      case "join" -> listener.onJoin(new PlayerJoinEvent(player, "joined"));
      case "quit" -> listener.onQuit(new PlayerQuitEvent(player, "left"));
      case "death" ->
          listener.onDeath(
              new PlayerDeathEvent(player, mock(DamageSource.class), new ArrayList<>(), 0, "died"));
      case "battle" ->
          listener.onBattleEnded(
              new BattleEndedEvent(
                  "battle",
                  BattleType.FIELD,
                  7,
                  "attackers",
                  Map.of(),
                  Set.of(player.getUniqueId())));
      default -> throw new AssertionError(event);
    }

    assertFalse(MercenaryStatService.isApplied(player));
    assertEquals(MercenaryStatPlan.EMPTY, MercenaryStatService.appliedTo(player));
    assertEquals(1, applier.strips);
  }

  @Test
  void clearingAllDropsOnlineAndOfflineRecordsAndSupportsShutdownWithoutAServer() {
    RecordingStatApplier applier = new RecordingStatApplier();
    MercenaryStatService.setApplier(applier);
    MercenaryStatService.setGate(name -> true);
    Player other = fixture.player("Edda");
    assertTrue(company.enlist("Edda"));
    assertTrue(MercenaryStatService.apply(player));
    assertTrue(MercenaryStatService.apply(other));
    fixture.online.remove("Edda");

    MercenaryStatService.clearAll();

    assertFalse(MercenaryStatService.isApplied(player));
    assertFalse(MercenaryStatService.isApplied(other));
    assertEquals(1, applier.strips, "Only the online player has live attributes to strip");
    assertTrue(MercenaryStatService.apply(player));
    when(Bukkit.getServer()).thenReturn(null);

    MercenaryStatService.clearAll();

    assertFalse(MercenaryStatService.isApplied(player));
    assertEquals(1, applier.strips);
  }

  @Test
  void missingCompanionDependenciesLogOnceAndNullParticipantsRemainHarmless() {
    RecordingStatApplier applier = new RecordingStatApplier();
    applier.available = false;
    MercenaryStatService.setApplier(applier);
    Logger logger = mock(Logger.class);
    when(fixture.ui.plugin.getLogger()).thenReturn(logger);
    SimpleFactions.plugin = null;
    assertFalse(MercenaryStatService.apply(player));
    verifyNoInteractions(logger);
    SimpleFactions.plugin = fixture.ui.plugin;

    assertFalse(MercenaryStatService.apply(player));
    assertFalse(MercenaryStatService.apply(player));

    verify(logger)
        .info(
            "[SimpleFactions] MythicLib or MMOCore missing; mercenary company buffs not applied.");
    assertTrue(applier.applications.isEmpty());
    assertEquals(0, applier.strips);
    MercenaryStatService.clear(null);
    MercenaryStatService.clearParticipants(null);
    assertFalse(MercenaryStatService.apply(null));
    assertFalse(MercenaryStatService.isApplied(null));
    assertEquals(MercenaryStatPlan.EMPTY, MercenaryStatService.appliedTo(null));
    assertEquals(MercenaryStatPlan.EMPTY, MercenaryStatService.planFor(null));
    MercenaryStatService.setGate(null);
    assertEquals(MercenaryStatPlan.EMPTY, MercenaryStatService.planFor("Sigrun"));
    MercenaryStatService.setApplier(null);
    assertFalse(MercenaryStatService.apply(player));
  }

  @Test
  void offlineParticipantsLoseBookkeepingWithoutTryingToStripPlayerAttributes() {
    RecordingStatApplier applier = new RecordingStatApplier();
    MercenaryStatService.setApplier(applier);
    assertTrue(MercenaryStatService.apply(player));
    fixture.online.clear();

    MercenaryStatService.clearParticipants(Set.of(player.getUniqueId()));

    assertFalse(MercenaryStatService.isApplied(player));
    assertEquals(0, applier.strips);
    assertTrue(MercenaryStatService.apply(player));
    when(Bukkit.getServer()).thenReturn(null);
    MercenaryStatService.clearParticipants(Set.of(player.getUniqueId()));
    assertFalse(MercenaryStatService.isApplied(player));
    assertEquals(0, applier.strips);
  }

  @Test
  void healthCleanupSurvivesAnUnloadedMmoProfileAndAnAbsentAttribute() {
    Health health = health(player);
    MMOPlayerData data = mock(MMOPlayerData.class);
    try (MockedStatic<MMOPlayerData> players = mockStatic(MMOPlayerData.class);
        MockedConstruction<StatModifier> modifiers = mockConstruction(StatModifier.class)) {
      MythicLibStatApplier applier = new MythicLibStatApplier();
      applier.apply(player, new MercenaryStatPlan(2, 3, 1));
      assertEquals(1, health.modifiers.size());
      assertTrue(modifiers.constructed().isEmpty(), "No MMO profile means no mana registration");
      applier.apply(player, MercenaryStatPlan.EMPTY);
      assertTrue(health.modifiers.isEmpty());
      players.when(() -> MMOPlayerData.getOrNull(player)).thenReturn(data);
      applier.apply(player, new MercenaryStatPlan(2, 3, 0));
      assertEquals(1, modifiers.constructed().size());
      verify(modifiers.constructed().getFirst()).register(data);
      players.when(() -> MMOPlayerData.getOrNull(player)).thenReturn(null);

      applier.strip(player);

      assertTrue(health.modifiers.isEmpty());
      verify(modifiers.constructed().getFirst(), never()).unregister(any());
      when(player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(null);
      players.when(() -> MMOPlayerData.getOrNull(player)).thenReturn(data);
      applier.apply(player, MercenaryStatPlan.EMPTY);
      applier.strip(player);
      assertEquals(1, modifiers.constructed().size());
    }
  }

  @Test
  void activeCharacterTraitsControlEligibilityWhileUnavailablePlayersRemainUnknown() {
    RpCharactersMercenaryTraitProbe probe = new RpCharactersMercenaryTraitProbe();
    assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.check("Offline"));
    when(plugins.isPluginEnabled("RPCharacters")).thenReturn(false);
    assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.check("Sigrun"));
    when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
    try (MockedStatic<PlayerManager> manager = mockStatic(PlayerManager.class)) {
      manager.when(() -> PlayerManager.get(player)).thenReturn(null);
      assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.check("Sigrun"));
      PlayerData data = mock(PlayerData.class);
      manager.when(() -> PlayerManager.get(player)).thenReturn(data);
      assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.check("Sigrun"));
      RPCharacter character = mock(RPCharacter.class);
      when(data.hasActiveCharacter()).thenReturn(true);
      when(data.getActiveCharacter()).thenReturn(character);
      Trait merchant = mock(Trait.class);
      when(merchant.getId()).thenReturn("merchant");
      when(character.getTraits()).thenReturn(Arrays.asList(null, merchant));
      assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.check("Sigrun"));
      Trait mercenary = mock(Trait.class);
      when(mercenary.getId()).thenReturn("MERCENARY");
      when(character.getTraits()).thenReturn(Arrays.asList(null, merchant, mercenary));
      assertEquals(MercenaryEligibility.Status.ELIGIBLE, probe.check("Sigrun"));
    }
    when(Bukkit.getPluginManager()).thenReturn(null);
    assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.check("Sigrun"));
    when(Bukkit.getServer()).thenReturn(null);
    assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.check("Sigrun"));
  }

  @Test
  void gatewayBridgePreservesRequestAndProviderSuccessOrFailure() throws Exception {
    Class<?> bridge = gatewayBridge();
    List<List<String>> requests = new ArrayList<>();
    GatewayProvider.response =
        request -> {
          requests.add(request);
          return new GatewayReply(true, "{\"saved\":true}", null);
        };
    GatewayView saved = request(bridge, "POST", "/plugin/state", "{\"id\":7}");
    assertTrue(saved.ok);
    assertEquals("{\"saved\":true}", saved.body);
    assertNull(saved.error);
    assertEquals(List.of(List.of("POST", "/plugin/state", "{\"id\":7}")), requests);
    GatewayProvider.response = request -> new GatewayReply(true, null, null);
    assertEquals("", request(bridge, "GET", "/empty", null).body);
    GatewayProvider.response = request -> new GatewayReply(false, null, "request denied");
    GatewayView denied = request(bridge, "DELETE", "/protected", null);
    assertFalse(denied.ok);
    assertNull(denied.body);
    assertEquals("request denied", denied.error);
    GatewayProvider.response = request -> new GatewayReply(false, null, null);
    assertEquals("request failed", request(bridge, "GET", "/failed", null).error);
    GatewayProvider.response =
        request -> {
          throw new IllegalStateException("provider offline");
        };
    assertEquals(
        "TFMCWeb gateway unavailable: provider offline",
        request(bridge, "GET", "/status", null).error);
    GatewayProvider.response =
        request -> {
          throw new IllegalStateException("  ");
        };
    assertEquals(
        "TFMCWeb gateway unavailable: IllegalStateException",
        request(bridge, "GET", "/status", null).error);
    GatewayClient.Result absent = GatewayClient.request("GET", "/status", null);
    assertFalse(absent.ok, "The isolated provider must not leak into the application classloader");
    assertTrue(absent.error.contains("ProvinceSystemGateway"));
  }

  private Health health(Player target) {
    AttributeInstance attribute = mock(AttributeInstance.class);
    List<AttributeModifier> modifiers = new ArrayList<>();
    when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(attribute);
    when(attribute.getModifiers()).thenAnswer(call -> List.copyOf(modifiers));
    doAnswer(
            call -> {
              modifiers.add(call.getArgument(0));
              return null;
            })
        .when(attribute)
        .addModifier(any(AttributeModifier.class));
    doAnswer(
            call -> {
              modifiers.remove(call.getArgument(0));
              return null;
            })
        .when(attribute)
        .removeModifier(any(AttributeModifier.class));
    return new Health(modifiers);
  }

  private static AttributeModifier modifier(String namespace, String id, double amount) {
    return new AttributeModifier(
        new NamespacedKey(namespace, id),
        amount,
        AttributeModifier.Operation.ADD_NUMBER,
        EquipmentSlotGroup.ANY);
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, MercenaryStatPlan> applied() throws Exception {
    Field field = MercenaryStatService.class.getDeclaredField("applied");
    field.setAccessible(true);
    return (Map<UUID, MercenaryStatPlan>) field.get(null);
  }

  private record Health(List<AttributeModifier> modifiers) {}

  /** An external provider API with the same reflective field contract as TFMCWeb. */
  public static final class GatewayReply {
    public final boolean ok;
    public final String body;
    public final String error;

    public GatewayReply(boolean ok, String body, String error) {
      this.ok = ok;
      this.body = body;
      this.error = error;
    }
  }

  public static final class GatewayProvider {
    static Function<List<String>, Object> response;

    public static Object request(String method, String path, String body) {
      return response.apply(Arrays.asList(method, path, body));
    }
  }

  private record GatewayView(boolean ok, String body, String error) {}

  private static GatewayView request(Class<?> bridge, String method, String path, String body)
      throws Exception {
    Object reply =
        bridge
            .getMethod("request", String.class, String.class, String.class)
            .invoke(null, method, path, body);
    return new GatewayView(
        (Boolean) reply.getClass().getField("ok").get(reply),
        (String) reply.getClass().getField("body").get(reply),
        (String) reply.getClass().getField("error").get(reply));
  }

  private static Class<?> gatewayBridge() throws Exception {
    String gateway = "net.tfminecraft.tfmcweb.api.ProvinceSystemGateway";
    Map<String, byte[]> definitions = new HashMap<>();
    definitions.put(
        gateway,
        new ByteBuddy()
            .subclass(Object.class)
            .name(gateway)
            .defineMethod("request", Object.class, Modifier.PUBLIC | Modifier.STATIC)
            .withParameters(String.class, String.class, String.class)
            .intercept(MethodDelegation.to(GatewayProvider.class))
            .make()
            .getBytes());
    for (Class<?> type : List.of(GatewayClient.class, GatewayClient.Result.class)) {
      try (var bytes =
          type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
        assertNotNull(bytes);
        definitions.put(type.getName(), bytes.readAllBytes());
      }
    }
    ClassLoader isolated =
        new ClassLoader(GatewayClient.class.getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
              if (!definitions.containsKey(name)) return super.loadClass(name, resolve);
              Class<?> type = findLoadedClass(name);
              if (type == null) {
                byte[] bytes = definitions.get(name);
                type =
                    defineClass(
                        name, bytes, 0, bytes.length, GatewayClient.class.getProtectionDomain());
              }
              if (resolve) resolveClass(type);
              return type;
            }
          }
        };
    return isolated.loadClass(GatewayClient.class.getName());
  }
}
