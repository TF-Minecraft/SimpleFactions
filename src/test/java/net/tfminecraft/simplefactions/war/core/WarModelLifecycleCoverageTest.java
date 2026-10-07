package net.tfminecraft.simplefactions.war.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.WarData;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.civilwar.split.CivilWarMemberMove;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarWartimeVassalEnd;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WarModelLifecycleCoverageTest {
  @Test
  void generatedWarIdsStayDistinctAndPersistedPreparationFreezeExpiresAtItsDeadline()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      War generated = new War(rig.attacker, rig.defender);
      assertNotEquals(rig.war.getId(), generated.getId());
      WarManager.addWar(generated);
      War next = new War(rig.attacker, rig.defender);
      assertNotEquals(generated.getId(), next.getId());
      assertSame(generated, WarManager.getById(generated.getId()));
      Instant deadline = Instant.parse("2026-10-11T20:00:00Z");
      generated.setPreparationFrozenUntil(deadline);
      War restored = jsonRoundTrip(WarMapper.toData(generated));
      assertTrue(restored.isPreparationFrozen(deadline.minusSeconds(1)));
      assertFalse(restored.isPreparationFrozen(deadline));
      assertFalse(restored.isPreparationFrozen(deadline.plusSeconds(1)));
      assertFalse(restored.isPreparationFrozen(null));
      assertFalse(next.isPreparationFrozen(deadline.minusSeconds(1)));
      restored.end(WarEndReason.ADMIN_END);
      War ended = jsonRoundTrip(WarMapper.toData(restored));
      assertFalse(ended.isActive());
      assertFalse(ended.isPreparationFrozen(deadline.minusSeconds(1)));
      assertEquals(WarEndReason.ADMIN_END, ended.getEndReason());
      assertNotNull(ended.getEndedAt());
      assertEquals(deadline, ended.getPreparationFrozenUntil());
    }
  }

  @Test
  void restoringLegacyParticipantWithNullOptionalCollectionsKeepsItsLeader() throws Exception {
    try (Fixture rig = new Fixture()) {
      Gson gson = new Gson();
      var saved = gson.toJsonTree(WarMapper.toData(rig.war)).getAsJsonObject();
      var participant =
          saved
              .getAsJsonObject("attackers")
              .getAsJsonArray("participants")
              .get(0)
              .getAsJsonObject();
      participant.add("subjects", JsonNull.INSTANCE);
      participant.add("allies", JsonNull.INSTANCE);
      participant.add("backers", JsonNull.INSTANCE);
      for (String optional :
          List.of(
              "wartimeInstallationOwners",
              "battleInstallationPicks",
              "raidRepairLockUntil",
              "battleVotes")) saved.add(optional, JsonNull.INSTANCE);
      War restored = WarMapper.fromData(gson.fromJson(saved, WarData.class));
      assertNotNull(restored);
      assertTrue(restored.isMainParticipant(rig.attacker));
      assertTrue(restored.isMainParticipant(rig.defender));
      assertSame(rig.attacker, restored.getAttackers().getLeader());
      assertEquals(1, restored.getAttackers().getMainParticipants().size());
      Participant recovered = restored.getParticipant(rig.attacker);
      assertTrue(recovered.getSubjects().isEmpty());
      assertTrue(recovered.getAllies().isEmpty());
      assertTrue(recovered.getBackers().isEmpty());
      assertTrue(restored.getWartimeInstallationOwners().isEmpty());
      assertTrue(restored.getBattleInstallationPicks().isEmpty());
      assertTrue(restored.getRaidRepairLockUntil().isEmpty());
      assertTrue(restored.getBattleVotes().isEmpty());
    }
  }

  @Test
  void corruptedOptionalSavedRowsDoNotEraseValidCampaignState() throws Exception {
    try (Fixture rig = new Fixture()) {
      Gson gson = new Gson();
      var saved = gson.toJsonTree(WarMapper.toData(rig.war)).getAsJsonObject();
      var optional =
          JsonParser.parseString(
                  """
{
  "fortControllers": {"valid_fort": "defender", "": "aggressor", "removed": null},
  "campaignBattleSchedule": [null, {"provinceId": 0}, {"provinceId": 11, "required": true}],
  "postBattleChoicePhase": "removed_legacy_phase",
  "initiativeHolderCoalition": "unknown_legacy_coalition",
  "initiativeHolder": "unknown_legacy_role",
  "battleInstallationPicks": {"": ["ignored"], "empty": [], "missing": null, "valid_faction": [null, " ", "port", "port"]},
  "raidRepairLockUntil": {"": "2026-10-10T20:00:00Z", "missing": null, "blank": " ", "broken": "yesterday", "port": "2026-10-10T20:00:00Z"},
  "battleVotes": {"": [20], "bad-uuid": [20], "00000000-0000-0000-0000-000000000001": [20, 21, 20], "00000000-0000-0000-0000-000000000002": null},
  "civilWarHostFactionId": "host",
  "civilWarTransferredProvinces": {"": "ignored", "bad": "ignored", "11": "original_owner"},
  "civilWarVassalEnds": [null, {"factionId": "subject", "formerOverlordId": "liege", "relationTypeId": "vassal"}],
  "civilWarMemberMoves": [null, {"player": "Alice", "originGuildId": "home", "originWasGuildLeader": true}]
}
""")
              .getAsJsonObject();
      optional.entrySet().forEach(entry -> saved.add(entry.getKey(), entry.getValue()));
      War restored = WarMapper.fromData(gson.fromJson(saved, WarData.class));
      assertEquals(Map.of("valid_fort", CampaignCoalition.DEFENDER), restored.getFortControllers());
      assertEquals(1, restored.getCampaignBattleSchedule().size());
      assertEquals(11, restored.getCampaignBattleSchedule().getFirst().provinceId());
      assertEquals(
          CampaignBattleKind.FIELD, restored.getCampaignBattleSchedule().getFirst().kind());
      assertEquals(PostBattleChoicePhase.NONE, restored.getPostBattleChoicePhase());
      assertEquals(CampaignCoalition.AGGRESSOR, restored.getInitiativeHolderCoalition());
      assertEquals(
          Map.of("valid_faction", new LinkedHashSet<>(List.of("port"))),
          restored.getBattleInstallationPicks());
      assertEquals(
          Map.of("port", Instant.parse("2026-10-10T20:00:00Z")), restored.getRaidRepairLockUntil());
      assertEquals(
          Map.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), Set.of(20, 21)),
          restored.getBattleVotes());
      CivilWarSnapshot snapshot = restored.getCivilWarSnapshot();
      assertEquals(Map.of(11, "original_owner"), snapshot.getTransferredProvinces());
      assertEquals(
          List.of(new CivilWarWartimeVassalEnd("subject", "liege", "vassal")),
          snapshot.getWartimeVassalEnds());
      assertEquals(
          List.of(new CivilWarMemberMove("Alice", "home", true)), snapshot.getMemberMoves());
      var sanitized = WarMapper.toData(restored);
      assertEquals(List.of("port"), sanitized.battleInstallationPicks.get("valid_faction"));
      assertEquals(1, sanitized.civilWarVassalEnds.size());
      assertEquals(1, sanitized.civilWarMemberMoves.size());
    }
  }

  @Test
  void publicCampaignStateEditsPersistValidRowsWithoutAliasingInputMaps() throws Exception {
    try (Fixture rig = new Fixture()) {
      var war = rig.war;
      war.putFortController(null, CampaignCoalition.AGGRESSOR);
      war.putFortController(" ", CampaignCoalition.AGGRESSOR);
      war.putFortController("fort", null);
      war.putFortController("fort", CampaignCoalition.DEFENDER);
      war.putWartimeInstallationOwner(null, rig.attacker.getId());
      war.putWartimeInstallationOwner("port", " ");
      war.putWartimeInstallationOwner("port", rig.attacker.getId());
      war.putWartimeInstallationOwner("port", rig.defender.getId());
      war.recordLocationBattle(null);
      war.recordLocationBattle(" ");
      war.recordLocationBattle("fort");
      war.recordLocationBattle("fort");
      assertEquals(0, war.getLocationBattleCount(null));
      assertEquals(0, war.getLocationBattleCount(" "));
      var picks = new LinkedHashMap<String, LinkedHashSet<String>>();
      picks.put(null, new LinkedHashSet<>(List.of("bad")));
      picks.put(" ", new LinkedHashSet<>(List.of("bad")));
      picks.put("missing", null);
      picks.put(rig.attacker.getId(), new LinkedHashSet<>(List.of("port")));
      war.setBattleInstallationPicks(picks);
      picks.get(rig.attacker.getId()).clear();
      Instant until = Instant.parse("2026-10-11T20:00:00Z");
      var locks = new LinkedHashMap<String, Instant>();
      locks.put(null, until);
      locks.put(" ", until);
      locks.put("missing", null);
      locks.put("port", until);
      war.setRaidRepairLockUntil(locks);
      locks.clear();
      war.setBattleInstallationPicksBattleDay(LocalDate.of(2026, 10, 10));
      CivilWarSnapshot snapshot = new CivilWarSnapshot();
      snapshot.setHostFactionId(rig.defender.getId());
      snapshot.setTransferredProvinces(new LinkedHashMap<>(Map.of(11, rig.defender.getId())));
      snapshot.getTransferredProvinces().put(null, "invalid_province");
      snapshot.setWartimeVassalEnds(
          Arrays.asList(null, new CivilWarWartimeVassalEnd("subject", "liege", "vassal")));
      snapshot.setMemberMoves(Arrays.asList(null, new CivilWarMemberMove("Alice", "home", false)));
      war.setCivilWarSnapshot(snapshot);
      var saved = WarMapper.toData(war);
      War restored = jsonRoundTrip(saved);
      assertEquals(3, restored.getSchemaVersion());
      assertEquals(Map.of("fort", CampaignCoalition.DEFENDER), restored.getFortControllers());
      assertEquals(Map.of("port", rig.attacker.getId()), restored.getWartimeInstallationOwners());
      assertEquals(2, restored.getLocationBattleCount("fort"));
      assertEquals(
          Map.of(rig.attacker.getId(), new LinkedHashSet<>(List.of("port"))),
          restored.getBattleInstallationPicks());
      assertEquals(LocalDate.of(2026, 10, 10), restored.getBattleInstallationPicksBattleDay());
      assertEquals(Map.of("port", until), restored.getRaidRepairLockUntil());
      assertEquals(
          Map.of(11, rig.defender.getId()),
          restored.getCivilWarSnapshot().getTransferredProvinces());
      assertEquals(1, restored.getCivilWarSnapshot().getWartimeVassalEnds().size());
      assertEquals(1, restored.getCivilWarSnapshot().getMemberMoves().size());
      war.setBattleVotes(null);
      war.setBattleInstallationPicks(null);
      war.setCampaignRaidsUsed(null);
      war.setRaidRepairLockUntil(null);
      var cleared = jsonRoundTrip(WarMapper.toData(war));
      assertTrue(cleared.getBattleVotes().isEmpty());
      assertTrue(cleared.getBattleInstallationPicks().isEmpty());
      assertTrue(cleared.getCampaignRaidsUsed().isEmpty());
      assertTrue(cleared.getRaidRepairLockUntil().isEmpty());
      assertEquals(Map.of("port", until), restored.getRaidRepairLockUntil());
    }
  }

  @Test
  void participantPromotionAndReloadedFactionIdentityPreserveDistinctJoinedRoles()
      throws Exception {
    try (Fixture rig = new Fixture()) {
      var allyType = rig.domain.relationType("ally", Map.of("mutual", true));
      Faction subject = rig.domain.saved("subject", "Subject");
      Faction ally = rig.domain.saved("ally", "Ally");
      Faction backer = rig.domain.saved("backer", "Backer");
      Faction outsider = rig.domain.saved("outsider", "Outsider");
      rig.domain.subject(rig.attacker, subject);
      rig.attacker.setRelation(ally, new Relation(allyType, RelationLoader.getDefaultAttitude()));
      ally.setRelation(rig.attacker, new Relation(allyType, RelationLoader.getDefaultAttitude()));
      Participant participant = new Participant(rig.attacker, true);
      participant.getAllies().put(ally, true);
      assertFalse(participant.addBacker(null));
      assertFalse(participant.addBacker(rig.attacker));
      assertTrue(participant.addBacker(ally));
      assertTrue(participant.addBacker(backer));
      assertFalse(participant.addBacker(backer));
      participant.getBackers().add(null);
      assertFalse(participant.isJoinedSecondary(null));
      assertEquals(Set.of(ally, backer), Set.copyOf(participant.getJoinedSecondaries()));
      Faction unidentified = rig.domain.saved("unidentified_backer", "Unidentified");
      assertTrue(participant.addBacker(unidentified));
      unidentified.setId(null);
      try {
        assertFalse(participant.isJoinedSecondary(unidentified));
        assertFalse(participant.addBacker(unidentified));
        assertEquals(Set.of(ally, backer), Set.copyOf(participant.getJoinedSecondaries()));
      } finally {
        unidentified.setId("unidentified_backer");
        participant.clean(unidentified);
      }
      FactionManager.factions.remove(ally);
      Faction reloadedAlly = rig.domain.saved(ally.getId(), "Ally");
      reloadedAlly.setRelation(
          rig.attacker, new Relation(allyType, RelationLoader.getDefaultAttitude()));
      assertTrue(participant.isJoinedSecondary(reloadedAlly));
      assertTrue(participant.isCivilWar());
      participant.setCivilWar(false);
      rig.war.getAttackers().getMainParticipants().set(0, participant);
      assertSame(rig.war.getAttackers(), rig.war.getSide(backer));
      assertSame(rig.defender, rig.war.getEnemy(backer));
      assertSame(rig.attacker, rig.war.getEnemy(rig.defender));
      assertNull(rig.war.getEnemy(outsider));
      assertFalse(rig.war.isParticipating(null));
      assertNull(rig.war.getOppositeSide(outsider));
      Participant promoted = rig.war.getAttackers().addNewParticipant(subject, participant);
      assertTrue(participant.getSubjects().isEmpty());
      assertSame(subject, promoted.getLeader());
      assertEquals("main_attacker", rig.war.getType(subject));
      assertEquals("main_defender", rig.war.getType(rig.defender));
      assertEquals("secondary_participant", rig.war.getType(backer));
      assertNull(participant.getWarGoal(outsider));
      participant.clean(reloadedAlly);
      assertFalse(participant.getBackers().contains(ally));
      assertEquals(
          List.of(backer),
          participant.getBackers().stream().filter(java.util.Objects::nonNull).toList());
      var saved = WarMapper.toData(rig.war);
      War restored = jsonRoundTrip(saved);
      assertEquals(2, restored.getAttackers().getMainParticipants().size());
      assertTrue(restored.isMainParticipant(subject));
      assertTrue(restored.getParticipant(rig.attacker).isJoinedSecondary(backer));
      assertFalse(restored.getParticipant(rig.attacker).isCivilWar());
    }
  }

  @Test
  void legacyParticipantGoalsRemainQueryableUntilWritingTheCurrentWarSchema() throws Exception {
    try (Fixture rig = new Fixture()) {
      var goal = new WarGoal("annex", new YamlConfiguration());
      Participant participant =
          new Participant(rig.attacker, List.of(), Map.of(), Map.of(rig.defender, goal), false);
      rig.war.getAttackers().getMainParticipants().set(0, participant);
      assertSame(goal, rig.war.getWarGoalsOn(rig.defender).get(rig.attacker));
      assertTrue(rig.war.getWarGoalsOn(rig.attacker).isEmpty());
      assertNull(participant.getWarGoal(rig.attacker));
      War restored = jsonRoundTrip(WarMapper.toData(rig.war));
      assertTrue(restored.getWarGoalsOn(rig.defender).isEmpty());
      assertTrue(restored.isMainParticipant(rig.attacker));
      assertSame(rig.war.getDefenders(), rig.war.getOppositeSide(rig.attacker));
      assertSame(rig.war.getAttackers(), rig.war.getOppositeSide(rig.defender));
    }
  }

  @Test
  void developmentRosterRefreshIgnoresUnrelatedBattlesAndPreservesRealMembers() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "warDevmodePhantomCount");
      Cache.warDevmodePhantomCount = 4;
      var manual = BattleFactory.createBlank(BattleType.FIELD, "manual");
      var missingWar = BattleFactory.createBlank(BattleType.FIELD, "missing_war");
      missingWar.setWarId(-999);
      BattleManager.addBattle(manual);
      BattleManager.addBattle(missingWar);
      assertEquals(0, WarDevMode.refreshCampaignBattleDummies());
      assertEquals(0, WarDevMode.setEnabled(true));
      assertEquals(0, WarDevMode.refreshCampaignBattleDummies());
      var campaign = BattleFactory.createBlank(BattleType.FIELD, "unseeded_campaign");
      campaign.setWarId(rig.war.getId());
      BattleManager.addBattle(campaign);
      assertEquals(0, WarDevMode.refreshCampaignBattleDummies());
      Warband band = Warband.createWithMemberIds("manual_roster", rig.alice.getUniqueId(), false);
      WarbandManager.get().add(band);
      WarDevMode.seedDummyMembersIfEnabled(band, null, campaign, BattleTemplate.ATTACKER_SIDE);
      assertEquals(1, band.getMemberCount());
      WarDevMode.seedDummyMembersIfEnabled(band, rig.war, campaign, BattleTemplate.ATTACKER_SIDE);
      assertEquals(4, band.getDummyMemberCount());
      assertEquals(1, band.getRealMemberCount());
      assertEquals(rig.alice.getUniqueId(), band.getLeaderId());
      assertEquals(
          WarDevMode.dummyDisplayName(band.getId(), 0),
          band.getMemberDisplayName(WarDevMode.dummyMemberId(band.getId(), 0)));
      WarDevMode.seedDummyMembersOnFirstSignupIfEnabled(
          band, rig.war, campaign, BattleTemplate.ATTACKER_SIDE);
      assertEquals(5, band.getMemberCount());
      rig.war.end(WarEndReason.ADMIN_END);
      assertEquals(0, WarDevMode.refreshCampaignBattleDummies());
      assertEquals(1, WarDevMode.setEnabled(false));
      WarDevMode.seedDummyMembersOnFirstSignupIfEnabled(
          band, rig.war, campaign, BattleTemplate.ATTACKER_SIDE);
      assertEquals(0, band.getDummyMemberCount());
      assertEquals(1, band.getRealMemberCount());
      assertEquals(rig.alice.getUniqueId(), band.getLeaderId());
      WarDevMode.seedDummyMembers(null, 1);
      WarDevMode.seedDummyMembers(band, 0);
      assertEquals(1, band.getMemberCount());
    }
  }

  @Test
  void anUnresolvedCampaignSideCannotCreatePhantomsWithoutAnEligibleTroopPool() throws Exception {
    try (Fixture rig = new Fixture()) {
      rig.remember(Cache.class, "warDevmodePhantomCount");
      Cache.warDevmodePhantomCount = 4;
      WarDevMode.setEnabled(true);
      var battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_context");
      battle.setWarId(rig.war.getId());
      battle.setProvinceId(11);
      Warband shell =
          Warband.createCampaignSideShell(
              "campaign_context_attacker",
              rig.war,
              rig.war.getAttackers(),
              BattleTemplate.ATTACKER_SIDE);
      WarDevMode.seedCampaignSideIfEnabled(shell, rig.war, battle, "removed_side");
      assertEquals(0, shell.getDummyMemberCount());
      assertEquals(0, shell.getMemberCount());
      assertTrue(shell.isPendingLeader());
      assertTrue(BattleManager.get().isEmpty());
    }
  }

  private static War jsonRoundTrip(WarData data) {
    Gson gson = new Gson();
    return WarMapper.fromData(gson.fromJson(gson.toJson(data), WarData.class));
  }
}
