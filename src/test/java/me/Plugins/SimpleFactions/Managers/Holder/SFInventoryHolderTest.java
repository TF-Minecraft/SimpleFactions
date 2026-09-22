package me.Plugins.SimpleFactions.Managers.Holder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.enums.SFGUI;

class SFInventoryHolderTest {

    @Test
    void defaultConstructorHasNullSecondaryId() {
        SFInventoryHolder h = new SFInventoryHolder("faction1", SFGUI.FACTION_VIEW);
        assertNull(h.getSecondaryId());
    }

    @Test
    void secondaryIdConstructorStoresAndReturnsValue() {
        SFInventoryHolder h = new SFInventoryHolder("faction1", SFGUI.LAW_SELECT, "groupA");
        assertEquals("faction1", h.getId());
        assertEquals(SFGUI.LAW_SELECT, h.getType());
        assertEquals("groupA", h.getSecondaryId());
        assertEquals(0, h.getPage());
    }

    @Test
    void fullConstructorStoresAllFields() {
        SFInventoryHolder h = new SFInventoryHolder("faction1", SFGUI.TITLE_TYPE_VIEW, 3, true, "tier42");
        assertEquals("faction1", h.getId());
        assertEquals(SFGUI.TITLE_TYPE_VIEW, h.getType());
        assertEquals(3, h.getPage());
        assertEquals(true, h.getFlag());
        assertEquals("tier42", h.getSecondaryId());
    }

    @Test
    void pageConstructorHasNullSecondaryId() {
        SFInventoryHolder h = new SFInventoryHolder("g1", SFGUI.GUILD_VIEW, 2);
        assertNull(h.getSecondaryId());
        assertEquals(2, h.getPage());
    }

    @Test
    void pageFlagConstructorHasNullSecondaryId() {
        SFInventoryHolder h = new SFInventoryHolder("g1", SFGUI.FAVOUR_REPRESS_TYPE, 0, true);
        assertNull(h.getSecondaryId());
        assertEquals(true, h.getFlag());
    }

    @Test
    void setPageUpdatesHolderAndClampsNegative() {
        SFInventoryHolder h = new SFInventoryHolder("faction1", SFGUI.TITLE_TYPE_VIEW, 0, false, "county");
        h.setPage(2);
        assertEquals(2, h.getPage());
        h.setPage(-1);
        assertEquals(0, h.getPage());
    }
}
