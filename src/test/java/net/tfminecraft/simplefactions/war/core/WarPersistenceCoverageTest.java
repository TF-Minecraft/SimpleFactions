package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarMemberMove;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarWartimeVassalEnd;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarPersistenceCoverageTest {
  private PersistenceFilesFixture files;
  private java.lang.reflect.Field commitmentStore;
  private Object previousCommitments;
  private FactionDomainFixture fixture;

  @BeforeEach
  void setup() throws Exception {
    files = new PersistenceFilesFixture();
    fixture = new FactionDomainFixture();
    commitmentStore =
        net.tfminecraft.simplefactions.war.commitment.WarCommitmentService.class.getDeclaredField(
            "commitmentsByWar");
    commitmentStore.setAccessible(true);
    previousCommitments = new LinkedHashMap<>((Map<?, ?>) commitmentStore.get(null));
    ((Map<?, ?>) commitmentStore.get(null)).clear();
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (commitmentStore != null) {
        @SuppressWarnings("unchecked")
        Map<Object, Object> store = (Map<Object, Object>) commitmentStore.get(null);
        store.clear();
        store.putAll((Map) previousCommitments);
      }
      if (fixture != null) fixture.close();
    } finally {
      if (files != null) files.close();
    }
  }

  @Test
  void theFirstAllocatedWarPreservesItsRegimentCommitmentsAcrossDiskReload() throws Exception {
    fixture.regiment("guard", false, 3, 0);
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    War war =
        new War(net.tfminecraft.simplefactions.managers.WarManager.newId(), attacker, defender);
    var expected =
        net.tfminecraft.simplefactions.war.commitment.WarCommitmentService.commitFaction(
            war, attacker);
    assertFalse(expected.isEmpty());
    var path = files.write("Wars/war_" + war.getId() + ".json", "{}");
    JsonUtil.writeJson(path.toFile(), WarMapper.toData(war));
    net.tfminecraft.simplefactions.war.commitment.WarCommitmentService.clearCommitments(
        war.getId());
    List<War> loaded = new Database().loadWars();
    assertEquals(1, loaded.size());
    assertEquals(war.getId(), loaded.getFirst().getId());
    assertEquals(
        expected,
        net.tfminecraft.simplefactions.war.commitment.WarCommitmentService.getCommitmentsForWar(
            war.getId()));
  }

  @Test
  void anUnknownSavedPushTargetFallsBackToTheLegacyCampaignDirection() throws Exception {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    War war = new War(802, attacker, defender);
    WarData data = WarMapper.toData(war);
    data.pushTarget = "unrecognized_direction";
    var path = files.write("Wars/war_802.json", "{}");
    JsonUtil.writeJson(path.toFile(), data);
    List<War> loaded = new Database().loadWars();
    assertEquals(1, loaded.size());
    assertEquals(war.getPushTarget(), loaded.getFirst().getPushTarget());
    assertEquals(802, loaded.getFirst().getId());
  }

  @Test
  void aMalformedOptionalRepairLockDoesNotDiscardTheWarOrValidSiblingLocks() throws Exception {
    Faction attacker = fixture.saved("attacker", "Alice");
    Faction defender = fixture.saved("defender", "Bob");
    War war = new War(801, attacker, defender);
    WarData data = WarMapper.toData(war);
    data.raidRepairLockUntil = new LinkedHashMap<>();
    data.raidRepairLockUntil.put("broken", "not-an-instant");
    data.raidRepairLockUntil.put("valid", "2026-10-09T12:00:00Z");
    var path = files.write("Wars/war_801.json", "{}");
    JsonUtil.writeJson(path.toFile(), data);
    List<War> loaded = new Database().loadWars();
    assertEquals(1, loaded.size());
    assertEquals(801, loaded.get(0).getId());
    assertEquals(
        Map.of("valid", Instant.parse("2026-10-09T12:00:00Z")),
        loaded.get(0).getRaidRepairLockUntil());
    assertSame(attacker, loaded.get(0).getAttackers().getLeader());
    assertSame(defender, loaded.get(0).getDefenders().getLeader());
  }

  @Test
  void anEndedCivilWarRetainsRestorationRecordsParticipantsAndBattleHistoryOnDisk()
      throws Exception {
    Faction attacker = fixture.saved("rebels", "Alice");
    Faction defender = fixture.saved("home", "Bob");
    Faction subject = fixture.saved("subject", "Cara");
    Faction ally = fixture.saved("ally", "Dave");
    Faction backer = fixture.saved("backer", "Elena");
    fixture.subject(attacker, subject);
    War war = new War(811, attacker, defender);
    Participant participant = war.getAttackers().getMainParticipants().getFirst();
    participant.getAllies().put(ally, true);
    participant.addBacker(backer);
    participant.setCivilWar(true);
    war.setGoal(WarGoalType.OVERTHROW);
    war.setWarType(WarType.OVERTHROW);
    war.setMovementId("reform");
    CivilWarSnapshot snapshot = new CivilWarSnapshot();
    snapshot.setHostFactionId(defender.getId());
    snapshot.setTempRebelFactionId(attacker.getId());
    snapshot.setTransferredProvinces(Map.of(7, defender.getId(), 8, defender.getId()));
    snapshot.setWartimeVassalEnds(
        List.of(new CivilWarWartimeVassalEnd(subject.getId(), defender.getId(), "vassal")));
    snapshot.setHostOldCapitalId(7);
    snapshot.setRebelCapitalId(8);
    snapshot.setWantedLeaderName("Alice");
    snapshot.setMemberMoves(
        List.of(
            new CivilWarMemberMove("Alice", "traders", true),
            new CivilWarMemberMove("Frank", "main", false)));
    snapshot.setRebelMainGuildOwnName("Merchant Company");
    snapshot.setMovedTitleId("county");
    war.setCivilWarSnapshot(snapshot);
    ScheduledCampaignBattle first =
        new ScheduledCampaignBattle(7, CampaignBattleKind.SIEGE, true, "fort");
    ScheduledCampaignBattle second =
        new ScheduledCampaignBattle(8, CampaignBattleKind.NAVAL_INVASION, false, null, "port", 7);
    war.setCampaignBattleSchedule(List.of(first, second));
    war.setCampaignCounterSchedule(List.of(first));
    war.setCampaignScheduleIndex(1);
    war.setCampaignCounterScheduleIndex(1);
    war.setCampaignProvinces(List.of(7, 8));
    war.setConcededScheduleSlots(List.of("attacker:0"));
    war.setFortControllers(Map.of("fort", CampaignCoalition.AGGRESSOR));
    war.setWartimeInstallationOwners(Map.of("fort", defender.getId()));
    war.setLocationBattleCounts(Map.of("7", 2));
    LocalDate day = LocalDate.of(2026, 10, 9);
    Instant scheduled = Instant.parse("2026-10-09T20:00:00Z");
    war.setBattleDay(day);
    war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
    war.setScheduledBattleAt(scheduled);
    war.setScheduledBattleHour(20);
    war.setScheduledBattleProvinceId(8);
    war.setSignupRemindersSent(Set.of(60, 10));
    UUID voter = UUID.fromString("00000000-0000-0000-0000-000000000011");
    war.setBattleVotes(Map.of(voter, new LinkedHashSet<>(List.of(19, 20))));
    war.setBattleInstallationPicks(Map.of("rebels", new LinkedHashSet<>(List.of("fort", "port"))));
    war.setBattleInstallationPicksBattleDay(day);
    war.setRaidRepairLockUntil(Map.of("fort", scheduled.plusSeconds(3600)));
    war.setPreparationFrozenUntil(scheduled.minusSeconds(60));
    war.setPostBattleChoiceResolved(true);
    war.end(WarEndReason.ATTACKER_VICTORY);
    WarData before = WarMapper.toData(war);
    var path = files.write("Wars/war_811.json", "{}");
    JsonUtil.writeJson(path.toFile(), before);

    War loaded = new Database().loadWars().getFirst();

    assertEquals(
        JsonUtil.GSON.toJsonTree(before), JsonUtil.GSON.toJsonTree(WarMapper.toData(loaded)));
    assertFalse(loaded.isActive());
    assertEquals(WarEndReason.ATTACKER_VICTORY, loaded.getEndReason());
    assertEquals(war.getEndedAt(), loaded.getEndedAt());
    assertEquals(
        List.of(subject), loaded.getAttackers().getMainParticipants().getFirst().getSubjects());
    assertEquals(
        Map.of(ally, true), loaded.getAttackers().getMainParticipants().getFirst().getAllies());
    assertEquals(
        List.of(backer), loaded.getAttackers().getMainParticipants().getFirst().getBackers());
    assertTrue(loaded.getAttackers().getMainParticipants().getFirst().isCivilWar());
    assertEquals(snapshot.getMemberMoves(), loaded.getCivilWarSnapshot().getMemberMoves());
    assertEquals(
        snapshot.getWartimeVassalEnds(), loaded.getCivilWarSnapshot().getWartimeVassalEnds());
    assertEquals(
        snapshot.getTransferredProvinces(), loaded.getCivilWarSnapshot().getTransferredProvinces());
    assertEquals(List.of(first, second), loaded.getCampaignBattleSchedule());
    assertEquals(war.getBattleVotes(), loaded.getBattleVotes());
    loaded.getCivilWarSnapshot().getTransferredProvinces().clear();
    assertEquals(2, snapshot.getTransferredProvinces().size());
  }

  @Test
  void incompletePublicOptionalCollectionsAreOmittedWithoutLosingValidState() {
    War war = new War(812, fixture.saved("attacker", "Alice"), fixture.saved("defender", "Bob"));
    ScheduledCampaignBattle slot =
        new ScheduledCampaignBattle(7, CampaignBattleKind.FIELD, true, null);
    war.setCampaignBattleSchedule(Arrays.asList(null, slot));
    Map<String, CampaignCoalition> controllers = new LinkedHashMap<>();
    controllers.put("", CampaignCoalition.AGGRESSOR);
    controllers.put("missing", null);
    controllers.put("fort", CampaignCoalition.DEFENDER);
    war.setFortControllers(controllers);
    war.getBattleInstallationPicks().put("", new LinkedHashSet<>(List.of("ignored")));
    war.getBattleInstallationPicks().put("missing", null);
    war.getBattleInstallationPicks().put("empty", new LinkedHashSet<>());
    war.getBattleInstallationPicks().put("attacker", new LinkedHashSet<>(List.of("fort")));
    war.getRaidRepairLockUntil().put("", Instant.EPOCH);
    war.getRaidRepairLockUntil().put("missing", null);
    war.getRaidRepairLockUntil().put("fort", Instant.EPOCH);
    UUID player = UUID.randomUUID();
    war.getBattleVotes().put(null, Set.of(20));
    war.getBattleVotes().put(UUID.randomUUID(), null);
    war.getBattleVotes().put(UUID.randomUUID(), Set.of());
    war.getBattleVotes().put(player, Set.of(20));
    CivilWarSnapshot snapshot = new CivilWarSnapshot();
    snapshot.setHostFactionId("defender");
    snapshot.getTransferredProvinces().put(null, "ignored");
    snapshot.getTransferredProvinces().put(7, "defender");
    snapshot.getWartimeVassalEnds().add(null);
    snapshot
        .getWartimeVassalEnds()
        .add(new CivilWarWartimeVassalEnd("subject", "defender", "vassal"));
    snapshot.getMemberMoves().add(null);
    snapshot.getMemberMoves().add(new CivilWarMemberMove("Cara", "main", false));
    war.setCivilWarSnapshot(snapshot);

    War loaded = WarMapper.fromData(WarMapper.toData(war));

    assertEquals(List.of(slot), loaded.getCampaignBattleSchedule());
    assertEquals(Map.of("fort", CampaignCoalition.DEFENDER), loaded.getFortControllers());
    assertEquals(Map.of("attacker", Set.of("fort")), loaded.getBattleInstallationPicks());
    assertEquals(Map.of("fort", Instant.EPOCH), loaded.getRaidRepairLockUntil());
    assertEquals(Map.of(player, Set.of(20)), loaded.getBattleVotes());
    assertEquals(Map.of(7, "defender"), loaded.getCivilWarSnapshot().getTransferredProvinces());
    assertEquals(
        List.of(new CivilWarMemberMove("Cara", "main", false)),
        loaded.getCivilWarSnapshot().getMemberMoves());
    assertEquals(
        List.of(new CivilWarWartimeVassalEnd("subject", "defender", "vassal")),
        loaded.getCivilWarSnapshot().getWartimeVassalEnds());
  }
}
