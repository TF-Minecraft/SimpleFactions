package net.tfminecraft.simplefactions.war.campaign.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.registry.*;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidLifecycleCoverageTest.Fixture;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleSiegeFortService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignBattlePresentationCoverageTest {
  @TempDir Path temporary;
  private Fixture rig;
  private PlayerVehicleRegistry registry;

  @BeforeEach
  void setup() throws Exception {
    rig = new Fixture();
    for (String field :
        List.of(
            "personalSlotLimit",
            "defaultPerPerson",
            "maintenanceHourlyDamagePercent",
            "maintenanceMinHealthPercent",
            "maintenanceIntervalTicks",
            "categoryIds",
            "typesByCategory",
            "categoryByVehicleTypeId",
            "categoryDisplayNames",
            "feeExcludedCategories")) rig.remember(VehiclesConfigLoader.class, field);
    registry = new PlayerVehicleRegistry();
    setRegistry(registry);
    YamlConfiguration config = new YamlConfiguration();
    config.set("default-upkeep", 0);
    for (String category : List.of("ships", "aircraft", "land_vehicles", "experimental"))
      config.set("categories." + category + ".show-on-upcoming-battle-icon", true);
    config.set("categories.ships.brig.size", 1);
    config.set("categories.ships.cutter.size", 1);
    config.set("categories.aircraft.airship.size", 1);
    config.set("categories.land_vehicles.cannon.size", 1);
    config.set("categories.experimental.unknown_category.size", 1);
    config.set("categories.ships.hidden.size", 1);
    config.set("categories.ships.hidden.show-on-upcoming-battle-icon", false);
    Path vehicles = temporary.resolve("vehicles.yml");
    config.save(vehicles.toFile());
    VehiclesConfigLoader.load(vehicles.toFile());
    rig.war.setCampaignPhase(CampaignPhase.INVASION);
    rig.war.setCampaignProvinces(new ArrayList<>(List.of(10, 20)));
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null)));
    rig.war.setCampaignScheduleIndex(0);
  }

  @AfterEach
  void cleanup() throws Exception {
    if (rig != null) rig.close();
  }

  @Test
  void scheduledBattleLoreCountsOnlyProfessionalPeopleAcrossParticipatingFactions() {
    Faction subject = rig.domain.saved("subject", "Subject");
    Faction ally = rig.domain.saved("ally", "Ally");
    Faction absentAlly = rig.domain.saved("unjoined", "Absent");
    Participant participant =
        new Participant(
            rig.attacker, List.of(subject), Map.of(ally, true, absentAlly, false), Map.of(), false);
    rig.war.getAttackers().getMainParticipants().clear();
    rig.war.getAttackers().getMainParticipants().add(participant);
    unit(rig.attacker, "regular", 12, false, false);
    unit(rig.attacker, "levy", 50, true, false);
    unit(rig.attacker, "artillery", 9, false, true);
    unit(subject, "subject_regular", 7, false, false);
    unit(ally, "allied_regular", 3, false, false);
    unit(absentAlly, "not_joined", 100, false, false);
    unit(rig.defender, "defenders", 6, false, false);
    unit(rig.defender, "invalid_saved_slots", -4, false, false);

    List<String> lore = routeLore();

    assertTrue(lore.contains("Attackers: 22"), lore.toString());
    assertTrue(lore.contains("Defenders: 6"), lore.toString());
    assertFalse(lore.contains("Vehicles"));
    assertEquals(22, CampaignBattleIconLore.countSoldiers(rig.war.getAttackers()));
    assertEquals(0, CampaignBattleIconLore.countSoldiers(null));
  }

  @Test
  void lockedBattleLoreGroupsSortsAndDeduplicatesVisibleVehiclesByPickedFaction() {
    rig.war
        .getBattleInstallationPicks()
        .put(rig.attacker.getId(), new LinkedHashSet<>(List.of("shared")));
    rig.war
        .getBattleInstallationPicks()
        .put(rig.defender.getId(), new LinkedHashSet<>(List.of("shared")));
    rig.war.getBattleInstallationPicks().put("invalid_empty_record", null);
    vehicle("ship-1", "cutter", rig.attacker.getId(), "shared");
    vehicle("ship-2", "brig", rig.attacker.getId(), "shared");
    vehicle("legacy", "brig", null, "shared");
    vehicle("air-1", "AIRSHIP", rig.defender.getId(), "shared");
    vehicle("land-1", "cannon", rig.defender.getId(), "shared");
    vehicle("hidden", "hidden", rig.attacker.getId(), "shared");
    vehicle("experimental", "unknown_category", rig.attacker.getId(), "shared");
    vehicle("missing_type", null, rig.attacker.getId(), "shared");
    vehicle("neutral", "brig", "neutral_faction", "shared");
    vehicle("unpicked", "brig", rig.attacker.getId(), "somewhere_else");

    List<String> lore = routeLore();

    assertEquals(
        List.of(
            "Vehicles",
            "Ships",
            "  brig: 2",
            "  cutter: 1",
            "Aircraft",
            "  airship: 1",
            "Land",
            "  cannon: 1"),
        lore.subList(lore.indexOf("Vehicles"), lore.size()));
    assertEquals(10, registry.getAll().size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void siegeLoreMustNotCountNeutralVehiclesAtAnUnrelatedFortWithTheSameLocalId(
      boolean earlierOwner) {
    Faction neutral = earlierOwner ? rig.attacker : rig.domain.saved("neutral", "Neutral");
    rig.install(rig.defender, "shared_fort", InstallationKind.FORT, 20);
    rig.install(neutral, "shared_fort", InstallationKind.FORT, 90);
    rig.war.setCampaignBattleSchedule(
        List.of(new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, true, "shared_fort")));
    vehicle("defending-cannon", "cannon", rig.defender.getId(), "shared_fort");
    vehicle("neutral-cannon", "cannon", neutral.getId(), "shared_fort");

    List<String> lore = routeLore();

    assertTrue(lore.contains("  cannon: 1"), lore.toString());
    assertFalse(lore.contains("  cannon: 2"));
    assertEquals(2, registry.getAll().size());
    assertSame(rig.defender, BattleSiegeFortService.currentSiegeFortOwner(rig.war).orElseThrow());
    assertTrue(
        BattleSiegeFortService.isSiegeFortInPlayForFaction(
            rig.war, rig.defender.getId(), "shared_fort"));
    assertFalse(
        BattleSiegeFortService.isSiegeFortInPlayForFaction(
            rig.war, neutral.getId(), "shared_fort"));
  }

  @Test
  void displayedVehicleIdentifiersRemainCaseInsensitiveUnderTurkishLocale() {
    rig.war
        .getBattleInstallationPicks()
        .put(rig.defender.getId(), new LinkedHashSet<>(List.of("airfield")));
    vehicle("airship", "AIRSHIP", rig.defender.getId(), "airfield");
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      List<String> lore = routeLore();
      assertTrue(lore.contains("  airship: 1"), lore.toString());
      assertFalse(lore.contains("Ships"));
      assertFalse(lore.contains("Land"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void unlockedOrUnavailableVehicleDataAddsNoVehicleSection() throws Exception {
    rig.war
        .getBattleInstallationPicks()
        .put(rig.attacker.getId(), new LinkedHashSet<>(List.of("picked")));
    vehicle("visible", "brig", rig.attacker.getId(), "picked");
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 15));
    assertFalse(routeLore().contains("Vehicles"));
    rig.time(BattleWindowService.atScheduleHour(Fixture.DAY, 19));
    SimpleFactions previous = SimpleFactions.plugin;
    try {
      SimpleFactions.plugin = null;
      List<String> missingPlugin = new ArrayList<>(List.of("Existing line"));
      CampaignBattleIconLore.appendVehiclesIfLocked(missingPlugin, rig.war);
      assertEquals(List.of("Existing line"), missingPlugin);
    } finally {
      SimpleFactions.plugin = previous;
    }
    setRegistry(null);
    assertFalse(routeLore().contains("Vehicles"));
    setRegistry(registry);
    registry.unregister("visible");
    vehicle("hidden", "hidden", rig.attacker.getId(), "picked");
    assertFalse(routeLore().contains("Vehicles"));
  }

  @Test
  void emptyOrMissingPresentationInputsKeepExistingLore() {
    List<String> lore = new ArrayList<>(List.of("Existing line"));
    CampaignBattleIconLore.appendSoldiers(null, rig.war);
    CampaignBattleIconLore.appendSoldiers(lore, null);
    CampaignBattleIconLore.appendVehiclesIfLocked(null, rig.war);
    CampaignBattleIconLore.appendVehiclesIfLocked(lore, null);
    assertEquals(List.of("Existing line"), lore);
    assertFalse(routeLore().contains("Vehicles"));
  }

  private void unit(Faction faction, String id, int slots, boolean levy, boolean equipment) {
    YamlConfiguration data = new YamlConfiguration();
    data.set(id + ".item.material", "PAPER");
    data.set(id + ".default-slots", slots);
    data.set(id + ".levy", levy);
    data.set(id + ".equipment", equipment);
    faction.getMilitary().getRegiments().add(new Regiment(id, data.getConfigurationSection(id)));
  }

  private void vehicle(String id, String type, String factionId, String installationId) {
    registry.register(
        new PlayerVehicleRecord(
            rig.alice.getUniqueId(),
            id,
            type,
            OwnershipMode.INSTALLATION,
            installationId,
            factionId));
  }

  private List<String> routeLore() {
    return CampaignRouteRenderer.buildRouteLore(
            rig.war,
            new CampaignRouteEntry(20, 1, 0),
            province -> province == 20 ? rig.defender.getId() : rig.attacker.getId())
        .stream()
        .map(ChatColor::stripColor)
        .toList();
  }

  private void setRegistry(PlayerVehicleRegistry value) throws Exception {
    Field field = SimpleFactions.class.getDeclaredField("vehicleRegistry");
    field.setAccessible(true);
    field.set(rig.domain.ui.plugin, value);
  }
}
