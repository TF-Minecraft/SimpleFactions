package net.tfminecraft.simplefactions.installation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SiegeFortOccupationIdentityTest {
  @ParameterizedTest
  @EnumSource(BelligerentRole.class)
  void theScheduledFortMovesAndRevertsWithoutTakingAnotherFactionsEarlierPort(
      BelligerentRole winner) throws Exception {
    try (PersistenceFilesFixture files = new PersistenceFilesFixture();
        FactionDomainFixture domain = new FactionDomainFixture()) {
      Faction portOwner = domain.saved("earlier_port_owner", "Cara");
      Faction attacker = domain.saved("siege_attacker", "Alice");
      Faction defender = domain.saved("siege_defender", "Bob");
      Faction holder = winner == BelligerentRole.ATTACKER ? defender : attacker;
      Faction occupying = winner == BelligerentRole.ATTACKER ? attacker : defender;
      Installation port = installation(portOwner, InstallationKind.PORT);
      Installation fort = installation(holder, InstallationKind.FORT);
      Field registry = SimpleFactions.class.getDeclaredField("vehicleRegistry");
      registry.setAccessible(true);
      registry.set(domain.ui.plugin, new PlayerVehicleRegistry());
      when(domain.ui.plugin.saveVehicleRegistry()).thenReturn(true);
      War war = new War(990341, attacker, defender);
      war.setGoal(WarGoalType.WAR);
      WarManager.addWar(war);
      ScheduledCampaignBattle siege =
          new ScheduledCampaignBattle(20, CampaignBattleKind.SIEGE, false, fort.getId());
      assertSame(
          portOwner,
          InstallationLookup.findHolderOnProvince(20),
          "The earlier province match belongs to a port, not the besieged fort");

      WartimeInstallationService.occupySiegeFort(war, winner, siege);

      assertAll(
          () -> assertSame(port, portOwner.getInstallationHandler().getById("shared")),
          () -> assertSame(portOwner, InstallationOwners.ownerOf(port)),
          () -> assertSame(occupying, InstallationOwners.ownerOf(fort)),
          () -> assertTrue(holder.getInstallationHandler().getAll().isEmpty()),
          () ->
              assertEquals(
                  Map.of(fort.getStableKey(), holder.getId()), war.getWartimeInstallationOwners()));
      assertSame(fort, occupying.getInstallationHandler().getByProvince(InstallationKind.FORT, 20));

      WartimeInstallationService.revert(war);

      assertAll(
          () -> assertSame(port, portOwner.getInstallationHandler().getById("shared")),
          () -> assertSame(portOwner, InstallationOwners.ownerOf(port)),
          () -> assertSame(fort, holder.getInstallationHandler().getById("shared")),
          () -> assertSame(holder, InstallationOwners.ownerOf(fort)),
          () -> assertTrue(occupying.getInstallationHandler().getAll().isEmpty()),
          () -> assertTrue(war.getWartimeInstallationOwners().isEmpty()));
    }
  }

  private static Installation installation(Faction holder, InstallationKind kind) {
    Installation installation = new Installation("shared", kind.name(), kind, 20, 200, 200, 100L);
    holder.getInstallationHandler().acceptTransferred(installation);
    return installation;
  }
}
