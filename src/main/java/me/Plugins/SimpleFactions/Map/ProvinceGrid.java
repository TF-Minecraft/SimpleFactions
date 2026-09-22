package me.Plugins.SimpleFactions.Map;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/**
 * Block-coordinate province lookup from Input/province_id_grid.bin.gz (uint16 row-major grid).
 */
public final class ProvinceGrid {
    private static final int HEADER_SIZE = 8;

    private final int width;
    private final int height;
    private final short[] ids;

    private ProvinceGrid(int width, int height, short[] ids) {
        this.width = width;
        this.height = height;
        this.ids = ids;
    }

    public static ProvinceGrid load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("province_id_grid.bin.gz not found: " + file);
        }

        byte[] payload;
        try (FileInputStream fis = new FileInputStream(file);
                GZIPInputStream gzip = new GZIPInputStream(fis);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            payload = out.toByteArray();
        }

        if (payload.length < HEADER_SIZE) {
            throw new IOException("province_id_grid data too short for header");
        }

        ByteBuffer header = ByteBuffer.wrap(payload, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int width = header.getInt();
        int height = header.getInt();
        if (width <= 0 || height <= 0) {
            throw new IOException("invalid province_id_grid dimensions: " + width + "x" + height);
        }

        int expectedBody = width * height * 2;
        int bodyOffset = HEADER_SIZE;
        if (payload.length - bodyOffset != expectedBody) {
            throw new IOException(
                    "province_id_grid body length "
                            + (payload.length - bodyOffset)
                            + " != expected "
                            + expectedBody
                            + " for "
                            + width
                            + "x"
                            + height);
        }

        short[] ids = new short[width * height];
        ByteBuffer body = ByteBuffer.wrap(payload, bodyOffset, expectedBody).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < ids.length; i++) {
            ids[i] = body.getShort();
        }

        return new ProvinceGrid(width, height, ids);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getAt(int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= height) {
            return 0;
        }
        return ids[z * width + x] & 0xFFFF;
    }
}
