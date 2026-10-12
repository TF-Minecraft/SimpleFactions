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

  @Test void personalWealthIncludesBothAccountsAndTheApplicableShareOfGuildAssets() {
    Faction realm=fixture.saved("realm","Alice");realm.addMember("Citizen");
    Guild guild=fixture.guild(realm,"merchants","Bob");guild.addMember("Dana");
    realm.getBank().deposit(100.0);guild.getBank().deposit(60.0);
    try(MockedStatic<OfflineModifier> accounts=mockStatic(OfflineModifier.class)) {
      accounts.when(() -> OfflineModifier.balance(anyString(),eq(Accounts.POUCH))).thenReturn(3.0);
      accounts.when(() -> OfflineModifier.balance(anyString(),eq(Accounts.BANK))).thenReturn(7.0);
      assertEquals(110,Wealth.wealth("Alice"));
      assertEquals(10,Wealth.wealth("Citizen"));
      assertEquals(40,Wealth.wealth("Bob"));
      assertEquals(40,Wealth.wealth("Dana"));
      assertEquals(10,Wealth.wealth("Visitor"));
    }
  }

  @Test void characterWealthReadsTheAccountPlayingTheCharacterAndUnknownCharactersHaveNone() {
    java.util.UUID account=java.util.UUID.randomUUID(), character=java.util.UUID.randomUUID();
    String member=net.tfminecraft.simplefactions.identity.Members.key(character);
    String stranger=net.tfminecraft.simplefactions.identity.Members.key(java.util.UUID.randomUUID());
    var names=net.tfminecraft.simplefactions.testsupport.NameKeyedMembers.INSTANCE;
    net.tfminecraft.simplefactions.identity.Members.setResolver(new net.tfminecraft.simplefactions.identity.Members.Resolver() {
      @Override public java.util.Optional<net.tfminecraft.tlibs.character.CharacterSession> session(org.bukkit.entity.Player p) { return names.session(p); }
      @Override public boolean isCurrent(net.tfminecraft.tlibs.character.CharacterSession s) { return true; }
      @Override public String key(org.bukkit.entity.Player p) { return names.key(p); }
      @Override public java.util.Optional<org.bukkit.entity.Player> online(String key) { return java.util.Optional.empty(); }
      @Override public java.util.Optional<net.tfminecraft.tlibs.character.CharacterInfo> info(String key) {
        return key.equals(member) ? java.util.Optional.of(new net.tfminecraft.tlibs.character.CharacterInfo(
            new net.tfminecraft.tlibs.character.CharacterRef(account,character),"Lady Alice",
            net.tfminecraft.tlibs.character.CharacterStatus.ALIVE,null,java.util.Set.of(),0,null)) : java.util.Optional.empty();
      }
    });
    try(MockedStatic<OfflineModifier> accounts=mockStatic(OfflineModifier.class)) {
      accounts.when(() -> OfflineModifier.balance(eq(account),eq(Accounts.POUCH))).thenReturn(3.0);
      accounts.when(() -> OfflineModifier.balance(eq(account),eq(Accounts.BANK))).thenReturn(7.0);
      assertEquals(10,Wealth.wealth(member));
      assertEquals(0,Wealth.wealth(stranger));
      accounts.verify(() -> OfflineModifier.balance(anyString(),any(Accounts.class)),never());
    }
  }

  @Test void wealthRankingIncludesSubjectsOnlyWhenRequestedAndSortsByTotalAssets() {
    Faction realm=fixture.saved("realm","Alice");realm.addMember("Citizen");
    Guild guild=fixture.guild(realm,"merchants","Bob");guild.addMember("Dana");
    Faction subject=fixture.saved("subject","Cara");fixture.subject(realm,subject);
    realm.getBank().deposit(100.0);guild.getBank().deposit(60.0);subject.getBank().deposit(200.0);
    Map<String,Double> pouch=Map.of("Alice",1.0,"Bob",10.0,"Dana",5.0,"Citizen",0.0,"Cara",2.0);
    try(MockedStatic<OfflineModifier> accounts=mockStatic(OfflineModifier.class)) {
      accounts.when(() -> OfflineModifier.balance(anyString(),eq(Accounts.POUCH)))
          .thenAnswer(call -> pouch.getOrDefault(call.getArgument(0),0.0));
      assertEquals(List.of("Alice","Bob","Dana","Citizen"),Wealth.topWealth(realm,false));
      assertEquals(List.of("Cara","Alice","Bob","Dana","Citizen"),Wealth.topWealth(realm,true));
    }
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
