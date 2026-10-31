package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.TestCharacters;
import net.tfminecraft.simplefactions.objects.Faction;

class FactionListItemsTest {
	private final long[] now = {1_000};
	private final FactionListItems items = new FactionListItems(5_000, () -> now[0]);
	private final Player viewer = mock(Player.class);
	private final Faction realm = faction("realm");
	private final int[] builds = {0};
	private TestCharacters characters;
	private String first;

	@BeforeEach
	void setUp() {
		when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
		characters = TestCharacters.install();
		first = characters.character(UUID.randomUUID(), "First");
		characters.play(viewer, first);
	}

	@Test
	void aRefreshReusesARowUntilItIsFiveSecondsOld() {
		ItemStack built = draw(true, realm);
		now[0] += 4_999;
		assertSame(built, draw(true, realm));
		assertEquals(1, builds[0]);
		now[0] += 1;
		assertNotSame(built, draw(true, realm));
		assertEquals(2, builds[0]);
	}

	@Test
	void openingTheListAlwaysBuildsFreshRows() {
		ItemStack built = draw(true, realm);
		assertNotSame(built, draw(false, realm));
		assertEquals(2, builds[0]);
	}

	@Test
	void aSwitchedCharacterOrReplacedFactionGetsFreshRows() {
		ItemStack built = draw(true, realm);
		characters.play(viewer, characters.character(UUID.randomUUID(), "Second"));
		ItemStack second = draw(true, realm);
		assertNotSame(built, second);
		ItemStack refounded = draw(true, faction("realm"));
		assertNotSame(second, refounded);
		assertEquals(3, builds[0]);
	}

	@Test
	void onlyRowsShownInTheLastDrawingAreKept() {
		Faction other = faction("other");
		ItemStack kept = draw(true, realm);
		draw(true, other);
		assertNotSame(kept, draw(true, realm));
		assertEquals(3, builds[0]);
	}

	@Test
	void viewersAreKeptApartAndForgottenOnQuit() {
		Player other = mock(Player.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		characters.play(other, first);
		ItemStack mine = draw(true, realm);
		var drawing = items.begin(other, true);
		assertNotSame(mine, drawing.item(realm, true, counted()));
		drawing.end();
		items.forget(viewer.getUniqueId());
		assertNotSame(mine, draw(true, realm));
		assertEquals(3, builds[0]);
	}

	@Test
	void aMissingViewerStillGetsItsRows() {
		var drawing = items.begin(null, true);
		ItemStack built = drawing.item(realm, true, counted());
		drawing.end();
		var again = items.begin(null, true);
		assertSame(built, again.item(realm, true, counted()));
		again.end();
		assertEquals(1, builds[0]);
	}

	@Test
	void aRowIsRebuiltWhenExactAccessChanges() {
		ItemStack exact = draw(true, realm, true);
		ItemStack reported = draw(true, realm, false);
		assertNotSame(exact, reported);
		assertSame(reported, draw(true, realm, false));
		assertNotSame(reported, draw(true, realm, true));
		assertEquals(3, builds[0]);
	}

	private ItemStack draw(boolean refresh, Faction faction) {
		return draw(refresh, faction, true);
	}

	private ItemStack draw(boolean refresh, Faction faction, boolean exact) {
		var drawing = items.begin(viewer, refresh);
		ItemStack item = drawing.item(faction, exact, counted());
		drawing.end();
		return item;
	}

	private Supplier<ItemStack> counted() {
		return () -> {
			builds[0]++;
			return mock(ItemStack.class);
		};
	}

	private static Faction faction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		return faction;
	}
}
