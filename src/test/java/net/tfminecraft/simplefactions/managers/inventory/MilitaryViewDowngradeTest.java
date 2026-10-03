package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.objects.Faction;

class MilitaryViewDowngradeTest {

	private SimpleFactions pluginBackup;

	@BeforeEach
	void setUp() {
		pluginBackup = SimpleFactions.plugin;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getName()).thenReturn("simplefactions");
		when(plugin.namespace()).thenReturn("simplefactions");
		SimpleFactions.plugin = plugin;
	}

	@AfterEach
	void tearDown() {
		SimpleFactions.plugin = pluginBackup;
	}

	@Test
	void downgradeButton_survivesTheSlotClearUnderTheRegiment() {
		InventoryManager manager = mock(InventoryManager.class);
		MilitaryView view = new MilitaryView(manager);
		view.creator = mock(MilitaryCreator.class);

		Faction faction = faction("Leader");
		Regiment professional = regiment("professional", 2, false);
		Regiment levy = regiment("levy", 4, true);
		Military military = faction.getMilitary();
		when(military.getRegiments()).thenReturn(List.of(professional, levy));
		when(military.getQueue()).thenReturn(List.of());
		when(military.getHomeCompany()).thenReturn(null);

		ItemStack downgrade = mock(ItemStack.class);
		when(view.creator.createRegimentIcon(eq(faction), any())).thenReturn(mock(ItemStack.class));
		when(view.creator.createRegimentIncreaseButton(eq(faction), any())).thenReturn(mock(ItemStack.class));
		when(view.creator.createRegimentDecreaseButton(faction, professional)).thenReturn(downgrade);
		when(view.creator.createMilitarySummary(faction)).thenReturn(mock(ItemStack.class));
		when(view.creator.createVehiclePoolIcon(faction)).thenReturn(mock(ItemStack.class));
		when(manager.createBackButton(SFGUI.MILITARY_VIEW)).thenReturn(mock(ItemStack.class));

		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Leader");
		Inventory inventory = mock(Inventory.class);
		Map<Integer, ItemStack> slots = new HashMap<>();
		doAnswer(invocation -> {
			slots.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(inventory).setItem(anyInt(), any());

		try (MockedConstruction<ItemStack> ignored = mockConstruction(ItemStack.class)) {
			view.militaryView(inventory, player, faction, false);
		}

		assertSame(downgrade, slots.get(21));
		verify(view.creator, never()).createRegimentDecreaseButton(faction, levy);
	}

	@Test
	void downgradeClick_opensConfirmBeforeShrinking() {
		InventoryManager manager = mock(InventoryManager.class);
		manager.confirming = new java.util.HashMap<>();
		MilitaryView view = spy(new MilitaryView(manager));
		doNothing().when(view).militaryView(any(), any(), any(), anyBoolean());

		Faction faction = faction("Leader");
		Regiment regiment = regiment("professional", 2, false);
		Military military = faction.getMilitary();
		when(military.getRegiment("professional")).thenReturn(regiment);
		when(military.getQueue()).thenReturn(List.of(
				mock(net.tfminecraft.simplefactions.army.MilitaryExpansion.class),
				mock(net.tfminecraft.simplefactions.army.MilitaryExpansion.class),
				mock(net.tfminecraft.simplefactions.army.MilitaryExpansion.class)));

		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Leader");
		Inventory inventory = mock(Inventory.class);
		InventoryView inventoryView = mock(InventoryView.class);
		when(inventoryView.getTopInventory()).thenReturn(inventory);
		ItemStack clicked = decreaseItem("faction-1", "professional");
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getView()).thenReturn(inventoryView);
		when(event.getCurrentItem()).thenReturn(clicked);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("faction-1")).thenReturn(faction);
			view.click(event, inventory, player);
		}

		verify(regiment, never()).sizeDecrease();
		verify(manager).confirmView(player, faction, "regiment", "professional");
		assertSame(faction, manager.confirming.get(player));
		verify(player).playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		verify(player, never()).sendMessage("§cQueue is full");
		verify(view, never()).militaryView(any(), any(), any(), anyBoolean());
	}

	@Test
	void downgradeClick_ignoresAnyoneButTheLeader() {
		InventoryManager manager = mock(InventoryManager.class);
		MilitaryView view = spy(new MilitaryView(manager));
		doNothing().when(view).militaryView(any(), any(), any(), anyBoolean());

		Faction faction = faction("Leader");
		Regiment regiment = regiment("professional", 2, false);
		when(faction.getMilitary().getRegiment("professional")).thenReturn(regiment);

		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Member");
		Inventory inventory = mock(Inventory.class);
		ItemStack clicked = decreaseItem("faction-1", "professional");
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getCurrentItem()).thenReturn(clicked);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getByString("faction-1")).thenReturn(faction);
			view.click(event, inventory, player);
		}

		verify(regiment, never()).sizeDecrease();
		verify(manager, never()).confirmView(any(), any(), any(), any());
		verify(view, never()).militaryView(any(), any(), any(), anyBoolean());
	}

	private static Faction faction(String leader) {
		Faction faction = mock(Faction.class);
		when(faction.getLeader()).thenReturn(leader);
		when(faction.isLeader(leader)).thenReturn(true);
		when(faction.getMilitary()).thenReturn(mock(Military.class));
		return faction;
	}

	private static Regiment regiment(String id, int slots, boolean levy) {
		Regiment regiment = mock(Regiment.class);
		when(regiment.getId()).thenReturn(id);
		when(regiment.getCurrentSlots()).thenReturn(slots);
		when(regiment.isLevy()).thenReturn(levy);
		return regiment;
	}

	private static ItemStack decreaseItem(String factionId, String regimentId) {
		PersistentDataContainer data = mock(PersistentDataContainer.class);
		when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenAnswer(invocation -> {
			NamespacedKey key = invocation.getArgument(0);
			return switch (key.getKey()) {
				case "id" -> factionId;
				case "regiment" -> regimentId;
				case "type" -> "decrease";
				default -> null;
			};
		});
		ItemMeta meta = mock(ItemMeta.class);
		when(meta.getPersistentDataContainer()).thenReturn(data);
		ItemStack item = mock(ItemStack.class);
		when(item.hasItemMeta()).thenReturn(true);
		when(item.getItemMeta()).thenReturn(meta);
		return item;
	}
}
