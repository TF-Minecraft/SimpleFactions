package net.tfminecraft.simplefactions.government;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.movement.Pool;
import net.tfminecraft.simplefactions.government.movement.UsurpCause;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.loaders.PoliticalActionLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarGoalMapper;
import net.tfminecraft.simplefactions.war.core.WarDeclareHelper;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.declare.WarGoalValidator;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/** A vassal takes its overlord's title through a movement, not a declared war. */
class UsurpMovementTest {
  private FactionDomainFixture fixture;
  private Map<Action, PoliticalAction> originalActions;
  private Faction duke;
  private Faction count;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    originalActions = PoliticalActionLoader.map;
    PoliticalActionLoader.map = new LinkedHashMap<>();
    for (Action action : Action.values())
      PoliticalActionLoader.map.put(action, new PoliticalAction(action));
    when(Bukkit.getPlayer(anyString())).thenAnswer(call -> fixture.online.get(call.getArgument(0)));
    duke = titled("duke", "Dana", "duke_title", "duchy");
    count = titled("count", "Carl", "count_title", "county");
    fixture.subject(duke, count);
  }

  @AfterEach
  void cleanup() {
    PoliticalActionLoader.map = originalActions;
    if (fixture != null) fixture.close();
  }

  private Faction titled(String id, String leader, String titleId, String tier) {
    fixture.title(titleId, tier);
    FactionData data = fixture.data(id, leader);
    data.titles.add(titleId);
    Faction faction = fixture.saved(data);
    faction.updateTier();
    return faction;
  }

  private Cause cause(String leader, Faction... members) {
    Cause cause = mock(Cause.class);
    when(cause.hasLeader()).thenReturn(leader != null);
    when(cause.getLeader()).thenReturn(leader);
    Pool pool = new Pool();
    for (Faction member : members) pool.addFaction(member);
    when(cause.getPool()).thenReturn(pool);
    return cause;
  }

  @Test
  void onlyADirectVassalOfEqualOrLowerRankCanUsurpATitledOverlord() {
    Faction baron = titled("baron", "Bea", "baron_title", "province");
    fixture.subject(count, baron);
    Faction stranger = titled("stranger", "Sam", "stranger_title", "county");

    assertTrue(UsurpCause.canUsurp(count, duke));
    assertFalse(UsurpCause.canUsurp(baron, duke), "a nested subject must go through its own liege");
    assertFalse(UsurpCause.canUsurp(stranger, duke));
    assertFalse(UsurpCause.canUsurp(duke, count));
    assertFalse(UsurpCause.canUsurp(null, duke));
    assertFalse(UsurpCause.canUsurp(count, null));
    Faction untitled = fixture.saved("untitled", "Uma");
    fixture.subject(untitled, stranger);
    assertFalse(UsurpCause.canUsurp(stranger, untitled), "an untitled overlord has nothing to take");
    Faction king = titled("king", "Kai", "king_title", "duchy");
    Faction prince = titled("prince", "Pia", "prince_title", "duchy");
    fixture.title("extra_duchy", "duchy");
    fixture.subject(prince, king);
    assertTrue(UsurpCause.canUsurp(king, prince), "equal rank may usurp");
  }

  @Test
  void theCauseLeadersFactionUsurpsElseTheFirstEligibleMember() {
    Faction other = titled("other", "Otto", "other_title", "county");
    fixture.subject(duke, other);
    Faction stranger = fixture.saved("stranger", "Sam");

    assertSame(count, UsurpCause.usurper(cause("Carl", other), duke));
    assertSame(other, UsurpCause.usurper(cause(null, stranger, other), duke));
    assertSame(other, UsurpCause.usurper(cause("Sam", other), duke));
    assertNull(UsurpCause.usurper(cause(null, stranger), duke));
    assertNull(UsurpCause.usurper(null, duke));
  }

  @Test
  void applyingTheCauseUsurpsTheOverlordAndAnEmptyCauseChangesNothing() {
    try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class, CALLS_REAL_METHODS)) {
      factions.when(() -> FactionManager.usurp(any(), any(), any())).thenReturn(null);
      Proposal proposal = new Proposal("Carl", duke.getGovernment());
      proposal.setPoliticalActionProposal(new PoliticalAction(Action.USURP));
      duke.applyPoliticalAction(cause("Carl", count), proposal);
      factions.verify(() -> FactionManager.usurp(null, count, duke));

      duke.applyPoliticalAction(cause(null), proposal);
      factions.verify(() -> FactionManager.usurp(any(), any(), any()), times(1));
    }
  }

  @Test
  void onlyAnEligibleVassalLeaderCanProposeAUsurp() {
    Government government = duke.getGovernment();
    count.addMember("Cody");
    Faction baron = titled("baron", "Bea", "baron_title", "province");
    fixture.subject(duke, baron);
    fixture.guild(duke, "merchants", "Eve");
    duke.addMember("Mia");
    Faction big = titled("big", "Bo", "big_title", "duchy");
    fixture.title("big_extra", "duchy");
    fixture.subject(duke, big);

    assertTrue(government.canProposePolitical(fixture.player("Carl"), Action.USURP));
    assertTrue(government.canProposePolitical(fixture.player("Bea"), Action.USURP));
    assertFalse(government.canProposePolitical(fixture.player("Cody"), Action.USURP), "vassal member");
    assertFalse(government.canProposePolitical(fixture.player("Eve"), Action.USURP), "guild leader");
    assertFalse(government.canProposePolitical(fixture.player("Mia"), Action.USURP), "citizen");
    assertFalse(government.canProposePolitical(fixture.player("Dana"), Action.USURP), "the ruler");
    duke.getTitles().clear();
    duke.updateTier();
    assertFalse(government.canProposePolitical(fixture.player("Carl"), Action.USURP), "nothing to take");
  }

  @Test
  void usurpIsVassalOnlyAndMapsToTheUsurpCivilWarGoal() {
    PoliticalAction usurp = new PoliticalAction(Action.USURP);
    assertTrue(usurp.allowFactions());
    assertFalse(usurp.allowGuilds());
    assertFalse(usurp.allowCitizens());
    assertTrue(new PoliticalAction(Action.INDEPENDENCE).allowGuilds());
    assertEquals(WarGoalType.USURP, CivilWarGoalMapper.fromAction(Action.USURP));
  }

  @Test
  void aPoliticalActionsFileWithoutUsurpStillOffersIt(@TempDir Path folder) throws Exception {
    File file = folder.resolve("political-actions.yml").toFile();
    Files.writeString(file.toPath(), "independence:\n  name: Independence\n  pools: [guilds]\n");
    new PoliticalActionLoader().load(file);
    assertFalse(PoliticalActionLoader.getByAction(Action.INDEPENDENCE).allowFactions());
    assertTrue(PoliticalActionLoader.getByAction(Action.USURP).allowFactions());
    assertFalse(PoliticalActionLoader.getByAction(Action.USURP).allowGuilds());
  }

  @Test
  void aVassalCanNoLongerDeclareAUsurpWarOnItsOverlord() {
    assertFalse(WarDeclareHelper.canDeclareUsurp(count, duke));
    var result = new WarGoalValidator().validate(WarDeclareRequest.of(count, duke, WarGoalType.USURP));
    assertFalse(result.isValid());
    assertEquals(WarGoalValidator.USURP_OVERLORD, result.getMessage());
    Faction stranger = titled("stranger", "Sam", "stranger_title", "county");
    assertTrue(WarDeclareHelper.canDeclareUsurp(stranger, duke));
  }
}
