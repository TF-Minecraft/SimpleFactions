package net.tfminecraft.simplefactions.government;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.GovernmentData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.MovementData;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.espionage.SpecialPosition;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.FeeChange;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.government.proposal.TaxLawChange;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.government.stability.StabilityDebuffs;
import net.tfminecraft.simplefactions.government.stability.StabilityTuning;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.BranchModifier;
import net.tfminecraft.simplefactions.loaders.PoliticalActionLoader;
import net.tfminecraft.simplefactions.managers.SessionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.core.War;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GovernmentCoverageTest {
  private FactionDomainFixture fixture;
  private Map<Action, PoliticalAction> originalActions;
  private String originalBaseYear;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    originalActions = PoliticalActionLoader.map;
    originalBaseYear = Cache.baseYear;
    Cache.baseYear = "322 AE";
    PoliticalActionLoader.map = new LinkedHashMap<>();
    for (Action action : Action.values())
      PoliticalActionLoader.map.put(action, new PoliticalAction(action));
    when(Bukkit.getPlayer(anyString())).thenAnswer(call -> fixture.online.get(call.getArgument(0)));
    when(fixture.ui.plugin.getSessionManager()).thenReturn(new SessionManager());
  }

  @AfterEach
  void cleanup() {
    PoliticalActionLoader.map = originalActions;
    Cache.baseYear = originalBaseYear;
    if (fixture != null) fixture.close();
  }

  private Faction councilFaction(Rules type, int size) {
    fixture.lawGroup(
        "constitution",
        Map.of(
            "effects.faction.rules",
            List.of("HAS_COUNCIL true", type.name() + " true"),
            "effects.faction.council-size",
            size));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    faction.addMember("Drew");
    faction.getGovernment().getCouncil().reorganize();
    return faction;
  }

  private Proposal tax(Faction faction, String proposer, TaxTarget target, double rate) {
    Proposal proposal = new Proposal(proposer, faction.getGovernment());
    proposal.setTaxProposal(new TaxLawChange(target, null, rate));
    return proposal;
  }

  @Test
  void councilEligibilityRejectsForeignersAndOrdinaryGuildMembers() {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 3);
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.addMember("Finn");
    Faction vassal = fixture.saved("vassal", "Grace");
    vassal.addMember("Hank");
    fixture.subject(faction, vassal);
    Council council = faction.getGovernment().getCouncil();

    assertTrue(council.canBeMember("Bob", false, false));
    assertTrue(council.canBeMember("Eve", false, false));
    assertTrue(council.canBeMember("Grace", false, false));
    assertFalse(council.canBeMember("Stranger", false, false));
    assertFalse(council.canBeMember("Finn", false, false));
    assertFalse(council.canBeMember("Hank", false, false));
    assertFalse(council.canRemainMember("Stranger"));
    assertFalse(council.canRemainMember("Finn"));
    assertFalse(council.canRemainMember("Hank"));
  }

  @Test
  void councilCleanupRemovesMultipleInvalidRefusalsWithoutLosingValidRefusals() {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 3);
    Council council = faction.getGovernment().getCouncil();
    council.toggleRefuse("Alice");
    council.toggleRefuse("Stranger");
    council.toggleRefuse("Bob");
    assertDoesNotThrow(council::replace);
    assertFalse(council.refuses("Alice"));
    assertFalse(council.refuses("Stranger"));
    assertTrue(council.refuses("Bob"));
  }

  @Test
  void councilSeatsRefusalsQuorumAndProposalLimitsReflectCurrentMembers() {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 2);
    Government government = faction.getGovernment();
    Council council = government.getCouncil();
    assertEquals(Rules.APPOINTED_COUNCIL, council.getType());
    assertEquals(2, council.getMaxSize());
    assertTrue(council.couldBeBigger());
    assertEquals(50, government.getStabilityMalusFromCouncil());
    assertFalse(council.canBeMember("Alice", false, false));
    assertTrue(council.toggleRefuse("Bob"));
    assertFalse(council.canBeMember("Bob", false, false));
    assertTrue(council.canBeMember("Bob", false, true));
    assertTrue(council.toggleRefuse("Bob"));
    council.addMember("Bob");
    council.addMemberForce("Bob");
    assertEquals(1, council.getCurrentSize());
    assertFalse(council.canBeMember("Bob", false, false));
    assertEquals(.5, council.fillPercentage());
    assertEquals(25, government.getStabilityMalusFromCouncil());
    council.addMember("Cara");
    council.addMember("Drew");
    assertEquals(List.of("Bob", "Cara"), council.getMembers());
    assertFalse(council.canBeMember("Drew", false, false));
    assertTrue(council.canBeMember("Drew", true, false));
    council.replaceMember(-1, "Drew");
    council.replaceMember(2, "Drew");
    council.replaceMember(1, "Drew");
    assertEquals(List.of("Bob", "Drew"), council.getMembers());
    assertEquals(Set.of("Alice", "Bob", "Drew"), council.getEligibleVoters());
    assertFalse(council.hasEnoughValidVoters());
    fixture.player("Alice");
    fixture.player("Bob");
    assertFalse(council.hasEnoughValidVoters());
    fixture.player("Drew");
    assertTrue(council.hasEnoughValidVoters());
    assertTrue(council.isDummyAccount("DuMmY_1"));
    assertFalse(council.isDummyAccount("Bob"));
    assertFalse(council.canPropose("Cara"));
    assertTrue(council.canPropose("Bob"));
    assertFalse(council.hasProposals());
    assertFalse(council.canHostSession());
    Proposal proposal = tax(faction, "Bob", TaxTarget.CITIZENS, 12);
    assertTrue(council.canBeProposed(proposal));
    government.propose(proposal);
    assertTrue(council.hasProposals());
    assertTrue(council.canHostSession());
    government.propose(tax(faction, "Bob", TaxTarget.GUILDS, 8));
    assertEquals(2, council.getCurrentProposals("Bob"));
    assertFalse(council.canPropose("Bob"));
    SessionManager sessions = fixture.ui.plugin.getSessionManager();
    sessions.newSession(fixture.online.get("Alice"), faction);
    assertTrue(council.hasSession());
    assertNotNull(council.getSession());
    assertFalse(council.canHostSession());
    sessions.endSession(council);
    council.clearMembers();
    assertEquals(0, council.getCurrentSize());
    council.setCouncilSize(0);
    assertEquals(0, council.fillPercentage());
  }

  @Test
  void councilReorganizationTrimsSeatsAndDropsProposalsOnlyWhenTypeChanges() {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 2);
    Council council = faction.getGovernment().getCouncil();
    council.setCouncilSize(4);
    council.addMemberForce("Bob");
    council.addMemberForce("Cara");
    council.addMemberForce("Drew");
    faction.getGovernment().propose(tax(faction, "Bob", TaxTarget.CITIZENS, 9));
    council.reorganize();
    assertEquals(List.of("Bob", "Cara"), council.getMembers());
    assertTrue(council.hasProposals());
    council.setCouncilType(Rules.NO_COUNCIL);
    council.reorganize();
    assertEquals(Rules.APPOINTED_COUNCIL, council.getType());
    assertEquals(List.of("Bob", "Cara"), council.getMembers());
    assertFalse(council.hasProposals());
    council.addMemberForce("Departed");
    council.replace();
    assertFalse(council.isMember("Departed"));
  }

  @Test
  void electedCouncilFillsFromStoredWinnersThenEligibleFactionMembers() {
    Faction faction = councilFaction(Rules.ELECTED_COUNCIL, 3);
    Council council = faction.getGovernment().getCouncil();
    council.clearMembers();
    faction
        .getGovernment()
        .getElection()
        .restoreFromData(
            null, null, Map.of("COUNCIL", Map.of("Missing", 100, "Cara", 50, "Alice", 20)), null);
    council.replace();
    assertEquals(List.of("Cara", "Bob", "Drew"), council.getMembers());
    council.replace();
    assertEquals(3, council.getCurrentSize());
  }

  @Test
  void wealthCouncilSelectsEligibleWealthiestMembersAndRespectsRefusals() {
    try (MockedStatic<OfflineModifier> economy = mockStatic(OfflineModifier.class)) {
      economy
          .when(() -> OfflineModifier.balance(anyString(), any(Accounts.class)))
          .thenAnswer(
              call ->
                  Map.of("Alice", 1000.0, "Bob", 800.0, "Cara", 500.0, "Drew", 100.0)
                      .getOrDefault(call.getArgument(0), 0.0));
      Faction faction = councilFaction(Rules.WEALTH_BASED_COUNCIL, 2);
      Council council = faction.getGovernment().getCouncil();
      council.clearMembers();
      council.toggleRefuse("Bob");
      council.replace();
      assertEquals(List.of("Cara", "Drew"), council.getMembers());
      assertFalse(council.couldBeBigger());
      assertEquals(0, faction.getGovernment().getStabilityMalusFromCouncil());
    }
  }

  @Test
  void governmentPermissionChecksUseRealCouncilGuildAndSubjectRoles() {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 2);
    Government government = faction.getGovernment();
    government.getCouncil().addMember("Bob");
    Guild guild = fixture.guild(faction, "merchants", "Eve");
    guild.addMember("Finn");
    Faction vassal = fixture.saved("vassal", "Grace");
    vassal.addMember("Hank");
    fixture.subject(faction, vassal);
    fixture.saved("foreign", "Iris");
    assertFalse(government.isCouncilMember((String) null));
    assertFalse(government.isCouncilMember((org.bukkit.entity.Player) null));
    assertTrue(government.isCouncilMember("aLiCe"));
    assertTrue(government.isCouncilMember(fixture.player("Bob")));
    assertFalse(government.isCouncilMember("Cara"));
    assertTrue(government.canPropose(fixture.player("Alice")));
    assertTrue(government.canPropose(fixture.online.get("Bob")));
    assertFalse(government.canPropose(fixture.player("Cara")));
    for (String name : List.of("Alice", "Bob", "Cara", "Eve", "Grace"))
      assertTrue(government.canProposeOrStartMovement(fixture.player(name)), name);
    for (String name : List.of("Finn", "Hank", "Iris", "Stranger"))
      assertFalse(government.canProposeOrStartMovement(fixture.player(name)), name);
    assertFalse(government.canProposePolitical(fixture.online.get("Alice"), Action.NONE));
    assertFalse(government.canProposePolitical(fixture.online.get("Alice"), Action.DISSOLVE));
    assertFalse(government.canProposePolitical(fixture.online.get("Bob"), Action.DISSOLVE));
    assertFalse(government.canProposePolitical(fixture.online.get("Cara"), Action.SNAP_ELECTIONS));
    assertFalse(government.canProposePolitical(fixture.online.get("Hank"), Action.DISSOLVE));
    assertFalse(government.canProposePolitical(fixture.online.get("Iris"), Action.DISSOLVE));
    assertTrue(government.canProposePolitical(fixture.online.get("Cara"), Action.DISSOLVE));
    assertTrue(government.canProposePolitical(fixture.online.get("Eve"), Action.DISSOLVE));
    assertTrue(government.canProposePolitical(fixture.online.get("Finn"), Action.DISSOLVE));
    assertTrue(government.canProposePolitical(fixture.online.get("Grace"), Action.DISSOLVE));
    assertFalse(government.canProposePolitical(fixture.online.get("Finn"), Action.NATIONHOOD));
    assertTrue(government.canProposePolitical(fixture.online.get("Eve"), Action.NATIONHOOD));
    fixture.provinceData.put(7, new Province(7, "PLAINS", 1));
    faction.addProvince(7);
    faction.setCapital(7);
    guild.setCapital(7);
    assertFalse(government.canProposePolitical(fixture.online.get("Eve"), Action.NATIONHOOD));
    YamlConfiguration blocked = new YamlConfiguration();
    blocked.set("DISSOLVE.pools", List.of());
    PoliticalActionLoader.map.put(
        Action.DISSOLVE,
        new PoliticalAction("DISSOLVE", blocked.getConfigurationSection("DISSOLVE")));
    for (String name : List.of("Cara", "Eve", "Grace"))
      assertFalse(government.canProposePolitical(fixture.online.get(name), Action.DISSOLVE), name);
    assertFalse(government.canProposePolitical(fixture.online.get("Alice"), Action.WHITE_PEACE));
    Faction enemy = fixture.saved("enemy", "Jo");
    WarManager.get().add(new War(1, faction, enemy));
    assertTrue(government.canProposePolitical(fixture.online.get("Alice"), Action.WHITE_PEACE));
    assertTrue(government.canProposePolitical(fixture.online.get("Bob"), Action.SURRENDER));
    government.propose(tax(faction, "Bob", TaxTarget.CITIZENS, 10));
    government.propose(tax(faction, "Bob", TaxTarget.GUILDS, 10));
    assertFalse(government.canProposeOrStartMovement(fixture.online.get("Bob")));
    assertFalse(government.canPropose(fixture.online.get("Bob")));
  }

  @Test
  void administrativePowerSeedsWithinCapacityRegeneratesAndDecaysWithoutOvershoot() {
    fixture.lawGroup(
        "administration",
        Map.of(
            "upkeep",
            5,
            "effects.faction.modifiers",
            List.of("ADMIN_POWER_MULTIPLIER(50)", "ADMIN_POWER_GAIN_MULTIPLIER(100)")));
    Faction faction = fixture.saved("home", "Alice");
    faction
        .getOrCreateMainGuild()
        .getBranches()
        .get(0)
        .getModifiers()
        .put(GuildModifier.ADMIN_POWER, new BranchModifier(100, 0));
    faction
        .getOrCreateMainGuild()
        .getBranches()
        .get(0)
        .getModifiers()
        .put(GuildModifier.ADMIN_POWER_GAIN, new BranchModifier(2, 0));
    Government government = faction.getGovernment();
    assertNull(government.serialize().power);
    assertEquals(90, government.getStability());
    assertEquals(148.828125, government.getBaseMaxPower(), .0001);
    assertEquals(5.078125, government.getTotalUpkeep(), .0001);
    assertEquals(143.75, government.getMaxPower(), .0001);
    government.ping();
    assertEquals(10, government.getPower());
    assertEquals(5.953125, government.getPowerGain(), .0001);
    government.powerTick();
    assertEquals(15.95, government.getPower());
    government.setPower(143);
    government.powerTick();
    assertEquals(143.75, government.getPower());
    assertEquals(0, government.getPowerGain());
    government.powerTick();
    assertEquals(143.75, government.getPower());
    government.setPower(170);
    assertEquals(-10, government.getPowerGain());
    government.powerTick();
    assertEquals(160, government.getPower());
    government.spendPower(12.25);
    assertEquals(147.75, government.getPower());
    government.spendPower(1000);
    assertEquals(0, government.getPower());
    government.setPower(-12);
    assertEquals(0, government.getPower());
    government.setPower(12.345);
    assertEquals(12.35, government.getPower());
    government.addStabilityModifier(new StabilityModifier("Unrest", -20, 5));
    government.addStabilityModifier(new StabilityModifier("uNrEsT", -5, 10));
    assertEquals(-25, government.getByName("UNREST").getModifier());
    assertNull(government.getByName("Missing"));
    assertEquals(2, government.getStabilityModifiers().size());
    assertEquals(5, government.getByName("Unrest").getDecay());
    for (int i = 0; i < 5; i++) government.powerTick();
    assertEquals("Vacant Spymaster", government.getStabilityModifiers().getFirst().getName());
    assertEquals(1, government.getStabilityModifiers().size());
    faction.getEspionage().addUnrest(SpecialPosition.SPYMASTER, 10, 2, System.currentTimeMillis());
    assertEquals("Replaced Spymaster", government.getStabilityModifiers().getFirst().getName());
    assertTrue(government.getStabilityModifiers().getFirst().getModifier() < 0);
    assertTrue(government.serialize().stabilityModifiers.isEmpty());
  }

  @Test
  void favourAndRepressionAreMutuallyExclusiveForGuildsAndDirectSubjectsAndContributeToUpkeep() {
    Faction faction = fixture.saved("home", "Alice");
    Government government = faction.getGovernment();
    Guild merchants = fixture.guild(faction, "merchants", "Bob");
    Guild artisans = fixture.guild(faction, "artisans", "Cara");
    Faction vassal = fixture.saved("vassal", "Drew");
    Faction foreign = fixture.saved("foreign", "Eve");
    fixture.subject(faction, vassal);
    Guild subject = vassal.getOrCreateMainGuild();
    assertTrue(government.canAffectStability(merchants));
    assertTrue(government.canAffectStability(subject));
    assertFalse(government.canAffectStability(faction.getOrCreateMainGuild()));
    assertFalse(government.canAffectStability(foreign.getOrCreateMainGuild()));
    assertTrue(government.canFavour(merchants));
    assertTrue(government.canRepress(merchants));
    government.toggleFavour(merchants);
    assertTrue(merchants.isFavoured());
    assertFalse(government.canRepress(merchants));
    government.toggleRepress(merchants);
    assertFalse(merchants.isRepressed());
    government.toggleFavour(merchants);
    assertFalse(merchants.isFavoured());
    government.toggleRepress(merchants);
    assertTrue(merchants.isRepressed());
    assertFalse(government.canFavour(merchants));
    government.toggleFavour(merchants);
    assertFalse(merchants.isFavoured());
    government.toggleRepress(merchants);
    assertFalse(merchants.isRepressed());
    merchants.setFavoured(true);
    artisans.setRepressed(true);
    subject.setFavoured(true);
    assertEquals(List.of(merchants, subject), government.getFavoured());
    assertEquals(List.of(artisans), government.getRepressed());
    double upkeep =
        (merchants.getRepressFavourCost()
                + artisans.getRepressFavourCost()
                + subject.getRepressFavourCost())
            * StabilityDebuffs.upkeepFactor(government.getStability(), StabilityTuning.get());
    assertEquals(upkeep, government.getTotalUpkeep(), .0001);
    merchants.setRepressed(true);
    subject.setRepressed(true);
    government.validateFavoursAndRepressions();
    assertFalse(merchants.isFavoured());
    assertFalse(subject.isFavoured());
    assertTrue(merchants.isRepressed());
    assertTrue(subject.isRepressed());
    assertTrue(government.getRepressed().contains(subject));
    assertEquals(25, government.getBaseStability());
    assertEquals(government.stateReport().legitimacy, government.getLegitimacy());
    assertEquals(government.stateReport().illegitimate, government.isIllegitimate());
    assertEquals(government.stateReport().status.getTaxFactor(), government.getTaxEfficiency());
    assertEquals(
        faction.getModifier(FactionModifiers.DE_JURE).getAmount()
            + StabilityDebuffs.deJureBonus(government.getStability(), StabilityTuning.get()),
        government.deJureScore());
    assertTrue(
        government.getStabilityString().endsWith(Double.toString(government.getStability())));
  }

  @Test
  void movementRoundTripRetainsAllProposalKindsPoolsBackersAndFrozenState() {
    fixture.lawGroup("finance", Map.of("name", "Existing"));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    Guild guild = fixture.guild(faction, "merchants", "Drew");
    Faction subject = fixture.saved("subject", "Eve");
    fixture.subject(faction, subject);
    Faction backer = fixture.saved("foreign", "Finn");
    Government government = faction.getGovernment();
    Proposal law = new Proposal("Bob", government);
    law.setLawProposal(fixture.law("finance", "new", Map.of("name", "New")));
    Proposal tax = tax(faction, "Bob", TaxTarget.CITIZENS, 14);
    Proposal fee = new Proposal("Bob", government);
    fee.setFeeProposal(new FeeChange(FeeKind.TRANSFER_FEE, "tf:cart", 1.5));
    Proposal political = new Proposal("Bob", government);
    political.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));
    political.setTarget("Cara");
    government.startMovement("Bob", law);
    Movement movement = government.getMovementByLeader("bOB");
    assertNotNull(movement);
    assertTrue(government.hasMovements());
    assertSame(movement, government.getMovementById(movement.getId().toUpperCase()));
    assertSame(movement, government.getMovementByMember("Bob"));
    assertNull(government.getMovementByMember("Stranger"));
    assertNull(government.getMovementByLeader("Stranger"));
    assertNull(government.getMovementById("missing"));
    assertNull(government.getMovementByForeignBacker(backer));
    movement.getForeignBackers().add(backer);
    assertSame(movement, government.getMovementByForeignBacker(backer));
    for (Proposal proposal : List.of(tax, fee, political))
      movement.addCause(new Cause(movement, proposal, "Bob"));
    movement.getCauses().getFirst().getPool().addGuild(guild);
    movement.getCauses().getFirst().getPool().addFaction(subject);
    movement.getSupporters().addCitizen("Cara");
    movement.getSupporters().addGuild(guild);
    movement.getSupporters().addFaction(subject);
    movement.changeOrganization(7.25);
    movement.setFrozen(true);
    government.setPower(42.75);
    government.addStabilityModifier(new StabilityModifier("Victory", 8, .5));
    government.getCouncil().addMemberForce("Cara");
    government.propose(fee);
    when(fixture.ui.world.getName()).thenReturn("world");
    Location booth = new Location(fixture.ui.world, 2, 64, -3);
    government.addVotingBooth(booth);
    GovernmentData snapshot =
        JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(government.serialize()), GovernmentData.class);
    assertEquals(4, snapshot.movements.getFirst().causes.size());
    assertEquals(
        List.of("law", "tax", "fee", "political"),
        snapshot.movements.getFirst().causes.stream().map(cause -> cause.proposal.type).toList());
    assertEquals("tf:cart", snapshot.movements.getFirst().causes.get(2).proposal.feeVehicle);
    assertEquals("Cara", snapshot.movements.getFirst().causes.get(3).proposal.target);
    assertEquals(List.of("merchants"), snapshot.movements.getFirst().supporters.guilds);
    assertEquals(List.of("subject"), snapshot.movements.getFirst().supporters.factions);
    assertEquals(List.of("foreign"), snapshot.movements.getFirst().foreignBackers);
    Government restored = new Government(faction, snapshot);
    restored.loadMovements();
    assertEquals(42.75, restored.getPower());
    assertEquals(List.of("Cara"), restored.getCouncilMembers());
    assertEquals(8, restored.getByName("Victory").getModifier());
    assertTrue(restored.isVotingBooth(booth));
    assertTrue(restored.getCouncil().hasProposals());
    assertEquals(7.25, restored.getMovements().getFirst().getOrganization());
    assertTrue(restored.getMovements().getFirst().isFrozen());
    assertEquals(JsonUtil.GSON.toJson(snapshot), JsonUtil.GSON.toJson(restored.serialize()));
    restored.deserializeMovements(faction, snapshot.movements);
    assertEquals(1, restored.getMovements().size());
    restored.removeVotingBooth(booth);
    assertFalse(restored.isVotingBooth(booth));
    restored.endMovement(restored.getMovements().getFirst());
    assertFalse(restored.hasMovements());
  }

  @Test
  void movementDayTickSkipsFrozenCausesAndAdvancesOnlyActiveOrganization() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    faction.addMember("Cara");
    Government government = faction.getGovernment();
    government.startMovement("Bob", tax(faction, "Bob", TaxTarget.CITIZENS, 8));
    government.startMovement("Cara", tax(faction, "Cara", TaxTarget.GUILDS, 8));
    Movement active = government.getMovementByLeader("Bob");
    Movement frozen = government.getMovementByLeader("Cara");
    frozen.setFrozen(true);
    double gain = active.getOrganizationGain();
    assertTrue(gain > 0);
    government.applyDailyOrganizationGain();
    assertEquals(Math.min(gain, active.getMaxOrganization()), active.getOrganization());
    assertEquals(0, frozen.getOrganization());
    government.setLastElectionDate();
    government.tick();
    assertEquals("Never", government.getLastElectionString());
    assertTrue(government.getMovements().containsAll(List.of(active, frozen)));
    faction.forceRemoveMember("Bob");
    government.tick();
    assertFalse(government.getMovements().contains(active));
    assertTrue(government.getMovements().contains(frozen));
  }

  @Test
  void restoredGovernmentToleratesAbsentOptionalFieldsAndBadMovementRecordsIndependently() {
    Faction faction = fixture.saved("home", "Alice");
    GovernmentData data = new GovernmentData();
    data.stabilityModifiers = null;
    data.councilMembers = null;
    data.proposals = null;
    data.movements = null;
    data.votingBooths = null;
    data.electionCandidates = null;
    data.electionVotes = null;
    data.previousVotes = null;
    data.eligibleVoters = null;
    Government restored = new Government(faction, data);
    assertEquals("Never", restored.getLastElectionString());
    assertNull(restored.getElectionEndDate());
    assertEquals("N/A", restored.getTimeUntilElectionEnds());
    assertEquals("N/A", restored.getTimeUntilNextElection());
    assertFalse(restored.hasCouncil());
    restored.cancelAllElections();
    restored.cancelElections(net.tfminecraft.simplefactions.government.election.Candidate.LEADER);
    assertFalse(restored.hasElection());
    restored.ping();
    assertEquals(0, restored.getPower());
    MovementData valid = new MovementData();
    valid.id = "valid";
    List<MovementData> records = new ArrayList<>();
    records.add(null);
    records.add(valid);
    restored.deserializeMovements(faction, records);
    assertEquals(1, restored.getMovements().size());
    assertEquals("valid", restored.getMovements().getFirst().getId());
    assertNull(restored.serialize().movements.getFirst().leader);
  }

  @Test
  void mondayElectionCadenceWaitsFourWeeksAndExpiresTheSevenDayBallot() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    Government government = faction.getGovernment();
    var candidate = net.tfminecraft.simplefactions.government.election.Candidate.LEADER;
    assertFalse(government.shouldStartElection());
    government.getElection().addCandidate(candidate, "Bob");
    LocalDate monday = LocalDate.of(2026, 10, 5);
    try (MockedStatic<LocalDate> dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
      dates.when(LocalDate::now).thenReturn(monday.minusDays(1));
      assertFalse(government.shouldStartElection());
      dates.when(LocalDate::now).thenReturn(monday);
      assertTrue(government.shouldStartElection());
      government.ping();
      assertTrue(government.hasElection());
      assertFalse(government.shouldStartElection());
      assertNotEquals("Never", government.getLastElectionString());
      assertEquals(
          government.getLastElectionStartDate().plusDays(7), government.getElectionEndDate());
      assertNotEquals("N/A", government.getTimeUntilElectionEnds());
      LocalDate electionEnd = government.getElectionEndDate();
      dates.when(LocalDate::now).thenReturn(electionEnd);
      government.ping();
      assertFalse(government.hasElection());
      assertEquals("Bob", faction.getLeader());
      assertEquals("N/A", government.getTimeUntilElectionEnds());
      GovernmentData recent = new GovernmentData();
      recent.lastElectionDate =
          monday.minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
      Government waiting = new Government(faction, recent);
      waiting.getElection().addCandidate(candidate, "Alice");
      dates.when(LocalDate::now).thenReturn(monday);
      assertFalse(waiting.shouldStartElection());
      assertEquals(monday.plusDays(14), waiting.getNextElectionStartDate());
    }
  }

  @Test
  void cancellingConstitutionalElectionsClearsBallotsWithoutReplacingTheCurrentLeader() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    Government government = faction.getGovernment();
    var type = net.tfminecraft.simplefactions.government.election.Candidate.LEADER;
    government.getElection().addCandidate(type, "Bob");
    government.getElection().start();
    government.getElection().addVote(type, "Alice", "Bob");
    government.cancelElections(type);
    assertTrue(government.getElection().getCandidates(type).isEmpty());
    assertNull(government.getElection().getVote(type, "Alice"));
    government.cancelAllElections();
    assertFalse(government.hasElection());
    assertEquals("Alice", faction.getLeader());
    assertTrue(government.getTimeUntilNextElection().matches("[0-9]+d [0-9]+h|Today"));
    assertEquals(DayOfWeek.MONDAY, government.getNextElectionStartDate().getDayOfWeek());
  }

  @Test
  void missingMovementLeaderDoesNotBreakLookupsForLaterValidMovements() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    MovementData leaderless = new MovementData();
    leaderless.id = "old";
    Government government = faction.getGovernment();
    government.deserializeMovements(faction, List.of(leaderless));
    government.startMovement("Bob", tax(faction, "Bob", TaxTarget.CITIZENS, 12));
    Movement found = assertDoesNotThrow(() -> government.getMovementByLeader("Bob"));
    assertNotNull(found);
    assertEquals("Bob", found.getLeader());
    assertNull(government.getMovementByLeader("Missing"));
  }

  @Test
  void councilMemberOptOutSurvivesCleanupAndVacatesTheSeat() {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 2);
    Council council = faction.getGovernment().getCouncil();
    council.addMember("Bob");
    council.toggleRefuse("Bob");
    council.replace();
    assertFalse(council.isMember("Bob"));
    assertTrue(council.refuses("Bob"));
    assertFalse(council.canBeMember("Bob", false, false));
  }

  @Test
  void leaderReplacementUsesAnEligibleElectionWinnerAndOtherwiseTheRemainingRoster() {
    Faction faction = fixture.saved("home", "Alice");
    faction.addMember("Bob");
    Government government = faction.getGovernment();
    faction.setLeader("Departed");
    government
        .getElection()
        .restoreFromData(null, null, Map.of("LEADER", Map.of("Outsider", 100, "Bob", 50)), null);
    government.replace();
    assertEquals("Bob", faction.getLeader());
    faction.setLeader("GoneAgain");
    government
        .getElection()
        .restoreFromData(null, null, Map.of("LEADER", Map.of("Outsider", 100)), null);
    government.replace();
    assertEquals("Alice", faction.getLeader());
  }

  @Test
  void electionResultsRetainEligibleOldCouncilMembersWhenTheBallotLeavesSeatsOpen() {
    Faction faction = councilFaction(Rules.ELECTED_COUNCIL, 3);
    Council council = faction.getGovernment().getCouncil();
    council.addMemberForce("Bob");
    council.addMemberForce("Cara");
    faction
        .getGovernment()
        .getElection()
        .restoreFromData(null, null, Map.of("COUNCIL", Map.of("Stranger", 100, "Drew", 50)), null);
    faction.getGovernment().applyElectionResults();
    assertEquals(List.of("Drew", "Bob", "Cara"), council.getMembers());
    council.setCouncilSize(1);
    faction.getGovernment().applyElectionResults();
    assertEquals(List.of("Drew"), council.getMembers());
  }

  @Test
  void changingCouncilKindsClearsOldSeatsWhenRequiredAndPreservesElectedSeatsUntilTheVote()
      throws Exception {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 2);
    Council council = faction.getGovernment().getCouncil();
    java.lang.reflect.Field loaded =
        net.tfminecraft.simplefactions.managers.FactionManager.class.getDeclaredField("loaded");
    loaded.setAccessible(true);
    boolean previous = loaded.getBoolean(null);
    try (MockedStatic<OfflineModifier> economy = mockStatic(OfflineModifier.class)) {
      loaded.setBoolean(null, true);
      council.addMember("Bob");
      var wealth =
          fixture.law(
              "constitution",
              "wealth",
              Map.of(
                  "effects.faction.rules",
                  List.of("HAS_COUNCIL true", "WEALTH_BASED_COUNCIL true"),
                  "effects.faction.council-size",
                  1));
      economy
          .when(() -> OfflineModifier.balance(anyString(), any(Accounts.class)))
          .thenAnswer(call -> "Cara".equals(call.getArgument(0)) ? 1000.0 : 0.0);
      faction.applyLaw(wealth, faction.getLawHandler().getGroup("constitution"));
      council.reorganize();
      assertEquals(Rules.WEALTH_BASED_COUNCIL, council.getType());
      assertEquals(List.of("Cara"), council.getMembers());
      var elected =
          fixture.law(
              "constitution",
              "elected",
              Map.of(
                  "effects.faction.rules",
                  List.of("HAS_COUNCIL true", "ELECTED_COUNCIL true"),
                  "effects.faction.council-size",
                  1));
      faction.applyLaw(elected, faction.getLawHandler().getGroup("constitution"));
      council.reorganize();
      assertEquals(Rules.ELECTED_COUNCIL, council.getType());
      assertEquals(List.of("Cara"), council.getMembers());
      var none =
          fixture.law(
              "constitution",
              "none",
              Map.of("effects.faction.rules", List.of("HAS_COUNCIL false")));
      faction.applyLaw(none, faction.getLawHandler().getGroup("constitution"));
      council.reorganize();
      assertEquals(Rules.NO_COUNCIL, council.getType());
      assertTrue(council.getMembers().isEmpty());
    } finally {
      loaded.setBoolean(null, previous);
    }
  }

  @Test
  void guildAndVassalLeadersCanRemainSeatedAndDummyAccountsCountTowardQuorum() {
    Faction faction = councilFaction(Rules.APPOINTED_COUNCIL, 3);
    fixture.guild(faction, "merchants", "Eve");
    Faction vassal = fixture.saved("vassal", "Grace");
    fixture.subject(faction, vassal);
    Council council = faction.getGovernment().getCouncil();
    assertTrue(council.canRemainMember("Eve"));
    assertTrue(council.canRemainMember("Grace"));
    assertFalse(council.canRemainMember("Alice"));
    council.addMemberForce("dummy_offline");
    fixture.player("Alice");
    assertTrue(council.hasEnoughValidVoters());
  }

  @Test
  void movementRulesBlockAGuildRevoltWithinASingleProvinceHost() {
    Faction faction = fixture.saved("home", "Alice");
    fixture.guild(faction, "merchants", "Bob");
    fixture.provinceData.put(7, new Province(7, "PLAINS", 1));
    faction.addProvince(7);
    faction.getGovernment().startMovement("Bob", tax(faction, "Bob", TaxTarget.CITIZENS, 12));
    assertFalse(faction.getGovernment().hasMovements());
    assertTrue(
        faction.getGovernment().canBeProposed(tax(faction, "Alice", TaxTarget.CITIZENS, 12)));
  }

  @Test
  void votingBoothRestoreSkipsMalformedEntriesAndUnavailableWorlds() {
    Faction faction = fixture.saved("home", "Alice");
    when(fixture.ui.world.getName()).thenReturn("world");
    when(Bukkit.getWorld("removed")).thenReturn(null);
    GovernmentData data = new GovernmentData();
    data.votingBooths =
        new ArrayList<>(List.of("bad", "world,a,64,3", "removed,1,2,3", "world,-2,64,3"));
    data.votingBooths.add(null);
    Government government = new Government(faction, data);
    Location booth = new Location(fixture.ui.world, -2, 64, 3);
    assertEquals(List.of(booth), government.getVotingBooths());
    assertFalse(government.isVotingBooth(new Location(fixture.ui.world, 9, 64, 3)));
    assertNull(Government.formatVotingBooth((Location) null));
    assertNull(Government.formatVotingBooth(new Location(null, 1, 2, 3)));
    assertNull(Government.formatVotingBooth(" ", 1, 2, 3));
    assertNull(Government.parseVotingBoothParts(" "));
    assertNull(Government.parseVotingBoothParts(",1,2,3"));
    assertArrayEquals(
        new String[] {"world", "-2", "64", "3"}, Government.parseVotingBoothParts("world,-2,64,3"));
  }

  @Test
  void countdownReportsTodayAndExpiredBallotsAtExactCalendarBoundaries() {
    fixture.lawGroup(
        "democracy", Map.of("effects.faction.rules", List.of("LEADER_ELECTIONS true")));
    Faction faction = fixture.saved("home", "Alice");
    LocalDate monday = LocalDate.of(2026, 10, 5);
    Instant noon = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(43200);
    Instant afterBallot = noon.plusSeconds(8 * 86400);
    try (MockedStatic<LocalDate> dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS);
        MockedStatic<Instant> instants = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
      dates.when(LocalDate::now).thenReturn(monday);
      instants.when(Instant::now).thenReturn(noon);
      assertEquals("Today", faction.getGovernment().getTimeUntilNextElection());
      GovernmentData data = new GovernmentData();
      data.lastElectionDate = noon.toEpochMilli();
      Government active = new Government(faction, data);
      assertTrue(active.hasElection());
      assertEquals("6d 12h", active.getTimeUntilElectionEnds());
      instants.when(Instant::now).thenReturn(afterBallot);
      assertEquals("0d 0h", active.getTimeUntilElectionEnds());
      assertEquals("0d 0h", Government.formatTimeUntil(afterBallot, noon));
      assertEquals("0d 0h", Government.formatTimeUntil(null, noon));
    }
  }
}
