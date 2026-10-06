package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.diplomacy.Attitude;
import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.RelationRequest;

class TradeRelationMigrationTest {
    private List<RelationType> savedTypes;
    private List<Attitude> savedAttitudes;
    private List<Faction> savedFactions;

    @BeforeEach
    void setUp() {
        savedTypes = new ArrayList<>(RelationLoader.types);
        savedAttitudes = new ArrayList<>(RelationLoader.attitudes);
        savedFactions = new ArrayList<>(FactionManager.factions);
        RelationLoader.types.clear();
        RelationLoader.attitudes.clear();
        FactionManager.factions.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("diplomacy.yml"), StandardCharsets.UTF_8));
        for (String key : yaml.getConfigurationSection("types").getKeys(false)) {
            RelationLoader.types.add(new RelationType(key, yaml.getConfigurationSection("types." + key)));
        }
        for (String key : yaml.getConfigurationSection("attitudes").getKeys(false)) {
            RelationLoader.attitudes.add(new Attitude(key, yaml.getConfigurationSection("attitudes." + key)));
        }
    }

    @AfterEach
    void tearDown() {
        RelationLoader.types.clear();
        RelationLoader.types.addAll(savedTypes);
        RelationLoader.attitudes.clear();
        RelationLoader.attitudes.addAll(savedAttitudes);
        FactionManager.factions.clear();
        FactionManager.factions.addAll(savedFactions);
    }

    @Test
    void misplacedTradeAgreementsMoveAndKeepOpinion() {
        Faction bog = faction("The_Bog");
        Faction thalendor = faction("Thalendor");
        Faction rats = faction("Rat_Hill");
        Faction aureate = faction("The_Aureate_Coterie");
        Faction ally = faction("Sunsora");
        relate(bog, thalendor, "trade_agreement", "friendly", 52);
        relate(thalendor, bog, "trade_agreement", "friendly", 52);
        relate(rats, aureate, "trade_agreement", "friendly", 65);
        relate(aureate, rats, "trade_agreement", "neutral", 15);
        relate(bog, ally, "ally", "friendly", 40);
        FactionManager.factions.addAll(List.of(bog, thalendor, rats, aureate, ally));

        FactionManager.migrateMisplacedTradeRelations();

        assertTrade(bog, thalendor, "trade_agreement", "friendly", 52);
        assertTrade(thalendor, bog, "trade_agreement", "friendly", 52);
        assertTrade(rats, aureate, "trade_agreement", "friendly", 65);
        assertTrade(aureate, rats, "trade_agreement", "neutral", 15);
        assertEquals("ally", bog.getDiplomacyHandler().getRelation(ally.getId()).getType().getId());
        assertNull(bog.getDiplomacyHandler().getTradeRelation(ally.getId()));

        FactionManager.migrateMisplacedTradeRelations();
        assertTrade(bog, thalendor, "trade_agreement", "friendly", 52);
        assertEquals("none", bog.getDiplomacyHandler().getRelation(thalendor.getId()).getType().getId());
    }

    @Test
    void oneSidedMutualAgreementFillsTheOtherSideAndKeepsAStoredTradeRelation() {
        Faction leader = faction("leader");
        Faction subject = faction("subject");
        relate(leader, subject, "unequal_treaty_leader", "neutral", 3);
        FactionManager.factions.addAll(List.of(leader, subject));

        FactionManager.migrateMisplacedTradeRelations();

        assertEquals("unequal_treaty_leader", leader.getDiplomacyHandler().getTradeRelation(subject.getId()).getId());
        assertEquals("unequal_treaty_subject", subject.getDiplomacyHandler().getTradeRelation(leader.getId()).getId());
        assertEquals("none", leader.getDiplomacyHandler().getRelation(subject.getId()).getType().getId());
        assertEquals(3, leader.getDiplomacyHandler().getRelation(subject.getId()).getOpinion());

        Faction kept = faction("kept");
        Faction other = faction("other");
        kept.getDiplomacyHandler().setTradeRelation(other, RelationLoader.getType("embargo"));
        relate(kept, other, "trade_agreement", "friendly", 10);
        FactionManager.factions.addAll(List.of(kept, other));
        FactionManager.migrateMisplacedTradeRelations();
        assertEquals("embargo", kept.getDiplomacyHandler().getTradeRelation(other.getId()).getId());
        assertEquals("none", kept.getDiplomacyHandler().getRelation(other.getId()).getType().getId());
        assertEquals(10, kept.getDiplomacyHandler().getRelation(other.getId()).getOpinion());
    }

    @Test
    void acceptTradeRequestWritesTheTradeMap() {
        Faction senderFaction = faction("sender");
        Faction receiverFaction = faction("receiver");
        RelationType trade = RelationLoader.getType("trade_agreement");
        Guild guild = mock(Guild.class);
        when(guild.getFaction()).thenReturn(senderFaction);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("receiver-leader");
        when(receiverFaction.getLeader()).thenReturn("receiver-leader");
        when(senderFaction.getLeader()).thenReturn("sender-leader");

        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
                MockedStatic<RequestManager> requests = mockStatic(RequestManager.class);
                MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            factions.when(() -> FactionManager.getByLeader("receiver-leader")).thenReturn(receiverFaction);
            requests.when(() -> RequestManager.getRequest(player))
                    .thenReturn(new RelationRequest(guild, trade, true));
            bukkit.when(() -> Bukkit.getPlayerExact("sender-leader")).thenReturn(null);
            RelationManager.acceptTradeRequest(player);
        }

        assertEquals("trade_agreement", senderFaction.getDiplomacyHandler().getTradeRelation("receiver").getId());
        assertEquals("trade_agreement", receiverFaction.getDiplomacyHandler().getTradeRelation("sender").getId());
        assertFalse(senderFaction.getRelations().containsKey("receiver"));
        assertTrue(senderFaction.getDiplomacyHandler().hasTradeRelation("receiver"));
    }

    private void assertTrade(Faction from, Faction to, String trade, String attitude, int opinion) {
        assertEquals(trade, from.getDiplomacyHandler().getTradeRelation(to.getId()).getId());
        Relation diplomatic = from.getDiplomacyHandler().getRelation(to.getId());
        assertEquals("none", diplomatic.getType().getId());
        assertEquals(attitude, diplomatic.getAttitude().getId());
        assertEquals(opinion, diplomatic.getOpinion());
    }

    private void relate(Faction from, Faction to, String type, String attitude, int opinion) {
        from.getDiplomacyHandler().setRelation(to, new Relation(
                RelationLoader.getType(type), RelationLoader.getAttitude(attitude), opinion));
    }

    private Faction faction(String id) {
        Faction faction = mock(Faction.class);
        DiplomacyHandler handler = new DiplomacyHandler(faction);
        when(faction.getId()).thenReturn(id);
        when(faction.getDiplomacyHandler()).thenReturn(handler);
        when(faction.getRelations()).thenReturn(handler.getRelations());
        return faction;
    }
}
