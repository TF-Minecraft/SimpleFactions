package net.tfminecraft.simplefactions.settlement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.SettlementData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Persisted settlements and public capital/transfer flows with real faction state. */
class SettlementLifecycleCoverageTest {
  private FactionDomainFixture fixture;
  private Faction home;
  private SettlementHandler settlements;
  private Player leader;

  @BeforeEach
  void setup() {
    fixture = new FactionDomainFixture();
    home = faction("home", "Leader", 10, 11, 12);
    settlements = home.getSettlementHandler();
    leader = fixture.player("Leader");
    when(leader.getLocation()).thenReturn(new Location(fixture.ui.world, 27.75, 64, -8.25));
  }

  @AfterEach
  void close() {
    fixture.close();
  }

  @Test
  void transferredCityDoesNotOverwriteADestinationCityWithTheSameLocalId() {
    Faction destination = faction("destination", "Recipient", 20);
    Settlement incoming = settlements.found("Harbor", 10, 15, 16).getSettlement();
    Settlement existing =
        destination.getSettlementHandler().found("Harbor", 20, 31, 32).getSettlement();
    home.setCapital(10, true, false);
    destination.setCapital(20, true, false);
    Settlement transferred = settlements.detachOnProvince(10);
    destination.getSettlementHandler().acceptTransferred(transferred);

    assertSame(incoming, transferred);
    assertTrue(settlements.getAll().isEmpty());
    assertEquals(2, destination.getSettlementHandler().getAll().size());
    assertSame(existing, destination.getSettlementHandler().getByProvince(20));
    assertSame(existing, destination.getSettlementHandler().getById(existing.getId()));
    Settlement received = destination.getSettlementHandler().getByProvince(10);
    assertNotNull(received);
    assertNotEquals(existing.getId(), received.getId());
    assertEquals(incoming.getName(), received.getName());
    assertEquals(15, received.getCenterX());
    assertEquals(16, received.getCenterZ());
    assertEquals(20, destination.getCapital());
  }

  @Test
  void nullPersistedSettlementDoesNotPreventLaterValidCitiesLoading() {
    SettlementData valid = new Settlement("valid", "Valid", 10, 3, 4).toData();
    settlements.load(Arrays.asList(null, new SettlementData(), valid));
    assertEquals(1, settlements.getAll().size());
    assertEquals("Valid", settlements.getByProvince(10).getName());
    assertEquals("valid", settlements.serialize().getFirst().id);
  }

  @Test
  void nullLegacyProvinceDoesNotPreventTheCenterBeingRestored() {
    SettlementData data = new Settlement("legacy", "Legacy", 10, 3, 4).toData();
    data.provinces = new ArrayList<>(Arrays.asList(11, null, 12L));
    settlements.load(List.of(data));
    Settlement restored = settlements.getByProvince(10);
    assertNotNull(restored);
    assertEquals(java.util.Set.of(10), restored.getProvinces());
    assertNull(settlements.getByProvince(11));
    assertNull(settlements.getByProvince(12));
  }

  @Test
  void persistedCitiesRoundTripCoordinatesAndLegacyProvinceListsBeforeNormalization() {
    SettlementData data = new Settlement("capital", "Old Capital", 10, 123, -456).toData();
    data.provinces = new ArrayList<>(List.of(11L, 12.0));
    Settlement legacy = new Settlement(data);
    assertEquals(Set.of(10, 11, 12), legacy.getProvinces());
    assertTrue(legacy.isCenter(10));
    assertFalse(legacy.isCenter(11));
    legacy.removeProvince(11);
    assertFalse(legacy.contains(11));
    legacy.addProvince(10);
    assertThrows(IllegalArgumentException.class, () -> legacy.addProvince(11));
    legacy.setName("New Capital");
    SettlementData saved = legacy.toData();
    assertEquals("capital", saved.id);
    assertEquals("New Capital", saved.name);
    assertEquals(123, saved.centerX);
    assertEquals(-456, saved.centerZ);
    assertEquals(Set.of(10, 12), Set.copyOf(saved.provinces));
    legacy.normalizeToCenterOnly();
    assertEquals(Set.of(10), legacy.getProvinces());
  }

