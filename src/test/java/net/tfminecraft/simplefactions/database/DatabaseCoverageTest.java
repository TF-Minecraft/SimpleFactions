package net.tfminecraft.simplefactions.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import net.tfminecraft.simplefactions.government.proposal.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.upgrade.Upgrade;
import net.tfminecraft.simplefactions.loaders.UpgradeLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.*;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.core.*;
import net.tfminecraft.simplefactions.war.resolution.WarReparationsObligation;
import org.bukkit.Bukkit;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseCoverageTest {
  FactionDomainFixture fixture;
  PersistenceFilesFixture disk;
  Database database;
  Map<Field,Object> oldQueues=new LinkedHashMap<>();

  @BeforeEach void setUp() throws Exception {
    disk=new PersistenceFilesFixture();
    fixture=new FactionDomainFixture();database=new Database();
    when(fixture.ui.world.getName()).thenReturn("world");
    for(String name:List.of("dbRelations","dbTradeRelations","dbTreatyRelations","loans")) {
      Field field=FactionManager.class.getDeclaredField(name);field.setAccessible(true);
      oldQueues.put(field,field.get(null));
      field.set(null,name.equals("loans") ? new ArrayList<>() : new HashMap<>());
    }
    when(Bukkit.createBossBar(anyString(),any(BarColor.class),any(BarStyle.class),any(BarFlag[].class)))
        .thenAnswer(call -> mock(BossBar.class));
  }

  @AfterEach void close() throws Exception {
    try {
      for(var entry:oldQueues.entrySet()) entry.getKey().set(null,entry.getValue());
      fixture.close();
    } finally { disk.close(); }
  }

  @Test void timerAndDayRoundTripAndMissingNullOrMalformedDataUseZero() throws Exception {
    assertEquals(0,database.getTimer());assertEquals(0,database.getDay());
    database.saveTimer(543,7);assertEquals(543,database.getTimer());assertEquals(7,database.getDay());
    disk.write("Cache/data.json","null");assertEquals(0,database.getTimer());assertEquals(0,database.getDay());
    disk.write("Cache/data.json","not-json{");assertEquals(0,database.getTimer());assertEquals(0,database.getDay());
    disk.remove("Cache/data.json");Files.createDirectory(disk.root.resolve("Cache/data.json"));
    assertDoesNotThrow(() -> database.saveTimer(10,2));
    assertTrue(Files.isDirectory(disk.root.resolve("Cache/data.json")));
  }

  @Test void aNonDirectoryFactionStoreFailsWithoutLeavingPrestigeLoadingSuppressed() throws Exception {
    disk.write("Data","not a directory");
    assertThrows(RuntimeException.class,database::loadFactions);
    assertFalse(FactionManager.loading,"A failed load must not permanently suppress prestige refreshes");
    assertTrue(FactionManager.factions.isEmpty());
  }

  @Test void missingStoresAreCreatedAndUnrelatedOrEmptyFactionFilesAreIgnored() throws Exception {
    database.loadFactions();assertTrue(Files.isDirectory(disk.root.resolve("Data")));
    disk.write("Data/readme.txt","operator notes");disk.write("Data/empty.json","null");
    disk.write("Data/no-id.json","{}");
    Path broken = disk.write("Data/broken.json", "not-json{");
    database.loadFactions();assertTrue(FactionManager.factions.isEmpty());assertFalse(FactionManager.loading);
    assertEquals("not-json{", Files.readString(broken));
  }

  private GuildData guild(String id,String type,String leader) {
    GuildData data=new GuildData();data.id=id;data.name=id;data.type=type;data.leader=leader;
    data.members=new ArrayList<>(List.of(leader));data.banner=new ArrayList<>(List.of("white"));
    data.rgb="4,5,6";data.capital=-1;data.bank="true";data.world="world";data.xPos=0.0;data.zPos=0.0;data.balance=75.0;
    return data;
  }

  private FactionData richFaction() {
    fixture.regiment("guards",false,2,5);fixture.regiment("levy",true,1,0);
    fixture.lawGroup("customs",Map.of("effects.faction.regiments",List.of("guards 2")));
    fixture.title("county","county",1,2);
    YamlConfiguration yaml=new YamlConfiguration();yaml.set("warehouse.allowed-types",List.of("realm","guild"));
    UpgradeLoader.map.put("warehouse",new Upgrade("warehouse",yaml.getConfigurationSection("warehouse")));
    FactionData data=fixture.data("realm","Alice");
    data.provinces=List.of(1,2);data.titles=List.of("county","deleted-title");
    data.laws=List.of("customs:current");data.lawChangedAt=new HashMap<>(Map.of("customs",12345L,"deleted",42L));data.lawChangedAt.put("missing",null);
    data.prestigeModifiers=List.of("Historic capital(12.5)");
    data.rank="renowned";data.foundedAt=1234L;data.capitalMoves=2;data.tierIndex=3.0;
    data.military=List.of("guards.5.2","deleted.3");data.militaryQueue=List.of("guards.7");
    data.specificTaxes.put("GUILDS",new HashMap<>(Map.of("merchants",3.5)));data.specificTaxes.put("VASSALS",null);
    data.vehicleFees=new HashMap<>(Map.of("REGISTRATION_FEE",2.0));
    data.vehicleTypeFees=new HashMap<>(Map.of("TRANSFER_FEE",new HashMap<>(Map.of("cart",3.0))));
    GuildData base=guild("realm","realm","Alice"),merchants=guild("merchants","guild","Bob");
    merchants.bank="false";merchants.balance=null;merchants.casinoProfit=5.0;merchants.vehicleFeeIncome=7.0;
    merchants.citizenTaxes=Map.of("Bob",1.5);merchants.ledgerLastDay=Map.of("income",Map.of("tax",3.0));
    merchants.ledgerLifetime=Map.of("income",Map.of("tax",8.0));merchants.depositsToday=Map.of("Bob",4.0);
    GuildBranchData branch=new GuildBranchData();branch.id="commerce";branch.level=2;
    merchants.branches.add(branch);GuildBranchData upgrade=new GuildBranchData();upgrade.id="warehouse";upgrade.level=1;merchants.upgrades.add(upgrade);
    UpgradeExpansionData queued=new UpgradeExpansionData();queued.upgrade="warehouse";queued.timeLeft=9;
    UpgradeExpansionData removed=new UpgradeExpansionData();removed.upgrade="deleted";removed.timeLeft=4;
    merchants.upgradeQueue=List.of(queued,removed);merchants.wealthModifiers=List.of("Grant(25.0)");
    StabilityModifierData hit=new StabilityModifierData();hit.name="Raid";hit.modifier=-4;hit.decay=1;merchants.pillageHits.add(hit);
    data.guilds=List.of(base,merchants);
    WarReparationsObligationData payment=new WarReparationsObligationData();payment.payeeFactionId="other";payment.incomePercent=10.0;payment.daysRemaining=4;
    WarReparationsObligationData invalid=new WarReparationsObligationData();invalid.payeeFactionId=" ";
    WarReparationsObligationData expired=new WarReparationsObligationData();expired.payeeFactionId="other";expired.incomePercent=10.0;expired.daysRemaining=0;
    data.warReparationsObligations=Arrays.asList(payment,invalid,null,expired);
    return data;
  }

  @Test void realFactionStateRoundTripsTreasuryRulesRegimentsQueuesTaxesAndLedgerHistory() throws Exception {
    FactionData data=richFaction();
    Path path=disk.write("Data/realm.json",JsonUtil.GSON.toJson(data));
    database.loadFactions();Faction faction=FactionManager.getByString("realm");assertNotNull(faction);
    assertEquals(400.0,faction.getWealth());assertEquals(75.0,faction.getBank().getWealth());assertEquals(1234L,faction.getFoundedAt());
    assertEquals(2,faction.getCapitalMoves());assertEquals("renowned",faction.getRank().getId());
    assertEquals(3.5,faction.getTaxRate(TaxTarget.GUILD_ID,"merchants",false));
    assertEquals(12345L,faction.getLawHandler().getGroup("customs").getChangedAt());
    assertEquals(5,faction.getMilitary().getRegiment("guards").getCurrentSlots());
    assertEquals(7,faction.getMilitary().getQueue().getFirst().getTimeLeft());
    Guild merchants=faction.getGuildHandler().getGuild("merchants");
    assertEquals(2,merchants.getBranch("commerce").getLevel());assertEquals(1,merchants.getUpgrade("warehouse").getLevel());
    assertEquals(1,merchants.getUpgradeQueue().size());assertEquals(9,merchants.getUpgradeQueue().getFirst().getTimeLeft());
    assertEquals(5.0,merchants.getLedger().getCasinoProfit());assertEquals(Map.of("Bob",1.5),merchants.getLedger().getCitizenTaxesCopy());
    assertEquals(1,faction.getWarReparationsObligations().size());
    assertTrue(database.saveFactionChecked(faction));
    FactionData saved=JsonUtil.readJson(path.toFile(),FactionData.class);
    assertEquals(List.of("county"),saved.titles);assertEquals(List.of("guards.5.2"),saved.military);
    assertEquals(data.laws,saved.laws);assertEquals(12345L,saved.lawChangedAt.get("customs"));
    assertEquals(3.5,saved.specificTaxes.get("GUILDS").get("merchants"));
    assertEquals(1,saved.warReparationsObligations.size());
    assertEquals(25.0,Database.loadModifiers(saved.guilds.stream().filter(g->g.id.equals("merchants")).findFirst().orElseThrow().wealthModifiers).getFirst().getAmount());
    FactionManager.factions.clear();database.loadFactions();Faction reloaded=FactionManager.getByString("realm");
    assertEquals(faction.getWealth(),reloaded.getWealth());assertEquals(75.0,reloaded.getBank().getWealth());
    database.deleteFaction(reloaded);assertFalse(Files.exists(path));database.deleteFaction(reloaded);
  }

  @Test void legacyMissingOptionalValuesUseDefaultsWithoutDiscardingTheFaction() throws Exception {
    FactionData data=fixture.data("legacy","Alice");
    data.capital=null;data.extraNodeCapacity=null;data.citizenTax=null;data.guildTax=null;data.vassalTax=null;data.dividendTax=null;data.tariffs=null;
    data.specificTaxes=null;data.guilds=null;data.relations=null;data.tradeRelations=null;data.treatyRelations=null;
    data.settlements=null;data.installations=null;data.installationQueue=null;data.lawChangedAt=null;data.warReparationsObligations=null;
    data.rank="removed-rank";
    disk.write("Data/legacy.json",new com.google.gson.GsonBuilder().serializeNulls().create().toJson(data));database.loadFactions();
    Faction faction=FactionManager.getByString("legacy");assertNotNull(faction);
    assertEquals(-1,faction.getCapital());assertEquals(0,faction.getExtraNodeCapacity());
    for(TaxTarget target:TaxTarget.values()) assertEquals(5.0,faction.getTaxRate(target,null,false));
    assertTrue(faction.getFoundedAt()>0);assertEquals(0,faction.getCapitalMoves());
  }

  @Test void relationsLoansConstructionAndRemovedBanksSurviveASaveAndReload() throws Exception {
    Files.createDirectories(disk.root.resolve("Data"));
    Faction realm=fixture.saved("realm","Alice"), liege=fixture.saved("liege","Bob");
    Guild issuer=realm.getOrCreateMainGuild(), borrower=liege.getOrCreateMainGuild();
    fixture.subject(liege,realm);
    var trade=fixture.relationType("trading",Map.of("trade-agreement",true));
    var treaty=fixture.relationType("peace",Map.of("treaty",true));
    realm.getDiplomacyHandler().setTradeRelation(liege,trade);
    realm.getDiplomacyHandler().setTreatyRelation(liege,treaty);
    Loan source=new Loan("agreement",100.0,issuer,borrower,1000L,2,0.1,0.5,true);
    issuer.getLoanHandler().issueLoan(source);
    realm.setBank(null);
    realm.addWarReparationsObligation(new WarReparationsObligation("liege",5.0,0));
    InstallationConstructionData construction=new InstallationConstructionData();
    construction.id="new-fort";construction.name="North Fort";construction.kind="fort";
    construction.province=10;construction.timeLeft=6;construction.startedAt=1234L;
    realm.getInstallationHandler().loadConstruction(construction);
    assertTrue(database.saveFactionChecked(realm));
    FactionData saved=JsonUtil.readJson(disk.root.resolve("Data/realm.json").toFile(),FactionData.class);
    assertEquals(List.of("liege(trading)"),saved.tradeRelations);
    assertEquals(List.of("liege(peace)"),saved.treatyRelations);
    assertEquals("false",saved.guilds.getFirst().bank);
    assertEquals("agreement",saved.guilds.getFirst().loans.getFirst().id);
    assertTrue(saved.warReparationsObligations.isEmpty());
    assertEquals(6,saved.installationQueue.timeLeft);
    FactionManager.factions.remove(realm);
    database.loadFactions();FactionManager.loadRelations();FactionManager.loadDBLoans();
    Faction reloaded=FactionManager.getByString("realm");assertNotNull(reloaded);
    assertNull(reloaded.getBank(), "An explicitly removed bank must stay removed after reload");
    assertSame(liege,reloaded.getOverlord());
    assertSame(trade,reloaded.getDiplomacyHandler().getTradeRelation("liege"));
    assertSame(treaty,reloaded.getDiplomacyHandler().getTreatyRelation("liege"));
    Loan restored=reloaded.getOrCreateMainGuild().getLoanHandler().getLoansGiven().getFirst();
    assertEquals("agreement",restored.getId());assertSame(borrower,restored.getBorrower());
    assertEquals(100.0,restored.getAmount());assertEquals(source.getDueDate(),restored.getDueDate());
    assertEquals("new-fort",reloaded.getInstallationHandler().getPendingConstruction().getId());
    assertEquals(6,reloaded.getInstallationHandler().getPendingConstruction().getTimeLeft());
  }

  @Test void legacyTwoPartRegimentsRetainTheirConfiguredFreeSlots() throws Exception {
    fixture.regiment("guards",false,2,5);
    fixture.lawGroup("customs",Map.of("effects.faction.regiments",List.of("guards 2")));
    FactionData data=fixture.data("legacy","Alice");data.laws=List.of("customs:current");data.military=List.of("guards.5");
    disk.write("Data/legacy.json",JsonUtil.GSON.toJson(data));database.loadFactions();
    var regiment=FactionManager.getByString("legacy").getMilitary().getRegiment("guards");
    assertEquals(5,regiment.getCurrentSlots());assertEquals(2,regiment.getFreeSlots());
  }

  @ParameterizedTest @ValueSource(strings={"Wars","Battles","Warbands"})
  void unreadableDirectoriesDoNotProduceObjects(String kind) throws Exception {
    Path directory=Files.createDirectories(disk.root.resolve(kind));
    Assumptions.assumeTrue(Files.getFileAttributeView(directory,PosixFileAttributeView.class)!=null);
    Set<PosixFilePermission> original=Files.getPosixFilePermissions(directory);
    try {
      Files.setPosixFilePermissions(directory,Set.of());
      Assumptions.assumeFalse(Files.isReadable(directory),"This user can read directories despite POSIX permissions");
      assertTrue(load(kind).isEmpty());
    } finally {Files.setPosixFilePermissions(directory,original);}
  }

  @Test void failedFactionSaveReportsFailureAndPreservesThePreviousBytes() throws Exception {
    Faction faction=fixture.saved("realm","Alice");
    Path file=disk.write("Data/realm.json","previous saved state");
    try(var writes=mockStatic(JsonUtil.class,CALLS_REAL_METHODS)) {
      writes.when(() -> JsonUtil.writeJsonAtomic(any(File.class),any())).thenThrow(new IOException("simulated full disk"));
      assertFalse(database.saveFactionChecked(faction));assertEquals("previous saved state",Files.readString(file));
      assertDoesNotThrow(() -> database.saveFaction(faction));
    }
  }

  @Test void savingGuildStateSkipsEmptyEntriesInThePublicPillageHistory() throws Exception {
    Files.createDirectories(disk.root.resolve("Data"));
    Faction realm=fixture.saved("realm","Alice");
    Guild guild=realm.getOrCreateMainGuild();
    guild.getPillageHits().add(null);
    assertTrue(database.saveFactionChecked(realm));
    FactionData saved=JsonUtil.readJson(disk.root.resolve("Data/realm.json").toFile(),FactionData.class);
    assertEquals("realm",saved.id);assertTrue(saved.guilds.getFirst().pillageHits.isEmpty());
  }

  @Test void warsBattlesAndWarbandsRoundTripThroughRealJsonAndDeleteIdempotently() throws Exception {
    Faction attacker=fixture.saved("attacker","Alice"),defender=fixture.saved("defender","Bob");
    War war=new War(7,attacker,defender);database.saveWar(war);
    assertEquals(7,database.loadWars().getFirst().getId());
    Battle battle=BattleFactory.createBlank(BattleType.FIELD,"training");database.saveBattle(battle);
    assertEquals("training",database.loadBattles().getFirst().getId());
    Warband band=Warband.createWithMemberIds("scouts",UUID.randomUUID(),true);database.saveWarband(band);
    assertEquals(band.getMemberIds(),database.loadWarbands().getFirst().getMemberIds());
    database.saveBattle(null);database.saveWarband(null);database.deleteBattleFile(null);database.deleteWarbandFile(null);
    database.deleteWar(war);database.deleteWar(war);assertTrue(database.loadWars().isEmpty());
    database.deleteBattleFile(battle.getId());database.deleteBattleFile(battle.getId());assertTrue(database.loadBattles().isEmpty());
    database.deleteWarbandFile(band.getId());database.deleteWarbandFile(band.getId());assertTrue(database.loadWarbands().isEmpty());
  }

  @ParameterizedTest @ValueSource(strings={"Wars","Battles","Warbands"})
  void absentNonDirectoryAndMalformedStoresDoNotProduceObjects(String kind) throws Exception {
    assertTrue(load(kind).isEmpty());disk.write(kind,"not directory");assertTrue(load(kind).isEmpty());disk.remove(kind);
    disk.write(kind+"/readme.txt","ignore");disk.write(kind+"/empty.json","null");disk.write(kind+"/broken.json","not-json{");
    assertTrue(load(kind).isEmpty());
  }

  @Test void writeFailuresInBattleWarbandAndWarStoresLeaveExistingEntriesAlone() throws Exception {
    Faction attacker=fixture.saved("attacker","Alice"),defender=fixture.saved("defender","Bob");
    War war=new War(7,attacker,defender);Battle battle=BattleFactory.createBlank(BattleType.FIELD,"training");
    Warband band=Warband.createWithMemberIds("scouts",UUID.randomUUID(),true);
    for(String dir:List.of("Wars","Battles","Warbands")) disk.write(dir,"occupied");
    assertDoesNotThrow(() -> database.saveWar(war));assertDoesNotThrow(() -> database.saveBattle(battle));assertDoesNotThrow(() -> database.saveWarband(band));
    for(String dir:List.of("Wars","Battles","Warbands")) assertEquals("occupied",Files.readString(disk.root.resolve(dir)));
  }

  private List<?> load(String kind) { return switch(kind) {case "Wars" -> database.loadWars();case "Battles" -> database.loadBattles();default -> database.loadWarbands();}; }
}
