package net.tfminecraft.simplefactions.war.battle.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.testsupport.SharedIdentity;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.simplefactions.war.battle.persistence.WarbandCharacters;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandMembershipService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WarbandAccountSlotTest {
    @ParameterizedTest
    @ValueSource(strings = {"other-character", "null-slot", "held-file"})
    void commandGuiCreateAndDirectSignupKeepOneStructuralBandPerAccount(String state) throws Exception {
        var previousPlugin = SimpleFactions.plugin;
        try (PersistenceFilesFixture files = new PersistenceFilesFixture(); SharedIdentity identity = new SharedIdentity();
                var persistence = mockStatic(BattlePersistenceService.class)) {
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
            var owner = identity.connect("owner");
            var recorded = identity.character(owner.getUniqueId(), "Recorded");
            var other = identity.character(owner.getUniqueId(), "Other");
            identity.heirs.put(owner.getUniqueId(), recorded);
            var file = files.write("Warbands/warband_legacy.json", "{\"id\":\"legacy\",\"leaderId\":\""
                    + owner.getUniqueId() + "\",\"memberIds\":[\"" + owner.getUniqueId()
                    + "\"],\"invitedIds\":[],\"locked\":true"
                    + (state.equals("null-slot") ? ",\"memberCharacters\":{\"" + owner.getUniqueId() + "\":null}" : "") + "}");
            Warband legacy = WarbandCharacters.read(file.toFile());
            WarbandManager.addWarband(legacy);
            if (state.equals("held-file")) {
                files.write("Warbands/warband_legacy.json", "{broken");
                assertThrows(IOException.class, () -> WarbandCharacters.read(file.toFile()));
                assertTrue(legacy.isIdentityHeld());
            }
            var host = identity.connect("host");
            identity.play(host, identity.character(host.getUniqueId(), "Host"));
            Warband open = new Warband("open", host);
            open.setLocked(false);
            WarbandManager.addWarband(open);
            var ended = identity.play(owner, other);
            assertNull(WarbandManager.getByPlayer(owner));
            var beforeLegacy = List.copyOf(legacy.getMemberIds());
            var beforeOpen = List.copyOf(open.getMemberIds());
            String bytes = java.nio.file.Files.readString(file);
            Command command = mock(Command.class);
            when(command.getName()).thenReturn("warband");
            var commands = new BattleCommandManager();
            assertTrue(commands.onCommand(owner, command, "warband", new String[] {"join", "open"}));
            verify(owner).sendMessage("§cAlready in a warband, leave your current warband first!");
            assertTrue(commands.onCommand(owner, command, "warband", new String[] {"create", "second"}));
            assertNull(WarbandManager.getByString("second"));
            assertEquals("Already in another warband, leave your current warband first",
                    CampaignWarbandSignupService.signupMember(owner.getUniqueId(), SharedIdentity.key(other), open, null, owner));
            assertEquals("Already in another warband, leave your current warband first",
                    CampaignWarbandSignupService.signupMember(owner.getUniqueId(), SharedIdentity.key(other), open, null));

            SimpleFactions.plugin = mock(SimpleFactions.class);
            when(SimpleFactions.plugin.getName()).thenReturn("SimpleFactions");
            when(SimpleFactions.plugin.namespace()).thenReturn("simplefactions");
            // The click listener receives the same PDC item used by the warband list.
            var click = click(owner, open.getId());
            identity.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            new WarbandManager().invenClick(click);
            verify(click).setCancelled(true);
            verify(owner, times(2)).sendMessage("§cAlready in a warband, leave your current warband first!");
            assertEquals(beforeLegacy, List.copyOf(legacy.getMemberIds()));
            assertEquals(beforeOpen, List.copyOf(open.getMemberIds()));
            assertSame(legacy, WarbandManager.getByMemberId(owner.getUniqueId()));
            assertEquals(1, WarbandManager.get().stream().filter(band -> band.hasMember(owner.getUniqueId())).count());
            assertEquals(bytes, java.nio.file.Files.readString(file));
            persistence.verifyNoInteractions();
            identity.stopPlaying(owner);
            var membership = WarbandMembershipService.getInstance();
            membership.handleSessionEnd(owner, ended);
            assertTrue(legacy.hasMember(owner.getUniqueId()));
            assertNull(membership.getPendingRejoin(owner.getUniqueId()));
            if (state.equals("other-character")) {
                var recordedSession = identity.play(owner, recorded);
                identity.stopPlaying(owner);
                membership.handleSessionEnd(owner, recordedSession);
                assertFalse(legacy.hasMember(owner.getUniqueId()));
                assertTrue(membership.getPendingRejoin(owner.getUniqueId()).isFor(recorded));
                assertFalse(open.hasMember(owner.getUniqueId()));
            } else if (state.equals("null-slot")) {
                assertTrue(legacy.getMemberCharacter(owner.getUniqueId()).isEmpty());
                assertEquals(com.google.gson.JsonNull.INSTANCE, JsonParser.parseString(bytes).getAsJsonObject()
                        .getAsJsonObject("memberCharacters").get(owner.getUniqueId().toString()));
            }
        } finally {
            SimpleFactions.plugin = previousPlugin;
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedDirectSignupPreservesExistingPendingRejoin(boolean online) {
        try (SharedIdentity identity = new SharedIdentity(); var persistence = mockStatic(BattlePersistenceService.class)) {
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
            var player = identity.connect("owner");
            var recorded = identity.character(player.getUniqueId(), "Recorded");
            var captured = identity.play(player, recorded);
            Warband original = new Warband("original", player);
            WarbandManager.addWarband(original);
            identity.stopPlaying(player);
            var membership = WarbandMembershipService.getInstance();
            membership.handleSessionEnd(player, captured);
            var pending = membership.getPendingRejoin(player.getUniqueId());
            identity.play(player, recorded);
            Warband current = new Warband("current", player);
            WarbandManager.addWarband(current);
            assertEquals("Already in another warband, leave your current warband first",
                    CampaignWarbandSignupService.signupMember(player.getUniqueId(), SharedIdentity.key(recorded), original,
                            null, online ? player : null));
            assertSame(pending, membership.getPendingRejoin(player.getUniqueId()));
            assertEquals(Optional.of(recorded), current.getMemberCharacter(player.getUniqueId()));
            assertFalse(original.hasMember(player.getUniqueId()));
            persistence.verifyNoInteractions();
        } finally {
            WarbandManager.resetForTests();
            WarbandMembershipService.resetForTests();
        }
    }

    private static InventoryClickEvent click(Player player, String band) {
        var event = mock(InventoryClickEvent.class);
        var view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        when(view.getTitle()).thenReturn(BattleInventoryManager.WARBAND_LIST_TITLE);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        var data = mock(PersistentDataContainer.class);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), eq(PersistentDataType.STRING))).thenReturn(band);
        when(event.getCurrentItem()).thenReturn(item);
        return event;
    }
}
