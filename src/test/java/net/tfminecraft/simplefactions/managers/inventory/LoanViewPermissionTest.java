package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;

/** Every loan button acts for a guild, so only that guild's leader may press it. */
class LoanViewPermissionTest {

	private MockedStatic<FactionManager> factionManager;
	private final Guild guild = mock(Guild.class);
	private final Player player = mock(Player.class);
	private final LoanView view = new LoanView(null);

	@BeforeEach
	void setUp() {
		factionManager = mockStatic(FactionManager.class);
		factionManager.when(() -> FactionManager.getGuildByString("victim")).thenReturn(guild);
		when(guild.getId()).thenReturn("victim");
	}

	@AfterEach
	void tearDown() {
		factionManager.close();
	}

	private InventoryClickEvent click(SFGUI type, int slot) {
		Inventory inventory = mock(Inventory.class);
		when(inventory.getHolder()).thenReturn(new SFInventoryHolder("victim", type));
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getSlot()).thenReturn(slot);
		view.click(event, inventory, player);
		return event;
	}

	@Test
	void onlyTheLenderLeaderGetsANewLoanBook() {
		when(guild.isLeader(player)).thenReturn(false);

		click(SFGUI.LOAN_MAIN_VIEW, 6);

		verify(player, never()).getInventory();
	}

	@Test
	void theLenderLeaderStillGetsPastTheCheck() {
		when(guild.isLeader(player)).thenReturn(true);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack hand = mock(ItemStack.class);
		when(hand.getType()).thenReturn(Material.AIR);
		when(inventory.getItemInMainHand()).thenReturn(hand);
		when(player.getInventory()).thenReturn(inventory);

		click(SFGUI.LOAN_MAIN_VIEW, 6);

		verify(player).sendMessage(anyString());
	}

	@Test
	void onlyTheLenderLeaderCanForgiveOrPauseInterest() {
		when(guild.isLeader(player)).thenReturn(false);

		for (int slot : new int[] { 13, 14 }) {
			InventoryClickEvent event = click(SFGUI.ISSUED_LOAN_DETAIL_VIEW, slot);
			verify(event).setCancelled(true);
			verify(event, never()).getCurrentItem();
		}
	}

	@Test
	void onlyTheBorrowerLeaderCanPayToggleAutoPayOrDefault() {
		when(guild.isLeader(player)).thenReturn(false);

		for (int slot : new int[] { 11, 12, 14 }) {
			InventoryClickEvent event = click(SFGUI.TAKEN_LOAN_DETAIL_VIEW, slot);
			verify(event).setCancelled(true);
			verify(event, never()).getCurrentItem();
		}
	}

	@Test
	void clicksInThePlayersOwnInventoryAreNotMenuButtons() {
		// A 9 slot loan menu: raw slot 6 is the Issue button, raw slot 42 is hotbar slot 7.
		assertTrue(InventoryManager.isTopInventoryClick(6, 9));
		assertTrue(!InventoryManager.isTopInventoryClick(9, 9));
		assertTrue(!InventoryManager.isTopInventoryClick(42, 9));
		assertTrue(!InventoryManager.isTopInventoryClick(-999, 9));
	}
}
