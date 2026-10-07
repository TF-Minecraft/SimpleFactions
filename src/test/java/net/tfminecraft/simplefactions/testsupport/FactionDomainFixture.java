package net.tfminecraft.simplefactions.testsupport;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildType;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.identity.LeaderCharacters;
import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.loaders.BranchLoader;
import net.tfminecraft.simplefactions.loaders.GuildLoader;
import net.tfminecraft.simplefactions.loaders.LawLoader;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.objects.PrestigeRank;
import net.tfminecraft.simplefactions.prestige.MemberPlaytime;
import net.tfminecraft.simplefactions.rest.BannerFetcher;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.mockito.MockedStatic;

/** Real faction/handler/configuration graph with scoped Paper and map-service boundaries. */
public final class FactionDomainFixture implements AutoCloseable {
  public final GuiTestFixture ui = new GuiTestFixture();
  public final ProvinceManager provinces = mock(ProvinceManager.class);
  public final MapSystem map = mock(MapSystem.class);
  public final InventoryManager inventory = mock(InventoryManager.class, RETURNS_DEEP_STUBS);
  public final Map<Integer, Province> provinceData = new LinkedHashMap<>();
  public final Map<String, Player> online = new LinkedHashMap<>();
  public final List<Consumer<List<String>>> bannerCallbacks = new ArrayList<>();
  private final Map<Field, Object> originalGlobals = new LinkedHashMap<>();
  private final Map<Map<Object, Object>, Map<Object, Object>> originalMapContents =
      new java.util.IdentityHashMap<>();
  private MockedStatic<BannerFetcher> banners;

