package net.tfminecraft.simplefactions.managers.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.holder.SFInventoryHolder;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarStartService;
import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.movement.Movement;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

class MovementViewDeclineTest {

	@Test
	void decline_successfulStart_doesNotEndMovement() {
		clickDecline(null);
	}

	@Test
	void decline_failedStart_doesNotEndMovement() {
		clickDecline(CivilWarCopy.COULD_NOT_START);
	}

	private static void clickDecline(String startError) {
		InventoryManager inventoryManager = mock(InventoryManager.class);
		MovementView view = new MovementView(inventoryManager);
		Movement movement = mock(Movement.class);
		Faction faction = mock(Faction.class);
		Government government = mock(Government.class);
		when(movement.getId()).thenReturn("mov-1");
		when(movement.getFaction()).thenReturn(faction);
		when(movement.hasLeader()).thenReturn(false);
		when(faction.getLeader()).thenReturn("Alice");
		when(faction.getGovernment()).thenReturn(government);

		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Alice");
		when(player.getLocation()).thenReturn(mock(Location.class));

		Inventory inventory = mock(Inventory.class);
		when(inventory.getHolder()).thenReturn(new SFInventoryHolder("mov-1", SFGUI.MOVEMENT_DEMANDS));

		ItemStack item = mock(ItemStack.class);
		ItemMeta meta = mock(ItemMeta.class);
		when(item.getItemMeta()).thenReturn(meta);

		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getCurrentItem()).thenReturn(item);
		when(event.getSlot()).thenReturn(33);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<CivilWarStartService> start = mockStatic(CivilWarStartService.class);
				MockedStatic<StringFormatter> hex = mockStatic(StringFormatter.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getMovementById("mov-1")).thenReturn(movement);
			start.when(() -> CivilWarStartService.start(movement)).thenReturn(startError);
			hex.when(() -> StringFormatter.formatHex(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

			view.click(event, inventory, player);

			verify(government, never()).endMovement(any());
			if (startError != null) {
				verify(player).sendMessage(startError);
				verify(player, never()).closeInventory();
			} else {
				verify(player).closeInventory();
			}
		}
	}
}
