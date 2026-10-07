package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;

class TierTitleViewPageClickTest {

	private SimpleFactions pluginBackup;

	@BeforeEach
	void setUp() {
		pluginBackup = SimpleFactions.plugin;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.namespace()).thenReturn("simplefactions");
		SimpleFactions.plugin = plugin;
	}

	@AfterEach
	void tearDown() {
		SimpleFactions.plugin = pluginBackup;
	}

	@Test
	void pageButton_redrawEmptiesClickedSlot_turnsPageWithoutReadingIt() {
		TierTitleView view = spy(new TierTitleView(mock(InventoryManager.class)));
		doNothing().when(view).titleTypeView(any(), any(), any(), any(), anyBoolean(), anyInt());

		Faction faction = mock(Faction.class);
		Tier tier = mock(Tier.class);
		Title title = mock(Title.class);
		when(title.getTier()).thenReturn(tier);
		Player player = mock(Player.class);

		// The next-page button, which is no longer in its slot on the last page.
		ItemStack pageItem = itemWith(PersistentDataType.INTEGER, 1);
		when(pageItem.getType()).thenReturn(Material.PAPER);
		ItemStack firstTitle = itemWith(PersistentDataType.STRING, "county-a");

		Inventory inventory = mock(Inventory.class);
		when(inventory.getHolder()).thenReturn(new SFInventoryHolder("faction-1", SFGUI.TITLE_TYPE_VIEW, 0, false, "county"));
		when(inventory.getContents()).thenReturn(new ItemStack[] { firstTitle });

		InventoryView inventoryView = mock(InventoryView.class);
		when(inventoryView.getTitle()).thenReturn("County§7 View");
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getView()).thenReturn(inventoryView);
		when(event.getCurrentItem()).thenReturn(pageItem, pageItem, null);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<TierLoader> tiers = mockStatic(TierLoader.class)) {
			factions.when(() -> FactionManager.getByString("faction-1")).thenReturn(faction);
			tiers.when(() -> TierLoader.getByString("county")).thenReturn(tier);

			assertDoesNotThrow(() -> view.click(event, inventory, player));

			verify(view).titleTypeView(inventory, player, faction, tier, false, 1);
			verify(player).playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		}
	}

	private static <T> ItemStack itemWith(PersistentDataType<?, T> type, T value) {
		PersistentDataContainer data = mock(PersistentDataContainer.class);
		when(data.get(any(NamespacedKey.class), eq(type))).thenReturn(value);
		ItemMeta meta = mock(ItemMeta.class);
		when(meta.getPersistentDataContainer()).thenReturn(data);
		ItemStack item = mock(ItemStack.class);
		when(item.getItemMeta()).thenReturn(meta);
		return item;
	}
}
