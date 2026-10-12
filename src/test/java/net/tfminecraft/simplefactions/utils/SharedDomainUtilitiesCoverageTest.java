package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class SharedDomainUtilitiesCoverageTest {
  FactionDomainFixture fixture;
  @BeforeEach void setUp() { fixture=new FactionDomainFixture(); new Wealth();new Represents();new RandomRGB(); }
  @AfterEach void close() { fixture.close(); }

  private String character(String name) {
    return java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
  }

  private void fund(String key, long pouch, long bank) {
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.set("char:" + key + ":pouch", pouch);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.set("char:" + key + ":bank", bank);
  }

  @Test void personalWealthIncludesBothAccountsAndTheApplicableShareOfGuildAssets() {
    String alice=character("Alice"),citizen=character("Citizen"),bob=character("Bob"),dana=character("Dana"),visitor=character("Visitor");
    Faction realm=fixture.saved("realm",alice);realm.addMember(citizen);
    Guild guild=fixture.guild(realm,"merchants",bob);guild.addMember(dana);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.add(realm.getBank(),100);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.add(guild.getBank(),60);
    for (String key:List.of(alice,citizen,bob,dana,visitor)) fund(key,300,700);
    assertEquals(110,Wealth.wealth(alice));
    assertEquals(10,Wealth.wealth(citizen));
    assertEquals(40,Wealth.wealth(bob));
    assertEquals(40,Wealth.wealth(dana));
    assertEquals(10,Wealth.wealth(visitor));
    assertEquals(0,Wealth.wealth("quarantine:unknown"));
  }

  @Test void characterWealthReadsItsOwnLedgerWithoutAnOnlineSession() {
    String member=character("first"),other=character("other");
    fund(member,300,700);
    fund(other,100,200);
    assertEquals(10,Wealth.wealth(member));
    assertEquals(3,Wealth.wealth(other));
    assertEquals(0,Wealth.wealth(character("unfunded")));
  }

  @Test void wealthRankingIncludesSubjectsOnlyWhenRequestedAndSortsByTotalAssets() {
    String alice=character("Alice"),citizen=character("Citizen"),bob=character("Bob"),dana=character("Dana"),cara=character("Cara");
    Faction realm=fixture.saved("realm",alice);realm.addMember(citizen);
    Guild guild=fixture.guild(realm,"merchants",bob);guild.addMember(dana);
    Faction subject=fixture.saved("subject",cara);fixture.subject(realm,subject);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.add(realm.getBank(),100);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.add(guild.getBank(),60);
    net.tfminecraft.simplefactions.testsupport.TestTreasuries.add(subject.getBank(),200);
    fund(alice,100,0);fund(bob,1000,0);fund(dana,500,0);fund(cara,200,0);
    assertEquals(List.of(alice,bob,dana,citizen),Wealth.topWealth(realm,false));
    assertEquals(List.of(cara,alice,bob,dana,citizen),Wealth.topWealth(realm,true));
  }

  @Test void representationDistinguishesCapitalGuildSubjectsAndUnrelatedPlayers() {
    Faction realm=fixture.saved("realm","Alice");
    Guild merchants=fixture.guild(realm,"merchants","Bob");
    Faction subject=fixture.saved("subject","Cara");fixture.subject(realm,subject);
    Faction unrelated=fixture.saved("other","Dan");
    assertTrue(ChatColor.stripColor(Represents.represents(realm,"Alice")).contains("Capital"));
    assertTrue(ChatColor.stripColor(Represents.represents(realm,"Bob")).contains(merchants.getType().getName()));
    assertTrue(ChatColor.stripColor(Represents.represents(realm,"Cara")).contains("Vassal"));
    assertTrue(ChatColor.stripColor(Represents.represents(unrelated,"Cara")).contains("Capital"));
    assertEquals("",Represents.represents(realm,"Visitor"));
  }

  @Test void availabilityReservesEveryFactionAndGuildColor() {
    Faction realm=fixture.saved("realm","Alice");realm.setRGB("21,42,63");
    Guild guild=fixture.guild(realm,"merchants","Bob");guild.setRGB("84,105,126");
    assertFalse(RandomRGB.isFree("21,42,63"));
    assertFalse(RandomRGB.isFree("84,105,126"));
    assertTrue(RandomRGB.isFree("0,0,0"));
  }

  @Test void randomFactionColorsRetryCollisionsAndRemainValidRgbValues() {
    Faction occupied=mock(Faction.class);
    try(MockedStatic<FactionManager> lookup=mockStatic(FactionManager.class)) {
      lookup.when(() -> FactionManager.getByRGB(anyString())).thenReturn(occupied,null);
      assertColor(RandomRGB.random());
      lookup.verify(() -> FactionManager.getByRGB(anyString()),times(2));
    }
  }

  @ParameterizedTest @ValueSource(strings={"0,0,0","255,255,255","128,128,128","255,0,0","0,255,0","0,0,255","250,180,220","20,10,30"})
  void relatedColorsStayWithinRgbBoundsAcrossDarkLightGrayAndChromaticInputs(String original) {
    for(int sample=0;sample<128;sample++) {
      String actual=RandomRGB.similarButDistinct(original);assertColor(actual);
      int[] before=components(original),after=components(actual);
      int beforeLight=java.util.Arrays.stream(before).min().orElseThrow()+java.util.Arrays.stream(before).max().orElseThrow();
      int afterLight=java.util.Arrays.stream(after).min().orElseThrow()+java.util.Arrays.stream(after).max().orElseThrow();
      assertTrue(Math.abs(beforeLight-afterLight)<=28,"Related color lightness drift: "+original+" -> "+actual);
    }
  }

  private static int[] components(String value) { return java.util.Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).toArray(); }
  private static void assertColor(String value) {
    int[] rgb=components(value);assertEquals(3,rgb.length);
    for(int component:rgb) assertTrue(component>=0&&component<=255,value);
  }
}
