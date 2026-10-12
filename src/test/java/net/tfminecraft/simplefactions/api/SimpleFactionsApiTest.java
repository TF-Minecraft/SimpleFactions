package net.tfminecraft.simplefactions.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.events.SimpleFactionsReadyEvent;

class SimpleFactionsApiTest {
    @Test
    void readyOnlyOnceThePluginHasStarted() {
        SimpleFactions previous = SimpleFactions.plugin;
        try {
            SimpleFactions.plugin = null;
            assertFalse(SimpleFactionsApi.ready());
            SimpleFactions plugin = mock(SimpleFactions.class);
            SimpleFactions.plugin = plugin;
            assertFalse(SimpleFactionsApi.ready());
            when(plugin.isStarted()).thenReturn(true);
            assertTrue(SimpleFactionsApi.ready());
        } finally {
            SimpleFactions.plugin = previous;
        }
    }

    @Test
    void theReadyEventHasItsOwnHandlers() {
        SimpleFactionsReadyEvent event = new SimpleFactionsReadyEvent();
        assertNotNull(event.getHandlers());
        assertSame(SimpleFactionsReadyEvent.getHandlerList(), event.getHandlers());
    }
}
