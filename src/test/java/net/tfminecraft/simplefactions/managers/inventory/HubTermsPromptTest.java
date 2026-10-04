package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HubTermsPromptTest {
    @Test
    void promptExpiresOnlyAfterSixtySeconds() {
        long askedAtMillis = 1_000L;

        assertFalse(HubTermsPrompt.isExpired(askedAtMillis, askedAtMillis + 60_000L));
        assertTrue(HubTermsPrompt.isExpired(askedAtMillis, askedAtMillis + 60_001L));
    }
}
