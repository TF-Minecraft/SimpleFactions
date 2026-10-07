package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.enums.Stance;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.RelocationPrompt;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.rest.RestServer;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.utils.DisplayNameGate;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class StaleGovernmentEconomyReviewRegressionTest {
  private FactionDomainFixture domain;
  private PersistenceFilesFixture files;
  private MockedStatic<TLibs> itemProvider;
  private MockedStatic<RestServer> rest;
  private final Map<Map<Object, Object>, Map<Object, Object>> globals = new IdentityHashMap<>();
  private Faction home;
  private Faction other;
  private Guild guild;
  private Player ruler;
  private Player alice;
  private GovernmentView government;
  private GuildView guildView;

  @BeforeEach
  void setUp() throws Exception {
    files = new PersistenceFilesFixture();
    domain = new FactionDomainFixture();
    for (var entry :
        List.of(
            Map.entry(RelocationPrompt.class, "pending"),
            Map.entry(DisplayNameGate.class, "pending"),
            Map.entry(RequestManager.class, "requests"))) {
      Field field = entry.getKey().getDeclaredField(entry.getValue());
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<Object, Object> map = (Map<Object, Object>) field.get(null);
      globals.put(map, new LinkedHashMap<>(map));
      map.clear();
    }
    domain.provincesEnabled(true);
    domain.lawGroup(
        "government",
        Map.of(
            "effects.faction.rules",
            List.of("CAN_HAVE_VASSALS true", "CAN_FAVOUR true", "CAN_REPRESS true")));
    var data = domain.data("home", "Ruler");
    data.provinces.addAll(List.of(1, 2));
    home = domain.saved(data);
    other = domain.saved("other", "Bob");
    guild = domain.guild(home, "traders", "Alice");
    for (int id : List.of(1, 2)) domain.provinceData.put(id, new Province(id, "PLAINS", 50));
    guild.setCapital(1);
    guild.getBank().setWealth(500.0);
    home.getSettlementHandler().found("Original", 1, 0, 0);
    home.getGovernment().setPower(50);
    ruler = domain.player("Ruler");
    alice = domain.player("Alice");
    ItemAPI api = mock(ItemAPI.class);
    ItemCreator items = mock(ItemCreator.class);
    when(api.getCreator()).thenReturn(items);
    when(items.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    when(items.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
    itemProvider = mockStatic(TLibs.class);
    itemProvider.when(TLibs::getItemAPI).thenReturn(api);
    rest = mockStatic(RestServer.class);
    rest.when(() -> RestServer.getProvince(alice)).thenReturn(2);
    when(domain.map.getRelocationTarget(alice)).thenReturn(home);
    when(domain.ui.scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
        .thenReturn(mock(BukkitTask.class));
    government = new GovernmentView(domain.inventory);
    guildView = new GuildView(domain.inventory);
    guildView.setProvinceManager(domain.provinces);
    domain.ui.tasks.clear();
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      globals.forEach(
          (map, original) -> {
            map.clear();
            map.putAll(original);
          });
    } finally {
      try {
        if (rest != null) rest.close();
        if (itemProvider != null) itemProvider.close();
      } finally {
        try {
          if (domain != null) domain.close();
        } finally {
          if (files != null) files.close();
        }
      }
    }
  }

  @Test
  void removingABankWhileTheCityNamePromptIsOpenPreservesTheCapitalAndSettlements() {
    var oldCity = home.getSettlementHandler().getByProvince(1);
    assertTrue(RelocationPrompt.begin(alice, guild, home, 2, false, 100));
    Bank account = guild.getBank();
    guild.setBank(null);
    var answer = new AsyncPlayerChatEvent(true, alice, "Destination", Set.of());
    new RelocationPrompt().onPlayerChat(answer);
    assertTrue(answer.isCancelled());
    assertAll(
        () -> assertDoesNotThrow(domain.ui::runTasks),
        () -> assertEquals(1, guild.getCapital()),
        () -> assertSame(oldCity, home.getSettlementHandler().getByProvince(1)),
        () -> assertNull(home.getSettlementHandler().getByProvince(2)),
        () -> assertEquals(500.0, account.getWealth()));
  }

  @Test
  void relocationFromAnOpenGuildMenuRejectsAMissingBank() {
    Inventory menu = guildMenu();
    menu.setItem(34, guildView.creator.createRelocateItem(alice, home, guild));
    Bank account = guild.getBank();
    guild.setBank(null);
    assertAll(
        () -> assertDoesNotThrow(() -> guildView.click(domain.ui.click(alice, 34), menu, alice)),
        () -> assertEquals(1, guild.getCapital()),
        () -> assertNull(home.getSettlementHandler().getByProvince(2)),
        () -> assertEquals(500.0, account.getWealth()),
        () -> assertFalse(RequestManager.hasRequest(ruler)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void branchActionsFromAnOpenMenuRejectAMissingBankBeforeChangingTheLevel(boolean upgrade) {
    Branch branch = guild.getBranch("commerce");
    assertNotNull(branch);
    branch.levelUp();
    Inventory menu = guildMenu();
    int slot = upgrade ? 20 : 38;
    menu.setItem(
        slot,
        upgrade
            ? guildView.creator.createBranchUpgradeItem(alice, guild, branch)
            : guildView.creator.createBranchDowngradeItem(alice, guild, branch));
    Bank account = guild.getBank();
    guild.setBank(null);
    assertAll(
        () -> assertDoesNotThrow(() -> guildView.click(domain.ui.click(alice, slot), menu, alice)),
        () ->
            assertEquals(
                1,
                branch.getLevel(),
                "An absent account must not lose an already-paid branch level"),
        () -> assertEquals(500.0, account.getWealth()));
  }

  private Inventory guildMenu() {
    Inventory menu =
        Bukkit.createInventory(
            new SFInventoryHolder(guild.getId(), SFGUI.GUILD_VIEW), 54, "§7Guild View");
    alice.openInventory(menu);
    return menu;
  }

  @Test
  void aStanceButtonCannotChangeAGuildThatHasRelocatedOutOfTheDisplayedFaction() {
    guild.setStance(Stance.NEUTRAL);
    government.governmentView(alice, home, null);
    Inventory menu = alice.getOpenInventory().getTopInventory();
    assertNotNull(menu.getItem(28));
    guild.relocate(other, -1);
    assertFalse(home.getGovernment().canAffectStability(guild));
    double power = home.getGovernment().getPower();
    assertAll(
        () -> assertDoesNotThrow(() -> government.click(domain.ui.click(alice, 28), menu, alice)),
        () -> assertEquals(Stance.NEUTRAL, guild.getStance(other)),
        () -> assertSame(other, guild.getFaction()),
        () -> assertEquals(power, home.getGovernment().getPower()));
  }

  @Test
  void theCurrentGuildLeaderCanStillUseTheDisplayedStanceButton() {
    guild.setStance(Stance.NEUTRAL);
    government.governmentView(alice, home, null);
    Inventory menu = alice.getOpenInventory().getTopInventory();
    assertNotNull(menu.getItem(28));
    government.click(domain.ui.click(alice, 28), menu, alice);
    assertEquals(Stance.SUPPORT, guild.getStance(home));
  }

  @ParameterizedTest
  @CsvSource({"true,true", "true,false", "false,true", "false,false"})
  void favourAndRepressCannotApplyToAGuildOrVassalThatLeftTheDisplayedRealm(
      boolean guildTarget, boolean favour) {
    Faction subject = null;
    Guild selected = guild;
    if (!guildTarget) {
      subject = domain.saved("subject", "VassalLeader");
      domain.subject(home, subject);
      selected = subject.getOrCreateMainGuild();
    }
    government.favourRepressSelectView(ruler, home, favour, guildTarget, null);
    Inventory menu = ruler.getOpenInventory().getTopInventory();
    assertNotNull(menu.getItem(0));
    if (guildTarget) guild.relocate(other, -1);
    else {
      RelationManager.endVassalage(home, subject, false);
      domain.subject(other, subject);
      assertFalse(home.getSubjects().contains(subject));
    }
    Guild changed = selected;
    double power = home.getGovernment().getPower();
    assertAll(
        () -> assertDoesNotThrow(() -> government.click(domain.ui.click(ruler, 0), menu, ruler)),
        () -> assertFalse(changed.isFavoured()),
        () -> assertFalse(changed.isRepressed()),
        () -> assertEquals(power, home.getGovernment().getPower()));
  }

  @ParameterizedTest
  @CsvSource({"true,true", "true,false", "false,true", "false,false"})
  void currentGuildsAndDirectVassalsStillAcceptTheLeadersFavourAndRepressChoices(
      boolean guildTarget, boolean favour) {
    Guild selected = guild;
    if (!guildTarget) {
      Faction subject = domain.saved("subject", "VassalLeader");
      domain.subject(home, subject);
      selected = subject.getOrCreateMainGuild();
    }
    government.favourRepressSelectView(ruler, home, favour, guildTarget, null);
    Inventory menu = ruler.getOpenInventory().getTopInventory();
    assertNotNull(menu.getItem(0));
    government.click(domain.ui.click(ruler, 0), menu, ruler);
    assertEquals(favour, selected.isFavoured());
    assertEquals(!favour, selected.isRepressed());
  }
}