  @Test
  void legacyCoordinatesAndProvinceListDefaultToTheRequiredCenter() {
    SettlementData data = new SettlementData();
    data.id = "legacy";
    data.name = "Legacy";
    data.centerProvince = 10;
    data.provinces = null;
    Settlement restored = new Settlement(data);
    assertEquals(0, restored.getCenterX());
    assertEquals(0, restored.getCenterZ());
    assertEquals(Set.of(10), restored.getProvinces());
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "name", "center"})
  void incompletePersistentCitiesAreRejectedWithoutReplacingValidLoadedCities(String missing) {
    SettlementData invalid = new Settlement("invalid", "Invalid", 10, 0, 0).toData();
    if (missing.equals("id")) invalid.id = null;
    if (missing.equals("name")) invalid.name = null;
    if (missing.equals("center")) invalid.centerProvince = null;
    assertThrows(IllegalArgumentException.class, () -> new Settlement(invalid));
    settlements.found("Obsolete", 12, 0, 0);
    SettlementData valid = new Settlement("valid", "Valid", 11, 1, 2).toData();
    settlements.load(List.of(invalid, valid));
    assertNull(settlements.getByProvince(12));
    assertEquals("valid", settlements.getByProvince(11).getId());
    assertEquals(1, settlements.serialize().size());
    settlements.load(null);
    assertTrue(settlements.getAll().isEmpty());
    assertTrue(settlements.serialize().isEmpty());
    assertNull(settlements.getById(null));
  }

  @Test
  void factionCapitalDryRunPreservesStateAndApplyUsesThePlayersBlockCoordinates() {
    clearInvocations(fixture.map);
    var preview = settlements.validateFactionCapital(leader, 10, "River City");
    assertTrue(preview.isSuccess());
    assertTrue(preview.getMessage().contains("Ready"));
    assertNull(preview.getSettlement());
    assertTrue(settlements.requiresFoundingName(10));
    assertTrue(settlements.getAll().isEmpty());
    verify(fixture.map, never()).enqueue(anyString(), anyString());

    var applied = settlements.resolveFactionCapital(leader, 10, "River City");
    assertTrue(applied.isSuccess());
    Settlement city = applied.getSettlement();
    assertSame(city, settlements.getByProvince(10));
    assertEquals(27, city.getCenterX());
    assertEquals(-9, city.getCenterZ());
    assertFalse(settlements.requiresFoundingName(10));
    assertEquals(-1, home.getCapital(), "The caller applies the validated capital separately");
    verify(fixture.map).enqueue("nation", home.getRGB());
    var same = settlements.resolveFactionCapital(leader, 10, null);
    assertTrue(same.isSuccess());
    assertSame(city, same.getSettlement());
    assertEquals(1, settlements.getAll().size());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void aFirstFactionCityNeedsANameForBothPreviewAndApplication(String name) {
    assertFalse(settlements.validateFactionCapital(leader, 10, name).isSuccess());
    assertFalse(settlements.applyFactionCapital(leader, 10, name).isSuccess());
    assertTrue(settlements.getAll().isEmpty());
    assertEquals(-1, home.getCapital());
  }

  @Test
  void unownedCapitalRequestsAndBaseGuildRequestsLeaveStateUnchanged() {
    Guild guild = fixture.guild(home, "craft", "Smith");
    assertFalse(settlements.validateFactionCapital(leader, 99, "Faraway").isSuccess());
    assertFalse(settlements.applyFactionCapital(leader, 99, "Faraway").isSuccess());
    assertFalse(settlements.resolveGuildCapital(leader, guild, 99, "Faraway").isSuccess());
    assertFalse(settlements.onGuildRelocateTo(leader, guild, 99, "Faraway").isSuccess());
    assertFalse(
        settlements
            .resolveGuildCapital(leader, home.getOrCreateMainGuild(), 10, "Town")
            .isSuccess());
    assertTrue(settlements.getAll().isEmpty());
    assertEquals(-1, guild.getCapital());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "water"})
  void invalidProvinceDefinitionsCannotBecomeCapitals(String state) {
    if (state.equals("missing")) fixture.provinceData.remove(11);
    else fixture.provinceData.put(11, new Province(11, "SEA", 0));
    var result = settlements.applyFactionCapital(leader, 11, "Town");
    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains(state.equals("missing") ? "no province" : "water"));
    assertTrue(settlements.getAll().isEmpty());
  }

  @Test
  void guildCapitalCannotMoveToADifferentCityWithoutRelocation() {
    Settlement original = settlements.found("Original", 10, 0, 0).getSettlement();
    Settlement other = settlements.found("Other", 11, 5, 6).getSettlement();
    Guild guild = fixture.guild(home, "craft", "Smith");
    guild.setCapital(10, false);
    assertFalse(settlements.resolveGuildCapital(leader, guild, 11, null).isSuccess());
    assertFalse(settlements.resolveGuildCapital(leader, guild, 12, "Third").isSuccess());
    assertSame(original, settlements.resolveGuildCapital(leader, guild, 10, null).getSettlement());
    assertSame(other, settlements.getByProvince(11));
    assertEquals(10, guild.getCapital());
    var relocated = settlements.onGuildRelocateTo(leader, guild, 12, "Third");
    assertTrue(relocated.isSuccess());
    assertSame(relocated.getSettlement(), settlements.getByProvince(12));
    assertEquals(10, guild.getCapital());
  }

  @Test
  void invalidAndCollidingNamesNeverAllocateOrRenameCities() {
    Settlement city = settlements.found("Original", 10, 0, 0).getSettlement();
    String originalName = city.getName();
    assertFalse(settlements.validateFactionCapital(leader, 11, "!!!").isSuccess());
    assertFalse(settlements.validateFactionCapital(leader, 11, "Original").isSuccess());
    assertFalse(settlements.found("!!!", 11, 0, 0).isSuccess());
    assertFalse(settlements.found("Original", 11, 0, 0).isSuccess());
    assertFalse(settlements.found("Other", 10, 0, 0).isSuccess());
    assertFalse(settlements.rename(10, "!!!", false).isSuccess());
    assertSame(city, settlements.getByProvince(10));
    assertEquals(originalName, city.getName());
    assertEquals(1, settlements.getAll().size());
  }

  @Test
  void validationRemovesStalePersistedCitiesAndClearsOnlyTheirCapitalReferences() {
    Faction foreign = faction("foreign", "Foreign", 20);
    Guild visitor = fixture.guild(foreign, "visitors", "Guest");
    visitor.setCapital(99, false);
    home.setCapital(99, true, false);
    settlements.load(
        List.of(
            new Settlement("stale", "Stale", 99, 1, 2).toData(),
            new Settlement("retained", "Retained", 10, 3, 4).toData()));
    clearInvocations(leader, fixture.map);
    settlements.validate();
    assertNull(settlements.getByProvince(99));
    assertNotNull(settlements.getByProvince(10));
    assertEquals(-1, home.getCapital());
    assertEquals(-1, visitor.getCapital());
    verify(leader).sendMessage(contains("Stale"));
    verify(fixture.map, atLeastOnce()).enqueue("nation", home.getRGB());
  }

  @Test
  void departureChecksLeaveOccupiedCitiesAndNullTransfersUntouched() {
    Settlement city = settlements.found("Home", 10, 0, 0).getSettlement();
    Guild resident = fixture.guild(home, "resident", "Resident");
    resident.setCapital(10, false);
    settlements.onGuildDepartedCapital(-1);
    settlements.onGuildDepartedCapital(99);
    settlements.acceptTransferred(null);
    assertNull(settlements.detachOnProvince(99));
    settlements.onGuildDepartedCapital(10);
    assertSame(city, settlements.getByProvince(10));
    assertEquals(List.of(resident), settlements.getPopulation(city));
    assertEquals(1, settlements.populationSize(city));
    resident.setCapital(-1, false);
    settlements.onGuildDepartedCapital(10);
    assertTrue(settlements.getAll().isEmpty());
    assertEquals(-1, resident.getCapital());
  }

  @Test
  void foundingWithoutTheOptionalMapStillRegistersTheSettlement() throws Exception {
    var field =
        net.tfminecraft.simplefactions.managers.FactionManager.class.getDeclaredField("map");
    field.setAccessible(true);
    Object original = field.get(null);
    try {
      field.set(null, null);
      var founded = settlements.found("Town", 10, 3, 4);
      assertTrue(founded.isSuccess());
      assertSame(founded.getSettlement(), settlements.getByProvince(10));
    } finally {
      field.set(null, original);
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void aGuildNeedsANameWhenFoundingOutsideTheExistingCity(String name) {
    Settlement city = settlements.found("Existing", 10, 0, 0).getSettlement();
    Guild guild = fixture.guild(home, "resident", "Resident");
    assertFalse(settlements.resolveGuildCapital(leader, guild, 11, name).isSuccess());
    assertSame(city, settlements.getByProvince(10));
    assertNull(settlements.getByProvince(11));
    assertEquals(-1, guild.getCapital());
  }

  @Test
  void losingACityProvinceClearsItsResidentsCapitalsAndKeepsOtherSettlements() {
    settlements.found("Capital", 10, 0, 0);
    Settlement retained = settlements.found("Retained", 11, 3, 4).getSettlement();
    Guild resident = fixture.guild(home, "resident", "Resident");
    resident.setCapital(10, false);
    home.setCapital(10, true, false);
    settlements.onProvinceLost(99);
    home.removeProvince(10, false);
    assertNull(settlements.getByProvince(10));
    assertEquals(-1, home.getCapital());
    assertEquals(-1, resident.getCapital());
    assertSame(retained, settlements.getByProvince(11));
    verify(leader).sendMessage(contains("has been destroyed"));
  }

  private Faction faction(String id, String name, int... provinces) {
    FactionData data = fixture.data(id, name);
    for (int province : provinces) {
      data.provinces.add(province);
      fixture.provinceData.put(province, new Province(province, "PLAINS", 50));
    }
    Faction faction = fixture.saved(data);
    faction.getOrCreateMainGuild();
    return faction;
  }
}
