package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Represents;

class VassalIntelligenceTest {
    @AfterEach void restoreDefaults() { EspionageConfig.load(new YamlConfiguration()); }

    @Test void sharedTiersAreExactWhileHigherTiersKeepTheRolledQuality() {
        Map<String, Double> values = Map.of("Wealth", 1234.4, "Prosperity", 61.6, "Administrative power", 40.0);
        var report = EspionageService.createReport(values, -50, IntelligenceTier.BROAD, new Random(1));
        assertEquals(IntelligenceTier.RUMOURS, report.tier());
        assertEquals("1234", report.display("Wealth"));
        assertEquals("62", report.display("Prosperity"));
        assertEquals(IntelligenceReport.UNKNOWN, report.display("Administrative power"));
        assertTrue(report.allows("levies"));
        assertFalse(report.allows("office-holder"));
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(report), IntelligenceReport.class);
        assertEquals(IntelligenceTier.BROAD, restored.sharedTier());
        assertEquals("1234", restored.display("Wealth"));

        var unshared = EspionageService.createReport(values, -50, new Random(1));
        assertEquals(IntelligenceTier.UNKNOWN, unshared.sharedTier());
        assertNotEquals("1234", unshared.display("Wealth"));
        assertEquals(IntelligenceReport.UNKNOWN, unshared.display("Prosperity"));
    }

    @Test void sharingIgnoresNonFiniteValuesAndFallsBackWhenAFieldLeavesTheSharedTiers() {
        var report = EspionageService.createReport(Map.of("Wealth", Double.NaN, "Prosperity", 50.0), 0, IntelligenceTier.BROAD, new Random(1));
        assertNull(report.estimate("Wealth"));
        var config = new YamlConfiguration();
        config.set("espionage.intelligence.minimum-tiers.prosperity", "reliable");
        EspionageConfig.load(config);
        assertNull(report.estimate("Prosperity"), "An exact value is never shown through the range rules");
    }

    @Test void sharedRosterAndOfficesAreComplete() {
        var target = mock(Faction.class, RETURNS_DEEP_STUBS);
        var members = java.util.stream.IntStream.range(0, 10).mapToObj(index -> "Account" + index).toList();
        when(target.getMembers()).thenReturn(members);
        when(target.getGuildHandler().getGuilds()).thenReturn(List.of());
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Account1";
        state.appoint(holder, 70);
        when(target.getEspionage()).thenReturn(state);
        when(target.isMemberIgnoreCase(anyString())).thenReturn(true);
        var report = EspionageService.createReport(Map.of(), -50, IntelligenceTier.DETAILED, new Random(2));
        try (var names = mockStatic(CharacterNames.class); var represents = mockStatic(Represents.class)) {
            names.when(() -> CharacterNames.forForeign(anyString())).thenAnswer(call -> call.getArgument(0));
            represents.when(() -> Represents.represents(eq(target), anyString())).thenReturn("Guild");
            EspionageService.captureMembers(report, target, -50, new Random(2));
            EspionageService.captureOffices(report, target, new Random(2));
        }
        assertEquals(10, report.members.size());
        assertEquals("Account1", report.officeHolder(SpecialPosition.SPYMASTER));
        assertEquals("70", report.display(IntelligenceReport.officeAptitudeKey(SpecialPosition.SPYMASTER)));
    }

    @Test void onlyDirectPartnersReceiveTheirChosenTier() {
        Faction vassal = mock(Faction.class), overlord = mock(Faction.class), stranger = mock(Faction.class);
        var vassalState = new EspionageState();
        vassalState.share(SharingPartner.OVERLORD, IntelligenceTier.RELIABLE);
        var overlordState = new EspionageState();
        overlordState.share(SharingPartner.VASSALS, IntelligenceTier.RUMOURS);
        when(vassal.getEspionage()).thenReturn(vassalState);
        when(overlord.getEspionage()).thenReturn(overlordState);
        when(stranger.getEspionage()).thenReturn(new EspionageState());
        try (var relations = mockStatic(RelationManager.class)) {
            relations.when(() -> RelationManager.isOverlord(vassal, overlord)).thenReturn(true);
            assertEquals(IntelligenceTier.RELIABLE, EspionageService.sharedTier(vassal, overlord));
            assertEquals(IntelligenceTier.RUMOURS, EspionageService.sharedTier(overlord, vassal));
            assertEquals(IntelligenceTier.UNKNOWN, EspionageService.sharedTier(vassal, stranger));
            assertEquals(IntelligenceTier.UNKNOWN, EspionageService.sharedTier(null, stranger));
            var config = new YamlConfiguration();
            config.set("espionage.vassalage.allow-sharing", false);
            EspionageConfig.load(config);
            assertEquals(IntelligenceTier.UNKNOWN, EspionageService.sharedTier(vassal, overlord));
        }
    }

    @Test void overlordsRollBetterAgainstEveryVassalBelowThem() {
        Faction vassal = mock(Faction.class), overlord = mock(Faction.class), stranger = mock(Faction.class);
        var config = new YamlConfiguration();
        config.set("espionage.vassalage.overlord-offense-bonus", 30);
        config.set("espionage.vassalage.overlord-defense-bonus", 10);
        EspionageConfig.load(config);
        try (var relations = mockStatic(RelationManager.class)) {
            relations.when(() -> RelationManager.isOnOverlordPath(vassal, overlord)).thenReturn(true);
            assertEquals(30, EspionageService.vassalageBonus(overlord, vassal));
            assertEquals(-10, EspionageService.vassalageBonus(vassal, overlord));
            assertEquals(0, EspionageService.vassalageBonus(stranger, vassal));
        }
        EspionageConfig.load(new YamlConfiguration());
        assertEquals(25, EspionageConfig.overlordOffenseBonus());
        assertEquals(25, EspionageConfig.overlordDefenseBonus());
        assertTrue(EspionageConfig.sharingAllowed());
    }

    @Test void onlyTheSpymasterSharesAndPartnersRebuildTodaysReport() {
        Faction faction = mock(Faction.class), overlord = mock(Faction.class), vassal = mock(Faction.class);
        Player spy = mock(Player.class), member = mock(Player.class);
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Spy";
        holder.playerId = UUID.randomUUID();
        state.appoint(holder, 60);
        when(faction.getId()).thenReturn("faction");
        when(faction.getEspionage()).thenReturn(state);
        when(faction.isMemberIgnoreCase(anyString())).thenReturn(true);
        when(faction.getOverlord()).thenReturn(overlord);
        when(faction.getVassals()).thenReturn(List.of(vassal));
        when(spy.getName()).thenReturn("Spy");
        when(spy.getUniqueId()).thenReturn(holder.playerId);
        when(member.getName()).thenReturn("Member");
        when(member.getUniqueId()).thenReturn(UUID.randomUUID());
        var overlordState = new EspionageState();
        overlordState.report("faction", 0, 1, IntelligenceReport::new);
        when(overlord.getEspionage()).thenReturn(overlordState);
        when(vassal.getEspionage()).thenReturn(new EspionageState());
        try (var databases = mockConstruction(Database.class, (database, context) ->
                when(database.saveFactionChecked(faction)).thenReturn(true))) {
            assertFalse(EspionageService.setSharing(member, faction, SharingPartner.OVERLORD, IntelligenceTier.BROAD));
            assertEquals(IntelligenceTier.UNKNOWN, state.sharing(SharingPartner.OVERLORD));
            assertTrue(EspionageService.setSharing(spy, faction, SharingPartner.OVERLORD, IntelligenceTier.BROAD));
            assertEquals(IntelligenceTier.BROAD, state.sharing(SharingPartner.OVERLORD));
            assertNull(overlordState.cachedReport("faction", 0, 1));
            verify(databases.constructed().get(1)).saveFaction(overlord);
            assertTrue(EspionageService.setSharing(spy, faction, SharingPartner.VASSALS, IntelligenceTier.DETAILED));
            assertEquals(3, databases.constructed().size(), "A partner without a cached report needs no save");
            assertTrue(EspionageService.setSharing(spy, faction, SharingPartner.OVERLORD, IntelligenceTier.UNKNOWN));
            assertEquals(IntelligenceTier.UNKNOWN, state.sharing(SharingPartner.OVERLORD));
            var config = new YamlConfiguration();
            config.set("espionage.vassalage.allow-sharing", false);
            EspionageConfig.load(config);
            assertFalse(EspionageService.setSharing(spy, faction, SharingPartner.VASSALS, IntelligenceTier.UNKNOWN));
            assertEquals(IntelligenceTier.DETAILED, state.sharing(SharingPartner.VASSALS));
        }
    }

    @Test void failedSaveKeepsThePreviousSharing() {
        Faction faction = mock(Faction.class);
        Player spy = mock(Player.class);
        var state = new EspionageState();
        var holder = new SpecialPositionAssignment();
        holder.playerName = "Spy";
        holder.playerId = UUID.randomUUID();
        state.appoint(holder, 60);
        state.share(SharingPartner.VASSALS, IntelligenceTier.RUMOURS);
        when(faction.getEspionage()).thenReturn(state);
        when(faction.isMemberIgnoreCase(anyString())).thenReturn(true);
        when(spy.getName()).thenReturn("Spy");
        when(spy.getUniqueId()).thenReturn(holder.playerId);
        try (var databases = mockConstruction(Database.class)) {
            assertFalse(EspionageService.setSharing(spy, faction, SharingPartner.VASSALS, IntelligenceTier.DETAILED));
            assertEquals(IntelligenceTier.RUMOURS, state.sharing(SharingPartner.VASSALS));
            verify(faction, never()).getVassals();
        }
    }

    @Test void sharingPersistsAndOlderSavesShareNothing() {
        var state = new EspionageState();
        state.share(SharingPartner.VASSALS, IntelligenceTier.RELIABLE);
        var restored = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
        assertEquals(IntelligenceTier.RELIABLE, restored.sharing(SharingPartner.VASSALS));
        assertEquals(IntelligenceTier.UNKNOWN, restored.sharing(SharingPartner.OVERLORD));
        var old = JsonUtil.GSON.fromJson("{\"sharing\":null}", EspionageState.class);
        assertEquals(IntelligenceTier.UNKNOWN, old.sharing(SharingPartner.OVERLORD));
        old.share(SharingPartner.OVERLORD, IntelligenceTier.BROAD);
        assertEquals(IntelligenceTier.BROAD, old.sharing(SharingPartner.OVERLORD));
    }

    @Test void exactEstimatesDisplayOneValue() {
        assertEquals("12", EspionageMath.Estimate.exact(11.6).display());
        assertTrue(EspionageMath.Estimate.exact(-3).isExact());
        assertEquals("3 to 5", new EspionageMath.Estimate(3, 5).display());
    }
}
