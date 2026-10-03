package net.tfminecraft.simplefactions.map.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.simplefactions.map.provinces.Province;

class TrackProvinceLookupTest {
    @TempDir
    Path tempDir;

    @Test
    void samplesMapToDistinctLandProvinces() throws IOException {
        ProvinceGrid grid = grid();
        Map<Integer, Province> provinces = Map.of(
                5, new Province(5, "plains", 0),
                6, new Province(6, "forest", 0),
                7, new Province(7, "sea", 0));

        assertEquals(java.util.Set.of(5, 6), TrackProvinceLookup.collect(grid, List.of(
                new TrackProvinceLookup.Point(0.5, 64, 0),
                new TrackProvinceLookup.Point(1.5, 64, 0),
                new TrackProvinceLookup.Point(2.5, 64, 0),
                new TrackProvinceLookup.Point(3.5, 64, 0),
                new TrackProvinceLookup.Point(4.5, 64, 0)), provinces));
    }

    private ProvinceGrid grid() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(5).putInt(1).array());
            try (DataOutputStream out = new DataOutputStream(gzip)) {
                for (int id : new int[] {5, 5, 6, 0, 7}) out.writeShort(Short.reverseBytes((short) id));
            }
        }
        Path file = tempDir.resolve("grid.gz");
        java.nio.file.Files.write(file, bytes.toByteArray());
        return ProvinceGrid.load(file.toFile());
    }
}
