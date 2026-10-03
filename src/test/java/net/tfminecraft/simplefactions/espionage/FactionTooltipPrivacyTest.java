package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.inventory.FactionCreator;
import net.tfminecraft.simplefactions.objects.Faction;

class FactionTooltipPrivacyTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void foreignContextRemainsPublicWithAndWithoutBypass(boolean bypass) {
        Player viewer = mock(Player.class);
        when(viewer.getName()).thenReturn("Visitor");
        Faction origin = mock(Faction.class, RETURNS_DEEP_STUBS);
        Faction target = mock(Faction.class, RETURNS_DEEP_STUBS);
        when(origin.getId()).thenReturn("origin");
        when(target.getId()).thenReturn("target");
        when(target.getRank().getName()).thenReturn("Obscure Faction");
        when(target.getTier().getFormattedName()).thenReturn("Landless");
        when(target.getLeader()).thenReturn("Leader");
        when(target.getRulerTitle()).thenReturn("Leader");
        when(target.getCulture()).thenReturn("Multicultural");
        when(target.getReligion()).thenReturn("Religious Diversity");
        when(target.getGovernmentString()).thenReturn("Community");
        when(target.getTitles()).thenReturn(List.of(mock(net.tfminecraft.simplefactions.tiers.Title.class)));
        when(target.getHighestTitle().getName()).thenReturn("Primary title");
        var guild = mock(net.tfminecraft.simplefactions.guild.Guild.class, RETURNS_DEEP_STUBS);
        when(guild.getName()).thenReturn("Foreign Guild");
        when(guild.getType().getName()).thenReturn("Guild");
        when(target.getGuildHandler().getGuilds()).thenReturn(List.of(guild));
        var subject = mock(Faction.class);
        var ally = mock(Faction.class);
        when(subject.getName()).thenReturn("Subject");
        when(ally.getName()).thenReturn("Ally");
        when(target.getMembers()).thenReturn(List.of("Leader", "Member"));
        when(target.getCompleteMemberList()).thenReturn(List.of("Leader", "Member"));
        when(target.getWealth()).thenReturn(123.45);
        when(target.getGovernment().stateReport()).thenReturn(
                new net.tfminecraft.simplefactions.government.stability.StabilityReport());
        var type = mock(net.tfminecraft.simplefactions.diplomacy.RelationType.class);
        when(type.getName()).thenReturn("Neutral");
        when(origin.getRelation("target").getType()).thenReturn(type);
        when(target.getRelation("origin").getType()).thenReturn(type);
        when(origin.getRelation("target").getOpinion()).thenReturn(12);
        when(target.getRelation("origin").getOpinion()).thenReturn(-8);
        try (var service = mockStatic(EspionageService.class);
             var factions = mockStatic(FactionManager.class);
             var relations = mockStatic(RelationManager.class);
             var titles = mockStatic(TitleManager.class)) {
            service.when(() -> EspionageService.canViewExact(viewer, target)).thenReturn(bypass);
            factions.when(() -> FactionManager.getByMember("Visitor")).thenReturn(origin);
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new java.util.ArrayList<>(List.of(target)));
            relations.when(() -> RelationManager.getSubjects(target)).thenReturn(List.of(subject));
            relations.when(() -> RelationManager.getAllies(target)).thenReturn(List.of(ally));
            List<String> plain = new FactionCreator().listLore(viewer, target).stream()
                    .map(line -> line.replaceAll("\u00a7[0-9a-fk-orx]", "")).toList();
            assertTrue(plain.contains("Diplomacy:"));
            assertTrue(plain.contains("Relation: Neutral (mutual)"));
            assertTrue(plain.contains("Our opinion of them: 12"));
            assertTrue(plain.contains("Their opinion of us: -8"));
            assertTrue(plain.contains("Primary Title: Primary title"));
            assertTrue(plain.contains("Tier: Landless"));
            assertTrue(plain.contains("Based in: None"));
            assertTrue(plain.contains("Leader: Leader"));
            assertTrue(plain.contains("Ruling System: Community"));
            assertTrue(plain.contains("Culture: Multicultural"));
            assertTrue(plain.contains("Religion: Religious Diversity"));
            assertTrue(plain.contains("Guilds:"));
            assertTrue(plain.contains("- Foreign Guild (Guild)"));
            assertTrue(plain.contains("Subjects:"));
            assertTrue(plain.contains("- Subject"));
            assertTrue(plain.contains("Allies:"));
            assertTrue(plain.contains("- Ally"));
            assertTrue(plain.stream().anyMatch(line -> line.startsWith(
                    bypass ? "Wealth: 123.45d" : "Wealth: Unknown")));
            service.verify(() -> EspionageService.refreshReports(viewer), never());
        }
    }

    @Test
    void foreignTooltipRetainsOriginalLayoutWhileNeverReadingPrivateValues() {
        Player viewer = mock(Player.class);
        when(viewer.getName()).thenReturn("Visitor");
        Faction faction = mock(Faction.class, RETURNS_DEEP_STUBS);
        when(faction.getId()).thenReturn("target");
        when(faction.getRank().getName()).thenReturn("Obscure Faction");
        when(faction.getTier().getFormattedName()).thenReturn("Landless");
        when(faction.getLeader()).thenReturn("Leader");
        when(faction.getRulerTitle()).thenReturn("Leader");
        when(faction.getCulture()).thenReturn("Multicultural");
        when(faction.getReligion()).thenReturn("Religious Diversity");
        when(faction.getPrestige()).thenReturn(42.0);
        when(faction.getTitles()).thenReturn(List.of());
        when(faction.getGuildHandler().getGuilds()).thenReturn(List.of());
        try (var service = mockStatic(EspionageService.class);
             var factions = mockStatic(FactionManager.class);
             var relations = mockStatic(RelationManager.class);
             var titles = mockStatic(TitleManager.class)) {
            factions.when(FactionManager::getCopy).thenAnswer(ignored -> new java.util.ArrayList<>(List.of(faction)));
            relations.when(() -> RelationManager.getSubjects(faction)).thenReturn(List.of());
            relations.when(() -> RelationManager.getAllies(faction)).thenReturn(List.of());
            var report = new IntelligenceReport();
            report.quality = "Detailed estimates";
            report.estimates.put("Wealth", new EspionageMath.Estimate(20, 70));
            report.estimates.put("Members", new EspionageMath.Estimate(2, 8));
            report.estimates.put("Stability", new EspionageMath.Estimate(80, 200));
            service.when(() -> EspionageService.report(viewer, faction)).thenReturn(report);
            service.when(() -> EspionageService.visibleValue(eq(viewer), eq(faction), eq("Wealth"), any()))
                    .thenReturn(45.0);
            List<String> colored = new FactionCreator().listLore(viewer, faction);
            List<String> plain = colored.stream().map(line -> line.replaceAll("§[0-9a-fk-orx]", "")).toList();
            assertEquals("Obscure Faction", plain.getFirst());
            assertTrue(plain.contains("Leader: Leader"));
            assertTrue(plain.contains("Members: 2 to 8"));
            assertTrue(plain.contains("Stable State"));
            assertFalse(plain.stream().anyMatch(line -> line.contains("200%")));
            assertTrue(plain.contains("Wealth: 20 to 70d (estimated 1)"));
            assertTrue(plain.stream().anyMatch(line -> line.startsWith("Prestige: 42.0")));
            assertTrue(plain.indexOf("Leader: Leader") < plain.indexOf("Members: 2 to 8"));
            assertTrue(plain.indexOf("Members: 2 to 8") < plain.indexOf("Wealth: 20 to 70d (estimated 1)"));
            assertTrue(colored.stream().filter(line -> line.contains("Wealth:")).allMatch(line -> line.contains("§x")));
            verify(faction, never()).getWealth();
            verify(faction, never()).getProsperity();
            verify(faction, never()).getMembers();
            verify(faction, never()).getCompleteMemberList();
            verify(faction.getGovernment(), never()).stateReport();
        }
    }
}