  public FactionDomainFixture() {
    try {
      initialise();
    } catch (RuntimeException | Error failure) {
      try {
        close();
      } catch (RuntimeException | Error cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  private void initialise() {
    replace(FactionManager.class, "factions", new ArrayList<Faction>());
    replace(FactionManager.class, "map", map);
    replace(FactionManager.class, "inv", inventory);
    replace(FactionManager.class, "loading", false);
    replace(RankLoader.class, "ranks", new ArrayList<PrestigeRank>());
    replace(TierLoader.class, "oList", new ArrayList<Tier>());
    replace(TitleLoader.class, "titles", new ArrayList<Title>());
    replace(GuildLoader.class, "map", new LinkedHashMap<>());
    replace(BranchLoader.class, "map", new LinkedHashMap<>());
    replace(UpgradeLoader.class, "map", new LinkedHashMap<>());
    replace(LawLoader.class, "map", new LinkedHashMap<>());
    replace(RegimentLoader.class, "oList", new ArrayList<Regiment>());
    replace(RelationLoader.class, "types", new ArrayList<RelationType>());
    replace(RelationLoader.class, "attitudes", new ArrayList<Attitude>());
    replace(WarManager.class, "wars", new ArrayList<>());
    replace(MemberPlaytime.class, "probe", MemberPlaytime.Probe.NONE);
    replace(LeaderCharacters.class, "probe", (LeaderCharacters.Probe) player -> null);
    // Keep the real inactivity calculation, with an explicitly loaded empty persistence snapshot.
    replace(InactivityService.class, "loaded", true);
    replace(InactivityService.class, "saveBlocked", false);
    replace(InactivityService.class, "guildClocks", new HashMap<>());
    replace(InactivityService.class, "factionClocks", new HashMap<>());
    replace(InactivityService.class, "earliestDue", Long.MAX_VALUE);
    replace(LogManager.class, "enabled", false);
    replace(Cache.class, "worldName", "world");
    replace(Cache.class, "provincesEnabled", false);
    replace(Cache.class, "branchUpgradeCost", 100.0);
    replace(Cache.class, "branchUpgradeExponent", 2.0);
    replace(Cache.class, "maxWealthPrestige", 1000);
    replace(Cache.class, "maxMembers", 100);
    replace(Cache.class, "maxExtraNodeCapacity", 10);
    replace(Cache.class, "maxUntitledProvinces", 10);
    replace(Cache.class, "maxFreeTitles", 10);
    replace(Cache.class, "baseEffects", new HashMap<>());
    when(Bukkit.getLogger()).thenReturn(Logger.getLogger("FactionDomainFixture"));
    when(Bukkit.getPluginManager()).thenReturn(mock(PluginManager.class));
    when(Bukkit.getPlayerExact(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
    Chunk origin = mock(Chunk.class);
    when(origin.getWorld()).thenReturn(ui.world);
    when(ui.world.getChunkAt(anyInt(), anyInt())).thenReturn(origin);
    when(ui.plugin.getProvinceManager()).thenReturn(provinces);
    when(provinces.get(anyInt()))
        .thenAnswer(call -> provinceData.getOrDefault(call.getArgument(0), new Province()));
    when(provinces.getProvinces()).thenAnswer(call -> new ArrayList<>(provinceData.values()));
    when(provinces.contains(anyInt()))
        .thenAnswer(call -> provinceData.containsKey(call.getArgument(0)));
    banners = mockStatic(BannerFetcher.class);
    banners.when(BannerFetcher::placeholder).thenAnswer(call -> new ArrayList<>(List.of("white")));
    banners
        .when(() -> BannerFetcher.isPlaceholder(anyList()))
        .thenAnswer(call -> List.of("white").equals(call.getArgument(0)));
    banners
        .when(() -> BannerFetcher.fetch(anyString(), any()))
        .thenAnswer(
            call -> {
              bannerCallbacks.add(call.getArgument(1));
              return null;
            });
    rank("common", 1, 0);
    rank("renowned", 2, 10000);
    tier("landless", 0, 0);
    tier("province", 1, 10);
    tier("county", 2, 50);
    tier("duchy", 3, 100);
    YamlConfiguration types = new YamlConfiguration();
    types.set("realm.base", true);
    types.set("guild.default", true);
    for (String id : List.of("realm", "guild"))
      GuildLoader.map.put(id, new GuildType(id, types.getConfigurationSection(id)));
    YamlConfiguration branches = new YamlConfiguration();
    branches.set("commerce.name", "Commerce");
    branches.set("commerce.allowed-types", List.of("realm", "guild"));
    branches.set("commerce.group", 0);
    branches.set("commerce.modifiers", List.of("TRADE_POWER 5 2", "PRESTIGE 3 1"));
    BranchLoader.map.put(
        "commerce", new Branch("commerce", branches.getConfigurationSection("commerce")));
    relationType("neutral", Map.of("default", true));
    relationType("vassal", Map.of("vassal", true, "link", "overlord"));
    relationType("overlord", Map.of("overlord", true, "link", "vassal"));
    YamlConfiguration attitude = new YamlConfiguration();
    attitude.set("neutral.default", true);
    RelationLoader.attitudes.add(
        new Attitude("neutral", attitude.getConfigurationSection("neutral")));
  }

  public Player player(String name) {
    Player player = ui.player(name);
    when(player.isOnline()).thenReturn(true);
    online.put(name, player);
    return player;
  }

  /** Lets callback tests update the actual open inventory while HTTP fetching remains isolated. */
  public void useRealBannerRefresh() {
    banners.when(() -> BannerFetcher.refreshOpenView(any(), any(), anyString(), any()))
        .thenCallRealMethod();
  }

  public PrestigeRank rank(String id, int level, double minimum) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(id + ".name", id);
    yaml.set(id + ".level", level);
    yaml.set(id + ".minimum-prestige", minimum);
    PrestigeRank rank = new PrestigeRank(id, yaml.getConfigurationSection(id));
    RankLoader.ranks.add(rank);
    return rank;
  }

  public Tier tier(String id, int level, int prestige) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(id + ".tier", level);
    yaml.set(id + ".prestige", prestige);
    Tier tier = new Tier(id, yaml.getConfigurationSection(id));
    TierLoader.oList.add(tier);
    return tier;
  }

  public RelationType relationType(String id, Map<String, ?> options) {
    YamlConfiguration yaml = new YamlConfiguration();
    options.forEach((key, value) -> yaml.set(id + "." + key, value));
    RelationType type = new RelationType(id, yaml.getConfigurationSection(id));
    RelationLoader.types.add(type);
    return type;
  }

  public LawGroup lawGroup(String id, Map<String, ?> options) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(id + ".laws.current.name", "Current " + id);
    options.forEach((key, value) -> yaml.set(id + ".laws.current." + key, value));
    LawGroup group = new LawGroup(id, yaml.getConfigurationSection(id));
    LawLoader.map.put(id, group);
    return group;
  }

  public void provincesEnabled(boolean enabled) {
    replace(Cache.class, "provincesEnabled", enabled);
  }

  public Law law(String groupId, String id, Map<String, ?> options) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(id + ".name", id);
    options.forEach((key, value) -> yaml.set(id + "." + key, value));
    var law = new Law(groupId, id, yaml.getConfigurationSection(id));
    LawGroup template = LawLoader.map.get(groupId);
    if (template != null) template.getLaws().put(id, law);
    for (Faction faction : FactionManager.factions) {
      LawGroup group = faction.getLawHandler().getGroup(groupId);
      if (group != null) group.getLaws().put(id, law);
    }
    return law;
  }

  public Regiment regiment(String id, boolean levy, int slots, double upkeep) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(id + ".item.material", "PAPER");
    yaml.set(id + ".default-slots", slots);
    yaml.set(id + ".upkeep", upkeep);
    yaml.set(id + ".levy", levy);
    yaml.set(id + ".expansion-time", 2);
    Regiment regiment = new Regiment(id, yaml.getConfigurationSection(id));
    RegimentLoader.oList.add(regiment);
    return regiment;
  }

