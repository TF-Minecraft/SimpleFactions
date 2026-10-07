package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.enums.*;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.movement.PoliticalAction;
import net.tfminecraft.simplefactions.government.proposal.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.laws.*;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class LoreWriterCoverageTest {
  FactionDomainFixture fixture;
  MockedStatic<EconomicImpact> estimates;
  Faction faction;
  Guild guild;
  Faction subject;
  Player reader;
  ItemMeta meta;
  List<String> lore;

  @BeforeEach void setUp() {
    new LoreWriter();
    fixture = new FactionDomainFixture();
    fixture.lawGroup("government", Map.of());
    fixture.lawGroup("customs", Map.of());
    faction = fixture.saved("realm", "Alice");
    guild = fixture.guild(faction, "merchants", "Bob");
    subject = fixture.saved("islands", "Cara");
    fixture.subject(faction, subject);
    reader = fixture.player("Alice");
    meta = new ItemStack(Material.PAPER).getItemMeta();
    lore = new ArrayList<>(List.of("Existing description"));
    estimates = mockStatic(EconomicImpact.class);
  }

  @AfterEach void close() { estimates.close(); fixture.close(); }

  private Proposal proposal() { return new Proposal("Alice", faction.getGovernment()); }
  private void render(Proposal proposal) { LoreWriter.applyProposalLore(proposal,lore,reader,faction,meta); }
  private String text() { return ChatColor.stripColor(String.join("\n", lore)); }

  @ParameterizedTest @ValueSource(strings={"government","customs"})
  void lawDescriptionsCompareTheCurrentAndProposedLawAndRequestOnlyEconomicEstimates(String id) {
    Law law = fixture.law(id,"proposed",Map.of());
    Proposal proposal=proposal(); proposal.setLawProposal(law);
    render(proposal);
    assertTrue(text().contains("Group: "+id),text());
    assertTrue(text().contains("Current "+id+" -> proposed"),text());
    if(id.equals("government")) {
      LawGroup group=faction.getLawHandler().getGroup(id);
      estimates.verify(() -> EconomicImpact.applyEconomicChange(lore,reader,faction,group,law,false,meta,false));
    } else estimates.verifyNoInteractions();
  }

  @ParameterizedTest @EnumSource(TaxTarget.class)
  void everyTaxTargetShowsItsCurrentRateAndUsesTheCorrectPreview(TaxTarget target) {
    boolean specific=target==TaxTarget.GUILD_ID || target==TaxTarget.VASSAL_ID || target==TaxTarget.TARIFF_ID;
    String id=specific ? target==TaxTarget.GUILD_ID ? guild.getId() : subject.getId() : null;
    double previous=faction.getTaxRate(target,id,false);
    Proposal proposal=proposal(); proposal.setTaxProposal(new TaxLawChange(target,id,17.5));
    render(proposal);
    String name=specific ? target==TaxTarget.GUILD_ID ? guild.getName() : subject.getName() : target.getDisplayName();
    assertTrue(text().contains("Target: "+name),text());
    assertTrue(text().contains("Change: "+previous+"% -> 17.5%"),text());
    assertEquals(specific,text().contains("Base Rate:"));
    if(target==TaxTarget.TARIFFS || target==TaxTarget.TARIFF_ID)
      estimates.verify(() -> EconomicImpact.applyTariffImpact(lore,reader,faction,id,17.5,false,meta,false));
    else estimates.verify(() -> EconomicImpact.applyTaxImpact(lore,reader,faction,target,id,17.5,false,meta,false));
  }

  @ParameterizedTest @EnumSource(value=TaxTarget.class,names={"GUILD_ID","VASSAL_ID","TARIFF_ID"})
  void aSpecificRateIsShownAlongsideTheGeneralRate(TaxTarget target) {
    String id=target==TaxTarget.GUILD_ID ? guild.getId() : subject.getId();
    faction.getTaxHandler().setTaxRate(target,id,7.5);
    Proposal proposal=proposal(); proposal.setTaxProposal(new TaxLawChange(target,id,17.5));
    render(proposal);
    assertTrue(text().contains("Change: 7.5% -> 17.5%"),text());
    assertTrue(text().contains("Base Rate: "+faction.getTaxRate(target,null,false)),text());
  }

  @ParameterizedTest @EnumSource(value=TaxTarget.class,names={"GUILD_ID","VASSAL_ID","TARIFF_ID"})
  void deletedTaxTargetsLeaveReadableProposalLoreWithoutCalculatingAnInvalidPreview(TaxTarget target) {
    String id=target==TaxTarget.GUILD_ID ? guild.getId() : subject.getId();
    Proposal proposal=proposal(); proposal.setTaxProposal(new TaxLawChange(target,id,17.5));
    if(target==TaxTarget.GUILD_ID) faction.getGuildHandler().removeGuild(guild.getId());
    else FactionManager.factions.remove(subject);
    assertDoesNotThrow(() -> render(proposal));
    assertTrue(text().startsWith("Existing description\n"));
    assertTrue(text().contains(id),text());
    assertTrue(text().contains("no longer available"),text());
    estimates.verifyNoInteractions();
  }

  @ParameterizedTest @EnumSource(FeeKind.class)
  void vehicleFeeDescriptionsUseTheSharedUnitAwareFormatter(FeeKind kind) {
    FeeChange fee=new FeeChange(kind,"cart",1.5);
    Proposal proposal=proposal();proposal.setFeeProposal(fee);render(proposal);
    assertEquals(FeeProposalText.lines(faction,fee),lore.subList(1,lore.size()));
    assertTrue(text().contains("Vehicle: cart"));
    estimates.verifyNoInteractions();
  }

  @ParameterizedTest @EnumSource(Action.class)
  void politicalDescriptionsExplainTheRequestedAction(Action action) {
    Proposal proposal=proposal();proposal.setPoliticalActionProposal(new PoliticalAction(action));render(proposal);
    assertTrue(text().contains("Action: "+ChatColor.stripColor(net.tfminecraft.tlibs.objects.api.subapi.StringFormatter.formatHex(action.getDisplay()))),text());
    String detail=switch(action) {
      case CHANGE_LEADER -> "No target";
      case WHITE_PEACE -> "white peace offer";
      case SURRENDER -> "Surrender a chosen war";
      case DISSOLVE -> "Dissolves the faction";
      case INDEPENDENCE -> "granted independence";
      case NATIONHOOD -> "elevated to nationhood";
      case SNAP_ELECTIONS -> "voting lasts for a week";
      default -> "Action:";
    };
    assertTrue(text().contains(detail),text());estimates.verifyNoInteractions();
  }

  @Test void leadershipProposalNamesTheSelectedPlayerAndAnEmptyProposalAddsNothing() {
    Proposal proposal=proposal();render(proposal);assertEquals(List.of("Existing description"),lore);
    proposal.setPoliticalActionProposal(new PoliticalAction(Action.CHANGE_LEADER));proposal.setTarget("Bob");render(proposal);
    assertTrue(text().contains("Target: Bob"));assertFalse(text().contains("No target"));
  }

  @ParameterizedTest @EnumSource(Scope.class)
  void effectsDescribeRulesRangesRegimentsAndRegionalModifiersWithinTheirScope(Scope scope) {
    var regiment = fixture.regiment("guards",false,2,5);
    YamlConfiguration config=new YamlConfiguration();
    config.set("rules",List.of("CAN_FAVOUR true","CAN_REPRESS false"));
    config.set("brackets.guild_tax","2-25");
    config.set("regiments",List.of("guards 3"));
    config.set("modifiers",List.of("PRESTIGE_BONUS(12.5)"));
    config.set("foreign_territory",List.of("INSTALLATION_ACCESS(0.25)"));
    lore.clear(); LoreWriter.writeEffect(scope,new LawEffect(scope,config),lore);
    assertEquals(scope!=Scope.FACTION,text().contains(scope.getDisplay()+":"));
    assertTrue(text().contains("✔")); assertTrue(text().contains("✖"));
    assertTrue(text().contains("Guild Tax"));assertTrue(text().contains("[2.0-25.0]"),text());
    assertTrue(text().contains("Free "+regiment.getName()+" Regiments: 3"),text());
    assertTrue(text().contains("Prestige Bonus")&&text().contains("12.5%"),text());
    assertTrue(text().contains("Foreign Territory:")&&text().contains("+25%"),text());
  }

  @Test void anEmptyFactionEffectAddsNoRowsAndAnEmptySubjectEffectKeepsItsHeader() {
    lore.clear(); LawEffect empty=new LawEffect(Scope.FACTION,new YamlConfiguration());
    LoreWriter.writeEffect(Scope.FACTION,empty,lore);assertTrue(lore.isEmpty());
    LoreWriter.writeEffect(Scope.VASSALS,empty,lore);assertEquals("  All Vassals:",text());
  }
}
