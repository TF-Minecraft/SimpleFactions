package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.simplefactions.government.movement.cause.Cause;
import net.tfminecraft.simplefactions.government.proposal.FeeKind;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.holder.CampaignInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.CampaignRaidLaunchHolder;
import net.tfminecraft.simplefactions.managers.holder.DeclareWarHolder;
import net.tfminecraft.simplefactions.managers.holder.SFCombinedInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.holder.WarInventoryHolder;
import net.tfminecraft.simplefactions.managers.inventory.GovernmentView;
import net.tfminecraft.simplefactions.managers.inventory.GuildView;
import net.tfminecraft.simplefactions.managers.inventory.MovementView;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.objects.handler.VehicleFeeHandler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class InventoryManagerRegressionTest {
  @ParameterizedTest
  @EnumSource(SFGUI.class)
  void everyFactionMenuProtectsItsTopSlotsFromDrags(SFGUI type) {
    InventoryDragEvent event = drag(new SFInventoryHolder("owner", type), "Menu", Set.of(0, 54));

    new InventoryManager().dragInWarGui(event);

    verify(event).setCancelled(true);
  }

  @ParameterizedTest
  @EnumSource(SFGUI.class)
  void factionMenusAllowDragsConfinedToThePlayersInventory(SFGUI type) {
    InventoryDragEvent event = drag(new SFInventoryHolder("owner", type), "Menu", Set.of(54, 89));

    new InventoryManager().dragInWarGui(event);

    verify(event, never()).setCancelled(true);
  }

  @ParameterizedTest
  @MethodSource("otherMenuHolders")
  void otherPluginMenusProtectTopSlotsAndAllowPlayerOnlyDrags(InventoryHolder holder) {
    InventoryManager manager = new InventoryManager();
    InventoryDragEvent top = drag(holder, "Menu", Set.of(53, 54));
    InventoryDragEvent bottom = drag(holder, "Menu", Set.of(54, 89));

    manager.dragInWarGui(top);
    manager.dragInWarGui(bottom);

    verify(top).setCancelled(true);
    verify(bottom, never()).setCancelled(true);
  }

  @Test
  void holderlessConfirmMenusProtectTopSlotsAndAllowPlayerOnlyDrags() {
    InventoryManager manager = new InventoryManager();
    InventoryDragEvent top = drag(null, "§7Confirm Action", Set.of(0));
    InventoryDragEvent bottom = drag(null, "§7Confirm Action", Set.of(54));

    manager.dragInWarGui(top);
    manager.dragInWarGui(bottom);

    verify(top).setCancelled(true);
    verify(bottom, never()).setCancelled(true);
  }

  @Test
  void unrelatedInventoriesKeepNormalDragBehavior() {
    InventoryDragEvent event = drag(mock(InventoryHolder.class), "Chest", Set.of(0, 54));

    new InventoryManager().dragInWarGui(event);

    verify(event, never()).setCancelled(true);
  }

  @ParameterizedTest
  @MethodSource("invalidRates")
  void invalidRateTextPreservesTheCurrentRateAndPrompt(RateKind kind, String input) {
    RateFixture fixture = new RateFixture(kind);
    Object prompt = fixture.prompt();

    fixture.submit(input);

    assertEquals(RateFixture.INITIAL_RATE, fixture.rate());
    assertSame(
        prompt, fixture.prompt(), "Invalid input must leave the same prompt available for retry");
    verify(fixture.player).sendMessage(startsWith("§cError inputting the amount,"));
    verifyNoInteractions(fixture.manager.governmentView, fixture.manager.guildView);

    fixture.submit("20.126");

    assertEquals(20.13, fixture.rate());
    assertNull(fixture.prompt());
    fixture.verifyRedraw();
  }

  @ParameterizedTest
  @MethodSource("validRates")
  void validRatesRetainRoundingAndBoundaryBehavior(RateKind kind, String input, double expected) {
    RateFixture fixture = new RateFixture(kind);

    fixture.submit(input);

    assertEquals(expected, fixture.rate());
    assertNull(fixture.prompt());
    fixture.verifyRedraw();
  }

  @ParameterizedTest
  @EnumSource(RateKind.class)
  void cancellationKeepsTheRateAndReturnsToItsMenu(RateKind kind) {
    RateFixture fixture = new RateFixture(kind);

    fixture.submit("CaNcEl");

    assertEquals(RateFixture.INITIAL_RATE, fixture.rate());
    assertNull(fixture.prompt());
    fixture.verifyRedraw();
  }

  @Test
  void finiteTaxAboveOneHundredKeepsItsExistingCap() {
    RateFixture fixture = new RateFixture(RateKind.TAX);

    fixture.submit("150");

    assertEquals(100.0, fixture.rate());
    assertNull(fixture.prompt());
    fixture.verifyRedraw();
  }

  @ParameterizedTest
  @CsvSource({"0,0", "1,1", "99,1", "-2,1"})
  void backFromAStaleCausePageReturnsToTheMovement(int page, int remainingCauses) {
    NavigationFixture fixture =
        new NavigationFixture(page, remainingCauses == 0 ? List.of() : List.of(mock(Cause.class)));

    fixture.clickBack();

    verify(fixture.event, atLeastOnce()).setCancelled(true);
    verify(fixture.event, never()).setCancelled(false);
    verify(fixture.manager.movementView)
        .movementView(fixture.player, fixture.faction, fixture.movement, null);
    verify(fixture.manager.movementView, never())
        .causeView(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void backFromAValidCausePageKeepsItsExistingCauseNavigation() {
    Cause cause = mock(Cause.class);
    NavigationFixture fixture = new NavigationFixture(0, List.of(cause));
    when(fixture.manager.movementView.displayedCause(fixture.inventory, fixture.movement))
        .thenReturn(cause);

    fixture.clickBack();

    verify(fixture.manager.movementView)
        .causeView(fixture.player, fixture.faction, fixture.movement, cause, null);
    verify(fixture.manager.movementView, never())
        .movementView(fixture.player, fixture.faction, fixture.movement, null);
  }

  @Test
  void backFromTheMovementLevelTargetPickerKeepsItsExistingNavigation() {
    NavigationFixture fixture = new NavigationFixture(-1, List.of(mock(Cause.class)));

    fixture.clickBack();

    verify(fixture.manager.movementView)
        .movementView(fixture.player, fixture.faction, fixture.movement, null);
  }

  private static InventoryDragEvent drag(InventoryHolder holder, String title, Set<Integer> slots) {
    Inventory inventory = mock(Inventory.class);
    when(inventory.getHolder()).thenReturn(holder);
    when(inventory.getSize()).thenReturn(54);
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(inventory);
    when(view.getTitle()).thenReturn(title);
    InventoryDragEvent event = mock(InventoryDragEvent.class);
    when(event.getView()).thenReturn(view);
    when(event.getRawSlots()).thenReturn(slots);
    return event;
  }

  private static Stream<InventoryHolder> otherMenuHolders() {
    return Stream.of(
        new WarInventoryHolder(1, SFGUI.WAR_VIEW),
        new CampaignInventoryHolder(1, SFGUI.CAMPAIGN_VIEW),
        new CampaignRaidLaunchHolder(1, "installation"),
        new SFCombinedInventoryHolder(1, "faction", SFGUI.PARTICIPANT_VIEW),
        new DeclareWarHolder("attacker", "defender", SFGUI.WAR_DECLARE_GOAL));
  }

  private static Stream<Arguments> invalidRates() {
    return Stream.of(RateKind.values())
        .flatMap(
            kind ->
                Stream.of("NaN", "Infinity", "-Infinity", "1e309", "not-a-number")
                    .map(input -> Arguments.of(kind, input)));
  }

  private static Stream<Arguments> validRates() {
    return Stream.of(RateKind.values())
        .flatMap(
            kind ->
                Stream.of(
                    Arguments.of(kind, "0", 0.0),
                    Arguments.of(kind, "20.126", 20.13),
                    Arguments.of(kind, "100", 100.0)));
  }

  private enum RateKind {
    TAX,
    FEE,
    DIVIDEND
  }

  private static final class RateFixture {
    private static final double INITIAL_RATE = 12.5;
    private final InventoryManager manager = new InventoryManager();
    private final Player player = mock(Player.class);
    private final Faction faction = mock(Faction.class);
    private final Guild guild = mock(Guild.class);
    private final TaxHandler taxes = new TaxHandler(faction, INITIAL_RATE, 0, 0, 0, 0);
    private final VehicleFeeHandler fees = new VehicleFeeHandler(faction);
    private final AtomicReference<Double> dividend = new AtomicReference<>(INITIAL_RATE);
    private final RateKind kind;

    private RateFixture(RateKind kind) {
      this.kind = kind;
      manager.governmentView = mock(GovernmentView.class);
      manager.guildView = mock(GuildView.class);
      when(player.getName()).thenReturn("Leader");
      when(faction.isLeader("Leader")).thenReturn(true);
      when(faction.hasFactionRule(Rules.CITIZEN_TAX)).thenReturn(true);
      when(faction.hasFactionRule(Rules.REGISTRATION_FEE)).thenReturn(true);
      when(faction.getTaxHandler()).thenReturn(taxes);
      when(faction.getVehicleFeeHandler()).thenReturn(fees);
      Government government = mock(Government.class);
      when(government.getFaction()).thenReturn(faction);
      when(faction.getGovernment()).thenReturn(government);
      fees.applyBracket(FeeKind.REGISTRATION_FEE, new Bracket(0, 100));
      fees.setRate(FeeKind.REGISTRATION_FEE, null, INITIAL_RATE);
      when(guild.isLeader(player)).thenReturn(true);
      when(guild.getDividendPercent()).thenAnswer(call -> dividend.get());
      doAnswer(
              call -> {
                dividend.set(call.getArgument(0));
                return null;
              })
          .when(guild)
          .setDividendPercent(anyDouble());
      switch (kind) {
        case TAX -> manager.setChanging(faction, player, TaxTarget.CITIZENS, null);
        case FEE -> manager.setChangingFee(faction, player, FeeKind.REGISTRATION_FEE, null);
        case DIVIDEND -> manager.setChangingDividend(player, guild);
      }
      clearInvocations(player);
    }

    private void submit(String input) {
      AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
      when(event.getMessage()).thenReturn(input);
      switch (kind) {
        case TAX -> manager.taxChat(player, event);
        case FEE -> manager.feeChat(player, event);
        case DIVIDEND -> manager.dividendChat(player, event);
      }
    }

    private double rate() {
      return switch (kind) {
        case TAX -> taxes.getCitizenTax();
        case FEE -> fees.getRate(FeeKind.REGISTRATION_FEE);
        case DIVIDEND -> dividend.get();
      };
    }

    private Object prompt() {
      return switch (kind) {
        case TAX -> manager.taxChange.get(player);
        case FEE -> manager.feeChange.get(player);
        case DIVIDEND -> manager.dividendChange.get(player);
      };
    }

    private void verifyRedraw() {
      if (kind == RateKind.DIVIDEND) verify(manager.guildView).guildView(player, guild);
      else verify(manager.governmentView).governmentView(player, faction, null);
    }
  }

  private static final class NavigationFixture {
    private final InventoryManager manager = new InventoryManager();
    private final Player player = mock(Player.class);
    private final Faction faction = mock(Faction.class);
    private final Movement movement = mock(Movement.class);
    private final Inventory inventory = mock(Inventory.class);
    private final InventoryClickEvent event = mock(InventoryClickEvent.class);

    private NavigationFixture(int page, List<Cause> causes) {
      manager.movementView = mock(MovementView.class);
      when(movement.getFaction()).thenReturn(faction);
      when(movement.getCauses()).thenReturn(causes);
      when(inventory.getHolder())
          .thenReturn(new SFInventoryHolder("movement", SFGUI.TARGET_SELECT, page));
      when(inventory.getSize()).thenReturn(54);
      InventoryView view = mock(InventoryView.class);
      when(view.getTopInventory()).thenReturn(inventory);
      when(view.getTitle()).thenReturn("Select Target");
      ItemStack back = mock(ItemStack.class);
      when(back.getType()).thenReturn(Material.BARRIER);
      when(event.getView()).thenReturn(view);
      when(event.getWhoClicked()).thenReturn(player);
      when(event.getCurrentItem()).thenReturn(back);
      when(event.getRawSlot()).thenReturn(53);
    }

    private void clickBack() {
      try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
        factions.when(() -> FactionManager.getMovementById("movement")).thenReturn(movement);
        manager.clickButton(event);
      }
    }
  }
}
