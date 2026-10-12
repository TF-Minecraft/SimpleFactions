package net.tfminecraft.simplefactions.war.battle.engine.core;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapturedBattleCharactersTest {
    @Test
    void savedParticipantsAndEventsRetainTheEnrolledCharacterAfterASwitch() {
        Player player = mock(Player.class);
        UUID account = UUID.randomUUID();
        CharacterRef first = new CharacterRef(account, UUID.randomUUID());
        CharacterRef second = new CharacterRef(account, UUID.randomUUID());
        when(player.getUniqueId()).thenReturn(account);
        try (var members = mockStatic(Members.class)) {
            members.when(() -> Members.session(player)).thenReturn(Optional.of(new CharacterSession(first, 1)));
            Battle battle = new Battle("captured");
            battle.captureParticipant(player);
            members.when(() -> Members.session(player)).thenReturn(Optional.of(new CharacterSession(second, 2)));
            assertEquals(Set.of(first), battle.getParticipants());
            Battle restored = BattleMapper.fromData(BattleMapper.toData(battle));
            assertEquals(Set.of(first), restored.getParticipants());
            var started = new BattleStartedEvent("captured", BattleType.FIELD, null, Set.of(account), battle.getParticipants());
            var ended = new BattleEndedEvent("captured", BattleType.FIELD, null, "attackers", Map.of(), Set.of(account),
                    BattleEndReason.SIDE_WIN, false, true, restored.getParticipants());
            assertEquals(Set.of(first), started.getParticipants());
            assertEquals(Set.of(first), ended.getParticipants());
            assertEquals(Set.of(account), ended.getParticipantIds());
            assertThrows(UnsupportedOperationException.class, () -> ended.getParticipants().add(second));
            battle.restoreParticipants(null);
            assertTrue(battle.getParticipants().isEmpty());
        }
    }

    @Test
    void warbandCaptureSurvivesSessionEndAndPersistence() {
        Player player = mock(Player.class);
        UUID account = UUID.randomUUID();
        CharacterRef captured = new CharacterRef(account, UUID.randomUUID());
        when(player.getUniqueId()).thenReturn(account);
        try (var members = mockStatic(Members.class)) {
            members.when(() -> Members.session(player)).thenReturn(Optional.of(new CharacterSession(captured, 1)));
            Warband warband = new Warband("band", player);
            assertEquals(Optional.of(captured), warband.getMemberCharacter(account));
            Warband restored = net.tfminecraft.simplefactions.war.battle.persistence.WarbandMapper.fromData(
                    net.tfminecraft.simplefactions.war.battle.persistence.WarbandMapper.toData(warband));
            members.when(() -> Members.session(player)).thenReturn(Optional.empty());
            assertEquals(Optional.of(captured), restored.getMemberCharacter(account));
            restored.removePlayer(player);
            assertTrue(restored.getMemberCharacter(account).isEmpty());
            restored.restoreMemberCharacters(Map.of(account, captured));
            assertTrue(restored.getMemberCharacter(account).isEmpty());
            warband.restoreMemberCharacters(Map.of(account, new CharacterRef(UUID.randomUUID(), UUID.randomUUID())));
            assertTrue(warband.getMemberCharacter(account).isEmpty());
            warband.restoreMemberCharacters(null);
            assertTrue(warband.getMemberCharacters().isEmpty());
        }
    }

    @Test
    void joiningAnActiveBattleCapturesTheNewParticipation() {
        Player player = mock(Player.class);
        UUID account = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(account);
        when(player.getName()).thenReturn("Fighter");
        Battle battle = new Battle("active");
        battle.setStarted(true);
        try (var manager = mockStatic(BattleManager.class)) {
            manager.when(() -> BattleManager.getBattleByMemberId(account)).thenReturn(battle);
            Warband warband = Warband.createWithMemberIds("band", UUID.randomUUID(), false);
            warband.addPlayer(player);
            assertEquals(1, battle.getParticipants().size());
            assertEquals(account, battle.getParticipants().iterator().next().ownerId());
        }
    }
}
