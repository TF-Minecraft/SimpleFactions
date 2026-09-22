package net.tfminecraft.simplefactions.util;

import java.util.List;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyModelDataTest {
    @Test
    void presenceAndReadUseOnlyTheFirstFloat() {
        ItemMeta meta = mock(ItemMeta.class);
        CustomModelDataComponent component = mock(CustomModelDataComponent.class);
        when(meta.getCustomModelDataComponent()).thenReturn(component);
        when(component.getFloats()).thenReturn(List.of());
        when(component.getStrings()).thenReturn(List.of("not-an-integer-model"));
        assertFalse(LegacyModelData.has(meta));
        assertThrows(IllegalStateException.class, () -> LegacyModelData.get(meta));
        when(component.getFloats()).thenReturn(List.of(-3.9f, 99f));
        assertTrue(LegacyModelData.has(meta));
        assertEquals(-3, LegacyModelData.get(meta));
    }

    @Test
    void integerWriteReplacesEveryComponentListAndAppliesTheSnapshot() {
        ItemMeta meta = mock(ItemMeta.class);
        CustomModelDataComponent component = mock(CustomModelDataComponent.class);
        when(meta.getCustomModelDataComponent()).thenReturn(component);
        LegacyModelData.set(meta, 16777217);
        verify(component).setFloats(List.of(16777216f));
        verify(component).setFlags(List.of());
        verify(component).setStrings(List.of());
        verify(component).setColors(List.of());
        verify(meta).setCustomModelDataComponent(component);
    }

    @Test
    void nullClearsTheWholeComponent() {
        ItemMeta meta = mock(ItemMeta.class);
        LegacyModelData.set(meta, null);
        verify(meta).setCustomModelDataComponent(null);
        verify(meta, never()).getCustomModelDataComponent();
    }
}
