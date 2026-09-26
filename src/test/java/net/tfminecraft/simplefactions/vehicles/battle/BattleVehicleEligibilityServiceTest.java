package net.tfminecraft.simplefactions.vehicles.battle;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.battle.BattleVehicleEligibilityService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidState;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

class BattleVehicleEligibilityServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private static final UUID PLAYER_UUID = UUID.randomUUID();

	private Path tempDir;
	private Faction attacker;
	private Faction defender;
	private War war;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-battle-vehicle-eligibility-");
		writeVehiclesFixture();
		InstallationConfigLoader.load(writeInstallationsFixture().toFile());

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setBattleInstallationPicksBattleDay(BATTLE_DAY);
	}

	@AfterEach
	void tearDown() throws IOException {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void personalVehicle_deniedWhateverTheType() {
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"coal_car",
				OwnershipMode.PERSONAL,
				null);

		assertFalse(BattleVehicleEligibilityService.isEligible(war, "atk", record));
		assertFalse(BattleVehicleEligibilityService.isEligible(war, "atk", "coal_car", null));
	}

	@Test
	void poolVehicle_allowedWhenFactionIsOnThePlayerSide() {
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"coal_car",
				OwnershipMode.POOL,
				null,
				"atk");

		assertTrue(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	@Test
	void poolVehicle_deniedWhenFactionIsOnTheOtherSide() {
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"field_artillery",
				OwnershipMode.POOL,
				null,
				"atk");

		assertFalse(BattleVehicleEligibilityService.isEligible(war, "def", record));
		assertEquals(
				"§cThis pool vehicle belongs to a faction that is not on your side.",
				BattleVehicleEligibilityService.Messages.forResult(
						BattleVehicleEligibilityService.BattleVehicleEligibilityResult.DENIED_POOL_SIDE));
		assertEquals(
				"§cOnly faction pool and installation vehicles can be used in a campaign battle.",
				BattleVehicleEligibilityService.Messages.forResult(
						BattleVehicleEligibilityService.BattleVehicleEligibilityResult.DENIED_NOT_FACTION_VEHICLE));
	}

	@Test
	void nonBerthable_ignoresPicks() {
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"coal_car",
				OwnershipMode.INSTALLATION,
				"airport-1");

		assertTrue(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	@Test
	void cloudskimmer_atCommittedAirport_ok() {
		setPicks("atk", "airport-1");
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cloudskimmer",
				OwnershipMode.INSTALLATION,
				"airport-1");

		assertTrue(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	@Test
	void cloudskimmer_atUncommittedAirport_denied() {
		setPicks("atk", "airport-2");
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cloudskimmer",
				OwnershipMode.INSTALLATION,
				"airport-1");

		assertFalse(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	@Test
	void cloudskimmer_atCampaignRaidSource_okWithoutPick() {
		CampaignRaid raid = new CampaignRaid();
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		raid.setState(CampaignRaidState.FIGHTING);
		raid.setSourceInstallationId("airport-1");
		raid.setTargetInstallationId("airport-def");
		war.setActiveCampaignRaid(raid);

		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cloudskimmer",
				OwnershipMode.INSTALLATION,
				"airport-1");

		assertTrue(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	@Test
	void cloudskimmer_personal_denied() {
		setPicks("atk", "airport-1");
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cloudskimmer",
				OwnershipMode.PERSONAL,
				null);

		assertFalse(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	@Test
	void cloudskimmer_missingBerthRow_denied() {
		setPicks("atk", "airport-1");
		assertFalse(BattleVehicleEligibilityService.isEligible(war, "atk", "cloudskimmer", null));
	}

	@Test
	void cloudskimmer_emptyCommittedSet_denied() {
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cloudskimmer",
				OwnershipMode.INSTALLATION,
				"airport-1");

		assertFalse(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	@Test
	void cannon_atSiegeFortWithoutPick_ok() {
		configureSiegeSchedule();
		mockFort(defender, "fort-1");
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cannon",
				OwnershipMode.INSTALLATION,
				"fort-1");

		assertTrue(BattleVehicleEligibilityService.isEligible(war, "def", record));
	}

	@Test
	void cannon_atFortOnFieldSlot_denied() {
		war.setObjectiveProvinceId(30);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort-1")));
		war.setCampaignScheduleIndex(0);
		mockFort(defender, "fort-1");
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cannon",
				OwnershipMode.INSTALLATION,
				"fort-1");

		assertFalse(BattleVehicleEligibilityService.isEligible(war, "def", record));
	}

	@Test
	void cannon_atSiegeFortWrongFaction_denied() {
		configureSiegeSchedule();
		mockFort(defender, "fort-1");
		PlayerVehicleRecord record = new PlayerVehicleRecord(
				PLAYER_UUID,
				"veh-1",
				"cannon",
				OwnershipMode.INSTALLATION,
				"fort-1");

		assertFalse(BattleVehicleEligibilityService.isEligible(war, "atk", record));
	}

	private void configureSiegeSchedule() {
		war.setObjectiveProvinceId(30);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort-1")));
		war.setCampaignScheduleIndex(0);
	}

	private static void mockFort(Faction faction, String fortId) {
		Installation fort = new Installation(fortId, "Fort", InstallationKind.FORT, 18, 0, 0, 0L);
		InstallationHandler handler = mock(InstallationHandler.class);
		when(faction.getInstallationHandler()).thenReturn(handler);
		when(handler.getById(fortId)).thenReturn(fort);
	}

	private void setPicks(String factionId, String installationId) {
		LinkedHashSet<String> picks = new LinkedHashSet<>();
		picks.add(installationId);
		war.setBattleInstallationPicks(Map.of(factionId, picks));
	}

	private Path writeInstallationsFixture() throws IOException {
		Path installationsYaml = tempDir.resolve("installations.yml");
		Files.writeString(installationsYaml, """
				consent-proximity-blocks: 20
				transfer-request-timeout-seconds: 60

				fort:
				  radius: 80
				  daily-upkeep: 50
				  construction-time: 10
				  slots:
				    static_emplacements: 8
				    land_vehicles: 2
				port:
				  radius: 80
				  daily-upkeep: 20
				  construction-time: 10
				  slots:
				    ships: 8
				airport:
				  radius: 80
				  daily-upkeep: 35
				  construction-time: 10
				  slots:
				    aircraft: 10
				""");
		return installationsYaml;
	}

	private void writeVehiclesFixture() throws IOException {
		Path vehiclesYaml = tempDir.resolve("vehicles.yml");
		Files.writeString(vehiclesYaml, """
				personal-slot-limit: 3
				default-upkeep: 4

				categories:
				  land_vehicles:
				    horse_cart:
				      upkeep: 5
				      size: 1
				  train:
				    coal_car:
				      upkeep: 1
				      size: 1
				      ignore-limit: true
				  ships:
				    ironclad:
				      upkeep: 20
				      size: 1
				  static_emplacements:
				    cannon:
				      upkeep: 10
				      size: 1
				  aircraft:
				    cloudskimmer:
				      upkeep: 8
				      size: 1
				""");
		VehiclesConfigLoader.load(vehiclesYaml.toFile());
	}
}
