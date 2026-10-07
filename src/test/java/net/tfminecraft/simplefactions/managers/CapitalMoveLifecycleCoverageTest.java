package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CapitalMoveLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Faction faction;
  private Player player;
  private Map<Object, Object> pending, original;
  private double oldCost;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    fixture = new FactionDomainFixture();
    fixture.provincesEnabled(true);
    var field = CapitalMovePrompt.class.getDeclaredField("pending");
    field.setAccessible(true);
    pending = (Map<Object, Object>) field.get(null);
    original = new LinkedHashMap<>(pending);
    pending.clear();
    oldCost = Cache.capitalMoveCost;
    Cache.capitalMoveCost = 20;
    for (int id : List.of(1, 2, 3)) fixture.provinceData.put(id, new Province(id, "PLAINS", 50));
    fixture.provinceData.get(1).addNeighbour(2);
    fixture.provinceData.get(2).addNeighbour(1);
    fixture.provinceData.get(2).addNeighbour(3);
    fixture.provinceData.get(3).addNeighbour(2);
    var data = fixture.data("realm", "Alice");
    data.provinces.addAll(List.of(1, 2, 3));
    data.capital = 1;
    faction = fixture.saved(data);
    faction.getOrCreateMainGuild();
    faction.getBank().setWealth(100.0);
    faction.getSettlementHandler().found("Old_City", 1, 0, 0);
    player = fixture.player("Alice");
    fixture.inventory.confirming = new HashMap<>();
  }

  @AfterEach
  void close() {
    try {
      pending.clear();
      pending.putAll(original);
      Cache.capitalMoveCost = oldCost;
    } finally {
      fixture.close();
    }
  }

  @Test
  void aFreeRenameOpensAQuotedConfirmationAndAppliesOnce() {
    CapitalMovePrompt.begin(player, faction, 1, "New_City", true);
    verify(fixture.inventory).confirmCapitalMoveView(player, faction, true, 0, 0);
    assertSame(faction, fixture.inventory.confirming.get(player));
    assertTrue(faction.getSettlementHandler().getByProvince(1).getName().contains("Old City"));
    CapitalMovePrompt.handleConfirm(player, true);
    CapitalMovePrompt.handleConfirm(player, true);
    assertTrue(faction.getSettlementHandler().getByProvince(1).getName().contains("New City"));
    assertEquals(1, faction.getCapitalMoves());
    assertEquals(100.0, faction.getBank().getWealth());
    assertFalse(fixture.inventory.confirming.containsKey(player));
    verify(player, times(1)).playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
  }

  @Test
  void paidRenameChargesExactlyTheDisplayedQuote() {
    faction.setCapitalMoves(1);
    CapitalMovePrompt.begin(player, faction, 1, "New_City", true);
    verify(fixture.inventory).confirmCapitalMoveView(player, faction, true, 20, 0);
    CapitalMovePrompt.handleConfirm(player, true);
    assertEquals(80.0, faction.getBank().getWealth());
    assertEquals(2, faction.getCapitalMoves());
  }

  @Test
  void cancelClosesTheMenuAndKeepsCapitalBankAndName() {
    CapitalMovePrompt.begin(player, faction, 1, "New_City", true);
    CapitalMovePrompt.handleConfirm(player, false);
    assertUnchanged();
    verify(player).closeInventory();
    verify(player).playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
  }

  @Test
  void insufficientBankBalanceDoesNotOpenConfirmation() {
    faction.setCapitalMoves(1);
    faction.getBank().setWealth(5.0);
    CapitalMovePrompt.begin(player, faction, 1, "New_City", true);
    assertTrue(pending.isEmpty());
    assertTrue(fixture.inventory.confirming.isEmpty());
    verify(fixture.inventory, never())
        .confirmCapitalMoveView(any(), any(), anyBoolean(), anyDouble(), anyInt());
    verify(player).playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
  }

  @Test
  void changedLeaderCannotApplyAnOpenCapitalConfirmation() {
    CapitalMovePrompt.begin(player, faction, 1, "New_City", true);
    faction.setLeader("Bob");
    CapitalMovePrompt.handleConfirm(player, true);
    assertUnchanged();
    verify(player).playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
  }

  @Test
  void failedDestinationValidationDoesNotSpendOrMove() {
    CapitalMovePrompt.begin(player, faction, 0, "Invalid", false);
    CapitalMovePrompt.handleConfirm(player, true);
    assertUnchanged();
  }

  @Test
  void capitalMovePublishesTheNewCityAndRevalidatesClaims() {
    CapitalMovePrompt.begin(player, faction, 2, "New_City", false);
    CapitalMovePrompt.handleConfirm(player, true);
    assertEquals(2, faction.getCapital());
    assertNotNull(faction.getSettlementHandler().getByProvince(2));
    assertNull(faction.getSettlementHandler().getByProvince(1));
    assertEquals(1, faction.getCapitalMoves());
    assertEquals(100.0, faction.getBank().getWealth());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void firstCapitalIsFreeAndOnlyAppliesValidDestinations(boolean valid) {
    faction.setCapital(-1, true, false);
    CapitalMovePrompt.applyFactionCapitalMove(player, faction, valid ? 2 : 0, "First_City");
    assertEquals(valid ? 2 : -1, faction.getCapital());
    assertEquals(0, faction.getCapitalMoves());
    assertEquals(100.0, faction.getBank().getWealth());
    verify(player)
        .playSound(player, valid ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 1f, 1f);
  }

  @Test
  void quittingClearsBothPendingRecordsAndLateConfirmationDoesNothing() {
    CapitalMovePrompt.begin(player, faction, 1, "New_City", true);
    CapitalMovePrompt listener = new CapitalMovePrompt();
    listener.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
    listener.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
    CapitalMovePrompt.handleConfirm(player, true);
    assertTrue(pending.isEmpty());
    assertTrue(fixture.inventory.confirming.isEmpty());
    assertUnchanged();
  }

  private void assertUnchanged() {
    assertEquals(1, faction.getCapital());
    assertEquals(100.0, faction.getBank().getWealth());
    assertTrue(faction.getSettlementHandler().getByProvince(1).getName().contains("Old City"));
    assertEquals(0, faction.getCapitalMoves());
  }
}
