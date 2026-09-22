package me.Plugins.SimpleFactions.Map.fertility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.ProvinceGrid;
import me.Plugins.SimpleFactions.Map.Provinces.Province;

class FertilityProvinceResolverTest {
    private boolean provincesEnabled;
    private boolean mapEnabled;

    @BeforeEach
    void rememberCacheFlags() {
        provincesEnabled = Cache.provincesEnabled;
        mapEnabled = Cache.mapEnabled;
    }

    @AfterEach
    void restoreCacheFlags() {
        Cache.provincesEnabled = provincesEnabled;
        Cache.mapEnabled = mapEnabled;
    }

    @Test
    void fertilityAt_validProvince_returnsStoredFertility() {
        ProvinceManager manager = new ProvinceManager();
        manager.start(Map.of(7, new Province(7, "plains", 62)));

        ProvinceGrid grid = mock(ProvinceGrid.class);
        when(grid.getAt(10, 20)).thenReturn(7);

        assertEquals(62, FertilityProvinceResolver.fertilityAt(10, 20, grid, manager));
    }

    @Test
    void fertilityAt_unmappedOrInvalidProvince_returnsZero() {
        ProvinceManager manager = new ProvinceManager();
        manager.start(new HashMap<>());

        ProvinceGrid grid = mock(ProvinceGrid.class);
        when(grid.getAt(1, 2)).thenReturn(0);
        when(grid.getAt(3, 4)).thenReturn(99);

        assertEquals(0, FertilityProvinceResolver.fertilityAt(1, 2, grid, manager));
        assertEquals(0, FertilityProvinceResolver.fertilityAt(3, 4, grid, manager));
    }

    @Test
    void fertilityAt_nullGridOrManager_returnsZero() {
        ProvinceManager manager = new ProvinceManager();
        ProvinceGrid grid = mock(ProvinceGrid.class);

        assertEquals(0, FertilityProvinceResolver.fertilityAt(1, 2, null, manager));
        assertEquals(0, FertilityProvinceResolver.fertilityAt(1, 2, grid, null));
    }

    @Test
    void isActive_followsCacheFlags() {
        Cache.provincesEnabled = true;
        Cache.mapEnabled = true;
        assertTrue(FertilityProvinceResolver.isActive());

        Cache.mapEnabled = false;
        assertFalse(FertilityProvinceResolver.isActive());

        Cache.mapEnabled = true;
        Cache.provincesEnabled = false;
        assertFalse(FertilityProvinceResolver.isActive());
    }
}
