package net.tfminecraft.simplefactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.database.MercenaryContractData;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.WageSettings;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.utils.PostSettlementPayouts.PlayerUuidLookup;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarMapper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MercenaryContractBoundaryCoverageTest {
  @Test
  void wageOverridesFollowThePlayerAcrossNameCasingAndLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      WageSettings wages = new WageSettings();
      wages.setActivePercent(20);
      wages.setPeacetimePerDay(2);
      wages.setActiveOverride("IRIS", 55.0);
      wages.setPeacetimeOverride("IRIS", 7.0);

      assertEquals(55.0, wages.activeShareOf(100, "iris"));
      assertEquals(7.0, wages.peacetimeFor("iris"));
      assertEquals(7.0, wages.getPeacetimeOverride("iris"));
      wages.clearActiveOverride("iris");
      wages.clearPeacetimeOverride("iris");
      assertNull(wages.getPeacetimeOverride("IRIS"));
      assertEquals(20.0, wages.activeShareOf(100, "IRIS"));
      assertEquals(2.0, wages.peacetimeFor("IRIS"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void aCompanyCannotFightItsHostsGrandchildRealm() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction host = domain.saved("host", "HostLeader");
      Faction middle = domain.saved("middle", "MiddleLeader");
      Faction enemy = domain.saved("grandchild", "GrandchildLeader");
      Faction hirer = domain.saved("hirer", "HirerLeader");
      domain.subject(host, middle);
      domain.subject(middle, enemy);
      Guild guild = domain.guild(host, "blades", "Captain");
      MercenaryCompany company = company(guild);
      War war = new War(551092, hirer, enemy);

      var result = MercenaryLoyalty.canServe(company, hirer, List.of(war));

      assertFalse(result.ok(), "The company must not fight a nested subject of its host realm");
      assertTrue(company.getContractHandler().getActive().isEmpty());
      assertEquals(List.of("Captain"), company.getEnlisted());
      assertSame(host, company.getGuild().getFaction());
    }
  }

  @Test
  void deepAncestorLoyaltyStillAllowsACompanyToServeAgainstAnUnrelatedPeer() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction root = domain.saved("root", "RootLeader");
      Faction middle = domain.saved("middle", "MiddleLeader");
      Faction host = domain.saved("host", "HostLeader");
      Faction peer = domain.saved("peer", "PeerLeader");
      Faction hirer = domain.saved("hirer", "HirerLeader");
      domain.subject(root, middle);
      domain.subject(middle, host);
      domain.subject(root, peer);
      Faction ally = domain.saved("neutral_ally", "AllyLeader");
      var alliance = domain.relationType("ally", Map.of("name", "Ally"));
      host.setRelation(ally, new Relation(alliance, RelationLoader.getDefaultAttitude()));
      MercenaryCompany company = company(domain.guild(host, "blades", "Captain"));
      assertFalse(
          MercenaryLoyalty.canServe(company, hirer, List.of(new War(551092, hirer, root))).ok());
      assertTrue(
          MercenaryLoyalty.canServe(company, hirer, List.of(new War(551093, hirer, peer))).ok(),
          "A neutral peer is not an ancestor, descendant, or ally of the company host");
    }
  }

  @Test
  void loyaltyQueriesHandleUnavailableCompaniesHirersAndWarSnapshots() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction host = domain.saved("host", "HostLeader");
      Faction hirer = domain.saved("hirer", "HirerLeader");
      MercenaryCompany company = company(domain.guild(host, "blades", "Captain"));
      assertFalse(MercenaryLoyalty.canServe(null, hirer, List.of()).ok());
      assertFalse(MercenaryLoyalty.canServe(company, null, List.of()).ok());
      assertTrue(MercenaryLoyalty.canServe(company, hirer, null).ok());
      MercenaryCompany unhosted = company(null);
      assertTrue(MercenaryLoyalty.canServe(unhosted, hirer, List.of()).ok());
      assertFalse(MercenaryLoyalty.canServeAlongside(null, hirer, List.of()).ok());
      assertFalse(MercenaryLoyalty.canServeAlongside(company, null, List.of()).ok());
      assertTrue(MercenaryLoyalty.canServeAlongside(company, hirer, null).ok());
      assertFalse(SlotReservations.canPromise(null, 1, 0, 1).ok());
      assertTrue(company.getContractHandler().getAll().isEmpty());
    }
  }

  @Test
  void contractsWithRemovedOrMatchingHirersDoNotBlockAnUnrelatedLoyalOffer() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction host = domain.saved("host", "HostLeader");
      Faction hirer = domain.saved("hirer", "HirerLeader");
      Faction enemy = domain.saved("enemy", "EnemyLeader");
      Faction neutral = domain.saved("neutral", "NeutralLeader");
      Faction removed = domain.saved("removed", "RemovedLeader");
      MercenaryCompany company = company(domain.guild(host, "blades", "Captain"));
      contract(company, hirer, 1, System.currentTimeMillis());
      contract(company, neutral, 1, System.currentTimeMillis());
      contract(company, removed, 1, System.currentTimeMillis());
      FactionManager.factions.remove(removed);
      War war = new War(551092, hirer, enemy);
      assertTrue(MercenaryLoyalty.canServeAlongside(company, hirer, Arrays.asList(null, war)).ok());
      assertNull(
          MercenaryEngagements.forPlayer(
              new War(551095, enemy, domain.saved("another_enemy", "OtherEnemyLeader")),
              "Captain"));
      assertEquals(
          3,
          company.getContractHandler().getActive().size(),
          "Loyalty queries do not mutate active contracts");
    }
  }

  @Test
  void aSavedWarWithoutItsHirersMainRowCannotAuthorizeMercenaryEnrollment() throws Exception {
    try (ContractRig rig = new ContractRig()) {
      MercenaryContract contract = contract(rig.company, rig.hirer, 1, System.currentTimeMillis());
      assertSame(contract, MercenaryEngagements.forPlayer(rig.war, "Captain").contract());
      assertSame(rig.war.getAttackers(), MercenaryEngagements.sideFor(rig.war, "Captain"));
      WarData saved = WarMapper.toData(rig.war);
      saved.attackers.participants.removeIf(row -> row.leader.equals(rig.hirer.getId()));
      Gson gson = new Gson();
      War restored = WarMapper.fromData(gson.fromJson(gson.toJson(saved), WarData.class));
      assertNotNull(restored);
      assertTrue(restored.isParticipating(rig.hirer), "The saved coalition still names its leader");
      assertNull(restored.getSide(rig.hirer), "The missing main row cannot resolve enrollment");

      assertNull(MercenaryEngagements.forPlayer(restored, "Captain"));
      assertNull(MercenaryEngagements.sideFor(restored, "Captain"));
      assertTrue(MercenaryEngagements.on(restored, restored.getAttackers()).isEmpty());
      assertTrue(MercenaryEngagements.on(restored, restored.getDefenders()).isEmpty());
      assertEquals(List.of(contract), rig.company.getContractHandler().getActive());
      assertEquals(List.of("Captain"), rig.company.getEnlisted());
      assertEquals(0.0, contract.getAccruedToCompany());
      assertEquals(0.0, contract.getAccruedToHirer());
    }
  }

  @Test
  void liveCampaignBattleFreezesAnAmendmentUntilTheFightEnds() throws Exception {
    try (ContractRig rig = new ContractRig()) {
      long now = System.currentTimeMillis();
      rig.company.getRegiment().setCurrentSlots(3);
      MercenaryContract contract = contract(rig.company, rig.hirer, 1, now);
      contract.setDaysServed(3);
      assertEquals(30.0, contract.getServedDaysOwed());
      assertTrue(
          rig.company.getContractHandler().proposeSlots(contract.getId(), "Captain", 2, now).ok());
      assertEquals(1, SlotReservations.remaining(rig.company, now, contract.getDueDate()));
      assertFalse(SlotReservations.canPromise(rig.company, 2, now, contract.getDueDate()).ok());
      Battle manual = new Battle("manual");
      manual.setStarted(true);
      Battle missingWar = new Battle("orphan");
      missingWar.setStarted(true);
      missingWar.setWarId(999_999);
      Battle campaign = new Battle("campaign");
      campaign.setWarId(rig.war.getId());
      BattleManager.get().addAll(List.of(manual, missingWar, campaign));
      assertFalse(ContractBattleGate.hirerIsFighting(rig.hirer));
      campaign.setStarted(true);

      var refused =
          rig.company
              .getContractHandler()
              .acceptSlots(contract.getId(), rig.hirer, "HirerLeader", now);

      assertFalse(refused.ok());
      assertTrue(refused.message().contains("battle"));
      assertEquals(1, contract.getSlots());
      assertEquals(2, contract.getPendingSlots(now));
      assertFalse(ContractBattleGate.hirerIsFighting(rig.host));
      campaign.setStarted(false);
      assertTrue(
          rig.company
              .getContractHandler()
              .acceptSlots(contract.getId(), rig.hirer, "HirerLeader", now)
              .ok());
      assertEquals(2, contract.getSlots());
      assertFalse(contract.hasPendingSlots(now));
      contract.addDayServed();
      assertEquals(
          50.0,
          contract.getServedDaysOwed(),
          "A size change keeps prior service at its original rate");
    }
  }

  @Test
  void battleEventsChargeOnceRefundAbsenceAndPayOnlyAttendingSoldiers() throws Exception {
    try (ContractRig rig = new ContractRig()) {
      rig.company.getRegiment().setCurrentSlots(2);
      assertTrue(rig.company.enlist("Recruit"));
      UUID captain = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID recruit = UUID.fromString("00000000-0000-0000-0000-000000000002");
      Map<String, UUID> ids = Map.of("Captain", captain, "Recruit", recruit);
      MercenaryEngagements.setUuidLookup(ids::get);
      rig.company.getWageSettings().setActivePercent(20);
      MercenaryContract contract = contract(rig.company, rig.hirer, 2, System.currentTimeMillis());
      AttendanceService.Hook listener = new AttendanceService.Hook();
      BattleStartedEvent started =
          new BattleStartedEvent(
              "contract-battle", BattleType.FIELD, rig.war.getId(), Set.of(captain, recruit));
      BattleEndedEvent ended =
          new BattleEndedEvent(
              "contract-battle",
              BattleType.FIELD,
              rig.war.getId(),
              "attacker",
              Map.of(),
              Set.of(captain));

      listener.onBattleStarted(started);
      listener.onBattleStarted(started);
      assertEquals(100.0, contract.getAccruedToCompany());
      listener.onBattleEnded(ended);

      AttendanceService.Result result = AttendanceService.result(contract, "contract-battle");
      assertEquals(1, result.attended());
      assertEquals(1, result.absent());
      assertEquals(Set.of(captain), result.attendedIds());
      assertFalse(result.snapshotMissing());
      assertEquals(50.0, contract.getAccruedToHirer());
      assertEquals(Map.of("Captain", 10.0), rig.company.getPendingWages());
      assertFalse(contract.hasCleanAttendance());
      int reputation = rig.company.getReputation();
      assertTrue(reputation < 50);
      listener.onBattleEnded(ended);
      assertEquals(50.0, contract.getAccruedToHirer());
      assertEquals(Map.of("Captain", 10.0), rig.company.getPendingWages());
      assertEquals(reputation, rig.company.getReputation());
    }
  }

  @Test
  void equalDateDoubleHireUsesStableSavedIdentityAndKeepsLoyaltyTerminationNeutral()
      throws Exception {
    try (ContractRig rig = new ContractRig()) {
      long now = System.currentTimeMillis();
      MercenaryContract current = restoredContract(rig.company, rig.hirer, "z-contract", now);
      MercenaryContract olderById = restoredContract(rig.company, rig.enemy, "a-contract", now);
      List<String> reputationEvents = new ArrayList<>();
      ContractReputationSeam recorder =
          (contract, reason) -> reputationEvents.add(contract.getId() + ":" + reason);
      ContractTerminationService.setReputationSeam(recorder);
      assertSame(recorder, ContractTerminationService.getReputationSeam());

      assertEquals(
          List.of(current),
          ContractTerminationService.resolveDoubleHire(rig.company, List.of(rig.war)));

      assertTrue(olderById.isActive());
      assertFalse(current.isActive());
      assertTrue(reputationEvents.isEmpty());
      assertEquals(0.0, current.getAccruedToHirer());
      assertTrue(
          ContractTerminationService.resolveDoubleHire(rig.company, List.of(rig.war)).isEmpty());
      assertEquals(
          0.0, ContractTerminationService.terminate(current, TerminationReason.LOYALTY_CONFLICT));
    }
  }

  @Test
  void correctingServedDaysRepricesUnamendedHistoryAndSurvivesPersistence() {
    try (FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction host = domain.saved("host", "HostLeader");
      Faction hirer = domain.saved("hirer", "HirerLeader");
      MercenaryCompany company = company(domain.guild(host, "blades", "Captain"));
      MercenaryContract contract = contract(company, hirer, 2, System.currentTimeMillis());
      contract.setDaysServed(3);
      assertEquals(60.0, contract.getServedDaysOwed());
      MercenaryContract restored = new MercenaryContract(company, contract.serialize());
      assertEquals(60.0, restored.getServedDaysOwed());
      restored.setDaysServed(-1);
      assertEquals(0.0, restored.getServedDaysOwed());
      restored.addDayServed();
      assertEquals(20.0, restored.getServedDaysOwed());
    }
  }

  private MercenaryContract contract(MercenaryCompany company, Faction hirer, int slots, long now) {
    MercenaryContract contract =
        new MercenaryContract(
            company,
            hirer,
            ContractKind.MERCENARY,
            new ContractTerms(slots, 50, 10, 7, 50, 500),
            now);
    company.getContractHandler().add(contract);
    assertTrue(contract.activate());
    return contract;
  }

  private MercenaryContract restoredContract(
      MercenaryCompany company, Faction hirer, String id, long now) {
    MercenaryContract original =
        new MercenaryContract(
            company, hirer, ContractKind.MERCENARY, new ContractTerms(1, 50, 10, 7, 50, 500), now);
    MercenaryContractData data = original.serialize();
    data.id = id;
    data.status = ContractStatus.ACTIVE.name();
    MercenaryContract restored = new MercenaryContract(company, data);
    company.getContractHandler().add(restored);
    return restored;
  }

  private final class ContractRig implements AutoCloseable {
    final FactionDomainFixture domain = new FactionDomainFixture();
    final Faction host = domain.saved("host", "HostLeader");
    final Faction hirer = domain.saved("hirer", "HirerLeader");
    final Faction enemy = domain.saved("enemy", "EnemyLeader");
    final MercenaryCompany company = company(domain.guild(host, "blades", "Captain"));
    final War war = new War(551094, hirer, enemy);
    final List<Battle> previousBattles = new ArrayList<>(BattleManager.get());
    final PlayerUuidLookup previousUuids = MercenaryEngagements.uuidLookup();
    final ContractReputationSeam previousReputation =
        ContractTerminationService.getReputationSeam();
    final ContractBattleGate.Fighting previousFighting;
    final Map<Object, Object> snapshots;
    final Map<Object, Object> savedSnapshots;
    final Map<Object, Object> results;
    final Map<Object, Object> savedResults;

    ContractRig() throws Exception {
      Field fighting = ContractBattleGate.class.getDeclaredField("fighting");
      fighting.setAccessible(true);
      previousFighting = (ContractBattleGate.Fighting) fighting.get(null);
      snapshots = map("startSnapshots");
      results = map("results");
      savedSnapshots = new HashMap<>(snapshots);
      savedResults = new HashMap<>(results);
      AttendanceService.reset();
      BattleManager.get().clear();
      ContractBattleGate.setFighting(null);
      WarManager.get().add(war);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> map(String name) throws Exception {
      Field field = AttendanceService.class.getDeclaredField(name);
      field.setAccessible(true);
      return (Map<Object, Object>) field.get(null);
    }

    @Override
    public void close() {
      try {
        BattleManager.get().clear();
        BattleManager.get().addAll(previousBattles);
        ContractBattleGate.setFighting(previousFighting);
        MercenaryEngagements.setUuidLookup(previousUuids);
        ContractTerminationService.setReputationSeam(previousReputation);
        snapshots.clear();
        snapshots.putAll(savedSnapshots);
        results.clear();
        results.putAll(savedResults);
      } finally {
        domain.close();
      }
    }
  }

  private MercenaryCompany company(Guild guild) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("item.material", "IRON_SWORD");
    yaml.set("mercenary", true);
    MercenaryCompany company =
        new MercenaryCompany(guild, "Blades", new Regiment("mercenary", yaml), 0);
    if (guild != null) guild.setCompany(company);
    company.enlistLeader();
    return company;
  }
}
