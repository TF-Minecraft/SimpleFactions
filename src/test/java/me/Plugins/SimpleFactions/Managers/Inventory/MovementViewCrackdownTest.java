package me.Plugins.SimpleFactions.Managers.Inventory;

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

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarStartService;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeService;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

class MovementViewCrackdownTest {

	@Test
	void refuse_successfulStart_doesNotEndMovement() {
		clickCrackdown(33, null);
	}

	@Test
	void refuse_failedStart_doesNotEndMovementOrClose() {
		clickCrackdown(33, CivilWarCopy.COULD_NOT_START);
	}

	@Test
	void accept_endsMovement_neverStartsCivilWar() {
		clickCrackdown(29, null);
	}

	private static void clickCrackdown(int slot, String startError) {
		InventoryManager inventoryManager = mock(InventoryManager.class);
		MovementView view = new MovementView(inventoryManager);
		Movement movement = mock(Movement.class);
		Faction faction = mock(Faction.class);
		Government government = mock(Government.class);
		when(movement.getId()).thenReturn("mov-1");
		when(movement.getFaction()).thenReturn(faction);
		when(movement.isLeader("Bob")).thenReturn(true);
		when(movement.getPower()).thenReturn(40.0);
		when(faction.getLeader()).thenReturn("Alice");
		when(faction.getId()).thenReturn("fac-1");
		when(faction.getGovernment()).thenReturn(government);

		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Bob");
		when(player.getLocation()).thenReturn(mock(Location.class));

		Inventory inventory = mock(Inventory.class);
		when(inventory.getHolder()).thenReturn(new SFInventoryHolder("mov-1", SFGUI.MOVEMENT_CRACKDOWN));

		ItemStack item = mock(ItemStack.class);
		ItemMeta meta = mock(ItemMeta.class);
		when(item.getItemMeta()).thenReturn(meta);

		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getCurrentItem()).thenReturn(item);
		when(event.getSlot()).thenReturn(slot);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<CivilWarStartService> start = mockStatic(CivilWarStartService.class);
				MockedStatic<MovementOutcomeService> outcomes = mockStatic(MovementOutcomeService.class);
				MockedStatic<StringFormatter> hex = mockStatic(StringFormatter.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			factions.when(() -> FactionManager.getMovementById("mov-1")).thenReturn(movement);
			start.when(() -> CivilWarStartService.start(movement)).thenReturn(startError);
			hex.when(() -> StringFormatter.formatHex(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

			view.click(event, inventory, player);

			if (slot == 29) {
				verify(government).endMovement(movement);
				start.verify(() -> CivilWarStartService.start(any()), never());
				outcomes.verify(() -> MovementOutcomeService.apply(any(), any()), never());
				verify(player).closeInventory();
			} else {
				verify(government, never()).endMovement(any());
				start.verify(() -> CivilWarStartService.start(movement));
				if (startError != null) {
					verify(player).sendMessage(startError);
					verify(player, never()).closeInventory();
				} else {
					verify(player).closeInventory();
				}
			}
		}
	}
}
