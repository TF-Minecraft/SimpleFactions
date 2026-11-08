package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

class StaffConfirmTest {

	@Test
	void confirmIsOnlyTheLastWordInAnyCase() {
		assertFalse(StaffConfirm.isConfirmed(null));
		assertFalse(StaffConfirm.isConfirmed(new String[0]));
		assertFalse(StaffConfirm.isConfirmed(new String[] {"confirm", "x"}));
		assertTrue(StaffConfirm.isConfirmed(new String[] {"x", "CONFIRM"}));
	}

	@Test
	void stripDropsOnlyATrailingConfirm() {
		String[] plain = {"admin", "end", "1"};
		assertSame(plain, StaffConfirm.strip(plain));
		assertArrayEquals(plain, StaffConfirm.strip(new String[] {"admin", "end", "1", "confirm"}));
	}

	@Test
	void gateRunsSafeAndConfirmedCommands() {
		Player player = mock(Player.class);
		assertArrayEquals(new String[] {"a"}, StaffConfirm.gate(player, "war", new String[] {"a", "confirm"}, null));
		assertArrayEquals(new String[] {"a"}, StaffConfirm.gate(player, "war", new String[] {"a", "confirm"}, "risk"));
		verify(player, never()).sendMessage(any(String.class));
	}

	@Test
	void gateStopsAnUnconfirmedRiskyCommandAndOffersTheConfirmedOne() {
		Player player = mock(Player.class);

		assertNull(StaffConfirm.gate(player, "WAR", new String[] {"admin", "end", "1"}, "ends it."));

		verify(player).sendMessage("§6This cannot be undone: §eends it.");
		ArgumentCaptor<Component> line = ArgumentCaptor.forClass(Component.class);
		verify(player).sendMessage(line.capture());
		Component command = line.getValue().children().get(0);
		assertTrue(((TextComponent) command).content().equals("/war admin end 1 confirm"));
		assertTrue(command.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND);
	}
}