  public FactionData data(String id, String leader) {
    FactionData data = new FactionData();
    data.id = id;
    data.name = id;
    data.leader = leader;
    data.rgb = "1,2,3";
    data.rulerTitle = "Governor";
    data.government = "Community";
    data.culture = "Riverfolk";
    data.religion = "Old Faith";
    data.banner = new ArrayList<>(List.of("white"));
    data.capital = -1;
    data.citizenTax = 5.0;
    data.guildTax = 10.0;
    data.vassalTax = 20.0;
    data.dividendTax = 15.0;
    data.tariffs = 30.0;
    data.extraNodeCapacity = 0.0;
    return data;
  }

  public Faction saved(String id, String leader) {
    return saved(data(id, leader));
  }

  public Faction saved(FactionData data) {
    List<Title> titles = data.titles.stream().map(TitleLoader::getById).toList();
    List<Modifier> modifiers = Database.loadModifiers(data.prestigeModifiers);
    Faction faction =
        new Faction(
            data.id,
            data.rgb,
            data.provinces.stream().map(Number::intValue).toList(),
            titles,
            data.leader,
            data.name,
            data.rulerTitle,
            new ArrayList<>(data.banner),
            data.government,
            data.culture,
            data.religion,
            data.extraNodeCapacity.intValue(),
            new ArrayList<>(modifiers),
            data.citizenTax,
            data.guildTax,
            data.vassalTax,
            data.dividendTax,
            data.tariffs,
            data.specificTaxes,
            data.capital,
            data.laws,
            data.governmentData);
    FactionManager.factions.add(faction);
    for (GuildData guild : data.guilds)
      faction.getGuildHandler().addGuild(new Guild(guild, faction));
    return faction;
  }

  public Guild guild(Faction faction, String id, String leader) {
    GuildData data = new GuildData();
    data.id = id;
    data.name = id;
    data.leader = leader;
    data.type = "guild";
    data.rgb = "4,5,6";
    data.capital = -1;
    data.banner = new ArrayList<>(List.of("white"));
    data.members = new ArrayList<>(List.of(leader));
    Guild guild = new Guild(data, faction);
    faction.getGuildHandler().addGuild(guild);
    return guild;
  }

  public Title title(String id, String tier, int... provinceIds) {
    JsonObject data = new JsonObject();
    data.addProperty("name", id);
    data.addProperty("rgb", "4,5,6");
    JsonArray provinces = new JsonArray();
    for (int province : provinceIds) provinces.add(province);
    data.add("provinces", provinces);
    Title title = new Title(TierLoader.getByString(tier), id, data);
    TitleLoader.getTitles().add(title);
    return title;
  }

  public void subject(Faction overlord, Faction vassal) {
    overlord.setRelation(
        vassal,
        new Relation(RelationLoader.getType("vassal"), RelationLoader.getDefaultAttitude()));
    vassal.setRelation(
        overlord,
        new Relation(RelationLoader.getType("overlord"), RelationLoader.getDefaultAttitude()));
  }

  /** Restores named global fields only; it never calls production methods through reflection. */
  @SuppressWarnings("unchecked")
  private void replace(Class<?> owner, String name, Object value) {
    try {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
        Map<Object, Object> contents = (Map<Object, Object>) field.get(null);
        originalMapContents.putIfAbsent(contents, new LinkedHashMap<>(contents));
        contents.clear();
        contents.putAll((Map<?, ?>) value);
        return;
      }
      originalGlobals.putIfAbsent(field, field.get(null));
      field.set(null, value);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
  }

  @Override
  public void close() {
    try {
      if (banners != null) banners.close();
    } finally {
      try {
        originalMapContents.forEach(
            (contents, original) -> {
              contents.clear();
              contents.putAll(original);
            });
        for (Map.Entry<Field, Object> entry : originalGlobals.entrySet()) {
          try {
            entry.getKey().set(null, entry.getValue());
          } catch (IllegalAccessException failure) {
            throw new AssertionError(failure);
          }
        }
      } finally {
        ui.close();
      }
    }
  }
}
