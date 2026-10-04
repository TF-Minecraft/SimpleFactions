package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.map.provinces.Province;

class ProvinceLoaderInfrastructureTest {
    @TempDir
    Path dir;

    @Test
    void aSavedInfrastructureSegmentDoesNotStopTheProvinceLoading() throws Exception {
        Path provinces = dir.resolve("provinces.txt");
        Path neighbours = dir.resolve("neighbours.json");
        Files.writeString(provinces, "1 = 10,64,20;bog;40;12\n2 = 30,64,40;plains;7\n");
        Files.writeString(neighbours, "{\"1\":[2],\"2\":[1]}");

        Map<Integer, Province> loaded = new ProvinceLoader().loadProvinces(
                provinces.toFile(), neighbours.toFile());

        assertEquals(Terrain.BOG, loaded.get(1).getTerrain());
        assertEquals(40, loaded.get(1).getFertility());
        assertEquals(10, loaded.get(1).getCenterX());
        assertEquals(20, loaded.get(1).getCenterZ());
        assertEquals(Terrain.PLAINS, loaded.get(2).getTerrain());
        assertEquals(7, loaded.get(2).getFertility());
        assertEquals(1, loaded.get(1).getNeighbours().size());
    }
}
