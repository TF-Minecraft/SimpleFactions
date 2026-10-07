package net.tfminecraft.simplefactions.government.movement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.database.CauseData;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.database.PoolData;
import net.tfminecraft.simplefactions.database.ProposalData;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MovementCoverageTest {
  private FactionDomainFixture fixture;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
  }

  @AfterEach
  void cleanup() {
    if (fixture != null) fixture.close();
  }

  private Faction host() {
    Faction faction = fixture.saved("home", "Alice");
    for (String member : List.of("Bob", "Cara", "Drew")) faction.addMember(member);
    return faction;
  }

  private Proposal tax(Faction faction, String proposer) {
    Proposal proposal = new Proposal(proposer, faction.getGovernment());
    proposal.setTaxProposal(new TaxLawChange(TaxTarget.CITIZENS, null, 12));
    return proposal;
  }

  private Movement movement(Faction faction, String founder) {
    faction.getGovernment().startMovement(founder, tax(faction, founder));
    return faction.getGovernment().getMovementByLeader(founder);
  }

  @ParameterizedTest
  @ValueSource(strings = {"citizen", "guild", "vassal", "founder"})
  void repeatedSupportDoesNotDuplicateMembersOrArtificiallyIncreasePower(String kind) {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    Object supporter;
    if (kind.equals("guild")) {
      Guild guild = fixture.guild(faction, "merchants", "Eve");
      guild.setStance(Stance.OPPOSE);
      supporter = guild;
    } else if (kind.equals("vassal")) {
      Faction vassal = fixture.saved("vassal", "Eve");
      fixture.subject(faction, vassal);
      vassal.getOrCreateMainGuild().setStance(Stance.OPPOSE);
      supporter = vassal;
    } else {
      supporter = kind.equals("founder") ? "Bob" : "Cara";
    }
    int before = movement.getAllMembers().size();
    movement.join(supporter, null);
    int expected = before + (kind.equals("founder") ? 0 : 1);
    assertEquals(expected, movement.getAllMembers().size());
    double power = movement.getPower();
    movement.changeOrganization(20);
    double organization = movement.getOrganization();
    movement.join(supporter, null);
    assertEquals(expected, movement.getAllMembers().size());
    assertEquals(power, movement.getPower());
    assertEquals(organization, movement.getOrganization());
  }

  @Test
  void repeatedForeignBackingCountsTheSameRealmOnlyOnce() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    Faction backer = fixture.saved("foreign", "Eve");
    movement.joinAsForeignBacker(backer);
    double power = movement.getPower();
    movement.joinAsForeignBacker(backer);
    assertEquals(List.of(backer), movement.getForeignBackers());
    assertEquals(power, movement.getPower());
  }

  @Test
  void leaderlessCauseDoesNotBreakLookupOfAnotherCauseLeader() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    movement.getCauses().getFirst().setLeader(null);
    movement.createCause("Cara", tax(faction, "Cara"));
    Cause found = assertDoesNotThrow(() -> movement.getCauseByLeader("Cara"));
    assertNotNull(found);
    assertEquals("Cara", found.getLeader());
    assertNull(movement.getCauseByLeader("missing"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void tickRemovesCauseSupportThatNowSupportsTheGovernment(boolean subject) {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    Cause cause = movement.getCauses().getFirst();
    if (subject) {
      Faction vassal = fixture.saved("vassal", "Eve");
      fixture.subject(faction, vassal);
      vassal.getOrCreateMainGuild().setStance(Stance.OPPOSE);
      movement.join(vassal, cause);
      assertEquals(List.of(vassal), cause.getPool().getFactions());
      vassal.getOrCreateMainGuild().setStance(Stance.SUPPORT);
      movement.tick();
      assertTrue(cause.getPool().getFactions().isEmpty());
    } else {
      Guild guild = fixture.guild(faction, "merchants", "Eve");
      guild.setName("Renamed merchants");
      guild.setStance(Stance.OPPOSE);
      movement.join(guild, cause);
      assertEquals(List.of(guild), cause.getPool().getGuilds());
      guild.setStance(Stance.SUPPORT);
      movement.tick();
      assertTrue(cause.getPool().getGuilds().isEmpty());
    }
    assertEquals(List.of("Bob"), movement.getAllMembers());
    assertTrue(faction.getGovernment().getMovements().contains(movement));
  }

  @Test
  void organizationRespectsPhaseBoundsAndOnlyAdjacentPhasesCanBeSelected() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    assertSame(faction, movement.getFaction());
    assertTrue(movement.hasLeader());
    assertTrue(movement.isLeader("bOB"));
    assertFalse(movement.isLeader("Cara"));
    assertEquals(Phase.GATHERING, movement.getPhase());
    assertEquals(20, movement.getMaxOrganization());
    assertFalse(movement.canChangeToPhase(Phase.GATHERING));
    assertFalse(movement.canChangeToPhase(Phase.PRESSURING));
    movement.changeOrganization(100);
    assertEquals(20, movement.getOrganization());
    assertTrue(movement.canChangeToPhase(Phase.PRESSURING));
    assertFalse(movement.canChangeToPhase(Phase.REBELLIOUS));
    movement.setPhase(Phase.PRESSURING);
    assertTrue(movement.canChangeToPhase(Phase.GATHERING));
    movement.changeOrganization(40);
    assertEquals(40, movement.getOrganization());
    movement.setPhase(Phase.GATHERING);
    assertEquals(20, movement.getOrganization());
    movement.changeOrganization(-100);
    assertEquals(0, movement.getOrganization());
    movement.setLeader(null);
    assertFalse(movement.hasLeader());
    assertFalse(movement.isLeader("Bob"));
    assertEquals(-10, movement.getOrganizationGain());
    movement.setLeader("Bob");
    assertTrue(movement.canBeLeader("Bob"));
    assertFalse(movement.canBeLeader("Cara"));
    assertTrue(movement.isMember("Bob"));
    assertFalse(movement.isMember("Alice"));
    assertEquals(25, movement.getPower());
    assertEquals(75, movement.getStabilityEffect());
  }

  @Test
  void citizenSupportMembershipChangesOrganizationAndFreezingBlocksNewMembersAndCauses() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    movement.changeOrganization(20);
    assertTrue(movement.canJoin("Cara", null));
    movement.join("Cara", null);
    assertEquals(List.of("Cara"), movement.getSupporters().getCitizens());
    assertEquals(0, movement.getOrganization());
    movement.leave("Cara", null);
    assertTrue(movement.getSupporters().getCitizens().isEmpty());
    assertEquals(20, movement.getOrganization());
    assertNotNull(movement.joinBlockReason("Stranger", null, false));
    movement.join("Stranger", null);
    assertFalse(movement.isMember("Stranger"));
    assertNotNull(movement.joinBlockReason(42, null, true));
    movement.join(42, null);
    assertEquals(List.of("Bob"), movement.getAllMembers());
    Cause cause = movement.getCauses().getFirst();
    assertTrue(movement.canJoin("Cara", cause, false));
    movement.join("Cara", cause);
    assertEquals(List.of("Bob", "Cara"), cause.getPool().getCitizens());
    assertFalse(movement.canJoin("Cara", cause));
    movement.join("Cara", cause);
    assertEquals(2, cause.getPool().getCitizens().size());
    movement.leave("Cara", cause);
    assertEquals(List.of("Bob"), cause.getPool().getCitizens());
    movement.setFrozen(true);
    movement.join("Cara", null);
    movement.createCause("Cara", tax(faction, "Cara"));
    movement.tick();
    assertTrue(movement.isFrozen());
    assertEquals(1, movement.getCauses().size());
    assertEquals(List.of("Bob"), movement.getAllMembers());
    movement.setFrozen(false);
    movement.removeCause(cause);
    movement.tick();
    assertFalse(faction.getGovernment().getMovements().contains(movement));
  }

  @Test
  void newCausesMoveTheirFounderOutOfGeneralSupportAndRespectTheThreeCauseLimit() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    movement.join("Cara", null);
    movement.createCause("Cara", tax(faction, "Cara"));
    assertTrue(movement.getSupporters().getCitizens().isEmpty());
    assertEquals("Cara", movement.getCauses().get(1).getLeader());
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.setStance(Stance.OPPOSE);
    movement.join(guild, null);
    movement.createCause("Eve", tax(faction, "Eve"));
    assertTrue(movement.getSupporters().getGuilds().isEmpty());
    assertEquals(List.of(guild), movement.getCauses().get(2).getPool().getGuilds());
    movement.createCause("Drew", tax(faction, "Drew"));
    assertEquals(3, movement.getCauses().size());
    assertNull(movement.getCauseByLeader("Drew"));
    movement.removeCause(movement.getCauses().get(2));
    Faction subject = fixture.saved("vassal", "Finn");
    fixture.subject(faction, subject);
    subject.getOrCreateMainGuild().setStance(Stance.OPPOSE);
    movement.join(subject, null);
    movement.createCause("Finn", tax(faction, "Finn"));
    assertTrue(movement.getSupporters().getFactions().isEmpty());
    assertEquals(List.of(subject), movement.getCauses().get(2).getPool().getFactions());
  }

  @Test
  void supporterPermissionsDistinguishFactionRolesForeignRealmsAndExistingOtherMovements() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    Movement other = movement(faction, "Cara");
    assertTrue(movement.citizenSupporterBlockReason("Alice", true).contains("not a citizen"));
    assertTrue(movement.citizenSupporterBlockReason("Cara", false).contains("another movement"));
    assertNull(movement.citizenSupporterBlockReason("Drew", true));
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.addMember("Finn");
    guild.setStance(Stance.OPPOSE);
    Faction foreign = fixture.saved("foreign", "Grace");
    Guild foreignGuild = fixture.guild(foreign, "foreign_guild", "Hank");
    assertNotNull(movement.guildSupporterBlockReason(foreignGuild, true));
    assertTrue(
        movement
            .guildSupporterBlockReason(faction.getOrCreateMainGuild(), false)
            .contains("base guild"));
    guild.setStance(Stance.SUPPORT);
    assertTrue(movement.guildSupporterBlockReason(guild, true).contains("SUPPORT"));
    movement.join(guild, null);
    assertFalse(movement.isMember("Eve"));
    guild.setStance(Stance.OPPOSE);
    assertNull(movement.joinBlockReason(guild, null, false));
    other.join(guild, null);
    assertTrue(movement.guildSupporterBlockReason(guild, true).contains("another movement"));
    other.leave(guild, null);
    assertNull(movement.guildSupporterBlockReason(guild, false));
    Faction vassal = fixture.saved("vassal", "Iris");
    vassal.addMember("Jo");
    assertTrue(movement.factionSupporterBlockReason(vassal, false).contains("must be a vassal"));
    fixture.subject(faction, vassal);
    vassal.getOrCreateMainGuild().setStance(Stance.SUPPORT);
    assertTrue(movement.factionSupporterBlockReason(vassal, true).contains("SUPPORT"));
    movement.join(vassal, null);
    assertFalse(movement.isMember("Iris"));
    vassal.getOrCreateMainGuild().setStance(Stance.OPPOSE);
    assertNull(movement.joinBlockReason(vassal, null, false));
    other.join(vassal, null);
    assertTrue(movement.factionSupporterBlockReason(vassal, true).contains("another movement"));
    other.leave(vassal, null);
    assertNull(movement.factionSupporterBlockReason(vassal, false));
    assertTrue(movement.foreignBackerBlockReason(faction, false).contains("same realm"));
    assertTrue(movement.foreignBackerBlockReason(vassal, true).contains("same realm"));
    assertNull(movement.foreignBackerBlockReason(foreign, false));
    other.joinAsForeignBacker(foreign);
    assertTrue(movement.foreignBackerBlockReason(foreign, true).contains("already backing"));
    movement.joinAsForeignBacker(foreign);
    assertTrue(movement.getForeignBackers().isEmpty());
    other.leaveAsForeignBacker(foreign);
    movement.joinAsForeignBacker(foreign);
    assertEquals(List.of(foreign), movement.getForeignBackers());
    fixture.subject(faction, foreign);
    movement.checkForeignBackers();
    assertTrue(movement.getForeignBackers().isEmpty());
    fixture.provinceData.put(7, new Province(7, "PLAINS", 1));
    faction.addProvince(7);
    assertTrue(movement.guildSupporterBlockReason(guild, false).contains("one-province"));
  }

  @Test
  void cleanupPrunesGeneralSupportersAfterCitizensLeaveAndGuildsOrSubjectsChangeStance() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.setStance(Stance.OPPOSE);
    Faction vassal = fixture.saved("vassal", "Finn");
    fixture.subject(faction, vassal);
    vassal.getOrCreateMainGuild().setStance(Stance.OPPOSE);
    movement.join("Cara", null);
    movement.join(guild, null);
    movement.join(vassal, null);
    assertEquals(List.of("Bob", "Cara", "Eve", "Finn"), movement.getAllMembers());
    faction.forceRemoveMember("Cara");
    guild.setStance(Stance.SUPPORT);
    vassal.getOrCreateMainGuild().setStance(Stance.SUPPORT);
    movement.checkSupporters();
    assertTrue(movement.getSupporters().getAllMembers().isEmpty());
    assertEquals(List.of("Bob"), movement.getAllMembers());
  }

  @Test
  void joiningRoleAndQuickJoinChecksUseThePlayersActualCitizenshipAndLeadership() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.addMember("Finn");
    Faction vassal = fixture.saved("vassal", "Grace");
    vassal.addMember("Hank");
    fixture.subject(faction, vassal);
    assertEquals("Cara", movement.getJoiningAs(fixture.player("Cara")));
    assertSame(guild, movement.getJoiningAs(fixture.player("Eve")));
    assertSame(guild, movement.getJoiningAs(fixture.player("Finn")));
    assertSame(vassal, movement.getJoiningAs(fixture.player("Grace")));
    for (String name : List.of("Alice", "Hank", "Stranger"))
      assertNull(movement.getJoiningAs(fixture.player(name)), name);
    for (String name : List.of("Cara", "Eve", "Grace"))
      assertTrue(movement.quickJoinCheck(fixture.player(name)), name);
    for (String name : List.of("Bob", "Alice", "Finn", "Hank", "Stranger"))
      assertFalse(movement.quickJoinCheck(fixture.player(name)), name);
  }

  @Test
  void tradeAndForeignBackingContributeToPowerWhileTheOverallShareIsCapped() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.setStance(Stance.OPPOSE);
    Faction vassal = fixture.saved("vassal", "Finn");
    fixture.subject(faction, vassal);
    vassal.getOrCreateMainGuild().setStance(Stance.OPPOSE);
    Faction backer = fixture.saved("foreign", "Grace");
    faction.getOrCreateMainGuild().getTradeBreakdown().setTradePower(100);
    guild.getTradeBreakdown().setTradePower(40);
    vassal.getOrCreateMainGuild().getTradeBreakdown().setTradePower(50);
    backer.getOrCreateMainGuild().getTradeBreakdown().setTradePower(80);
    assertEquals(190, faction.getTotalTradePower());
    assertEquals(16.67, movement.getPower());
    movement.join(guild, null);
    movement.join(vassal, movement.getCauses().getFirst());
    movement.joinAsForeignBacker(backer);
    assertEquals(List.of(guild), movement.getAllSupportingGuilds());
    assertEquals(List.of(vassal), movement.getAllSupportingFactions());
    assertEquals(75.70, movement.getPower(), .001);
    movement.join("Cara", null);
    movement.join("Drew", null);
    assertEquals(100, movement.getPower());
    assertEquals(50, movement.getStabilityEffect());
    movement.leave(guild, null);
    movement.leave(vassal, movement.getCauses().getFirst());
    assertTrue(movement.getAllSupportingGuilds().isEmpty());
    assertTrue(movement.getAllSupportingFactions().isEmpty());
  }

  @Test
  void organizationGainRespondsToMissingTargetsLeadersAndGovernmentLegitimacy() {
    Faction faction = host();
    Movement movement = movement(faction, "Bob");
    assertEquals(7.5, movement.getOrganizationGain());
    Guild opposition = fixture.guild(faction, "merchants", "Eve");
    opposition.setStance(Stance.OPPOSE);
    assertTrue(faction.getGovernment().isIllegitimate());
    assertEquals(12, movement.getOrganizationGain());
    Proposal political = new Proposal("Cara", faction.getGovernment());
    political.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
    movement.createCause("Cara", political);
    assertEquals(2, movement.getOrganizationGain());
    movement.getCauses().get(1).setLeader(null);
    assertEquals(-1.4, movement.getOrganizationGain());
    political.setTarget("Drew");
    assertEquals(7.2, movement.getOrganizationGain());
    movement.setLeader(null);
    assertEquals(-10, movement.getOrganizationGain());
  }

  private CauseData savedCause(String type) {
    CauseData cause = new CauseData();
    cause.leader = "Bob";
    cause.proposal = new ProposalData();
    cause.proposal.proposer = "Bob";
    cause.proposal.type = type;
    cause.members.citizens.add("Bob");
    return cause;
  }

  @Test
  void persistedMovementRestoresEveryProposalAndPoolWithoutKeepingMissingEntities() {
    fixture.lawGroup("finance", Map.of("name", "Finance"));
    Faction faction = host();
    fixture.law("finance", "new", Map.of("name", "New law"));
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    Faction vassal = fixture.saved("vassal", "Finn");
    fixture.subject(faction, vassal);
    Faction foreign = fixture.saved("foreign", "Grace");
    MovementData data = new MovementData();
    data.id = "saved_movement";
    data.leader = "Bob";
    data.organization = 32.5;
    data.phase = "PRESSURING";
    data.frozen = true;
    CauseData law = savedCause("law");
    law.proposal.groupId = "finance";
    law.proposal.lawId = "new";
    law.members.guilds.addAll(List.of("merchants", "missing"));
    law.members.factions.addAll(List.of("vassal", "missing"));
    CauseData tax = savedCause("tax");
    tax.proposal.taxTarget = "CITIZENS";
    tax.proposal.newTax = 12.0;
    CauseData fee = savedCause("fee");
    fee.proposal.feeKind = "REGISTRATION_FEE";
    fee.proposal.feeVehicle = "tf:cart";
    fee.proposal.newFee = 1.5;
    CauseData political = savedCause("political");
    political.proposal.actionKey = "CHANGE_LEADER";
    political.proposal.target = "Cara";
    data.causes.addAll(List.of(law, tax, fee, political));
    data.supporters.citizens.add("Drew");
    data.supporters.guilds.add("merchants");
    data.supporters.factions.add("vassal");
    data.foreignBackers.addAll(List.of("foreign", "missing"));
    Movement restored = new Movement(faction, data);
    assertEquals("saved_movement", restored.getId());
    assertEquals("Bob", restored.getLeader());
    assertEquals(32.5, restored.getOrganization());
    assertEquals(Phase.PRESSURING, restored.getPhase());
    assertTrue(restored.isFrozen());
    assertEquals(4, restored.getCauses().size());
    assertEquals("new", restored.getCauses().getFirst().getProposal().getLaw().getId());
    assertEquals(12, restored.getCauses().get(1).getProposal().getTaxChange().getNewTax());
    assertEquals(
        "tf:cart", restored.getCauses().get(2).getProposal().getFeeChange().getVehicleTypeId());
    assertEquals(1.5, restored.getCauses().get(2).getProposal().getFeeChange().getNewRate());
    assertEquals("Cara", restored.getCauses().get(3).getProposal().getTarget());
    assertEquals(List.of(guild), restored.getCauses().getFirst().getPool().getGuilds());
    assertEquals(List.of(vassal), restored.getCauses().getFirst().getPool().getFactions());
    assertEquals(List.of(guild), restored.getSupporters().getGuilds());
    assertEquals(List.of(vassal), restored.getSupporters().getFactions());
    assertEquals(List.of(foreign), restored.getForeignBackers());
  }

  @Test
  void malformedSavedCausesDoNotDiscardValidSiblingsAndOptionalFieldsUseSafeDefaults() {
    Faction faction = host();
    MovementData data = new MovementData();
    data.phase = "old_removed_phase";
    CauseData missingGroup = savedCause("law");
    missingGroup.proposal.groupId = "missing";
    CauseData badTax = savedCause("tax");
    badTax.proposal.taxTarget = "REMOVED_TARGET";
    CauseData unknown = savedCause("unknown");
    CauseData valid = savedCause("fee");
    valid.proposal.feeKind = "TRANSFER_FEE";
    valid.members = null;
    data.causes = new ArrayList<>();
    data.causes.add(null);
    data.causes.add(new CauseData());
    data.causes.addAll(List.of(missingGroup, badTax, unknown, valid));
    data.supporters = new PoolData();
    data.supporters.citizens = null;
    data.supporters.guilds = null;
    data.supporters.factions = null;
    data.foreignBackers = null;
    Movement restored = new Movement(faction, data);
    assertNotNull(restored.getId());
    assertEquals(Phase.GATHERING, restored.getPhase());
    assertEquals(0, restored.getOrganization());
    assertFalse(restored.hasLeader());
    assertEquals(1, restored.getCauses().size());
    assertEquals(0, restored.getCauses().getFirst().getProposal().getFeeChange().getNewRate());
    assertTrue(restored.getSupporters().getAllMembers().isEmpty());
    MovementData empty = new MovementData();
    empty.causes = null;
    empty.supporters = null;
    empty.foreignBackers = null;
    assertTrue(new Movement(faction, empty).getCauses().isEmpty());
  }
}
