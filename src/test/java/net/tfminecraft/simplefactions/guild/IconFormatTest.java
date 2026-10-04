package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.guild.IconFormat.Kind;

class IconFormatTest {
    @Test
    void materialFormKeepsCustomModelData() {
        assertEquals(Kind.MATERIAL, IconFormat.classify("chest_minecart.0"));
        assertEquals(Kind.MATERIAL, IconFormat.classify("gold_ingot.0"));
        assertEquals(Kind.MATERIAL, IconFormat.classify("writable_book.0"));
        assertEquals(Kind.MATERIAL, IconFormat.classify("CHEST_MINECART.12"));
    }

    @Test
    void itemPathsUseTlibsPrefixes() {
        assertEquals(Kind.ITEM_PATH, IconFormat.classify("m.currency.stack_of_coins"));
        assertEquals(Kind.ITEM_PATH, IconFormat.classify("m.currency.handful_of_coins"));
        assertEquals(Kind.ITEM_PATH, IconFormat.classify("M.CURRENCY.POUCH_OF_COINS"));
        assertEquals(Kind.ITEM_PATH, IconFormat.classify("ia.iasurvival:letter"));
        assertEquals(Kind.ITEM_PATH, IconFormat.classify("v.book"));
        assertEquals(Kind.ITEM_PATH, IconFormat.classify("v.paper"));
        assertEquals(Kind.ITEM_PATH, IconFormat.classify("modeled.(type=emerald;model=3)"));
    }

    @Test
    void malformedIconsAreNeitherForm() {
        assertEquals(Kind.MALFORMED, IconFormat.classify(null));
        assertEquals(Kind.MALFORMED, IconFormat.classify(""));
        assertEquals(Kind.MALFORMED, IconFormat.classify("   "));
        assertEquals(Kind.MALFORMED, IconFormat.classify("dirt"));
        assertEquals(Kind.MALFORMED, IconFormat.classify("chest_minecart"));
        assertEquals(Kind.MALFORMED, IconFormat.classify("foo.bar"));
        assertEquals(Kind.MALFORMED, IconFormat.classify("not.a.path"));
    }
}
