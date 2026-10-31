package net.tfminecraft.simplefactions.managers.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * The faction list's rows per viewer, kept between periodic refreshes.
 *
 * <p>An open faction list is redrawn every second, and each row reads the faction's ranks,
 * stability, guilds, subjects, allies and the viewer's relations and intelligence. Rebuilding
 * every row for every viewer on each pass was the steadiest cost of the menu refresh on Main.
 * A refresh now reuses a row built for the same viewer, character and faction within the last
 * few seconds, so a change reaches an open list at most that much later. Opening the list,
 * paging and changing the ranking always build every row fresh.
 */
final class FactionListItems {
	static final long MAX_AGE_NANOS = TimeUnit.SECONDS.toNanos(5);

	private record Row(Faction faction, ItemStack item, long built) {}

	private record Viewer(String character, Map<String, Row> rows) {}

	private final Map<UUID, Viewer> viewers = new HashMap<>();
	private final long maxAge;
	private final LongSupplier clock;

	FactionListItems(long maxAge, LongSupplier clock) {
		this.maxAge = maxAge;
		this.clock = clock;
	}

	/** Starts one drawing of the list. Only a refresh may reuse rows, and only the shown ones are kept. */
	Drawing begin(Player viewer, boolean refresh) {
		return new Drawing(viewer, refresh);
	}

	void forget(UUID viewer) {
		viewers.remove(viewer);
	}

	final class Drawing {
		private final UUID id;
		private final String character;
		private final Map<String, Row> previous;
		private final Map<String, Row> shown = new HashMap<>();
		private final long now = clock.getAsLong();

		private Drawing(Player viewer, boolean refresh) {
			id = viewer == null ? null : viewer.getUniqueId();
			character = Members.key(viewer);
			Viewer last = refresh ? viewers.get(id) : null;
			// A character switch changes whose relations and intelligence every row shows.
			previous = last != null && Objects.equals(last.character(), character) ? last.rows() : Map.of();
		}

		ItemStack item(Faction faction, Supplier<ItemStack> build) {
			Row row = previous.get(faction.getId());
			if (row == null || row.faction() != faction || now - row.built() >= maxAge) {
				row = new Row(faction, build.get(), now);
			}
			shown.put(faction.getId(), row);
			return row.item();
		}

		void end() {
			viewers.put(id, new Viewer(character, shown));
		}
	}
}
