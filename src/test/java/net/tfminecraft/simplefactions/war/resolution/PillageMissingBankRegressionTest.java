package net.tfminecraft.simplefactions.war.resolution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.map.provinces.ProvinceDataEntry;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.commitment.WarCommitmentService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommitment;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PillageMissingBankRegressionTest {
  private static final int WAR_ID = 918792;
  private FactionDomainFixture domain;
  private PersistenceFilesFixture disk;
  private Database database;
  private ProvinceManager provinces;
  private Province targetProvince;
  private final Map<Field, Object> previousQueues = new LinkedHashMap<>();
  private List<WarCommitment> previousCommitments;
  private List<Battle> previousBattles;
  private Map<Player, Battle> previousEditors;
  private int previousLootDays, previousHitDays, previousEmptyGrace;
  private double previousHitPercent;

  @BeforeEach
  void setup() throws Exception {
    disk = new PersistenceFilesFixture();
    Files.createDirectories(disk.root.resolve("Data"));
    domain = new FactionDomainFixture();
    when(Bukkit.createBossBar(
            anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class)))
        .thenAnswer(call -> mock(BossBar.class));
    database = new Database();
    for (String name : List.of("dbRelations", "dbTradeRelations", "dbTreatyRelations", "loans")) {
      Field field = FactionManager.class.getDeclaredField(name);
      field.setAccessible(true);
      previousQueues.put(field, field.get(null));
      field.set(null, name.equals("loans") ? new ArrayList<>() : new HashMap<>());
    }
    previousCommitments = new ArrayList<>(WarCommitmentService.getCommitmentsForWar(WAR_ID));
    WarCommitmentService.clearCommitments(WAR_ID);
    previousBattles = new ArrayList<>(BattleManager.get());
    BattleManager.get().clear();
    previousEditors = new HashMap<>(BattleManager.currentBattle);
    BattleManager.currentBattle.clear();
    previousLootDays = Cache.pillageLootDays;
    previousHitDays = Cache.pillageTradeHitDays;
    previousHitPercent = Cache.pillageTradeHitPercent;
    previousEmptyGrace = Cache.battleEmptySideGraceSeconds;
    Cache.pillageLootDays = 3;
    Cache.pillageTradeHitDays = 2;
    Cache.pillageTradeHitPercent = -60;
    Cache.battleEmptySideGraceSeconds = 300;
    domain.regiment("guard", false, 0, 0);
    domain.provincesEnabled(true);
    provinces = new ProvinceManager();
    Province home = new Province(1, "PLAINS", 50);
    targetProvince = new Province(2, "PLAINS", 50);
    home.addNeighbour(2);
    targetProvince.addNeighbour(1);
    provinces.start(Map.of(1, home, 2, targetProvince));
    when(domain.ui.plugin.getProvinceManager()).thenReturn(provinces);
  }

  @AfterEach
  void close() throws Exception {
    try {
      for (Battle battle : new ArrayList<>(BattleManager.get())) battle.end();
      if (previousBattles != null) {
        BattleManager.get().clear();
        BattleManager.get().addAll(previousBattles);
      }
      if (previousEditors != null) {
        BattleManager.currentBattle.clear();
        BattleManager.currentBattle.putAll(previousEditors);
      }
      WarCommitmentService.restoreCommitments(WAR_ID, previousCommitments);
      for (var entry : previousQueues.entrySet()) entry.getKey().set(null, entry.getValue());
      Cache.pillageLootDays = previousLootDays;
      Cache.pillageTradeHitDays = previousHitDays;
      Cache.pillageTradeHitPercent = previousHitPercent;
      Cache.battleEmptySideGraceSeconds = previousEmptyGrace;
      if (domain != null) domain.close();
    } finally {
      if (disk != null) disk.close();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void persistedBankChoiceCannotInterruptWinningPillageTeardown(boolean bankRemoved)
      throws Exception {
    Faction attacker = domain.saved("pillage_attacker", "Alice");
    Faction defender = domain.saved("pillage_defender", "Bob");
    attacker.addProvince(1);
    attacker.setCapital(1, true, false);
    defender.addProvince(2);
    assertTrue(defender.getSettlementHandler().found("Victim", 2, 0, 0).isSuccess());
    Guild merchant = domain.guild(defender, "victim_merchants", "Trader");
    merchant.setCapital(2, false);
    attacker.getMilitary().getRegiment("guard").setCurrentSlots(3);
    defender.getMilitary().getRegiment("guard").setCurrentSlots(3);
    attacker.getBank().setWealth(100.0);
    if (bankRemoved) attacker.setBank(null);
    assertTrue(database.saveFactionChecked(attacker));
    assertTrue(database.saveFactionChecked(defender));
    FactionData savedAttacker =
        JsonUtil.readJson(
            disk.root.resolve("Data/pillage_attacker.json").toFile(), FactionData.class);
    assertEquals(bankRemoved ? "false" : "true", savedAttacker.guilds.getFirst().bank);
    War original = new War(WAR_ID, attacker, defender);
    original.setGoal(WarGoalType.PILLAGE);
    original.setWarType(WarType.PILLAGE);
    original.setTargetSettlementId("Victim");
    original.setObjectiveProvinceId(2);
    WarManager.addWar(original);
    WarCommitmentService.commitAllParticipants(original);
    database.saveWar(original);
    assertTrue(Files.exists(disk.root.resolve("Wars/war_" + WAR_ID + ".json")));

    FactionManager.factions.clear();
    WarManager.get().clear();
    WarCommitmentService.clearCommitments(WAR_ID);
    database.loadFactions();
    WarManager.start();
    War restored = WarManager.getById(WAR_ID);
    assertNotNull(restored);
    Faction loadedAttacker = FactionManager.getByString("pillage_attacker");
    Faction loadedDefender = FactionManager.getByString("pillage_defender");
    Guild loadedMerchant = FactionManager.getGuildByString("victim_merchants");
    assertSame(loadedAttacker, restored.getAttackers().getLeader());
    assertSame(loadedDefender, restored.getDefenders().getLeader());
    assertNotNull(loadedMerchant);
    if (bankRemoved) assertNull(loadedAttacker.getBank());
    else assertEquals(100.0, loadedAttacker.getBank().getWealth());
    assertFalse(WarCommitmentService.getCommitmentsForWar(WAR_ID).isEmpty());
    assertEquals(3, loadedAttacker.getMilitary().getRegiment("guard").getCurrentSlots());
    assertEquals(3, loadedDefender.getMilitary().getRegiment("guard").getCurrentSlots());
    targetProvince.setData(loadedMerchant.getId(), new ProvinceDataEntry(loadedMerchant, 10, 0));
    targetProvince.setProsperity(100);
    double expectedLoot = provinces.getIncome(loadedMerchant, false) * Cache.pillageLootDays;
    assertTrue(expectedLoot > 0, "The regression must attempt an actual positive payout");

    Battle battle = BattleFactory.createBlank(BattleType.FIELD, "missing_bank_pillage_battle");
    battle.setWarId(WAR_ID);
    battle.setProvinceId(2);
    for (BattleSide side : battle.getSides()) {
      side.setSpawn(new Location(domain.ui.world, 0, 64, 0));
      side.setJail(new Location(domain.ui.world, 10, 64, 0));
    }
    BattleManager.addBattle(battle);
    assertNull(battle.start());
    assertTrue(battle.hasStarted());
    BattlePersistenceService.persistBattle(battle);
    assertTrue(Files.exists(disk.root.resolve("Battles/battle_" + battle.getId() + ".json")));

    assertDoesNotThrow(() -> WarManager.endWar(restored, WarEndReason.ATTACKER_VICTORY));

    assertFalse(restored.isActive());
    assertEquals(WarEndReason.ATTACKER_VICTORY, restored.getEndReason());
    assertNull(WarManager.getById(WAR_ID));
    assertTrue(WarCommitmentService.getCommitmentsForWar(WAR_ID).isEmpty());
    assertFalse(battle.hasStarted());
    assertNull(BattleManager.getByString(battle.getId()));
    assertFalse(Files.exists(disk.root.resolve("Wars/war_" + WAR_ID + ".json")));
    assertFalse(Files.exists(disk.root.resolve("Battles/battle_" + battle.getId() + ".json")));
    assertEquals(1, loadedMerchant.getPillageHits().size());
    assertEquals(-60, PillageTradeHit.percent(loadedMerchant));
    if (bankRemoved)
      assertNull(loadedAttacker.getBank(), "Pillage must not recreate a removed bank");
    else assertEquals(100.0 + expectedLoot, loadedAttacker.getBank().getWealth(), 0.00001);
    assertTrue(database.saveFactionChecked(loadedAttacker));
    assertTrue(database.saveFactionChecked(loadedDefender));
    FactionData finalAttacker =
        JsonUtil.readJson(
            disk.root.resolve("Data/pillage_attacker.json").toFile(), FactionData.class);
    FactionData finalDefender =
        JsonUtil.readJson(
            disk.root.resolve("Data/pillage_defender.json").toFile(), FactionData.class);
    assertEquals(bankRemoved ? "false" : "true", finalAttacker.guilds.getFirst().bank);
    assertEquals(
        1,
        finalDefender.guilds.stream()
            .filter(guild -> guild.id.equals("victim_merchants"))
            .findFirst()
            .orElseThrow()
            .pillageHits
            .size());
  }

  @Test
  void reportingAnUnavailableBankDoesNotRequireThePluginSingleton() {
    Faction attacker = domain.saved("no_plugin_attacker", "Alice");
    Faction defender = domain.saved("no_plugin_defender", "Bob");
    attacker.setBank(null);
    War war = new War(WAR_ID, attacker, defender);
    SimpleFactions previous = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      assertDoesNotThrow(() -> PillageApplyService.depositLoot(war, 50));
      assertNull(attacker.getBank());
      assertTrue(war.isActive());
    } finally {
      SimpleFactions.plugin = previous;
    }
  }
}
