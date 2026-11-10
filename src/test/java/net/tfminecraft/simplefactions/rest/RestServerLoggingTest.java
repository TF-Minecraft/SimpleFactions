package net.tfminecraft.simplefactions.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.api.GatewayClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestServerLoggingTest {
    @TempDir Path directory;
    private SimpleFactions previousPlugin;
    private boolean previousMapEnabled;
    private final Logger logger = Logger.getLogger(RestServer.class.getName());
    private final List<LogRecord> records = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    };

    @BeforeEach
    void setUp() {
        previousPlugin = SimpleFactions.plugin;
        previousMapEnabled = Cache.mapEnabled;
        SimpleFactions.plugin = null;
        Cache.mapEnabled = true;
        logger.addHandler(handler);
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(handler);
        SimpleFactions.plugin = previousPlugin;
        Cache.mapEnabled = previousMapEnabled;
    }

    @Test
    void failedRequestsBeforeEnableKeepWarningDetails() throws Exception {
        Path upload = Files.writeString(directory.resolve("regions.json"), "{}");
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request(anyString(), anyString(), nullable(String.class)))
                    .thenReturn(GatewayClient.Result.fail("gateway offline"));

            assertNull(RestServer.fetchBannerList());
            RestServer.upload("regions", upload.toFile());
            RestServer.commenceRegen("queued");
        }

        assertEquals(List.of("fetchBannerList failed: gateway offline", "Upload failed for regions: gateway offline",
                "Regeneration failed: gateway offline"), records.stream()
                .filter(record -> record.getLevel() == Level.WARNING).map(LogRecord::getMessage).toList());
    }

    @Test
    void exceptionsBeforeEnableKeepOriginalCause() throws Exception {
        Path upload = Files.writeString(directory.resolve("regions.json"), "{}");
        RuntimeException failure = new IllegalStateException("gateway failure");
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request(anyString(), anyString(), nullable(String.class)))
                    .thenThrow(failure);

            assertNull(RestServer.fetchBannerList());
            RestServer.upload("regions", upload.toFile());
            RestServer.commenceRegen("queued");
        }

        assertEquals(3, records.size());
        for (LogRecord record : records) {
            assertEquals(Level.WARNING, record.getLevel());
            assertSame(failure, record.getThrown());
        }
    }

    @Test
    void sendJsonPostsTheGivenPayloadAndSaysWhetherItWasTaken() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request(eq("POST"), endsWith("/data/upload/coalitions"), anyString()))
                    .thenReturn(GatewayClient.Result.success("{}"));
            assertTrue(RestServer.sendJson("coalitions", "{\"coalitions\": []}"));
            gateway.verify(() -> GatewayClient.request(eq("POST"), endsWith("/data/upload/coalitions"),
                    eq("{\"coalitions\":[]}")));

            assertFalse(RestServer.sendJson("coalitions", "{not json"));
            Cache.mapEnabled = false;
            assertFalse(RestServer.sendJson("coalitions", "{}"));
            gateway.verify(() -> GatewayClient.request(anyString(), anyString(), eq("{}")), never());
        }
    }
}
