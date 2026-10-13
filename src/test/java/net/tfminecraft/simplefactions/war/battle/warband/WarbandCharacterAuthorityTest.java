package net.tfminecraft.simplefactions.war.battle.warband;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import org.junit.jupiter.api.Test;

class WarbandCharacterAuthorityTest {
    @Test
    void enrollmentUsesTheNamedCharacterAndRefusesTheWrongActiveCharacterOrUnknownRecord() {
        try (SharedIdentity identity = new SharedIdentity()) {
            var player = identity.connect("owner");
            var first = identity.character(player.getUniqueId(), "First");
            var second = identity.character(player.getUniqueId(), "Second");
            identity.play(player, first);
            assertEquals(java.util.Optional.of(first), Warband.participant(player.getUniqueId(),
                    SharedIdentity.key(first), player));
            assertTrue(Warband.participant(player.getUniqueId(), SharedIdentity.key(second), player).isEmpty());
            identity.stopPlaying(player);
            assertTrue(Warband.participant(player.getUniqueId(), SharedIdentity.key(first), player).isEmpty());
            identity.online.clear();
            identity.connections.clear();
            assertEquals(java.util.Optional.of(second), Warband.participant(player.getUniqueId(),
                    SharedIdentity.key(second), null));
            assertTrue(Warband.participant(player.getUniqueId(), java.util.UUID.randomUUID().toString(), null).isEmpty());
        }
    }
    @Test
    void membershipLeadershipInvitationAndOnlineRoutingStayWithTheRecordedCharacter() {
        try (SharedIdentity identity = new SharedIdentity()) {
            var player = identity.connect("owner");
            var first = identity.character(player.getUniqueId(), "First");
            var second = identity.character(player.getUniqueId(), "Second");
            identity.play(player, first);
            Warband band = new Warband("one", player);
            WarbandManager.resetForTests();
            WarbandManager.addWarband(band);
            band.invite(player);
            assertTrue(band.hasMember(player));
            assertTrue(band.isLeader(player));
            assertTrue(band.isInvited(player));
            assertEquals(List.of(player), band.getOnlineMembers());
            assertEquals(player, band.getLeader());
            assertEquals("First", band.getMemberDisplayName(player.getUniqueId()));
            identity.play(player, second);
            assertFalse(band.hasMember(player));
            assertFalse(band.isLeader(player));
            assertFalse(band.isInvited(player));
            assertTrue(band.getOnlineMembers().isEmpty());
            assertTrue(band.getInvited().isEmpty());
            assertNull(band.getLeader());
            assertEquals("First", band.getMemberDisplayName(player.getUniqueId()));
            assertNull(WarbandManager.getByPlayer(player));
            assertNull(WarbandManager.getByLeader(player));
            assertSame(band, WarbandManager.getByMemberId(player.getUniqueId()));
            band.addPlayer(player);
            assertEquals(first, band.getMemberCharacter(player.getUniqueId()).orElseThrow());
            band.restoreMemberCharacters(java.util.Map.of());
            assertEquals("owner", band.getMemberDisplayName(player.getUniqueId()));
            var newcomer = identity.connect("newcomer");
            assertEquals("Character identity is not ready for warband signup",
                    net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService.signupMember(
                            newcomer.getUniqueId(), "named", band, null, newcomer));
        } finally {
            WarbandManager.resetForTests();
        }
    }

    @Test
    void capturedEndCleansUpOutgoingCharacterAndCannotDetachAnotherRole() {
        try (SharedIdentity identity = new SharedIdentity()) {
            var player = identity.connect("owner");
            var first = identity.character(player.getUniqueId(), "First");
            var second = identity.character(player.getUniqueId(), "Second");
            var ended = identity.play(player, first);
            Warband band = new Warband("one", player);
            WarbandManager.resetForTests();
            WarbandManager.addWarband(band);
            WarbandMembershipService.resetForTests();
            identity.stopPlaying(player);
            var service = WarbandMembershipService.getInstance();
            new WarbandMembershipListener().onSessionEnd(new net.tfminecraft.tlibs.character.CharacterSessionEndEvent(
                    player, ended, net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason.SWITCH));
            assertFalse(band.hasMember(player.getUniqueId()));
            assertTrue(service.getPendingRejoin(player.getUniqueId()).isFor(first));
            var incoming = identity.play(player, second);
            assertFalse(service.handleSessionStart(player, ended));
            assertNotNull(service.getPendingRejoin(player.getUniqueId()));
            assertFalse(service.handleSessionStart(player, incoming));
            band.addPlayer(player);
            service.handleSessionEnd(player, ended);
            assertTrue(band.hasMember(player));
            assertEquals(second, band.getMemberCharacter(player.getUniqueId()).orElseThrow());
        } finally {
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
        }
    }

    @Test
    void repeatSignupCannotAcknowledgeAnotherCharactersSlotAndRejoinCannotDuplicateAnotherBand() {
        try (SharedIdentity identity = new SharedIdentity()) {
            var player = identity.connect("owner");
            var first = identity.character(player.getUniqueId(), "First");
            var second = identity.character(player.getUniqueId(), "Second");
            var captured = identity.play(player, first);
            Warband band = new Warband("one", player);
            WarbandManager.resetForTests();
            WarbandManager.addWarband(band);
            assertNull(net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService.signupMember(
                    player.getUniqueId(), SharedIdentity.key(first), band, null, player));
            identity.play(player, second);
            assertEquals("Another character holds this warband slot",
                    net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService.signupMember(
                            player.getUniqueId(), SharedIdentity.key(second), band, null, player));
            var service = WarbandMembershipService.getInstance();
            service.handleSessionEnd(player, captured);
            var returning = identity.play(player, first);
            Warband other = new Warband("other", player);
            WarbandManager.addWarband(other);
            assertFalse(service.handleSessionStart(player, returning));
            assertFalse(band.hasMember(player.getUniqueId()));
            assertTrue(other.hasMember(player));
        } finally {
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
        }
    }
}
