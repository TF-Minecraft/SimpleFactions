package net.tfminecraft.simplefactions.utils;

import java.util.Arrays;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Staff commands whose effects cannot be undone run only when typed again with {@code confirm}
 * as the last word. Stateless on purpose: nothing to expire, and a restart cannot leave a
 * half-confirmed command behind.
 */
public final class StaffConfirm {
	public static final String WORD = "confirm";

	private StaffConfirm() {}

	public static boolean isConfirmed(String[] args) {
		return args != null && args.length > 0 && WORD.equalsIgnoreCase(args[args.length - 1]);
	}

	/** The arguments without a trailing {@code confirm}. */
	public static String[] strip(String[] args) {
		return isConfirmed(args) ? Arrays.copyOf(args, args.length - 1) : args;
	}

	/** Tells staff what the command would do and how to run it for real. */
	public static void prompt(Player player, String command, String risk) {
		String full = "/" + command + " " + WORD;
		player.sendMessage("§6This cannot be undone: §e" + risk);
		player.sendMessage(Component.text("Run ", NamedTextColor.GRAY)
				.append(Component.text(full, NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
						.clickEvent(ClickEvent.suggestCommand(full))
						.hoverEvent(HoverEvent.showText(Component.text("Click to fill in the command"))))
				.append(Component.text(" to go ahead.", NamedTextColor.GRAY)));
	}

	/**
	 * Runs the confirm gate for one command. Returns the arguments to execute, or null when the
	 * command was stopped to ask for confirmation.
	 */
	public static String[] gate(Player player, String label, String[] args, String risk) {
		String[] stripped = strip(args);
		if (risk == null || isConfirmed(args)) {
			return stripped;
		}
		prompt(player, label.toLowerCase(Locale.ROOT) + " " + String.join(" ", stripped), risk);
		return null;
	}
}
