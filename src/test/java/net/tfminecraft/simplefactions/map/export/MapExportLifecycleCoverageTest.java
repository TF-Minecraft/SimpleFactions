package net.tfminecraft.simplefactions.map.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.denareconomy.DenarEconomy;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.denareconomy.managers.MoneyManager;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class MapExportLifecycleCoverageTest {
  @TempDir Path temporary;
  private FactionDomainFixture fixture;
  private MockedStatic<DenarEconomy> economy;
  private MoneyManager money;
  private ProvinceManager provinces;
  private Map<Object, Object> installationKinds;
  private final Map<Field, Object> globals = new LinkedHashMap<>();

  @BeforeEach
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    economy = mockStatic(DenarEconomy.class);
    money = mock(MoneyManager.class);
    economy.when(DenarEconomy::getMoneyManager).thenReturn(money);
    when(money.getServerBal(Accounts.POUCH)).thenReturn(25.5);
    when(money.getServerBal(Accounts.BANK)).thenReturn(35.75);
    installationKinds = new LinkedHashMap<>(kindMap());
    remember(InstallationConfigLoader.class, "consentProximityBlocks");
    remember(InstallationConfigLoader.class, "transferRequestTimeoutSeconds");
    YamlConfiguration config = new YamlConfiguration();
    config.set("consent-proximity-blocks", 20);
    config.set("transfer-request-timeout-seconds", 60);
    for (InstallationKind kind : InstallationKind.values()) {
      String key = kind.getCommandName();
      config.set(key + ".daily-upkeep", 1);
      config.set(key + ".construction-time", 1);
      config.set(key + ".radius", 2);
      config.createSection(key + ".slots");
    }
    Path configFile = temporary.resolve("installations.yml");
    config.save(configFile.toFile());
    InstallationConfigLoader.load(configFile.toFile());
    for (String name :
        List.of("mapRef", "chapterId", "chapterName", "settlementLargePopulationThreshold"))
      remember(Cache.class, name);
    Cache.mapRef = "test-map";
    Cache.chapterId = "riverlands";
    Cache.chapterName = "Riverlands";
    Cache.settlementLargePopulationThreshold = 1;
    provinces = new ProvinceManager();
    Map<Integer, Province> data = new LinkedHashMap<>();
    for (int id = 1; id <= 8; id++) data.put(id, new Province(id, "PLAINS", 50, id * 10, id * 20));
    data.put(9, new Province(9, "SEA", 0));
    provinces.start(data);
    when(fixture.ui.plugin.getProvinceManager()).thenReturn(provinces);
  }

  @AfterEach
  void close() throws Exception {
    kindMap().clear();
    kindMap().putAll(installationKinds);
    for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    economy.close();
    fixture.close();
  }

  @Test
  void publishedMarkersContainSettlementsInstallationsFortControlAndActiveWar() throws Exception {
    WorldState state = worldState();
    Path output = temporary.resolve("nested/markers.json");
    Markers.export(output.toFile());
    JsonObject root = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
    assertEquals("test-map", root.get("map_id").getAsString());
    assertEquals("riverlands", root.get("chapter_id").getAsString());
    assertEquals("Riverlands", root.get("chapter_name").getAsString());
    assertDoesNotThrow(() -> Instant.parse(root.get("exported_at").getAsString()));
    assertEquals(1, root.get("settlement_large_population_threshold").getAsInt());
    JsonObject capital = find(root.getAsJsonArray("settlements"), "capital");
    assertEquals("faction_capital", capital.get("kind").getAsString());
    assertEquals("home", capital.get("faction_id").getAsString());
    assertEquals(1, capital.get("population").getAsInt());
    assertEquals("small", capital.get("marker_size").getAsString());
    assertEquals(10, capital.get("center_x").getAsInt());
    assertEquals(20, capital.get("center_z").getAsInt());
    assertEquals(List.of(1), ints(capital.getAsJsonArray("provinces")));
    JsonObject town = find(root.getAsJsonArray("settlements"), "town");
    assertEquals("settlement", town.get("kind").getAsString());
    assertEquals("large", town.get("marker_size").getAsString());
    assertEquals(2, town.get("population").getAsInt());
    assertEquals(4, root.getAsJsonArray("installations").size());
    assertEquals(
        "fort", find(root.getAsJsonArray("installations"), "fort").get("kind").getAsString());
    assertEquals(1, find(root.getAsJsonArray("installations"), "station").get("level").getAsInt());
    JsonObject fort = find(root.getAsJsonArray("forts"), "fort");
    assertEquals(List.of(2, 4), ints(fort.getAsJsonArray("zoc_provinces")));
    JsonObject war = root.getAsJsonArray("wars").get(0).getAsJsonObject();
    assertEquals("7", war.get("id").getAsString());
    assertEquals(List.of(1, 2, 4), ints(war.getAsJsonArray("campaign_provinces")));
    assertEquals(10, war.getAsJsonObject("attacker_capital").get("center_x").getAsInt());
    assertEquals(4, war.getAsJsonObject("defender_capital").get("province_id").getAsInt());
    assertFalse(war.getAsJsonObject("defender_capital").has("center_x"));
    assertSame(state.home, state.traders.getFaction());
    assertEquals(150, state.home.getWealth(), 1e-9);
  }

  @Test
  void chronicleReflectsRealSubjectsGuildWealthPopulationAndWarMembership() throws Exception {
    WorldState state = worldState();
    state.home.setRank(RankLoader.getByLevel(2));
    state.home.setPrestigeModifiers(
        new ArrayList<>(
            Arrays.asList(
                null, new Modifier(null, 2.0, false), new Modifier("Historic Award", 12.0, true))));
    Instant captured = Instant.parse("2026-10-07T00:00:00Z");
    JsonObject root = ChronicleSnapshot.build(FactionManager.factions, 8, 900, captured);
    assertEquals(captured.toString(), root.get("captured_at").getAsString());
    assertTrue(root.get("complete").getAsBoolean());
    assertEquals(8, root.get("server_day").getAsInt());
    assertEquals(900, root.get("day_progress_seconds").getAsInt());
    JsonObject global = root.getAsJsonObject("global");
    assertEquals(3, global.get("faction_count").getAsInt());
    assertEquals(4, global.get("guild_count").getAsInt());
    assertEquals(25.5, global.get("pouch_wealth").getAsDouble());
    assertEquals(35.75, global.get("player_bank_wealth").getAsDouble());
    assertEquals(1, global.get("active_wars").getAsInt());
    JsonObject home = find(root.getAsJsonArray("factions"), "home");
    assertEquals(List.of("subject"), strings(home.getAsJsonArray("subjects")));
    assertEquals(List.of("7"), strings(home.getAsJsonArray("wars")));
    assertEquals(2, home.get("settlements").getAsInt());
    assertEquals(3, home.get("population").getAsInt());
    assertEquals(4, home.get("installations").getAsInt());
    assertEquals(1, home.get("forts").getAsInt());
    assertEquals(50, home.getAsJsonObject("wealth_breakdown").get("traders").getAsDouble());
    assertEquals(
        12, home.getAsJsonObject("prestige_breakdown").get("Historic Award").getAsDouble());
    assertEquals(9500, home.get("rank_down_at").getAsDouble());
    JsonObject subject = find(root.getAsJsonArray("factions"), "subject");
    assertEquals("home", subject.get("overlord").getAsString());
    assertEquals(List.of("7"), strings(subject.getAsJsonArray("wars")));
    assertEquals(50, find(root.getAsJsonArray("guilds"), "traders").get("bank").getAsDouble());
    assertEquals(0, root.getAsJsonArray("events").size());
    assertDoesNotThrow(() -> new Gson().toJson(root));
    state.home.setPrestigeModifiers(new ArrayList<>());
    Path output = temporary.resolve("live/chronicle.json");
    ChronicleExport.export(output.toFile());
    JsonObject written = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
    assertEquals(3, written.getAsJsonArray("factions").size());
    assertEquals(4, written.getAsJsonArray("guilds").size());
    assertEquals(150, state.home.getWealth(), 1e-9);
  }

  @Test
  void absentOrFailingEconomyDegradesOnlyItsAggregateFigures() {
    Faction home = fixture.saved("home", "Alice");
    assertNull(WarMapExporter.exportCapitalCoords(home));
    home.getBank().deposit(25.0);
    economy.when(DenarEconomy::getMoneyManager).thenReturn(null);
    JsonObject root = ChronicleSnapshot.build(FactionManager.factions, 1, 0, Instant.EPOCH);
    assertEquals(0, root.getAsJsonObject("global").get("pouch_wealth").getAsDouble());
    assertEquals(0, root.getAsJsonObject("global").get("player_bank_wealth").getAsDouble());
    assertEquals(25, find(root.getAsJsonArray("factions"), "home").get("bank").getAsDouble());
    economy
        .when(DenarEconomy::getMoneyManager)
        .thenThrow(new LinkageError("economy plugin unavailable"));
    JsonObject failed = ChronicleSnapshot.build(FactionManager.factions, 1, 0, Instant.EPOCH);
    assertEquals(0, failed.getAsJsonObject("global").get("pouch_wealth").getAsDouble());
    assertEquals(25, find(failed.getAsJsonArray("factions"), "home").get("wealth").getAsDouble());
  }

  @Test
  void fortExportHandlesMissingLocationsUnownedNeighborsAndAmbiguousWars() {
    WorldState state = worldState();
    assertEquals(List.of(), ZocRealm.computeZocProvinces(state.home, 999));
    assertEquals(List.of(), ZocRealm.computeZocProvincesForExport(null, state.home, List.of()));
    assertSame(state.home, ZocRealm.resolveExportControllerFaction(null, state.home, List.of()));
    assertNull(ZocRealm.resolveExportControllerFaction(state.fort, null, List.of()));
    assertNull(ZocRealm.selectPrimaryWarForFort(state.fort, null));
    War unrelated = new War(8, state.home, state.enemy);
    unrelated.setCampaignProvinces(List.of(6, 7));
    unrelated.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(6, CampaignBattleKind.SIEGE, true, "other-fort")));
    assertNull(ZocRealm.selectPrimaryWarForFort(state.fort, Arrays.asList(null, unrelated)));
    assertEquals(
        List.of(2),
        ZocRealm.computeZocProvincesForExport(state.fort, state.home, List.of(unrelated)));
    War scheduled = new War(9, state.home, state.enemy);
    scheduled.setCampaignProvinces(List.of(6, 7));
    scheduled.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(2, CampaignBattleKind.SIEGE, false, "fort")));
    assertSame(
        scheduled, ZocRealm.selectPrimaryWarForFort(state.fort, List.of(unrelated, scheduled)));
    assertSame(
        state.home,
        ZocRealm.resolveExportControllerFaction(state.fort, state.home, List.of(scheduled)));
    Installation unnamed =
        new Installation("", "Unnamed import", InstallationKind.FORT, 2, 20, 40, 0);
    assertSame(
        state.home,
        ZocRealm.resolveExportControllerFaction(unnamed, state.home, List.of(state.war)));
    assertNull(ZocRealm.selectPrimaryWarForFort(unnamed, List.of(unrelated)));
    assertTrue(OccupationMapExport.occupiedProvinceIds(null).isEmpty());
    assertTrue(OccupationMapExport.occupierByProvince(null).isEmpty());
    assertTrue(OccupationMapExport.nationRgbsToEnqueue(null, null).isEmpty());
    assertTrue(WarMapExporter.exportWars(null).isEmpty());
    assertNull(WarMapExporter.exportCapitalCoords(null));
    state.war.setOccupiedByAttacker(Arrays.asList(null, 2, 4));
    assertEquals(
        Map.of(2, "home", 4, "home"), OccupationMapExport.occupierByProvince(List.of(state.war)));
  }

  @Test
  void legacyWarWithoutPushTargetStillExportsTheInvasionDirection() {
    WorldState state = worldState();
    state.war.setPushTarget(null);
    JsonObject row = WarMapExporter.exportWars(List.of(state.war)).get(0).getAsJsonObject();
    assertEquals("toward_objective", row.get("push_target").getAsString());
    assertEquals("7", row.get("id").getAsString());
    assertEquals(List.of("home", "subject", "enemy"), strings(row.getAsJsonArray("belligerents")));
  }

  @Test
  void occupationRefreshSkipsMissingProvinceIdsWhileRetainingValidOwners() {
    WorldState state = worldState();
    state.enemy.setRGB("4,5,6");
    state.war.setOccupiedByAttacker(Arrays.asList(null, 2, 4, 5));
    assertEquals(Set.of(2, 4, 5), OccupationMapExport.occupiedProvinceIds(state.war));
    assertEquals(
        List.of("1,2,3", "4,5,6"),
        OccupationMapExport.nationRgbsToEnqueue(state.war, id -> provinces.get(id).getOwner()));
    assertEquals(
        Map.of(2, "home", 4, "home", 5, "home"),
        OccupationMapExport.occupierByProvince(List.of(state.war)));
  }

  private WorldState worldState() {
    Faction home = fixture.saved("home", "Alice");
    Faction enemy = fixture.saved("enemy", "Eve");
    Faction subject = fixture.saved("subject", "Sam");
    home.addProvince(1);
    home.addProvince(2);
    enemy.addProvince(4);
    subject.addProvince(3);
    home.setCapital(1, true, false);
    enemy.setCapital(4, true, false);
    fixture.subject(home, subject);
    Guild traders = fixture.guild(home, "traders", "Bob");
    traders.setCapital(2);
    traders.addMember("Charlie");
    home.getBank().deposit(100.0);
    traders.getBank().deposit(50.0);
    home.getSettlementHandler().acceptTransferred(new Settlement("capital", "Capital", 1, 10, 20));
    home.getSettlementHandler().acceptTransferred(new Settlement("town", "Town", 2, 20, 40));
    Installation fort = new Installation("fort", "Fort", InstallationKind.FORT, 2, 20, 40, 1);
    home.getInstallationHandler().acceptTransferred(fort);
    home.getInstallationHandler()
        .acceptTransferred(new Installation("port", "Port", InstallationKind.PORT, 1, 10, 20, 2));
    home.getInstallationHandler()
        .acceptTransferred(
            new Installation("air", "Airfield", InstallationKind.AIRPORT, 1, 10, 20, 3));
    home.getInstallationHandler()
        .acceptTransferred(
            new Installation("station", "Station", InstallationKind.TRAIN_STATION, 1, 10, 20, 4));
    for (int id : List.of(4, 5, 9, 999)) provinces.get(2).addNeighbour(id);
    War war = new War(7, home, enemy);
    war.setWarType(WarType.SUBJUGATE);
    war.setGoal(WarGoalType.SUBJUGATE);
    war.setCampaignProvinces(List.of(1, 2, 4));
    war.setObjectiveProvinceId(4);
    war.putFortController("fort", CampaignCoalition.DEFENDER);
    WarManager.get().add(war);
    return new WorldState(home, enemy, subject, traders, fort, war);
  }

  private static JsonObject find(JsonArray rows, String id) {
    for (var row : rows)
      if (row.getAsJsonObject().get("id").getAsString().equals(id)) return row.getAsJsonObject();
    fail("Missing exported row " + id);
    return null;
  }

  private static List<Integer> ints(JsonArray values) {
    List<Integer> out = new ArrayList<>();
    values.forEach(value -> out.add(value.getAsInt()));
    return out;
  }

  private static List<String> strings(JsonArray values) {
    List<String> out = new ArrayList<>();
    values.forEach(value -> out.add(value.getAsString()));
    return out;
  }

  private void remember(Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    globals.put(field, field.get(null));
  }

  @SuppressWarnings("unchecked")
  private Map<Object, Object> kindMap() throws Exception {
    Field field = InstallationConfigLoader.class.getDeclaredField("byKind");
    field.setAccessible(true);
    return (Map<Object, Object>) field.get(null);
  }

  private record WorldState(
      Faction home, Faction enemy, Faction subject, Guild traders, Installation fort, War war) {}
}
